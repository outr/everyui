package everyui.background

import everyui.{SettingsPage, SystemSettings}
import everyui.async.Task
import everyui.push.{NoticeAction, NoticeChannel, NoticeTap, Ongoing, OngoingNotice}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What runs with the app closed, as far as the desktop shows it: scheduled work checked before the platform sees it
  * (WorkRequest.problems, WorkBackoff's limits), the app's BackgroundApp made ready once and its handlers called with
  * what was delivered, an ongoing notice's actions cut to three, and the desktop's answers, which are all no: nothing
  * scheduled or pending, no ongoing notices, no settings pages. */
class BackgroundSpec extends AnyWordSpec with Matchers {
  private def request(key: String = "sync", every: Int = 0, constraints: WorkConstraints = WorkConstraints(),
                      expedited: Boolean = false, delay: Double = 0.0,
                      data: Map[String, String] = Map.empty): WorkRequest =
    WorkRequest(key, every, constraints, expedited, WorkBackoff(), delay, data, replace = true)

  private def answer[T](task: Task[T]): Option[T] = {
    var got: Option[T] = None
    task.map(t => got = Some(t)).start()
    got
  }

  /** A BackgroundApp that notes what it is asked. */
  private class Noting extends BackgroundApp {
    var prepared = 0
    var heard: Vector[String] = Vector.empty
    override def prepare(): Unit = prepared += 1
    override def onWork(key: String, data: Map[String, String]): Task[WorkResult] = Task {
      heard :+= "work " + key + " " + data.toList.sorted.mkString(",")
      if (key == "again") WorkResult.Retry else WorkResult.Done
    }
    override def onNoticeAction(notice: String, action: String, args: Map[String, String]): Task[Unit] = Task {
      heard :+= "action " + notice + " " + action + " " + args.toList.sorted.mkString(",")
    }
    override def onNoticeDismissed(notice: String): Task[Unit] = Task(heard :+= "dismissed " + notice)
    override def onRestart(reason: RestartReason): Task[Unit] = Task(heard :+= "restart " + reason.toString)
  }

  "Scheduled work" should {
    "be taken once or every so many minutes, with a network, data and a delay" in {
      request().problems shouldBe Nil
      request(every = 15).problems shouldBe Nil
      request(constraints = WorkConstraints(WorkNetwork.Unmetered, charging = true, batteryNotLow = true, idle = true),
        delay = 30.0, data = Map("id" -> "m1")).problems shouldBe Nil
      request(expedited = true, constraints = WorkConstraints(WorkNetwork.Connected)).problems shouldBe Nil
      request().periodic shouldBe false
      request(every = 60).periodic shouldBe true
    }
    "be refused with no key, or a key over a hundred characters" in {
      request(key = "").problems shouldBe List("work needs a key")
      request(key = "k" * 100).problems shouldBe Nil
      request(key = "k" * 101).problems.head should include("100 characters at most")
    }
    "be refused expedited where it would wait, or where it is periodic" in {
      val waits = List(WorkConstraints(charging = true), WorkConstraints(batteryNotLow = true),
        WorkConstraints(idle = true))
      waits.foreach(c => request(expedited = true, constraints = c).problems.head should include("cannot wait"))
      request(expedited = true, delay = 5.0).problems.head should include("cannot wait")
      request(expedited = true, every = 15).problems shouldBe List("work \"sync\": periodic work cannot be expedited")
    }
    "be refused with a delay below nothing, or more than 8192 characters of data" in {
      request(delay = -1.0).problems shouldBe List("work \"sync\": a delay is not negative")
      request(data = Map("k" -> "v" * 8191)).problems shouldBe Nil
      request(data = Map("k" -> "v" * 8192)).problems.head should include("8193 characters, more than 8192")
    }
    "back off between ten seconds and five hours" in {
      WorkBackoff().millis shouldBe 30000L
      WorkBackoff(seconds = 1.0).millis shouldBe 10000L
      WorkBackoff(exponential = false, seconds = 600.0).millis shouldBe 600000L
      WorkBackoff(seconds = 86400.0).millis shouldBe 18000000L
    }
    "be scheduled nowhere on the desktop, and never pending" in {
      Work.supported shouldBe false
      Work.once("sync") shouldBe false
      Work.once("") shouldBe false
      Work.every("sync", 5) shouldBe false
      Work.every("sync", 60, WorkConstraints(WorkNetwork.Unmetered, charging = true)) shouldBe false
      Work.pending("sync") shouldBe false
      Work.cancel("sync")
      Work.pending("sync") shouldBe false
    }
  }

  "The BackgroundApp" should {
    "be made ready once, and again only for another" in {
      Background.forget()
      val first = new Noting
      Background.start(first)
      Background.start(first)
      first.prepared shouldBe 1
      Background.app shouldBe Some(first)
      val second = new Noting
      Background.start(second)
      second.prepared shouldBe 1
      first.prepared shouldBe 1
      Background.app shouldBe Some(second)
      Background.forget()
    }
    "hear its work with its data, and answer for it" in {
      Background.forget()
      val app = new Noting
      Background.start(app)
      answer(Background.work("sync", Map("b" -> "2", "a" -> "1"))) shouldBe Some(WorkResult.Done)
      answer(Background.work("again", Map.empty)) shouldBe Some(WorkResult.Retry)
      app.heard shouldBe Vector("work sync (a,1),(b,2)", "work again ")
      Background.forget()
    }
    "hear a notice's action, its dismissal and each restart" in {
      Background.forget()
      val app = new Noting
      Background.start(app)
      answer(Background.noticeAction("status", "pause", Map("until" -> "18:00"))) shouldBe Some(())
      answer(Background.noticeDismissed("status")) shouldBe Some(())
      answer(Background.restart(RestartReason.Booted)) shouldBe Some(())
      answer(Background.restart(RestartReason.Updated)) shouldBe Some(())
      app.heard shouldBe Vector("action status pause (until,18:00)", "dismissed status", "restart Booted",
        "restart Updated")
      Background.forget()
    }
    "leave work failed, and the rest done, where there is none" in {
      Background.forget()
      Background.app shouldBe None
      answer(Background.work("sync", Map.empty)) shouldBe Some(WorkResult.Failed)
      answer(Background.noticeAction("status", "pause", Map.empty)) shouldBe Some(())
      answer(Background.restart(RestartReason.Booted)) shouldBe Some(())
    }
    "never run with the app closed on the desktop" in {
      Background.supported shouldBe false
    }
  }

  "An ongoing notice" should {
    val channel = NoticeChannel("status", "Status")
    val actions = Vector("Pause", "Open", "Stop", "More").map(l => NoticeAction(l, NoticeTap.RunAction(l.toLowerCase)))
    "show its first three actions" in {
      OngoingNotice("status", "Running", channel = channel, actions = actions).shownActions.map(_.label) shouldBe
        Vector("Pause", "Open", "Stop")
      OngoingNotice("status", "Running", channel = channel, actions = actions.take(1)).shownActions.size shouldBe 1
    }
    "be quiet unless its channel says otherwise" in {
      channel.importance shouldBe everyui.push.NoticeImportance.Low
    }
    "show nowhere on the desktop" in {
      Ongoing.supported shouldBe false
      Ongoing.show(OngoingNotice("status", "Running", "Since 9:12", channel, actions,
        Some(NoticeTap.OpenApp("/places")))) shouldBe false
      Ongoing.clear("status")
    }
  }

  "The settings pages" should {
    "open none on the desktop, and say so" in {
      SettingsPage.values.toList.map(p => answer(SystemSettings.open(p))) shouldBe
        List.fill(SettingsPage.values.length)(Some(false))
    }
    "have no unused-app restrictions on the desktop" in {
      answer(SystemSettings.unusedAppRestrictions) shouldBe Some(false)
    }
  }
}
