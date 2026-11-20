package everyui.kit

import everyui.component.Container
import everyui.motion.{Keyframe, Track}
import everyui.render.{Host, Motion}
import everyui.{FxSpec, ui}
import javafx.stage.Stage

/** A motion that takes its own node out of the window and brings it back goes round and round: NaboTV's falling leaves
  * go out past the edge of the screen at the end of each crossing and in again at the other, and nothing but the
  * motion's repeat brings one back. Android stopped such a motion as its node went out of sight; here it runs on while
  * the screen around it is laid out again and boxes are shown and hidden, which is when a platform looks again at what
  * can be seen. The window is shown (under Xvfb when the specs run), since a scene in no shown window runs no
  * motion. */
class OffScreenMotionSpec extends FxSpec {
  private var stage: Stage = null

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      ui.children.clear()
      javafx.application.Platform.setImplicitExit(false)
      stage = new Stage()
      stage.setScene(Host.scene)
      stage.setWidth(400.0)
      stage.setHeight(300.0)
      stage.show()
    }
  }

  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      stage.hide()
      stage.setScene(null)
    }
    super.afterAll()
  }

  "A motion that carries its node out of the window and back" should {
    "keep going round while the screen is laid out and boxes are shown and hidden" in {
      val before = fx(Motion.running)
      val leaf = fx(new Container {
        size.width @= 10.0
        size.height @= 10.0
        // in the window, out past its left side (keys close together, as a leaf's wrap), and in again
        motion @= List(Track(Track.Position, 1.0, List(Keyframe(0.0, 100.0, 100.0), Keyframe(0.4, 100.0, 100.0),
          Keyframe(0.4005, -5000.0, 100.0), Keyframe(0.7, -5000.0, -5000.0), Keyframe(0.7005, 100.0, 100.0),
          Keyframe(1.0, 100.0, 100.0))))
      })
      val other = fx(new Container {
        size.width @= 20.0
        size.height @= 20.0
      })
      fx {
        ui.children += leaf
        ui.children += other
      }
      def x: Double = leaf.node.fx.getTranslateX
      try {
        fx(Motion.running) shouldBe before + 1
        (1 to 3).foreach { round =>
          eventually(x < -1000.0) shouldBe true
          // looked at again while it is out: something else placed again, and a box hidden and shown
          fx {
            other.position.left @= round * 30.0
            other.visible @= false
            other.visible @= true
          }
          fx(Motion.running) shouldBe before + 1
          eventually(x > 0.0) shouldBe true
        }
      }
      finally fx {
        ui.children -= leaf
        ui.children -= other
      }
      fx(Motion.running) shouldBe before
    }
  }
}
