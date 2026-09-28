package everyui.layout

import everyui.component.*
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.kit.Mirror
import everyui.{FxSpec, ui}
import reactify.*

/** A column's lines start where the language does: at the left, or at the right in a right-to-left language. */
class ColumnSpec extends FxSpec {
  private def box(w: Double): Container = new Container {
    size.width @= w
    size.height @= 20.0
  }

  /** `test` with the page read right to left, back to left to right after. */
  private def rtl[T](test: => T): T = {
    fx(Direction.set(true))
    try test finally fx(Direction.set(false))
  }

  private def lefts(c: Container): List[Double] = fx(c.children().toList.map(_.position.left()))

  "A column" should {
    "put its lines at the right of its own width in a right-to-left language" in {
      def column(): Container = fx(new Container {
        size.width @= 300.0
        padding @= 10.0
        layout @= Some(VerticalLayout(4.0))
        children ++= List(box(100.0), box(200.0))
      })
      lefts(column()) shouldBe List(10.0, 10.0)
      rtl(lefts(column())) shouldBe List(190.0, 90.0)
    }
    "put its lines at the right of its widest when it follows them" in {
      def column(): Container = fx(Column(4.0, padding = 10.0)(box(100.0), box(200.0)))
      lefts(column()) shouldBe List(10.0, 10.0)
      rtl {
        val c = column()
        lefts(c) shouldBe List(110.0, 10.0)
        fx(c.size.width()) shouldBe 220.0
      }
    }
    "leave centred lines where they are" in {
      def column(): Container = fx(Column(4.0, center = true)(box(100.0), box(200.0)))
      lefts(column()) shouldBe List(50.0, 0.0)
      rtl(lefts(column())) shouldBe List(50.0, 0.0)
    }
    "leave a line as wide as it where it is" in {
      val strip = fx(box(80.0))
      def column(): Container = fx(new Container {
        size.width @= 300.0
        layout @= Some(VerticalLayout(0.0))
        children ++= List(box(300.0), Mirror.start(strip, () => 300.0))
      })
      lefts(column()) shouldBe List(0.0, 0.0)
      fx(strip.position.left()) shouldBe 0.0
      rtl {
        lefts(column()) shouldBe List(0.0, 0.0)
        // mirrored once, by the strip, not again by the column
        fx(strip.position.left()) shouldBe 220.0
      }
    }
    "mirror again when the reading direction changes" in {
      val column = fx(Column(4.0)(box(100.0), box(200.0)))
      fx {
        ui.children.clear()
        Focus.focused @= None
        ui.children += column
      }
      try {
        lefts(column) shouldBe List(0.0, 0.0)
        rtl(lefts(column) shouldBe List(100.0, 0.0))
        lefts(column) shouldBe List(0.0, 0.0)
      }
      finally fx(ui.children.clear())
    }
  }
}
