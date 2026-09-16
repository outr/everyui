package everyui.component

import everyui.event.{Key, PointerEvent}
import everyui.focus.Focus
import everyui.Color
import everyui.component.types.Prop
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A viewport over `content`, which may be taller than it: what fits shows, and the wheel, a drag, the arrows and
  * the page keys (while focused) bring the rest into view, and the focus moving onto something out of view scrolls to
  * it (a remote has no other way to see it). Add to `content.children`, not to this.
  *
  * The web scrolls the node itself (its own scrollbar, momentum and keys); every other backend clips the node and
  * this shifts the content. */
class ScrollContainer extends Container {
  override def kind: String = "ScrollContainer"

  val content: Container = new Container
  /** How far the content is scrolled, in pixels. */
  val offset: Var[Double] = Var(0.0)
  private val furthest: Val[Double] = Val(math.max(0.0, content.contentHeight() - size.height()))

  /** The scrollbar's colour, where the platform draws one (the web: a thin one, on a clear track). */
  lazy val scrollbar: Prop[Color] = new Prop[Color](Theme.border(), c => node.setScrollbarColor(Component.css(c)))

  node.setScrollable(true)
  node.setScrollbarColor(Component.css(scrollbar()))
  content.size.width := size.width
  children += content

  if (!Platform.nativeScroll()) {
    size.width.attach(_ => clip())
    size.height.attach(_ => clip())
    content.position.top := -offset()
    furthest.attach(most => if (offset() > most) offset @= most)
    event.pointer.wheel.attach(e => scrollBy(e.y))
    var dragFrom: Option[Double] = None
    var dragOffset = 0.0
    event.pointer.down.attach { e => dragFrom = Some(e.y); dragOffset = offset() }
    event.pointer.move.attach(e => dragFrom.foreach(from => scrollTo(dragOffset + from - e.y)))
    event.pointer.up.attach(_ => dragFrom = None)
    Focus.focused.attach(_.foreach(c => if (holds(c)) reveal(c)))
    event.key.down.attach { k =>
      if (k.key == Key.ArrowDown) scrollBy(40.0)
      else if (k.key == Key.ArrowUp) scrollBy(-40.0)
      else if (k.key == Key.PageDown) scrollBy(size.height() * 0.9)
      else if (k.key == Key.PageUp) scrollBy(-size.height() * 0.9)
    }
  }

  def scrollBy(delta: Double): Unit = scrollTo(offset() + delta)
  def scrollTo(y: Double): Unit = {
    if (Platform.nativeScroll()) node.setScrollTop(y)
    else offset @= math.max(0.0, math.min(furthest(), y))
  }

  /** Scroll the least that shows all of `c` (a component under `content`), with a margin. */
  def reveal(c: Component): Unit = {
    val top = c.absoluteTop - content.absoluteTop
    val bottom = top + c.size.height()
    if (top < offset()) scrollTo(top - 16.0)
    else if (bottom > offset() + size.height()) scrollTo(bottom - size.height() + 16.0)
  }

  /** Whether `c` is somewhere under `content`. */
  private def holds(c: Component): Boolean = {
    var at: Option[Component] = Some(c)
    var found = false
    while (!found && at.nonEmpty) {
      if (at.exists(_ eq content)) found = true
      at = at.flatMap(_.parent())
    }
    found
  }

  private def clip(): Unit = node.setClip(size.width(), size.height())
}
