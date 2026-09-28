package everyui.list

import everyui.component.Container
import everyui.event.{Key, KeyEvent}
import everyui.i18n.Direction
import reactify.*

/** Results under headers, as a search finds them in files: each group a header with how many it holds, its results
  * under it, each a line with what matched lit. Only the rows that show are made (a `VirtualList`), so a search with
  * tens of thousands of matches scrolls as easily as one with ten.
  *
  * The arrows go through headers and results alike, across the groups; Left on a result goes to its header, and on an
  * open header closes it; Right opens a header, or goes to its first result; Enter or a click on a header opens or
  * closes it, and Enter or a double click on a result activates it. `nextResult` and `previousResult` step from result
  * to result over the headers, for an app's F4 and Shift+F4.
  *
  * {{{
  *   val results = new GroupedList[Hit](groups, _.id, _.line, _.matches, h => (h.row + 1).toString)
  *   groups @= found.groupBy(_.file).map((file, hits) => ResultGroup(file, file, hits.toVector)).toVector
  *   results.activated.attach(hit => openAt(hit.file, hit.row, hit.column))
  * }}} */
class GroupedList[T](val groups: Var[Vector[ResultGroup[T]]], val keyOf: T => String, val textOf: T => String,
                     val spansOf: T => Seq[(Int, Int)], val leadOf: T => String = (_: T) => "",
                     rowHeight: Double = 24.0) extends Container {
  override def kind: String = "GroupedList"

  /** The keys of the groups that are closed. */
  val collapsed: Var[Set[String]] = own(Var(Set.empty))
  /** A result activated: Enter on it, a double click (a tap on a touch screen). */
  val activated: Channel[T] = Channel[T]

  private val lines: Var[Vector[GroupLine[T]]] = own(Var(Vector.empty))
  val list: VirtualList[GroupLine[T]] = new VirtualList[GroupLine[T]](lines, _.key,
    l => l.item.map(textOf).getOrElse(l.group.title), rowHeight)(() => new GroupRow[T](this))

  /** How many results there are, in every group. */
  def resultCount: Int = groups().iterator.map(_.items.size).sum

  def expand(group: String): Unit = if (collapsed().contains(group)) collapsed @= collapsed() - group
  /** Group `group` closed; a cursor on one of its results goes up to its header. */
  def collapse(group: String): Unit = if (!collapsed().contains(group)) {
    if (list.currentItem.exists(l => l.group.key == group && l.item.nonEmpty)) list.select(GroupedList.headerKey(group))
    collapsed @= collapsed() + group
  }
  def toggle(group: String): Unit = if (collapsed().contains(group)) expand(group) else collapse(group)
  def expandAll(): Unit = collapsed @= Set.empty
  def collapseAll(): Unit = {
    list.currentItem.foreach(l => list.select(GroupedList.headerKey(l.group.key)))
    collapsed @= groups().map(_.key).toSet
  }

  /** The cursor onto the next result, over any headers (opening a closed group on the way), round to the first after
    * the last: the result it lands on, which is not activated. */
  def nextResult(): Option[T] = step(1)
  def previousResult(): Option[T] = step(-1)

  private def step(way: Int): Option[T] = {
    val all = groups().flatMap(g => g.items.map(t => (g, t)))
    if (all.isEmpty) None else {
      val at = list.currentItem.flatMap(l => l.item.map(t => all.indexWhere(p => keyOf(p._2) == keyOf(t)))).getOrElse(-1)
      val here = list.currentItem.filter(_.item.isEmpty).map(h => all.indexWhere(_._1.key == h.group.key)).getOrElse(-1)
      val next = if (at >= 0) (at + way + all.length) % all.length
        else if (here >= 0) (if (way > 0) here else (here - 1 + all.length) % all.length)
        else if (way > 0) 0 else all.length - 1
      val (g, t) = all(next)
      expand(g.key)
      list.select(GroupedList.itemKey(g.key, keyOf(t)))
      Some(t)
    }
  }

  private def flatten(): Unit = {
    val shut = collapsed()
    val out = Vector.newBuilder[GroupLine[T]]
    groups().foreach { g =>
      val open = !shut.contains(g.key)
      out += GroupLine(GroupedList.headerKey(g.key), g, None, open)
      if (open) g.items.foreach(t => out += GroupLine(GroupedList.itemKey(g.key, keyOf(t)), g, Some(t), open))
    }
    lines @= out.result()
  }

  list.keys = (e: KeyEvent) => list.currentItem.filter(_ => !e.shift && !e.ctrl && !e.meta && !e.alt).flatMap { line =>
    val forward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
    val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
    val at = list.indexOf(line.key)
    if (line.item.isEmpty && e.key == forward && !line.expanded) Some(() => expand(line.group.key))
    else if (line.item.isEmpty && e.key == forward && at + 1 < list.count && list(at + 1).item.nonEmpty)
      Some(() => list.moveTo(at + 1))
    else if (line.item.isEmpty && e.key == back && line.expanded) Some(() => collapse(line.group.key))
    else if (line.item.nonEmpty && e.key == back) Some(() => list.select(GroupedList.headerKey(line.group.key)))
    else None
  }
  list.clicks = (i: Int, _: Double) => {
    val line = list(i)
    if (line.item.isEmpty) {
      list.moveTo(i)
      toggle(line.group.key)
      true
    }
    else false
  }
  list.activated.attach(line => line.item match {
    case Some(t) => activated @= t
    case None => toggle(line.group.key)
  })

  groups.attach(_ => flatten())
  collapsed.attach(_ => flatten())
  list.size.width := size.width()
  list.size.height := size.height()
  size.width @= 400.0
  size.height @= 400.0
  children += list
  flatten()

  override def focus(): Unit = list.focus()
}

object GroupedList {
  def headerKey(group: String): String = "g\u0000" + group
  def itemKey(group: String, item: String): String = "i\u0000" + group + "\u0000" + item
}
