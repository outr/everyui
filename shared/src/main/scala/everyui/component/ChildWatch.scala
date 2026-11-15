package everyui.component

import reactify.reaction.{Reaction, ReactionStatus}

/** What a container follows of one of its children: its size, which the layout places by, and its place, which the
  * container's content size reaches to (Extent). One child changing then costs the container the same however many
  * children it has, where a Val over all of them walked every child again.
  *
  * The reach last counted for the child is kept here, so a change is counted from where the child really was, in
  * whatever order its changes arrive.
  *
  * It hears the child's size itself, and its place through `ChildPlace`: two reactions for the four values, where a
  * function attached to each cost a closure, its scope and a reaction wrapping it, for every child of every
  * container. */
private[component] class ChildWatch(val container: Container, val child: Component) extends Reaction[Double] {
  var x: Double = 0.0
  var y: Double = 0.0
  // which of the container's counts this was last counted in: a child listed twice has a watch for each, counted once
  var round: Int = 0

  private val left = child.position.left
  private val top = child.position.top
  private val width = child.size.width
  private val height = child.size.height
  private val place = new ChildPlace(this)
  width.reactions += this
  height.reactions += this
  left.reactions += place
  top.reactions += place

  /** Its width or its height changed. */
  override def apply(value: Double, previous: Option[Double]): ReactionStatus = {
    container.childWatched(this, true)
    ReactionStatus.Continue
  }

  // a child taking no room (`Component.roomless`) reaches nowhere
  def reachX: Double = if (child.roomless) 0.0 else left() + width()
  def reachY: Double = if (child.roomless) 0.0 else top() + height()

  def stop(): Unit = {
    width.reactions -= this
    height.reactions -= this
    left.reactions -= place
    top.reactions -= place
    ()
  }
}
