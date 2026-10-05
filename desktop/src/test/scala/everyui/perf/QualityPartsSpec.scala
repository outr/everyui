package everyui.perf

import everyui.component.{Component, Container, ScrollContainer, TextView}
import everyui.list.VirtualList
import everyui.motion.{Keyframe, Track}
import everyui.render.RenderNode
import everyui.task.*
import everyui.{Color, FxSpec, Shadow, ui}
import javafx.scene.layout.Pane
import reactify.*

import scala.concurrent.duration.*

/** EveryUI's own parts follow the quality level: an animation takes half as long at Reduced and a quarter at Minimal
  * while a pause takes as long as it did, a scroll's glide is half as long and then a jump, a box's shadow and blur go
  * from Reduced and a text's shadow and the platform's motion at Minimal (what is already drawn with them too), and a
  * list builds fewer rows beyond what shows. */
class QualityPartsSpec extends FxSpec {
  private def at(level: QualityLevel): Unit = fx {
    Quality.forget()
    if (level != QualityLevel.Full) Quality.start(Some(level))
  }

  /** How many seconds of 1/60s frames `task` takes to finish. */
  private def lasts(task: Animation): Double = fx {
    var t = 0.0
    var first = true
    var done = false
    while (!done && t < 10.0) {
      done = task.update(1.0 / 60.0, first) == Conclusion.Finished
      first = false
      t += 1.0 / 60.0
    }
    t
  }

  private def shown[T](c: Component)(test: => T): T = {
    fx(ui.children += c)
    try test finally fx(ui.children.clear())
  }

  "An animation" should {
    "take its time at Full, half at Reduced and a quarter at Minimal, while a pause takes its own" in {
      val x = Var(0.0)
      def move: Animation = x.to(100.0).in(400.millis)
      at(QualityLevel.Full)
      lasts(move) shouldBe 0.4 +- 0.02
      lasts(new Sleep(400.millis)) shouldBe 0.4 +- 0.02
      at(QualityLevel.Reduced)
      lasts(move) shouldBe 0.2 +- 0.02
      fx(x()) shouldBe 100.0
      lasts(new Sleep(400.millis)) shouldBe 0.4 +- 0.02
      at(QualityLevel.Minimal)
      lasts(move) shouldBe 0.1 +- 0.02
      lasts(new Sleep(400.millis)) shouldBe 0.4 +- 0.02
      at(QualityLevel.Full)
    }
  }

  "A scroll's glide" should {
    def scroll(): ScrollContainer = fx {
      val s = new ScrollContainer
      s.size.width @= 200.0
      s.size.height @= 200.0
      val tall = new Container
      tall.size.width @= 200.0
      tall.size.height @= 2000.0
      s.content.children += tall
      s
    }
    /** Where the glide to 600 over 0.3s has got after `seconds` of frames. */
    def glided(level: QualityLevel, seconds: Double): Double = {
      at(level)
      val s = scroll()
      shown(s) {
        fx(s.glideTo(600.0, 0.3))
        val stepped = fx(s.offset())
        var t = 0.0
        while (t < seconds - 1e-9) {
          fx(ui.update(1.0 / 60.0))
          t += 1.0 / 60.0
        }
        if (seconds == 0.0) stepped else fx(s.offset())
      }
    }
    "take its time at Full, half of it at Reduced, and jump at Minimal" in {
      glided(QualityLevel.Full, 0.0) shouldBe 0.0
      glided(QualityLevel.Full, 0.16) should be < 600.0
      glided(QualityLevel.Full, 0.32) shouldBe 600.0
      glided(QualityLevel.Reduced, 0.16) shouldBe 600.0
      glided(QualityLevel.Minimal, 0.0) shouldBe 600.0
      at(QualityLevel.Full)
    }
  }

