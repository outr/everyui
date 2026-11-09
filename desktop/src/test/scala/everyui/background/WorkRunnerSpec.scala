package everyui.background

import everyui.async.Task
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Scheduled work run while the app is open (WorkRunner), on a clock, timers, settings and a network of the spec's
  * own: once after its delay, every period, retried after its backoff, waiting for a network, cancelled, kept in the
  * settings and caught up as the app starts again, and its records written and read back. */
class WorkRunnerSpec extends AnyWordSpec with Matchers {
  /** A world for a runner: a clock moved by hand, timers that fire as it passes them, settings, a network, and the
    * runs the app was asked for with what it answers. */
  private class World(val settings: scala.collection.mutable.Map[String, String] =
                        scala.collection.mutable.Map.empty[String, String], val start: Long = 1_000_000_000_000L) {
    var now: Long = start
    var online: Boolean = true
    var timers: Vector[(Long, Int, () => Unit)] = Vector.empty
    private var order = 0
    var runs: Vector[(String, Long, Map[String, String])] = Vector.empty
    var answer: String => Option[WorkResult] = _ => Some(WorkResult.Done)

    val runner = new WorkRunner(() => now, (seconds, f) => {
      order += 1
      timers :+= ((now + (seconds * 1000.0).toLong, order, f))
    }, name => settings.getOrElse(name, ""), (name, value) =>
      if (value.isEmpty) settings.remove(name) else settings(name) = value, () => online, (key, data) =>
      Task.async[WorkResult] { done =>
        runs :+= ((key, now, data))
        answer(key).foreach(done)
      })

    /** The clock moved on by `seconds`, each timer that comes due on the way fired at its time, in order. */
    def pass(seconds: Double): Unit = {
      val end = now + (seconds * 1000.0).toLong
      var next = timers.filter(_._1 <= end).sortBy(t => (t._1, t._2)).headOption
      while (next.nonEmpty) {
        val t = next.get
        timers = timers.filterNot(_ eq t)
        now = math.max(now, t._1)
        t._3()
        next = timers.filter(_._1 <= end).sortBy(t => (t._1, t._2)).headOption
      }
      now = end
    }

    def ranAt: Vector[Long] = runs.map(_._2 - start)  }

  private def once(key: String, delay: Double = 0.0, network: WorkNetwork = WorkNetwork.NotNeeded,
                   backoff: WorkBackoff = WorkBackoff(), data: Map[String, String] = Map.empty,
                   replace: Boolean = true): WorkRequest =
    WorkRequest(key, 0, WorkConstraints(network), expedited = false, backoff, delay, data, replace)

  private def every(key: String, minutes: Int): WorkRequest =
    WorkRequest(key, minutes, WorkConstraints(), expedited = false, WorkBackoff(), 0.0, Map.empty, replace = true)

  "Work once, while the app is open" should {
    "run after its delay with its data, and then be gone" in {
      val w = new World
      w.runner.resume()
      w.runner.schedule(once("send", delay = 5.0, data = Map("id" -> "m1"))) shouldBe true
      w.runner.pending("send") shouldBe true
      w.pass(4.9)
      w.runs shouldBe empty
      w.pass(0.2)
      w.runs.map(r => (r._1, r._3)) shouldBe Vector("send" -> Map("id" -> "m1"))
      w.ranAt shouldBe Vector(5000L)
      w.runner.pending("send") shouldBe false
      w.runner.keys shouldBe empty
      w.pass(3600.0)
      w.runs.size shouldBe 1
    }
    "be tried again after its backoff, doubling, until it is done" in {
      val w = new World
      var tries = 0
      w.answer = _ => {
        tries += 1
        Some(if (tries < 3) WorkResult.Retry else WorkResult.Done)
      }
      w.runner.resume()
      w.runner.schedule(once("send", backoff = WorkBackoff(exponential = true, seconds = 30.0)))
      w.pass(600.0)
      w.ranAt shouldBe Vector(0L, 30000L, 90000L)
      w.runner.pending("send") shouldBe false
    }
    "be tried again a fixed time apart with a linear backoff, and let go when it fails" in {
      val w = new World
      var tries = 0
      w.answer = _ => {
        tries += 1
        Some(if (tries < 3) WorkResult.Retry else WorkResult.Failed)
      }
      w.runner.resume()
      w.runner.schedule(once("send", backoff = WorkBackoff(exponential = false, seconds = 20.0)))
      w.pass(600.0)
      w.ranAt shouldBe Vector(0L, 20000L, 60000L)
      w.runner.pending("send") shouldBe false
    }
    "wait for a network, and run once there is one" in {
      val w = new World
      w.online = false
      w.runner.resume()
      w.runner.schedule(once("send", network = WorkNetwork.Connected))
      w.pass(100.0)
      w.runs shouldBe empty
      w.online = true
      w.pass(30.0)
      w.ranAt shouldBe Vector(120000L)
    }
    "be taken as needing another try where it never answers" in {
      val w = new World
      w.answer = _ => None
      w.runner.resume()
      w.runner.schedule(once("stuck"))
      w.pass(569.0)
      w.runner.record("stuck").map(_.retries) shouldBe Some(0)
      w.pass(1.0)
      w.runner.record("stuck").map(_.retries) shouldBe Some(1)
      w.pass(30.0)
      w.runs.size shouldBe 2
    }
    "keep the one scheduled where it is not to be replaced, and replace it otherwise" in {
      val w = new World
      w.runner.resume()
      w.runner.schedule(once("send", delay = 60.0, data = Map("v" -> "1")))
      w.runner.schedule(once("send", delay = 5.0, data = Map("v" -> "2"), replace = false))
      w.pass(10.0)
      w.runs shouldBe empty
      w.runner.schedule(once("send", delay = 5.0, data = Map("v" -> "3")))
      w.pass(60.0)
      w.runs.map(_._3) shouldBe Vector(Map("v" -> "3"))
    }
    "not run once cancelled" in {
      val w = new World
      w.runner.resume()
      w.runner.schedule(once("send", delay = 5.0))
      w.runner.cancel("send")
      w.runner.pending("send") shouldBe false
      w.pass(60.0)
      w.runs shouldBe empty
      w.settings.keys.filter(_.startsWith("everyui.work")).toList.filter(k => w.settings(k).nonEmpty) shouldBe Nil
    }
  }

