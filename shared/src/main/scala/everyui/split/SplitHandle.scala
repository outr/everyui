package everyui.split

import everyui.Color
import everyui.component.Container
import everyui.event.{Key, PointerEvent}
import everyui.i18n.Direction
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** What a pointer drags to share room out between two parts (`SplitPane`, `StackedPanes`): a rule down its middle, lit
  * in the accent while the pointer is over it, it is focused or it is held, and the resize cursor. Pressed, it tells
  * `pressed`; dragged, it tells `dragged` how far it has gone from where it was pressed (once past a couple of pixels,
  * mirrored across in a right-to-left language), over the whole screen, since a browser tells only what is under the
  * pointer that it moved; and `clicked` hears each click on it, `moved` saying whether the press before it dragged. Its
  * owner sizes and places it. */
class SplitHandle(val vertical: Boolean) extends Container {
  /** Whether a pointer has hold of it. */
  val grabbed: Var[Boolean] = own(Var(false))
  /** Whether the rule shows at rest, in the border's colour; without it, it shows only while lit. */
  val ruled: Var[Boolean] = own(Var(true))
  /** Where the rule lies across it, from its top (or its start): in its middle where this is less than 0. */
  val ruleAt: Var[Double] = own(Var(-1.0))
  val lit: Val[Boolean] = own(Val(focused() || event.pointer.over() || grabbed()))
  /** A press on it, before any drag. */
  val pressed: Channel[PointerEvent] = Channel[PointerEvent]
  /** How far it has been dragged from where it was pressed, in pixels along the way it moves. */
  val dragged: Channel[Double] = Channel[Double]
  val clicked: Channel[PointerEvent] = Channel[PointerEvent]
  private var from: Double = 0.0
  private var dragging: Boolean = false

  // the rule drawn down the middle, heavier while it is lit
  private val line: Container = new Container {
    backgroundColor := (if (lit()) Theme.accent() else if (ruled()) Theme.border() else Color.Clear)
  }
  cursor @= (if (vertical) "row-resize" else "col-resize")
  touchDrags @= true
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
  override def keepsKey(key: Key): Boolean = key == Key.Enter ||
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
  event.pointer.up.attach(_ => release())
  catcher.event.pointer.up.attach(_ => release())
  event.click.attach { e =>
    e.consume()
    clicked @= e
  }
  // let go of, should it be taken apart while it is held
  onDispose(() => {
    release()
    catcher.dispose()
  })

  private def at(e: PointerEvent): Double = if (vertical) e.y else e.x

  private def drag(e: PointerEvent): Unit = if (grabbed()) {
    e.consume()
    val delta = (at(e) - from) * (if (!vertical && Direction.rtl()) -1.0 else 1.0)
    if (math.abs(delta) > 2.0) dragging = true
    if (dragging) dragged @= delta
  }

  private def release(): Unit = if (grabbed()) {
    grabbed @= false
    ui.children -= catcher
  }

  override def kind: String = "SplitHandle"
}
