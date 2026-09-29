package everyui.render

import javafx.animation.Timeline
import javafx.beans.value.ObservableValue
import javafx.scene.{Node, Scene}
import javafx.stage.Window

/** A node's motion (`Component.motion`): the timelines that move it, repeating, run only while the node is in a window
  * being shown and is shown itself, it and everything it is in visible.
  *
  * A running timeline keeps JavaFX pulsing, and so keeps the frame loop from idling, whether or not its node is drawn.
  * A spinner taken off the screen without being disposed of (a page's loading spinner removed once the page came)
  * went on turning a node in no scene for as long as the app ran, each one taken off adding another, and one inside a
  * box that was hidden, its own visibility untouched, went on too. Out of a shown window, or hidden (by its own
  * visibility or any box's it is in), the timelines are stopped; back in one and shown, they are made again (`make`)
  * from where the node is then. As Android's Motion does for its animators.
  *
  * A box shown or hidden (`RenderNode.setVisible`) looks again at the motions in a shown window
  * (`Motion.visibilityChanged`), which are few: nothing each pulse. */
final class Motion(node: Node, make: () => List[Timeline]) {
  private var timelines: List[Timeline] = Nil
  private var moving = false
  // whether the node is in a window being shown, followed through its scene and the scene's window
  private val showing: ObservableValue[java.lang.Boolean] = node.sceneProperty
    .flatMap[Window]((s: Scene) => s.windowProperty)
    .flatMap[java.lang.Boolean]((w: Window) => w.showingProperty)
    .orElse(java.lang.Boolean.FALSE)

  showing.addListener((_, _, _) => refresh())
  node.visibleProperty.addListener((_, _, _) => refresh())

  /** Whether the timelines run now. */
  def running: Boolean = timelines.nonEmpty

  /** The motion is set: made again, and run if the node can be seen. */
  def set(on: Boolean): Unit = {
    moving = on
    stop()
    refresh()
  }

  /** Run while it can be seen, and stop while it cannot. */
  def refresh(): Unit = {
    val inWindow = showing.getValue.booleanValue
    if (moving && inWindow) Motion.inWindow.add(this) else Motion.inWindow.remove(this)
    if (moving && inWindow && Motion.shown(node)) run() else stop()
  }

  private def run(): Unit = if (timelines.isEmpty) {
    timelines = make()
    timelines.foreach(_.play())
    if (timelines.nonEmpty) Motion.running += 1
  }

  private def stop(): Unit = if (timelines.nonEmpty) {
    timelines.foreach(_.stop())
    timelines = Nil
    Motion.running -= 1
  }
}

object Motion {
  /** How many nodes' motions are running now. */
  var running: Int = 0
  /** The motions moving in a shown window, which a box shown or hidden may start or stop. */
  private val inWindow: java.util.Set[Motion] =
    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap[Motion, java.lang.Boolean])

  /** Whether `node` and everything it is in are visible. */
  def shown(node: Node): Boolean = {
    var at = node
    while (at != null && at.isVisible) at = at.getParent
    at == null
  }

  /** A node was shown or hidden: each motion in a shown window looks again at whether it can be seen. */
  def visibilityChanged(): Unit = if (!inWindow.isEmpty) {
    val all = inWindow.toArray(new Array[Motion](0))
    all.foreach(_.refresh())
  }
}
