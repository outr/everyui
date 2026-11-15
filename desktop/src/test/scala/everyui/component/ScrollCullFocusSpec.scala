package everyui.component

import everyui.focus.Focus
import everyui.layout.VerticalLayout
import everyui.{FxSpec, ui}

/** A scroll culls what is out of its view, but never what holds the focus: a child the focus is in stays drawn as
  * the scroll moves away from it, and one out of view the focus moves into is drawn at once and brought into view; the
  * place kept as it goes, and culled again once the focus leaves it. */
class ScrollCullFocusSpec extends FxSpec {
  private def row(): Container = new Container {
    size.width @= 300.0
    size.height @= 50.0
    focusable @= true
  }

  "A ScrollContainer" should {
    "keep drawn what holds the focus, and draw what the focus moves into" in {
      val (scroll, rows) = fx {
        val rows = Vector.fill(40)(row())
        val scroll = new ScrollContainer {
          size.width @= 300.0
          size.height @= 200.0
          content.layout @= Some(VerticalLayout(0.0))
        }
        scroll.content.children := rows
        ui.children.clear()
        ui.children += scroll
        (scroll, rows)
      }
      try {
        fx(rows(1).visible()) shouldBe true
        fx(rows(30).visible()) shouldBe false
        fx(rows(1).focus())
        fx(scroll.scrollTo(1400.0))
        // far from the view, and still drawn: it has the focus
        fx(rows(1).visible()) shouldBe true
        fx(rows(5).visible()) shouldBe false
        // the focus onto one out of view: drawn, and brought into it
        fx(scroll.scrollTo(0.0))
        fx(rows(35).visible()) shouldBe false
        fx(rows(35).focus())
        fx(rows(35).visible()) shouldBe true
        fx(scroll.offset() > 1000.0) shouldBe true
        // the one it left is culled again
        fx(rows(1).visible()) shouldBe false
        fx(Focus.focused @= None)
        fx(scroll.scrollTo(0.0))
        fx(rows(35).visible()) shouldBe false
      } finally fx {
        Focus.focused @= None
        ui.children.clear()
        scroll.dispose()
      }
    }
  }
}
