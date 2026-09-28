package everyui.list

import everyui.component.{Component, Container}
import everyui.event.{Key, KeyEvent, Modifiers, PointerEvent}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

import scala.collection.mutable

/** A list of any length that makes only the rows that can be seen: as it scrolls, a row leaving one end is handed the
  * item arriving at the other (`ListRow.item`), so fifty thousand items cost what a screenful does.
  *
  * `items` is the app's, and the list follows it; each item has a `key`, by which the cursor, the selection and the
  * scroll are kept while items come and go around them. Rows are `rowHeight` tall, or with `measured` as tall as each
  * one draws itself (`rowHeight` is then the guess for one not yet drawn).
  *
  * The list takes the focus as one component, with a cursor (`current`) among its rows: the arrows, Page Up and Down,
  * Home and End move it (Shift extends the selection, the primary modifier moves it alone), Enter activates the row
  * (`activated`), and a letter jumps to the next row whose label starts with it. At either end an arrow leaves the list
  * as the focus layer says. A click picks a row, Shift+click a range, Primary+click one more; Primary+A picks them all.
  * `selection` is the app's to read and set. On a remote only one row is chosen, and it follows the cursor.
  *
  * {{{
  *   val list = VirtualList.text(files, (f: File) => f.path, (f: File) => f.name)
  *   list.size.width @= 320.0
  *   list.size.height := panel.size.height()
  *   list.activated.attach(f => open(f))
  *   list.scrollTo(list.indexOf(path))
  * }}} */
