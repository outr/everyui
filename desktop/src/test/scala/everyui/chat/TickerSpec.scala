package everyui.chat

import everyui.component.{Container, OnScreen}
import everyui.focus.Focus
import everyui.list.{RichList, RichLook, RichSection}
import everyui.render.Platform
import everyui.{FxSpec, ui}
import reactify.*

/** A ticker moves on only while what owns it is on the screen and the app at the front: off the screen, it sets no
  * timer; back, it reads the clock at once and moves on from there, one timer at most however often it comes and goes.
  * Run on a clock and a timer the spec keeps (`Ticker.clock`, `Ticker.after`), so every tick is one the spec runs. */
class TickerSpec extends FxSpec {
  private val minute = 60000L

  // the time, and the ticks waiting, kept by the spec
  @volatile private var time = 1790000000000L
  @volatile private var waiting: Vector[() => Unit] = Vector.empty

  private def timed[T](test: => T): T = {
    time = 1790000000000L
    waiting = Vector.empty
    fx {
      Ticker.clock = () => time
      Ticker.after = (_, f) => waiting = waiting :+ f
    }
    try test finally fx {
      Ticker.clock = () => everyui.util.Clock.real()
      Ticker.after = (s, f) => Platform.after(s, f)
      ui.children.clear()
      Focus.focused @= None
    }
  }

  /** `ms` passed, and the ticks waiting run, each as its timer would: what they ask for runs at the next. */
  private def tick(ms: Long = 1000L): Unit = fx {
    time += ms
    val due = waiting
    waiting = Vector.empty
    due.foreach(f => f())
  }

  private def owner(): Container = fx {
    val c = new Container
    c.size.width @= 100.0
    c.size.height @= 100.0
    c
  }

  "A ticker with an owner" should {
    "move on while its owner is on the screen" in timed {
      val c = owner()
      val t = fx(Ticker.of(c))
      // made off the screen: nothing waits
      fx(waiting.size) shouldBe 0
      fx(ui.children += c)
      fx(t.now()) shouldBe time
      fx(waiting.size) shouldBe 1
      (1 to 3).foreach { _ =>
        tick()
        fx(t.now()) shouldBe time
        fx(waiting.size) shouldBe 1
      }
    }

    "set no timer while it is off the screen, and read the clock at once as it comes back" in timed {
      val c = owner()
      val t = fx(Ticker.of(c))
      fx(ui.children += c)
      tick()
      fx(ui.children -= c)
      val left = fx(t.now())
      // the tick already waiting comes, and changes nothing, and none is set after it
      tick()
      fx(t.now()) shouldBe left
      fx(waiting.size) shouldBe 0
      tick(10L * minute)
      fx(t.now()) shouldBe left
      // back after ten minutes: the clock read at once, once, and moving on from there
      var heard = 0
      fx(t.now.attach(_ => heard += 1))
      fx(ui.children += c)
      fx(t.now()) shouldBe time
      heard shouldBe 1
      fx(waiting.size) shouldBe 1
      tick()
      fx(t.now()) shouldBe time
      heard shouldBe 2
    }

    "keep one timer at most, however quickly its owner comes and goes" in timed {
      val c = owner()
      val t = fx(Ticker.of(c))
      (1 to 5).foreach { _ =>
        fx(ui.children += c)
        fx(waiting.size) shouldBe 1
        fx(ui.children -= c)
        fx(waiting.size) shouldBe 1
      }
      fx {
        ui.children += c
        ui.children -= c
        ui.children += c
      }
      fx(waiting.size) shouldBe 1
      tick()
      fx(t.now()) shouldBe time
      fx(waiting.size) shouldBe 1
      // gone and back between two ticks: still the one
      fx(ui.children -= c)
      tick()
      fx(waiting.size) shouldBe 0
      fx(ui.children += c)
      fx(ui.children -= c)
      fx(ui.children += c)
      fx(waiting.size) shouldBe 1
    }

    "stop for a container above it taken off the screen or hidden, and come back with it" in timed {
      val c = owner()
      val (outer, middle) = fx((new Container, new Container))
      fx {
        middle.children += c
        outer.children += middle
        ui.children += outer
      }
      val t = fx(Ticker.of(c))
      fx(waiting.size) shouldBe 1
      // a tab's content taken off as another shows: the container two up, not the owner
      fx(ui.children -= outer)
      tick()
      fx(waiting.size) shouldBe 0
      tick(5L * minute)
      fx(ui.children += outer)
      fx(t.now()) shouldBe time
      fx(waiting.size) shouldBe 1
      // hidden, then shown again
      fx(middle.visible @= false)
      tick()
      fx(waiting.size) shouldBe 0
      tick(minute)
      fx(middle.visible @= true)
      fx(t.now()) shouldBe time
      fx(waiting.size) shouldBe 1
      // moved from one container on the screen to another: on the screen throughout, the one timer
      val other = fx(new Container)
      fx(ui.children += other)
      fx(other.children += c)
      fx(waiting.size) shouldBe 1
      tick()
      fx(t.now()) shouldBe time
    }

    "stay stopped once stopped, and stop as its owner is disposed of" in timed {
      val c = owner()
      val t = fx(Ticker.of(c))
      fx(ui.children += c)
      fx(t.stop())
      val at = fx(t.now())
      tick()
      fx(waiting.size) shouldBe 0
      fx(ui.children -= c)
      fx(ui.children += c)
      fx(t.now()) shouldBe at
      fx(waiting.size) shouldBe 0
      val d = owner()
      val u = fx(Ticker.of(d))
      fx(ui.children += d)
      fx(d.dispose())
      tick()
      fx(waiting.size) shouldBe 0
      fx(u.ticking) shouldBe false
    }

    "rest while the app is away, and read the clock at once as it is back" in timed {
      val c = owner()
      val t = fx(Ticker.of(c))
      fx(ui.children += c)
      fx(Platform.away())
      val left = fx(t.now())
      tick()
      fx(t.now()) shouldBe left
      fx(waiting.size) shouldBe 0
      tick(30L * minute)
      fx(Platform.resumed())
      fx(t.now()) shouldBe time
      fx(waiting.size) shouldBe 1
      // away and back before the tick: still the one timer
      fx(Platform.away())
      fx(Platform.resumed())
      fx(waiting.size) shouldBe 1
      // what came onto the screen while the app was away moves on only as it is back
      fx(Platform.away())
      tick()
      val d = owner()
      val u = fx(Ticker.of(d))
      fx(ui.children += d)
      fx(waiting.size) shouldBe 0
      tick(minute)
      fx(Platform.resumed())
      fx(u.now()) shouldBe time
      fx(t.now()) shouldBe time
      fx(waiting.size) shouldBe 2
    }
  }

