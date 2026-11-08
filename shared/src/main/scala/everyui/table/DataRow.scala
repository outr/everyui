package everyui.table

import everyui.component.{Container, MaterialIconView, RichText}
import everyui.component.types.{Semantics, TextAlign}
import everyui.font.MaterialIcon
import everyui.list.ListRow
import everyui.text.TextRun
import everyui.theme.Theme
import reactify.*

/** A row of a `DataTable`, its header's (`index` -1) or the row of cells at `index`: a cell a column, laid out in the
  * table's columns as it shares its width, the header on the look's band with an arrow by the column its rows are
  * sorted by, every second row striped where the look says (by where it shows, sorted and filtered), and a chosen row
  * washed in the selection's colour. Where the table's rows open something (`DataTable.onOpen`), a row is a button
  * named by its words, shows the pointer's hand and a wash under it, and the one open is washed faintly in the accent
  * and current. The cells of the columns in `sized` are also measured out of sight on one line and
  * a word to a line, for how wide their columns are to be. A row the filter hides takes no room (and so no place in
  * the table's column of rows). */
private[table] class DataRow(table: DataTable, val index: Int, val content: Vector[DataCell], val sized: Set[Int])
  extends Container {
  private val header = index < 0
  private val look = table.style.look
  private val pad = look.vertical(header)
  val cells: Vector[RichText] = content.indices.toVector.map(cell)
  /** The cells on one line, and a word to a line, where they are measured. */
  val lines: Vector[Option[RichText]] =
    content.indices.toVector.map(c => if (sized(c)) Some(twin(c, words = false)) else None)
  val words: Vector[Option[RichText]] =
    content.indices.toVector.map(c => if (sized(c)) Some(twin(c, words = true)) else None)
  /** Whether the filter lets it show. */
  val shown: Val[Boolean] = own(Val(header || table.passes(index)))
  private val tall: Val[Double] = own(Val(cells.foldLeft(0.0)((h, c) => math.max(h, c.size.height())) + pad * 2.0))
  // the arrow by the column the rows are sorted by, made once they first are
  private var arrow: Option[MaterialIconView] = None

  copyAs @= "tr"
  if (header) semantics @= Semantics("row")
  else semantics := (if (table.opening()) Semantics("button", label = said, selected = Some(table.selection()
    .contains(index)), current = table.opened().contains(index))
    else Semantics("row", selected = Some(table.selection().contains(index))))
  if (!header) cursor := (if (table.opening()) "pointer" else "")
  size.width := (if (shown()) table.across() else 0.0)
  size.height := (if (shown()) tall() else 0.0)
  visible := shown()
  if (header) backgroundColor := look.header()
  // a chosen row washed in the selection's colour; where rows open, the one open faintly in the accent and the one
  // under the pointer in the lit control colour, which a stripe is not; else striped by where it shows
  else backgroundColor := (if (table.selection().contains(index)) ListRow.fill(selected = true, current = false,
    active = table.active(), into = false)
    else if (table.opening() && table.opened().contains(index)) DataRow.openFill(event.pointer.over())
    else if (table.opening() && event.pointer.over()) Theme.controlLit()
    else look.rowFill(table.stripeOf(index)))
  cells.indices.foreach { c =>
    val cell = cells(c)
    cell.position.left := table.cellLeft(c, cell.size.width(), header)
    cell.position.top @= pad
  }
  // where the look rules the cells, a rule across above every row but the header, and in the header a rule down before
  // every column but the first (the body's are the table's own): all put in at once
  children ++= (if (header || !look.rules) Vector.empty else Vector(new Container {
    backgroundColor := Theme.border()
    size.width := table.across()
    size.height @= 1.0
  })) ++ cells ++ (if (!header || !look.rules) Vector.empty else (1 until content.size).toVector.map(c => new Container {
    backgroundColor := Theme.border()
    position.left := table.ruleAt(c)
    size.width @= 1.0
    size.height := tall()
  }))
  if (header) {
    follow(table.sort, (s: Option[ColumnSort]) => if (s.nonEmpty) sorted())
    if (table.sort().nonEmpty) sorted()
  }
  // what is measured is let go with the row
  onDispose(() => table.letGo(lines.flatten ++ words.flatten))

  // the arrow, made where it is first wanted: by the header the rows are sorted by, up or down
  private def sorted(): Unit = if (arrow.isEmpty) {
    val a = new MaterialIconView(MaterialIcon.ArrowUpward, DataTable.arrowSize(table.style.points))
    a.color := Theme.muted()
    a.icon := (if (table.sort().exists(_.ascending)) MaterialIcon.ArrowUpward else MaterialIcon.ArrowDownward)
    a.visible := table.sort().exists(_.column < cells.size)
    a.position.left := table.sort().filter(_.column < cells.size)
      .map(s => table.arrowLeft(s.column, cells(s.column).size.width())).getOrElse(0.0)
    a.position.top := (tall() - a.size.height()) / 2.0
    arrow = Some(a)
    children += a
  }

  /** The column at `x` across the row (from its left), if any. */
  def columnAt(x: Double): Option[Int] = cells.indices.find { c =>
    val left = table.columnLeft(c)
    x >= left && x < left + table.columnWidth(c)
  }

  // a cell drawn: its words on one line where its column has room, else wrapped to the column
  private def cell(c: Int): RichText = new RichText {
    // The grid handles row clicks, so its DOM is unselectable. Cell text explicitly opts back in:
    // a drag copies words while a plain click still chooses the row (or sorts the header).
    selectable @= true
    font.size @= table.style.points
    // the pointer's hand over its words too where rows open, rather than the words' own
    if (!header) cursor := (if (table.opening()) "pointer" else "")
    // a cell holding no value muted, as a quiet table's are
    color := (if (table.style.quiet || content(c).nothing) Theme.muted() else Theme.text())
    follow = href => table.onLink(href)
    if (header) semantics := Semantics("columnheader", sort = table.sort().filter(_.column == c)
      .map(s => if (s.ascending) "ascending" else "descending").getOrElse(""))
    // (a row that opens is a button, read by its words: its cells say nothing of their own)
    else semantics := (if (table.opening()) Semantics.Plain
      else if (content(c).nothing) Semantics("cell", label = TableWords.nothing) else Semantics("cell"))
    copyAs @= (if (header) "th" else "td")
    direction @= table.style.direction
    size.width := table.cellWidth(c, header)
    // its lines, where it wraps, aligned as the column
    align := table.aligns().lift(c).getOrElse(TextAlign.Start)
    runs @= DataRow.runs(content(c), header, look.headerWeight)
  }

  // what a row that opens is called: its words, cell by cell, a cell holding no value said as such
  private lazy val said: String =
    content.map(c => if (c.nothing) TableWords.nothing else c.text).filter(_.nonEmpty).mkString(", ")

  // a cell measured, on one line or a word to a line: what a body's measures is told to the table as it is measured
  private def twin(c: Int, words: Boolean): RichText = table.twin(content(c), c, words, header)

  override def kind: String = if (header) "DataRow(header)" else "DataRow(" + index.toString + ")"
}

private[table] object DataRow {
  /** The row open's wash (`DataTable.opened`): the accent, faintly (a little more than a list's row in use, which has a
    * ring besides), and a little more under the pointer: plainly not a stripe, which is the text's grey. */
  def openFill(over: Boolean): everyui.Color = Theme.accent().withAlpha(if (over) 0.22 else 0.16)

  /** What `cell` draws: NULL where it holds no value, else its runs, or its words as they are (of `weight` in a
    * header). */
  def runs(cell: DataCell, header: Boolean, weight: Int): List[TextRun] =
    if (cell.nothing) List(TextRun(DataCell.nullText))
    else if (cell.runs.nonEmpty) cell.runs
    else if (cell.text.isEmpty) Nil
    else List(TextRun(cell.text, weight = if (header) weight else 0))
}
