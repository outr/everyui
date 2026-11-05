package everyui.rich

import everyui.component.{Component, Container}
import everyui.text.TextArea
import everyui.{FxSpec, ui}

/** A suggestion where the document is edited as its Markdown (the desktop): offered by the Accept chip, which says what
  * it would put in; taken at the end as one change undo takes back; put away by typing or by the app. A draft set
  * with its caret at the end, as one change. */
class SuggestionSpec extends FxSpec {
  private def chipOf(e: RichTextEditor): Container =
    fx(e.children().collect { case c: Container if !c.isInstanceOf[TextArea] => c }.last)

  "A suggestion" should {
    "be offered by the chip, taken at the end as one change, and put away by typing or the app" in {
      val editor = fx {
        ui.children.clear()
        val e = new RichTextEditor(RichDoc.fromMarkdown("Thanks for the note."))
        ui.children += e
        e
      }
      try {
        val chip = chipOf(editor)
        fx(chip.visible()) shouldBe false
        var taken = List.empty[String]
        fx(editor.suggestionAccepted.attach(md => taken = md :: taken))
        fx(editor.suggestion @= Some("See you **Friday**."))
        fx(chip.visible()) shouldBe true
        fx(chip.focusable()) shouldBe true
        fx(chip.semantics().label) should include("See you Friday.")
        fx(editor.acceptSuggestion()) shouldBe true
        fx(editor.markdown) shouldBe "Thanks for the note.See you **Friday**."
        taken shouldBe List("See you **Friday**.")
        fx((editor.suggestion(), chip.visible())) shouldBe (None, false)
        fx(editor.undo())
        fx(editor.markdown) shouldBe "Thanks for the note."
        // typed: put away
        fx(editor.suggestion @= Some("Lunch?"))
        val area = fx(editor.children().collectFirst { case a: TextArea => a }.get)
        fx(area.value @= "Thanks for the note. x")
        fx(editor.suggestion()) shouldBe None
        // the app taking it back
        fx(editor.suggestion @= Some("Lunch?"))
        fx(editor.suggestion @= None)
        fx(chip.visible()) shouldBe false
        fx(editor.acceptSuggestion()) shouldBe false
      } finally fx(ui.children.clear())
    }
    "not be taken where the editor is read-only" in {
      val editor = fx(new RichTextEditor())
      fx {
        editor.readOnly @= true
        editor.suggestion @= Some("Lunch?")
      }
      fx(editor.acceptSuggestion()) shouldBe false
      fx(editor.markdown) shouldBe ""
    }
  }

  "A draft set with its caret at the end" should {
    "leave the caret after its last word, and be one change" in {
      val editor = fx(new RichTextEditor(RichDoc.fromMarkdown("Old")))
      fx(editor.setMarkdown("A draft to **go on** with", atEnd = true))
      fx(editor.selection()) shouldBe RichRange.at(RichPos(0, 21))
      fx(editor.undo())
      fx(editor.markdown) shouldBe "Old"
    }
  }
}