class VirtualList[T](val items: Var[Vector[T]], val keyOf: T => String, val labelOf: T => String,
                     val rowHeight: Double = VirtualList.rowHeight, measured: Boolean = false)
                    (makeRow: () => ListRow[T]) extends Container {
  override def kind: String = "VirtualList"

  /** How far the rows are scrolled, in pixels. */
  val offset: Var[Double] = own(Var(0.0))
  /** The key of the row under the cursor. */
  val current: Var[Option[String]] = own(Var(None))
  val selection: Selection = new Selection(!Platform.remoteNavigation())
  /** A row activated: Enter on it (let go, since a held OK asks for its menu), a double click, or a single click where
    * `clickActivates` (a touch screen). */
  val activated: Channel[T] = Channel[T]
  /** A context menu asked for on a row: the secondary button, the menu key, Shift+F10, or OK held. */
  val menu: Channel[ListPoint[T]] = Channel[ListPoint[T]]
  val clickActivates: Var[Boolean] = own(Var(!Platform.pointerSupport()))
  /** Rows made beyond each end of what shows, so a short scroll has them ready. */
  val overscan: Var[Int] = own(Var(4))

  /** Keys a list built on this one answers first (a tree's Left and Right): the action for a press, if it takes it. */
  var keys: KeyEvent => Option[() => Unit] = _ => None
  /** A click at `x` (from the row's left) on row `index` that a list built on this one takes itself (a tree's chevron). */
  var clicks: (Int, Double) => Boolean = (_, _) => false

  private var shown: Vector[T] = Vector.empty
  private val indexes = mutable.HashMap.empty[String, Int]
  private val heights = mutable.HashMap.empty[String, Double]
  private var tops: Array[Double] = Array(0.0)
  private var topsStale = false
  private val placed = mutable.HashMap.empty[Int, ListRow[T]]
  private var spare: List[ListRow[T]] = Nil
  private var currentAt = -1
  private var laying = false
  private var again = false

  private val thumb = new Container {
    size.width @= 6.0
    radius @= 3.0
    backgroundColor := Theme.muted().withAlpha(0.45)
  }

  focusable @= true
  size.width @= 320.0
  size.height @= 400.0

  // ---- the items, by index and by key ----

  def count: Int = shown.length
  def apply(index: Int): T = shown(index)
  def keyAt(index: Int): String = keyOf(shown(index))
  /** Where `key` is in the list, -1 where it is not. */
  def indexOf(key: String): Int = indexes.getOrElse(key, -1)
  /** The item under the cursor. */
  def currentItem: Option[T] = current().flatMap(k => indexes.get(k)).map(i => shown(i))
  /** The chosen items, in the list's order. */
  def selectedItems: Vector[T] = {
    val keys = selection.keys()
    if (keys.isEmpty) Vector.empty else shown.filter(t => keys.contains(keyOf(t)))
  }

  /** Where row `index` starts, from the top of the first. */
  def top(index: Int): Double =
    if (!measured) index * rowHeight
    else {
      if (topsStale) retop()
      tops(math.max(0, math.min(index, tops.length - 1)))
    }
  def heightAt(index: Int): Double =
    if (!measured) rowHeight else heights.getOrElse(keyOf(shown(index)), rowHeight)
  /** How tall all the rows are together. */
  def total: Double = top(count)

  /** The row at `y` down the rows (0 at the first's top), kept within the list. */
  def indexAt(y: Double): Int = if (count == 0) -1 else if (!measured) {
    math.max(0, math.min(count - 1, (y / rowHeight).toInt))
  } else {
    if (topsStale) retop()
    var low = 0
    var high = count - 1
    while (low < high) {
      val mid = (low + high + 1) / 2
      if (tops(mid) <= y) low = mid else high = mid - 1
    }
    low
  }

  private def retop(): Unit = {
    val next = new Array[Double](shown.length + 1)
    var i = 0
    while (i < shown.length) {
      next(i + 1) = next(i) + heights.getOrElse(keyOf(shown(i)), rowHeight)
      i += 1
    }
    tops = next
    topsStale = false
  }

  // ---- scrolling ----

  private def furthest: Double = math.max(0.0, total - size.height())

  def scrollBy(delta: Double): Unit = scrollTop(offset() + delta)
  /** Scrolled `to` pixels down, within the rows. */
  def scrollTop(to: Double): Unit = offset @= math.max(0.0, math.min(furthest, to))

  /** Row `index` brought to `align` of the view: 0 at the top, 0.5 in the middle, 1 at the bottom. */
  def scrollTo(index: Int, align: Double = 0.0): Unit = if (index >= 0 && index < count) {
    scrollTop(top(index) - (size.height() - heightAt(index)) * align)
  }

  /** Scrolled the least that shows all of row `index`. */
  def reveal(index: Int): Unit = if (index >= 0 && index < count) {
    val start = top(index)
    val end = start + heightAt(index)
    if (start < offset()) scrollTop(start)
    else if (end > offset() + size.height()) scrollTop(end - size.height())
  }

  // ---- the cursor and the selection ----

  /** The cursor onto row `index`, which is shown: with `extend`, the selection runs to it from the anchor; with `keep`,
    * only the cursor moves; else it is the one row chosen. On a list of one choice, it is always chosen. */
  def moveTo(index: Int, extend: Boolean = false, keep: Boolean = false): Unit = if (index >= 0 && index < count) {
    val key = keyAt(index)
    val multi = selection.multiple()
    if (multi && extend) {
      val from = selection.anchor().map(indexOf).filter(_ >= 0).getOrElse(math.max(0, currentAt))
      selectRange(from, index)
    }
    else if (!(multi && keep)) selection.only(key)
    current @= Some(key)
    reveal(index)
  }

  /** The rows from `from` to `to` chosen, and only those; the anchor stays at `from`. */
  def selectRange(from: Int, to: Int): Unit = if (count > 0) {
    val a = math.max(0, math.min(from, to))
    val b = math.min(count - 1, math.max(from, to))
    val keys = Set.newBuilder[String]
    var i = a
    while (i <= b) {
      keys += keyAt(i)
      i += 1
    }
    selection.keys @= keys.result()
    selection.anchor @= Some(keyAt(math.max(0, math.min(count - 1, from))))
  }

  /** Every row chosen, where more than one may be. */
  def selectAll(): Unit = if (selection.multiple() && count > 0) {
    val anchor = selection.anchor()
    selection.keys @= shown.iterator.map(keyOf).toSet
    selection.anchor @= anchor.orElse(Some(keyAt(0)))
  }

  /** The cursor on `key`, which is chosen alone and scrolled into view. */
  def select(key: String): Unit = {
    val i = indexOf(key)
    if (i >= 0) moveTo(i)
  }

  /** Row `index` activated, as Enter or a double click does. */
  def activate(index: Int): Unit = if (index >= 0 && index < count) activated @= shown(index)

  /** The context menu asked for on row `index`, at `x`, `y` (the row's start, where not given). */
  def menuAt(index: Int, x: Double = -1.0, y: Double = -1.0): Unit = if (index >= 0 && index < count) {
    val atX = if (x < 0.0) absoluteLeft + editStartOf(index) else x
    val atY = if (y < 0.0) absoluteTop + top(index) - offset() + heightAt(index) else y
    menu @= ListPoint(shown(index), keyAt(index), atX, atY)
  }

  private def editStartOf(index: Int): Double = placed.get(index).map(_.editStart).getOrElse(ListRow.inset)

  // ---- keys ----

  override def keepsEvent(e: KeyEvent): Boolean = action(e).nonEmpty

  private def primary(ctrl: Boolean, meta: Boolean): Boolean = if (Platform.commandKey()) meta else ctrl

  /** What a press does here, if the list takes it. */
  private def action(e: KeyEvent): Option[() => Unit] = keys(e).orElse(own(e))

  private def own(e: KeyEvent): Option[() => Unit] = if (count == 0) None else {
    val at = currentAt
    val keep = primary(e.ctrl, e.meta)
    val page = math.max(1, (size.height() / rowHeight).toInt - 1)
    e.key match {
      case Key.ArrowUp if at > 0 => Some(() => moveTo(at - 1, e.shift, keep))
      case Key.ArrowUp if at < 0 => Some(() => moveTo(0))
      case Key.ArrowDown if at < count - 1 => Some(() => moveTo(at + 1, e.shift, keep))
      case Key.PageUp => Some(() => moveTo(math.max(0, at - page), e.shift, keep))
      case Key.PageDown => Some(() => moveTo(math.min(count - 1, at + page), e.shift, keep))
      case Key.Enter if at >= 0 => Some(() => enterDown(e))
      case Key.Space if at >= 0 && keep && selection.multiple() => Some(() => selection.toggle(keyAt(at)))
      case Key.ContextMenu if at >= 0 => Some(() => menuAt(at))
      case Key.Other if e.name == "F10" && e.shift && at >= 0 => Some(() => menuAt(at))
      case Key.Other if e.name == "Home" => Some(() => moveTo(0, e.shift, keep))
      case Key.Other if e.name == "End" => Some(() => moveTo(count - 1, e.shift, keep))
      case Key.Other if keep && !e.alt && e.name.toLowerCase == "a" && selection.multiple() => Some(() => selectAll())
      case Key.Other if e.name.length == 1 && !e.ctrl && !e.meta && !e.alt => Some(() => typeAhead(e.name))
      case _ => None
    }
  }

  // Enter activates as it is let go, so that held it can ask for the menu instead
  private var entering = false
  private var enterHeld = false
  private var enters = 0

  private def enterDown(e: KeyEvent): Unit = if (!entering) {
    entering = true
    enterHeld = false
    enters += 1
    val mine = enters
    val at = currentAt
    Platform.after(VirtualList.holdSeconds, () => if (entering && mine == enters && at == currentAt) {
      enterHeld = true
      menuAt(at)
    })
  }

  event.key.down.attach { e =>
    if (e.key != Key.Enter || !entering) action(e).foreach(f => f())
  }
  event.key.up.attach { e =>
    if (e.key == Key.Enter && entering) {
      entering = false
      if (!enterHeld) activate(currentAt)
    }
  }
  focused.attach(on => if (!on) entering = false)

  // letters typed within a moment of each other are one word to find
  private var typed = ""
  private var typedAt = 0.0

  private def typeAhead(letter: String): Unit = {
    val now = Platform.ticks()
    typed = (if (now - typedAt > 800.0) "" else typed) + letter.toLowerCase
    typedAt = now
    val from = if (typed.length == 1) currentAt + 1 else math.max(0, currentAt)
    var i = 0
    var found = -1
    while (found < 0 && i < count) {
      val at = (from + i) % count
      if (labelOf(shown(at)).toLowerCase.startsWith(typed)) found = at
      i += 1
    }
    if (found >= 0) moveTo(found)
  }

  // ---- the pointer ----

  private var lastClick = -1
  private var lastClickAt = 0.0

  /** A click at `x`, `y` in the root's coordinates: the row under it, picked as the modifiers held say. */
  private[everyui] def clickAt(x: Double, y: Double, held: Modifiers): Unit = {
    val down = y - absoluteTop + offset()
    if (down >= 0.0 && down < total) {
      val i = indexAt(down)
      if (!clicks(i, x - absoluteLeft)) press(i, held)
    }
  }

  /** Row `index` clicked with `held` down. */
  private[everyui] def press(index: Int, held: Modifiers): Unit = if (index >= 0 && index < count) {
    val key = keyAt(index)
    val multi = selection.multiple()
    val now = Platform.ticks()
    val double = lastClick == index && now - lastClickAt < VirtualList.doubleSeconds * 1000.0
    lastClick = index
    lastClickAt = if (double) 0.0 else now
    if (multi && held.shift) {
      val from = selection.anchor().map(indexOf).filter(_ >= 0).getOrElse(index)
      selectRange(from, index)
      current @= Some(key)
    }
    else if (multi && primary(held.ctrl, held.meta)) {
      selection.toggle(key)
      current @= Some(key)
    }
    else {
      moveTo(index)
      if (double || clickActivates()) activate(index)
    }
  }

  event.click.attach(e => clickAt(e.x, e.y, Platform.modifiers()))
  cursor @= "default"
  event.pointer.wheel.attach(e => scrollBy(e.y))
  // a finger drags the rows along where there is no mouse to wheel them
  if (Platform.touchSupport() && !Platform.pointerSupport()) event.pointer.down.attach { e =>
    val from = e.y
    val was = offset()
    Pointers.track(m => scrollTop(was + from - m.y), _ => ())
  }
  thumb.event.pointer.down.attach { e =>
    e.consume()
    val from = e.y
    val was = offset()
    val room = math.max(1.0, size.height() - thumb.size.height())
    Pointers.track(m => scrollTop(was + (m.y - from) * furthest / room), _ => ())
  }
  thumb.event.click.attach(_.consume())

  // ---- the rows ----

  /** Every row made so far, those out of use among them (hidden). */
  def rows: Vector[ListRow[T]] = children().collect { case r: ListRow[T] @unchecked => r }

  /** The row showing `index`, if it is made. */
  def rowAt(index: Int): Option[ListRow[T]] = placed.get(index)

  private def take(): ListRow[T] = spare match {
    case row :: rest =>
      spare = rest
      row
    case Nil =>
      val row = makeRow()
      if (measured) row.size.height.attach(_ => note(row))
      row
  }

  // a row drawn at a height of its own: the rows after it move
  private def note(row: ListRow[T]): Unit = {
    val h = row.size.height()
    if (h > 0.0) row.item().foreach { t =>
      val key = keyOf(t)
      if (!heights.get(key).contains(h)) {
        heights(key) = h
        topsStale = true
        later()
      }
    }
  }

  /** The rows placed again: those that can be seen, handed their items, the rest put aside. */
  def refresh(): Unit = if (laying) again = true else {
    laying = true
    try {
      again = true
      var turns = 0
      while (again && turns < 4) {
        again = false
        turns += 1
        place()
      }
    } finally laying = false
  }

  private def place(): Unit = {
    val height = size.height()
    val width = size.width()
    val first = if (count == 0) 0 else math.max(0, indexAt(offset()) - overscan())
    val last = if (count == 0) -1 else math.min(count - 1, indexAt(offset() + height) + overscan())
    placed.filterInPlace { (i, row) =>
      val keep = i >= first && i <= last
      if (!keep) {
        row.visible @= false
        row.item @= None
        spare = row :: spare
      }
      keep
    }
    var made: List[Component] = Nil
    val chosen = selection.keys()
    val cursor = current()
    val lit = focused()
    var i = first
    while (i <= last) {
      val row = placed.getOrElseUpdate(i, {
        val r = take()
        if (r.parent().isEmpty) made = r :: made
        r
      })
      val t = shown(i)
      val key = keyOf(t)
      row.item @= Some(t)
      if (measured) note(row)
      row.index @= i
      row.selected @= chosen.contains(key)
      row.current @= cursor.contains(key)
      row.active @= lit
      row.editing @= editingKey.contains(key)
      row.size.width @= width
      if (!measured) row.size.height @= rowHeight
      row.position.top @= top(i) - offset()
      row.visible @= true
      i += 1
    }
    if (made.nonEmpty) children ++= made.reverse
    // the thumb last, over the rows
    if (children().lastOption.forall(c => !(c eq thumb))) children @= children().filterNot(_ eq thumb) :+ thumb
    val all = total
    thumb.visible @= all > height + 0.5
    val length = math.max(24.0, height * height / math.max(1.0, all))
    thumb.size.height @= length
    thumb.position.left @= width - 8.0
    thumb.position.top @= (if (furthest <= 0.0) 0.0 else offset() / furthest * (height - length))
  }

  private var waiting = false
  private def later(): Unit = if (!waiting) {
    waiting = true
    Platform.later { () =>
      waiting = false
      keepingPlace(())
      refresh()
    }
  }

  /** `change` made with the first row that shows kept where it is, when it is still in the list. */
  private def keepingPlace(change: => Unit): Unit = {
    val anchor = if (count == 0 || offset() <= 0.0) None else {
      val i = indexAt(offset())
      Some((keyAt(i), top(i) - offset()))
    }
    change
    if (measured) retop()
    anchor.foreach { case (key, dy) => indexes.get(key).foreach(i => offset @= top(i) - dy) }
    offset @= math.max(0.0, math.min(furthest, offset()))
  }

  // ---- editing (see `edit`) ----
  private[list] var editingKey: Option[String] = None
  // a row put in for a new item while its name is being typed: where, and what stands for it
  private[list] var placeholder: Option[(Int, T)] = None

  /** The items again, as they are now, with the cursor, the selection and the scroll kept by key. */
  def reindex(): Unit = {
    val was = currentAt
    keepingPlace {
      val base = items()
      shown = placeholder match {
        case Some((at, t)) => base.patch(math.max(0, math.min(at, base.length)), Vector(t), 0)
        case None => base
      }
      indexes.clear()
      var i = 0
      while (i < shown.length) {
        indexes(keyOf(shown(i))) = i
        i += 1
      }
      topsStale = measured
    }
    selection.keep(k => indexes.contains(k))
    current() match {
      // the row under the cursor gone, the cursor goes to what took its place
      case Some(key) if !indexes.contains(key) =>
        current @= (if (count == 0) None else Some(keyAt(math.max(0, math.min(was, count - 1)))))
        if (!selection.multiple()) current().foreach(k => selection.only(k))
      case _ => ()
    }
    currentAt = current().flatMap(indexes.get).getOrElse(-1)
    refresh()
  }

  current.attach { c =>
    currentAt = c.flatMap(indexes.get).getOrElse(-1)
    refresh()
  }
  // arriving, the focus lands on the row it left, else the first chosen, else the first
  focused.attach { on =>
    if (on && current().isEmpty && count > 0) {
      current @= selection.keys().headOption.filter(indexes.contains).orElse(Some(keyAt(0)))
      if (!selection.multiple()) current().foreach(k => selection.only(k))
    }
    refresh()
  }
  items.attach(_ => reindex())
  selection.keys.attach(_ => refresh())
  offset.attach(_ => refresh())
  size.width.attach(_ => clip())
  size.height.attach { _ =>
    clip()
    offset @= math.max(0.0, math.min(furthest, offset()))
  }
  private def clip(): Unit = {
    node.setClip(size.width(), size.height())
    refresh()
  }

  children += thumb
  clip()
  reindex()
}

object VirtualList {
  /** A row's height unless the list says. */
  val rowHeight: Double = 28.0
  /** How long OK is held before it asks for the menu, in seconds. */
  val holdSeconds: Double = 0.5
  /** How close two clicks come to be a double click, in seconds. */
  val doubleSeconds: Double = 0.45

  /** A list of text rows: each item's label, dressed as `decorate` says. */
  def text[T](items: Var[Vector[T]], key: T => String, label: T => String,
              decorate: T => Decoration = (_: T) => Decoration.plain,
              rowHeight: Double = VirtualList.rowHeight): VirtualList[T] =
    new VirtualList[T](items, key, label, rowHeight)(() => new TextRow[T](label, decorate))
}
