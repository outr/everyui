package everyui.chat

import everyui.focus.Focus
import everyui.{FxSpec, ui}
import javafx.scene.control.TextInputControl

/** Emoji by name in a composer (`emojiShortcodes`), typed into the desktop's real field: a name between colons becomes
  * its emoji as the closing colon is typed, the caret after it; Backspace just after gives the name back; a colon that
  * closes no name, or one in a time, is left; the emoji whose names start with what follows a colon are listed after two
  * letters, and the one chosen takes the name's place; and off, nothing is turned or listed. */
class ComposerEmojiSpec extends FxSpec {
  private def make(): Composer = fx {
    val c = new Composer(_ => (), touch = false)
    ui.children.clear()
    Focus.focused @= None
    ui.children += c
    c.focusField()
    c
  }

  private def control(c: Composer): TextInputControl = c.field.field.get.node.fx.asInstanceOf[TextInputControl]
  private def typed(c: Composer, words: String): Unit = words.foreach(ch => fx(control(c).replaceSelection(ch.toString)))
  private def settle(): Unit = (1 to 4).foreach(_ => fx(()))
  private def popup(): Option[CompletionPopup] = fx(ui.children().collectFirst { case p: CompletionPopup => p })

  "A composer's emoji names" should {
    "become the emoji as the closing colon is typed, and the name again on Backspace just after" in {
      val c = make()
      typed(c, "Shipped :tada")
      fx(c.text()) shouldBe "Shipped :tada"
      typed(c, ":")
      settle()
      fx(c.text()) shouldBe "Shipped 🎉"
      fx(control(c).getCaretPosition) shouldBe "Shipped 🎉".length
      typed(c, " ok")
      fx(c.text()) shouldBe "Shipped 🎉 ok"
      // Backspace just after one: its name back
      fx(c.text @= "")
      typed(c, ":+1:")
      settle()
      fx(c.text()) shouldBe "👍"
      fx(control(c).deletePreviousChar())
      settle()
      fx(c.text()) shouldBe ":+1:"
      fx(c.dispose())
    }
    "leave a colon that closes no name, and a time, as typed" in {
      val c = make()
      typed(c, "at 12:30:45 :nosuchname:")
      settle()
      fx(c.text()) shouldBe "at 12:30:45 :nosuchname:"
      fx(c.dispose())
    }
    "list the emoji a name starts as after two letters, the one chosen in its place" in {
      val c = make()
      typed(c, "go :t")
      settle()
      popup() shouldBe None
      typed(c, "ad")
      settle()
      eventually(ui.children().exists(_.isInstanceOf[CompletionPopup])) shouldBe true
      fx(c.text()) shouldBe "go :tad"
      fx(c.act(ComposerAction.Complete))
      settle()
      fx(c.text()) shouldBe "go 🎉 "
      fx(c.dispose())
    }
    "do nothing of it when turned off" in {
      val c = make()
      fx(c.emojiShortcodes @= false)
      typed(c, ":tada:")
      settle()
      fx(c.text()) shouldBe ":tada:"
      popup() shouldBe None
      fx(c.dispose())
    }
  }
}
