package everyui.split

import everyui.component.*
import everyui.event.{Key, PointerEvent}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, ui}
import reactify.*

/** Two parts side by side (or, `vertical`, one over the other) with a handle between them that a pointer drags to
  * share the room out differently. Give the split its size; each part is given its side's.
  *
  * `ratio` is the first side's share of the room, 0 to 1: pass in a Var the app keeps (and saves) to remember it.
  * Neither side is made smaller than its minimum (`minFirst`, `minSecond`) while there is room for both. The
  * `collapsible` side folds away (`collapsed`, or `toggle()`) when the handle is double-clicked, or dragged most of the
  * way past its minimum, and comes back at the ratio it had. `grow()` and `shrink()` move the handle by `step`, for an
  * app's commands; a focused handle (`handle.focusable @= true`) moves with the arrows along it and folds with Enter.
  * `startCollapsed` makes it with that side folded from the first frame: a folded sidebar restored from a saved
  * layout never shows open first. A split can be a side of another. Split across, the first side is at the start of
  * the reading direction: on the right in a right-to-left language, where dragging and the arrows are mirrored with it.
  *
  * The arrows cross from one side to the other (Right from the first, Left from the second; Down and Up split
  * vertically), landing where the focus last was on that side, and not onto a folded side.
  *
  * {{{
  *   val width = Var(0.25)   // saved as the app likes
  *   val split = new SplitPane(tree, editor, ratio = width, collapsible = Some(SplitSide.First))
  *   split.size.width := ui.size.width()
  *   split.size.height := ui.size.height()
  *   Keymap.global.add("sidebar.toggle", "Toggle sidebar", Chord("B", primary = true)) { split.toggle() }
  * }}} */
