package everyui.render

import android.animation.ValueAnimator
import android.graphics.RectF
import android.view.{Choreographer, View, ViewGroup, ViewTreeObserver}

/** A node's motion (`Component.motion`): the animators that move its view, repeating, run only while the view is in a
  * window, shown (it and everything it is in visible: `View.isShown`) and in sight (some of it inside every box that
  * clips it, and the window).
  *
  * An animator is the Choreographer's every frame for as long as it runs, whether or not its view is drawn. A spinner
  * taken off the screen without being disposed of (a page's loading spinner removed once the page came) went on
  * turning a view in no window, frame after frame, for as long as the app ran, and each one taken off added another,
  * keeping the main thread busy under a screen with nothing moving on it and no frame drawn; and so did one inside a
  * box that was hidden, its own visibility untouched. Out of a window, hidden, or out of sight (scrolled out of the
  * box that clips it, or off the window), the animators are let go; back in one, shown and in sight, they are made
  * again (`make`) from where the node is then.
  *
  * The animators are not started: one frame callback steps every running motion about 30 times a second
  * (`Motion.gapMillis`), whatever the display's rate, setting each animator's time. A started animator set its view's
  * property on every frame of the display, each frame a traversal and a whole frame drawn: a busy glyph 24 dp across,
  * breathing, kept a 120 Hz phone drawing 120 frames a second, 13% of its main thread, all day. A breath or a turn reads
  * the same at 30. Android cannot run them on its RenderThread for an app (the animator that does is hidden, and
  * refused). Android 15 on is also told the view wants a low frame rate (`setRequestedFrameRate`), so a display that
  * can slow down for it does. While the app is away (another in front, the screen off) nothing is stepped.
  *
  * A box shown or hidden (`RenderNode.setVisible`) looks again at the motions in a window (`Motion.visibilityChanged`),
  * which are few; and a running or waiting motion looks again at whether it is in sight before a frame is drawn after
  * something moved or was laid out (`Motion.moved`), not on the frames its own steps draw. */
final class Motion(view: View, make: () => List[ValueAnimator]) extends View.OnAttachStateChangeListener {
  private var animators: List[ValueAnimator] = Nil
  private var moving = false
  // when the animators began, by the Choreographer's clock (-1 until the first step)
  private var began = -1L
  // the frame rate the view asked for before it moved (Android 15 on)
  private var rate = Float.NaN
  // what was last looked at for whether it is in sight (Motion.generation)
  private var looked = -1L
  /** What the motion is for (`Component.lineage`), for the idle audit. */
  var owner: () => String = () => ""

  private val beforeDraw: ViewTreeObserver.OnPreDrawListener = () => {
    if (looked != Motion.generation) refresh()
    true
  }
  private val laidOut: ViewTreeObserver.OnGlobalLayoutListener = () => Motion.moved()
  private var watching: ViewTreeObserver = null

  view.addOnAttachStateChangeListener(this)
  if (view.isAttachedToWindow) Motion.inWindow.add(this)

  /** Whether the animators run now. */
  def running: Boolean = animators.nonEmpty

  /** The motion is set, or its view moved: made again, and run if the view can be seen. */
  def set(on: Boolean): Unit = {
    moving = on
    stop()
    refresh()
  }

  /** Run while it can be seen, and stop while it cannot. */
  def refresh(): Unit = {
    looked = Motion.generation
    watch(moving && view.isAttachedToWindow)
    if (moving && view.isAttachedToWindow && view.isShown && inSight) run() else stop()
  }

  /** What it is for now, and whether its view is in the window, shown (as the motion reads it: it and everything it is
    * in visible), in sight and under the app's root: for the idle audit. */
  def state: String = owner() + " [attached " + view.isAttachedToWindow.toString + ", shown " + view.isShown.toString +
    ", in sight " + inSight.toString + ", under root " + under(view).toString + "]"

  private def under(v: View): Boolean = v.getParent match {
    case p: View => (p eq Host.root) || under(p)
    case _ => false
  }

  /** Whether some of the view is inside every box it is in that clips (to its outline, its bounds or its children's),
    * and inside the window: its box carried up through each view's transform and place, as the web's
    * IntersectionObserver finds an element in the view. What covers it is not looked at. */
  private def inSight: Boolean = {
    val box = Motion.box
    box.set(0f, 0f, view.getWidth.toFloat, view.getHeight.toFloat)
    var v: View = view
    var seen = true
    while (seen && v != null) {
      val clips = !(v eq view) && (v.getClipToOutline || v.getClipBounds != null ||
        (v match {
          case g: ViewGroup => g.getClipChildren
          case _ => false
        }))
      v.getParent match {
        case p: View =>
          if (clips) seen = box.intersect(0f, 0f, v.getWidth.toFloat, v.getHeight.toFloat)
          if (!v.getMatrix.isIdentity) v.getMatrix.mapRect(box)
          box.offset((v.getLeft - p.getScrollX).toFloat, (v.getTop - p.getScrollY).toFloat)
          v = p
        case _ =>
          // the window's own view: what is outside it is off the screen
          seen = box.intersect(0f, 0f, v.getWidth.toFloat, v.getHeight.toFloat)
          v = null
      }
    }
    seen
  }

