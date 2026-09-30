package everyui.table

import everyui.Color
import everyui.component.{Component, Container}
import everyui.event.{Key, KeyEvent, PointerEvent}
import everyui.i18n.Direction
import everyui.list.{ListPoint, ListRow, Pointers, Selection, VirtualList}
import everyui.paging.PagedSource
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A table of any length on a `VirtualList`: a row per item, a cell per column, headings over them. Only the rows that
  * show are made, and each row's cells are made once and handed item after item.
  *
  * The rows are `items`, or, made with `VirtualTable.paged`, a server's, a page at a time (`PagedSource`): a row not
  * come yet is drawn as a grey bar per cell (the app's cells hidden), or as `placeholder` says where it gives one, and a
  * click on a heading sorts on the server (the source's `sort`). `loadingView` shows under the headings while the
  * source looks for a query's first rows, and `emptyView` where there are none.
  *
  * The columns are `columns`, which the app may change at any time (a view of its own): a row keeps the cells of the
  * columns it still has. Their widths are `widths`, by column key, which a pointer changes by dragging a heading's edge
  * and the app may keep and give back; a column not in it is its own `width` wide, and one in `hidden` is not shown.
  * With `fills` on, the columns fill the table's width: those that stretch share what the others leave in proportion
  * to their widths, and dragging an edge moves the room between that column and the next; where the table is narrower
  * than the columns, they keep their widths and scroll sideways. A column that sorts is
  * sorted by a click on its heading, up, down and then not at all, with an arrow on it; `sort` says which, and the
  * table sorts the rows itself unless `sortsItself` is off (the app sorts `items`, or the server does). Wider than the
  * table, the columns scroll sideways: a sideways wheel, the bar at the foot, or Left and Right.
  *
  * Rows are chosen as a list's are (`selection`): a click, Shift+click, Primary+click, the keys. With `checks` on, a
  * column of tick boxes leads, a click on one adding its row to the choice or taking it out, and the one in its heading
  * chooses every row or none. Over a paged source every row means the rows never loaded too: `selection.all` is on and
  * `selection.keys` holds the rows taken out since. The table cannot list those rows; `chosenCount` counts them, and
  * the app acts on its query (less those keys) on the server. `selectedItems` gives only the chosen rows that are here.
  *
  * The row under the pointer is washed in `hoverColor`; `rowColor` gives a row a background of the app's (a state of
  * the item), under the selection's. F2 edits the row's first cell that edits (`GridCell.edits`: a `MenuCell`'s menu).
  * `footer` is a line of the app's under the rows ("1,902,345 results"). The whole table is mirrored right to left.
  *
  * {{{
  *   val table = new VirtualTable[Commit](commits, _.hash, _.subject, Vector(
  *     GridColumn[Commit]("graph", "", 80.0, () => new GraphCell),
  *     GridColumn[Commit]("subject", "Subject", 420.0, () => new SubjectCell),   // the subject and its refs as Tags
  *     GridColumn.text[Commit]("author", "Author", 160.0, sorts = true)(_.author),
  *     GridColumn.text[Commit]("date", "Date", 140.0, sortBy = Some(Ordering.by(_.time)))(c => dateText(c.time))))
  *   table.widths @= saved
  *   table.widths.attach(save)
  *   table.activated.attach(showCommit)
  * }}} */
class VirtualTable[T](val items: Var[Vector[T]], val keyOf: T => String, val labelOf: T => String,
                      initialColumns: Vector[GridColumn[T]], rowHeight: Double = VirtualList.rowHeight,
                      val source: Option[PagedSource[T]] = None) extends Container {
  override def kind: String = "VirtualTable"

  /** The columns, in order; the app's to change. */
  val columns: Var[Vector[GridColumn[T]]] = own(Var(initialColumns))
  /** The columns' widths, by key, as a pointer or the app sets them (with `fills`, their weights). */
  val widths: Var[Map[String, Double]] = own(Var(Map.empty))
  /** The keys of the columns not shown. */
  val hidden: Var[Set[String]] = own(Var(Set.empty))
  /** Which column the rows are sorted by, if any: the source's, over a paged source. */
  val sort: Var[Option[Sorting]] = source.map(_.sort).getOrElse(own(Var(None)))
  /** Whether the table sorts the rows by `sort` itself; off, the app does (and `items` comes sorted). */
  val sortsItself: Var[Boolean] = own(Var(source.isEmpty))
  /** How far the columns are scrolled sideways, from the start edge. */
  val scrollX: Var[Double] = own(Var(0.0))
  /** Whether the columns fill the table's width. */
  val fills: Var[Boolean] = own(Var(false))
  /** Whether a column of tick boxes leads, to choose rows by. */
  val checks: Var[Boolean] = own(Var(false))
  /** The wash over the row under the pointer. */
  val hoverColor: Var[Color] = own(Var(Color.Clear))
  hoverColor := Theme.text().withAlpha(0.06)
  /** A row's own background, by its item, where the app gives one: under the selection's. */
  val rowColor: Var[T => Option[Color]] = own(Var((_: T) => None))
  /** A line of the app's under the rows. */
  val footer: Var[Option[Component]] = own(Var(None))

  private val checkColumn: GridColumn[T] = GridColumn[T](VirtualTable.checkKey, "", VirtualTable.checkWidth,
    () => new CheckCell[T], minWidth = VirtualTable.checkWidth, stretches = false)
  /** The columns shown, the tick boxes' first where there are any. */
  val shown: Val[Vector[GridColumn[T]]] = own(Val(if (checks()) checkColumn +: columns() else columns()))
  // each column's width and where it starts, worked out once for each change
  private val laidOut: Val[Map[String, Double]] = own(Val(layOut()))
  private val starts: Val[Map[String, Double]] = own(Val {
    val w = laidOut()
    var at = 0.0
    shown().map { c =>
      val start = at
      at += w.getOrElse(c.key, 0.0)
      c.key -> start
    }.toMap
  })

  private val rows: Var[Vector[T]] = own(Var(Vector.empty))
  val list: VirtualList[T] = new VirtualList[T](rows, keyOf, labelOf, rowHeight, source = source)(() => new TableRow[T](this))
  val header: TableHeader[T] = new TableHeader[T](this)
  // a row not come draws a grey bar in each cell itself
  list.placeholder @= None

  /** What stands in a row not come from the source, where not its grey bars (see `VirtualList.placeholder`). */
  def placeholder: Var[Option[() => Component]] = list.placeholder
  /** Shown under the headings while the source looks for a query's first rows (see `VirtualList.loadingView`). */
  def loadingView: Var[Option[Component]] = list.loadingView
  /** Shown under the headings where there are no rows (see `VirtualList.emptyView`). */
  def emptyView: Var[Option[Component]] = list.emptyView
  def selection: Selection = list.selection
  def current: Var[Option[String]] = list.current
  def activated: Channel[T] = list.activated
  def menu: Channel[ListPoint[T]] = list.menu
  /** The chosen rows, in order: over a paged source, those that are here. */
  def selectedItems: Vector[T] = list.selectedItems
  /** How many rows are chosen, the rows not loaded among them where every row is (read in a binding, it follows). */
  def chosenCount: Int = source match {
    case Some(s) => selection.size(s.count)
    case None => selection.keys().size
  }

  /** Every row chosen, or none where every row already is: the tick box in the heading. */
  def toggleAll(): Unit = if (list.count > 0 && chosenCount >= list.count) selection.clear() else list.selectAll()

  /** Row `key`'s cells made again from its item, for what they show from outside it (see `VirtualList.redecorate`). */
  def redecorate(key: String): Unit = list.redecorate(key)
  def redecorateAll(): Unit = list.redecorateAll()

  /** Column `key`'s width: 0 while it is hidden. */
  def widthOf(key: String): Double = laidOut().getOrElse(key, 0.0)

  /** Where column `key` starts, from the start edge of the first. */
  def startOf(key: String): Double = starts().getOrElse(key, 0.0)

  /** How wide the columns are together. */
  def totalWidth: Double = laidOut().values.sum

  private def base(c: GridColumn[T], set: Map[String, Double]): Double = set.getOrElse(c.key, c.width)

  // the room the stretching columns share, and their widths together, with `set` as the widths
  private def weighing(set: Map[String, Double]): (Double, Double) = {
    val showing = shown().filter(c => !hidden().contains(c.key))
    val fixed = showing.filter(!_.stretches).map(c => base(c, set)).sum
    (math.max(0.0, size.width() - fixed), showing.filter(_.stretches).map(c => base(c, set)).sum)
  }

  private def layOut(): Map[String, Double] = {
    val set = widths()
    val gone = hidden()
    val all = shown()
    val (room, weights) = weighing(set)
    // where the table is narrower than the columns, they keep their widths (and scroll sideways)
    if (!fills() || room <= weights) all.map(c => c.key -> (if (gone.contains(c.key)) 0.0 else base(c, set))).toMap
    else {
      val stretching = all.filter(c => c.stretches && !gone.contains(c.key))
      // whole pixels, the last stretching column taking what rounding leaves
      var left = room
      val last = stretching.lastOption.map(_.key)
      all.map { c =>
        c.key -> (if (gone.contains(c.key)) 0.0
          else if (!c.stretches) base(c, set)
          else {
            val share = if (last.contains(c.key)) math.max(0.0, left) else math.floor(base(c, set) * room / weights)
            left -= share
            math.max(c.minWidth, share)
          })
      }.toMap
    }
  }

  /** Column `key` dragged `along` from where it was when the drag began (`from`, the widths then): wider or narrower;
    * with `fills`, the room it takes or gives is the next stretching column's. */
  def resize(key: String, from: Map[String, Double], along: Double): Unit = shown().find(_.key == key).foreach { c =>
    val (room, weights) = weighing(from)
    if (!fills() || !c.stretches || room <= weights) widths @= from + (key -> math.max(c.minWidth, base(c, from) + along))
    else {
      val after = shown().dropWhile(_.key != key).drop(1).find(n => n.stretches && !hidden().contains(n.key))
      after.foreach { n =>
        if (weights > 0.0) {
          val scale = room / weights
          val was = base(c, from) * scale
          val next = base(n, from) * scale
          val now = math.max(c.minWidth, math.min(was + along, was + next - n.minWidth))
          widths @= from + (key -> now / scale) + (n.key -> (was + next - now) / scale)
        }
      }
    }
  }

  private def furthestX: Double = math.max(0.0, totalWidth - size.width())

  def scrollSideways(to: Double): Unit = scrollX @= math.max(0.0, math.min(furthestX, to))

  /** Sorted by column `key` up, or down if it already is up, or not at all if it is down. */
  def sortBy(key: String): Unit = if (columns().exists(c => c.key == key && c.sorts)) sort @= (sort() match {
    case Some(Sorting(k, true)) if k == key => Some(Sorting(key, ascending = false))
    case Some(Sorting(k, false)) if k == key => None
    case _ => Some(Sorting(key))
  })

  private def arrange(): Unit = if (source.isEmpty) {
    val all = items()
    rows @= (if (!sortsItself()) all else sort().flatMap { s =>
      columns().find(_.key == s.column).flatMap(_.sort).map(o => all.sorted(if (s.ascending) o else o.reverse))
    }.getOrElse(all))
  }

  // the bar the columns scroll sideways by, at the foot, where they are wider than the table
  private val bar = new Container {
    size.height @= 6.0
    radius @= 3.0
    backgroundColor := Theme.muted().withAlpha(0.45)
  }
  private val barRoom: Val[Double] = own(Val(if (totalWidth > size.width() + 0.5) 8.0 else 0.0))
  private val footRoom: Val[Double] = own(Val(footer().map(_.size.height()).getOrElse(0.0)))

  bar.visible := barRoom() > 0.0
  bar.size.width := math.max(24.0, size.width() * size.width() / math.max(1.0, totalWidth))
  bar.position.top := size.height() - footRoom() - 7.0
  bar.position.left := ListRow.along(
    if (furthestX <= 0.0) 0.0 else scrollX() / furthestX * (size.width() - bar.size.width()), bar.size.width(), size.width())
  bar.event.pointer.down.attach { e =>
    if (e.button == PointerEvent.PrimaryButton) {
      e.consume()
      val from = e.x
      val was = scrollX()
      val room = math.max(1.0, size.width() - bar.size.width())
      Pointers.track(m => scrollSideways(was + (if (Direction.rtl()) from - m.x else m.x - from) * furthestX / room),
        _ => ())
    }
  }
  bar.event.click.attach(_.consume())
  // a sideways wheel (a trackpad's)
  list.event.pointer.wheel.attach(e => if (e.x != 0.0) scrollSideways(scrollX() + (if (Direction.rtl()) -e.x else e.x)))
  // Left and Right scroll the columns where they are wider than the table, and leave the table where they are not; F2
  // edits the row's first cell that edits
  list.keys = (e: KeyEvent) => if (e.ctrl || e.meta || e.alt || e.shift) None
    else if (e.key == Key.Other && e.name == "F2") editor.map(cell => () => cell.edit())
    else if (furthestX <= 0.0) None else {
      val forward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
      val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
      if (e.key == forward && scrollX() < furthestX) Some(() => scrollSideways(scrollX() + VirtualTable.step))
      else if (e.key == back && scrollX() > 0.0) Some(() => scrollSideways(scrollX() - VirtualTable.step))
      else None
    }
  // a click on a tick box adds its row to the choice, or takes it out
  list.clicks = (index: Int, along: Double) => checks() && {
    val at = along + scrollX()
    val start = startOf(VirtualTable.checkKey)
    val hit = at >= start && at < start + widthOf(VirtualTable.checkKey)
    if (hit) {
      val key = list.keyAt(index)
      selection.toggle(key)
      current @= Some(key)
    }
    hit
  }

  /** The first cell that edits on the row under the cursor, edited: what F2 does. */
  def edit(): Unit = editor.foreach(_.edit())

  // the first cell that edits on the row under the cursor
  private def editor: Option[GridCell[T]] = current().map(list.indexOf).flatMap(list.rowAt).flatMap {
    case row: TableRow[T] @unchecked => row.cells.find(_.edits)
    case _ => None
  }

  header.size.width := size.width()
  list.position.top @= TableHeader.height
  list.size.width := size.width()
  list.size.height := math.max(0.0, size.height() - TableHeader.height - barRoom() - footRoom())
  items.attach(_ => arrange())
  // (a paged table's sort is its source's, which may outlive it)
  follow(sort, (_: Option[Sorting]) => arrange())
  sortsItself.attach(_ => arrange())
  laidOut.attach(_ => scrollSideways(scrollX()))
  size.width @= 640.0
  size.height @= 400.0
  size.width.attach(_ => scrollSideways(scrollX()))
  children ++= List(header, list, bar)
  footer.attach { _ =>
    children @= children().filter(c => (c eq header) || (c eq list) || (c eq bar))
    footer().foreach { f =>
      f.size.width := size.width()
      f.position.top := size.height() - f.size.height()
      children += f
    }
  }
  // a new query starts at the top, and what stood for places in it is let go
  source.foreach(s => follow(s.reset, (_: Unit) => {
    list.scrollTop(0.0)
    selection.keep(k => !PagedSource.isPlace(k))
    if (current().exists(PagedSource.isPlace)) current @= None
  }))
  arrange()

  override def focus(): Unit = list.focus()
}

object VirtualTable {
  /** How far Left and Right scroll the columns. */
  val step: Double = 60.0
  /** The key of the column of tick boxes. */
  val checkKey: String = "✓"
  /** How wide the column of tick boxes is. */
  val checkWidth: Double = 36.0

  /** A table of a server's rows, a page at a time: `source`'s, sorted on the server by the headings of the columns
    * that are `sortable`. */
  def paged[T](source: PagedSource[T], labelOf: T => String, columns: Vector[GridColumn[T]],
               rowHeight: Double = VirtualList.rowHeight): VirtualTable[T] =
    new VirtualTable[T](Var(Vector.empty), source.keyOf, labelOf, columns, rowHeight, Some(source))
}
