package everyui.webcheck

import rapid.*
import robobrowser.display.VirtualDisplayConfig
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import java.nio.file.Paths
import scala.concurrent.duration.*

/** Runs a page live in a real browser and relays its console to stdout.
  *
  * Headless Chrome throttles `requestAnimationFrame`, so a frame rate measured there means nothing. RoboBrowser runs
  * a HEADFUL Chrome on its own Xvfb display, kiosk-fullscreen at 1920x1080 (viewport pixels are display pixels), where
  * rAF follows the display's 60 Hz as on a real screen. The page's `[ui] fps=` lines come out on stdout for
  * tools/checks.py to read, exactly as the Roku's come off its debug console.
  *
  * {{{
  *   WebLive <url> [seconds = 60] [screenshot.png [screenshot-a-second-earlier.png]]
  * }}}
  */
object WebLive extends RapidApp:
  override def run(args: List[String]): Task[Unit] =
    val url = args.headOption.getOrElse(sys.error("usage: WebLive <url> [seconds] [screenshot.png]"))
    val seconds = args.lift(1).flatMap(_.toIntOption).getOrElse(60)
    val screenshot = args.lift(2)
    val earlier = args.lift(3)   // a second before the last one, for the motion check
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
        viewport <- browser.eval("return window.innerWidth + 'x' + window.innerHeight + ' at ' + window.devicePixelRatio + 'x'")
          .map(_("result")("value").asString)
        _ <- Task(println("[webcheck] viewport " + viewport))
        _ <- Task.sleep((seconds - 1).seconds)
        _ <- earlier.map(path => browser.screenshot(Paths.get(path))).getOrElse(Task.unit)
        _ <- Task.sleep(1.second)
        _ <- screenshot.map(path => browser.screenshot(Paths.get(path))).getOrElse(Task.unit)
      yield ()
    }
