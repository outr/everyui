package everyui.focus

import everyui.component.{Component, Container, Modal}
import everyui.event.{KeyEvent, Keys}
import everyui.layout.HorizontalLayout
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import reactify.*

/** What is typed where the focus is in something that takes typing (`Container.typeInto`): an app's own keyboard, its
  * keys focused, has the characters, Backspace and a phone app's Enter, while OK still presses the key and the arrows
  * still move along the keys. Run on the JavaFX thread, against the desktop backend's real nodes. */
class TypingSpec extends FxSpec {
  private def cap(): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c.size.width @= 60.0
    c.size.height @= 60.0
    c
  }

  private def press(name: String): Boolean = {
    val taken = Focus.key(name, true)
    Focus.key(name, false)
    taken
  }

  "A keyboard of the app's own" should {
    "take what is typed while its keys have the focus, and leave OK and the arrows to them" in {
      val keys = Vector.fill(3)(cap())
      val words = Var("")
      var done = ""
      var clicks = 0
      var heard = List.empty[String]
      val board = new Container
      board.layout @= Some(HorizontalLayout(10.0))
      board.children ++= keys
      board.typeInto(TextEntry(words, () => done = words()))
      keys.head.event.click.attach(_ => clicks += 1)
      keys.head.event.key.down.attach((e: KeyEvent) => heard = e.name :: heard)
      val outside = cap()
      fx {
        ui.children.clear()
        Focus.focused @= None
        val screen = new Container
        screen.children ++= List(board, outside)
        ui.children += screen
      }
      try {
        fx {
          keys.head.focus()
          List("H", "i", " ", "é").map(press) shouldBe List(true, true, true, true)
          words() shouldBe "Hi é"
          press("Backspace")
          words() shouldBe "Hi "
          press("Enter")
          clicks shouldBe 1
          words() shouldBe "Hi "
          press(Keys.TypedEnter)
          done shouldBe "Hi "
          // the focused key heard OK, but none of what was typed
          heard.reverse shouldBe List("Enter")
          press("ArrowRight")
          Focus.focused() shouldBe Some(keys(1))
          press("x")
          words() shouldBe "Hi x"
          // outside it, nothing is typed anywhere
          outside.focus()
          press("y") shouldBe false
          words() shouldBe "Hi x"
        }
      } finally fx {
        while (Modal.showing) Modal.close()
        ui.children.clear()
        Focus.focused @= None
      }
    }
  }
}
