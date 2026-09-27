package everyui.component

import everyui.easing.Easing
import everyui.event.{Key, PointerEvent}
import everyui.focus.Focus
import everyui.Color
import everyui.component.types.Prop
import everyui.render.Platform
import everyui.theme.Theme
import everyui.task.*
import reactify.*

import scala.concurrent.duration.*

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
  val offset: Var[Double] = own(Var(0.0))
  // as far as the content's length, which is what it holds unless it is given more (room left at its end)
  private val furthest: Val[Double] =
    Val(math.max(0.0, (if (horizontal) content.size.width() - size.width() else content.size.height() - size.height())))
  // the glide under way, and where it is headed: a second press while one glides goes on from where the first was
  // going, not from where it has got to, so pressing quickly never comes up short
  private var glideRun: Option[TaskInstance] = None
  private var glideAim: Double = 0.0
  private var glideAt: Double = 0.0

  /** How much room `reveal` leaves around what it brings into view: enough for whatever sits beside it and should be
    * seen with it (a rail's name, above its titles). */
  val revealMargin: Var[Double] = own(Var(16.0))

  /** The scrollbar's colour, where the platform draws one (the web: a thin one, on a clear track). */
  lazy val scrollbar: Prop[Color] = own(new Prop[Color](Theme.border(), c => node.setScrollbarColor(Component.css(c))))

  node.setScrollable(!horizontal, horizontal)
  node.setScrollbarColor(Component.css(scrollbar()))
  // across, the content is the viewport's size; along, as long as what it holds reaches, unless it is given a length (a
  // content with no length draws nothing on Android, which clips a view to its box)
  if (horizontal) {
    content.size.height := size.height
    content.size.width := content.contentWidth()
    // and a row laid out in it runs the length of what it holds: mirrored for a right-to-left language within its
    // width instead, which is itself the reach of the children just mirrored, it shrank to its first child
    content.widthFollowsContent = true
  }
  else {
    content.size.width := size.width
    content.size.height := content.contentHeight()
  }
  children += content

  // the web scrolls to the focus itself only where a remote drives it (a TV's browser): under a pointer the focus lands
  // where it was clicked, which is already in view
  if (!Platform.nativeScroll() || Platform.remoteNavigation()) {
    follow(Focus.focused, (f: Option[Component]) => f.foreach(c => if (holds(c)) reveal(c)))
  }
  if (!Platform.nativeScroll()) {
    size.width.attach(_ => clip())
    size.height.attach(_ => clip())
    if (horizontal) content.position.left := -offset() else content.position.top := -offset()
    furthest.attach(most => if (offset() > most) offset @= most)
    // anything else setting the offset takes over from a glide
    offset.attach(at => if (glideRun.nonEmpty && at != glideAt) stopGlide())
    event.pointer.wheel.attach(e => scrollBy(if (horizontal && e.x != 0.0) e.x else e.y))
    var dragFrom: Option[Double] = None
    var dragOffset = 0.0
    event.pointer.down.attach { e =>
      dragFrom = Some(if (horizontal) e.x else e.y)
      dragOffset = offset()
    }
    event.pointer.move.attach(e => dragFrom.foreach(from => scrollTo(dragOffset + from - (if (horizontal) e.x else e.y))))
    event.pointer.up.attach(_ => dragFrom = None)
    content.children.attach(_.foreach(cull))
    event.key.down.attach { k =>
      val along = if (horizontal) size.width() else size.height()
      if (k.key == (if (horizontal) Key.ArrowRight else Key.ArrowDown)) stride(step())
      else if (k.key == (if (horizontal) Key.ArrowLeft else Key.ArrowUp)) stride(-step())
      else if (k.key == Key.PageDown) scrollBy(along * 0.9)
      else if (k.key == Key.PageUp) scrollBy(-along * 0.9)
    }
  }

  /** How far an arrow moves it, and over how long, in seconds (0: at once). A page of text read with a remote wants a
    * long stride that glides, so the eye can follow where the words went. */
  val step: Var[Double] = own(Var(40.0))
  val glide: Var[Double] = own(Var(0.0))

  private def stride(delta: Double): Unit = glideTo((if (glideRun.nonEmpty) glideAim else offset()) + delta, glide())

  def scrollBy(delta: Double): Unit = scrollTo(offset() + delta)
  def scrollTo(to: Double): Unit = {
    if (Platform.nativeScroll()) {
      if (horizontal) node.setScrollLeft(to) else node.setScrollTop(to)
    }
    else {
      stopGlide()
      offset @= math.max(0.0, math.min(furthest(), to))
    }
  }

  /** Scroll to `to` over `seconds`, easing out as it arrives (0: at once). The web glides as its browser does. */
  def glideTo(to: Double, seconds: Double): Unit =
    if (seconds <= 0.0) scrollTo(to)
    else if (Platform.nativeScroll()) node.scrollSmoothly(to, horizontal)
    else {
      stopGlide()
      val aim = math.max(0.0, math.min(furthest(), to))
      if (math.abs(aim - offset()) >= 0.5) {
        glideAim = aim
        val move = PartialAnimate(() => offset(), (d: Double) => { glideAt = d; offset @= d }, () => aim)
          .in((seconds * 1000.0).toLong.millis).easing(Easing.cubicOut)
        glideRun = Some(sequential(move, synchronous { glideRun = None }).start())
      }
      else offset @= aim
    }

  private def stopGlide(): Unit = {
    glideRun.foreach(_.cancel())
    glideRun = None
  }

  /** Where `reveal` puts what it brings into view, as a share of the room the viewport has around it: 0 at the start,
    * 0.5 in the middle (as a television's focus is followed), 1 at the end. None scrolls the least that shows it.
    * Unless set, what `ScrollContainer.revealAlignDefault` says. */
  val revealAlign: Var[Option[Double]] = own(Var(ScrollContainer.revealAlignDefault()))

  /** How long `reveal` glides for, in seconds (0: it jumps). Unless set, what `ScrollContainer.revealGlideDefault`
    * says. */
  val revealGlide: Var[Double] = own(Var(ScrollContainer.revealGlideDefault()))

  /** Bring `c` (a component under `content`) into view: where `revealAlign` says, else by the least that shows all of
    * it, with a margin. Either way no further than the content goes, so the first and the last are shown at the ends
    * rather than in the middle, and something longer than the viewport shows from its start.
    *
    * It glides for `revealGlide`, unless the way is more than twice the viewport: a glide that long is a blur of what
    * is passed (and on a television, a great deal to draw), so it jumps. */
  def reveal(c: Component): Unit = {
    val start = if (horizontal) c.absoluteLeft - content.absoluteLeft else c.absoluteTop - content.absoluteTop
    val length = if (horizontal) c.size.width() else c.size.height()
    val along = if (horizontal) size.width() else size.height()
    val at = if (Platform.nativeScroll()) node.scrolled(horizontal) else if (glideRun.nonEmpty) glideAim else offset()
    val margin = revealMargin()
    val to = revealAlign() match {
      // the margin is left at the start whatever the alignment: at 0, what sits above it (a rail's name) shows too
      case Some(share) if length + margin < along => start - margin - (along - length - margin) * share
      case Some(_) => start - margin
      case None =>
        if (start < at + margin || length + margin >= along) start - margin
        else if (start + length > at + along) start + length - along + 16.0
        else at
    }
    val far = math.abs(to - at) > along * 2.0
    if (math.abs(to - at) >= 0.5) glideTo(to, if (far) 0.0 else revealGlide())
  }

  /** What is out of sight is not drawn: a rail of a hundred cards would otherwise be a hundred nodes for the platform
    * to draw every frame, which a television has no time for. A child keeps its place and its size either way, and
    * half a screen of slack on each side means one is drawn before it can be seen (a whole screen each way was two
    * more screens of cards for a Roku to draw).
    *
    * The web scrolls the node itself, and a browser already draws only what shows. */
  private def cull(c: Component): Unit = if (!culling.contains(c)) {
    culling = culling + c
    // and what is out of sight lets its pictures go until it comes back
    c.visible.attach(on => ImageView.showing(c, on))
    c.visible := {
      val along = if (horizontal) size.width() else size.height()
      val start = if (horizontal) c.position.left() else c.position.top()
      val end = start + (if (horizontal) c.size.width() else c.size.height())
      end > offset() - along * 0.5 && start < offset() + along * 1.5
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

object ScrollContainer {
  /** Where a ScrollContainer's `reveal` puts the focus unless it says otherwise, for an app to set once before it
    * builds any: a television's wants 0.5, the focus followed down the middle of the screen. */
  val revealAlignDefault: Var[Option[Double]] = Var(None)

  /** How long a ScrollContainer's `reveal` glides unless it says otherwise, in seconds (0: it jumps). */
  val revealGlideDefault: Var[Double] = Var(0.0)
}
