package everyui.component

import everyui.component.types.TextRange
import everyui.focus.Focus
import everyui.kit.SearchField
import everyui.{FxSpec, ui}
import javafx.event.Event
import javafx.scene.control.TextInputControl
import javafx.scene.input.{MouseButton, MouseEvent}

/** A field that chooses all it holds as the caret arrives in it (`selectOnFocus`), as a browser's address bar does: by
  * a click, the press's release choosing it; by Tab; a second click placing the caret, a drag choosing a range, and the
  * focus given back to a field that moved choosing nothing; off by default. Real JavaFX mouse events on the field's own
  * node, whose behaviour places the caret and drags out a range, and Tab through the focus layer. */
class SelectOnFocusSpec extends FxSpec {
  private def control(input: InputView): TextInputControl = input.node.fx.asInstanceOf[TextInputControl]

  // `kind` at `x` across the field (its middle, down), in the scene's place, as a mouse puts it
  private def mouse(input: InputView, kind: javafx.event.EventType[MouseEvent], x: Double, clicks: Int = 1): Unit = {
    val c = control(input)
    val at = c.localToScene(x, c.getHeight / 2.0)
    val down = kind == MouseEvent.MOUSE_PRESSED || kind == MouseEvent.MOUSE_DRAGGED
    Event.fireEvent(c, new MouseEvent(kind, at.getX, at.getY, at.getX, at.getY, MouseButton.PRIMARY, clicks, false,
      false, false, false, down, false, false, false, false, true, null))
  }

  // pressed, released and clicked at `x`, as a mouse does, in a turn of its own
  private def click(input: InputView, x: Double, clicks: Int = 1): Unit = fx {
    List(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED).foreach(mouse(input, _, x, clicks))
  }

  // pressed at `from`, dragged to `to`, let go and clicked there (JavaFX clicks where it lets go on what it pressed)
  private def drag(input: InputView, from: Double, to: Double): Unit = fx {
    mouse(input, MouseEvent.MOUSE_PRESSED, from)
    mouse(input, MouseEvent.MOUSE_DRAGGED, (from + to) / 2.0)
    mouse(input, MouseEvent.MOUSE_DRAGGED, to)
    mouse(input, MouseEvent.MOUSE_RELEASED, to)
    mouse(input, MouseEvent.MOUSE_CLICKED, to)
  }

  private def tab(): Unit = fx {
    Focus.key("Tab", true)
    Focus.key("Tab", false)
  }

  private def selection(input: InputView): TextRange = fx(input.selection)

  private val words = "https://everyui.com/start"

  // in a window in front (under Xvfb, with no window manager to hold one back), as the field's focus needs one
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
    eventually(window.getWindow.isFocused) shouldBe true
  }

  override def afterAll(): Unit = {
    offStage()
    super.afterAll()
  }

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      Focus.visible @= false
      ui.children += c
      c.node.fx.applyCss()
      c.node.fx.asInstanceOf[javafx.scene.Parent].layout()
    }
    try test finally fx {
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
      Focus.visible @= false
    }
  }

  private def field(select: Boolean): (TextInput, InputView) = fx {
    val t = new TextInput("Address", width = 480.0)
    t.value @= words
    if (select) t.selectOnFocus @= true
    (t, t.field.get)
  }

  private def search(select: Boolean): (SearchField, InputView) = fx {
    val s = new SearchField(width = 480.0)
    s.value @= words
    if (select) s.selectOnFocus @= true
    (s, s.children().collectFirst { case i: InputView => i }.get)
  }

  "selectAll" should {
    "choose all a TextInput holds" in {
      val (t, input) = field(select = false)
      onScreen(t) {
        fx(input.moveCaret(3))
        fx(t.selectAll())
        selection(input) shouldBe TextRange(0, words.length)
      }
    }
    "choose all a SearchField holds" in {
      val (s, input) = search(select = false)
      onScreen(s) {
        fx(input.moveCaret(3))
        fx(s.selectAll())
        selection(input) shouldBe TextRange(0, words.length)
      }
    }
  }

  "A field that selects on focus" should {
    "choose all it holds as a click puts the caret in it, and place the caret on a second click" in {
      val (t, input) = field(select = true)
      onScreen(t) {
        click(input, 40.0)
        fx(Focus.focused().exists(_ eq t)) shouldBe true
        fx(control(input).isFocused) shouldBe true
        selection(input) shouldBe TextRange(0, words.length)
        click(input, 40.0)
        val placed = selection(input)
        placed.collapsed shouldBe true
        placed.start should (be > 0 and be < words.length)
      }
    }
    "do the same as a SearchField, a browser's address bar" in {
      val (s, input) = search(select = true)
      onScreen(s) {
        click(input, 40.0)
        fx(Focus.focused().exists(_ eq s)) shouldBe true
        selection(input) shouldBe TextRange(0, words.length)
        click(input, 60.0)
        selection(input).collapsed shouldBe true
      }
    }
    "choose all as Tab puts the caret in it" in {
      val (t, input) = field(select = true)
      onScreen(t) {
        tab()
        fx(Focus.focused().exists(_ eq t)) shouldBe true
        fx(control(input).isFocused) shouldBe true
        selection(input) shouldBe TextRange(0, words.length)
      }
    }
    "keep the range a drag chooses as it puts the caret in it" in {
      val (s, input) = search(select = true)
      onScreen(s) {
        drag(input, 20.0, 120.0)
        fx(control(input).isFocused) shouldBe true
        val chosen = selection(input)
        chosen.collapsed shouldBe false
        chosen should not be TextRange(0, words.length)
      }
    }
    "choose nothing as the focus is given back to it: put back after it moved, or its window come back to" in {
      val (t, input) = field(select = true)
      val holder = fx(new Container)
      fx(holder.children += t)
      onScreen(holder) {
        click(input, 40.0)
        click(input, 40.0)
        val placed = selection(input)
        placed.collapsed shouldBe true
        fx {
          holder.children -= t
          holder.children += t
        }
        fx(Focus.focused().exists(_ eq t)) shouldBe true
        selection(input) shouldBe placed
        // another window in front, and this one again
        val other = fx {
          val s = new javafx.stage.Stage()
          s.setScene(new javafx.scene.Scene(new javafx.scene.layout.Pane(), 200.0, 100.0))
          s.show()
          s.requestFocus()
          s
        }
        try {
          eventually(!control(input).isFocused) shouldBe true
          fx {
            other.hide()
            window.getWindow.requestFocus()
          }
          eventually(control(input).isFocused) shouldBe true
          selection(input) shouldBe placed
        } finally fx(other.hide())
      }
    }
    "be off by default, or turned off: a click places the caret" in {
      val (t, input) = field(select = false)
      onScreen(t) {
        click(input, 40.0)
        fx(control(input).isFocused) shouldBe true
        selection(input).collapsed shouldBe true
      }
      val (s, other) = search(select = true)
      fx(s.selectOnFocus @= false)
      onScreen(s) {
        click(other, 40.0)
        fx(control(other).isFocused) shouldBe true
        selection(other).collapsed shouldBe true
      }
    }
  }
}
