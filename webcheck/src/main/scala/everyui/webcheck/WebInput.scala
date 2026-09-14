package everyui.webcheck

import rapid.*
import robobrowser.display.VirtualDisplayConfig
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import scala.concurrent.duration.*

/** The input demo in a real Chrome, driven by real DOM events: the arrows and Enter on the document, a click and
  * the pointer on the third box — what a keyboard, a TV remote in a browser, and a mouse produce. Prints the
  * page's console; tools/check.sh compares it to the expected lines.
  *
  *   WebInput <url>
  */
object WebInput extends RapidApp:
  private val keys = List("ArrowRight", "Enter", "ArrowLeft", "ArrowDown")

  override def run(args: List[String]): Task[Unit] =
    val url = args.headOption.getOrElse(sys.error("usage: WebInput <url>"))
    RoboBrowser.withBrowser(RoboBrowserConfig(
      browserConfig = BrowserConfig(noSandbox = true),
      tabSelector = TabSelector.FirstPage,
      virtualDisplay = Some(VirtualDisplayConfig(width = 1920, height = 1080))
    )) { browser =>
      browser.event.runtime.consoleAPICalled.attach { event =>
        println(event.args.map(_.value).mkString(" "))
        System.out.flush()
      }
      for
        _ <- browser.enableRuntime
        _ <- browser.navigate(url)
        _ <- browser.waitForLoaded()
        _ <- Task.sleep(1.second)
        _ <- keys.map(key => browser.eval(s"""document.dispatchEvent(new KeyboardEvent("keydown", { key: "$key" }));
                                              document.dispatchEvent(new KeyboardEvent("keyup", { key: "$key" }))"""))
          .foldLeft(Task.unit)((t, next) => t.flatMap(_ => next.unit))
        // the third box: at (540..720, 100..220), so its centre is (630, 160)
        _ <- browser.eval("""const c = document.elementFromPoint(630, 160);
                             c.dispatchEvent(new PointerEvent("pointerenter", { clientX: 630, clientY: 160 }));
                             c.dispatchEvent(new MouseEvent("click", { clientX: 630, clientY: 160, bubbles: true }));
                             c.dispatchEvent(new PointerEvent("pointerleave", { clientX: 900, clientY: 160 }))""")
        _ <- Task.sleep(1.second)
      yield ()
    }
