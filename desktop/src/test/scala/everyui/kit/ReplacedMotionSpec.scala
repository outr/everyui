package everyui.kit

import everyui.component.{Container, ScrollContainer}
import everyui.layout.VerticalLayout
import everyui.motion.{Keyframe, Track}
import everyui.render.{Host, Motion}
import everyui.{FxSpec, ui}
import javafx.stage.Stage

/** A child dropped from a container by replacing its children (`children @= parts`) leaves it: it has no parent, and
  * whatever moves inside it stops, without its being disposed of. The shape is a settings page's: a loader in the
  * middle of a box, in a column laid out top to bottom, in a scroll's content, replaced by the page's rows once they
  * come. */
class ReplacedMotionSpec extends FxSpec {
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
      stage.hide()
      stage.setScene(null)
    }
    super.afterAll()
  }

  /** A loader: a frame fading in over an hour, placed by its box's size, and a bar sliding round it. */
  private def loader(): Container = {
    val fading = Track(Track.Opacity, 3600.0, List(Keyframe(0.0, 0.0), Keyframe(0.5 / 3600.0, 1.0), Keyframe(1.0, 1.0)))
    val bar = new Container {
      size.width @= 40.0
      size.height @= 4.0
      motion @= List(Track(Track.Position, 1.6, List(Keyframe(0.0, 0.0, 0.0), Keyframe(1.0, 200.0, 0.0))))
    }
    val box = new Container {
      size.width @= 300.0
      size.height @= 176.0
    }
    val frame = new Container {
      size.width @= 300.0
      size.height @= 176.0
      motion @= List(fading)
      children += bar
    }
    frame.position.left := (box.size.width() - 300.0) / 2.0
    box.children += frame
    new Container {
      size.width @= 600.0
      size.height @= 240.0
      children += box
    }
  }

  private def row(): Container = new Container {
    size.width @= 600.0
    size.height @= 48.0
  }

  "A child dropped by replacing a container's children" should {
    "leave it, and stop what moves inside it" in {
      val before = fx(Motion.running)
      val scroll = fx(new ScrollContainer)
      val column = fx(new Container {
        layout @= Some(VerticalLayout(0.0))
      })
      val waiting = fx(loader())
      fx {
        scroll.size.width @= 800.0
        scroll.size.height @= 600.0
        column.size.width @= 600.0
        scroll.content.children += column
        ui.children += scroll
        column.children @= Vector(row(), waiting)
      }
      try {
        fx(Motion.running) shouldBe before + 2
        // the page's rows come, and the loader is not among them
        fx(column.children @= Vector(row(), row(), row()))
        fx(waiting.parent()) shouldBe None
        fx(Motion.running) shouldBe before
        // and again, as a page drawn once more for each part that comes
        val again = fx(loader())
        fx(column.children @= Vector(row(), again))
        fx(Motion.running) shouldBe before + 2
        fx(column.children @= Vector(row(), row()))
        fx(again.parent()) shouldBe None
        fx(Motion.running) shouldBe before
      }
      finally fx(ui.children -= scroll)
      fx(Motion.running) shouldBe before
    }

    "leave it when its change is heard late, made deep in a chain of changes, after a later one made nearer the top" in {
      // a change made past the depth a chain may nest to is heard once the chain unwinds, and one made nearer the top
      // in the same turn is heard before it; each depth up to past that budget, so wherever it lies
      (1 to 12).foreach { depth =>
        val before = fx(Motion.running)
        val column = fx(new Container)
        val waiting = fx(loader())
        fx {
          ui.children += column
          column.children @= Vector(row(), waiting)
        }
        try {
          fx(Motion.running) shouldBe before + 2
          val start = reactify.Var(0)
          val chain = (1 to depth).map(_ => reactify.Var(0)).toList
          (start :: chain).zip(chain).foreach { case (a, b) => a.attach(n => b @= n) }
          val later = fx(loader())
          chain.last.attach(_ => column.children @= Vector(row(), later))
          start.attach(_ => column.children @= Vector(row(), row()))
          fx(start @= 1)
          withClue("depth " + depth.toString + ": ") {
            fx(waiting.parent()) shouldBe None
            fx(waiting.node.fx.getParent) shouldBe null
            // the nodes are the list's, whichever change it ended on
            val children = fx(column.children())
            fx(column.node.fx.asInstanceOf[javafx.scene.Parent].getChildrenUnmodifiable.size) shouldBe children.size
            children.foreach(c => fx(c.parent()) shouldBe Some(column))
            fx(Motion.running) shouldBe before + (if (children.contains(later)) 2 else 0)
          }
        }
        finally fx(ui.children -= column)
        fx(Motion.running) shouldBe before
      }
    }
  }
}
