package everyui.component

import everyui.easing.Easing
import everyui.event.{Fling, FlingRun, Key, Pointer, PointerEvent}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.Color
import everyui.component.feature.ContainerFeature
import everyui.component.types.Prop
import everyui.render.Platform
import everyui.theme.Theme
import everyui.task.*
import reactify.*
import reactify.reaction.Reaction

import scala.concurrent.duration.*

/** A viewport over `content`, which may be larger than it: what fits shows, and the wheel, a drag, the arrows and
  * the page keys (while focused) bring the rest into view, and the focus moving onto something out of view scrolls to
  * it (a remote has no other way to see it). Add to `content.children`, not to this.
  *
  * Down the page by default; `horizontal` makes it a rail, scrolled along its length instead. A rail in a
  * right-to-left language runs from the right: it opens at its right end, on its first items, and Left goes on along it.
  * Without `followsDirection` it runs from its left in every language, as a timeline does.
  *
  * The web scrolls the node itself (its own scrollbar, momentum and keys); every other backend clips the node and
  * this shifts the content. */
class ScrollContainer(val horizontal: Boolean = false, val followsDirection: Boolean = true) extends Container {
  override def kind: String = "ScrollContainer"

  val content: Container = new Container {
    // where the page scrolls the node itself (the web) the content stays put in it, and is where it is on the screen
    // only less how far it has scrolled, as it is where this shifts it: a pointer on something in it is found there
    override def absoluteLeft: Double = super.absoluteLeft - natively(true)
    override def absoluteTop: Double = super.absoluteTop - natively(false)
  }
  /** How far the content is scrolled from its start, in pixels: a rail's start is its right end in a right-to-left
    * language, so it stays on its first items at 0 however it grows or narrows. */
  val offset: Var[Double] = own(Var(0.0))
  // a rail in a right-to-left language, which runs from the right unless it runs from its left in every language
  private val fromRight: Val[Boolean] = own(Val(horizontal && followsDirection && Direction.rtl()))
  // how far the page's own scroll has moved the content on the screen, across or down: back along a rail from the right
  private def natively(across: Boolean): Double =
    if (across != horizontal || !Platform.nativeScroll()) 0.0 else if (fromRight()) -offset() else offset()
  // as far as the content's length, which is what it holds unless it is given more (room left at its end)
  private val furthest: Val[Double] =
    Val(math.max(0.0, (if (horizontal) content.size.width() - size.width() else content.size.height() - size.height())))
  // the glide under way, and where it is headed: a second press while one glides goes on from where the first was
  // going, not from where it has got to, so pressing quickly never comes up short
  private var glideRun: Option[TaskInstance] = None
  private var glideAim: Double = 0.0
  private var glideAt: Double = 0.0
  // a flick carrying the content on after the finger lifted, on a touch screen
  private var flung: Option[FlingRun] = None

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
    // its start at the viewport's: in a right-to-left language the content's right end at the viewport's right, which
    // is also where a browser's own scroll starts (content overhanging the right of a right-to-left page's scroll could
    // not be scrolled to at all)
    val shifted = !Platform.nativeScroll()
    content.position.left := {
      val by = if (shifted) offset() else 0.0
      if (fromRight()) size.width() - content.size.width() + by else 0.0 - by
    }
    // and a change of direction starts it again at its start, as a browser does; one run from its left stays put, and
    // the browser counts it from its left too
    if (followsDirection) follow(Direction.rtl, (_: Boolean) => scrollTo(0.0))
    else node.setLeftToRight()
  }
  else {
    content.size.width := size.width
    content.size.height := content.contentHeight()
  }
  children += content
  // the web scrolls the node itself (a wheel, a finger, the scrollbar, a smooth scroll): the offset follows it there
  if (Platform.nativeScroll()) node.onScrolled(() => offset @= node.scrolled(horizontal))

  // a focus moved by the keys, a remote or the app is brought into view; on the web one a pointer just moved is not,
  // since it lands where it was clicked or hovered, which is already in view
  follow(Focus.focused, (f: Option[Component]) => f.foreach { c =>
    if (holds(c) && (!Platform.nativeScroll() || Platform.remoteNavigation() || !Pointer.recent)) reveal(c)
  })
  if (!Platform.nativeScroll()) {
    size.width.attach(_ => clip())
    size.height.attach(_ => clip())
    if (!horizontal) content.position.top := -offset()
    furthest.attach(most => if (offset() > most) offset @= most)
    // anything else setting the offset takes over from a glide
    offset.attach(at => if (glideRun.nonEmpty && at != glideAt) stopGlide())
    // a sideways wheel and a drag move the content as they go, which is back along a rail from the right
    event.pointer.wheel.attach(e => scrollBy(if (horizontal && e.x != 0.0) (if (fromRight()) -e.x else e.x) else e.y))
    var dragFrom: Option[Double] = None
    var dragOffset = 0.0
    // and on a touch screen a flick carries on once the finger lifts, slowing as a phone's own scrolling does
    val flicks = Platform.touchSupport() && !Platform.pointerSupport()
    val fling = new Fling
    event.pointer.down.attach { e =>
      if (flicks) stopGlide()
      val at = if (horizontal) e.x else e.y
      dragFrom = Some(at)
      dragOffset = offset()
      fling.clear()
      fling.at(at, Platform.ticks())
    }
    event.pointer.move.attach { e =>
      dragFrom.foreach { from =>
        val moved = (if (horizontal) e.x else e.y) - from
        scrollTo(dragOffset + (if (fromRight()) moved else -moved))
        fling.at(if (horizontal) e.x else e.y, Platform.ticks())
      }
    }
    event.pointer.up.attach { _ =>
      val v = fling.velocity(Platform.ticks())
      if (flicks && dragFrom.nonEmpty && v != 0.0) {
        val run = new FlingRun(if (fromRight()) v else -v, (d: Double) => {
          val was = offset()
          offset @= math.max(0.0, math.min(furthest(), was + d))
          offset() - was
        })
        flung = Some(run)
        run.start()
      }
      dragFrom = None
    }
    // what comes into the content is culled, and what leaves it is let go: a child followed on after it had gone held it, and
    // all it held, for as long as this scroll lived
    content.children.attach(_ => recull())
    event.key.down.attach { k =>
      val along = if (horizontal) size.width() else size.height()
      val on = if (fromRight()) Key.ArrowLeft else if (horizontal) Key.ArrowRight else Key.ArrowDown
      val back = if (fromRight()) Key.ArrowRight else if (horizontal) Key.ArrowLeft else Key.ArrowUp
      if (k.key == on) stride(step())
      else if (k.key == back) stride(-step())
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
    flung.foreach(_.stop())
    flung = None
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
    val length = if (horizontal) c.size.width() else c.size.height()
    // from the content's start, which is its right end in a rail running from the right
    val left = c.absoluteLeft - content.absoluteLeft
    val start =
      if (fromRight()) content.size.width() - left - length
      else if (horizontal) left
      else c.absoluteTop - content.absoluteTop
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
    // and what is out of sight lets its pictures go until it comes back
    culling = culling.updated(c, c.visible.attach(on => ImageView.showing(c, on)))
    c.visible := {
      val along = if (horizontal) size.width() else size.height()
      val start = if (horizontal) c.position.left() else c.position.top()
      val end = start + (if (horizontal) c.size.width() else c.size.height())
      // where the viewport is over the content, from its left in a rail running from the right
      val at = if (fromRight()) content.size.width() - along - offset() else offset()
      end > at - along * 0.5 && start < at + along * 1.5
    }
  }

  // the children culled, each with what lets its pictures go
  private var culling: Map[Component, Reaction[Boolean]] = Map.empty
  // the content's children as they were last culled
  private var culled: Vector[Component] = Vector.empty

  /** The content's children culled as they are now: worked out from what was culled last, not from a change's own previous
    * value, which need not be what was there (a change made deep in a chain of changes is heard late, or is dropped for a
    * later one, whose previous value is then a list that never was). */
  private def recull(): Unit = {
    val now = content.children()
    if (!(now eq culled)) {
      val was = culled
      culled = now
      ContainerFeature.missing(was, now).foreach(uncull)
      ContainerFeature.missing(now, was).foreach(cull)
    }
  }

  /** A child taken out of the content is no longer culled: its `visible` stays as it was and stops following the
    * viewport (which would otherwise hold it for as long as this lived). */
  private def uncull(c: Component): Unit = culling.get(c).foreach { shows =>
    culling = culling - c
    c.visible.reactions -= shows
    Component.unbind(c.visible)
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

object ScrollContainer {
  /** Where a ScrollContainer's `reveal` puts the focus unless it says otherwise, for an app to set once before it
    * builds any: a television's wants 0.5, the focus followed down the middle of the screen. */
  val revealAlignDefault: Var[Option[Double]] = Var(None)

  /** How long a ScrollContainer's `reveal` glides unless it says otherwise, in seconds (0: it jumps). */
  val revealGlideDefault: Var[Double] = Var(0.0)
}
