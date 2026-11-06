package everyui.component

import everyui.event.{PointerEvent, PointerKind}
import everyui.font.MaterialIcon
import everyui.kit.Spinner
import everyui.{FxSpec, ui}

/** A busy button: a spinner in its glyph's place (before its words where it has none), made only once it is first
  * busy, a press doing nothing until it is set back, and a screen reader told it is busy. */
class ButtonBusySpec extends FxSpec {
  private def spinners(c: Component): List[Spinner] = c match {
    case s: Spinner => List(s)
    case k: Container => k.children().toList.flatMap(spinners)
    case _ => Nil
  }

  "A busy button" should {
    "turn a spinner in its glyph's place, take no press, and say it is busy" in {
      var pressed = 0
      val b = fx(new Button("Save", () => pressed += 1, glyph = Some(MaterialIcon.Save)))
      fx {
        ui.children.clear()
        ui.children += b
      }
      try {
        fx(spinners(b)) shouldBe Nil
        fx(b.busy @= true)
        fx(spinners(b).map(_.visible())) shouldBe List(true)
        fx(b.semantics().label) shouldBe "Save, " + everyui.i18n.Words.busy
        fx(b.updating()) shouldBe true
        fx(b.event.click @= PointerEvent(PointerKind.Click, 5.0, 5.0))
        pressed shouldBe 0
        fx(b.busy @= false)
        fx(spinners(b).map(_.visible())) shouldBe List(false)
        fx(b.semantics().label) shouldBe ""
        fx(b.event.click @= PointerEvent(PointerKind.Click, 5.0, 5.0))
        pressed shouldBe 1
      } finally fx(ui.children.clear())
    }
  }
}
