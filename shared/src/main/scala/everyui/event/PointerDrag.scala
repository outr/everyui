package everyui.event

import everyui.component.{Component, Container}
import everyui.ui

/** A pointer or a finger dragged across `target`: `place` hears where it is, relative to the target's box, from the
  * press until it lets go, wherever it goes meanwhile, and `end` hears that it let go. While it drags, a sheet over the
  * whole screen catches it, since a platform tells only what is under the pointer that it moved. `take` says whether a
  * press there is the drag's (not while the target is disabled, say); one it takes puts the focus on the target, where
  * a click would (`Focus.clickFocuses`).
  *
  * {{{
  * new PointerDrag(bar, (x, _) => value @= x / bar.size.width(), () => save(value()))
  * }}} */
class PointerDrag(target: Component, place: (Double, Double) => Unit, end: () => Unit = () => (),
                  take: (Double, Double) => Boolean = (_, _) => true) {
  private var grabbed: Boolean = false
  private val catcher: Container = new Container
  catcher.size.width := ui.size.width()
  catcher.size.height := ui.size.height()
  target.touchDrags @= true

  target.event.pointer.down.attach(e => press(e))
  target.event.pointer.move.attach(e => drag(e))
  catcher.event.pointer.move.attach(e => drag(e))
  target.event.pointer.up.attach(e => release(e))
  catcher.event.pointer.up.attach(e => release(e))

  /** Whether it is being dragged. */
  def dragging: Boolean = grabbed

  private def press(e: PointerEvent): Unit =
    if (e.button == PointerEvent.PrimaryButton && !grabbed && take(e.x - target.absoluteLeft, e.y - target.absoluteTop)) {
      e.consume()
      everyui.focus.Focus.dragging()
      if (target.focusable() && everyui.focus.Focus.clickFocuses(target) && !target.focused()) target.focus()
      grabbed = true
      ui.children += catcher
      at(e)
    }

  private def drag(e: PointerEvent): Unit = if (grabbed) {
    e.consume()
    at(e)
  }

  private def release(e: PointerEvent): Unit = if (grabbed) {
    e.consume()
    at(e)
    grabbed = false
    ui.children -= catcher
    end()
  }

  private def at(e: PointerEvent): Unit = place(e.x - target.absoluteLeft, e.y - target.absoluteTop)
}
