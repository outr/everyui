package everyui.tree

import everyui.async.Task
import everyui.component.{Component, Container}
import everyui.event.{Key, KeyEvent}
import everyui.i18n.Direction
import everyui.list.{Decoration, ListPoint, Selection, VirtualList}
import reactify.*

import scala.collection.mutable

/** A tree over a `TreeModel`, shown as a `VirtualList` of its open nodes: the model changes a node at a time and the
  * tree follows, keeping what is open, what is chosen and where it is scrolled.
  *
  * A node opens where `expandable` says (by default where it has children, or where `loader` has yet to be asked for
  * them); opening one whose children are not yet known asks `loader` for them, showing an hourglass until they come.
  * Keys: Up and Down move the cursor, Right opens a node or goes to its first child, Left closes it or goes to its
  * parent (the other way round in a right-to-left language), Enter activates, and opens or closes a node that opens.
  * A click on the chevron opens or closes. The app dresses each node's row (`decorate`), hears its menu (`menu`), and
  * reads and sets the selection by key (`selection`), as a list's.
  *
  * {{{
  *   val model = new TreeModel[File](_.path)
  *   val tree = new Tree(model, (f: File) => f.name)
  *   tree.expandable @= (f => f.directory)
  *   tree.loader @= Some(dir => Task.async(done => server.list(dir.path)(done)))
  *   tree.decorate @= (f => Decoration(icon = Some(if (f.directory) MaterialIcon.Folder else MaterialIcon.Description)))
  *   tree.activated.attach(f => if (!f.directory) open(f))
  *   model.setChildren(None, roots)
  * }}} */
