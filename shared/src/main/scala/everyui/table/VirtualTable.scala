package everyui.table

import everyui.component.Container
import everyui.event.{Key, KeyEvent, PointerEvent}
import everyui.i18n.Direction
import everyui.list.{ListPoint, ListRow, Pointers, Selection, VirtualList}
import everyui.theme.Theme
import reactify.*

/** A table of any length on a `VirtualList`: a row per item, a cell per column, headings over them. Only the rows that
  * show are made, and each row's cells are made once and handed item after item.
  *
  * The columns' widths are `widths`, by column key, which a pointer changes by dragging a heading's edge and the app may
  * keep and give back; a column not in it is its own `width` wide, and one in `hidden` is not shown. A column that sorts
  * is sorted by a click on its heading, up, down and then not at all, with an arrow on it; `sort` says which, and the
  * table sorts the rows itself unless `sortsItself` is off (the app sorts `items`, a server's query, say). Wider than
  * the table, the columns scroll sideways: a sideways wheel, the bar at the foot, or Left and Right. Rows are chosen as
  * a list's are (`selection`), and the whole table is mirrored right to left.
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
                      val columns: Vector[GridColumn[T]], rowHeight: Double = VirtualList.rowHeight) extends Container {
  override def kind: String = "VirtualTable"

  /** The columns' widths, by key, as a pointer or the app sets them. */
  val widths: Var[Map[String, Double]] = own(Var(Map.empty))
  /** The keys of the columns not shown. */
  val hidden: Var[Set[String]] = own(Var(Set.empty))
  /** Which column the rows are sorted by, if any. */
  val sort: Var[Option[Sorting]] = own(Var(None))
  /** Whether the table sorts the rows by `sort` itself; off, the app does (and `items` comes sorted). */
  val sortsItself: Var[Boolean] = own(Var(true))
  /** How far the columns are scrolled sideways, from the start edge. */
  val scrollX: Var[Double] = own(Var(0.0))

  private val rows: Var[Vector[T]] = own(Var(Vector.empty))
  val list: VirtualList[T] = new VirtualList[T](rows, keyOf, labelOf, rowHeight)(() => new TableRow[T](this))
  val header: TableHeader[T] = new TableHeader[T](this)

  def selection: Selection = list.selection
  def current: Var[Option[String]] = list.current
  def activated: Channel[T] = list.activated
  def menu: Channel[ListPoint[T]] = list.menu
  def selectedItems: Vector[T] = list.selectedItems

  /** Row `key`'s cells made again from its item, for what they show from outside it (see `VirtualList.redecorate`). */
  def redecorate(key: String): Unit = list.redecorate(key)
  def redecorateAll(): Unit = list.redecorateAll()

  /** Column `key`'s width: 0 while it is hidden. */
  def widthOf(key: String): Double =
    if (hidden().contains(key)) 0.0
    else widths().getOrElse(key, columns.find(_.key == key).map(_.width).getOrElse(0.0))

  /** Where column `key` starts, from the start edge of the first. */
  def startOf(key: String): Double = {
    var at = 0.0
    var found = false
    columns.foreach { c =>
      if (c.key == key) found = true
      if (!found) at += widthOf(c.key)
    }
    at
  }

  /** How wide the columns are together. */
  def totalWidth: Double = columns.map(c => widthOf(c.key)).sum

  private def furthestX: Double = math.max(0.0, totalWidth - size.width())

  def scrollSideways(to: Double): Unit = scrollX @= math.max(0.0, math.min(furthestX, to))

  /** Sorted by column `key` up, or down if it already is up, or not at all if it is down. */
  def sortBy(key: String): Unit = if (columns.exists(c => c.key == key && c.sort.nonEmpty)) sort @= (sort() match {
    case Some(Sorting(k, true)) if k == key => Some(Sorting(key, ascending = false))
    case Some(Sorting(k, false)) if k == key => None
    case _ => Some(Sorting(key))
  })

  private def arrange(): Unit = {
    val all = items()
    rows @= (if (!sortsItself()) all else sort().flatMap { s =>
      columns.find(_.key == s.column).flatMap(_.sort).map(o => all.sorted(if (s.ascending) o else o.reverse))
    }.getOrElse(all))
  }

  // the bar the columns scroll sideways by, at the foot, where they are wider than the table
  private val bar = new Container {
    size.height @= 6.0
    radius @= 3.0
    backgroundColor := Theme.muted().withAlpha(0.45)
  }
  private val barRoom: Val[Double] = own(Val(if (totalWidth > size.width() + 0.5) 8.0 else 0.0))

  bar.visible := barRoom() > 0.0
  bar.size.width := math.max(24.0, size.width() * size.width() / math.max(1.0, totalWidth))
  bar.position.top := size.height() - 7.0
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
  // Left and Right scroll the columns where they are wider than the table, and leave the table where they are not
  list.keys = (e: KeyEvent) => if (e.ctrl || e.meta || e.alt || e.shift || furthestX <= 0.0) None else {
    val forward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
    val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
    if (e.key == forward && scrollX() < furthestX) Some(() => scrollSideways(scrollX() + VirtualTable.step))
    else if (e.key == back && scrollX() > 0.0) Some(() => scrollSideways(scrollX() - VirtualTable.step))
    else None
  }

  header.size.width := size.width()
  list.position.top @= TableHeader.height
  list.size.width := size.width()
  list.size.height := math.max(0.0, size.height() - TableHeader.height - barRoom())
  items.attach(_ => arrange())
  sort.attach(_ => arrange())
  sortsItself.attach(_ => arrange())
  widths.attach(_ => scrollSideways(scrollX()))
  hidden.attach(_ => scrollSideways(scrollX()))
  size.width @= 640.0
  size.height @= 400.0
  size.width.attach(_ => scrollSideways(scrollX()))
  children ++= List(header, list, bar)
  arrange()

  override def focus(): Unit = list.focus()
}

object VirtualTable {
  /** How far Left and Right scroll the columns. */
  val step: Double = 60.0
}
