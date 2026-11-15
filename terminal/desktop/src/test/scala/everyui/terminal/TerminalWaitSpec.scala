package everyui.terminal

import everyui.ui
import everyui.render.Host
import javafx.scene.Node
import javafx.scene.web.WebView

/** A terminal not yet on the screen (in a tab never shown): what is written to it waits, only its end kept, and it
  * looks once whether it can be made and then waits to be shown, rather than looking again every 50 ms. Shown, it is
  * made, says how much it let go, and draws the end it kept; its scrollback is what the app sets as it runs. */
class TerminalWaitSpec extends TerminalFxSpec {
  private def webViews(node: Node): List[WebView] = node match {
    case w: WebView => List(w)
    case p: javafx.scene.Parent => p.getChildrenUnmodifiable.toArray.toList.flatMap(n => webViews(n.asInstanceOf[Node]))
    case _ => Nil
  }

  private val term = "Object.values(terminalBridge.terminals)[0].term"

  "A terminal not on the screen" should {
    "keep only the end of what is written, look once, and be made with it as it is shown" in {
      val t = fx {
        val t = new Terminal()
        t.size.width @= 600.0
        t.size.height @= 300.0
        t
      }
      // a megabyte and more of numbered lines, before it is ever shown
      val lines = 40000
      fx((0 until lines).foreach(i => t.write("line " + i.toString + " of the build's output\r\n")))
      fx(t.waitingLength) should be <= Terminal.waitingMost
      fx(t.waitingLength) should be > Terminal.waitingMost / 4
      // the turns go by with nothing asked of it
      val looks = fx(t.looked)
      looks should be <= 1
      (0 until 5).foreach(_ => fx(()))
      fx(t.looked) shouldBe looks
      fx(t.scrollback @= 300)
      fx(ui.children += t)
      try {
        withClue("the terminal was made: ") {
          eventually(60.0)(t.ready()) shouldBe true
        }
        fx(t.looked) shouldBe looks + 1
        val view = fx(webViews(Host.root).find(v =>
          v.getEngine.executeScript("!!window.terminalBridge && Object.keys(terminalBridge.terminals).length > 0") == true)).get
        def script(js: String): Any = view.getEngine.executeScript(js)
        // the last line written drawn at the end, and the scrollback held to what was set before it was made
        // (the line above the cursor's, "" until the engine has written that far: read as it fills, not before)
        val lastLine = s"(function () { var b = $term.buffer.active; var l = b.getLine(b.baseY + b.cursorY - 1); " +
          "return l ? l.translateToString(true) : ''; })()"
        eventually(60.0)(script(lastLine) ==
          "line " + (lines - 1).toString + " of the build's output") shouldBe true
        fx(script(s"$term.options.scrollback")) shouldBe 300
        fx(t.scrollback @= 50)
        eventually(30.0)(script(s"$term.options.scrollback") == 50) shouldBe true
      } finally fx {
        ui.children -= t
        t.dispose()
      }
    }

    "say how much it let go, on a line of its own, first" in {
      Terminal.dropped(1234L) should include("1234 earlier characters not kept")
      Terminal.dropped(1234L) should endWith("\r\n")
    }
  }
}
