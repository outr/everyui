package everyui.terminal

import everyui.render.Host
import everyui.ui
import javafx.scene.Node
import javafx.scene.web.WebView

/** xterm.js inside a page drawn smaller with a CSS scale, as the web draws the app under UiScale: a pointer pressed
  * over a cell near the bottom right is reported at that cell, not rows above it. */
class TerminalScaleSpec extends TerminalFxSpec {
  private def webViews(node: Node): List[WebView] = node match {
    case w: WebView => List(w)
    case p: javafx.scene.Parent => p.getChildrenUnmodifiable.toArray.toList.flatMap(n => webViews(n.asInstanceOf[Node]))
    case _ => Nil
  }

  private val term = "Object.values(terminalBridge.terminals)[0].term"

  "A terminal under UiScale" should {
    "report a pointer to the program at the cell drawn under it" in {
      val sent = new StringBuilder
      val t = fx {
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
      val view = fx(webViews(Host.root).find(v =>
        v.getEngine.executeScript("!!window.terminalBridge && Object.keys(terminalBridge.terminals).length > 0") == true)).get
      def script(js: String): Any = view.getEngine.executeScript(js)

      // the page drawn at 80%, as Platform.setUiScale draws the app's root on the web
      fx(script("""(() => {
        document.documentElement.style.setProperty('--everyui-ui-scale', '0.8');
        const b = document.body.style;
        b.setProperty('scale', '0.8'); b.transformOrigin = '0 0'; b.width = b.height = 'calc(100% / 0.8)';
      })()"""))
      withClue("the text sized to the scale: ") {
        eventually(5.0)(script(s"$term.options.fontSize").asInstanceOf[Number].doubleValue == 13.0 * 0.8) shouldBe true
      }

      // the middle of the cell second from the right on the last row but one, where the screen draws it
      fx(t.write("\u001b[?1000h\u001b[?1006h"))
      val (col, row) = fx((script(s"$term.cols").asInstanceOf[Number].intValue - 1, script(s"$term.rows").asInstanceOf[Number].intValue - 1))
      def press(): Unit = fx(script(s"""(() => {
        const t = $term, screen = t.element.querySelector('.xterm-screen'), r = screen.getBoundingClientRect();
        const at = { clientX: r.left + ($col - 0.5) * r.width / t.cols, clientY: r.top + ($row - 0.5) * r.height / t.rows,
          button: 0, bubbles: true, cancelable: true, view: window };
        screen.dispatchEvent(new MouseEvent('mousedown', Object.assign({ buttons: 1 }, at)));
        screen.dispatchEvent(new MouseEvent('mouseup', Object.assign({ buttons: 0 }, at)));
      })()"""))
      eventually(5.0)(script(s"$term.modes.mouseTrackingMode") == "vt200") shouldBe true
      press()
      withClue("sent " + sent.toString.replace("\u001b", "ESC") + ": ") {
        eventually(5.0)(sent.toString.contains(s"\u001b[<0;$col;${row}M")) shouldBe true
      }

      fx(t.dispose())
      fx(ui.children -= t)
    }
  }
}
