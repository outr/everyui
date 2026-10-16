package everyui.table

import everyui.{Color, Gradient, GradientStop}
import everyui.clipboard.Clipboard
import everyui.component.{Component, Container, Link, MaterialIconView, RichText, ScrollContainer, TextView}
import everyui.component.types.{Semantics, TextAlign, WhiteSpace}
import everyui.component.feature.MeasuredFeature
import everyui.event.{Key, KeyEvent, PointerEvent}
import everyui.file.Files
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.{SearchField, Toast}
import everyui.layout.{Layout, LayoutPass, VerticalLayout}
import everyui.list.ListRow
import everyui.markdown.{TableLook, TableWidths}
import everyui.menu.{Menu, MenuItem}
import everyui.render.{Gestures, Platform}
import everyui.theme.Theme
import reactify.*

/** A table of rows of words that the person can sort, filter, copy and export: an app's own data (`DataTable(columns,
  * rows)`, or `DataTable.of(items)(columns)` from typed items), and every Markdown table (`MarkdownView`).
  *
  * It is laid out as a Markdown table always was (`TableWidths`), and drawn as its style's `look` says (`TableLook`: by
  * default a header band, every second row striped, rounded corners and no rules between the cells): each column as
  * wide as its widest cell on one line where all fit, else sharing the width so a column of phrases wraps more than one
  * of short words, no word ever cut; only where the widest words alone are wider than it does it scroll sideways, a
  * shade at each edge with more beyond it. Cells wrap within their columns rather than being cut, so there is nothing
  * hidden for a tip to show. Taller than `maxHeight`, its rows scroll under a header that stays.
  * A table whose words read right to left has its first column at the right.
  *
  *   - Sort: a click or a tap on a header (or Enter on it) sorts the rows by that column, up, then down, then back as
  *     they came, an arrow by the header saying which; numbers sort by their value, dates by when they are, words as
  *     the language orders them (`CellValues`). The header says so to a screen reader (`aria-sort`).
  *   - Filter: past `filterAfter` rows a field over the table hides the rows without every word typed in it (in any
  *     cell, whatever the case), "12 of 60" beside it.
  *   - Choose: a click on a row chooses it (again, it is let go), Shift+click the rows from the last one chosen to it,
  *     Ctrl or Command+click adds or takes away one; a finger's tap adds or takes away, on any device. Ctrl or
  *     Command+C copies the rows chosen (or, with none, the cell the keys are on) as tab-separated lines under the
  *     header, so they paste into a spreadsheet; where the page has words selected with the pointer (a drag across
  *     cells, on the web) those are copied instead, as the browser copies them.
  *   - Copy and export: the tools' button (⋮), shown while the pointer is over the table or the focus is in it, and
  *     always on a touch screen, copies the rows showing (sorted and filtered) as tab-separated lines or as Markdown,
  *     or hands them over as a CSV or a Markdown file (`Files.download`: the browser's downloads on the web, the save
  *     dialog on the desktop and Android). Where the platform has no clipboard the copies are not offered, nor the
  *     files where it cannot save one.
  *   - Keys: the table takes the focus as one stop (Tab goes on past it). The arrows move between its cells (Up from the
  *     first row reaches the header), Home and End to the first and last column (with Ctrl, row), Page Up and Down a
  *     page of rows; Space or Enter chooses the row (Enter on a header sorts), Shift with Up or Down chooses as it
  *     goes, Ctrl or Command+A chooses every row showing, Escape lets them go.
  *
  * Past `DataTable.eager` rows, the rows are made a page a turn after the first page is drawn, and only the widest cells
  * of each column (by their characters and their longest words) are measured for the columns' widths, so a long table
  * does not hold the page up as it is made, nor as it is filtered: a cell wider than those measured wraps.
  *
  * {{{
  *   val table = DataTable(Vector(DataColumn("Persona"), DataColumn("Conversations"), DataColumn("Last active")),
  *     personas.map(p => Vector(p.name, p.count.toString, p.last)))
  *   table.size.width := panel.size.width()
  *   table.name @= "personas"           // personas.csv
  * }}} */
