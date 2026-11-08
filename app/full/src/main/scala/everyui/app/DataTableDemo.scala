package everyui.app

import everyui.component.{Button, ButtonStyle, Row, ScrollContainer, TextView}
import everyui.component.types.TextAlign
import everyui.i18n.Direction
import everyui.render.Platform
import everyui.async.Task
import everyui.paging.{Page, PagedSource}
import everyui.table.{ColumnKind, DataCell, DataColumn, DataTable}
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A `DataTable` of an app's own rows: `n=` personas (500 by default) with their conversations, spend, share, when each
  * was last active and a note, sorted by a header, filtered, chosen, copied and downloaded; a small table of a few
  * rows under it, which offers no filter. `theme=light` and `rtl=1` as elsewhere; `max=<pixels>` sets its tallest, and
  * `at=<seconds>` makes the tables that long after the page. `paged=1` gives it a server's `n` personas (200,000 by
  * default) a page at a time instead, searched and sorted there.
  * `edit=1` lets its cells be typed over, on a double-click or F2.
  * `open=1` makes its rows open (`DataTable.onOpen`), as a console's open into a detail pane: a click or Enter opens
  * one, written under the tables until Close.
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

  // the `i`th persona's row as a server gives it: its number first, which keys it, shown and never exported
  private def numbered(i: Int): Vector[DataCell] = (DataCell((i + 1).toString) +: row(i).map(DataCell(_)))

  /** `n` personas as a server would give them, a page at a time a fifth of a second after it is asked: searched by the
    * words in a persona's name, a scan that says how far it has got, and sorted by the persona, up or down. */
  private def served(n: Int): PagedSource[Vector[DataCell]] = {
    val source = new PagedSource[Vector[DataCell]](_.head.text, pageSize = 100)(request =>
      Task.async[Either[String, Page[Vector[DataCell]]]] { done =>
        val words = request.query.toLowerCase
        // the scan through every persona for the words, in steps that say how far it has got
        def step(at: Int, found: Vector[Int]): Unit = if (request.live()) {
          if (at >= n) {
            val ordered = if (request.sort.exists(!_.ascending)) found.reverse else found
            done(Right(Page(ordered.slice(request.offset, request.offset + request.limit).map(i => numbered(i)),
              Some(found.size))))
          }
          else {
            val upTo = math.min(n, at + n / 5 + 1)
            val more = (at until upTo).filter(i => row(i).head.toLowerCase.contains(words))
            request.progress(upTo.toDouble / n)
            Platform.after(0.15, () => step(upTo, found ++ more))
          }
        }
        if (words.isEmpty) Platform.after(0.2, () => if (request.live()) {
          val ids = request.offset until math.min(n, request.offset + request.limit)
          val ordered = if (request.sort.exists(!_.ascending)) ids.map(i => n - 1 - i) else ids
          done(Right(Page(ordered.toVector.map(i => numbered(i)), Some(n))))
        })
        else step(0, Vector.empty)
      })
    source.searchable @= true
    source.sortable @= Set("persona")
    source
  }

  /** The tables made and put on `page`, timed. */
  private def show(page: ScrollContainer, width: Val[Double], left: Val[Double]): Unit = {
    val paged = Platform.launchArg("paged") == "1"
    val n = Option(Platform.launchArg("n")).filter(_.nonEmpty).map(_.toInt).getOrElse(if (paged) 200000 else 500)
    val start = Platform.ticks()
    val table = if (paged) {
      val source = served(n)
      val t = DataTable.paged(DataColumn("#", kind = Some(ColumnKind.Number), exported = false) +:
        columns.updated(0, columns(0).copy(key = "persona")), source)
      t.maxHeight @= 520.0
      t
    }
    else DataTable(columns, (0 until n).toVector.map(row))
    table.name @= "personas"
    // edit=1: its cells typed over on a double-click, what was typed logged
    if (Platform.launchArg("edit") == "1") {
      table.editable @= true
      table.edited.attach(e => Platform.log("datatable edited row " + e.row.toString + " column " + e.column.toString +
        ": " + e.before + " -> " + e.text))
    }
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
    // open=1: its rows open, as a console's open into a detail pane: what is open written under them, Close shuts it
    if (Platform.launchArg("open") == "1") {
      table.onOpen @= Some((i: Int) =>
        Platform.log("datatable opened row " + i.toString + ": " + table.rowTexts(i).head))
      val said = Val(table.opened().map(i => "Open: " + table.rowTexts(i).head).getOrElse("Nothing open: click a row"))
      val detail = Row(12.0, middle = true)(TextView.bound(said(), 13.0, Theme.text()),
        new Button("Close", () => table.opened @= None, style = ButtonStyle.Outlined))
      detail.position.left := left()
      detail.position.top := small.position.top() + small.size.height() + 16.0
      page.content.children += detail
    }
  }
}
