package everyui.render

import android.view.View

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** A change to what is drawn, made off the main thread, taken there.
  *
  * An app's task resumes on a thread of rapid's (a socket's answer, `Task.sleep`), and what it changes there reaches
  * the views there: Android says `CalledFromWrongThreadException` for a view in the window changed off the main thread
  * (and will throw it), and a child taken away while the main thread draws left a container empty. So each
  * [[RenderNode]] change asks `needed` first, and where the answer is yes it is queued here, as the call it was, and
  * made on the main thread through one Handler, in the order the changes were made in. The components themselves
  * (their `Var`s, their children) change where they are changed.
  *
  * Only a view in the window is taken there: one being built off the screen is changed where it is, as Android allows,
  * so it is measured as it will be drawn. A view on its way into the window (added to one there, the add still queued)
  * counts as in it (`joining`), and so does all under it. What lives on the main thread whether it is drawn or not (a
  * player, a web page) is taken there whenever it is called off it (`offThread`).
  *
  * On the main thread a change is made at once, as ever, after whatever is still queued: a change the main thread
  * makes after one from a worker is never overtaken by it. That costs the main thread a look at the thread and at the
  * queue.
  *
  * In a debug build, or launched with `--es hops 1`, the first change taken from each place in the app is logged
  * (`hop: …`), with the app's frame that made it, so the app can make the change on the main thread itself
  * (`everyui.network.onScreen`, `Platform.later`). */
object Hop {
  private val queue = new ConcurrentLinkedQueue[() => Unit]
  private val posted = new AtomicBoolean(false)
  private lazy val main = android.os.Looper.getMainLooper.getThread
  private lazy val handler = new android.os.Handler(android.os.Looper.getMainLooper)
  // on the main thread only: whether the queue is being emptied
  private var draining = false
  // views added to one in the window by a queued change, until it is made
  private val joining = new java.util.IdentityHashMap[View, Integer]
  @volatile private var joiners = 0

  /** Whether the first change from each place in the app is logged: in a debug build, or where the launch asks. */
  @volatile var audit: Boolean = false

  private def onThread: Boolean = Thread.currentThread eq main

  /** Whether a change to `node` made here goes to the main thread: made off it, to a view in the window or on its way
    * to it. On the main thread never, and what is queued is made first. */
  def needed(node: RenderNode): Boolean =
    if (onThread) {
      caughtUp()
      false
    } else live(node.view)

  /** The same, for a change that puts `child` under `node`: either in the window takes it there. */
  def needed(node: RenderNode, child: RenderNode): Boolean =
    if (onThread) {
      caughtUp()
      false
    } else live(node.view) || live(child.view)

  /** Whether a call made here goes to the main thread whatever it is made to: made off it, to what lives on that thread
    * whether it is drawn or not (a player, a web page). On the main thread never, and what is queued is made first. */
  def offThread: Boolean =
    if (onThread) {
      caughtUp()
      false
    } else true

  /** `change` made on the main thread, after every change queued before it. */
  def apply(change: () => Unit): Unit = {
    if (audit) HopSite.said("the main thread")
    queue.add(change)
    if (posted.compareAndSet(false, true)) handler.post(() => drain())
  }

  /** `change`, which puts `child` under a view in the window, made on the main thread; until it is, `child` and all
    * under it count as in the window. */
  def joining(child: RenderNode, change: () => Unit): Unit = {
    joined(child.view, 1)
    apply(() => try change() finally joined(child.view, -1))
  }

  private def joined(view: View, by: Int): Unit = joining.synchronized {
    val now = Option(joining.get(view)).map(_.intValue).getOrElse(0) + by
    if (now <= 0) joining.remove(view) else joining.put(view, now)
    joiners = joining.size
  }

  // (what is joining read first: a view put in the window and let go of as joining between the two reads is then seen in
  // the window, the main thread having put it there before it let go)
  private def live(view: View): Boolean = (joiners > 0 && joining.synchronized {
    var found = joining.containsKey(view)
    var at = view.getParent
    while (!found && at != null) at match {
      case v: View =>
        found = joining.containsKey(v)
        at = v.getParent
      case _ => at = null
    }
    found
  }) || view.isAttachedToWindow

  private def caughtUp(): Unit = if (!draining && !queue.isEmpty) drain()

  // every change queued, in order, those queued meanwhile with them
  private def drain(): Unit = if (!draining) {
    draining = true
    posted.set(false)
    try {
      var next = queue.poll()
      while (next != null) {
        try next()
        catch { case t: Throwable => Platform.logError("A change made off the main thread", t) }
        next = queue.poll()
      }
    } finally draining = false
  }
}