class DataTable(initialColumns: Vector[DataColumn] = Vector.empty, initialCells: Vector[Vector[DataCell]] = Vector.empty,
                val style: DataTableStyle = DataTableStyle(), initialWidth: => Double = 600.0) extends Container {
  /** The columns: changed, the table is drawn again. */
  val columns: Var[Vector[DataColumn]] = own(Var(initialColumns))
  /** The rows' cells, a row a vector, as many as there are columns (missing ones empty): changed, the rows that did
    * not change keep what draws them. */
  val cells: Var[Vector[Vector[DataCell]]] = own(Var(initialCells))
  /** The column the rows are sorted by, and which way; None as they came. */
  val sort: Var[Option[ColumnSort]] = own(Var(None))
  /** The words a row must hold to show. */
  val filter: Var[String] = own(Var(""))
  /** The rows chosen, by their index in `cells`. */
  val selection: Var[Set[Int]] = own(Var(Set.empty))
  /** How many rows a table has before it offers its filter. */
  val filterAfter: Var[Int] = own(Var(8))
  /** The tallest the table is drawn before its rows scroll under its header (0: as tall as its rows). */
  val maxHeight: Var[Double] = own(Var(420.0))
  /** Whether it offers its tools (copy and export). */
  val tools: Var[Boolean] = own(Var(true))
  /** The name its files are given, without their extension. */
  val name: Var[String] = own(Var("table"))
  /** What a click on a link in a cell does: by default a path of this app is shown in place, anything else opened. */
  var onLink: String => Unit = href => Link.follow(href, newTab = !RichText.inApp(href))

  private val look = style.look
  private val pad = look.across
  private val columnCount: Val[Int] = own(Val(columns().size))
  // the rows, by their index in `cells`, as they are made; and a count that changes as they are
  private var slots: Vector[Option[DataRow]] = Vector.empty
  private val made: Var[Int] = own(Var(0))
  private var making = 0
  // the words of each row, in lower case, for the filter
  private var haystacks: Vector[String] = Vector.empty
  // the cells of a long table measured for its columns' widths: each one's column, on one line and a word to a line
  private var samples: Vector[(Int, RichText, RichText)] = Vector.empty
  // the measuring cells made and not yet out of sight with the others
  private var unplaced: Vector[RichText] = Vector.empty
  // what each column holds, found once for the cells it was found for
  private var kinds: Vector[ColumnKind] = Vector.empty
  private var kindsFor: AnyRef = null
  // where the keys are: the row (-1 the header, else an index in `cells`) and the column; and the row a choice runs from
  private val cursor: Var[(Int, Int)] = own(Var((-1, 0)))
  private val ringShown: Var[Boolean] = own(Var(false))
  private var anchor: Option[Int] = None
  // its columns from the right where its words read right to left
  private val mirrored: Boolean = everyui.text.Bidi.rightToLeft(style.direction.value,
    initialColumns.map(_.title).mkString(" "), Direction.rtl())

  // each cell measured out of sight and taking no room: on one line, and a word to a line
  private val twins: Container = new Container {
    size.width @= 0.0
    size.height @= 0.0
    visible @= false
  }
  // the filter's words as the rows take them: changed with the layout held, so the rows that come and go are laid out
  // once for the change rather than once each
  private val query: Var[String] = own(Var(""))
  private[table] val passing: Val[Vector[Boolean]] = own(Val {
    made()
    val typed = query().trim.toLowerCase
    val words = if (typed.isEmpty) Vector.empty[String] else typed.split("\\s+").toVector
    if (words.isEmpty) Vector.fill(haystacks.size)(true) else haystacks.map(h => words.forall(w => h.contains(w)))
  })
  // the rows in the order they are sorted, those the filter hides among them
  private val ordered: Val[Vector[Int]] = own(Val {
    val all = cells()
    sort() match {
      case Some(s) if s.column < columnCount() =>
        CellValues.order(all.map(r => r.lift(s.column).map(_.text).getOrElse("")), kindOf(s.column), s.ascending)
      case _ => all.indices.toVector
    }
  })
  /** The rows in the order they show: sorted where the table is, and those the filter hides left out. */
  val shown: Val[Vector[Int]] = own(Val {
    val pass = passing()
    ordered().filter(i => i >= pass.size || pass(i))
  })
  // the header's row, made again with the columns
  private val head: Var[DataRow] = own(Var[DataRow](null))
  // each column's widest measured cell of the rows, a word to a line and on one line, kept as they are measured
  private val bodyLeast: Var[Vector[Double]] = own(Var(Vector.empty))
  private val bodyMost: Var[Vector[Double]] = own(Var(Vector.empty))
  // how each column's cells are aligned, found for the columns and the cells
  private[table] val aligns: Var[Vector[TextAlign]] = own(Var(Vector.empty))
  private val least: Val[Vector[Double]] = own(Val(widest(words = true)))
  private val most: Val[Vector[Double]] = own(Val(widest(words = false)))
  // room for the page's scrollbar beside the columns, where the rows are surely too many to show at once and the page draws
  // one (the web): over the room's end (its left in a right-to-left page), never over the last column's words. By all the
  // rows, not those the filter shows, so filtering never changes the columns' widths (and so every cell's lines)
  private val gutter: Val[Double] = own(Val(if (Platform.nativeScroll() && maxHeight() > 0.0 &&
    cells().size * (style.points * 1.2 + look.down * 2.0) > maxHeight()) DataTable.gutter else 0.0))
  private val widths: Val[Vector[Double]] = own(Val(TableWidths(least(), most(), size.width() - gutter())))
  private val starts: Val[Vector[Double]] = own(Val(widths().scanLeft(0.0)(_ + _).dropRight(1)))
  private[table] val across: Val[Double] = own(Val(widths().foldLeft(0.0)(_ + _) + gutter()))
  private val headHeight: Val[Double] = own(Val(Option(head()).map(_.size.height()).getOrElse(0.0)))

  // the rows, in a column, under the header; and the rules down between the columns, over them, where the look rules them
  private val body = new Container {
    layout @= Some(VerticalLayout(0.0))
  }
  private val lines = new Container
  private val rows = new ScrollContainer
  private val grid: Container = new Container {
    semantics @= Semantics("table")
    copyAs @= "table"
    radius @= look.radius
    focusable @= true

    override def keepsEvent(e: KeyEvent): Boolean = action(e).nonEmpty
  }
  // the table's edge, over the header and the rows
  private val edge = TableLook.edge(look)
  /** Where each row shows, by its index in `cells`: its place among the rows showing (0 the first), or -1 where the
    * filter hides it. Found only where the rows are striped. */
  private[table] val places: Val[Vector[Int]] = own(Val(if (!look.zebra) Vector.empty else {
    val at = shown().zipWithIndex.toMap
    Vector.tabulate(cells().size)(i => at.getOrElse(i, -1))
  }))
  /** Whether the focus is on the table itself, and so its choice drawn in the accent. */
  private[table] val active: Val[Boolean] = own(Val(grid.focused()))
  private val scroll = new ScrollContainer(horizontal = true, followsDirection = mirrored)
  // where the table starts across its width: at its right where its columns run from the right
  private val gridLeft: Val[Double] = own(Val(if (mirrored) size.width() - scroll.size.width() else 0.0))
  // how far there is to scroll on each side: its start is its right end where it runs from the right
  private val beyond: Val[Double] = own(Val(math.max(0.0, across() - scroll.size.width())))
  private val fromRight: Val[Boolean] = own(Val(mirrored && Direction.rtl()))
  /** Whether it is made for a pointer that hovers (its tools shown on hover) or for a finger (always shown). */
  private val touch: Boolean = Platform.touchSupport() && !Platform.pointerSupport()
  private val copies: Boolean = Clipboard.copies
  private val saves: Boolean = Files.savable
  // whether the tools are wanted at all, the filter is offered, and the pointer is over it or the focus in it
  private val offered: Val[Boolean] = own(Val(tools() && (copies || saves)))
  private val filtering: Val[Boolean] = own(Val(cells().size > filterAfter()))
  /** Whether the pointer is over it as what holds it knows (a Markdown table made under the pointer, which hears no
    * pointer come in). */
  private[everyui] val hovered: Var[Boolean] = own(Var(false))
  private lazy val near: Val[Boolean] = own(Val(hovered() || event.pointer.over() ||
    Focus.focused().exists(f => Focus.within(f, this))))
  // room beside the table for its tools, where it is narrower than its width
  private val beside: Val[Boolean] = own(Val(size.width() - across() >= DataTable.toolSize + 6.0))
  // the line over the table: the filter, or the tools on a touch screen with no room beside the table
  private val barred: Val[Boolean] = own(Val(filtering() || (touch && offered() && !beside())))
  private val barHeight: Val[Double] = own(Val(if (barred()) SearchField.height + 8.0 else 0.0))
  private val leftShade = shade(true)
  private val rightShade = shade(false)
  private var bar: Option[Container] = None
  private var toolButton: Option[Container] = None
  // the ring round the cell the keys are on, while they move it
  private val ring = new Container {
    borderWidth @= 2.0
    borderColor := Theme.accent()
    radius @= 2.0
    Gestures.passThrough(node)
  }

  // the columns' widths found again once the measuring cells' sizes are read: once a batch, however many were measured
  twins.layout @= Some(new Layout {
    def layout(container: Container, children: Vector[Component]): Unit = recount()
    def fitsWidth(): Boolean = false
    def neighbour(container: Container, children: Vector[Component], from: Component, direction: Key): Option[Component] =
      None
  })
  size.width := initialWidth
  body.size.width := across()
  lines.size.width := across()
  lines.size.height := body.size.height()
  rows.content.children ++= List(body, lines)
  rows.size.width := across()
  rows.size.height := (if (maxHeight() <= 0.0) body.size.height()
    else math.min(body.size.height(), math.max(DataTable.toolSize * 2.0, maxHeight() - headHeight())))
  rows.position.top := headHeight()
  grid.size.width := across()
  grid.size.height := headHeight() + rows.size.height()
  TableLook.clip(grid, edge)
  grid.children ++= List(rows, edge, ring)
  scroll.content.children += grid
  scroll.size.width := math.min(size.width(), across())
  // the scrollbar under the table where the page draws one, never over its last row
  scroll.size.height := grid.size.height() + scroll.scrollbarRoom()
  scroll.position.left := gridLeft()
  scroll.position.top := barHeight()
  size.height := barHeight() + scroll.size.height()
  children ++= List(twins, scroll, leftShade, rightShade)

  ring.visible := ringShown() && grid.focused()
  ring.position.left := columnLeft(cursor()._2)
  ring.size.width := columnWidth(cursor()._2)
  ring.position.top := {
    val (row, _) = cursor()
    if (row < 0) 0.0 else slots.lift(row).flatten.map(r => headHeight() + r.position.top() - rows.offset()).getOrElse(0.0)
  }
  ring.size.height := {
    val (row, _) = cursor()
    if (row < 0) headHeight() else slots.lift(row).flatten.map(_.size.height()).getOrElse(0.0)
  }

  drawHead()
  ordered.attach(_ => arrange())
  made.attach(_ => arrange())
  cells.attach(_ => build())
  filter.attach(v => LayoutPass.batch(query @= v))
  columns.attach(_ => drawHead())
  build()
  // the tools and the filter, made once they are first wanted
  barred.attach(on => if (on) showBar())
  if (barred()) showBar()
  offered.attach(_ => placeTools())
  if (touch) placeTools() else near.attach(on => if (on) placeTools())

  grid.event.click.attach(e => clicked(e))
  grid.event.key.down.attach(k => pressed(k))
  grid.focused.attach(on => if (!on) ringShown @= false)

  /** Rows sorted by column `c`: up, or down where they already are up, or as they came where they are down. */
  def sortBy(c: Int): Unit = if (c >= 0 && c < columnCount()) sort @= (sort() match {
    case Some(ColumnSort(k, true)) if k == c => Some(ColumnSort(c, ascending = false))
    case Some(ColumnSort(k, false)) if k == c => None
    case _ => Some(ColumnSort(c))
  })

  /** What column `c` holds: as its column says, else as its cells are found to (`CellValues.infer`). */
  def kindOf(c: Int): ColumnKind = columns().lift(c).flatMap(_.kind).getOrElse {
    val all = cells()
    if (!(kindsFor eq all)) {
      kinds = columns().indices.toVector.map(k => CellValues.infer(all.map(r => r.lift(k).map(_.text).getOrElse(""))))
      kindsFor = all
    }
    kinds.lift(c).getOrElse(ColumnKind.Text)
  }

  /** How column `c`'s cells are aligned: as it says, else numbers at the end and anything else at the start. */
  def alignOf(c: Int): TextAlign = columns().lift(c).flatMap(_.align).getOrElse(
    if (kindOf(c) == ColumnKind.Number) TextAlign.End else TextAlign.Start)

  /** The header's words, a column a cell. */
  def headerTexts: Vector[String] = columns().map(_.title)

  /** Row `i`'s words, a column a cell. */
  def rowTexts(i: Int): Vector[String] = {
    val row = cells().lift(i).getOrElse(Vector.empty)
    (0 until columnCount()).toVector.map(c => row.lift(c).map(_.text).getOrElse(""))
  }

  /** `which` rows (the rows showing, unless it says), tab-separated under the header, for a spreadsheet's paste. */
  def tsv(which: Vector[Int] = shown()): String = TableText.tsv(headerTexts, which.map(rowTexts))
  /** `which` rows as a Markdown table. */
  def markdown(which: Vector[Int] = shown()): String =
    TableText.markdown(headerTexts, which.map(rowTexts), (0 until columnCount()).toVector.map(alignOf))
  /** `which` rows as comma-separated lines (RFC 4180). */
  def csv(which: Vector[Int] = shown()): String = TableText.csv(headerTexts, which.map(rowTexts))
  /** The rows chosen, in the order they show. */
  def chosen: Vector[Int] = shown().filter(selection().contains)

  /** The rows showing on the clipboard, tab-separated under the header. */
  def copy(): Unit = copied(tsv(), shown().size)
  /** The rows showing on the clipboard as a Markdown table. */
  def copyMarkdown(): Unit = copied(markdown(), shown().size)
  /** The rows chosen on the clipboard, tab-separated under the header. */
  def copySelection(): Unit = copied(tsv(chosen), chosen.size)
  /** The rows showing handed over as a CSV file (`name`.csv), a byte-order mark first so that a spreadsheet reads it
    * as UTF-8. */
  def exportCsv(): Unit = Files.download(("﻿" + csv()).getBytes("UTF-8"), name() + ".csv", Some("text/csv")).start()
  /** The rows showing handed over as a Markdown file (`name`.md). */
  def exportMarkdown(): Unit = Files.download(markdown().getBytes("UTF-8"), name() + ".md", Some("text/markdown")).start()

  /** The table's tools, as its button offers them: the rows chosen copied, where some are; the rows showing copied,
    * and downloaded. */
  def actions: List[MenuItem] = {
    val some = chosen
    (if (copies && some.nonEmpty) List(MenuItem(TableWords.copyRows(some.size), () => copySelection(),
      MaterialIcon.ContentCopy)) else Nil) ++
      (if (copies) List(MenuItem(TableWords.copy, () => copy(), MaterialIcon.ContentCopy),
        MenuItem(TableWords.copyMarkdown, () => copyMarkdown(), MaterialIcon.Code)) else Nil) ++
      (if (copies && saves) List(MenuItem.separator) else Nil) ++
      (if (saves) List(MenuItem(TableWords.exportCsv, () => exportCsv(), MaterialIcon.Download),
        MenuItem(TableWords.exportMarkdown, () => exportMarkdown(), MaterialIcon.Description)) else Nil)
  }

  private def copied(text: String, count: Int): Unit = {
    Clipboard.copy(text)
    Toast.show(TableWords.copied(count), seconds = 2.0)
  }

  // ---- where the parts go, for the rows ----

  /** Whether row `i` gets past the filter. */
  private[table] def passes(i: Int): Boolean = {
    val pass = passing()
    i >= pass.size || pass(i)
  }

  /** Where column `c` starts, from the table's left. */
  private[table] def columnLeft(c: Int): Double = {
    val s = starts()
    val before = if (Direction.rtl()) gutter() else 0.0
    if (c >= s.size) 0.0 else before + (if (mirrored) across() - gutter() - s(c) - widths()(c) else s(c))
  }
  private[table] def columnWidth(c: Int): Double = widths().lift(c).getOrElse(0.0)
  /** Where the rule before column `c` goes. */
  private[table] def ruleAt(c: Int): Double = if (mirrored) columnLeft(c) + columnWidth(c) - 1.0 else columnLeft(c)
  // the room for column `c`'s words
  private def inner(c: Int): Double = math.max(0.0, columnWidth(c) - pad * 2.0)
  // the room the arrow takes in a header sorted by it
  private def arrowRoom(c: Int, header: Boolean): Double =
    if (header && sort().exists(_.column == c)) DataTable.arrowSize(style.points) + 2.0 else 0.0

  /** How wide a cell of column `c` is: the column's room, or its widest cell on one line where that is less; less a
    * header's arrow. */
  private[table] def cellWidth(c: Int, header: Boolean): Double =
    math.max(0.0, math.min(inner(c), most().lift(c).getOrElse(0.0) - pad * 2.0) - arrowRoom(c, header))

  /** Where a cell of column `c`, `width` wide, starts: aligned as the column within its room, after a header's arrow
    * where the columns run from the right. */
  private[table] def cellLeft(c: Int, width: Double, header: Boolean): Double = {
    val arrow = arrowRoom(c, header)
    val room = inner(c) - arrow
    val end = if (mirrored) TextAlign.Start else TextAlign.End
    val align = aligns().lift(c).getOrElse(TextAlign.Start)
    columnLeft(c) + pad + (if (mirrored) arrow else 0.0) + (if (align == TextAlign.Center) (room - width) / 2.0
      else if (align == end) room - width else 0.0)
  }

  /** Where the header's arrow goes, by column `c`'s words `width` wide. */
  private[table] def arrowLeft(c: Int, width: Double): Double =
    if (mirrored) cellLeft(c, width, header = true) - DataTable.arrowSize(style.points) - 2.0
    else cellLeft(c, width, header = true) + width + 2.0

  /** `cell` of column `c` measured out of sight with the others, on one line or a word to a line (`words`). */
  private[table] def twin(cell: DataCell, c: Int, words: Boolean, header: Boolean): RichText = {
    val shown = DataRow.runs(cell, header, look.headerWeight)
    val text = new RichText {
      whiteSpace @= (if (words) WhiteSpace.Pre else WhiteSpace.NoWrap)
      font.size @= style.points
      direction @= style.direction
      runs @= (if (words) shown.map(run => run.copy(text = DataTable.broken(run.text))) else shown)
    }
    unplaced = unplaced :+ text
    text
  }

  // the measuring cells made since, put out of sight together: one change of the children for a table's worth
  private def placeTwins(): Unit = if (unplaced.nonEmpty) {
    twins.children ++= unplaced
    unplaced = Vector.empty
  }

  /** Measuring cells let go of. */
  private[table] def letGo(texts: Vector[RichText]): Unit = if (texts.nonEmpty && !takenApart) {
    val gone = texts.toSet[Component]
    twins.children @= twins.children().filterNot(gone.contains)
    texts.foreach(_.dispose())
  }

  // each column's widest, of the header's cell and the rows' measured ones (on one line, or a word to a line), with the
  // cells' padding: a whole pixel more, so a cell given it never wraps; the header's with its arrow where the rows are
  // sorted by it
  private def widest(words: Boolean): Vector[Double] = {
    val body = if (words) bodyLeast() else bodyMost()
    val top = Option(head())
    (0 until columnCount()).toVector.map { c =>
      val heading = top.flatMap(r => (if (words) r.words else r.lines).lift(c).flatten)
        .map(t => math.ceil(t.size.width()) + arrowRoom(c, header = true)).getOrElse(0.0)
      math.max(heading, body.lift(c).getOrElse(0.0)) + pad * 2.0
    }
  }

  // each column's widest measured cell of the rows, found from them all, read where they are and not read for the asking
  private def recount(): Unit = MeasuredFeature.withBatch {
    val n = columnCount()
    val measured: Vector[(Int, RichText, RichText)] = samples ++ slots.flatten.flatMap { row =>
      row.sized.toVector.flatMap(c => for (l <- row.lines.lift(c).flatten; w <- row.words.lift(c).flatten) yield (c, l, w))
    }
    def of(words: Boolean): Vector[Double] = (0 until n).toVector.map { c =>
      measured.foldLeft(0.0)((w, m) =>
        if (m._1 != c) w else math.max(w, math.ceil((if (words) m._3 else m._2).size.width.get)))
    }
    bodyLeast @= of(words = true)
    bodyMost @= of(words = false)
  }

  // ---- making the rows ----

  /** The header made for the columns, and every row again under them. */
  private def drawHead(): Unit = {
    val was = Option(head())
    val fresh = MeasuredFeature.withBatch(LayoutPass.batch {
      val row = new DataRow(this, -1, columns().map(col => DataCell(col.title, col.runs)), columns().indices.toSet)
      head @= row
      placeTwins()
      row
    })
    grid.children @= fresh +: grid.children().filterNot(c => was.exists(_ eq c))
    was.foreach(_.dispose())
    val gone = slots.flatten
    slots = Vector.empty
    gone.foreach(_.dispose())
    lines.children.replaceMaking((1 until (if (look.rules) columnCount() else 1)).toVector.map(c => new Container {
      backgroundColor := Theme.border()
      position.left := ruleAt(c)
      size.width @= 1.0
      size.height := lines.size.height()
    }))
    if (was.nonEmpty) build()
  }

  /** The rows as the cells are now: a row whose cells did not change keeps what draws it. Up to `eager` rows are made
    * at once, each cell measured with its row; a longer table makes its first `lead` at once and the rest a page a
    * turn after them, and measures only the widest of each column's cells (`sample`), apart from the rows. */
  private def build(): Unit = {
    val all = cells()
    val n = columnCount()
    val content = all.map(r => (0 until n).toVector.map(c => r.lift(c).getOrElse(DataCell(""))))
    haystacks = content.map(_.map(_.text).mkString("\u0001").toLowerCase)
    aligns @= (0 until n).toVector.map(alignOf)
    val whole = content.size <= DataTable.eager
    val every = if (whole) (0 until n).toSet else Set.empty[Int]
    val old = slots
    slots = content.indices.toVector.map(i => old.lift(i).flatten.filter(r => r.content == content(i) && r.sized == every))
    val kept = slots.flatten.toSet
    old.flatten.filterNot(kept.contains).foreach(_.dispose())
    letGo(samples.flatMap(s => Vector(s._2, s._3)))
    samples = Vector.empty
    if (selection().exists(_ >= content.size)) selection @= selection().filter(_ < content.size)
    // what the rows make is measured with the turn's batch (the web), as the table goes into the page, rather than read by
    // read as it is made
    MeasuredFeature.withBatch(LayoutPass.batch {
      if (!whole) samples = DataTable.sample(content.map(_.map(_.text))).map { case (i, c) =>
        (c, twin(content(i)(c), c, words = false, header = false), twin(content(i)(c), c, words = true, header = false))
      }
      content.indices.foreach(i => if (slots(i).isEmpty && (whole || i < DataTable.lead))
        slots = slots.updated(i, Some(new DataRow(this, i, content(i), every))))
      placeTwins()
      making += 1
      made @= made() + 1
    })
    if (slots.exists(_.isEmpty)) later(making, content)
  }

  // the rows not made yet, a page a turn: measured by what was made first, they measure nothing themselves
  private def later(turn: Int, content: Vector[Vector[DataCell]]): Unit =
    Platform.later(() => if (turn == making && !takenApart) {
      MeasuredFeature.withBatch(LayoutPass.batch {
        var left = DataTable.page
        var i = 0
        while (left > 0 && i < slots.size) {
          if (slots(i).isEmpty) {
            slots = slots.updated(i, Some(new DataRow(this, i, content(i), Set.empty)))
            left -= 1
          }
          i += 1
        }
        made @= made() + 1
      })
      if (slots.exists(_.isEmpty)) later(turn, content)
    })

  // the rows made, in their order
  private def arrange(): Unit = body.children @= ordered().flatMap(i => slots.lift(i).flatten)

  /** The shade over its `leftSide` edge, there while the table goes on past that edge. A finger, a click and the
    * wheel go through it to the table. */
  private def shade(leftSide: Boolean): Container = new Container {
    gradient := {
      val tint = Theme.text().withAlpha(0.18)
      val gone = tint.withAlpha(0.0)
      Some(Gradient(90.0, if (leftSide) List(GradientStop(tint, 0.0), GradientStop(gone, 1.0))
      else List(GradientStop(gone, 0.0), GradientStop(tint, 1.0))))
    }
    size.width @= DataTable.shadeWidth
    size.height := grid.size.height()
    position.top := barHeight()
    position.left := gridLeft() + (if (leftSide) 0.0 else scroll.size.width() - DataTable.shadeWidth)
    // the start shows more once scrolled from it, the end while it has not reached it
    visible := {
      val atStart = leftSide != fromRight()
      if (atStart) scroll.offset() > 0.5 else scroll.offset() < beyond() - 0.5
    }
    Gestures.passThrough(node)
  }

  // ---- the filter and the tools ----

  private def showBar(): Unit = if (bar.isEmpty) {
    val search = new SearchField(TableWords.filter, 220.0)
    search.value.attach(v => filter @= v)
    filter.attach(v => if (search.value() != v) search.value @= v)
    search.exitFocus(Key.ArrowDown, grid)
    val count = new TextView {
      font.size @= 12.5
      color := Theme.muted()
      content := (if (filter().trim.isEmpty) TableWords.rows(cells().size)
        else TableWords.shownOf(shown().size, cells().size))
    }
    val line = new Container {
      size.height @= SearchField.height
    }
    line.visible := barred()
    line.size.width := size.width()
    search.size.width := math.max(120.0, math.min(240.0, size.width() - count.size.width() - DataTable.toolSize - 24.0))
    search.visible := filtering()
    search.position.left := ListRow.along(0.0, search.size.width(), line.size.width())
    count.visible := filtering()
    count.position.left := ListRow.along(search.size.width() + 10.0, count.size.width(), line.size.width())
    count.position.top := (SearchField.height - count.size.height()) / 2.0
    line.children ++= List(search, count)
    bar = Some(line)
    children += line
    placeTools()
  }

  /** The tools' button, once it is first wanted: at the end of the line over the table where there is one, else beside
    * the header where there is room, else over the header's end; shown while the pointer is over the table or the
    * focus is in it, and always on a touch screen. */
  private def placeTools(): Unit = if (offered() && toolButton.isEmpty && (touch || near() || barred())) {
    val button = toolsButton()
    toolButton = Some(button)
    val side = DataTable.toolSize
    button.visible := offered() && (touch || barred() || near() || button.focused())
    button.position.left := {
      val width = size.width()
      if (barred()) ListRow.along(width - side, side, width)
      else if (beside()) (if (mirrored) gridLeft() - side - 6.0 else across() + 6.0)
      else if (mirrored) gridLeft() + 3.0
      else math.min(width, across()) - side - 3.0
    }
    button.position.top := (if (barred()) (SearchField.height - side) / 2.0
      else barHeight() + math.max(0.0, (headHeight() - side) / 2.0))
    children += button
  }

  private def toolsButton(): Container = new Container {
    private val glyph = new MaterialIconView(MaterialIcon.MoreVert, 18.0)
    size.width @= DataTable.toolSize
    size.height @= DataTable.toolSize
    radius @= 6.0
    focusable @= true
    tapSlop @= true
    tooltip @= TableWords.actions
    semantics @= Semantics.button(TableWords.actions)
    backgroundColor := (if (focused() || event.pointer.over()) Theme.controlLit()
      else if (!barred() && !beside()) Theme.raised() else Color.Clear)
    glyph.color := (if (focused() || event.pointer.over()) Theme.text() else Theme.muted())
    glyph.position.left := (DataTable.toolSize - glyph.size.width()) / 2.0
    glyph.position.top := (DataTable.toolSize - glyph.size.height()) / 2.0
    children += glyph
    event.click.attach { e =>
      e.consume()
      Menu.below(this, actions)
    }
  }

  // ---- the pointer and the keys ----

  // the row (-1 the header) and the column at `e`, if it is on one
  private def at(e: PointerEvent): Option[(Int, Int)] = {
    val top = head().absoluteTop
    val target: Option[DataRow] =
      if (e.y >= top && e.y < top + headHeight()) Some(head())
      else body.children().collectFirst {
        case r: DataRow if r.shown() && e.y >= r.absoluteTop && e.y < r.absoluteTop + r.size.height() => r
      }
    target.flatMap(r => r.columnAt(e.x - r.absoluteLeft).map(c => (r.index, c)))
  }

  private def clicked(e: PointerEvent): Unit = at(e).foreach { case (row, c) =>
    ringShown @= false
    cursor @= ((row, c))
    if (row < 0) sortBy(c)
    // the end of a drag across words selects them, and chooses nothing
    else if (!Platform.selectsText() || Platform.selectedText().isEmpty) {
      if (e.shift && anchor.nonEmpty) chooseTo(row)
      else {
        // a finger's tap adds or takes away, as Ctrl or Command does, on a touch laptop as on a phone
        val toggles = e.primary || e.finger || touch
        if (toggles) selection @= (if (selection().contains(row)) selection() - row else selection() + row)
        else selection @= (if (selection() == Set(row)) Set.empty else Set(row))
        anchor = Some(row)
      }
    }
  }

  // the rows from the last chosen to `row`, as they show
  private def chooseTo(row: Int): Unit = {
    val order = shown()
    val from = order.indexOf(anchor.getOrElse(row))
    val to = order.indexOf(row)
    if (from >= 0 && to >= 0) selection @= order.slice(math.min(from, to), math.max(from, to) + 1).toSet
  }

  private def pressed(k: KeyEvent): Unit = action(k).foreach { act =>
    act()
    ringShown @= true
  }

  /** What `k` does to the table, if anything (what it keeps from the focus layer): the arrows, Home, End and the page
    * keys move between the cells, Space and Enter choose (or sort, on the header), Escape lets the choice go, and the
    * shortcut's A and C choose every row showing and copy. */
  private def action(k: KeyEvent): Option[() => Unit] = {
    val (row, c) = cursor()
    val order = shown()
    val at = order.indexOf(row)
    val primary = if (Platform.commandKey()) k.meta else k.ctrl
    val letter = k.name.toLowerCase
    val forward = if (mirrored) Key.ArrowLeft else Key.ArrowRight
    val back = if (mirrored) Key.ArrowRight else Key.ArrowLeft
    def go(r: Int, col: Int, extend: Boolean = false): Option[() => Unit] = Some(() => {
      if (extend && anchor.isEmpty) anchor = Some(if (row >= 0) row else r)
      cursor @= ((r, math.max(0, math.min(columnCount() - 1, col))))
      if (extend) chooseTo(r)
      reveal()
    })
    if (primary && !k.alt && letter == "c" && copies) {
      // words selected with the pointer are the browser's to copy
      if (Platform.selectsText() && Platform.selectedText().nonEmpty) None
      else if (chosen.nonEmpty) Some(() => copySelection())
      else if (row >= 0) Some(() => {
        Clipboard.copy(rowTexts(row).lift(c).getOrElse(""))
        Toast.show(TableWords.copiedCell, seconds = 2.0)
      })
      else None
    }
    else if (primary && !k.alt && letter == "a") Some(() => selection @= order.toSet)
    else if (k.ctrl && !k.alt && !k.meta && (letter == "home" || letter == "end") && order.nonEmpty)
      go(if (letter == "home") order.head else order.last, c)
    else if (k.ctrl || k.meta || k.alt) None
    else if (k.key == Key.ArrowDown && at < order.size - 1) go(order(at + 1), c, k.shift)
    else if (k.key == Key.ArrowUp && at > 0) go(order(at - 1), c, k.shift)
    else if (k.key == Key.ArrowUp && at == 0 && !k.shift) go(-1, c)
    else if (k.key == forward && c < columnCount() - 1) go(row, c + 1)
    else if (k.key == back && c > 0) go(row, c - 1)
    else if (k.key == Key.PageDown && at < order.size - 1)
      go(order(math.min(order.size - 1, math.max(0, at) + DataTable.pageKeys)), c, k.shift)
    else if (k.key == Key.PageUp && at > 0) go(order(math.max(0, at - DataTable.pageKeys)), c, k.shift)
    else if (letter == "home" && c > 0) go(row, 0)
    else if (letter == "end" && c < columnCount() - 1) go(row, columnCount() - 1)
    else if ((k.key == Key.Enter || k.key == Key.Space) && row < 0) Some(() => sortBy(c))
    else if ((k.key == Key.Enter || k.key == Key.Space) && at >= 0) Some(() => {
      selection @= (if (selection().contains(row)) selection() - row else selection() + row)
      anchor = Some(row)
    })
    else if (k.key == Key.Escape && selection().nonEmpty) Some(() => selection @= Set.empty)
    else None
  }

  /** The cell the keys are on brought into view: its row down the rows, its column across the table. */
  private def reveal(): Unit = {
    val (row, c) = cursor()
    slots.lift(row).flatten.foreach { r =>
      val top = r.position.top()
      if (top < rows.offset()) rows.scrollTo(top)
      else if (top + r.size.height() > rows.offset() + rows.size.height())
        rows.scrollTo(top + r.size.height() - rows.size.height())
    }
    // how far along the scroll the column starts, from the scroll's own start
    val along = if (fromRight()) across() - columnLeft(c) - columnWidth(c) else columnLeft(c)
    if (along < scroll.offset()) scroll.scrollTo(along)
    else if (along + columnWidth(c) > scroll.offset() + scroll.size.width())
      scroll.scrollTo(along + columnWidth(c) - scroll.size.width())
  }

  override def focus(): Unit = grid.focus()

  override def kind: String = "DataTable"
}

