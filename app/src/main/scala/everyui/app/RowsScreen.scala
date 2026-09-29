package everyui.app

import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Column, Component, Container, ProgressBar, Row, TextView}
import everyui.layout.FlowLayout
import everyui.net.*
import everyui.render.Platform
import everyui.theme.Theme
import everyui.util.{Clock, Format}
import reactify.*

/** The rows demo's one screen, a dashboard as DataWrangler's is built: a panel of gauges above a column of runs, each
  * bound to what the socket last said (`children.bind`), a running one's duration ticking with the clock. It builds
  * `n=` rows (200 when not given), then adds a hundred more a row at a time, timing each from the start to the turn it
  * settles. Then, with `stream=<pushes a second>`, it hears pushes as a live socket brings them: each changes `burst=`
  * runs (8 when not given) and the gauges, which the panel draws again whole. Without, it stays as it is: still, with
  * `running=0`, where no run is left running and so nothing follows the clock. */
class RowsScreen extends ScrollScreen {
  override def manager: ScreenManager = RowsDemo
  override def title: String = "Rows"
  override def path: URLPath = path"/rows"

  /** The runs, as the last push left them. */
  val runs: Var[List[RowRun]] = Var(Nil)
  /** The gauges, as the last push left them: a name and how full. */
  val gauges: Var[List[(String, Double)]] = Var(Nil)

  private val caption = new TextView {
    content @= "Runs"
    font.size @= 18.0
    color := Theme.text()
    focusable @= true
  }
  private val panel = new Container
  private val column = Column(2.0)()
  private var pushes = 0

  override def defaultFocus(): Component = caption

  /** Children made afresh for each value, so taken apart as each leaves (`bindMaking`); with `keep=1`, `bind`, as a
    * dashboard that holds on to them has it, which leaves each one bound to the theme and the clock for good. */
  private def bound[T](c: Container, values: => List[T])(render: T => Component): Unit =
    if (Platform.launchArg("keep") == "1") c.children.bind(values)(render) else c.children.bindMaking(values)(render)

  override def createUI(): Task[Unit] = Task {
    container.backgroundColor := Theme.background()
    panel.size.width := body.size.width
    panel.layout @= Some(FlowLayout(8.0, 8.0))
    // the whole panel is one value, drawn again on every push, as a dashboard's activity panel is
    bound(panel, gauges()) { case (name, full) =>
      Column(4.0, padding = 8.0)(TextView(name, 11.0, Theme.muted()), TextView(math.round(full * 100.0).toString + "%", 12.0),
        ProgressBar(full, 180.0))
    }
    bound(column, runs())(row)
    body.children ++= List(caption, panel, column)
  }

  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    val asked = Platform.launchArg("n")
    val n = if (asked.isEmpty) 200 else asked.toInt
    val now = Clock.real()
    val ticking = Platform.launchArg("running") != "0"
    val start = Platform.ticks()
    gauges @= RowsScreen.gaugeNames.map(name => name -> 0.4)
    runs @= (0 until n).toList.map(i => RowRun(i, now, ticking))
    settled(n, () => {
      Platform.log("rows " + n + " built in " + elapsed(start) + " ms")
      val more = Platform.ticks()
      (n until n + 100).foreach(i => runs @= runs() :+ RowRun(i, now, ticking))
      settled(n + 100, () => {
        Platform.log("rows 100 appended in " + elapsed(more) + " ms")
        val rate = Platform.launchArg("stream").toDoubleOption.getOrElse(0.0)
        if (rate > 0.0) stream(1.0 / rate, Platform.launchArg("burst").toIntOption.getOrElse(8))
      })
    })
  }

  private def row(r: RowRun): Container =
    Row(12.0, middle = true)(words("#" + r.index), words(r.pipeline), words(r.status), words(r.records + " records"),
      TextView.bound(Format.elapsed(r.started, r.finished)))

  private def words(s: String): TextView = TextView(s)

  /** A push every `seconds`: `burst` runs changed, spread over the list, and every gauge moved. */
  private def stream(seconds: Double, burst: Int): Unit = Platform.after(seconds, () => {
    pushes += 1
    val all = runs()
    val step = math.max(1, all.length / burst)
    val changed = (0 until burst).map(k => (pushes * 7 + k * step) % all.length).toSet
    runs @= all.map(r => if (changed.contains(r.index)) r.copy(records = r.records + 1,
      status = if (r.finished.isEmpty) "running" else if (pushes % 5 == 0) "failed" else "done") else r)
    gauges @= gauges().zipWithIndex.map { case ((name, full), i) => name -> ((full + 0.013 * (i + 1)) % 1.0) }
    stream(seconds, burst)
  })

  private def elapsed(since: Double): String = (math.round((Platform.ticks() - since) * 10.0) / 10.0).toString

  /** `next` once every row has its size and the column reaches the bottom of the last: on a turn of its own each time it
    * looks again, which a timer of no length is not on the desktop. */
  private def settled(count: Int, next: () => Unit): Unit = {
    val rows = column.children()
    val last = rows.lastOption
    val done = rows.length == count && rows.forall(_.size.height() > 0.0) &&
      last.exists(r => r.position.top() > 0.0 && column.size.height() == r.position.top() + r.size.height())
    if (done) next() else Platform.later(() => settled(count, next))
  }
}

object RowsScreen {
  val gaugeNames: List[String] = List("CPU", "Heap", "Physical Memory", "Swap", "Disk /", "Disk /data", "Threads",
    "Queue", "Workers", "Open files")
}
