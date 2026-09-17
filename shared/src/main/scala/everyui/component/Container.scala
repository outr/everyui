package everyui.component

import everyui.component.feature.ContainerFeature
import everyui.layout.{Layout, LayoutPass}
import everyui.render.{Nodes, RenderNode}
import reactify.*

class Container(node: RenderNode = Nodes.container()) extends Component(node) {
  lazy val children: ContainerFeature = new ContainerFeature(this)

  /** youi's `LayoutSupport`: the layout, if any, places the children again whenever they, the container's size or
    * their sizes change, through the layout pass (`LayoutPass`: the deepest container first, in a loop). A container
    * given a layout sizes itself to its content, unless a size was set: its height always, its width when the layout
    * fits it (a column, a row; not a flow, which wraps to the width it is given). */
  lazy val layout: Var[Option[Layout]] = Var(None)
  /** Space between the edges and the children, on every side. */
  lazy val padding: Var[Double] = Var(0.0)
  /** Whether the width follows the children (a row or a column given no width): a mirrored row then spans its own run,
    * not the width its last placement made. */
  var widthFollowsContent: Boolean = false

  /** How far the children reach, plus the padding beyond them. */
  lazy val contentWidth: Val[Double] = Val(extent(c => c.position.left() + c.size.width()) + padding())
  lazy val contentHeight: Val[Double] = Val(extent(c => c.position.top() + c.size.height()) + padding())

  /** The furthest any child reaches, by `edge`; 0 with no children. */
  private def extent(edge: Component => Double): Double = {
    var furthest = 0.0
    children().foreach(c => furthest = math.max(furthest, edge(c)))
    furthest
  }

  private lazy val childSizes: Val[Double] = Val(children().map(c => c.size.width() + c.size.height()).sum)
  private var exits: Map[everyui.event.Key, Option[Component]] = Map.empty

  /** An arrow `direction`'s way that finds nothing inside this container goes to `target` (onto its focusable part
    * nearest the focus): a header leading into a list, a panel handing back to a sidebar. */
  def exitFocus(direction: everyui.event.Key, target: Component): Unit = exits = exits + (direction -> Some(target))

  /** Arrows these ways stop at this container's edge instead of leaving it: a dialog, a keyboard, a menu. */
  def stopFocus(directions: everyui.event.Key*): Unit = directions.foreach(d => exits = exits + (d -> None))

  /** Where an arrow leaving this container `direction`'s way goes: a component, `Some(None)` to stop, `None` when the
    * container does not say (its parent decides). */
  def focusExit(direction: everyui.event.Key): Option[Option[Component]] = exits.get(direction)

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
      if (!size.height.explicit) size.height := contentHeight()
      if (lay.fitsWidth() && !size.width.explicit) {
        widthFollowsContent = true
        size.width := contentWidth()
      }
    }
    place()
  }
  // What moves the children is followed once there is a layout to move them: most containers never have one (a card, a
  // badge), and following every child's size for each of them is time a screen of them waits for on a Roku.
  private var following: Boolean = false
  private def follow(): Unit = if (!following) {
    following = true
    padding.attach(_ => place())
    children.attach(_ => place())
    size.width.attach(_ => place())
    size.height.attach(_ => place())
    childSizes.attach(_ => place())
  }

  override def kind: String = "Container"
  override def childComponents: Vector[Component] = children()
}

