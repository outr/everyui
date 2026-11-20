package everyui.kit

import everyui.component.{Component, Container, MaterialIconView}
import everyui.font.MaterialIcon
import everyui.motion.{Keyframe, Track}
import everyui.perf.{Quality, QualityLevel}
import everyui.render.Host
import everyui.{FxSpec, ui}
import javafx.stage.Stage

/** A motion taken away mid-cycle leaves its node as the node has it, not where the motion stood: a busy Sync glyph
  * turned into a done tick mid-turn was drawn at the turn's last angle, an upside-down tick (Voidcraft, on Android).
  * The same for a scale, an opacity and a position, for a node whose own rotation, scale or opacity is not the
  * default, for a motion replaced by another, and for one stood still by `Quality` going to Minimal. Each is taken away
  * in the same turn of the FX thread that saw it mid-cycle. The window is shown (under Xvfb when the specs run), since
  * a scene in no shown window runs no motion. */
class MotionStillSpec extends FxSpec {
  private var stage: Stage = null

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      ui.children.clear()
      javafx.application.Platform.setImplicitExit(false)
      stage = new Stage()
      stage.setScene(Host.scene)
      stage.show()
    }
  }

  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      Quality.forget()
      Quality.start(Some(QualityLevel.Full))
      stage.hide()
      stage.setScene(null)
    }
    super.afterAll()
  }

  private val turn = Track(Track.Rotation, 1.3, List(Keyframe(0.0, 0.0), Keyframe(1.0, 2.0 * math.Pi)))
  private val grow = Track(Track.Scale, 1.3, List(Keyframe(0.0, 1.0, 1.0), Keyframe(1.0, 3.0, 2.0)))
  private val fade = Track(Track.Opacity, 1.3, List(Keyframe(0.0, 1.0), Keyframe(1.0, 0.0)))
  private val slide = Track(Track.Position, 1.3, List(Keyframe(0.0, 0.0, 0.0), Keyframe(1.0, 300.0, 200.0)))
  // 0.05 of a turn, the boxes' own rotation, in degrees
  private val own = 18.0

  /** How JavaFX draws a node now: its rotation (degrees), scale across and down, opacity, and translation. */
  private case class Drawn(rotate: Double, scaleX: Double, scaleY: Double, opacity: Double, x: Double, y: Double)
  private def drawn(c: Component): Drawn = {
    val n = c.node.fx
    Drawn(n.getRotate, n.getScaleX, n.getScaleY, n.getOpacity, n.getTranslateX, n.getTranslateY)
  }

  /** A box on the screen, `setup` giving it its own properties first. */
  private def box(setup: Container => Unit = _ => ()): Container = fx {
    ui.children.clear()
    val c = new Container {
      position.left @= 40.0
      position.top @= 60.0
      size.width @= 80.0
      size.height @= 80.0
    }
    setup(c)
    ui.children += c
    c
  }

  /** Once `c` is drawn `mid` its cycle, `change` in that same turn, and how it is drawn after: the change is made
    * mid-cycle, however busy the machine. */
  private def midway(c: Component, mid: Drawn => Boolean)(change: => Unit): Drawn = {
    val until = System.currentTimeMillis() + 20000L
    var after: Option[Drawn] = None
    while (after.isEmpty && System.currentTimeMillis() < until) {
      after = fx(if (mid(drawn(c))) { change; Some(drawn(c)) } else None)
      if (after.isEmpty) Thread.sleep(10L)
    }
    after.getOrElse(fail("never mid-cycle: " + fx(drawn(c))))
  }

  // well into a turn, away from its own rotation either way
  private def turned(d: Drawn): Boolean = math.abs(d.rotate - own) > 30.0 && math.abs(d.rotate - own) < 330.0

  "A motion taken away mid-cycle" should {
    "leave a busy Sync glyph turned into a done tick upright" in {
      val g = fx {
        ui.children.clear()
        val g = new MaterialIconView(MaterialIcon.Sync)
        g.position.left @= 40.0
        g.position.top @= 60.0
        ui.children += g
        g.busy @= true
        g
      }
      fx(g.motion()) shouldBe List(MaterialIconView.turn)
      // the work done, as an app says so: the tick, and no longer busy
      val after = midway(g, d => d.rotate > 30.0 && d.rotate < 330.0) {
        g.icon @= MaterialIcon.TaskAlt
        g.busy @= false
      }
      after.rotate shouldBe 0.0
      Thread.sleep(300L)
      fx(drawn(g).rotate) shouldBe 0.0
    }

    "put a rotation back to the node's own" in {
      val c = box(_.rotation @= 0.05)
      fx(c.motion @= List(turn))
      midway(c, turned)(c.motion @= Nil).rotate shouldBe own +- 1e-9
    }

    "put a scale back to the node's own" in {
      val c = box(_.scale @= 0.5)
      fx(c.motion @= List(grow))
      val after = midway(c, _.scaleX > 1.5)(c.motion @= Nil)
      (after.scaleX, after.scaleY) shouldBe (0.5, 0.5)
    }

    "put an opacity back to the node's own" in {
      val c = box(_.opacity @= 0.8)
      fx(c.motion @= List(fade))
      midway(c, _.opacity < 0.6)(c.motion @= Nil).opacity shouldBe 0.8
    }

    "put a position back to where the node is placed" in {
      val c = box()
      fx(c.motion @= List(slide))
      val after = midway(c, _.x > 60.0)(c.motion @= Nil)
      (after.x, after.y) shouldBe (0.0, 0.0)
      fx((c.node.fx.getLayoutX, c.node.fx.getLayoutY)) shouldBe (40.0, 60.0)
    }

    "put back what a motion moved as another replaces it, the other moving on" in {
      val c = box(_.rotation @= 0.05)
      fx(c.motion @= List(turn, grow))
      val swapped = midway(c, d => turned(d) && d.scaleX > 1.5)(c.motion @= List(fade))
      swapped.rotate shouldBe own +- 1e-9
      swapped.scaleX shouldBe 1.0
      val stopped = midway(c, _.opacity < 0.6)(c.motion @= Nil)
      stopped.rotate shouldBe own +- 1e-9
      (stopped.scaleX, stopped.opacity) shouldBe (1.0, 1.0)
    }

    "stand a node as its own while Quality is Minimal, and move it again after" in {
      val c = box { b =>
        b.rotation @= 0.05
        b.scale @= 0.5
        b.opacity @= 0.8
      }
      fx(c.motion @= List(turn, grow, fade, slide))
      try {
        val still = midway(c, d => turned(d) && d.scaleX > 1.2 && d.opacity < 0.9 && d.x > 30.0) {
          Quality.start(Some(QualityLevel.Minimal))
        }
        still.rotate shouldBe own +- 1e-9
        (still.scaleX, still.scaleY, still.opacity, still.x, still.y) shouldBe (0.5, 0.5, 0.8, 0.0, 0.0)
      }
      finally fx {
        Quality.forget()
        Quality.start(Some(QualityLevel.Full))
      }
      midway(c, turned)(c.motion @= Nil).rotate shouldBe own +- 1e-9
    }
  }
}
