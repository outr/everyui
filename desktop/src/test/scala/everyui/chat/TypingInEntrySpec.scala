package everyui.chat

import everyui.component.{Component, Container, ScrollContainer, TextInput}
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** A field in a conversation stays in sight while it is typed into: entries arriving under it leave it where it is and
  * are counted as new, the view growing shorter (the keyboard coming up) scrolls it back into sight, and once it is
  * left the conversation follows its newest again. A scroll container keeps a field typed into in sight the same way. */
class TypingInEntrySpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")

  private def said(i: Int): ChatEntry = ChatEntry.message("m" + i, ada, false, "Message " + i + " of the conversation")

  // a sign-in card: a field and a little room
  private def card(field: TextInput): Component = {
    val c = new Container
    c.size.width @= 360.0
    c.size.height @= 120.0
    field.position.top @= 40.0
    c.children += field
    c
  }

  private def atBottom(t: Transcript): Boolean = t.list.offset() >= t.list.total - t.list.size.height() - 1.0

  // where `c` lies in what the transcript shows: its top and bottom from the view's top
  private def shown(t: Transcript, c: Component): (Double, Double) = {
    val top = c.absoluteTop - t.list.absoluteTop
    (top, top + c.size.height())
  }

  "A field in an entry" should {
    "stay where it is as entries arrive under it, they counted as new, and be kept in sight as the view shrinks" in {
      val field = fx(new TextInput("Email"))
      val entries = fx(Var((0 until 6).map(said).toVector :+ ChatEntry.custom("signin", () => card(field))))
      val chat = fx(new ChatView(entries))
      fx {
        ui.children.clear()
        chat.size.width @= 400.0
        chat.size.height @= 600.0
        ui.children += chat
      }
      val t = chat.transcript
      try {
        fx(())
        fx(atBottom(t)) shouldBe true
        fx(field.focus())
        fx(t.typing()) shouldBe true
        val before = fx(shown(t, field))
        fx(entries @= entries() ++ (6 until 16).map(said))
        fx(())
        fx(shown(t, field)) shouldBe before
        fx(t.pin.unseen()) should be > 0
        // the keyboard coming up: the view a good deal shorter, the field still in sight
        fx(chat.size.height @= 260.0)
        fx(())
        val (top, bottom) = fx(shown(t, field))
        top should be >= 0.0
        bottom should be <= fx(t.list.size.height())
        // let go: the newest again
        fx(Focus.focused @= None)
        fx(())
        fx(t.typing()) shouldBe false
        fx(atBottom(t)) shouldBe true
      } finally fx {
        ui.children.clear()
        chat.dispose()
      }
    }
  }

  "A field in a scroll container" should {
    "be kept in sight as the container grows shorter" in {
      val (scroll, field) = fx {
        ui.children.clear()
        val scroll = new ScrollContainer
        scroll.size.width @= 400.0
        scroll.size.height @= 600.0
        val field = new TextInput("Email")
        field.position.top @= 520.0
        val filler = new Container
        filler.size.width @= 10.0
        filler.size.height @= 900.0
        scroll.content.children ++= List(filler, field)
        ui.children += scroll
        (scroll, field)
      }
      try {
        fx(field.focus())
        fx(scroll.size.height @= 300.0)
        fx(())
        val top = fx(field.absoluteTop - scroll.absoluteTop)
        top should be >= 0.0
        (top + fx(field.size.height())) should be <= 300.0
      } finally fx {
        Focus.focused @= None
        ui.children.clear()
      }
    }
  }
}
