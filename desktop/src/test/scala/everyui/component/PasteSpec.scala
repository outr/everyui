package everyui.component

import everyui.component.types.PasteAction
import everyui.focus.Focus
import everyui.text.TextArea
import everyui.{FxSpec, ui}
import javafx.scene.control.{ContextMenu, TextInputControl}
import javafx.scene.input.{ClipboardContent, ContextMenuEvent, DataFormat, KeyCode, KeyEvent}
import reactify.*

import scala.jdk.CollectionConverters.*

/** Pastes on the desktop backend's real fields, with the clipboard set as another app would set it: what the app's
  * hook answers is what lands, at the caret or over the selection, and one undo takes it back; by the control's paste,
  * by Ctrl+V fired at the field, and by the context menu's Paste. Whatever the clipboard held before is put back
  * after. */
class PasteSpec extends FxSpec {
  private var before: Option[java.util.Map[DataFormat, AnyRef]] = None
  private var heard: List[String] = Nil

  override def beforeAll(): Unit = {
    super.beforeAll()
    before = Some(fx {
      val clipboard = javafx.scene.input.Clipboard.getSystemClipboard
      val kept = new java.util.HashMap[DataFormat, AnyRef]()
      clipboard.getContentTypes.forEach(t => Option(clipboard.getContent(t)).foreach(c => kept.put(t, c)))
      kept
    })
  }

  override def afterAll(): Unit = {
    before.foreach(kept => fx(javafx.scene.input.Clipboard.getSystemClipboard.setContent(kept)))
    super.afterAll()
  }

  private def copy(words: String): Unit = fx {
    val content = new ClipboardContent()
    content.putString(words)
    javafx.scene.input.Clipboard.getSystemClipboard.setContent(content)
  }

  // a field on screen holding `value`, `from` to `to` chosen, whose pastes are answered by `answer`
  private def field(value: String, from: Int, to: Int)(answer: Option[String => PasteAction]): (InputView, TextInputControl) = fx {
    heard = Nil
    ui.children.clear()
    Focus.focused @= None
    val input = new InputView()
    input.size.width @= 300.0
    input.size.height @= 24.0
    input.pasted @= answer.map(f => (words: String) => {
      heard = heard :+ words
      f(words)
    })
    ui.children += input
    input.text @= value
    val control = input.node.fx.asInstanceOf[TextInputControl]
    control.selectRange(from, to)
    (input, control)
  }

