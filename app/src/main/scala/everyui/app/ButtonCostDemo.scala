package everyui.app

import everyui.component.{Button, Component, Container, IconButton, Row, TextView, ToggleButton}
import everyui.font.MaterialIcon
import everyui.layout.FlowLayout
import everyui.perf.PerfCounters
import everyui.picker.HighlightRow
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** What a control costs to make and to show: `n=` of a `kind=` (button, glyph, icon, toggle, highlight, text, or all of
  * them in turn), made on a turn of their own, put on the screen together on the next and drawn, `reps=` times, each
  * taken apart a moment later. Each round is logged, the making apart from the showing:
  *
  *   [ui] buttoncost button made 40 in 31.2ms (0.78ms each), shown in 21.0ms
  *
  * and, at the end, the median of each kind after its first round. With `perfCounters=1` each round's counts are
  * logged as well (`PerfCounters`: the layouts, reads, reads forced by a write, measures and writes), once made and
  * once shown. */
object ButtonCostDemo {
  private val kinds = List("button", "glyph", "icon", "toggle", "highlight", "text")

  def run(): Unit = {
    Theme.family @= "Open Sans"
    val kind = Platform.launchArg("kind")
    val n = Platform.launchArg("n").toIntOption.getOrElse(40)
    val reps = Platform.launchArg("reps").toIntOption.getOrElse(8)
    val order = if (kind.isEmpty || kind == "all") kinds else List(kind)
    Platform.after(2.0, () => round(order.flatMap(k => List.fill(reps)(k)), n, Map.empty))
  }

  /** One of `kind`, the `i`th. */
  private def make(kind: String, i: Int): Component = kind match {
    case "glyph" => new Button("Play " + i.toString, glyph = Some(MaterialIcon.PlayArrow))
    case "icon" => new IconButton(MaterialIcon.Settings, "Settings " + i.toString)
    case "toggle" => new ToggleButton(Var(false), "Bold " + i.toString, MaterialIcon.FormatBold)
    case "highlight" =>
      val row = new HighlightRow[String](s => s, _ => List(0 -> 2), _ => "detail")
      row.size.width @= 280.0
      row.size.height @= 32.0
      row.item @= Some("Item " + i.toString)
      row
    case "text" => Row(8.0, middle = true)(TextView("Name " + i.toString), TextView("1:04:05"))
    case _ => new Button("Button " + i.toString)
  }

  private def counts(when: String): Unit = if (PerfCounters.on)
    Platform.log("buttoncost counts " + when + ": " + PerfCounters.counts.filter(_._2 != 0).map(c => c._1 + " " +
      c._2.toString).mkString(", "))

  private def round(left: List[String], n: Int, times: Map[String, List[(Double, Double)]]): Unit = left match {
    case Nil =>
      times.foreach { (kind, all) =>
        val later = if (all.size > 1) all.drop(1) else all
        def median(xs: List[Double]): Double = xs.sorted.apply(xs.size / 2)
        Platform.log("buttoncost median " + kind + " made " + show(median(later.map(_._1)) / n) + "ms each, shown " +
          show(median(later.map(_._2)) / n) + "ms each")
      }
      Platform.log("buttoncost done")
    case kind :: rest =>
      if (PerfCounters.on) PerfCounters.reset()
      Platform.log("buttoncost begin " + kind)
      val started = Platform.ticks()
      val made = (0 until n).map(i => make(kind, i)).toVector
      val madeMs = Platform.ticks() - started
      Platform.log("buttoncost end " + kind)
      counts("made")
      Platform.later(() => {
        Platform.log("buttoncost show " + kind)
        val showing = Platform.ticks()
        val box = new Container {
          layout @= Some(FlowLayout(8.0, 8.0))
          size.width @= 1200.0
          position.left @= 40.0
          position.top @= 40.0
          children ++= made
        }
        everyui.ui.children += box
        Platform.nextFrame(() => Platform.later(() => {
          val shownMs = Platform.ticks() - showing
          counts("shown")
          Platform.log("buttoncost " + kind + " made " + n.toString + " in " + show(madeMs) + "ms (" +
            show(madeMs / n) + "ms each), shown in " + show(shownMs) + "ms")
          val more = times.updated(kind, times.getOrElse(kind, Nil) :+ (madeMs -> shownMs))
          Platform.after(0.4, () => {
            everyui.ui.children -= box
            box.dispose()
            Platform.after(0.6, () => round(rest, n, more))
          })
        }))
      })
  }

  private def show(ms: Double): String = (math.round(ms * 100.0) / 100.0).toString
}
