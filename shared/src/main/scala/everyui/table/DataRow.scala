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
  * washed in the selection's colour. Where the table's rows open something (`DataTable.onOpen`), a row is named by
  * its words and told that it opens, shows the pointer's hand and a wash under it, and the one open is washed faintly
  * in the accent and current. The cells of the columns in `sized` are also measured out of sight on one line and a
  * word to a line, for how wide their columns are to be. A row the filter hides takes no room (and so no place in
  * the table's column of rows). */
private[table] class DataRow(table: DataTable, val index: Int, val content: Vector[DataCell], val sized: Set[Int])
  extends Container {
  private val header = index < 0
  private val look = table.style.look
  private val pad = look.vertical(header)
  val cells: Vector[RichText] = content.indices.toVector.map(cell)
  // the cells on one line, and a word to a line, where they are measured, until they have been read (`settle`)
  private var lines: Vector[Option[RichText]] =
    content.indices.toVector.map(c => if (sized(c)) Some(twin(c, words = false)) else None)
  private var words: Vector[Option[RichText]] =
    content.indices.toVector.map(c => if (sized(c)) Some(twin(c, words = true)) else None)
  // what each was read at, on one line and a word to a line, once it has been
  private val read: Var[Vector[Option[(Double, Double)]]] = own(Var(Vector.fill(content.size)(None)))
  /** Whether the filter lets it show. */
  val shown: Val[Boolean] = own(Val(header || table.passes(index)))
  private val tall: Val[Double] = own(Val(cells.foldLeft(0.0)((h, c) => math.max(h, c.size.height())) + pad * 2.0))
  // the arrow by the column the rows are sorted by, made once they first are
  private var arrow: Option[MaterialIconView] = None

  copyAs @= "tr"
  if (header) semantics @= Semantics("row")
  // a row that opens is still a row of cells, named by its words, the one open current, and told that it opens; a row
  // with an edit pending told which (new, deleted, changed)
  else semantics := {
    val state = table.rowStates().get(index).map(TableWords.state).getOrElse("")
    val chosen = Some(table.selection().contains(index))
    if (table.opening()) Semantics("row", label = said, selected = chosen, current = table.opened().contains(index),
      description = List(state, TableWords.opens).filter(_.nonEmpty).mkString(", "))
    else Semantics("row", selected = chosen, description = state)
  }
  if (!header) cursor := (if (table.opening()) "pointer" else "")
  size.width := (if (shown()) table.across() else 0.0)
  size.height := (if (shown()) tall() else 0.0)
  visible := shown()
  if (header) backgroundColor := look.header()
  // a chosen row washed in the selection's colour; where rows open, the one open faintly in the accent and the one
  // under the pointer in the lit control colour, which a stripe is not; a new or deleted row in its edit's tint; else
  // striped by where it shows
  else backgroundColor := (if (table.selection().contains(index)) ListRow.fill(selected = true, current = false,
    active = table.active(), into = false)
    else if (table.opening() && table.opened().contains(index)) DataRow.openFill(event.pointer.over())
    else if (table.opening() && event.pointer.over()) Theme.controlLit()
    else table.rowStates().get(index).flatMap(DataRow.rowTint).getOrElse(look.rowFill(table.stripeOf(index))))
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
  // the edits pending on it drawn: nothing made where there are none
  private var styled: (Option[EditState], Map[Int, EditState]) = (None, Map.empty)
  private var marks: Vector[Container] = Vector.empty
  restyle()

  /** The row drawn as the edits pending on it are now (`DataTable.rowStates`, `cellStates`), where they changed: each
    * changed cell washed in its tint, a deleted row's words struck through and the row dimmed, and a bar at the start
    * of a row with an edit of its own in its colour (seen on a chosen row too, whose wash covers the row's tint). A
    * changed cell is told as such to a screen reader. */
  private[table] def restyle(): Unit = if (!header) {
    val row = table.rowStates().get(index)
    val states = table.cellStates()
    val changed = content.indices.flatMap(c => states.get((index, c)).map(c -> _)).toMap
    if ((row, changed) != styled) {
      val struck = row.contains(EditState.Deleted)
      if (struck != styled._1.contains(EditState.Deleted)) {
        cells.indices.foreach(c => cells(c).runs @= DataRow.runs(content(c), header, look.headerWeight, struck))
        opacity @= (if (struck) DataRow.deletedOpacity else 1.0)
      }
      cells.indices.foreach(c => if (changed.get(c) != styled._2.get(c))
        cells(c).semantics @= DataRow.cellSemantics(content(c), changed.get(c)))
      val gone = marks
      marks = changed.toVector.sortBy(_._1).map { case (c, s) => tint(c, s) } ++ row.map(bar).toVector
      children @= marks ++ children().filterNot(m => gone.exists(_ eq m))
      gone.foreach(_.dispose())
      styled = (row, changed)
    }
  }

  // a changed cell's wash, over its column as tall as the row, under its words
  private def tint(c: Int, state: EditState): Container = new Container {
    backgroundColor := DataRow.cellTint(state)
    position.left := table.columnLeft(c)
    size.width := table.columnWidth(c)
    size.height := tall()
    everyui.render.Gestures.passThrough(node)
  }

  // the bar at the row's start (its right, where the columns run from the right) in its edit's colour
  private def bar(state: EditState): Container = new Container {
    backgroundColor := DataRow.colour(state)
    size.width @= DataRow.barWidth
    size.height := tall()
    position.left := table.rowStart(DataRow.barWidth)
    everyui.render.Gestures.passThrough(node)
  }

  /** How wide column `c`'s cell is on one line, or a word to a line (`words`), where it is measured: as its measuring
    * cell reads, or as it read once it has been let go. */
  def measure(c: Int, words: Boolean): Option[Double] = (if (words) this.words else lines).lift(c).flatten match {
    case Some(t) => Some(t.size.width())
    case None => read().lift(c).flatten.map(r => if (words) r._2 else r._1)
  }

  /** Each measured column, how wide its cell is on one line and a word to a line, as they are (not read for the
    * asking). */
  def measures: Vector[(Int, Double, Double)] = sized.toVector.flatMap { c =>
    (lines.lift(c).flatten, words.lift(c).flatten) match {
      case (Some(l), Some(w)) => Vector((c, l.size.width.get, w.size.width.get))
      case _ => read.get.lift(c).flatten.toVector.map(r => (c, r._1, r._2))
    }
  }

  /** The measuring cells `done` says have been read turned to what they read, and handed back to be let go. */
  def settle(done: RichText => Boolean): Vector[RichText] = {
    var gone = Vector.empty[RichText]
    var now = read.get
    content.indices.foreach { c =>
      (lines.lift(c).flatten, words.lift(c).flatten) match {
        case (Some(l), Some(w)) if done(l) && done(w) =>
          now = now.updated(c, Some((l.size.width.get, w.size.width.get)))
          lines = lines.updated(c, None)
          words = words.updated(c, None)
          gone = gone ++ Vector(l, w)
        case _ => ()
      }
    }
    if (gone.nonEmpty) read @= now
    gone
  }

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
    else semantics @= DataRow.cellSemantics(content(c), None)
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

  /** What `cell` draws: NULL in italics where it holds no value, else its runs, or its words as they are (of `weight`
    * in a header); every run struck through where its row is to be deleted (`struck`). */
  def runs(cell: DataCell, header: Boolean, weight: Int, struck: Boolean = false): List[TextRun] = {
    val drawn = if (cell.nothing) List(TextRun(DataCell.nullText, italic = true))
      else if (cell.runs.nonEmpty) cell.runs
      else if (cell.text.isEmpty) Nil
      else List(TextRun(cell.text, weight = if (header) weight else 0))
    if (struck) drawn.map(_.copy(strike = true)) else drawn
  }

  /** What a screen reader is told of a body's cell: "null" for one holding no value, and the edit pending on it. */
  def cellSemantics(cell: DataCell, state: Option[EditState]): Semantics =
    Semantics("cell", label = if (cell.nothing) TableWords.nothing else "",
      description = state.map(TableWords.state).getOrElse(""))

  /** An edit's colour: the theme's warning for a change, its success for a new row, its danger for a deleted one. */
  def colour(state: EditState): everyui.Color = state match {
    case EditState.Changed => Theme.warning()
    case EditState.New => Theme.success()
    case EditState.Deleted => Theme.danger()
  }

  /** A changed cell's wash: its edit's colour, faint enough for the words over it in light and dark alike. */
  def cellTint(state: EditState): everyui.Color = colour(state).withAlpha(0.2)

  /** A row's wash for its own edit: a new or a deleted row's, fainter than a cell's (a changed row has its cells'). */
  def rowTint(state: EditState): Option[everyui.Color] =
    if (state == EditState.Changed) None else Some(colour(state).withAlpha(0.12))

  /** How opaque a row to be deleted is drawn, and how wide the bar at the start of a row with an edit is. */
  val deletedOpacity: Double = 0.6
  val barWidth: Double = 3.0
}
