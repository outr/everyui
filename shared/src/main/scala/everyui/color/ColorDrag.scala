package everyui.color

import everyui.component.{Component, Container}
import everyui.event.PointerEvent
import everyui.ui

/** A pointer or a finger dragged across `target`: `place` hears where it is, relative to the target's box, from the
  * press until it lets go, wherever it goes meanwhile. While it drags, a sheet over the whole screen catches it, since
  * a platform tells only what is under the pointer that it moved. The press also puts the focus on the target. */
class ColorDrag(target: Component, place: (Double, Double) => Unit) {
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

  private def press(e: PointerEvent): Unit = if (e.button == PointerEvent.PrimaryButton) {
    e.consume()
    if (target.focusable()) target.focus()
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
  }

  private def at(e: PointerEvent): Unit = place(e.x - target.absoluteLeft, e.y - target.absoluteTop)
}
