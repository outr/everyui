package everyui.webcheck

import rapid.*
import robobrowser.display.VirtualDisplayConfig
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

/** A page in a real Chrome: load it, run a script against it, screenshot it — how a click on a live page is checked.
  * Prints the page's console.
  *
  *   WebDrive <url> <script.js> [screenshot.png] [seconds before the screenshot = 2]
  */
object WebDrive extends RapidApp:
  override def run(args: List[String]): Task[Unit] =
    val url = args.headOption.getOrElse(sys.error("usage: WebDrive <url> <script.js> [screenshot.png] [seconds]"))
    val script = Files.readString(Paths.get(args(1)))
    val screenshot = args.lift(2)
    val seconds = args.lift(3).flatMap(_.toIntOption).getOrElse(2)
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
        _ <- Task.sleep(2.seconds)
        result <- browser.eval(script)
        _ <- Task(println("[webdrive] " + result("result").get("value").map(_.toString).getOrElse("")))
        _ <- Task.sleep(seconds.seconds)
        _ <- screenshot.map(path => browser.screenshot(Paths.get(path))).getOrElse(Task.unit)
      yield ()
    }
