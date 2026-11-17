package everyui.render

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** A change to what is drawn, made off the FX thread, taken there.
  *
  * An app's task resumes on a thread of rapid's (a socket's answer, `Task.sleep`), and what it changes there reaches
  * the scene graph there: JavaFX throws for a child added to or taken from a node in a window (`Not on FX application
  * thread`), and a property set races the pulse that draws it. So each [[RenderNode]] change asks `needed` first, and
  * where the answer is yes it is queued here, as the call it was, and made on the FX thread, in the order the changes
  * were made in. The components themselves (their `Var`s, their children) change where they are changed.
  *
  * Only a node in a window is taken there: one being built off the screen is changed where it is, as JavaFX allows,
  * so it is measured as it will be drawn. A node on its way into a window (added to one there, the add still queued)
  * counts as in it (`joining`), and so does all under it.
  *
  * On the FX thread a change is made at once, as ever, after whatever is still queued: a change the FX thread makes
  * after one from a worker is never overtaken by it. That costs the FX thread a look at the thread and at the queue.
  *
  * With `everyui.hops` set (`-Deveryui.hops=1`, or `EVERYUI_HOPS=1`), the first change taken from each place in the app
  * is logged, with the app's frame that made it, so the app can make the change on the FX thread itself
  * (`everyui.network.onScreen`, `Platform.later`). */
object Hop {
  private val queue = new ConcurrentLinkedQueue[() => Unit]
  private val posted = new AtomicBoolean(false)
  // on the FX thread only: whether the queue is being emptied
  private var draining = false
  // nodes added to one in a window by a queued change, until it is made
  private val joining = new java.util.IdentityHashMap[javafx.scene.Node, Integer]
  @volatile private var joiners = 0

  /** Whether the first change from each place in the app is logged. */
  @volatile var audit: Boolean =
    Option(System.getProperty("everyui.hops")).orElse(Option(System.getenv("EVERYUI_HOPS"))).exists(v => v == "1" || v == "true")

  private def onThread: Boolean = javafx.application.Platform.isFxApplicationThread

  /** Whether a change to `node` made here goes to the FX thread: made off it, to a node in a window or on its way to
    * one. On the FX thread never, and what is queued is made first. */
  def needed(node: RenderNode): Boolean =
    if (onThread) {
      caughtUp()
      false
    } else live(node.fx)

  /** The same, for a change that puts `child` under `node`: either in a window takes it there. */
  def needed(node: RenderNode, child: RenderNode): Boolean =
    if (onThread) {
      caughtUp()
      false
    } else live(node.fx) || live(child.fx)

  /** Whether a call made here goes to the FX thread whatever it is made to: made off it, to what lives on that thread
    * whether it is drawn or not (a web page, a player). On the FX thread never, and what is queued is made first. */
  def offThread: Boolean =
    if (onThread) {
      caughtUp()
      false
    } else true

  /** `change` made on the FX thread, after every change queued before it. */
  def apply(change: () => Unit): Unit = {
    if (audit) HopSite.said("the FX thread")
    queue.add(change)
    if (posted.compareAndSet(false, true)) {
      // with no FX thread at all (a spec that never starts the toolkit), made here
      try javafx.application.Platform.runLater(() => drain())
      catch { case _: IllegalStateException => drain() }
    }
  }

  /** `change`, which puts `child` under a node in a window, made on the FX thread; until it is, `child` and all under
    * it count as in the window. */
  def joining(child: RenderNode, change: () => Unit): Unit = {
    joined(child.fx, 1)
    apply(() => try change() finally joined(child.fx, -1))
  }

  private def joined(node: javafx.scene.Node, by: Int): Unit = joining.synchronized {
    val now = Option(joining.get(node)).map(_.intValue).getOrElse(0) + by
    if (now <= 0) joining.remove(node) else joining.put(node, now)
    joiners = joining.size
  }

  // (what is joining read first: a node put in the window and let go of as joining between the two reads is then seen in
  // the window, the FX thread having put it there before it let go)
  private def live(node: javafx.scene.Node): Boolean = (joiners > 0 && joining.synchronized {
    var at = node
    while (at != null && !joining.containsKey(at)) at = at.getParent
    at != null
  }) || {
    val scene = node.getScene
    scene != null && scene.getWindow != null
  }

  private def caughtUp(): Unit = if (!draining && !queue.isEmpty) drain()

  // every change queued, in order, those queued meanwhile with them
  private def drain(): Unit = if (!draining) {
    draining = true
    posted.set(false)
    try {
      var next = queue.poll()
      while (next != null) {
        try next()
        catch { case t: Throwable => Platform.logError("A change made off the FX thread", t) }
        next = queue.poll()
      }
    } finally draining = false
  }
}