object DataTable {
  /** How many rows a table makes, and measures, at once: a longer one makes its first `lead` and the rows of the cells
    * it measures (the widest of each column, `sample`) at once, and the rest a `page` a turn after them. */
  val eager: Int = 60
  val lead: Int = 12
  val page: Int = 16
  /** How far Page Up and Page Down move. */
  val pageKeys: Int = 10
  /** How wide a shade is at an edge the table goes on past. */
  val shadeWidth: Double = 18.0
  /** The room kept for the page's scrollbar beside rows that scroll. */
  val gutter: Double = 12.0
  /** How big the tools' button is. */
  val toolSize: Double = 26.0

  /** A table of `rows` of words under `columns`. */
  def apply(columns: Vector[DataColumn], rows: Vector[Vector[String]]): DataTable =
    new DataTable(columns, rows.map(_.map(DataCell(_))))

  /** A table of `items`, a row each: each column with what it shows of an item.
    * {{{
    *   DataTable.of(personas)(DataColumn("Persona") -> (_.name), DataColumn("Conversations") -> (_.count.toString))
    * }}} */
  def of[T](items: Seq[T])(columns: (DataColumn, T => String)*): DataTable =
    new DataTable(columns.toVector.map(_._1), items.toVector.map(item => columns.toVector.map(c => DataCell(c._2(item)))))

