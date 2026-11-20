package everyui.component

import everyui.layout.VerticalLayout
import everyui.{FxSpec, ui}

/** A container taken apart is let go of quietly: itself first, so its children leave a container already taken apart,
  * and nothing is measured, laid out or sized again for them, nor anything it is in. Taken apart after its children, as
  * `Component.disposeGradually` takes them (the deepest first), each child's going shrank it and laid out every
  * container still holding it: closing a dialog of about 25 components on a 2017 TV (Chromium 38) took 5 turns of 12 ms
  * slices. Each is taken apart all the same: its node out of the container's, its parent gone, no longer counted. */
class DisposeQuietSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    offStage()
    super.afterAll()
  }

  /** A column of `n` lines, sized to them. */
  private def column(n: Int): Container = new Container {
    layout @= Some(VerticalLayout(4.0))
    children ++= (1 to n).map(i => TextView("Line " + i.toString))
  }

  "A container taken apart" should {
    "leave what it is in as it was, its children taken apart, deepest first" in {
      val inner = fx(column(3))
      val below = fx(TextView("Below"))
      val outer = fx(new Container {
        layout @= Some(VerticalLayout(8.0))
        children ++= List(inner, below)
      })
      fx(ui.children += outer)
      fx(())
      val (height, at) = fx((outer.size.height(), below.position.top()))
      height should be > 0.0
      val lines = fx(inner.children())
      val before = fx(Component.liveNow)
      var heard = 0
      fx(outer.size.height.attach(_ => heard += 1))
      // as disposeGradually takes them: the lines, then their column
      fx {
        lines.foreach(_.dispose())
        inner.dispose()
        inner.layoutPending shouldBe false
      }
      fx(())
      fx((outer.size.height(), below.position.top(), heard)) shouldBe ((height, at, 0))
      fx(Component.liveNow) shouldBe before - 4
      fx(inner.children()) shouldBe empty
      lines.foreach { line =>
        line.takenApart shouldBe true
        fx(line.parent()) shouldBe None
      }
      lines.foreach(line => fx(line.node.fx.getParent) shouldBe null)
      fx(ui.children -= outer)
      fx(outer.dispose())
    }
    "take apart what is under it with it, taken apart first" in {
      val inner = fx(column(2))
      val outer = fx(new Container {
        children += inner
      })
      fx(ui.children += outer)
      fx(())
      val before = fx(Component.liveNow)
      fx(ui.children -= outer)
      fx(outer.dispose())
      fx(Component.liveNow) shouldBe before - 4
      fx(outer.children()) shouldBe empty
      inner.takenApart shouldBe true
      fx(inner.parent()) shouldBe None
    }
  }
}
