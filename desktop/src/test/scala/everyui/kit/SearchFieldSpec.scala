package everyui.kit

import everyui.component.{Component, Container, MaterialIconView, TextInput}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.list.{RichList, RichLook, RichSection}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** The search box on one line: as tall as a line, not a labelled field's two; a × once it holds something, which
  * empties it, as Escape does, an empty one leaving Escape to the screen; and over a RichList, the words narrowing it
  * and Down going on to it. */
class SearchFieldSpec extends FxSpec {
  private def press(code: KeyCode, text: String = ""): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, false, false, false, false))
  }

  // (on the FX thread)
  private def cross(f: SearchField): MaterialIconView =
    f.children().collect { case m: MaterialIconView if m.icon() == MaterialIcon.Close => m }.head

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      ui.children.clear()
      // taken apart, so a list's clock (Ticker) stops rather than moving on for the rest of the run
      c.dispose()
      Focus.focused @= None
    }
  }

  "A search field" should {
    "be one line tall, with a × once it holds something that empties it, as Escape does" in {
      val f = fx(new SearchField())
      onScreen(f) {
        fx(f.size.height()) shouldBe SearchField.height
        fx(f.size.height()) should be < fx(new TextInput("Search").size.height())
        fx(f.placeholder()) shouldBe "Search"
        fx(cross(f).visible()) shouldBe false
        fx(f.focus())
        fx(f.value @= "ada")
        fx(cross(f).visible()) shouldBe true
        fx(cross(f).event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        fx(f.value()) shouldBe ""
        fx(cross(f).visible()) shouldBe false
        fx(Focus.focused().exists(_ eq f)) shouldBe true
        // Escape empties it, and is its own only while there is something to empty
        fx(f.value @= "lovelace")
        fx(f.keepsEvent(everyui.event.KeyEvent(everyui.event.Key.Escape, "Escape"))) shouldBe true
        press(KeyCode.ESCAPE)
        fx(f.value()) shouldBe ""
        fx(f.keepsEvent(everyui.event.KeyEvent(everyui.event.Key.Escape, "Escape"))) shouldBe false
        fx(Focus.focused().exists(_ eq f)) shouldBe true
      }
    }
    "narrow a RichList as it is typed in, empty it back, and hand Down on to the list" in {
      case class Chat(id: String, title: String)
      val chats = (1 to 12).map(i => Chat("c" + i, "Chat " + i)).toVector
      val sections = Var(Vector(RichSection[Chat]("all", "All", chats)))
      val list = fx(new RichList[Chat](sections, _.id, c => RichLook(c.title), search = true))
      onScreen(list) {
        val f = list.field.get
        fx(f.size.height()) shouldBe SearchField.height
        def shown: Int = fx(list.list.count)
        val all = shown
        fx(f.focus())
        fx(f.value @= "Chat 1")
        shown should be < all
        fx(list.query()) shouldBe "Chat 1"
        press(KeyCode.ESCAPE)
        fx(list.query()) shouldBe ""
        shown shouldBe all
        fx(f.value @= "Chat 12")
        fx(cross(f).event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        shown shouldBe all
        press(KeyCode.DOWN)
        fx(Focus.focused().exists(_ eq list.list)) shouldBe true
        press(KeyCode.UP)
        fx(Focus.focused().exists(_ eq f)) shouldBe true
      }
    }
  }
}
