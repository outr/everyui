package everyui.webcheck

import everyui.memory.{MemoryRow, MemoryTable}
import fabric.*
import rapid.*
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.duration.*

/** The web's memory check: each case of the memory page (memory/web, `MemoryPage`) in a headless Chrome, measured over
  * the DevTools protocol, against the baseline. For each case, on a page of its own: the case's rig is put on the
  * screen and given a step of its units, and the page is let settle, its garbage collected three times
  * (`HeapProfiler.collectGarbage`) and read (the JavaScript heap used, and Blink's, where the DOM's nodes and styles
  * live, `Runtime.getHeapUsage`; the DOM nodes and event listeners, `Performance.getMetrics`; the live components,
  * `Component.liveNow`); then another step, read again, and the rig taken
  * apart, read once more. A unit costs the difference between the two steps over the step, so what the first of them
  * set up once is left out; "components left" is what taking the rig apart did not let go, per unit.
  *
  * The table is written to `out`, and each measure held to the baseline's with a tenth's margin (`margin`, and a heap's
  * noise, `slack`): one over fails the check. With `--update` the table becomes the baseline instead.
  *
  *   MemoryCheck <page dir> <baseline.md> <out.md> [--update] [--only=<case name>]
  */
object MemoryCheck extends RapidApp {
  private val columns = Vector("JS heap bytes", "DOM heap bytes", "DOM nodes", "listeners", "components",
    "components left")
  /** How far over its baseline each measure may go: a tenth, and a quarter for Blink's heap, which after collecting
    * still moves by up to a tenth from run to run (a tool call opened, say). */
  def margin(column: String): Double = if (column == "DOM heap bytes") 0.25 else 0.1

  /** What a measure may move by with nothing changed: a heap read after collecting moves by some tens of kilobytes over
    * a step, which is less per unit the more units a step has; a count does not move. */
  def slack(steps: Map[String, Int])(name: String, column: String): Double =
    if (column.endsWith("bytes")) 65536.0 / steps.getOrElse(name, 1) else 0.01

  override def run(args: List[String]): Task[Unit] = {
    val page = Paths.get(args.headOption.getOrElse(sys.error("usage: MemoryCheck <page dir> <baseline.md> <out.md> " +
      "[--update] [--only=<case>]"))).toAbsolutePath.resolve("index.html").toUri.toString
    val baseline = Paths.get(args(1))
    val out = Paths.get(args(2))
    val update = args.contains("--update")
    val only = args.collectFirst { case a if a.startsWith("--only=") => a.stripPrefix("--only=") }
    Profiles.around(RoboBrowser.withBrowser(RoboBrowserConfig(
      browserConfig = BrowserConfig(noSandbox = true, extraArgs = List("--mute-audio", "--window-size=1920,1080")),
      tabSelector = TabSelector.FirstPage
    )) { browser =>
      for {
        _ <- load(browser, page)
        listed <- browser.eval("return everyuiMemory.cases()")
        cases = text(listed).split("\n").toList.map(_.split("\t")).map(c => (c(0), c(1), c(2).toInt))
          .filter(c => only.forall(_ == c._1))
        rows <- cases.foldLeft(Task.pure(Vector.empty[MemoryRow])) { case (done, (name, unit, step)) =>
          done.flatMap(rows => measure(browser, page, name, unit, step).map(rows :+ _))
        }
      } yield report(MemoryTable(columns, rows), cases.map(c => c._1 -> c._3).toMap, baseline, out, update)
    }).map(within => if (!within) sys.exit(1)) // once the browser is closed
  }

  // the table written, and held to the baseline: whether it is within its budgets
  private def report(table: MemoryTable, steps: Map[String, Int], baseline: Path, out: Path,
                     update: Boolean): Boolean = {
    val heading = "# EveryUI's memory on the web, per unit of each case (MemoryCheck; headless Chrome)\n\n"
    Files.createDirectories(out.toAbsolutePath.getParent)
    Files.writeString(out, heading + table.markdown)
    println(table.markdown)
    if (update) {
      Files.writeString(baseline, heading + table.markdown)
      println("[memory] baseline written: " + baseline)
      true
    }
    else if (!Files.exists(baseline)) {
      println("[memory] no baseline at " + baseline + ": nothing to hold the run to")
      true
    }
    else {
      val over = table.over(MemoryTable.parse(Files.readString(baseline)), margin, slack(steps))
      over.foreach(o => println("[memory] OVER " + o))
      if (over.isEmpty) println("[memory] ok: " + table.rows.size.toString + " cases within their budgets")
      over.isEmpty
    }
  }

  private def load(browser: RoboBrowser, page: String): Task[Unit] = for {
    _ <- browser.navigate(page)
    _ <- browser.waitForLoaded()
    // the page up, or a minute gone: a page that failed to start fails the check rather than holding it
    _ <- browser.eval("return new Promise((r, no) => { const until = Date.now() + 60000; const wait = () => " +
      "window.everyuiMemory ? r(true) : Date.now() > until ? no('the memory page did not start') : " +
      "setTimeout(wait, 50); " +
      "wait(); })", awaitPromise = true)
    _ <- browser.send("Performance.enable")
  } yield ()

  private def measure(browser: RoboBrowser, page: String, name: String, unit: String, step: Int): Task[MemoryRow] = {
    val quoted = "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    for {
      _ <- load(browser, page)
      before <- read(browser)
      _ <- browser.eval("everyuiMemory.start(" + quoted + "); everyuiMemory.add(" + step.toString + "); return 0")
      first <- read(browser)
      _ <- browser.eval("everyuiMemory.add(" + step.toString + "); return 0")
      second <- read(browser)
      _ <- browser.eval("everyuiMemory.dispose(); return 0")
      after <- read(browser)
    } yield {
      val per = second.indices.map(i => (second(i) - first(i)) / step).toVector
      val row = MemoryRow(name, unit, per :+ (after(4) - before(4)) / (2 * step))
      println("[memory] " + name + ": " + columns.indices.map(i => columns(i) + " " +
        MemoryTable.show(columns(i), row.values(i))).mkString(", "))
      System.out.flush()
      row
    }
  }

  // the page settled and its garbage collected, then: the heaps used, the DOM nodes, the listeners, the live components
  private def read(browser: RoboBrowser): Task[Vector[Double]] = for {
    _ <- browser.eval("return new Promise(r => setTimeout(() => requestAnimationFrame(() => requestAnimationFrame(" +
      "() => setTimeout(() => r(0), 100))), 1000))", awaitPromise = true)
    _ <- browser.send("HeapProfiler.collectGarbage")
    _ <- browser.send("HeapProfiler.collectGarbage")
    _ <- Task.sleep(100.millis)
    _ <- browser.send("HeapProfiler.collectGarbage")
    heap <- browser.send("Runtime.getHeapUsage")
    metrics <- browser.send("Performance.getMetrics")
    live <- browser.eval("return everyuiMemory.live()")
  } yield {
    val named = metrics.result.value("metrics").asVector.map(m => m("name").asString -> m("value").asDouble).toMap
    Vector(heap.result.value("usedSize").asDouble, heap.result.value("embedderHeapUsedSize").asDouble, named("Nodes"),
      named("JSEventListeners"), live("result")("value").asDouble)
  }

  private def text(result: Json): String = result("result")("value").asString
}