  "A ticker without an owner" should {
    "move on as it always did, until stopped" in timed {
      val t = fx(new Ticker(1.0))
      fx(waiting.size) shouldBe 1
      tick()
      fx(t.now()) shouldBe time
      fx(t.stop())
      tick()
      fx(waiting.size) shouldBe 0
    }
  }

  "What is on the screen" should {
    "be told as it changes, through every container it is in" in {
      val c = owner()
      val holder = fx(new Container)
      var told: List[Boolean] = Nil
      val on = fx(new OnScreen(c, b => told = told :+ b))
      fx(on()) shouldBe false
      fx(holder.children += c)
      fx(ui.children += holder)
      fx(on()) shouldBe true
      fx(c.visible @= false)
      fx(c.visible @= true)
      fx(ui.children -= holder)
      fx(told) shouldBe List(true, false, true, false)
      // listening to `ui` only while on the screen: off it, to nothing but the owner and what holds it
      def onUi: Int = fx(ui.parent.reactions().size + ui.visible.reactions().size)
      val off = onUi
      fx(ui.children += holder)
      onUi shouldBe off + 2
      fx(ui.children -= holder)
      onUi shouldBe off
      told = Nil
      fx(on.stop())
      fx(ui.children += holder)
      fx(told) shouldBe Nil
      fx(ui.children.clear())
    }
  }

  "A transcript and a rich list kept off the screen" should {
    "set no timer, without being disposed of, and say their times right as they come back" in timed {
      val ada = Participant("u-ada", "Ada")
      val entries = Var[Vector[ChatEntry]](Vector(ChatEntry.message("m1", ada, self = false, "Hello", time = Some(time))))
      val transcript = fx(new Transcript(entries))
      val list = fx(new RichList[String](Var(Vector(RichSection("s", "S", Vector("a")))), identity,
        (s: String) => RichLook(s, time = Some(time))))
      fx(waiting.size) shouldBe 0
      fx {
        ui.children += transcript
        ui.children += list
      }
      fx(waiting.size) shouldBe 2
      tick()
      fx(waiting.size) shouldBe 2
      fx(ui.children.clear())
      tick()
      fx(waiting.size) shouldBe 0
      tick(5L * minute)
      fx(waiting.size) shouldBe 0
      fx {
        ui.children += transcript
        ui.children += list
      }
      fx(transcript.ticker.now()) shouldBe time
      fx(list.now()) shouldBe time
      fx(waiting.size) shouldBe 2
      fx(ui.children.clear())
      fx {
        transcript.dispose()
        list.dispose()
      }
    }
  }
}
