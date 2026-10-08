package everyui.split

import everyui.component.*
import everyui.event.Key
import everyui.focus.Focus
import everyui.i18n.Direction
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
  * `length` keeps a side at a length in pixels instead: `Some(SplitSide.First -> width)`, `width` a Var the app keeps
  * (and saves), which a drag writes. As the split is made longer or shorter (the window resized), that side keeps its
  * length and the other takes the change, the minimums still holding; `ratio` is not read. Either way neither side is
  * made longer than its most (`maxFirst`, `maxSecond`), room allowing.
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
  *
  *   val sidebar = Var(280.0)   // pixels, kept as the window changes
  *   val pinned = new SplitPane(tree, editor, length = Some(SplitSide.First -> sidebar), collapsible = Some(SplitSide.First))
  *   pinned.maxFirst @= 480.0
  * }}} */
class SplitPane(val first: Component, val second: Component, val vertical: Boolean = false,
                val ratio: Var[Double] = Var(0.5), val collapsible: Option[SplitSide] = None,
                startCollapsed: Boolean = false, val length: Option[(SplitSide, Var[Double])] = None) extends Container {
  val minFirst: Var[Double] = own(Var(SplitPane.minimum))
  val minSecond: Var[Double] = own(Var(SplitPane.minimum))
  /** The most each side is made, room allowing: no limit unless said. */
  val maxFirst: Var[Double] = own(Var(SplitPane.unbounded))
  val maxSecond: Var[Double] = own(Var(SplitPane.unbounded))
  /** Whether the collapsible side is folded away. */
  val collapsed: Var[Boolean] = own(Var(startCollapsed && collapsible.nonEmpty))
  /** How far `grow` and `shrink` move the handle, in pixels. */
  val step: Var[Double] = own(Var(24.0))
  /** How thick the handle is, which is the room a pointer has to take hold of it. */
  val thickness: Var[Double] = own(Var(SplitPane.thickness))

  private def along: Double = if (vertical) size.height() else size.width()
  /** The room the two sides share. */
  private def room: Double = math.max(0.0, along - thickness())

  /** `to` for the first side, kept to both minimums and both mosts, or shared between them by their minimums where there
    * is not room for both. */
  private def kept(to: Double): Double = {
    val most = room - minSecond()
    if (most < minFirst()) room * minFirst() / math.max(1.0, minFirst() + minSecond())
    else math.max(math.max(minFirst(), room - maxSecond()), math.min(math.min(most, maxFirst()), to))
  }

  /** How long the first side is, in pixels. */
  val firstLength: Val[Double] = own(Val(
    if (collapsed() && collapsible.contains(SplitSide.First)) 0.0
    else if (collapsed() && collapsible.contains(SplitSide.Second)) room
    else length match {
      case Some((SplitSide.First, pixels)) => kept(pixels())
      case Some((_, pixels)) => kept(room - pixels())
      case None => kept(ratio() * room)
    }))

  private val firstPane: Container = SplitPane.pane(first)
  private val secondPane: Container = SplitPane.pane(second)
  private var dragStart: Double = 0.0

  /** What a pointer drags: the arrows along it move it, and Enter folds a side, where it takes the focus. */
  val handle: SplitHandle = new SplitHandle(vertical)

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

  handle.pressed.attach(_ => dragStart = firstLength())
  handle.dragged.attach(delta => resizeTo(dragStart + delta))
  handle.doubleClicked.attach(_ => toggle())
  handle.entered.attach(_ => toggle())
  handle.nudged.attach(way => nudge(way * step()))

  /** The first side made longer by `step` (a folded side comes back). */
  def grow(): Unit = nudge(step())
  /** The first side made shorter by `step`. */
  def shrink(): Unit = nudge(-step())
  /** The collapsible side folded away, or back. */
  def toggle(): Unit = if (collapsible.nonEmpty) collapsed @= !collapsed()

  /** The handle put `to` pixels along, as a drag puts it: past most of a collapsible side's minimum it folds. What it
    * leaves is written to `ratio`, or to the side's `length` where it keeps one. */
  def resizeTo(to: Double): Unit = if (room > 0.0) {
    if (collapsible.contains(SplitSide.First) && to < minFirst() / 2.0) collapsed @= true
    else if (collapsible.contains(SplitSide.Second) && room - to < minSecond() / 2.0) collapsed @= true
    else {
      collapsed @= false
      length match {
        case Some((SplitSide.First, pixels)) => pixels @= kept(to)
        case Some((_, pixels)) => pixels @= room - kept(to)
        case None => ratio @= kept(to) / room
      }
    }
  }

  private def nudge(delta: Double): Unit =
    if (collapsed()) collapsed @= false else resizeTo(firstLength() + delta)

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
  /** No most at all (`maxFirst`, `maxSecond`). */
  val unbounded: Double = 1.0e9

  /** A side's box: it clips what it holds, which it sizes to itself. */
  private[split] def pane(part: Component): Container = new Container {
    part.size.width := size.width()
    part.size.height := size.height()
    size.width.attach(_ => node.setClip(size.width(), size.height()))
    size.height.attach(_ => node.setClip(size.width(), size.height()))
    children += part
  }
}
