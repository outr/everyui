package everyui.dialog

import everyui.component.{Column, Component, Modal, Select}
import everyui.focus.Focus
import everyui.text.TextArea
import everyui.{FxSpec, ui}

/** A dialog holding parts given widths of their own (a box of lines 520 wide, a select 520 wide): as wide as they need
  * where the screen has room, each whole inside it, nothing cut at its edge; and on a phone, no wider than the screen,
  * the parts made as wide as the room it has. A select is drawn in a box of its own. */
class DialogFitSpec extends FxSpec {
  private def settle(): Unit = (1 to 6).foreach(_ => fx(()))

  private def opened(screen: Double)(test: (DialogPanel, TextArea, Select) => Unit): Unit = {
    val was = fx(ui.size.width())
    fx(ui.size.width @= screen)
    try {
      val (panel, area, select) = fx {
        val select = new Select(List("Continue with Helix", "Continue with Scout"))
        select.size.width @= 520.0
        select.size.height @= 40.0
        val area = new TextArea("Paste a chat: \"User:\" and \"Assistant:\" (or names) before each turn", width = 520.0,
          minHeight = 180.0)
        val panel = new DialogPanel("Import a chat", List(Column(10.0)(select, area)), List(DialogAction("Import", () => ())))
        panel.open()
        (panel, area, select)
      }
      settle()
      test(panel, area, select)
    } finally fx {
      while (Modal.showing) Modal.close()
      ui.size.width @= was
      Focus.focused @= None
    }
  }

  // where `c` ends against the panel's right edge, its padding inside it
  private def inside(panel: DialogPanel, c: Component): Boolean = fx {
    c.absoluteLeft >= panel.absoluteLeft && c.absoluteLeft + c.size.width() <= panel.absoluteLeft + panel.size.width() - 8.0
  }

  "A dialog of parts given widths of their own" should {
    "be as wide as they need, each whole inside it, where the screen has room" in opened(1600.0) { (panel, area, select) =>
      fx(panel.size.width()) should be > 520.0
      fx(area.size.width()) shouldBe 520.0
      fx(select.size.width()) shouldBe 520.0
      inside(panel, area) shouldBe true
      inside(panel, select) shouldBe true
      // a select in a box of its own
      fx(select.borderWidth()) shouldBe 1.0
    }
    "be no wider than a phone's screen, its parts made as wide as the room" in opened(390.0) { (panel, area, select) =>
      fx(panel.size.width()) should be <= 390.0 - 32.0 + 0.5
      fx(area.size.width()) should be < 520.0
      inside(panel, area) shouldBe true
      inside(panel, select) shouldBe true
    }
  }
}
