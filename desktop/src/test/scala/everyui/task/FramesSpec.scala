package everyui.task

import everyui.component.{Container, TextView}
import everyui.focus.Focus
import everyui.{Color, FxSpec}
import reactify.*

import scala.concurrent.duration.*

/** The frame loop on demand (`Frames`), driven here a frame at a time by the spec rather than by JavaFX's pulses: it
  * idles once nothing needs a frame, and each thing that needs one wakes it for as long as it does. */
class FramesSpec extends FxSpec {
  /** The frames asked for and not yet run. */
  private var asked: Vector[() => Unit] = Vector.empty
  /** What each frame run was told: when what woke it asked, or -1. */
  private var told: Vector[Double] = Vector.empty
  private var idled = 0
  /** What the loop does on each frame, and whether it needs the next for itself. */
  private var onFrame: () => Unit = () => ()
  private var wanted: () => Boolean = () => false
  /** How a frame is asked for: kept to be run by `frames`, unless a case asks as a platform that fails to. */
  private var asking: (() => Unit) => Unit = f => asked = asked :+ f
  private val keep: (() => Unit) => Unit = f => asked = asked :+ f

  /** Run the frames asked for, each asking for the next if it wants one, until none is asked for or `most` have run:
    * how many ran. */
  private def frames(most: Int = 1000): Int = {
    var ran = 0
    while (asked.nonEmpty && ran < most) {
      val next = asked.head
      asked = asked.tail
      val before = told.size
      next()
      if (told.size > before) ran += 1
    }
    ran
  }

  /** A fresh loop, run until it idles. */
  private def fresh(): Unit = {
    onFrame = () => ()
    wanted = () => false
    asked = Vector.empty
    asking = keep
    Frames.drive(woke => {
      told = told :+ woke
      onFrame()
    }, () => wanted(), () => idled += 1, f => asking(f))
    frames()
    told = Vector.empty
  }

  /** `f` with the loop guarded as Android's is, giving up on a frame after `patience` ms; what it logs as an error
    * kept in `errors` and its warnings in `warnings`. */
  private var errors: Vector[String] = Vector.empty
  private var warnings: Vector[String] = Vector.empty
  private def guarded[T](patience: Double)(f: => T): T = {
    val forward = everyui.scribe.forward
    errors = Vector.empty
    warnings = Vector.empty
    everyui.scribe.forward = (level, message) => if (level == "error") errors = errors :+ message
      else warnings = warnings :+ message
    Frames.guarded = true
    Frames.patience = patience
    Frames.origin = () => "the spec's wake"
    try f
    finally {
      Frames.guarded = false
      Frames.patience = 1000.0
      Frames.origin = () => null
      everyui.scribe.forward = forward
    }
  }

  override def afterAll(): Unit = {
    // no loop left behind for the specs after: frames asked for go nowhere
    fx(Frames.drive(_ => (), () => false, () => (), _ => ()))
    super.afterAll()
  }

