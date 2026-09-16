package everyui.component

import everyui.event.{Key, PointerEvent}
import everyui.focus.Focus
import everyui.Color
import everyui.component.types.Prop
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A viewport over `content`, which may be larger than it: what fits shows, and the wheel, a drag, the arrows and
  * the page keys (while focused) bring the rest into view, and the focus moving onto something out of view scrolls to
  * it (a remote has no other way to see it). Add to `content.children`, not to this.
  *
  * Down the page by default; `horizontal` makes it a rail, scrolled along its length instead.
  *
  * The web scrolls the node itself (its own scrollbar, momentum and keys); every other backend clips the node and
  * this shifts the content. */
class ScrollContainer(val horizontal: Boolean = false) extends Container {
  override def kind: String = "ScrollContainer"

  val content: Container = new Container
  /** How far the content is scrolled, in pixels. */
  val offset: Var[Double] = Var(0.0)
  private val furthest: Val[Double] =
    Val(math.max(0.0, (if (horizontal) content.contentWidth() - size.width() else content.contentHeight() - size.height())))

  /** How much room `reveal` leaves around what it brings into view: enough for whatever sits beside it and should be
    * seen with it (a rail's name, above its titles). */
  val revealMargin: Var[Double] = Var(16.0)

  /** The scrollbar's colour, where the platform draws one (the web: a thin one, on a clear track). */
  lazy val scrollbar: Prop[Color] = new Prop[Color](Theme.border(), c => node.setScrollbarColor(Component.css(c)))

  node.setScrollable(!horizontal, horizontal)
  node.setScrollbarColor(Component.css(scrollbar()))
  if (horizontal) content.size.height := size.height else content.size.width := size.width
  children += content

  if (!Platform.nativeScroll()) {
    size.width.attach(_ => clip())
    size.height.attach(_ => clip())
    if (horizontal) content.position.left := -offset() else content.position.top := -offset()
    furthest.attach(most => if (offset() > most) offset @= most)
    event.pointer.wheel.attach(e => scrollBy(if (horizontal && e.x != 0.0) e.x else e.y))
    var dragFrom: Option[Double] = None
    var dragOffset = 0.0
    event.pointer.down.attach { e =>
      dragFrom = Some(if (horizontal) e.x else e.y)
      dragOffset = offset()
    }
    event.pointer.move.attach(e => dragFrom.foreach(from => scrollTo(dragOffset + from - (if (horizontal) e.x else e.y))))
    event.pointer.up.attach(_ => dragFrom = None)
    Focus.focused.attach(_.foreach(c => if (holds(c)) reveal(c)))
    content.children.attach(_.foreach(cull))
    event.key.down.attach { k =>
      val along = if (horizontal) size.width() else size.height()
      if (k.key == (if (horizontal) Key.ArrowRight else Key.ArrowDown)) scrollBy(40.0)
      else if (k.key == (if (horizontal) Key.ArrowLeft else Key.ArrowUp)) scrollBy(-40.0)
      else if (k.key == Key.PageDown) scrollBy(along * 0.9)
      else if (k.key == Key.PageUp) scrollBy(-along * 0.9)
    }
  }

  def scrollBy(delta: Double): Unit = scrollTo(offset() + delta)
  def scrollTo(to: Double): Unit = {
    if (Platform.nativeScroll()) {
      if (horizontal) node.setScrollLeft(to) else node.setScrollTop(to)
    }
    else offset @= math.max(0.0, math.min(furthest(), to))
  }

  /** Scroll the least that shows all of `c` (a component under `content`), with a margin. */
  def reveal(c: Component): Unit = {
    val start = if (horizontal) c.absoluteLeft - content.absoluteLeft else c.absoluteTop - content.absoluteTop
    val end = start + (if (horizontal) c.size.width() else c.size.height())
    val along = if (horizontal) size.width() else size.height()
    if (start < offset() + revealMargin()) scrollTo(start - revealMargin())
    else if (end > offset() + along) scrollTo(end - along + 16.0)
  }

  /** What is out of sight is not drawn: a rail of a hundred cards would otherwise be a hundred nodes for the platform
    * to draw every frame, which a television has no time for. A child keeps its place and its size either way, and a
    * screen's worth of slack on each side means one is always drawn before it can be seen.
    *
    * The web scrolls the node itself, and a browser already draws only what shows. */
  private def cull(c: Component): Unit = if (!culling.contains(c)) {
    culling = culling + c
    c.visible := {
      val along = if (horizontal) size.width() else size.height()
      val start = if (horizontal) c.position.left() else c.position.top()
      val end = start + (if (horizontal) c.size.width() else c.size.height())
      end > offset() - along && start < offset() + along * 2.0
    }
  }

  private var culling: Set[Component] = Set.empty

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
