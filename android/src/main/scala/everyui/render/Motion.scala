package everyui.render

import android.animation.Animator
import android.view.View

/** A node's motion (`Component.motion`): the animators that move its view, repeating, run only while the view is in a
  * window and not hidden by its own visibility.
  *
  * An animator is the Choreographer's every frame for as long as it runs, whether or not its view is drawn. A spinner
  * taken off the screen without being disposed of (a page's loading spinner removed once the page came) went on
  * turning a view in no window, frame after frame, for as long as the app ran, and each one taken off added another,
  * keeping the main thread busy under a screen with nothing moving on it and no frame drawn. Out of a window, or hidden,
  * the animators are cancelled; back in one and shown, they are made again (`make`) from where the node is then. */
final class Motion(view: View, make: () => List[Animator]) extends View.OnAttachStateChangeListener {
  private var animators: List[Animator] = Nil
  private var moving = false
  private var shown = true
  /** What the motion is for (`Component.lineage`), for the idle audit. */
  var owner: () => String = () => ""

  view.addOnAttachStateChangeListener(this)

  /** Whether the animators run now. */
  def running: Boolean = animators.nonEmpty

  /** The motion is set, or its view moved: made again, and run if the view can be seen. */
  def set(on: Boolean): Unit = {
    moving = on
    stop()
    run()
  }

  /** Hidden or shown by the node's own visibility. */
  def show(visible: Boolean): Unit = if (visible != shown) {
    shown = visible
    if (visible) run() else stop()
  }

  /** What it is for now, and whether its view is in the window, drawn (it and everything it is in are visible) and
    * under the app's root: for the idle audit. */
  def state: String = owner() + " [attached " + view.isAttachedToWindow.toString + ", shown " + view.isShown.toString +
    ", under root " + under(view).toString + "]"

  private def under(v: View): Boolean = v.getParent match {
    case p: View => (p eq Host.root) || under(p)
    case _ => false
  }

  override def onViewAttachedToWindow(v: View): Unit = run()
  override def onViewDetachedFromWindow(v: View): Unit = stop()

  private def run(): Unit = if (moving && shown && animators.isEmpty && view.isAttachedToWindow) {
    animators = make()
    animators.foreach(_.start())
    if (animators.nonEmpty) {
      Motion.running += 1
      Motion.live.add(this)
      IdleAudit.motion(true, owner)
    }
  }

  private def stop(): Unit = if (animators.nonEmpty) {
    animators.foreach(_.cancel())
    animators = Nil
    Motion.running -= 1
    Motion.live.remove(this)
    IdleAudit.motion(false, owner)
  }
}

object Motion {
  /** How many nodes' motions are running now, for the idle audit. */
  var running: Int = 0
  /** The motions running, for the idle audit to say what each is for and where its view is now. */
  val live: java.util.Set[Motion] = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap[Motion, java.lang.Boolean])
}