  override def onViewAttachedToWindow(v: View): Unit = {
    Motion.inWindow.add(this)
    refresh()
  }
  override def onViewDetachedFromWindow(v: View): Unit = {
    Motion.inWindow.remove(this)
    watch(false)
    stop()
  }

  // looking before each frame drawn for whether it came into sight or went out of it, while it is set and in a window
  private def watch(on: Boolean): Unit = if (on != (watching != null)) {
    if (on) {
      watching = view.getViewTreeObserver
      watching.addOnPreDrawListener(beforeDraw)
      watching.addOnGlobalLayoutListener(laidOut)
    }
    else {
      if (watching.isAlive) {
        watching.removeOnPreDrawListener(beforeDraw)
        watching.removeOnGlobalLayoutListener(laidOut)
      }
      watching = null
    }
  }

  private def run(): Unit = if (animators.isEmpty) {
    animators = make()
    if (animators.nonEmpty) {
      began = -1L
      if (android.os.Build.VERSION.SDK_INT >= 35) {
        rate = view.getRequestedFrameRate
        view.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_LOW)
      }
      Motion.running += 1
      Motion.live.add(this)
      Motion.step()
      IdleAudit.motion(true, owner)
    }
  }

  private def stop(): Unit = if (animators.nonEmpty) {
    animators = Nil
    if (android.os.Build.VERSION.SDK_INT >= 35) view.setRequestedFrameRate(rate)
    Motion.running -= 1
    Motion.live.remove(this)
    IdleAudit.motion(false, owner)
  }

  /** Each animator set to where it is `now` (the Choreographer's nanoseconds): nothing in its first wait (its start
    * delay), then round its cycle. */
  private def step(now: Long): Unit = {
    if (began < 0L) began = now
    val ms = (now - began) / 1000000L
    animators.foreach { a =>
      val at = ms - a.getStartDelay
      if (at >= 0L) a.setCurrentPlayTime(if (a.getDuration > 0L) at % a.getDuration else 0L)
    }
  }
}

object Motion {
  /** How long after a step the next is asked for, on the frame after that: 33 ms on at 60, 90 or 120 Hz, about 30
    * steps a second. */
  val gapMillis: Long = 25L
  /** How many nodes' motions are running now, for the idle audit. */
  var running: Int = 0
  /** The motions running, for the idle audit to say what each is for and where its view is now. */
  val live: java.util.Set[Motion] = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap[Motion, java.lang.Boolean])
  /** The motions whose views are in a window, which a box shown or hidden may start or stop: a view out of one is
    * looked at again as it comes back. */
  private val inWindow: java.util.Set[Motion] =
    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap[Motion, java.lang.Boolean])
  // bumped as anything moves or is laid out: a motion looks again at whether it is in sight before the next frame
  private var generation = 0L
  // whether the next step is asked for
  private var stepping = false
  private val box = new RectF
  // away (another app in front, the screen off) nothing is stepped, as Android 14 on pauses an endless animator: the
  // steps go on as the app comes back
  private var away = false
  Platform.onAway(() => away = true)
  Platform.onResume(() => {
    away = false
    step()
  })
  private lazy val handler = new android.os.Handler(android.os.Looper.getMainLooper)
  // the first frame after the gap, asked for once the gap is over
  private val ask: Runnable = () => Choreographer.getInstance.postFrameCallback(stepper)
  private val stepper: Choreographer.FrameCallback = now => {
    stepping = false
    if (!live.isEmpty) {
      live.toArray(new Array[Motion](0)).foreach(_.step(now))
      step()
    }
  }

  /** The next step of the running motions asked for, `gapMillis` on, unless it already is. */
  private def step(): Unit = if (!stepping && !away && !live.isEmpty) {
    stepping = true
    handler.postDelayed(ask, gapMillis)
  }

  /** Something moved (a node placed again, a scroll) or was laid out: each motion set looks again, before the next
    * frame, at whether it is in sight. */
  def moved(): Unit = generation += 1L

  /** A view was shown or hidden: each motion in a window looks again at whether it can be seen. */
  def visibilityChanged(): Unit = if (!inWindow.isEmpty) {
    val all = inWindow.toArray(new Array[Motion](0))
    all.foreach(_.refresh())
  }
}