  "A paste" should {
    "land as it is with no hook" in {
      val (input, control) = field("ab", 1, 1)(None)
      copy("XY")
      fx(control.paste())
      fx(input.text()) shouldBe "aXYb"
      fx(control.getCaretPosition) shouldBe 3
    }
    "land as it is where the hook says Insert, the hook hearing its words" in {
      val (input, control) = field("ab", 2, 2)(Some(_ => PasteAction.Insert))
      copy("pasted words")
      fx(control.paste())
      heard shouldBe List("pasted words")
      fx(input.text()) shouldBe "abpasted words"
    }
    "put other words at the caret, the caret after them, and one undo take them back" in {
      val (input, control) = field("hello world", 5, 5)(Some(w => PasteAction.InsertText("[" + w.toUpperCase + "]")))
      copy("there")
      fx(control.paste())
      fx(input.text()) shouldBe "hello[THERE] world"
      fx(control.getCaretPosition) shouldBe 12
      fx(control.undo())
      fx(input.text()) shouldBe "hello world"
    }
    "put other words over the selection" in {
      val (input, control) = field("hello world", 6, 11)(Some(_ => PasteAction.InsertText("moon")))
      copy("anything")
      fx(control.paste())
      fx(input.text()) shouldBe "hello moon"
      fx(control.getCaretPosition) shouldBe 10
    }
    "land nothing where the hook says Ignore, the selection kept" in {
      val (input, control) = field("hello world", 0, 5)(Some(_ => PasteAction.Ignore))
      copy("gone")
      fx(control.paste())
      heard shouldBe List("gone")
      fx(input.text()) shouldBe "hello world"
      fx(control.getSelectedText) shouldBe "hello"
    }
    "not be asked about, nor land anything, in a read-only field" in {
      val (input, control) = field("ab", 1, 1)(Some(_ => PasteAction.InsertText("no")))
      fx(input.readOnly @= true)
      copy("XY")
      fx(control.paste())
      heard shouldBe Nil
      fx(input.text()) shouldBe "ab"
      fx(input.readOnly @= false)
      fx(control.paste())
      heard shouldBe List("XY")
      fx(input.text()) shouldBe "anob"
    }
    "not ask about a clipboard with no words" in {
      val (input, control) = field("ab", 2, 2)(Some(_ => PasteAction.Ignore))
      fx {
        val content = new ClipboardContent()
        content.putFiles(java.util.List.of(new java.io.File("/tmp")))
        javafx.scene.input.Clipboard.getSystemClipboard.setContent(content)
      }
      fx(control.paste())
      heard shouldBe Nil
      fx(input.text()) shouldBe "ab"
    }
    "be asked about for Ctrl+V fired at the field" in {
      val (input, control) = field("ab", 2, 2)(Some(w => PasteAction.InsertText(w.reverse)))
      copy("xyz")
      windowed(control) {
        fx(control.selectRange(2, 2))
        fx {
          javafx.event.Event.fireEvent(control, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.V, false, true, false, false))
          javafx.event.Event.fireEvent(control, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.V, false, true, false, false))
        }
        heard shouldBe List("xyz")
        fx(input.text()) shouldBe "abzyx"
      }
    }
    "be asked about for the context menu's Paste" in {
      val (input, control) = field("ab", 0, 0)(Some(w => PasteAction.InsertText(w + "!")))
      copy("menu")
      windowed(control) {
        fx(control.selectRange(0, 0))
        fx {
          val at = control.localToScreen(10.0, 10.0)
          javafx.event.Event.fireEvent(control, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 10.0, 10.0,
            at.getX, at.getY, false, null))
        }
        try {
          eventually(menu().nonEmpty) shouldBe true
          val paste = fx(menu().get.getItems.asScala.find(i => i.getText != null && i.getText.replace("_", "") == "Paste"))
          paste.nonEmpty shouldBe true
          fx(paste.get.fire())
          heard shouldBe List("menu")
          fx(control.getText) shouldBe "menu!ab"
          fx(input.text()) shouldBe "menu!ab"
        } finally fx(menu().foreach(_.hide()))
      }
    }
    "go through a text area and a text field to their platform's fields" in {
      val (area, text) = fx {
        ui.children.clear()
        val area = new TextArea()
        val text = new TextInput("Name")
        area.pasted @= Some(w => PasteAction.InsertText(w.trim))
        text.pasted @= Some(_ => PasteAction.Ignore)
        ui.children ++= List(area, text)
        (area, text)
      }
      copy("  padded  ")
      fx(area.field.get.node.fx.asInstanceOf[TextInputControl].paste())
      fx(area.value()) shouldBe "padded"
      val inner = fx(text.children().collectFirst { case i: InputView => i }.get)
      fx(inner.node.fx.asInstanceOf[TextInputControl].paste())
      fx(text.value()) shouldBe ""
    }
  }

  // the context menu showing, if one is (on the FX thread)
  private def menu(): Option[ContextMenu] = javafx.stage.Window.getWindows.asScala.collectFirst {
    case m: ContextMenu if m.isShowing => m
  }

  // `control` in a window of its own while `test` runs, skinned as a shown field is: its keys and its menu are its skin's
  private def windowed[T](control: TextInputControl)(test: => T): T = {
    val stage = fx {
      // closing the only window shown must not end JavaFX for the specs after
      javafx.application.Platform.setImplicitExit(false)
      val stage = new javafx.stage.Stage()
      stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.StackPane(control), 300.0, 60.0))
      stage.show()
      control.requestFocus()
      stage
    }
    try {
      eventually(control.getSkin != null) shouldBe true
      test
    } finally fx {
      // its caret stopped: the keys and the clicks fired at it set it blinking, focused or not (a window under Xvfb is
      // never focused, so never loses it either), and it blinked on after the window closed, JavaFX pulsing for it
      // through every spec after this one (MotionSpec's and BusyRingSpec's idle JavaFX)
      control.getSkin match {
        case skin: javafx.scene.control.skin.TextInputControlSkin[?] => skin.setCaretAnimating(false)
        case _ => ()
      }
      stage.close()
    }
  }
}