  /** How big a header's sort arrow is beside words of `points`. */
  def arrowSize(points: Double): Double = math.round(points * 1.05).toDouble

  /** `text` with a line break for each space or tab, so that measured it is as wide as its widest word. */
  def broken(text: String): String = text.replace(' ', '\n').replace('\t', '\n')

  /** The cells of a long table measured for its columns' widths, as (row, column): of each column the four with the
    * most characters and the four with the longest words. */
  def sample(rows: Vector[Vector[String]]): Vector[(Int, Int)] = {
    val columns = rows.foldLeft(0)((n, r) => math.max(n, r.size))
    (0 until columns).toVector.flatMap { c =>
      val texts = rows.map(_.lift(c).getOrElse(""))
      val lengths = texts.map(_.length)
      val words = texts.map(longestWord)
      (top(lengths, 4) ++ top(words, 4)).distinct.map(i => (i, c))
    }
  }

  // the indices of the `k` largest of `values`, the first of equals first: a pass over them, not a sort
  private def top(values: Vector[Int], k: Int): Vector[Int] = {
    var best = Vector.empty[Int]
    values.indices.foreach { i =>
      if (best.size < k || values(i) > values(best.last)) {
        val at = best.indexWhere(j => values(i) > values(j))
        best = (if (at < 0) best :+ i else best.patch(at, Vector(i), 0)).take(k)
      }
    }
    best
  }

  // how many characters the longest word of `text` has
  private def longestWord(text: String): Int = {
    var longest = 0
    var run = 0
    var i = 0
    while (i < text.length) {
      if (text.charAt(i).isWhitespace) run = 0
      else {
        run += 1
        if (run > longest) longest = run
      }
      i += 1
    }
    longest
  }
}
