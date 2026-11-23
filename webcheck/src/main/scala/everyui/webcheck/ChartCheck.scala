package everyui.webcheck

import fabric.*
import rapid.*

import java.nio.file.Paths
import scala.concurrent.duration.*

/** A chart's marks in a real Chrome, by the protocol's own mouse, touches and keys (not events made in the page): the
  * example's `demo=charts`, its "Spend, last 7 days" bars, which lead somewhere (`Chart.actionable`). The pointer moving
  * over the bars is heard by the chart (its axes' words laid over the plot let it through) and its tip follows, with
  * what choosing does; the hand shows over those bars, and not over another chart's that lead nowhere; a click chooses
  * a bar (`Chart.chosen`), a drag across the bars chooses none; Tab reaches the chart, the arrows move along its bars,
  * and Enter and Space choose; its table's days are buttons that choose; and a finger's tap chooses a bar, where one
  * swiped up from a bar scrolls the page and chooses none (where the engine's protocol has touches: not Chromium 38's,
  * whose page is linked as ES 5.1). Exits 1 where a check fails.
  *
  *   ChartCheck <page dir> [--chrome <binary>]
  */
object ChartCheck extends RapidApp {
  private val usage = "usage: ChartCheck <page dir> [--chrome <binary>]"

  override def run(args: List[String]): Task[Unit] = {
    val chrome = args.dropWhile(_ != "--chrome").drop(1).headOption.getOrElse(DevTools.defaultChrome)
    val page = Paths.get(args.headOption.getOrElse(sys.error(usage))).toAbsolutePath.resolve("index.html").toUri.toString
    var failed = List.empty[String]
    def check(what: String, ok: Boolean): Unit = {
      println((if (ok) "[chart] ok " else "[chart] FAILED ") + what)
      System.out.flush()
      if (!ok) failed :+= what
    }
    DevTools.around(chrome) { browser =>
      def told(what: String, from: Int): List[String] =
        browser.lines.toArray.drop(from).map(_.toString).toList.filter(_.contains(what))
      def all(tasks: List[Task[Unit]]): Task[Unit] = tasks.foldLeft(Task.unit)((a, b) => a.flatMap(_ => b))
      def mouse(kind: String, x: Double, y: Double, pressed: Boolean = false): Task[Unit] =
        browser.send("Input.dispatchMouseEvent", obj("type" -> str(kind), "x" -> num(x), "y" -> num(y),
          "button" -> str(if (pressed || kind != "mouseMoved") "left" else "none"), "clickCount" -> num(1),
          "buttons" -> num(if (pressed || kind == "mousePressed") 1 else 0))).unit
      def click(x: Double, y: Double): Task[Unit] = for {
        _ <- mouse("mouseMoved", x, y)
        _ <- mouse("mousePressed", x, y)
        _ <- mouse("mouseReleased", x, y)
        _ <- Task.sleep(300.millis)
      } yield ()
      def key(name: String, code: Int): Task[Unit] = for {
        _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("rawKeyDown"), "key" -> str(name),
          "code" -> str(if (name == " ") "Space" else name), "windowsVirtualKeyCode" -> num(code)))
        _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("keyUp"), "key" -> str(name),
          "code" -> str(if (name == " ") "Space" else name), "windowsVirtualKeyCode" -> num(code)))
        _ <- Task.sleep(200.millis)
      } yield ()
      def touch(kind: String, points: List[(Double, Double)]): Task[Unit] =
        browser.send("Input.dispatchTouchEvent", obj("type" -> str(kind), "touchPoints" -> Arr(points.zipWithIndex
          .map { case ((x, y), i) => obj("x" -> num(x), "y" -> num(y), "id" -> num(i)) }.toVector))).unit
      // the chart named `name` (its figure's label begins so), as `f`
      def figure(name: String): String = "var f = null, fs = document.querySelectorAll('[role=figure]'); " +
        "for (var i = 0; i < fs.length; i++) if (fs[i].getAttribute('aria-label').indexOf('" + name + "') === 0) f = fs[i]; "
      val spend = figure("Bar chart: Spend")
      def boxOf(find: String): Task[Vector[Double]] =
        browser.value(find + "var r = f.getBoundingClientRect(); return [r.left, r.top, r.width, r.height]")
          .map(_.asVector.map(_.asDouble))
      def cursorAt(x: Double, y: Double): Task[String] =
        browser.value("return getComputedStyle(document.elementFromPoint(" + x + ", " + y + ")).cursor").map(_.asString)
      // along the chart's foot from its start, until a bar is pointed at: where it was, or None
      def sweep(find: String): Task[Option[(Double, Double)]] = boxOf(find).flatMap { b =>
        val y = b(1) + b(3) - 40.0
        val from = browser.lines.size
        def at(x: Double): Task[Option[(Double, Double)]] =
          if (x > b(0) + b(2)) Task.pure(None)
          else mouse("mouseMoved", x, y).flatMap(_ => Task.sleep(30.millis)).flatMap { _ =>
            if (told("charts pointed", from).nonEmpty) Task.pure(Some((x + 4.0, y))) else at(x + 6.0)
          }
        at(b(0) + 2.0)
      }
      val focusedIsSpend = spend + "return document.activeElement === f"
      def tabTo(left: Int): Task[Boolean] = browser.value(focusedIsSpend).flatMap { on =>
        if (on.asBoolean) Task.pure(true) else if (left == 0) Task.pure(false)
        else key("Tab", 9).flatMap(_ => tabTo(left - 1))
      }
      // a finger: a tap chooses, a swipe up from a bar scrolls the page and chooses none; not where the engine's
      // protocol has no touches (Chromium 38's), said
      def fingers(bar: Option[(Double, Double)]): Task[Unit] = browser.call("Emulation.setTouchEmulationEnabled",
        obj("enabled" -> bool(true), "maxTouchPoints" -> num(5))).flatMap { answer =>
        if (answer.get("error").nonEmpty || bar.isEmpty) Task(println("[chart] a finger's tap and swipe: not checked, " +
          (if (bar.isEmpty) "no bar found" else "no touches in this engine's protocol")))
        else {
          val (x, y) = bar.get
          val tapped = browser.lines.size
          for {
            _ <- touch("touchStart", List((x, y)))
            _ <- touch("touchEnd", Nil)
            _ <- Task.sleep(400.millis)
            _ = check("a tap chooses the bar (" + told("charts chosen", tapped).mkString + ")",
              told("charts chosen", tapped).size == 1)
            before <- boxOf(spend)
            swiped = browser.lines.size
            _ <- touch("touchStart", List((x, y)))
            _ <- all((1 to 15).toList.map(i => touch("touchMove", List((x, y - i * 16.0))).flatMap(_ => Task.sleep(16.millis))))
            _ <- touch("touchEnd", Nil)
            _ <- Task.sleep(800.millis)
            after <- boxOf(spend)
          } yield {
            check("a finger swiped up from a bar scrolls the page (" + (before(1) - after(1)).round + " px)",
              before(1) - after(1) > 100.0)
            check("and chooses none (" + told("charts chosen", swiped).mkString + ")", told("charts chosen", swiped).isEmpty)
          }
        }
      }
      for {
        _ <- browser.addScript("Error.stackTraceLimit = 50")
        _ <- browser.size(1280, 800)
        _ <- browser.navigate(page + "?demo=charts")
        _ <- DevTools.until(Task(told("charts ok", 0).nonEmpty), 20.0)(identity)
        _ <- Task.sleep(500.millis)
        said <- browser.value(spend + "return f.getAttribute('aria-description') || ''")
        _ = check("the figure says its bars can be pressed (" + said.asString + ")", said.asString == "Press a bar to open it.")
        // the pointer over the bars: heard, the tip shown with what choosing does, the hand
        from = browser.lines.size
        bar <- sweep(spend)
        b <- boxOf(spend)
        _ <- all((0 to 40).toList.map(i => mouse("mouseMoved", b(0) + i * b(2) / 40.0, b(1) + b(3) - 40.0)))
        _ = check("the pointer moving over the bars is heard (" + told("charts pointed", from).size + " bars)",
          told("charts pointed", from).size >= 5)
        _ <- bar.map { case (x, y) => mouse("mouseMoved", x, y) }.getOrElse(Task.unit)
        _ <- Task.sleep(200.millis)
        tip <- browser.value("var all = document.querySelectorAll('#app *'); for (var i = 0; i < all.length; i++) " +
          "if (all[i].children.length === 0 && /^Open the spend on /.test(all[i].textContent)) return all[i].textContent; return ''")
        _ = check("the tip follows the pointer, with what choosing does (" + tip.asString + ")", tip.asString.nonEmpty)
        hand <- bar.map { case (x, y) => cursorAt(x, y) }.getOrElse(Task.pure(""))
        _ = check("the hand over a bar that leads somewhere (" + hand + ")", hand == "pointer")
        other <- sweep(figure("Bar chart: Meetings"))
        arrow <- other.map { case (x, y) => cursorAt(x, y) }.getOrElse(Task.pure(""))
        _ = check("no hand over a bar that leads nowhere (" + arrow + ")", other.nonEmpty && arrow != "pointer")
        // a click chooses; a drag across the bars does not
        clicked = browser.lines.size
        _ <- bar.map { case (x, y) => click(x, y) }.getOrElse(Task.unit)
        _ = check("a click chooses the bar (" + told("charts chosen", clicked).mkString + ")",
          told("charts chosen", clicked).size == 1)
        dragged = browser.lines.size
        _ <- bar.map { case (x, y) =>
          for {
            _ <- mouse("mousePressed", x, y)
            _ <- all((1 to 10).toList.map(i => mouse("mouseMoved", x + i * 8.0, y, pressed = true)))
            _ <- mouse("mouseReleased", x + 80.0, y)
            _ <- Task.sleep(300.millis)
          } yield ()
        }.getOrElse(Task.unit)
        _ = check("a drag across the bars chooses none (" + told("charts chosen", dragged).mkString + ")",
          bar.nonEmpty && told("charts chosen", dragged).isEmpty)
        // Tab reaches the chart; the arrows move along its bars; Enter and Space choose
        _ <- mouse("mouseMoved", 2.0, 2.0)
        reached <- tabTo(40)
        _ = check("Tab reaches the chart", reached)
        keyed = browser.lines.size
        _ <- key("ArrowRight", 39)
        _ <- key("ArrowRight", 39)
        _ = check("the arrows move along its bars (" + told("charts pointed", keyed).mkString("; ") + ")",
          told("charts pointed", keyed).size == 2)
        _ <- key("Enter", 13)
        _ <- key(" ", 32)
        _ = check("Enter and Space choose the bar the keys are on (" + told("charts chosen", keyed).mkString("; ") + ")",
          told("charts chosen", keyed).size == 2 && told("charts chosen", keyed).forall(_.contains("Fri")))
        // its table: each day a button that chooses
        toggle <- browser.value(spend + "var t = f.querySelector('[aria-label=\"Show the data as a table\"]'); " +
          "var r = t.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]").map(_.asVector.map(_.asDouble))
        _ <- click(toggle(0), toggle(1))
        day <- DevTools.until(browser.value(spend + "var bs = f.querySelectorAll('[role=button]'); " +
          "for (var i = 0; i < bs.length; i++) if (bs[i].textContent === 'Sat') { var r = bs[i].getBoundingClientRect(); " +
          "return [r.left + r.width / 2, r.top + r.height / 2]; } return null"))(_.isArr)
        _ = check("its table's days are buttons", day.isArr)
        tabled = browser.lines.size
        _ <- if (day.isArr) click(day.asVector(0).asDouble, day.asVector(1).asDouble) else Task.unit
        _ = check("a day's button in the table chooses it (" + told("charts chosen", tabled).mkString + ")",
          told("charts chosen", tabled).exists(_.contains("Sat")))
        _ <- click(toggle(0), toggle(1))
        _ <- fingers(bar)
        errors = told("", 0).filter(l => l.toLowerCase.contains("uncaught") || l.contains("ERROR"))
        _ = check("no error left uncaught" + errors.headOption.map(": " + _).getOrElse(""), errors.isEmpty)
      } yield ()
    }.map { _ =>
      println(if (failed.isEmpty) "[chart] all passed" else "[chart] failed: " + failed.mkString("; "))
      if (failed.nonEmpty) sys.exit(1)
    }
  }
}
