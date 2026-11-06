package everyui.tree

import everyui.async.Task
import everyui.component.{Component, Container}
import everyui.event.{Key, KeyEvent}
import everyui.i18n.Direction
import everyui.list.{Decoration, Drop, DropPosition, ListEdit, ListPoint, Selection, VirtualList}
import everyui.render.Platform
import reactify.*

import scala.collection.mutable

/** A tree over a `TreeModel`, shown as a `VirtualList` of its open nodes: the model changes a node at a time and the
  * tree follows, keeping what is open, what is chosen and where it is scrolled.
  *
  * A node opens where `expandable` says (by default where it has children, or where `loader` has yet to be asked for
  * them); opening one whose children are not yet known asks `loader` for them, showing an hourglass until they come.
  * Keys: Up and Down move the cursor, Right opens a node or goes to its first child, Left closes it or goes to its
  * parent (the other way round in a right-to-left language), * opens every node beside it, Enter activates, and opens
  * or closes a node that opens.
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
  // a tree to a screen reader, its rows its items
  list.semantics @= everyui.component.types.Semantics("tree")

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
        model.batch {
          model.setChildren(Some(key), found)
          // children arriving under a ticked node are ticked, as it was
          if (checked().contains(key)) checked @= checked() ++ under(key).filter(k => model.get(k).exists(isCheckable))
        }
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

  /** Every folder beside `key` (under the same parent, `key` among them) opened, as * opens them. */
  def expandSiblings(key: String): Unit = {
    val parent = model.parentOf(key)
    val beside = (0 until list.count).map(i => list(i))
      .filter(l => l.expandable && !l.expanded && model.parentOf(l.key) == parent).map(_.key)
    beside.foreach(expand)
  }

  /** Every node above `key` opened, and the cursor on it, chosen alone and scrolled into view. */
  def reveal(key: String): Unit = if (model.contains(key)) {
    expanded @= expanded() ++ model.ancestors(key)
    list.select(key)
  }

  /** Node `key`'s row dressed again (`decorate` asked again), for a look that comes from outside the node. A node
    * changed in the model (`model.update`) is dressed again as it changes. */
  def redecorate(key: String): Unit = list.redecorate(key)
  /** Every row showing dressed again. */
  def redecorateAll(): Unit = list.redecorateAll()

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
          val tick = if (isCheckable(e.value)) Some(checkState(key)) else None
          val line = kept.get(key) match {
            case Some(l) if l.value.exists(_.asInstanceOf[AnyRef] eq e.value.asInstanceOf[AnyRef]) && l.depth == depth &&
              l.expandable == can && l.expanded == opened && l.loading == busy.contains(key) && l.check == tick => l
            case _ => TreeLine[T](key, Some(e.value), depth, can, opened, busy.contains(key), tick)
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

  // ---- ticks ----

  /** Whether a node has a box to tick. None has by default; a list of changes might tick every file and folder but not
    * its root headers. */
  val checkable: Var[T => Boolean] = own(Var((_: T) => false))
  /** The keys of the ticked nodes. A node with ticked children is ticked when all of them are, and shows mixed when
    * only some are; one whose children are not loaded yet stands for them, and they arrive ticked as it is. The app
    * reads and sets it; what it sets is made whole (a parent follows its children). */
  val checked: Var[Set[String]] = own(Var(Set.empty))
  /** Nodes ticked or unticked by the person: a click on a box, Space, or OK where `checkOnEnter`. */
  val checkToggled: Channel[CheckToggle] = Channel[CheckToggle]
  /** Whether OK (Enter) on a node with a box ticks it, rather than activating it or opening it: on a remote, where
    * Space is not to hand, by default. Right still opens a folder. */
  val checkOnEnter: Var[Boolean] = own(Var(Platform.remoteNavigation()))

  // the nodes something under which is ticked: those that show mixed, unless ticked themselves
  private var partly: Set[String] = Set.empty
  // the nodes without a box of their own everything under which is ticked
  private var covered: Set[String] = Set.empty

  def isCheckable(t: T): Boolean = checkable()(t)

  /** Node `key`'s tick. */
  def checkState(key: String): CheckState =
    if (checked().contains(key) || covered.contains(key)) CheckState.Checked
    else if (partly.contains(key)) CheckState.Mixed
    else CheckState.Unchecked

  /** Nodes `keys` ticked (or unticked), with everything loaded under them, and what is above them following. */
  def setChecked(keys: Iterable[String], on: Boolean): Unit = {
    val affected = keys.iterator.flatMap(k => Iterator(k) ++ under(k).iterator)
      .filter(k => model.get(k).exists(isCheckable)).toSet
    checked @= (if (on) checked() ++ affected else checked() -- affected)
  }

  /** The ticks of `keys` (the chosen nodes, else the one under the cursor) turned: all on, unless all are on already. */
  def toggleChecks(keys: Iterable[String] = Nil): Unit = {
    val asked = (if (keys.nonEmpty) keys.toSet else if (selection.keys().nonEmpty) selection.keys() else current().toSet)
      .filter(k => model.get(k).exists(isCheckable))
    if (asked.nonEmpty) {
      val on = !asked.forall(k => checked().contains(k))
      setChecked(asked, on)
      checkToggled @= CheckToggle(asked, on)
    }
  }

  // every key loaded under `key`
  private def under(key: String): Vector[String] = {
    val out = Vector.newBuilder[String]
    var pending = model.childKeys(Some(key)).toList
    while (pending.nonEmpty) {
      val k = pending.head
      out += k
      pending = model.childKeys(Some(k)).toList ++ pending.tail
    }
    out.result()
  }

  // what is ticked made whole: a node with ticked children ticked when all of them are, gone keys let go; and which
  // nodes are partly ticked
  private var settling = false
  private def settle(): Unit = if (!settling) {
    settling = true
    try {
      val was = checked()
      val now = mutable.HashSet.empty[String]
      val some = mutable.HashSet.empty[String]
      val full = mutable.HashSet.empty[String]
      // after its children, each node: whether all its boxes below are ticked, whether any is, and whether it or
      // anything under it has a box at all
      def visit(key: String): (Boolean, Boolean, Boolean) = {
        val kids = model.childKeys(Some(key))
        val self = model.get(key).exists(isCheckable)
        var all = true
        var any = false
        var boxes = false
        kids.foreach { k =>
          val (a, n, boxed) = visit(k)
          if (boxed) {
            boxes = true
            all = all && a
            any = any || n
          }
        }
        val on = if (boxes) all else self && was.contains(key)
        if (on && self) now += key
        if (on && !self && boxes) full += key
        if (any && !on) some += key
        (on, on || any, self || boxes)
      }
      model.childKeys(None).foreach(visit)
      partly = some.toSet
      covered = full.toSet
      val whole = now.toSet
      if (whole != was) checked @= whole
    } finally settling = false
  }

  checked.attach { _ =>
    settle()
    flatten()
  }
  checkable.attach(_ => settle())

  // ---- naming in place ----

  /** What the app does with a name typed in a row, a rename or a new node's (`TreeEdit.node` empty): `None` when it
    * has taken it (and changed the model to match: a new node inserted where it was named), or a message saying why
    * not, shown under the field, which stays open. */
  val commit: Var[TreeEdit[T] => Task[Option[String]]] =
    own(Var((_: TreeEdit[T]) => Task.pure(None: Option[String])))

  /** Node `key`'s name typed in place (see `VirtualList.edit`). */
  def edit(key: String): Unit = list.edit(key)

  /** A new node named in place under `parent` (the roots for None), at `position` among its children (at the end, past
    * the last): the parent is opened first, and its children loaded if they have to be. */
  def insertEditing(parent: Option[String], position: Int = Int.MaxValue): Unit = {
    parent.foreach(expand)
    def begin(): Unit = {
      pending = Some((parent, math.max(0, math.min(position, model.childKeys(parent).length))))
      flatten()
      list.edit(Tree.newKey, Some(""))
    }
    parent.filter(p => loading().contains(p)) match {
      case Some(p) => loading.once(_ => begin(), busy => !busy.contains(p))
      case None => begin()
    }
  }

  list.commit @= { (e: ListEdit[TreeLine[T]]) =>
    e.item.value match {
      case Some(t) =>
        val parent = model.parentOf(e.key)
        commit()(TreeEdit(Some(t), parent, model.childKeys(parent).indexOf(e.key), e.text))
      case None =>
        val (parent, position) = pending.getOrElse((None, 0))
        commit()(TreeEdit(None, parent, position, e.text))
    }
  }
  // the new node's row goes once its name is taken or let go
  list.editing.changes((was, now) => if (was.contains(Tree.newKey) && !now.contains(Tree.newKey) && pending.nonEmpty) {
    pending = None
    flatten()
  })

  // ---- dragging and dropping (see `VirtualList`'s) ----

  /** Whether nodes can be dragged out of this tree with the pointer. */
  def draggable: Var[Boolean] = list.draggable
  /** Whether nodes may land where a drop says: the app's rule. `Drop.target` is a node's key, and `Into` a node that
    * opens. */
  def accepts: Var[Drop => Boolean] = list.accepts
  /** Nodes dropped here, where `accepts` let them. */
  def dropped: Channel[Drop] = list.dropped
  /** The chosen nodes held to be pasted: the keyboard's drag. */
  def cut(): Unit = list.cut()
  /** The nodes cut put into the node under the cursor (where it opens) or after it; whether they went. */
  def paste(position: Option[DropPosition] = None): Boolean = list.paste(position)

  /** Whether `key` is one of `keys` or under one of them: where nodes being moved cannot go. */
  def inside(key: String, keys: Seq[String]): Boolean = keys.contains(key) || model.ancestors(key).exists(keys.contains)

  list.holds = (l: TreeLine[T]) => l.expandable
  list.owner = this
  // a closed folder held under a drag for a moment opens
  list.dropHover.attach(_.filter(_.position == DropPosition.Into).flatMap(_.target).foreach { key =>
    if (!expanded().contains(key)) Platform.after(Tree.openAfter, () =>
      if (list.dropHover().exists(d => d.target.contains(key) && d.position == DropPosition.Into)) expand(key))
  })

  // ---- keys and clicks ----

  list.keys = (e: KeyEvent) => {
    val at = list.indexOf(current().getOrElse(""))
    // * opens every closed folder beside the one under the cursor (typed with Shift on most keyboards)
    if (at >= 0 && e.name == "*" && !e.ctrl && !e.meta && !e.alt) Some(() => expandSiblings(list(at).key))
    else if (at < 0 || e.shift || e.ctrl || e.meta || e.alt) None else {
      val line = list(at)
      val forward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
      val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
      val ticks = line.value.exists(isCheckable)
      if (e.key == Key.Space && ticks) Some(() => toggleChecks())
      else if (e.key == Key.Enter && ticks && checkOnEnter()) Some(() => toggleChecks(List(line.key)))
      else if (e.key == forward && line.expandable && !line.expanded) Some(() => expand(line.key))
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
    val box = from + Tree.chevron + 2.0
    if (line.expandable && x >= from - 4.0 && x < box) {
      toggle(line.key)
      true
    }
    else if (line.check.nonEmpty && x >= box && x < box + Tree.box + 2.0) {
      toggleChecks(List(line.key))
      list.current @= Some(line.key)
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
  list.menu.attach(p => p.item.value.foreach(t => menu @= ListPoint(t, p.key, p.x, p.y, p.pointer)))

  model.changed.attach { _ =>
    settle()
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
  /** A tick box's size. */
  val box: Double = 18.0
  /** The key of the row a new node is named in. */
  val newKey: String = "\u0000new"
  /** How long a closed node is held under a drag before it opens, in seconds. */
  val openAfter: Double = 0.7
}
