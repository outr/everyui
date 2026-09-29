package everyui.component

/** What a container follows of one of its children: its size, which the layout places by, and its place, which the
  * container's content size reaches to (Extent). One child changing then costs the container the same however many
  * children it has, where a Val over all of them walked every child again.
  *
  * The reach last counted for the child is kept here, so a change is counted from where the child really was, in
  * whatever order its changes arrive. */
private[component] class ChildWatch(val container: Container, val child: Component) {
  var x: Double = 0.0
  var y: Double = 0.0
  // which of the container's counts this was last counted in: a child listed twice has a watch for each, counted once
  var round: Int = 0

  private val left = child.position.left
  private val top = child.position.top
  private val width = child.size.width
  private val height = child.size.height
  private val onWidth = width.on(container.childWatched(this, true))
  private val onHeight = height.on(container.childWatched(this, true))
  private val onLeft = left.on(container.childWatched(this, false))
  private val onTop = top.on(container.childWatched(this, false))

  def reachX: Double = left() + width()
  def reachY: Double = top() + height()

  def stop(): Unit = {
    width.reactions -= onWidth
    height.reactions -= onHeight
    left.reactions -= onLeft
    top.reactions -= onTop
    ()
  }
}
