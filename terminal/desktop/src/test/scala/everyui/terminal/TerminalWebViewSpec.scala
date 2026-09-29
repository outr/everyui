package everyui.terminal

import everyui.keymap.{Chord, Keymap}
import everyui.render.Host
import everyui.ui
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.input.{KeyCode, KeyEvent}
import javafx.scene.web.WebView

/** xterm.js itself, in JavaFX's web view: loaded from the classpath through the embed server, written to, typed into
  * (key events handed to the web view alone, through the app's own scene), resized, and scrolled back. */
class TerminalWebViewSpec extends TerminalFxSpec {
  private def webView(node: Node): Option[WebView] = node match {
    case w: WebView => Some(w)
    case p: javafx.scene.Parent => p.getChildrenUnmodifiable.toArray.iterator.map(n => webView(n.asInstanceOf[Node])).collectFirst { case Some(w) => w }
    case _ => None
  }

  private def script(view: WebView, js: String): Any = view.getEngine.executeScript(js)

  private val term = "Object.values(terminalBridge.terminals)[0].term"

  // a key as the window hands it on: pressed, typed (with the character it makes, if any), released
  private def key(view: WebView, code: KeyCode, text: String, typed: String, ctrl: Boolean = false, shift: Boolean = false): Unit = {
    Event.fireEvent(view, new KeyEvent(KeyEvent.KEY_PRESSED, "", text, code, shift, ctrl, false, false))
    if (typed.nonEmpty) Event.fireEvent(view, new KeyEvent(KeyEvent.KEY_TYPED, typed, "", KeyCode.UNDEFINED, shift, ctrl, false, false))
    Event.fireEvent(view, new KeyEvent(KeyEvent.KEY_RELEASED, "", text, code, shift, ctrl, false, false))
  }

  "xterm.js in a JavaFX web view" should {
    "load, draw what is written, send what is typed, keep the app's chords, resize and scroll back" in {
      val sent = new StringBuilder
      var bigger = 0
      val t = fx {
        Keymap.global.add("spec.bigger", "Bigger", Chord("=", primary = true))(bigger += 1)
        Terminal.keep(Chord("=", primary = true))
        val t = new Terminal()
        t.size.width @= 800.0
        t.size.height @= 480.0
        t.onInput.attach(d => sent.append(d))
        ui.children += t
        t
      }
      withClue("the terminal was made: ") {
        eventually(60.0)(t.ready()) shouldBe true
      }
      val first = fx(t.dimensions()).get
      first.cols should be > 60
      val view = fx(webView(Host.root)).get

      fx(t.write("\u001b[31mred\u001b[0m plain\r\n"))
      eventually(5.0)(script(view, s"$term.buffer.active.getLine(0).translateToString(true)") == "red plain") shouldBe true
      fx(script(view, s"$term.buffer.active.getLine(0).getCell(0).getFgColor()")) shouldBe 1

      fx(t.focus())
      eventually(5.0)(Host.scene.getFocusOwner eq view) shouldBe true
      fx(key(view, KeyCode.A, "a", "a"))
      fx(key(view, KeyCode.C, "c", "\u0003", ctrl = true))
      fx(key(view, KeyCode.UP, "", ""))
      fx(key(view, KeyCode.ENTER, "", "\r"))
      withClue("sent " + sent.toString.map(c => if (c < ' ') "\\u%04x".format(c.toInt) else c.toString).mkString + ": ") {
        eventually(5.0)(sent.toString == "a\u0003\u001b[A\r") shouldBe true
      }

      // a chord the app keeps runs its command and never reaches the shell
      fx(key(view, KeyCode.EQUALS, "=", "=", ctrl = true))
      eventually(2.0)(bigger == 1) shouldBe true
      Thread.sleep(300L)
      sent.toString shouldBe "a\u0003\u001b[A\r"

      // narrower: fewer columns, told
      fx(t.size.width @= 500.0)
      eventually(5.0)(t.dimensions().exists(_.cols < first.cols)) shouldBe true

      // the scrollback
      fx(t.write((1 to 300).map(i => "line " + i).mkString("\r\n") + "\r\n"))
      eventually(5.0)(script(view, s"$term.buffer.active.length").asInstanceOf[Number].intValue > 300) shouldBe true
      fx(t.scrollLines(-50))
      eventually(5.0)(script(view, s"$term.buffer.active.viewportY").asInstanceOf[Number].intValue <
        script(view, s"$term.buffer.active.baseY").asInstanceOf[Number].intValue) shouldBe true

      // the selection is heard, and copied through the platform's clipboard
      fx(t.selectAll())
      eventually(5.0)(t.selection().exists(_.contains("line 300"))) shouldBe true
      // the clipboard is the person's: what was on it goes back
      val clipboard = fx(javafx.scene.input.Clipboard.getSystemClipboard)
      val before = fx(Option(clipboard.getString))
      fx(t.copy())
      try eventually(5.0)(Option(clipboard.getString).exists(_.contains("line 300"))) shouldBe true
      finally fx(before.foreach(text => everyui.render.Platform.copyText(text)))

      fx(t.dispose())
      fx(Keymap.global.remove("spec.bigger"))
    }
  }
}
