package everyui.render

import javafx.animation.Timeline
import javafx.beans.value.ObservableValue
import javafx.scene.{Node, Scene}
import javafx.stage.Window

/** A node's motion (`Component.motion`): the timelines that move it, repeating, run only while the node is in a window
  * being shown and is not hidden by its own visibility.
  *
  * A running timeline keeps JavaFX pulsing, and so keeps the frame loop from idling, whether or not its node is drawn.
  * A spinner taken off the screen without being disposed of (a page's loading spinner removed once the page came)
  * went on turning a node in no scene for as long as the app ran, each one taken off adding another. Out of a shown
  * window, or hidden, the timelines are stopped; back in one and shown, they are made again (`make`) from where the node
  * is then. As Android's Motion does for its animators. */
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

  private def refresh(): Unit =
    if (moving && node.isVisible && showing.getValue.booleanValue) run() else stop()

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
}
