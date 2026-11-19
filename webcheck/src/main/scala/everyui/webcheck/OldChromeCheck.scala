package everyui.webcheck

import fabric.*
import rapid.*

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

/** The example app's demos in an old TV's engine (Chromium 53, webOS 4's; 56, Tizen 4's), or any Chrome: each loaded
  * from its files at 1920 by 1080, given a few of the remote's keys and the pointer's moves and clicks, and checked to
  * have drawn its screen and left no error uncaught. `--shots` keeps a picture of each, for a look at what it drew.
  * Prints the page's console and each check; exits 1 where one fails. With WebOSCheck (its `--chrome`), the check that
  * a change still runs on the oldest TV EveryUI supports.
  *
  *   OldChromeCheck <page dir> [--chrome <binary>] [--shots <dir>] [demo ...]
  *
  * The demos are tv, chat, rows, widgets and icons unless named. An engine older than 59 opens a window on `$DISPLAY`:
  * run it under `xvfb-run`.
  */
object OldChromeCheck extends RapidApp {
  private val usage = "usage: OldChromeCheck <page dir> [--chrome <binary>] [--shots <dir>] [demo ...]"

  override def run(args: List[String]): Task[Unit] = {
    def option(name: String): Option[String] = args.dropWhile(_ != name).drop(1).headOption
    val chrome = option("--chrome").getOrElse(DevTools.defaultChrome)
    val shots = option("--shots").map(Paths.get(_))
    // the demos named: what is neither an option nor its value
    def named(rest: List[String]): List[String] = rest match {
      case o :: _ :: more if o.startsWith("--") => named(more)
      case d :: more => d :: named(more)
      case Nil => Nil
    }
    val demos = named(args.drop(1)) match {
      case Nil => List("tv", "chat", "rows", "widgets", "icons")
      case some => some
    }
    val page = Paths.get(args.headOption.getOrElse(sys.error(usage))).toAbsolutePath.resolve("index.html").toUri.toString
    shots.foreach(Files.createDirectories(_))
    var failed = List.empty[String]
    def check(what: String, ok: Boolean): Unit = {
      println((if (ok) "[old chrome] ok " else "[old chrome] FAILED ") + what)
      System.out.flush()
      if (!ok) failed :+= what
    }
    DevTools.around(chrome) { browser =>
      println("[old chrome] Chrome " + browser.major + ": " + chrome)
      browser.onLine { line =>
        println(line)
        System.out.flush()
      }
      def key(code: Int, name: String): Task[Unit] = for {
        _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("rawKeyDown"), "windowsVirtualKeyCode" -> num(code),
          "key" -> str(name)))
        _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("keyUp"), "windowsVirtualKeyCode" -> num(code),
          "key" -> str(name)))
        _ <- Task.sleep(250.millis)
      } yield ()
      def mouse(kind: String, x: Double, y: Double): Task[Unit] = browser.send("Input.dispatchMouseEvent", obj(
        "type" -> str(kind), "x" -> num(x.round.toInt), "y" -> num(y.round.toInt), "button" -> str("left"), "clickCount" -> num(1))).unit
      def demo(name: String): Task[Unit] = {
        val before = browser.lines.size
        def errors: List[String] = browser.lines.toArray.drop(before).map(_.toString)
          .filter(l => l.toLowerCase.contains("uncaught") || l.contains("ERROR")).toList
        for {
          _ <- browser.navigate(page + "?demo=" + name)
          _ <- Task.sleep(3.seconds)
          drawn <- browser.value("let n = 0; document.querySelectorAll('#app *').forEach(e => { " +
            "const r = e.getBoundingClientRect(); if (r.width > 0 && r.height > 0) n++; }); return n")
          _ <- shots.map(d => browser.screenshot(d.resolve(name + ".png"))).getOrElse(Task.unit)
          _ = check(name + ": its screen drawn (" + drawn.asInt + " boxes shown)", drawn.asInt >= 5)
          _ <- key(40, "ArrowDown")
          _ <- key(39, "ArrowRight")
          _ <- key(38, "ArrowUp")
          _ <- mouse("mouseMoved", 960.0, 540.0)
          _ <- mouse("mouseMoved", 700.0, 300.0)
          _ <- mouse("mousePressed", 700.0, 300.0)
          _ <- mouse("mouseReleased", 700.0, 300.0)
          _ <- Task.sleep(1.second)
          _ <- shots.map(d => browser.screenshot(d.resolve(name + "-after.png"))).getOrElse(Task.unit)
          _ = check(name + ": no error left uncaught" + errors.headOption.map(": " + _).getOrElse(""), errors.isEmpty)
        } yield ()
      }
      for {
        // (an error's whole stack, for the console)
        _ <- browser.addScript("Error.stackTraceLimit = 50")
        _ <- browser.size(1920, 1080)
        _ <- demos.map(demo).foldLeft(Task.unit)((a, b) => a.flatMap(_ => b))
      } yield ()
    }.map { _ =>
      println(if (failed.isEmpty) "[old chrome] all passed" else "[old chrome] failed: " + failed.mkString("; "))
      if (failed.nonEmpty) sys.exit(1)
    }
  }
}
