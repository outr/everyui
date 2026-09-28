package everyui.text

import everyui.component.*
import everyui.focus.Focus
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** A box of lines: its keys left to the platform's field while it is edited, Primary+Enter sent, the box growing with
  * its lines; and a field's own typing keys. */
class TextAreaSpec extends FxSpec {
  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      ui.children ++= parts
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def key(code: KeyCode, ctrl: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, ctrl, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  "A text area" should {
    "leave Enter and the arrows to its field while it is edited, and send on Primary+Enter" in {
      val (area, below) = fx {
        val below = new Component(Nodes.container())
        below.focusable @= true
        below.size.width @= 100.0
        below.size.height @= 20.0
        (new TextArea("Commit message"), below)
      }
      val column = fx(Column(8.0)(area, below))
      onScreen(column) {
        var sent: List[String] = Nil
        fx {
          area.submitted.attach(v => sent = sent :+ v)
          area.value @= "Fix the thing"
          area.focus()
        }
        fx(area.editing()) shouldBe true
        fx(Focus.key("ArrowDown", true)) shouldBe false
        fx(Focus.focused()) shouldBe Some(area)
        fx(Focus.key("Enter", true)) shouldBe false
        fx(Focus.key("Enter", false))
        sent shouldBe Nil
        key(KeyCode.ENTER, ctrl = true)
        sent shouldBe List("Fix the thing")
        // Tab still moves on
        fx(Focus.key("Tab", true))
        fx(Focus.focused()) shouldBe Some(below)
        fx(area.children().collect { case i: InputView => i.node.fx.asInstanceOf[javafx.scene.control.TextArea].getPromptText }) shouldBe
          Vector("Commit message")
      }
    }
    "grow with its lines, between its least and most height" in {
      val area = fx(new TextArea(grow = true, minHeight = 60.0, maxHeight = 140.0))
      onScreen(area) {
        fx(area.size.height()) shouldBe 60.0
        fx(area.value @= (1 to 4).map(_.toString).mkString("\n"))
        eventually(area.size.height() > 60.0 && area.size.height() < 140.0) shouldBe true
        fx(area.value @= (1 to 40).map(_.toString).mkString("\n"))
        eventually(area.size.height() == 140.0) shouldBe true
      }
    }
  }

  "A text field being edited" should {
    "leave the keys it types with to the platform's field" in {
      val input = fx(new TextInput("Name"))
      onScreen(input) {
        fx(input.focus())
        fx(input.editing()) shouldBe true
        fx(Focus.key("Backspace", true)) shouldBe false
        fx(Focus.key("ArrowLeft", true)) shouldBe false
        fx(Focus.focused()) shouldBe Some(input)
      }
    }
  }
}
