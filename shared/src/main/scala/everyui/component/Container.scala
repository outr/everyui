package everyui.component

import everyui.component.feature.ContainerFeature
import everyui.layout.{Layout, LayoutPass}
import everyui.render.{Nodes, RenderNode}
import reactify.*

class Container(node: RenderNode = Nodes.container()) extends Component(node) {
  lazy val children: ContainerFeature = new ContainerFeature(this)

  override def dispose(): Unit = if (!disposed) {
    val held = children()
    children.clear()
    held.foreach(c => c.dispose())
    super.dispose()
  }

  /** youi's `LayoutSupport`: the layout, if any, places the children again whenever they, the container's size or
    * their sizes change, through the layout pass (`LayoutPass`: the deepest container first, in a loop). A container
    * given a layout sizes itself to its content, unless a size was set: its height always, its width when the layout
    * fits it (a column, a row; not a flow, which wraps to the width it is given). */
  lazy val layout: Var[Option[Layout]] = own(Var(None))
  /** Space between the edges and the children, on every side. */
  lazy val padding: Var[Double] = own(Var(0.0))
  /** Whether the width follows the children (a row or a column given no width): a mirrored row then spans its own run,
    * not the width its last placement made. */
  var widthFollowsContent: Boolean = false

  /** How far the children reach, plus the padding beyond them. */
  lazy val contentWidth: Val[Double] = {
    followReach()
    reachedWidth
  }
  lazy val contentHeight: Val[Double] = {
    followReach()
    reachedHeight
  }

  // How far the children reach, kept up as each one moves or changes size (ChildWatch, Extent): a Val over every child
  // walked them all again for each child laid out, which for a table of a few hundred rows kept a browser busy for
  // seconds.
  private lazy val reachedWidth: Var[Double] = own(Var(0.0))
  private lazy val reachedHeight: Var[Double] = own(Var(0.0))
  private val reachAcross: Extent = new Extent
  private val reachDown: Extent = new Extent
  private var reachFollowed: Boolean = false

  private def followReach(): Unit = if (!reachFollowed) {
    reachFollowed = true
    watchChildren()
    padding.attach(_ => setReach())
    recountReach()
  }

  private var reachRecounts: Int = 0

  /** Every child counted again (each watch once, though a child be listed twice). */
  private def recountReach(): Unit = {
    reachAcross.reset()
    reachDown.reset()
    reachRecounts += 1
    children().foreach { c =>
      c.containerWatches.foreach { w =>
        if ((w.container eq this) && w.round != reachRecounts) {
          w.round = reachRecounts
          countReach(w)
        }
      }
    }
    setReach()
  }

  private def countReach(w: ChildWatch): Unit = {
    w.x = w.reachX
    w.y = w.reachY
    reachAcross.add(w.x)
    reachDown.add(w.y)
  }

  private def setReach(): Unit = {
    reachedWidth @= reachAcross.value + padding()
    reachedHeight @= reachDown.value + padding()
  }

  /** A child's size (`sized`) or place changed: it is laid out again, and its reach counted where it now is. */
  private[component] def childWatched(w: ChildWatch, sized: Boolean): Unit = {
    if (sized && following) place()
    if (reachFollowed) {
      val x = w.reachX
      val y = w.reachY
      if (x != w.x || y != w.y) {
        val wasX = w.x
        val wasY = w.y
        w.x = x
        w.y = y
        if (reachAcross.moved(wasX, x) && reachDown.moved(wasY, y)) setReach() else recountReach()
      }
    }
  }

  // Whether the children are watched (ChildWatch): once there is a layout to follow their sizes, or a content size to
  // follow their reach.
  private var childrenWatched: Boolean = false

  private def watchChildren(): Unit = if (!childrenWatched) {
    childrenWatched = true
    children().foreach(watchChild)
  }

  private def watchChild(c: Component): Unit = c.containerWatches = new ChildWatch(this, c) :: c.containerWatches

  private def unwatchChild(c: Component): Unit = {
    c.containerWatches.foreach(w => if (w.container eq this) w.stop())
    c.containerWatches = c.containerWatches.filterNot(_.container eq this)
  }

