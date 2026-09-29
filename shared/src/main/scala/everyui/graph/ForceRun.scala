package everyui.graph

import everyui.task.{Animation, Conclusion}

/** A force layout run on the app's frames (`ForceLayout.animate`): a tick a frame while it is warm, and done once it
  * settles, which lets the frames stop. */
private[graph] class ForceRun(layout: ForceLayout) extends Animation {
  override def update(delta: Double, reset: Boolean): Conclusion =
    if (layout.frame()) Conclusion.Continue else Conclusion.Finished
}
