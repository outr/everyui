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
    Frames.drive(woke => {
      told = told :+ woke
      onFrame()
    }, () => wanted(), () => idled += 1, f => asked = asked :+ f)
    frames()
    told = Vector.empty
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
  }
}
