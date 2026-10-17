package everyui.terminal

import everyui.{Color, ui}
import everyui.component.Container
import everyui.render.Host
import javafx.scene.Node
import javafx.scene.image.WritableImage
import javafx.scene.web.WebView

/** A terminal whose theme's ground is translucent, in JavaFX's web view: what is behind it shows through the ground,
  * painted once, while a cell background a program sets stays solid. */
class TerminalGroundSpec extends TerminalFxSpec {
  private def webViews(node: Node): List[WebView] = node match {
    case w: WebView => List(w)
    case p: javafx.scene.Parent => p.getChildrenUnmodifiable.toArray.toList.flatMap(n => webViews(n.asInstanceOf[Node]))
    case _ => Nil
  }

  private val term = "Object.values(terminalBridge.terminals)[0].term"
  private val behind = Color.fromHex("ff00ff")
  private val ground = Color.fromHex("1e1e2e").withAlpha(0.4)

  // the scene's pixel at (x, y), as its red, green and blue
  private def pixel(x: Int, y: Int): (Int, Int, Int) = {
    val argb = Host.scene.snapshot(new WritableImage(Host.scene.getWidth.toInt, Host.scene.getHeight.toInt)).getPixelReader.getArgb(x, y)
    ((argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff)
  }

  private def near(p: (Int, Int, Int), r: Int, g: Int, b: Int): Boolean =
    math.abs(p._1 - r) <= 3 && math.abs(p._2 - g) <= 3 && math.abs(p._3 - b) <= 3

  private def blend(over: Int, under: Int): Int = math.round(over * 0.4 + under * 0.6).toInt

  "A terminal with a translucent ground" should {
    "show what is behind it through the ground, and keep a program's cell backgrounds solid" in {
      val t = fx {
        val t = new Terminal()
        t.theme @= TerminalTheme.of(everyui.theme.Theme.palette()).copy(background = ground)
        t.size.width @= 800.0
        t.size.height @= 480.0
        ui.children += new Container {
          backgroundColor @= behind
          size.width @= 800.0
          size.height @= 480.0
          children += t
        }
        t
      }
      try {
        withClue("the terminal was made: ") {
          eventually(60.0)(t.ready()) shouldBe true
        }
        val view = fx(webViews(Host.root).find(v =>
          v.getEngine.executeScript("!!window.terminalBridge && Object.keys(terminalBridge.terminals).length > 0") == true)).get
        def script(js: String): Any = view.getEngine.executeScript(js)
        fx(view.getPageFill) shouldBe javafx.scene.paint.Color.TRANSPARENT

        fx(t.write("\u001b[44m          \u001b[0m\r\n"))
        eventually(5.0)(script(s"$term.buffer.active.getLine(0).getCell(0).getBgColor()") == 4) shouldBe true
        // the middle of a cell on the first row, in the blue, and one far along the third, on the ground
        val (blue, open) = fx {
          val at = script(s"""(() => {
            const t = $term, r = t.element.querySelector('.xterm-screen').getBoundingClientRect();
            const w = r.width / t.cols, h = r.height / t.rows;
            return [r.left + 4.5 * w, r.top + 0.5 * h, r.left + (t.cols - 5.5) * w, r.top + 2.5 * h].map(Math.round).join(',');
          })()""").toString.split(',').map(_.toInt)
          ((at(0), at(1)), (at(2), at(3)))
        }
        val (x, y) = fx((t.absoluteLeft.toInt, t.absoluteTop.toInt))
        val expected = (blend(ground.redInt, behind.redInt), blend(ground.greenInt, behind.greenInt), blend(ground.blueInt, behind.blueInt))
        withClue("the ground blended once over what is behind it: ") {
          eventually(5.0)(near(pixel(x + open._1, y + open._2), expected._1, expected._2, expected._3)) shouldBe true
        }
        val ansiBlue = t.theme().ansi(4)
        withClue("the program's blue cell solid: ") {
          eventually(5.0)(near(pixel(x + blue._1, y + blue._2), ansiBlue.redInt, ansiBlue.greenInt, ansiBlue.blueInt)) shouldBe true
        }
      }
      finally {
        fx(t.dispose())
        fx(ui.children.clear())
      }
    }
  }
}