  "Shadows, blur and motion" should {
    "be drawn at Full, the box's shadow and blur gone from Reduced, the text's shadow and the motion at Minimal" in {
      var moving: List[Track] = Nil
      val box = fx {
        new Component(new RenderNode(new Pane) {
          override def setMotion(tracks: List[Track]): Unit = {
            moving = tracks
            super.setMotion(tracks)
          }
        })
      }
      val glow = Some(Shadow(0.0, 2.0, 8.0, Color.Black))
      val turn = List(Track(Track.Rotation, 2.0, List(Keyframe(0.0, 0.0), Keyframe(1.0, 6.28))))
      var lettered = ""
      at(QualityLevel.Full)
      val words = fx {
        new TextView(new RenderNode(new javafx.scene.text.Text) {
          override def setTextShadow(x: Double, y: Double, blur: Double, hex: String): Unit = lettered = hex
        })
      }
      val holder = fx(new Container)
      fx(holder.children ++= List(box, words))
      shown(holder) {
        fx {
          box.shadow @= glow
          box.motion @= turn
          words.textShadow @= glow
        }
        fx(box.node.fx.getEffect) shouldBe a[javafx.scene.effect.DropShadow]
        lettered should not be empty
        moving shouldBe turn
        // stepped down with all of them drawn: the box's shadow goes, the words keep theirs, it still moves
        fx(Quality.start(Some(QualityLevel.Reduced)))
        fx(box.node.fx.getEffect) shouldBe null
        lettered should not be empty
        moving shouldBe turn
        fx(box.blur @= 4.0)
        fx(box.node.fx.getEffect) shouldBe null
        fx(box.shadow()) shouldBe glow
        fx(Quality.start(Some(QualityLevel.Minimal)))
        lettered shouldBe empty
        moving shouldBe Nil
        fx(box.motion()) shouldBe turn
        // and back at Full (a new session) they are drawn as they were set
        at(QualityLevel.Full)
        fx(box.node.fx.getEffect) shouldBe a[javafx.scene.effect.GaussianBlur]
        lettered should not be empty
        moving shouldBe turn
      }
    }
    "draw what was made before a change of level as the level has it once it is put on the screen" in {
      at(QualityLevel.Full)
      val card = fx {
        val c = new Container
        c.shadow @= Some(Shadow(0.0, 2.0, 8.0, Color.Black))
        c
      }
      val holder = fx(new Container)
      fx(holder.children += card)
      fx(card.node.fx.getEffect) shouldBe a[javafx.scene.effect.DropShadow]
      fx(Quality.start(Some(QualityLevel.Reduced)))
      // not on the screen as the level changed, so drawn as it was
      fx(card.node.fx.getEffect) shouldBe a[javafx.scene.effect.DropShadow]
      shown(holder) {
        fx(card.node.fx.getEffect) shouldBe null
        // and one made since is drawn at the level from the start
        val later = fx {
          val c = new Container
          c.shadow @= Some(Shadow(0.0, 2.0, 8.0, Color.Black))
          c
        }
        fx(later.node.fx.getEffect) shouldBe null
      }
      at(QualityLevel.Full)
    }
  }

  "A virtual list" should {
    "build four rows beyond what shows at Full, two at Reduced and one at Minimal" in {
      def made(level: QualityLevel): Int = {
        at(level)
        val list = fx(VirtualList.text[String](Var((0 until 500).map(i => "item " + i).toVector), s => s, s => s))
        shown(list) {
          fx(list.size.height @= 280.0)
          fx(list.scrollTo(200))
          fx((0 until 500).count(i => list.rowAt(i).nonEmpty))
        }
      }
      val full = made(QualityLevel.Full)
      made(QualityLevel.Reduced) shouldBe full - 4
      made(QualityLevel.Minimal) shouldBe full - 6
      at(QualityLevel.Full)
      Quality.overscan(4) shouldBe 4
      at(QualityLevel.Reduced)
      Quality.overscan(4) shouldBe 2
      at(QualityLevel.Minimal)
      Quality.overscan(4) shouldBe 1
      at(QualityLevel.Full)
    }
  }

  override def afterAll(): Unit = {
    fx(Quality.forget())
    super.afterAll()
  }
}