  "The frame loop" should {
    "idle once nothing needs a frame, and ask the platform for none" in fx {
      fresh()
      val before = idled
      Frames.running shouldBe false
      asked shouldBe empty
      frames() shouldBe 0
      idled shouldBe before
    }

    "wake for a component's change: the frame that draws it and the one after" in fx {
      fresh()
      val box = new Container
      box.backgroundColor @= Color.Red
      Frames.running shouldBe true
      frames() shouldBe 2
      Frames.running shouldBe false
      // the first frame is told when the change asked, the second that it was running already
      told.size shouldBe 2
      told(0) should be >= 0.0
      told(1) shouldBe -1.0
      // a size set, and a child added, wake it too
      box.size.width @= 40.0
      frames() shouldBe 2
      box.children += new Container
      frames() should be >= 2
    }

    "run two frames more after a change made in a frame" in fx {
      fresh()
      val box = new Container
      var changes = 3
      onFrame = () => if (changes > 0) {
        changes -= 1
        box.opacity @= changes.toDouble / 10.0
      }
      Frames.request()
      // the one asked for, then two after each of its three changes
      frames() shouldBe 5
    }

    "run the frames asked for, and every frame while held" in fx {
      fresh()
      Frames.request(3)
      frames() shouldBe 3
      val hold = Frames.hold()
      frames(50) shouldBe 50
      hold.release()
      frames() shouldBe 1
      Frames.running shouldBe false
      hold.release()
      frames() shouldBe 0
    }

    "run an animation's frames, and stop when it ends" in fx {
      fresh()
      val tasks = new TaskSupport {}
      val value = Var(0.0)
      wanted = () => tasks.needsFrames
      onFrame = () => tasks.update(1.0 / 60.0)
      value.to(1.0).in(100.millis).start(tasks)
      Frames.running shouldBe true
      val ran = frames()
      ran should (be >= 6 and be <= 8)
      value() shouldBe 1.0
      tasks.needsFrames shouldBe false
      Frames.running shouldBe false
    }

    "hold no frames for a paused animation, and run on once it plays" in fx {
      fresh()
      val tasks = new TaskSupport {}
      val value = Var(0.0)
      wanted = () => tasks.needsFrames
      onFrame = () => tasks.update(1.0 / 60.0)
      val running = value.to(1.0).in(1.second).start(tasks)
      frames(10) shouldBe 10
      running.pause()
      frames() should be <= 1
      Frames.running shouldBe false
      running.play()
      Frames.running shouldBe true
      frames() should be > 40
      value() shouldBe 1.0
    }

    "run every frame while something listens to a delta" in fx {
      fresh()
      val tasks = new TaskSupport {}
      wanted = () => tasks.needsFrames
      onFrame = () => tasks.update(1.0 / 60.0)
      var heard = 0
      val listening = tasks.delta.attach(_ => heard += 1)
      Frames.request()
      frames(30) shouldBe 30
      heard shouldBe 30
      tasks.delta.reactions -= listening
      frames() shouldBe 1
      Frames.running shouldBe false
    }

    "wake when somebody uses the app" in fx {
      fresh()
      Focus.used()
      Frames.running shouldBe true
      frames() shouldBe 2
      Focus.key("x", false)
      frames() shouldBe 2
    }

    "wake for words changed, as a binding to a live value changes them" in fx {
      fresh()
      val count = Var(1)
      val words = TextView.bound("Count: " + count())
      frames()
      count @= 2
      words.content() shouldBe "Count: 2"
      frames() shouldBe 2
    }

    "count the frames it runs, and those run without idling since it last idled" in fx {
      fresh()
      val before = Frames.stepped
      Frames.request(3)
      frames(2) shouldBe 2
      Frames.streak shouldBe 2
      frames() shouldBe 1
      Frames.streak shouldBe 0
      Frames.stepped shouldBe before + 3
      val hold = Frames.hold()
      frames(100)
      Frames.streak shouldBe 100
      Frames.held shouldBe 1
      hold.release()
      frames()
      Frames.streak shouldBe 0
      Frames.held shouldBe 0
      Frames.owed shouldBe 0
      Frames.wants shouldBe false
    }

    "tell a watcher of each wake, request and hold, and only while it is watched" in fx {
      fresh()
      var heard = Vector.empty[String]
      Frames.watcher = kind => heard = heard :+ kind
      try {
        Frames.request()
        frames()
        heard shouldBe empty
        Frames.watched = true
        Frames.request()
        new Container().backgroundColor @= Color.Red
        val hold = Frames.hold()
        hold.release()
        frames()
        heard shouldBe Vector("request", "wake", "hold")
      } finally {
        Frames.watched = false
        Frames.watcher = _ => ()
      }
    }

    // NaboTV on a SHIELD: a card changed on the socket's thread woke the idle loop there, where Android's Choreographer
    // threw (no Looper), and the loop was left running with no frame asked for: nothing that needs frames moved again
    "idle, to be woken again, when the next frame cannot be asked for" in fx {
      fresh()
      asking = _ => throw new IllegalStateException("The current thread must have a looper!")
      Frames.wake()
      Frames.running shouldBe false
      asking = keep
      Frames.wake()
      Frames.running shouldBe true
      frames() shouldBe 2
    }

    "go on after a frame that fails, logging it" in fx {
      fresh()
      val before = Frames.failed
      onFrame = () => {
        onFrame = () => ()
        throw new IllegalStateException("a frame failed")
      }
      Frames.request(3)
      frames() shouldBe 3
      Frames.failed shouldBe before + 1
      Frames.running shouldBe false
      Frames.request(2)
      frames() shouldBe 2
    }

    "ask again, once guarded, for a frame asked for that never came" in fx {
      guarded(20.0) {
        fresh()
        val before = Frames.lost
        // posted to a thread's Choreographer that never runs it
        asking = _ => ()
        Frames.wake()
        asking = keep
        // not yet overdue: waited for
        Frames.wake()
        asked shouldBe empty
        Thread.sleep(40L)
        Frames.wake()
        Frames.lost shouldBe before + 1
        frames() shouldBe 2
        Frames.running shouldBe false
        errors.size shouldBe 1
        errors.head should include("never came")
        errors.head should include("on waking, from the spec's wake")
      }
    }

    "say so when a frame given up on comes after all, the thread having been held up" in fx {
      guarded(20.0) {
        fresh()
        val before = Frames.lost
        Frames.wake()
        // the frame asked for, kept from running for longer than the loop waits, as a long build on the thread would
        Thread.sleep(40L)
        Frames.wake()
        Frames.lost shouldBe before + 1
        // that one, and the one asked in its place, which runs the loop on: two frames, no more
        frames() shouldBe 2
        Frames.lost shouldBe before
        errors.size shouldBe 1
        warnings.size shouldBe 1
        warnings.head should include("came after all")
        Frames.running shouldBe false
      }
    }

    "not give up on a frame while one runs, however long it takes" in fx {
      guarded(20.0) {
        fresh()
        val before = Frames.lost
        val box = new Container
        onFrame = () => {
          onFrame = () => ()
          Thread.sleep(40L)
          box.opacity @= 0.5
        }
        Frames.request()
        // the one asked for and the two after the change made in it, and no more
        frames() shouldBe 3
        Frames.lost shouldBe before
      }
    }

    "run a frame on resuming, and run a loop left waiting on a frame that never came" in fx {
      guarded(20.0) {
        fresh()
        Frames.resumed()
        frames() shouldBe 1
        asking = _ => ()
        Frames.wake()
        Thread.sleep(40L)
        asking = keep
        Frames.resumed()
        frames() should be >= 1
        Frames.running shouldBe false
      }
    }

    "run a frame woken on another thread on the loop's own, where the platform hands it there" in {
      var ranOn = ""
      fx {
        fresh()
        onFrame = () => ranOn = Thread.currentThread.getName
        // as Android's does: on the thread that draws, whichever thread asks
        asking = f =>
          if (javafx.application.Platform.isFxApplicationThread) keep(f)
          else javafx.application.Platform.runLater(() => keep(f))
      }
      val socket = new Thread(() => new Container().backgroundColor @= Color.Red, "socket")
      socket.start()
      socket.join()
      eventually(asked.nonEmpty) shouldBe true
      fx(frames()) shouldBe 2
      ranOn shouldBe fx(Thread.currentThread.getName)
      fx(Frames.running) shouldBe false
    }
  }
}
