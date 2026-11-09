package everyui.list

import everyui.component.{Component, Container}
import everyui.event.{Flick, Key, KeyEvent, Modifiers, Pointer, PointerEvent, ScrollPress}
import everyui.i18n.Direction
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A list of any length that makes only the rows that can be seen: as it scrolls, a row leaving one end is handed the
  * item arriving at the other (`ListRow.item`), so fifty thousand items cost what a screenful does.
  *
  * `items` is the app's, and the list follows it; each item has a `key`, by which the cursor, the selection and the
  * scroll are kept while items come and go around them. Rows are `rowHeight` tall, or with `measuredRows` as tall as
  * each one draws itself (`rowHeight` is then the guess for one not yet drawn).
  *
  * The list takes the focus as one component, with a cursor (`current`) among its rows: the arrows, Page Up and Down,
  * Home and End move it (Shift extends the selection, the primary modifier moves it alone), Enter activates the row
  * (`activated`), and a letter jumps to the next row whose label starts with it. At either end an arrow leaves the list
  * as the focus layer says. A click picks a row, Shift+click a range, Primary+click one more; Primary+A picks them all.
  * `selection` is the app's to read and set. On a remote only one row is chosen, and it follows the cursor.
  *
  * Given a `source`, the rows are read from it by place instead of from `items`, and only those in view are asked
  * for: a server's millions, paged (`everyui.paging.PagedSource`). A row not here yet is drawn by `placeholder` (a
  * faint bar) rather than by the app's row, until it comes; `loadingView` shows in the list's box while the source
  * looks for the first rows of a query, and `emptyView` where there are none. There the rows are `rowHeight` tall (or as
  * tall as a `sized` source says: a log's wrapped lines), a letter does not jump, and Primary+A chooses every row,
  * those never loaded among them (`Selection.all`).
  *
  * {{{
  *   val list = VirtualList.text(files, (f: File) => f.path, (f: File) => f.name)
  *   list.size.width @= 320.0
  *   list.size.height := panel.size.height()
  *   list.activated.attach(f => open(f))
  *   list.scrollTo(list.indexOf(path))
  * }}} */
class VirtualList[T](val items: Var[Vector[T]], val keyOf: T => String, val labelOf: T => String,
                     val rowHeight: Double = VirtualList.rowHeight, measuredRows: Boolean = false,
                     val source: Option[ListSource[T]] = None)
                    (makeRow: () => ListRow[T]) extends Container {
  override def kind: String = "VirtualList"
  // a list a screen reader moves through by its cursor: a list box of options, unless it says otherwise (a tree, a
  // table, a conversation)
  override private[everyui] def impliedRole: String = "listbox"
  // the key of the row last said to be the cursor's (`RenderNode.setActiveRow`), while the list has the focus
  private var toldRow: String = ""
  // rows at heights of their own are the items' alone: a source's are `rowHeight` tall, or as tall as it says (`sized`)
  private val ownHeights = measuredRows && source.isEmpty
  // rows at heights the source knows (a log's wrapped lines)
  private def sizedSource: Boolean = source.exists(_.sized)

  /** How far the rows are scrolled, in pixels. */
  val offset: Var[Double] = own(Var(0.0))
  /** The key of the row under the cursor. */
  val current: Var[Option[String]] = own(Var(None))
  val selection: Selection = new Selection(!Platform.remoteNavigation())
  /** A row activated: Enter on it (let go, since a held OK asks for its menu), a double click, or a single click that
    * activates (`clickActivates`: a finger's tap). */
  val activated: Channel[T] = Channel[T]
  /** A context menu asked for on a row: the secondary button, the menu key, Shift+F10, or OK held. */
  val menu: Channel[ListPoint[T]] = Channel[ListPoint[T]]
  /** A finger dragged across the rows, by how far: for a list whose rows scroll sideways (a log's long lines). Up and
    * down are the list's own. */
  val dragAcross: Channel[Double] = Channel[Double]
  /** Whether a single click activates the row it puts the cursor on, as a tap does on a phone, rather than only
    * choosing it (a double click activates either way). None, by default, lets each click say: a finger's tap
    * activates, on any device, a touch laptop's as much as a phone's, while a mouse's click chooses, unless the device
    * has no pointer of its own (a phone, a television), where every click activates. `Some(true)` makes every click
    * activate, a mouse's too (a picker's choice, made and done with), and `Some(false)` none, a finger's neither. */
  val clickActivates: Var[Option[Boolean]] = own(Var(None))
  /** Which items are the one in use, of a "pick one of these" list (the model a conversation runs on, the topic shown):
    * their rows are drawn as it (`ListRow.inUse`), apart from the cursor and the selection. None, by default. */
  val inUse: Var[T => Boolean] = own(Var((_: T) => false))
  /** Whether a finger held on a row shows its tip (`ListRow.tip`) where that opens no menu. None, by default, lets
    * each hold say: a finger's does, on any device, as does any on a touch screen with no pointer of its own (a
    * phone); `Some` says for every hold. */
  val holdTips: Var[Option[Boolean]] = own(Var(None))
  /** Rows made beyond each end of what shows, so a short scroll has them ready: half as many at Reduced, and one at
    * Minimal (`everyui.perf.Quality`). */
  val overscan: Var[Int] = own(Var(4))
  /** Whether the rows are kept scrolled to their end as they come, go and are drawn at heights of their own, rather
    * than with the first that shows kept where it is: a chat's transcript while its newest message is in view. */
  val followsEnd: Var[Boolean] = own(Var(false))
  /** What stands in a row whose item has not come from the `source`, sized to the row, which draws the cursor and the
    * selection under it (a `PlaceholderRow`): by default a faint bar (`VirtualList.blank`). None hands the app's own
    * row no item instead (`ListRow.item` None, `present` false), for a row that draws its own placeholder. */
  val placeholder: Var[Option[() => Component]] = own(Var[Option[() => Component]](Some(() => VirtualList.blank())))
  /** Shown in the middle of the list's box, in place of its rows, while the source looks for the first rows of a query
    * (`ListSource.searching`): `VirtualList.loading()`, say. None by default: the rows stand in until they come. */
  val loadingView: Var[Option[Component]] = own(Var(None))
  /** Shown in the middle of the list's box when there are no rows (a query that found none, once it has answered):
    * `VirtualList.empty()`, say. */
  val emptyView: Var[Option[Component]] = own(Var(None))

  /** Keys a list built on this one answers first (a tree's Left and Right): the action for a press, if it takes it. */
  var keys: KeyEvent => Option[() => Unit] = _ => None
  /** A click at `x` (from the row's start edge: its right, right to left) on row `index` that a list built on this
    * one takes itself (a tree's chevron). */
  var clicks: (Int, Double) => Boolean = (_, _) => false

  private var shown: Vector[T] = Vector.empty
  // (immutable maps: a Roku keeps one keyed by strings as an associative array, and has no mutable one)
  private var indexes: Map[String, Int] = Map.empty
  private var heights: Map[String, Double] = Map.empty
  private var tops: Array[Double] = Array(0.0)
  private var topsStale = false
  private var placed: Map[Int, ListRow[T]] = Map.empty
  private var spare: List[ListRow[T]] = Nil
  private var spareStands: List[ListRow[T]] = Nil
  // whether a row has been handed a place it did not have since the rows were last put in reading order
  private var shuffled = false
  // whether the loading view shows in place of the rows
  private var blank = false
  private var currentAt = -1
  private var laying = false
  private var again = false

  // the rows' tips (ListRow.tip)
  private val tips = new RowTips[T](this)

  private val thumb = new Container {
    size.width @= 6.0
    radius @= 3.0
    backgroundColor := Theme.muted().withAlpha(0.45)
  }

  focusable @= true
  sayRole()
  clickFocus @= true
  size.width @= 320.0
  size.height @= 400.0

  // ---- the items, by index and by key ----

  def count: Int = source match {
    case Some(s) => s.count
    case None => shown.length
  }
  /** The item at `index` (which, from a source, has come: see `itemAt`). */
  def apply(index: Int): T = itemAt(index).get
  /** The item at `index`, where it is here: always from `items`, and from a source once its row has come. */
  def itemAt(index: Int): Option[T] = source match {
    case Some(s) => s.at(index)
    case None => if (index >= 0 && index < shown.length) Some(shown(index)) else None
  }
  def keyAt(index: Int): String = source match {
    case Some(s) => s.keyAt(index)
    case None => keyOf(shown(index))
  }
  /** Where `key` is in the list, -1 where it is not. */
  def indexOf(key: String): Int = source match {
    case Some(s) => s.indexOf(key)
    case None => indexes.getOrElse(key, -1)
  }
  /** The item under the cursor. */
  def currentItem: Option[T] = current().map(indexOf).flatMap(itemAt)
  /** The chosen items, in the list's order: from a source, those that are here. */
  def selectedItems: Vector[T] = source match {
    case Some(s) => if (selection.isEmpty) Vector.empty else s.here.filter(t => selection.contains(keyOf(t)))
    case None =>
      val keys = selection.keys()
      if (keys.isEmpty) Vector.empty else shown.filter(t => keys.contains(keyOf(t)))
  }

  /** Where row `index` starts, from the top of the first. */
  def top(index: Int): Double =
    if (sizedSource) source.get.top(math.max(0, math.min(index, count)))
    else if (!ownHeights) index * rowHeight
    else {
      if (topsStale) retop()
      tops(math.max(0, math.min(index, tops.length - 1)))
    }
  def heightAt(index: Int): Double =
    if (sizedSource) top(index + 1) - top(index)
    else if (!ownHeights) rowHeight else heights.getOrElse(keyOf(shown(index)), guessAt(index))
  /** How tall all the rows are together. */
  def total: Double = top(count)

  /** The row at `y` down the rows (0 at the first's top), kept within the list. */
  def indexAt(y: Double): Int = if (count == 0) -1 else if (sizedSource) {
    math.max(0, math.min(count - 1, source.get.indexAt(y)))
  } else if (!ownHeights) {
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

  /** How tall a row is taken to be until it has drawn itself, where the rows are as tall as each draws (`measuredRows`):
    * `rowHeight` unless the list is told better (a chat's message by its length), so that the rows made first are about
    * those that show, and the scroll is about as long as it will be. Asked once for each item at each width. */
  var estimate: T => Double = _ => rowHeight
  // the estimates by place, while the items and the width stay as they are (0 for one not asked yet)
  private var guesses: Array[Double] = new Array[Double](0)
  private def guessAt(i: Int): Double = {
    if (guesses.length != shown.length) guesses = new Array[Double](shown.length)
    if (guesses(i) <= 0.0) guesses(i) = math.max(1.0, estimate(shown(i)))
    guesses(i)
  }

  private def retop(): Unit = {
    val next = new Array[Double](shown.length + 1)
    var i = 0
    while (i < shown.length) {
      next(i + 1) = next(i) + heights.getOrElse(keyOf(shown(i)), guessAt(i))
      i += 1
    }
    tops = next
    topsStale = false
  }

  // ---- scrolling ----

  private def furthest: Double = math.max(0.0, total - size.height())

  // the rows following a finger, and gliding on once it lifts; and whether the press last put down caught a glide
  private val flick = new Flick((d: Double) => {
    val was = offset()
    scrollBy(d)
    offset() - was
  })
  private var caught = false

  /** A flick still carrying the rows on stops where it is: the app scrolling them itself, its newest row brought into
    * view, say. A row brought into view, the wheel and a press put down stop it already. */
  def stopFling(): Unit = flick.stop()

  /** Whether a flick is still carrying the rows on, after the finger that flicked them lifted. */
  def gliding: Boolean = flick.gliding

  /** Scrolled `delta` pixels on from where it is, from the place kept first where rows above it were just drawn at
    * heights of their own. A row whose place is still to be kept keeps it, moved by as far as this went: a flick or a
    * wheel turning as an earlier page of a chat comes in above would otherwise have the rows jump by however much
    * taller those drew than they were guessed. */
  def scrollBy(delta: Double): Unit = {
    settle()
    val anchor = held
    val from = offset()
    scrollTop(from + delta)
    if (anchor.nonEmpty) held = anchor.map { case (key, dy) => (key, dy - (offset() - from)) }
  }
  /** Scrolled `to` pixels down, within the rows. (Where the rows showed before some were drawn at heights of their own
    * is let go: they are to show from here now.) */
  def scrollTop(to: Double): Unit = {
    held = None
    aimed = None
    offset @= math.max(0.0, math.min(furthest, to))
  }

  /** Row `index` brought to `align` of the view: 0 at the top, 0.5 in the middle, 1 at the bottom, and kept there as
    * the rows around it draw at heights of their own, until the rows are scrolled some other way. */
  def scrollTo(index: Int, align: Double = 0.0): Unit = if (index >= 0 && index < count) {
    stopFling()
    val to = top(index) - (size.height() - heightAt(index)) * align
    scrollTop(to)
    // (where the rows end before it could get there, the first that shows is kept instead, as ever)
    if (offset() == to) aimed = Some((keyAt(index), align))
  }

  /** Scrolled the least that shows all of row `index`. */
  def reveal(index: Int): Unit = if (index >= 0 && index < count) {
    stopFling()
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

  /** The rows from `from` to `to` chosen, and only those; the anchor stays at `from`. From a source, those of them that
    * are here and the two ends: a row between never loaded has no key to choose it by. */
  def selectRange(from: Int, to: Int): Unit = if (count > 0) {
    val a = math.max(0, math.min(from, to))
    val b = math.min(count - 1, math.max(from, to))
    var keys = Set.empty[String]
    source match {
      case Some(s) =>
        s.here.foreach { t =>
          val key = keyOf(t)
          val at = indexOf(key)
          if (at >= a && at <= b) keys += key
        }
        keys = keys + keyAt(a) + keyAt(b)
      case None =>
        var i = a
        while (i <= b) {
          keys += keyAt(i)
          i += 1
        }
    }
    selection.all @= false
    selection.keys @= keys
    selection.anchor @= Some(keyAt(math.max(0, math.min(count - 1, from))))
  }

  /** Every row chosen, where more than one may be: from a source, those not loaded among them (`Selection.all`). */
  def selectAll(): Unit = if (selection.multiple() && count > 0) {
    val anchor = selection.anchor()
    if (source.nonEmpty) selection.everything() else selection.keys @= shown.map(keyOf).toSet
    selection.anchor @= anchor.orElse(Some(keyAt(0)))
  }

  /** The cursor on `key`, which is chosen alone and scrolled into view. */
  def select(key: String): Unit = {
    val i = indexOf(key)
    if (i >= 0) moveTo(i)
  }

  /** Row `index` activated, as Enter or a double click does. */
  def activate(index: Int): Unit = if (index >= 0 && index < count) itemAt(index).foreach(t => activated @= t)

  /** The context menu asked for on row `index`, at `x`, `y`, the pointer's place (the row's start, where not given: the
    * keys asking). */
  def menuAt(index: Int, x: Double = -1.0, y: Double = -1.0): Unit = if (index >= 0 && index < count) {
    val atX = if (x < 0.0) absoluteLeft + editStartOf(index) else x
    val atY = if (y < 0.0) absoluteTop + top(index) - offset() + heightAt(index) else y
    itemAt(index).foreach(t => menu @= ListPoint(t, keyAt(index), atX, atY, x >= 0.0))
  }

  private def editStartOf(index: Int): Double = placed.get(index).map(_.editStart).getOrElse(ListRow.inset)

  // ---- keys ----

  override def keepsEvent(e: KeyEvent): Boolean = action(e).nonEmpty

  private def primary(ctrl: Boolean, meta: Boolean): Boolean = if (Platform.commandKey()) meta else ctrl

  /** What a press does here, if the list takes it. */
  private def action(e: KeyEvent): Option[() => Unit] = keys(e).orElse(own(e))

  private def own(e: KeyEvent): Option[() => Unit] = if (count == 0 || blank) None else {
    val at = currentAt
    val keep = primary(e.ctrl, e.meta)
    // the arrows with Shift, or the primary modifier to move without selecting; with Alt or the other modifier they are
    // the platform's
    val arrow = !e.alt && !(if (Platform.commandKey()) e.ctrl else e.meta)
    val page = math.max(1, (size.height() / rowHeight).toInt - 1)
    e.key match {
      case Key.ArrowUp if arrow && at > 0 => Some(() => moveTo(at - 1, e.shift, keep))
      case Key.ArrowUp if arrow && at < 0 => Some(() => moveTo(0))
      case Key.ArrowDown if arrow && at < count - 1 => Some(() => moveTo(at + 1, e.shift, keep))
      case Key.PageUp => Some(() => moveTo(math.max(0, at - page), e.shift, keep))
      case Key.PageDown => Some(() => moveTo(math.min(count - 1, at + page), e.shift, keep))
      case Key.Enter if at >= 0 => Some(() => enterDown(e))
      case Key.Space if at >= 0 && keep && selection.multiple() => Some(() => selection.toggle(keyAt(at)))
      case Key.Other if e.name == "F2" && renames() && at >= 0 => Some(() => edit(keyAt(at)))
      case Key.ContextMenu if at >= 0 => Some(() => menuAt(at))
      case Key.Other if e.name == "F10" && e.shift && at >= 0 => Some(() => menuAt(at))
      case Key.Other if e.name == "Home" => Some(() => moveTo(0, e.shift, keep))
      case Key.Other if e.name == "End" => Some(() => moveTo(count - 1, e.shift, keep))
      case Key.Other if keep && !e.alt && e.name.toLowerCase == "a" && selection.multiple() => Some(() => selectAll())
      case Key.Other if e.name.length == 1 && !e.ctrl && !e.meta && !e.alt && source.isEmpty =>
        Some(() => typeAhead(e.name))
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

  // whether the keys are moving the cursor now: its row's tip follows it then
  private var keying = false
  event.key.down.attach { e =>
    if (e.key != Key.Enter || !entering) {
      keying = true
      try action(e).foreach(f => f()) finally keying = false
    }
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

  /** A click at `x`, `y` in the root's coordinates: the row under it, picked as the modifiers held say; `finger` is a
    * finger's tap (`PointerEvent.finger`), which activates it where `clickActivates` leaves it to each click. */
  private[everyui] def clickAt(x: Double, y: Double, held: Modifiers, finger: Boolean = false): Unit = {
    val down = y - absoluteTop + offset()
    if (down >= 0.0 && down < total && !blank) {
      val i = indexAt(down)
      val along = if (Direction.rtl()) absoluteLeft + size.width() - x else x - absoluteLeft
      if (!clicks(i, along)) press(i, held, finger)
    }
  }

  /** Row `index` clicked with `held` down, by a finger's tap where `tap`. */
  private[everyui] def press(index: Int, held: Modifiers, tap: Boolean = false): Unit = if (index >= 0 && index < count) {
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
      if (double || activates(tap)) activate(index)
    }
  }

  // whether a single click activates its row: as the app says, else a finger's tap, or any where there is no pointer
  private def activates(finger: Boolean): Boolean = clickActivates().getOrElse(finger || !Platform.pointerSupport())

  // a press on words that can be selected: the rows in the order they read, for the selection a drag makes
  event.pointer.down.attach(e => if (e.button == PointerEvent.PrimaryButton && Pointer.onText) inReadingOrder())
  // and before that, as a resting pointer moves over them: put in order on the press, the row pressed was out of the page
  // for a moment, and the browser made no click of the press (a click on a chat's message, once it had scrolled, did
  // not put the cursor there). By any mouse, not only where one is the main pointer: a touch laptop's, a headless
  // browser's; not a finger, whose moves are a scroll, and whose tap is judged where it went down
  if (Platform.selectsText())
    event.pointer.move.attach(e => if (shuffled && !e.finger) inReadingOrder())
  // (not the click a drag ends in)
  event.click.attach(e => if (!DragDrop.justEnded && !caught) clickAt(e.x, e.y, e.modifiers, e.finger))
  // the secondary button: the row under it chosen (unless it already is) where it is, which is in view, and its menu
  // asked for; the page's own menu shows unless something answered (Pointer.menuAnswered)
  event.pointer.menu.attach { e =>
    val down = e.y - absoluteTop + offset()
    if (down >= 0.0 && down < total && !blank) {
      val i = indexAt(down)
      if (!selection.contains(keyAt(i))) selection.only(keyAt(i))
      current @= Some(keyAt(i))
      val dialogs = everyui.component.Modal.opened
      menuAt(i, e.x, e.y)
      // a finger held where that put no menu up shows the row's tip instead (any hold, on a phone)
      val tip = holdTips().getOrElse(e.finger || (Platform.touchSupport() && !Platform.pointerSupport()))
      if (tip && everyui.component.Modal.opened == dialogs && !Pointer.menuAnswered) tips.heldOn(i)
    }
  }
  cursor @= "default"
  event.pointer.wheel.attach { e =>
    stopFling()
    scrollBy(e.y)
  }
  // a finger drags the rows along, one for one, each move from the last (so rows put in above while it drags, an
  // earlier page of a chat, do not move what is under it), and a flick carries on once it lifts, slowing as a phone's
  // own lists do. Whether it is a finger is each press's own (`PointerEvent.finger`), not the device's: a touch laptop,
  // a tablet with a trackpad and a browser's phone emulation have both, and a mouse there drags as a mouse does. Up and
  // down are the list's, never the page's: a page given them took the finger for its own pan a few pixels in, and the
  // list heard no more of it.
  node.setTouchScrolls(true)
  event.pointer.down.attach { e =>
    // a finger put down on rows still gliding stops them, and is no tap on the row under it; any press stops them
    caught = e.finger && flick.gliding
    stopFling()
    // (a row that can be dragged is held a moment first, and scrolls only once the finger moves: see below)
    if (e.finger && e.button == PointerEvent.PrimaryButton && !dragsRow(e)) fingerScroll(e.x, e.y, e.x, e.y)
  }

  // the rows following a finger from `from`, and gliding on from how fast it went as it lifts; the finger went down at
  // `pressX`, `pressY`, and once it has gone further from there than a tap may it is a scroll, whose lift taps no row
  private def fingerScroll(fromX: Double, from: Double, pressX: Double, pressY: Double): Unit = {
    var at = from
    var across = fromX
    flick.down(at)
    Pointers.track({ m =>
      scrollBy(at - m.y)
      if (ScrollPress.beyond(pressX, pressY, m.x, m.y)) ScrollPress.scrolled()
      at = m.y
      if (m.x != across) dragAcross @= across - m.x
      across = m.x
      flick.at(at)
    }, _ => flick.lift())
  }
  thumb.event.pointer.down.attach(e => if (e.button == PointerEvent.PrimaryButton) {
    e.consume()
    val from = e.y
    val was = offset()
    val room = math.max(1.0, size.height() - thumb.size.height())
    Pointers.track(m => scrollTop(was + (m.y - from) * furthest / room), _ => ())
  })
  thumb.event.click.attach(_.consume())

  // ---- dragging and dropping ----

  /** Whether rows can be dragged out of this list with the pointer (never where a remote drives the app). */
  val draggable: Var[Boolean] = own(Var(false))
  /** Whether rows may land where a drop says: the app's rule, for each place the pointer passes. None may by default. */
  val accepts: Var[Drop => Boolean] = own(Var((_: Drop) => false))
  /** Rows dropped here, where `accepts` let them. */
  val dropped: Channel[Drop] = Channel[Drop]
  /** What a drag out of this list says it came from (`Drop.from`): a tree built on a list names itself. */
  var owner: Component = this
  /** Whether a row takes rows dropped into it (a folder), rather than only before or after it. */
  var holds: T => Boolean = _ => false
  /** How the rows being dragged are named beside the pointer. */
  val dragLabel: Var[Vector[T] => String] = own(Var((rows: Vector[T]) =>
    rows.headOption.map(labelOf).getOrElse("") + (if (rows.size > 1) "  +" + (rows.size - 1).toString else "")))
  /** Where a drop over this list would land now, if it may: the row's key and the place against it. */
  val dropHover: Var[Option[Drop]] = own(Var(None))

  private val dropLine = new Container {
    size.height @= 2.0
    backgroundColor := Theme.accent()
    visible @= false
  }
  private var pressedAt: Option[(Int, Double, Double)] = None

  // whether a press at `e` may drag a row out (not from words that can be selected, where a drag selects them)
  private def dragsRow(e: PointerEvent): Boolean = {
    val down = e.y - absoluteTop + offset()
    e.button == PointerEvent.PrimaryButton && draggable() && !Platform.remoteNavigation() && editing().isEmpty &&
      !Pointer.onText && down >= 0.0 && down < total
  }

  event.pointer.down.attach { e =>
    if (dragsRow(e)) {
      pressedAt = Some((indexAt(e.y - absoluteTop + offset()), e.x, e.y))
      val pressed = Platform.ticks()
      Pointers.track(m => pressedAt.foreach { case (i, x, y) =>
        if (DragDrop.dragging().nonEmpty) DragDrop.move(m.x, m.y)
        else if (math.abs(m.x - x) + math.abs(m.y - y) > VirtualList.dragStart) {
          // a finger drags a row only once held on it a moment, as a phone's lists have it; moved at once, it scrolls
          if (!e.finger || Platform.ticks() - pressed >= VirtualList.holdSeconds * 1000.0) startDrag(i, m.x, m.y)
          else {
            pressedAt = None
            scrollBy(y - m.y)
            fingerScroll(m.x, m.y, x, y)
          }
        }
      }, u => {
        pressedAt = None
        DragDrop.end(u.x, u.y)
      })
    }
  }

  private def startDrag(index: Int, x: Double, y: Double): Unit = if (index < count) {
    val key = keyAt(index)
    if (!selection.contains(key)) moveTo(index)
    val rows = if (selection.contains(key)) selectedItems else itemAt(index).toVector
    if (rows.nonEmpty) DragDrop.begin(Dragged(owner, rows.map(keyOf).toList), dragLabel()(rows), x, y)
  }

  /** Where rows dragged to `x`, `y` would land, if `accepts` lets them. */
  private def dropFor(x: Double, y: Double, rows: Dragged): Option[Drop] = {
    val down = y - absoluteTop + offset()
    val drop = if (count == 0) Drop(rows.from, rows.keys, None, DropPosition.After)
    else if (down >= total) Drop(rows.from, rows.keys, Some(keyAt(count - 1)), DropPosition.After)
    else {
      val i = indexAt(down)
      val along = (down - top(i)) / math.max(1.0, heightAt(i))
      val place = if (itemAt(i).exists(holds)) {
        if (along < 0.25) DropPosition.Before else if (along > 0.75) DropPosition.After else DropPosition.Into
      } else if (along < 0.5) DropPosition.Before else DropPosition.After
      Drop(rows.from, rows.keys, Some(keyAt(i)), place)
    }
    Some(drop).filter(d => accepts()(d))
  }

  private[list] def hoverDrop(x: Double, y: Double, rows: Dragged): Unit = {
    val drop = dropFor(x, y, rows)
    if (dropHover() != drop) dropHover @= drop
    // near an end, the rows scroll under the pointer
    val edge = math.min(VirtualList.scrollEdge, size.height() / 4.0)
    val from = y - absoluteTop
    scrolling = if (from < edge) -(edge - from) / 2.0 else if (from > size.height() - edge) (from - size.height() + edge) / 2.0 else 0.0
    if (scrolling != 0.0 && !scrollRunning) {
      scrollRunning = true
      Platform.after(VirtualList.scrollTick, () => scrollOn())
    }
  }

  private var scrolling = 0.0
  private var scrollRunning = false

  private def scrollOn(): Unit = if (scrolling != 0.0 && DragDrop.hovering(this)) {
    scrollBy(scrolling)
    DragDrop.again()
    Platform.after(VirtualList.scrollTick, () => scrollOn())
  } else scrollRunning = false

  private[list] def leaveDrop(): Unit = {
    scrolling = 0.0
    if (dropHover().nonEmpty) dropHover @= None
  }

  private[list] def dropAt(x: Double, y: Double, rows: Dragged): Unit = {
    val drop = dropFor(x, y, rows)
    leaveDrop()
    drop.foreach(d => dropped @= d)
  }

  /** The chosen rows (else the one under the cursor) held to be put somewhere with `paste`: the keyboard's drag. */
  def cut(): Unit = {
    val keys = if (selection.keys().nonEmpty) selectedItems.map(keyOf).toList else current().toList
    DragDrop.held @= (if (keys.isEmpty) None else Some(Dragged(owner, keys)))
  }

  /** The rows `cut` (here or in another list) put against the row under the cursor: into it where it holds rows, else
    * after it, or where `position` says; if `accepts` lets them. Whether they went. */
  def paste(position: Option[DropPosition] = None): Boolean = DragDrop.held().exists { rows =>
    val target = current().filter(k => indexOf(k) >= 0)
    val place = position.getOrElse(target.flatMap(k => itemAt(indexOf(k))) match {
      case Some(t) if holds(t) => DropPosition.Into
      case _ => DropPosition.After
    })
    val drop = Drop(rows.from, rows.keys, target, place)
    val may = accepts()(drop)
    if (may) {
      DragDrop.held @= None
      dropped @= drop
    }
    may
  }

  private def placeDropLine(width: Double): Unit = dropHover() match {
    case Some(d) if d.position != DropPosition.Into =>
      val i = d.target.map(indexOf).getOrElse(-1)
      val y = if (i < 0) 0.0 else if (d.position == DropPosition.Before) top(i) else top(i) + heightAt(i)
      val start = if (i < 0) ListRow.inset else placed.get(i).map(_.editStart).getOrElse(ListRow.inset)
      dropLine.position.left @= ListRow.along(start, math.max(0.0, width - start - 4.0), width)
      dropLine.size.width @= math.max(0.0, width - start - 4.0)
      dropLine.position.top @= y - offset() - 1.0
      dropLine.visible @= true
    case _ => dropLine.visible @= false
  }

  // ---- the rows ----

  /** Every row made so far, those out of use (hidden) and those standing in for rows not come among them. */
  def rows: Vector[ListRow[T]] = children().collect { case r: ListRow[T] @unchecked => r }

  /** The row showing `key` made again from its item, now: for what it shows that comes from outside the item (a
    * status the app keeps elsewhere). A change to the item itself (a new value in `items`) redraws its row anyway. */
  def redecorate(key: String): Unit = placed.values.filter(_.item().exists(t => keyOf(t) == key)).foreach(redraw)

  /** Every row showing made again from its item (see `redecorate`). */
  def redecorateAll(): Unit = placed.values.foreach(redraw)

  private def redraw(row: ListRow[T]): Unit = {
    val was = row.item()
    row.item @= None
    row.item @= was
  }

  /** The row showing `index`, if it is made. */
  def rowAt(index: Int): Option[ListRow[T]] = placed.get(index)

  /** Every word in the rows made selected (`Component.selectable`), in the order they read, where the platform selects
    * text: those in view and the few made either side. */
  def selectText(): Unit = {
    inReadingOrder()
    node.selectText()
  }

  /** The rows put in the order they read among the list's nodes, where scrolling (a row leaving one end handed the item
    * arriving at the other) shuffled them: a platform selects text in the nodes' order, so a drag from one row to the
    * next took in the rows made out of sight as well. Only the rows' places change; nothing else moves. */
  private[everyui] def inReadingOrder(): Unit = {
    shuffled = false
    val all = children()
    val made = rows
    val shown = placed.toVector.sortBy(_._1).map(_._2)
    val order = shown ++ made.filterNot(r => shown.exists(_ eq r))
    if (!made.indices.forall(i => made(i) eq order(i))) {
      var out = Vector.empty[Component]
      var next = 0
      var i = 0
      while (i < all.size) {
        all(i) match {
          case _: ListRow[T] @unchecked =>
            out = out :+ order(next)
            next += 1
          case c => out = out :+ c
        }
        i += 1
      }
      children @= out
    }
  }

  private def take(stand: Boolean): ListRow[T] = if (stand) spareStands match {
    case row :: rest =>
      spareStands = rest
      row
    case Nil => new PlaceholderRow[T](placeholder().map(make => make()).getOrElse(new Container))
  } else spare match {
    case row :: rest =>
      spare = rest
      row
    case Nil =>
      val row = makeRow()
      if (ownHeights) row.size.height.attach(_ => note(row))
      row
  }

  private def putAway(row: ListRow[T]): Unit = {
    row.visible @= false
    row.item @= None
    if (row.standsIn) spareStands = row :: spareStands else spare = row :: spare
  }

  // the rows standing in let go, for another `placeholder`
  private def dropStands(): Unit = {
    val stood = placed.values.filter(_.standsIn).toList ++ spareStands
    placed = placed.filter { case (_, r) => !r.standsIn }
    spareStands = Nil
    if (stood.nonEmpty) {
      children @= children().filterNot(c => stood.exists(_ eq c))
      stood.foreach(_.dispose())
    }
  }

  // a row drawn at a height of its own: the rows after it move (the first that showed is noted first, as it was, for
  // `later` to keep it there: a row above it drawn taller or shorter must not move it)
  // `later` too, once the sizes asked for are all read (MeasuredFeature.settled): until then a row made for an item is a
  // step on the way, a message as tall as its padding with its words still to be read, and the list made more rows to
  // fill the room it seemed to leave
  private def note(row: ListRow[T]): Unit = if (!everyui.component.feature.MeasuredFeature.settled) later()
    else if (record(row)) later()

  // the row's height kept, where it has one and it changed
  private def record(row: ListRow[T]): Boolean = {
    val h = row.size.height()
    h > 0.0 && row.item().exists { t =>
      val key = keyOf(t)
      !heights.get(key).contains(h) && {
        if (held.isEmpty) held = firstShowing()
        heights = heights.updated(key, h)
        topsStale = true
        true
      }
    }
  }

  /** The rows placed again: those that can be seen, handed their items, the rest put aside. */
  def refresh(): Unit = if (laying) again = true
    else if (!node.onPage) {
      // out of the page (a view built before it is shown, on the web), the rows wait for the end of the turn, by which
      // an app has put it there: made in the page, their sizes are read in the turn's one batch, where out of it each
      // read laid the page out again (a chat's transcript of long answers took seconds). Still out of it then, they are
      // made where they are.
      if (!offPage) {
        offPage = true
        Platform.afterTurn(() => {
          offPage = false
          lay()
        })
      }
    }
    else lay()
  private var offPage = false

  private def lay(): Unit = if (laying) again = true else {
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
    val searching = source.exists(_.searching())
    blank = searching && loadingView().nonEmpty
    // fewer beyond on a slower device (`everyui.perf.Quality`)
    val beyond = everyui.perf.Quality.overscan(overscan())
    val shownFirst = if (count == 0) 0 else indexAt(offset())
    val shownLast = if (count == 0) -1 else indexAt(offset() + height)
    val first = math.max(0, shownFirst - beyond)
    val near = if (count == 0) -1 else math.min(count - 1, shownLast + beyond)
    // where the rows are as tall as each draws and the sizes are read in a batch (a chat's messages, on the web), those
    // beyond the view not made yet are made after the next frame is drawn, so the rows that show are drawn first
    val later = ownHeights && Platform.measuresInBatches && !aheadDue
    // (the rows the source is to fetch are asked for even while the loading view hides them)
    val last = if (blank) -1 else near
    val leaving = placed.filter { case (i, _) => i < first || i > last }
    if (leaving.nonEmpty) {
      shuffled = true
      leaving.values.foreach(putAway)
      placed = placed.filter { case (i, _) => i >= first && i <= last }
    }
    var made: List[Component] = Nil
    val chosen = selection.keys()
    val everyRow = selection.all()
    val cursor = current()
    val lit = focused()
    val shown = focusShown()
    source.foreach(s => if (near >= first) s.showing(first, near))
    val stands = source.nonEmpty && placeholder().nonEmpty
    // a row not come stands in until it does, and the row then takes its place (and back, as rows are let go)
    var i = first
    var ahead = false
    while (i <= last) {
      val stand = stands && itemAt(i).isEmpty
      placed.get(i) match {
        case Some(r) if r.standsIn == stand => ()
        case None if later && (i < shownFirst || i > shownLast) => ahead = true
        case other =>
          other.foreach(putAway)
          shuffled = true
          val r = take(stand)
          if (r.parent().isEmpty) made = r :: made
          placed = placed.updated(i, r)
      }
      i += 1
    }
    // the rows made join the list before they are handed their items, and are as wide as it and know their place
    // first: what they make for an item is then made in the page, as wide as it will be drawn (where the web reads its
    // sizes in one batch), rather than made and measured out of it at no width and made again
    if (made.nonEmpty) children ++= made.reverse
    if (ahead) makeAhead()
    // (what a row makes for its item goes into the page with it: its sizes are read there, with the turn's batch)
    everyui.component.feature.MeasuredFeature.withBatch {
      var at = first
      while (at <= last) {
        placed.get(at).foreach { row =>
          val item = itemAt(at)
          val key = keyAt(at)
          row.size.width @= width
          row.index @= at
          row.item @= item
          if (row.tip().nonEmpty) tips.watch(row)
          if (ownHeights) note(row)
          row.selected @= everyRow != chosen.contains(key)
          row.current @= cursor.contains(key)
          row.active @= lit
          row.cursorShown @= shown
          row.inUse @= item.exists(inUse())
          row.editing @= editing().contains(key)
          row.drop @= dropHover().filter(_.target.contains(key)).map(_.position)
          if (sizedSource) row.size.height @= heightAt(at)
          else if (!ownHeights) row.size.height @= rowHeight
          row.position.top @= top(at) - offset()
          row.visible @= true
        }
        at += 1
      }
    }
    // the row under the cursor told to a screen reader as it moves, while the list has the focus
    if (!lit) toldRow = ""
    else cursor.foreach(k => if (k != toldRow) placed.values.find(_.current()).foreach { row =>
      toldRow = k
      node.setActiveRow(row.node, () => everyui.access.Access.nameOf(row))
    })
    placeEditor(width, height)
    placeDropLine(width)
    // the drop line and the thumb last, over the rows
    if (children().lastOption.forall(c => !(c eq thumb)))
      children @= children().filterNot(c => (c eq thumb) || (c eq dropLine)) :+ dropLine :+ thumb
    loadingView().foreach(_.visible @= blank)
    emptyView().foreach(_.visible @= count == 0 && !searching)
    val all = total
    thumb.visible @= !blank && all > height + 0.5
    val length = math.max(24.0, height * height / math.max(1.0, all))
    thumb.size.height @= length
    thumb.position.left @= (if (Direction.rtl()) 2.0 else width - 8.0)
    thumb.position.top @= (if (furthest <= 0.0) 0.0 else offset() / furthest * (height - length))
    node.setScrolled(offset(), furthest)
  }

  // the rows beyond the view still to be made (see `place`): after the next frame is drawn, and while they are made
  private var aheadWaiting = false
  private var aheadDue = false
  private def makeAhead(): Unit = if (!aheadWaiting) {
    aheadWaiting = true
    Platform.afterFirstPaint(() => {
      aheadWaiting = false
      if (!takenApart) {
        aheadDue = true
        try refresh() finally aheadDue = false
      }
    })
  }

  private var waiting = false
  // the place kept once the rows drawn at heights of their own are all in, before anything is drawn where the platform
  // can say (Platform.afterTurn): a page drew a frame with the rows moved by heights the place had not yet been kept
  // from, an earlier page of a chat coming in, before the place was kept in the next
  private def later(): Unit = if (!waiting) {
    waiting = true
    Platform.afterTurn(() => settle())
  }
  private def settle(): Unit = if (waiting) {
    waiting = false
    if (!everyui.component.feature.MeasuredFeature.settled && unsettled < VirtualList.settleTries) {
      // the turn's sizes are still to be read: after them
      unsettled += 1
      later()
    } else {
      unsettled = 0
      if (ownHeights) placed.values.foreach(r => if (!r.standsIn) record(r))
      keepingPlace(())
      refresh()
    }
  }
  private var unsettled = 0

  // the first row that shows and how far above the top it starts, unless the rows are at their very top
  private def firstShowing(): Option[(String, Double)] = if (count == 0 || offset() <= 0.0) None else {
    val i = indexAt(offset())
    Some((keyAt(i), top(i) - offset()))
  }
  private var held: Option[(String, Double)] = None
  // the row `scrollTo` brought to a place of the view, and where (its `align`), while the rows are not scrolled since
  private var aimed: Option[(String, Double)] = None

  /** `change` made with the first row that shows kept where it is, when it is still in the list: where it was before
    * rows were drawn at heights of their own, if they were since; or the row `scrollTo` brought into view, where it
    * was brought. */
  private def keepingPlace(change: => Unit): Unit = {
    val anchor = held.orElse(firstShowing())
    held = None
    change
    if (ownHeights) retop()
    // set once, where it ends up: whatever follows the scroll hears one move, not a step on the way
    val kept = if (followsEnd()) furthest else aimed.flatMap { case (key, align) => Some(indexOf(key)).filter(_ >= 0)
      .map(i => top(i) - (size.height() - heightAt(i)) * align) }.orElse(anchor.flatMap { case (key, dy) =>
      Some(indexOf(key)).filter(_ >= 0).map(i => top(i) - dy) }).getOrElse(offset())
    offset @= math.max(0.0, math.min(furthest, kept))
  }


  // ---- renaming in place ----

  /** The key of the row being named, if one is. */
  val editing: Var[Option[String]] = own(Var(None))
  /** What the app does with a name typed in a row: `None` when it has taken it (and changed its items to match), or a
    * message saying why not, shown under the field, which stays open. By default every name is taken, and nothing
    * changes. */
  val commit: Var[ListEdit[T] => everyui.async.Task[Option[String]]] =
    own(Var((_: ListEdit[T]) => everyui.async.Task.pure(None: Option[String])))
  /** Whether F2 renames the row under the cursor. */
  val renames: Var[Boolean] = own(Var(false))
  private var editor: Option[RowEditor] = None
  private var editorAt = -1

  /** Row `key`'s name typed in place: a field over its label, holding it, all chosen. Enter commits, Escape cancels,
    * leaving the field commits; the app's `commit` takes the name or says why not. Where the platform has no field of
    * its own (a Roku, an Apple TV), its keyboard asks for the name instead, and a refusal is toasted. */
  def edit(key: String, text: Option[String] = None): Unit = {
    val i = indexOf(key)
    if (i >= 0) {
      dropField()
      val initial = text.getOrElse(if (standIn.exists(p => keyOf(p._2) == key)) "" else itemAt(i).map(labelOf).getOrElse(""))
      current @= Some(key)
      reveal(i)
      if (!Platform.nativeInput()) Platform.editText("", initial, false, v => submit(None, v, false))
      else {
        val field = new RowEditor(initial)
        field.submitted.attach(v => submit(Some(field), v, false))
        field.left.attach(v => submit(Some(field), v, true))
        field.cancelled.attach(_ => if (editor.exists(_ eq field)) closeEditor(true))
        editor = Some(field)
        editing @= Some(key)
        children += field
        refresh()
        field.start()
      }
    }
  }

  /** A new row at `index`, `stand` standing in for the item until its name is typed (see `edit`): the app's `commit`
    * hears it with `created`, and adds the item itself; cancelled, the row goes. */
  def insertEditing(index: Int, stand: T): Unit = {
    closeEditor(false)
    standIn = Some((index, stand))
    reindex()
    edit(keyOf(stand), Some(""))
  }

  /** The name being typed let go, unchanged. */
  def cancelEdit(): Unit = closeEditor(focused() || editor.exists(_.focused()))

  private var committing = false

  private def submit(field: Option[RowEditor], text: String, left: Boolean): Unit =
    if (!committing && field.forall(f => editor.exists(_ eq f))) {
      val key = if (field.isEmpty) current() else editing()
      key.map(indexOf).filter(_ >= 0).foreach(i => itemAt(i).foreach { t =>
        val created = standIn.exists(p => keyOf(p._2) == keyAt(i))
        committing = true
        commit()(ListEdit(t, keyAt(i), i, text, created)).map { verdict =>
          committing = false
          if (field.forall(f => editor.exists(_ eq f))) verdict match {
            case None => closeEditor(!left)
            case Some(message) => field match {
              case Some(f) =>
                f.error @= message
                if (!left) f.start()
              case None => everyui.kit.Toast.show(message)
            }
          }
        }.start()
      })
    }

  // the field taken away, and the row it was over shown again
  private def dropField(): Unit = {
    val field = editor
    editor = None
    field.foreach { f =>
      f.stop()
      children -= f
      f.dispose()
    }
    if (editing().nonEmpty) editing @= None
  }

  private def closeEditor(refocus: Boolean): Unit = {
    dropField()
    if (standIn.nonEmpty) {
      standIn = None
      reindex()
    }
    if (refocus) focus()
  }

  private def placeEditor(width: Double, height: Double): Unit = editor.foreach { f =>
    val i = editing().map(indexOf).getOrElse(-1)
    if (i < 0) f.visible @= false
    else {
      val start = placed.get(i).map(_.editStart).getOrElse(ListRow.inset) - 6.0
      val y = top(i) - offset()
      f.visible @= y + heightAt(i) > 0.0 && y < height
      f.position.left @= ListRow.along(start, width - start - 4.0, width)
      f.position.top @= y
      f.size.width @= width - start - 4.0
      f.size.height @= heightAt(i)
      f.above @= y + heightAt(i) * 2.5 > height
    }
  }

  // ---- editing (see `edit`) ----
  // a row put in for a new item while its name is being typed: where, and what stands for it
  private[list] var standIn: Option[(Int, T)] = None

  /** The items again, as they are now, with the cursor, the selection and the scroll kept by key. */
  def reindex(): Unit = if (source.nonEmpty) reread() else {
    val was = currentAt
    val before = shown
    keepingPlace {
      val base = items()
      shown = standIn match {
        case Some((at, t)) => base.patch(math.max(0, math.min(at, base.length)), Vector(t), 0)
        case None => base
      }
      // made in one go: a Roku's map copies itself for each key added, and 5000 rows added a key at a time took minutes
      indexes = (0 until shown.length).toVector.map(i => keyOf(shown(i)) -> i).toMap
      topsStale = ownHeights
      guesses = new Array[Double](0)
      // items put in before the rows made, all of them moved on by as many (an earlier page of a chat): each row
      // keeps its item at its new place, where each was handed the item now at its own and made again (a message's
      // words being selected by a drag were made again under the pointer, and the selection went). Items moved any
      // other way are handed to the rows at their places, as before.
      if (placed.nonEmpty) {
        val shifts = placed.toList.map { case (i, r) =>
          r.item().map(keyOf).flatMap(indexes.get).map(_ - i).getOrElse(0)
        }
        val by = shifts.head
        if (by != 0 && shifts.forall(_ == by)) placed = placed.map { case (i, r) => (i + by, r) }
      }
    }
    selection.keep(k => indexes.contains(k))
    current() match {
      // the row under the cursor gone, the cursor goes to what took its place: the first after it still here, else the
      // last before it
      case Some(key) if !indexes.contains(key) =>
        // (one new in its place, a row renamed, is what took it)
        val had = if (was >= 0 && was < count) before.map(keyOf).toSet else Set.empty[String]
        val renamed = if (was >= 0 && was < count && !had.contains(keyAt(was))) Some(keyAt(was)) else None
        val after = before.drop(math.max(0, was)).find(t => indexes.contains(keyOf(t))).map(keyOf)
        val earlier = before.take(math.max(0, was)).reverse.find(t => indexes.contains(keyOf(t))).map(keyOf)
        current @= renamed.orElse(after).orElse(earlier).orElse(if (count == 0) None else Some(keyAt(math.max(0, math.min(was, count - 1)))))
        if (!selection.multiple()) current().foreach(k => selection.only(k))
      case _ => ()
    }
    currentAt = current().flatMap(indexes.get).getOrElse(-1)
    refresh()
  }

  // a source's rows again: the cursor and the chosen keys that stood for places moved onto the rows come there, and the
  // cursor on a row no longer known (let go by the source) kept at its place
  private def reread(): Unit = {
    val was = currentAt
    keepingPlace(())
    val chosen = selection.keys()
    if (chosen.nonEmpty) {
      val moved = chosen.map { k =>
        val i = indexOf(k)
        if (i >= 0) keyAt(i) else k
      }
      if (moved != chosen) selection.keys @= moved
    }
    current().foreach { key =>
      val i = indexOf(key)
      if (i >= 0 && keyAt(i) != key) current @= Some(keyAt(i))
      else if (i < 0 && was >= 0 && count > 0) current @= Some(keyAt(math.min(was, count - 1)))
    }
    currentAt = current().map(indexOf).getOrElse(-1)
    refresh()
  }

  editing.attach(_ => refresh())
  inUse.attach(_ => refresh())
  current.attach { c =>
    currentAt = c.map(indexOf).getOrElse(-1)
    refresh()
    tips.cursor(keying)
  }
  // arriving, the focus lands on the row it left, else the first chosen, else the first
  focused.attach { on =>
    if (on && current().isEmpty && count > 0) {
      current @= selection.keys().headOption.filter(k => indexOf(k) >= 0).orElse(Some(keyAt(0)))
      if (!selection.multiple()) current().foreach(k => selection.only(k))
    }
    refresh()
    // (arriving by the keys, not the pointer)
    tips.cursor(!Pointer.recent)
  }
  // the cursor drawn as the keys come to move the focus, and no longer once a pointer is put down (Focus.visible)
  if (!Platform.remoteNavigation()) focusShown.attach(_ => refresh())
  items.attach(_ => reindex())
  source.foreach(s => follow(s.changed, (_: Unit) => reindex()))
  follow(Direction.rtl, (_: Boolean) => refresh())
  selection.keys.attach(_ => refresh())
  selection.all.attach(_ => refresh())
  offset.attach { _ =>
    refresh()
    tips.scrolled()
  }
  onDispose(() => tips.hide())
  size.width.attach { _ =>
    // (an estimate is for a width: the rows above are guessed again with the first that shows kept where it is, or the
    // end, at once, before the rows are placed for the width)
    if (ownHeights && guesses.length > 0) keepingPlace {
      guesses = new Array[Double](0)
      topsStale = true
    }
    clip()
  }
  size.height.attach { _ =>
    clip()
    offset @= (if (followsEnd()) furthest else math.max(0.0, math.min(furthest, offset())))
  }
  followsEnd.attach(on => if (on) offset @= furthest)
  private def clip(): Unit = {
    clipTo(size.width(), size.height())
    refresh()
  }

  children ++= List(dropLine, thumb)
  // the loading and empty views, in the middle of the list's box, under the drop line and the thumb
  private var views: List[Component] = Nil
  private def showViews(): Unit = {
    val now = loadingView().toList ++ emptyView().toList
    now.foreach { v =>
      v.visible @= false
      v.position.left := math.max(0.0, (size.width() - v.size.width()) / 2.0)
      v.position.top := math.max(0.0, (size.height() - v.size.height()) / 2.0)
    }
    children @= children().filterNot(c => views.exists(_ eq c) || (c eq dropLine) || (c eq thumb)) ++ now ++
      List(dropLine, thumb)
    views = now
    refresh()
  }
  loadingView.attach(_ => showViews())
  emptyView.attach(_ => showViews())
  placeholder.attach { _ =>
    dropStands()
    refresh()
  }
  dropHover.attach(_ => refresh())
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
  /** How far the pointer moves, held down on a row, before it is dragging it. */
  val dragStart: Double = 6.0
  /** How near an end a drag scrolls the rows, and how often, in seconds. */
  val scrollEdge: Double = 28.0
  val scrollTick: Double = 0.03
  // how many turns a list waits for the sizes asked for to be read before it takes its rows' heights as they are
  private[list] val settleTries: Int = 8

  /** A list of text rows: each item's label, dressed as `decorate` says. */
  def text[T](items: Var[Vector[T]], key: T => String, label: T => String,
              decorate: T => Decoration = (_: T) => Decoration.plain,
              rowHeight: Double = VirtualList.rowHeight): VirtualList[T] =
    new VirtualList[T](items, key, label, rowHeight)(() => new TextRow[T](label, decorate))

  /** A row still to come, as `placeholder` draws it by default: a faint bar where its words will be. */
  def blank(): Component = new Container {
    private val bar = new Container {
      size.height @= 10.0
      radius @= 5.0
      backgroundColor := Theme.muted().withAlpha(0.18)
    }
    bar.size.width := math.max(0.0, math.min(240.0, size.width() * 0.4))
    bar.position.left := ListRow.along(ListRow.inset, bar.size.width(), size.width())
    bar.position.top := (size.height() - 10.0) / 2.0
    children += bar
  }

  /** A spinner in the theme's accent, for `loadingView`. */
  def loading(): Component = new everyui.kit.Spinner(28.0, () => Theme.accent())

  /** `words` (by default "No results" in the language in use, `Words("noResults")`, which an app may reword with
    * `Words.add`), muted, for `emptyView`. */
  def empty(words: => String = everyui.i18n.Words("noResults")): Component =
    everyui.component.TextView.bound(words, 14.0, Theme.muted())
}