class SplitPane(val first: Component, val second: Component, val vertical: Boolean = false,
                val ratio: Var[Double] = Var(0.5), val collapsible: Option[SplitSide] = None,
                startCollapsed: Boolean = false) extends Container {
  val minFirst: Var[Double] = own(Var(SplitPane.minimum))
  val minSecond: Var[Double] = own(Var(SplitPane.minimum))
  /** Whether the collapsible side is folded away. */
  val collapsed: Var[Boolean] = own(Var(startCollapsed && collapsible.nonEmpty))
  /** How far `grow` and `shrink` move the handle, in pixels. */
  val step: Var[Double] = own(Var(24.0))
  /** How thick the handle is, which is the room a pointer has to take hold of it. */
  val thickness: Var[Double] = own(Var(SplitPane.thickness))

  private def along: Double = if (vertical) size.height() else size.width()
  /** The room the two sides share. */
  private def room: Double = math.max(0.0, along - thickness())

  /** `length` for the first side, kept to both minimums, or shared between them by their minimums where there is not
    * room for both. */
  private def kept(length: Double): Double = {
    val most = room - minSecond()
    if (most < minFirst()) room * minFirst() / math.max(1.0, minFirst() + minSecond())
    else math.max(minFirst(), math.min(most, length))
  }

  /** How long the first side is, in pixels. */
  val firstLength: Val[Double] = own(Val(
    if (collapsed() && collapsible.contains(SplitSide.First)) 0.0
    else if (collapsed() && collapsible.contains(SplitSide.Second)) room
    else kept(ratio() * room)))

  private val firstPane: Container = SplitPane.pane(first)
  private val secondPane: Container = SplitPane.pane(second)
  private var dragFrom: Double = 0.0
  private var dragStart: Double = 0.0
  private var moved: Boolean = false
  private var lastClick: Double = -1000.0

  /** What a pointer drags. */
  val handle: Container = new Container {
    // the arrows along it move it, and Enter folds a side
    override def keepsKey(key: Key): Boolean = key == Key.Enter ||
      (if (vertical) key == Key.ArrowUp || key == Key.ArrowDown else key == Key.ArrowLeft || key == Key.ArrowRight)
  }
  // whether a pointer has hold of it
  private val grabbed: Var[Boolean] = own(Var(false))
  private val lit: Val[Boolean] = own(Val(handle.focused() || handle.event.pointer.over() || grabbed()))
  // the rule drawn down the middle of the handle, heavier while it is lit
  private val line: Container = new Container {
    backgroundColor := (if (lit()) Theme.accent() else Theme.border())
  }
  handle.cursor @= (if (vertical) "row-resize" else "col-resize")
  if (vertical) {
    line.size.width := handle.size.width()
    line.size.height := (if (lit()) 2.0 else 1.0)
    line.position.top := (handle.size.height() - line.size.height()) / 2.0
  }
  else {
    line.size.height := handle.size.height()
    line.size.width := (if (lit()) 2.0 else 1.0)
    line.position.left := (handle.size.width() - line.size.width()) / 2.0
  }
  handle.children += line

  // what catches the pointer while it drags, over the whole screen: a browser tells only what is under the pointer
  // that it moved, and the handle is a few pixels across
  private val catcher: Container = new Container {
    size.width := ui.size.width()
    size.height := ui.size.height()
    cursor @= (if (vertical) "row-resize" else "col-resize")
  }

  if (vertical) {
    List(firstPane, secondPane, handle).foreach(_.size.width := size.width())
    firstPane.size.height := firstLength()
    handle.position.top := firstLength()
    handle.size.height := thickness()
    secondPane.position.top := firstLength() + thickness()
    secondPane.size.height := room - firstLength()
  }
  else {
    List(firstPane, secondPane, handle).foreach(_.size.height := size.height())
    // the first side at the start of the reading direction: on the right in a right-to-left language
    firstPane.size.width := firstLength()
    firstPane.position.left := (if (Direction.rtl()) room + thickness() - firstLength() else 0.0)
    handle.position.left := (if (Direction.rtl()) room - firstLength() else firstLength())
    handle.size.width := thickness()
    secondPane.position.left := (if (Direction.rtl()) 0.0 else firstLength() + thickness())
    secondPane.size.width := room - firstLength()
  }
  firstPane.visible := firstLength() > 0.0
  secondPane.visible := room - firstLength() > 0.0
  children ++= List(firstPane, secondPane, handle)

  // the arrows across, onto a side that shows, the way the sides lie in the reading direction; the other way out of a
  // side is left to what the split is in
  private def crossings(rtl: Boolean): Unit =
    if (vertical) {
      firstPane.exitAction(Key.ArrowDown, () => enter(secondPane))
      secondPane.exitAction(Key.ArrowUp, () => enter(firstPane))
    }
    else {
      val (on, back) = if (rtl) (Key.ArrowLeft, Key.ArrowRight) else (Key.ArrowRight, Key.ArrowLeft)
      firstPane.dropExit(back)
      secondPane.dropExit(on)
      firstPane.exitAction(on, () => enter(secondPane))
      secondPane.exitAction(back, () => enter(firstPane))
    }
  crossings(Direction.rtl())
  follow(Direction.rtl, (rtl: Boolean) => crossings(rtl))
  // a side folding away with the focus in it hands the focus across
  collapsed.attach(on => if (on) {
    val gone = if (collapsible.contains(SplitSide.First)) firstPane else secondPane
    if (Focus.focused().exists(c => Focus.within(c, gone))) enter(if (gone eq firstPane) secondPane else firstPane)
  })

  handle.event.pointer.down.attach { e =>
    e.consume()
    dragFrom = at(e)
    dragStart = firstLength()
    grabbed @= true
    moved = false
    ui.children += catcher
  }
  handle.event.pointer.move.attach(drag)
  catcher.event.pointer.move.attach(drag)
  handle.event.pointer.up.attach(e => release())
  catcher.event.pointer.up.attach(e => release())
  handle.event.click.attach { e =>
    e.consume()
    val now = Platform.ticks()
    if (!moved && now - lastClick < SplitPane.doubleClick) {
      toggle()
      lastClick = -1000.0
    }
    else lastClick = now
  }
  handle.event.key.down.attach { k =>
    if (k.key == Key.Enter) toggle()
    else if (k.key == Key.ArrowUp || k.key == (if (mirrored) Key.ArrowRight else Key.ArrowLeft)) shrink()
    else if (k.key == Key.ArrowDown || k.key == (if (mirrored) Key.ArrowLeft else Key.ArrowRight)) grow()
  }

  /** The first side made longer by `step` (a folded side comes back). */
  def grow(): Unit = nudge(step())
  /** The first side made shorter by `step`. */
  def shrink(): Unit = nudge(-step())
  /** The collapsible side folded away, or back. */
  def toggle(): Unit = if (collapsible.nonEmpty) collapsed @= !collapsed()

  /** The handle put `length` pixels along, as a drag puts it: past most of a collapsible side's minimum it folds. */
  def resizeTo(length: Double): Unit = if (room > 0.0) {
    if (collapsible.contains(SplitSide.First) && length < minFirst() / 2.0) collapsed @= true
    else if (collapsible.contains(SplitSide.Second) && room - length < minSecond() / 2.0) collapsed @= true
    else {
      collapsed @= false
      ratio @= kept(length) / room
    }
  }

  private def nudge(delta: Double): Unit =
    if (collapsed()) collapsed @= false else resizeTo(firstLength() + delta)

  // whether the handle moves the other way to the first side growing: across, in a right-to-left language
  private def mirrored: Boolean = !vertical && Direction.rtl()

  private def at(e: PointerEvent): Double = if (vertical) e.y else e.x

  private def drag(e: PointerEvent): Unit = if (grabbed()) {
    e.consume()
    val delta = (at(e) - dragFrom) * (if (mirrored) -1.0 else 1.0)
    if (math.abs(delta) > 2.0) moved = true
    if (moved) resizeTo(dragStart + delta)
  }

  private def release(): Unit = if (grabbed()) {
    grabbed @= false
    ui.children -= catcher
  }

  private def enter(pane: Container): Unit = if (pane.visible()) {
    val all = Focus.candidates(pane)
    pane.remembered.filter(r => all.exists(_ eq r)).orElse(all.headOption).foreach(_.focus())
  }

  override def kind: String = "SplitPane"
}

object SplitPane {
  /** A side's least length, unless the split says. */
  val minimum: Double = 120.0
  /** The handle's thickness, unless the split says. */
  val thickness: Double = 6.0
  /** How close two clicks are, in milliseconds, to be a double-click. */
  val doubleClick: Double = 400.0

  /** A side's box: it clips what it holds, which it sizes to itself. */
  private def pane(part: Component): Container = new Container {
    part.size.width := size.width()
    part.size.height := size.height()
    size.width.attach(_ => node.setClip(size.width(), size.height()))
    size.height.attach(_ => node.setClip(size.width(), size.height()))
    children += part
  }
}