  /** The children changed (ContainerFeature): what was added is watched, what left is not, and the reach follows. */
  override private[everyui] def childListChanged(added: Vector[Component], removed: Vector[Component]): Unit =
    if (childrenWatched) {
    removed.foreach(unwatchChild)
    added.foreach { c =>
      watchChild(c)
      if (reachFollowed) countReach(c.containerWatches.head)
    }
    if (reachFollowed) {
      if (removed.nonEmpty) recountReach() else setReach()
    }
  }

  private var exits: Map[everyui.event.Key, Option[Component]] = Map.empty

  /** An arrow `direction`'s way that finds nothing inside this container goes to `target` (onto its focusable part
    * nearest the focus): a header leading into a list, a panel handing back to a sidebar. */
  def exitFocus(direction: everyui.event.Key, target: Component): Unit = exits = exits + (direction -> Some(target))

  /** Arrows these ways stop at this container's edge instead of leaving it: a dialog, a keyboard, a menu. */
  def stopFocus(directions: everyui.event.Key*): Unit = directions.foreach(d => exits = exits + (d -> None))

  private var exitActions: Map[everyui.event.Key, () => Unit] = Map.empty

  /** An arrow `direction`'s way that finds nothing inside this container runs `action` instead of moving the focus:
    * a row of buttons that raises a panel when pressed on past, rather than handing off to whatever lies that way.
    * The arrow goes no further once it has run. */
  def exitAction(direction: everyui.event.Key, action: () => Unit): Unit =
    exitActions = exitActions + (direction -> action)

  /** Whatever this container said about an arrow leaving it `direction`'s way (`exitFocus`, `stopFocus`, `exitAction`)
    * taken back: its parent decides again. */
  def dropExit(direction: everyui.event.Key): Unit = {
    exits = exits - direction
    exitActions = exitActions - direction
  }

  /** What an arrow leaving this container `direction`'s way does instead of moving, when the container says. */
  def focusAction(direction: everyui.event.Key): Option[() => Unit] = exitActions.get(direction)

  /** Where an arrow leaving this container `direction`'s way goes: a component, `Some(None)` to stop, `None` when the
    * container does not say (its parent decides). */
  def focusExit(direction: everyui.event.Key): Option[Option[Component]] = exits.get(direction)

  /** The part of this container that had the focus last: where an arrow coming back into it lands, when it states no
    * entry. Kept by [[everyui.focus.Focus]]. */
  private[everyui] var remembered: Option[Component] = None

  private var arrival: Option[Component] = None

  /** An arrow arriving at this container from outside lands on `target` (onto its focusable part), wherever the focus
    * came from: a row of buttons that is entered at its first. */
  def entryFocus(target: Component): Unit = arrival = Some(target)

  /** Where an arrow arriving at this container lands, when the container says. */
  def focusEntry: Option[Component] = arrival

  /** Place the children again, here and all the way down, in one pass: the reading direction changed. */
  def relayout(): Unit = LayoutPass.relayout(this)

  /** Whether the layout pass has this container waiting to be laid out. */
  private[everyui] var layoutPending: Boolean = false
  /** Whether the layout is placing the children now. */
  private[everyui] var placing: Boolean = false

  /** Run the layout (the layout pass calls it): where the children go, now. */
  private[everyui] def placeNow(): Unit = if (!placing) {
    placing = true
    try layout().foreach(_.layout(this, children()))
    finally placing = false
  }

  private def place(): Unit = LayoutPass.request(this)
  override def requestLayout(): Unit = place()
  layout.attach { l =>
    l.foreach { lay =>
      follow()
      if (!size.height.explicit) {
        val content = contentHeight
        size.height := content()
      }
      if (lay.fitsWidth() && !size.width.explicit) {
        widthFollowsContent = true
        val content = contentWidth
        size.width := content()
      }
    }
    place()
  }
  // What moves the children is followed once there is a layout to move them: most containers never have one (a card, a
  // badge), and following every child's size for each of them is time a screen of them waits for on a Roku. (A change
  // of the children asks for the layout itself: ContainerFeature.)
  private var following: Boolean = false
  private def follow(): Unit = if (!following) {
    following = true
    padding.attach(_ => place())
    size.width.attach(_ => place())
    size.height.attach(_ => place())
    watchChildren()
  }

  override def kind: String = "Container"
  override def childComponents: Vector[Component] = children()
}

