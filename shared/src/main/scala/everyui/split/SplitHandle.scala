package everyui.split

import everyui.Color
import everyui.component.Container
import everyui.component.types.{SemanticRange, Semantics}
import everyui.event.{Key, PointerEvent}
import everyui.i18n.{Direction, Words}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** What a pointer drags to share room out between two parts (`SplitPane`, `StackedPanes`): a rule down its middle, lit
  * in the accent while the pointer is over it, it is focused or it is held, and the resize cursor. Pressed, it tells
  * `pressed`; dragged, it tells `dragged` how far it has gone from where it was pressed (once past a couple of pixels,
  * mirrored across in a right-to-left language), over the whole screen, since a browser tells only what is under the
  * pointer that it moved; `clicked` hears each click on it, `moved` saying whether the press before it dragged, and
  * `doubleClicked` two that dragged nothing close together. The keys reach it (Tab, in reading order between the parts
  * it divides), except where a remote moves the focus: the arrows along it tell `nudged` the way (1 on, to the reading
  * direction's end or down, -1 back), four times as far with Shift or as Page Up and Page Down; Home and End tell
  * `ended` (-1 as far back as it goes, 1 as far on); and Enter tells `entered`. A screen reader hears it as a divider,
  * where its owner says it stands (`value`). Its owner sizes and places it. */
class SplitHandle(val vertical: Boolean) extends Container {
  /** Whether a pointer has hold of it. */
  val grabbed: Var[Boolean] = own(Var(false))
  /** Whether the rule shows at rest, in the border's colour; without it, it shows only while lit. */
  val ruled: Var[Boolean] = own(Var(true))
  /** Where the rule lies across it, from its top (or its start): in its middle where this is less than 0. */
  val ruleAt: Var[Double] = own(Var(-1.0))
  val lit: Val[Boolean] = own(Val(focusShown() || event.pointer.over() || grabbed()))
  /** A press on it, before any drag. */
  val pressed: Channel[PointerEvent] = Channel[PointerEvent]
  /** How far it has been dragged from where it was pressed, in pixels along the way it moves. */
  val dragged: Channel[Double] = Channel[Double]
  val clicked: Channel[PointerEvent] = Channel[PointerEvent]
  val doubleClicked: Channel[PointerEvent] = Channel[PointerEvent]
  val nudged: Channel[Double] = Channel[Double]
  val entered: Channel[Unit] = Channel[Unit]
  /** Home (-1) or End (1): as far back, or on, as it goes. */
  val ended: Channel[Double] = Channel[Double]
  /** Where it stands, for a screen reader: its owner's to say (a split's first side as a percentage). */
  val value: Var[Option[SemanticRange]] = own(Var(None))
  private var from: Double = 0.0
  private var dragging: Boolean = false
  private var lastClick: Double = -1000.0
  private var tappedAt: Double = -1000.0

  // the rule drawn down the middle, heavier while it is lit
  private val line: Container = new Container {
    backgroundColor := (if (lit()) Theme.accent() else if (ruled()) Theme.border() else Color.Clear)
  }
  cursor @= (if (vertical) "row-resize" else "col-resize")
  touchDrags @= true
  focusable @= !Platform.remoteNavigation()
  // a click on it (to fold a side) leaves the focus in the side it was in
  clickLeavesFocus = true
  semantics := Semantics("separator", label = Words.divider, range = value())
  if (vertical) {
    line.size.width := size.width()
    line.size.height := (if (lit()) 2.0 else 1.0)
    line.position.top := (if (ruleAt() >= 0.0) ruleAt() else size.height() / 2.0) - line.size.height() / 2.0
  }
  else {
    line.size.height := size.height()
    line.size.width := (if (lit()) 2.0 else 1.0)
    line.position.left := (if (ruleAt() >= 0.0) ruleAt() else size.width() / 2.0) - line.size.width() / 2.0
  }
  children += line

  // what catches the pointer while it drags, over the whole screen
  private val catcher: Container = new Container {
    size.width := ui.size.width()
    size.height := ui.size.height()
    cursor @= (if (vertical) "row-resize" else "col-resize")
  }

  // the arrows along it, and Enter, are its own where it takes the focus
  override def keepsKey(key: Key): Boolean = key == Key.Enter || key == Key.PageUp || key == Key.PageDown ||
    (if (vertical) key == Key.ArrowUp || key == Key.ArrowDown else key == Key.ArrowLeft || key == Key.ArrowRight)

  /** Whether the press before the last click dragged it. */
  def moved: Boolean = dragging

  event.pointer.down.attach { e =>
    e.consume()
    from = at(e)
    grabbed @= true
    dragging = false
    ui.children += catcher
    pressed @= e
  }
  event.pointer.move.attach(drag)
  catcher.event.pointer.move.attach(drag)
  event.pointer.up.attach(e => release(Some(e)))
  catcher.event.pointer.up.attach(e => release(Some(e)))
  // a press let go without a drag is a click, told as it lets go: a browser sends its own click to what holds both the
  // handle and the catcher the press put over it, never to the handle; where the platform's own click comes too, just
  // after, it is the same one
  event.click.attach { e =>
    e.consume()
    if (Platform.ticks() - tappedAt > SplitHandle.sameClick) tap(e)
  }
  event.key.down.attach { k =>
    // (across, in a right-to-left language, on is to the left)
    val mirrored = !vertical && Direction.rtl()
    val far = if (k.shift) 4.0 else 1.0
    if (k.key == Key.Enter) entered @= (())
    else if (k.name == "Home") ended @= -1.0
    else if (k.name == "End") ended @= 1.0
    else if (k.key == Key.PageUp) nudged @= -4.0
    else if (k.key == Key.PageDown) nudged @= 4.0
    else if (k.key == Key.ArrowUp || k.key == (if (mirrored) Key.ArrowRight else Key.ArrowLeft)) nudged @= -far
    else if (k.key == Key.ArrowDown || k.key == (if (mirrored) Key.ArrowLeft else Key.ArrowRight)) nudged @= far
  }
  // let go of, should it be taken apart while it is held
  onDispose(() => {
    release(None)
    catcher.dispose()
  })

  private def at(e: PointerEvent): Double = if (vertical) e.y else e.x

  private def drag(e: PointerEvent): Unit = if (grabbed()) {
    e.consume()
    val delta = (at(e) - from) * (if (!vertical && Direction.rtl()) -1.0 else 1.0)
    if (math.abs(delta) > 2.0) dragging = true
    if (dragging) dragged @= delta
  }

  private def release(up: Option[PointerEvent]): Unit = if (grabbed()) {
    grabbed @= false
    ui.children -= catcher
    up.filter(_ => !dragging).foreach { e =>
      tappedAt = Platform.ticks()
      tap(e)
    }
  }

  private def tap(e: PointerEvent): Unit = {
    clicked @= e
    val now = Platform.ticks()
    if (!dragging && now - lastClick < SplitPane.doubleClick) {
      lastClick = -1000.0
      doubleClicked @= e
    }
    else lastClick = now
  }

  override def kind: String = "SplitHandle"
}

object SplitHandle {
  /** How soon after a press let go the platform's own click for it comes, in milliseconds: one sooner is the same. */
  val sameClick: Double = 250.0
}