class Tree[T](val model: TreeModel[T], val labelOf: T => String, rowHeight: Double = VirtualList.rowHeight)
  extends Container {
  override def kind: String = "Tree"

  /** The keys of the open nodes. */
  val expanded: Var[Set[String]] = own(Var(Set.empty))
  /** The keys of the nodes whose children are being loaded. */
  val loading: Var[Set[String]] = own(Var(Set.empty))
  /** Whether a node can be opened. */
  val expandable: Var[T => Boolean] = own(Var((t: T) => {
    val key = model.keyOf(t)
    model.childKeys(Some(key)).nonEmpty || (loader().nonEmpty && !model.loaded(Some(key)))
  }))
  /** How a node's children are fetched when it is first opened, if they are not given. */
  val loader: Var[Option[T => Task[Seq[T]]]] = own(Var(None))
  val decorate: Var[T => Decoration] = own(Var((_: T) => Decoration.plain))
  /** How far each level is indented, in pixels. */
  val indent: Var[Double] = own(Var(16.0))
  /** A node activated: Enter, a double click (a tap on a touch screen). */
  val activated: Channel[T] = Channel[T]
  /** A context menu asked for on a node. */
  val menu: Channel[ListPoint[T]] = Channel[ListPoint[T]]

  private val lines: Var[Vector[TreeLine[T]]] = own(Var(Vector.empty))
  private var kept = mutable.HashMap.empty[String, TreeLine[T]]
  // a new node's row, being named: under which parent, and where among its children
  private[tree] var pending: Option[(Option[String], Int)] = None

  val list: VirtualList[TreeLine[T]] = new VirtualList[TreeLine[T]](lines, _.key,
    l => l.value.map(labelOf).getOrElse(""), rowHeight)(() => new TreeRow[T](this))

  def selection: Selection = list.selection
  /** The key of the node under the cursor. */
  def current: Var[Option[String]] = list.current
  /** The chosen nodes, in the order they show. */
  def selectedItems: Vector[T] = list.selectedItems.flatMap(_.value)

  def isExpandable(t: T): Boolean = expandable()(t)

  /** Node `key` opened, its children loaded first if they are not known. */
  def expand(key: String): Unit = model.get(key).foreach { t =>
    if (isExpandable(t)) {
      if (!expanded().contains(key)) expanded @= expanded() + key
      load(key, t)
    }
  }

  private def load(key: String, t: T): Unit = loader().foreach { fetch =>
    if (!model.loaded(Some(key)) && !loading().contains(key)) {
      loading @= loading() + key
      fetch(t).map { found =>
        model.setChildren(Some(key), found)
        loading @= loading() - key
      }.start()
    }
  }

  /** Node `key` closed; a cursor inside it goes up to it. */
  def collapse(key: String): Unit = if (expanded().contains(key)) {
    if (current().exists(c => model.ancestors(c).contains(key))) {
      current @= Some(key)
      if (!selection.multiple()) selection.only(key)
    }
    expanded @= expanded() - key
  }

  def toggle(key: String): Unit = if (expanded().contains(key)) collapse(key) else expand(key)

  /** Every node above `key` opened, and the cursor on it, chosen alone and scrolled into view. */
  def reveal(key: String): Unit = if (model.contains(key)) {
    expanded @= expanded() ++ model.ancestors(key)
    list.select(key)
  }

  /** The line `key` shows as, if it shows. */
  def lineOf(key: String): Option[TreeLine[T]] = {
    val i = list.indexOf(key)
    if (i < 0) None else Some(list(i))
  }

  override def focus(): Unit = list.focus()

  // ---- the lines ----

  private def flatten(): Unit = {
    val out = Vector.newBuilder[TreeLine[T]]
    val open = expanded()
    val busy = loading()
    val next = mutable.HashMap.empty[String, TreeLine[T]]
    def newLine(parent: Option[String], depth: Int): Unit = out += TreeLine[T](Tree.newKey, None, depth, false, false, false)
    def walk(parent: Option[String], depth: Int): Unit = {
      val keys = model.childKeys(parent)
      var i = 0
      while (i < keys.length) {
        if (pending.exists(p => p._1 == parent && p._2 == i)) newLine(parent, depth)
        val key = keys(i)
        model.entry(key).foreach { e =>
          val can = isExpandable(e.value)
          val opened = can && open.contains(key)
          val line = kept.get(key) match {
            case Some(l) if l.value.exists(_.asInstanceOf[AnyRef] eq e.value.asInstanceOf[AnyRef]) && l.depth == depth &&
              l.expandable == can && l.expanded == opened && l.loading == busy.contains(key) => l
            case _ => TreeLine[T](key, Some(e.value), depth, can, opened, busy.contains(key))
          }
          next(key) = line
          out += line
          if (opened) walk(Some(key), depth + 1)
        }
        i += 1
      }
      if (pending.exists(p => p._1 == parent && p._2 >= keys.length)) newLine(parent, depth)
    }
    walk(None, 0)
    kept = next
    lines @= out.result()
  }

  // ---- keys and clicks ----

  list.keys = (e: KeyEvent) => {
    val at = list.indexOf(current().getOrElse(""))
    if (at < 0 || e.shift || e.ctrl || e.meta || e.alt) None else {
      val line = list(at)
      val forward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
      val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
      if (e.key == forward && line.expandable && !line.expanded) Some(() => expand(line.key))
      else if (e.key == forward && line.expanded && at + 1 < list.count && list(at + 1).depth > line.depth)
        Some(() => list.moveTo(at + 1))
      else if (e.key == back && line.expanded) Some(() => collapse(line.key))
      else if (e.key == back) model.parentOf(line.key).map(p => () => list.select(p))
      else None
    }
  }
  list.clicks = (i: Int, x: Double) => {
    val line = list(i)
    val from = everyui.list.ListRow.inset + line.depth * indent()
    if (line.expandable && x >= from - 4.0 && x < from + Tree.chevron + 2.0) {
      toggle(line.key)
      true
    }
    else false
  }
  list.activated.attach { line =>
    line.value.foreach { t =>
      if (line.expandable) toggle(line.key)
      activated @= t
    }
  }
  list.menu.attach(p => p.item.value.foreach(t => menu @= ListPoint(t, p.key, p.x, p.y)))

  model.changed.attach { _ =>
    val open = expanded().filter(model.contains)
    if (open.size != expanded().size) expanded @= open
    val busy = loading().filter(model.contains)
    if (busy.size != loading().size) loading @= busy
    flatten()
  }
  expanded.attach(_ => flatten())
  loading.attach(_ => flatten())
  expandable.attach(_ => flatten())
  indent.attach(_ => flatten())

  list.size.width := size.width()
  list.size.height := size.height()
  size.width @= 320.0
  size.height @= 400.0
  children += list
  flatten()
}

object Tree {
  /** The chevron's size. */
  val chevron: Double = 18.0
  /** The key of the row a new node is named in. */
  val newKey: String = "\u0000new"
}