  "Work every so many minutes, while the app is open" should {
    "run at once and then every period, until cancelled" in {
      val w = new World
      w.runner.resume()
      w.runner.schedule(every("sync", 15))
      w.pass(46.0 * 60.0)
      w.ranAt shouldBe Vector(0L, 900000L, 1800000L, 2700000L)
      w.runner.record("sync").map(_.lastRun) shouldBe Some(w.start + 2700000L)
      w.runner.cancel("sync")
      w.pass(3600.0)
      w.runs.size shouldBe 4
    }
  }

  "Work kept in the settings" should {
    "run nothing until the app has started it" in {
      val w = new World
      w.runner.schedule(once("send"))
      w.pass(60.0)
      w.runs shouldBe empty
      w.runner.resume()
      w.pass(0.0)
      w.runs.size shouldBe 1
    }
    "outlast the app: once due while it was closed runs as it starts, and one not yet due when it is" in {
      val first = new World
      first.runner.resume()
      first.runner.schedule(once("due", delay = 60.0, data = Map("id" -> "a|b;c=d\\e")))
      first.runner.schedule(once("later", delay = 7200.0))
      // the app closed after ten seconds, and started again an hour later
      first.pass(10.0)
      val again = new World(first.settings, first.now + 3600000L)
      again.runner.keys shouldBe Vector("due", "later")
      again.runner.resume()
      again.pass(0.0)
      again.runs.map(r => (r._1, r._3)) shouldBe Vector("due" -> Map("id" -> "a|b;c=d\\e"))
      again.pass(3600.0 - 10.0 - 1.0)
      again.runs.size shouldBe 1
      again.pass(2.0)
      again.runs.map(_._1) shouldBe Vector("due", "later")
    }
    "catch up periodic work missed while the app was closed once, its next a period after" in {
      val first = new World
      first.runner.resume()
      first.runner.schedule(every("sync", 15))
      first.pass(1.0)
      first.runs.size shouldBe 1
      // closed for three hours: twelve periods missed
      val again = new World(first.settings, first.now + 3L * 3600000L)
      again.runner.resume()
      again.pass(14.0 * 60.0)
      again.ranAt shouldBe Vector(0L)
      again.pass(60.0)
      again.ranAt shouldBe Vector(0L, 900000L)
    }
  }

  "A work record" should {
    "be written and read back, whatever its key and data hold" in {
      val r = WorkRecord("k|e;y=\\", 15, WorkConstraints(WorkNetwork.Unmetered, charging = true, idle = true),
        WorkBackoff(exponential = false, seconds = 42.5), Map("a=b" -> "c;d|e", "" -> "", "x" -> "\\p"),
        1234567890123L, 99L, 3)
      WorkRecord.decode(r.encode) shouldBe Some(r)
      WorkRecord.decode(r.copy(data = Map.empty).encode) shouldBe Some(r.copy(data = Map.empty))
    }
    "be nothing where the text is not one" in {
      List("", "x", "a|b|c", "k|x|0|0|0|0|1|30.0||0|0|0", "k|0|9|0|0|0|1|30.0||0|0|0").map(WorkRecord.decode) shouldBe
        List.fill(5)(None)
    }
  }
}
