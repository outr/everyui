package everyui.app

import everyui.component.{Button, ButtonStyle, Row, ScrollContainer, TextView}
import everyui.component.types.TextAlign
import everyui.i18n.Direction
import everyui.render.Platform
import everyui.table.{ColumnKind, DataColumn, DataTable}
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A `DataTable` of an app's own rows: `n=` personas (500 by default) with their conversations, spend, share, when each
  * was last active and a note, sorted by a header, filtered, chosen, copied and downloaded; a small table of a few
  * rows under it, which offers no filter. `theme=light` and `rtl=1` as elsewhere; `max=<pixels>` sets its tallest, and
  * `at=<seconds>` makes the tables that long after the page.
  * It logs how long the table took to make and to filter:
  *
  *   [ui] datatable 500 rows made in 92.4 ms
  */
object DataTableDemo {
  private val first = Vector("Ada", "Grace", "Alan", "Edsger", "Barbara", "Donald", "Margaret", "Ken", "Dennis", "Frances",
    "Leslie", "Radia", "Tim", "Vint", "Hedy", "Katherine", "John", "Sophie", "Niklaus", "Guido")
  private val roles = Vector("Researcher", "Reviewer", "Planner", "Coder", "Tester", "Writer", "Analyst", "Designer")
  private val models = Vector("Large", "Medium", "Small", "Local 8B", "Local 70B")
  private val notes = Vector("Short replies", "Writes long, careful answers with tables and code",
    "Asks before acting", "", "Reads the whole repository first, then proposes a plan in steps", "n/a",
    "Prefers `sbt` over scripts", "Escalates to a person when unsure")

  /** The `i`th persona's row: a name, conversations, spend, share, last active, model and a note. */
  def row(i: Int): Vector[String] = {
    val conversations = (i * 7919 + 13) % 4200
    val spend = ((i * 104729) % 1000000) / 100.0
    val share = ((i * 31) % 1000) / 10.0
    val day = 1 + (i * 17) % 28
    val month = 1 + (i * 5) % 12
    val year = 2024 + (i % 3)
    Vector(first(i % first.size) + " the " + roles(i % roles.size) + " " + (i / (first.size * roles.size) + 1).toString,
      grouped(conversations.toLong),
      "$" + grouped(spend.toLong) + "." + two(((spend * 100).round % 100).toInt),
      share.toString + "%",
      year.toString + "-" + two(month) + "-" + two(day),
      models(i % models.size),
      notes(i % notes.size))
  }

  // `n` in two digits
  private def two(n: Int): String = (if (n < 10) "0" else "") + n.toString

  /** `n` with commas between its thousands. */
  def grouped(n: Long): String = n.toString.reverse.grouped(3).mkString(",").reverse

  val columns: Vector[DataColumn] = Vector(DataColumn("Persona"), DataColumn("Conversations"), DataColumn("Spend"),
    DataColumn("Share"), DataColumn("Last active", kind = Some(ColumnKind.Date)), DataColumn("Model"),
    DataColumn("Notes", align = Some(TextAlign.Start)))

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val width = Val(math.min(1000.0, ui.size.width() - 32.0))
    val left = Val((ui.size.width() - width()) / 2.0)
    val title = TextView("Personas", 18.0, Theme.text())
    val note = TextView.bound("Click a header to sort; click, Shift+click and Ctrl+click rows; Ctrl+C copies them",
      12.0, Theme.muted())
    val theme = new Button("Light / dark", () =>
      Theme.palette @= (if (Theme.palette() eq Palette.light) Palette.dark else Palette.light), style = ButtonStyle.Outlined)
    val bar = Row(12.0, middle = true)(title, theme)
    val page = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    bar.position.left := left()
    bar.position.top @= 12.0
    note.position.left := left()
    note.position.top @= 56.0
    page.content.children ++= List(bar, note)
    ui.children += page
    val at = Platform.launchArg("at")
    if (at.isEmpty) show(page, width, left) else Platform.after(at.toDouble, () => show(page, width, left))
  }

  /** The tables made and put on `page`, timed. */
  private def show(page: ScrollContainer, width: Val[Double], left: Val[Double]): Unit = {
    val n = Option(Platform.launchArg("n")).filter(_.nonEmpty).map(_.toInt).getOrElse(500)
    val rows = (0 until n).toVector.map(row)
    val start = Platform.ticks()
    val table = DataTable(columns, rows)
    table.name @= "personas"
    Option(Platform.launchArg("max")).filter(_.nonEmpty).foreach(m => table.maxHeight @= m.toDouble)
    table.size.width := width()
    table.position.left := left()
    table.position.top @= 80.0
    page.content.children += table
    val made = Platform.ticks() - start
    Platform.log("datatable " + n.toString + " rows made in " + (math.round(made * 10.0) / 10.0).toString + " ms")
    table.filter.attach { words =>
      val began = Platform.ticks()
      Platform.afterTurn(() => Platform.log("datatable filtered for \"" + words + "\" to " + table.shown().size.toString +
        " rows in " + (math.round((Platform.ticks() - began) * 10.0) / 10.0).toString + " ms"))
    }
    val small = DataTable(Vector(DataColumn("Provider"), DataColumn("Models"), DataColumn("Price per million")),
      Vector(Vector("Northwind", "3", "$15.00"), Vector("Fireworks.ai", "12", "$0.90"), Vector("Ollama", "any", "free")))
    small.size.width := width()
    small.position.left := left()
    small.position.top := table.position.top() + table.size.height() + 24.0
    page.content.children += small
  }
}
