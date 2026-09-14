package everyui.component

import everyui.component.feature.ContainerFeature
import everyui.layout.Layout
import everyui.render.{Nodes, RenderNode}
import reactify.*

class Container(node: RenderNode = Nodes.container()) extends Component(node) {
  lazy val children: ContainerFeature = new ContainerFeature(this)

  /** youi's `LayoutSupport`: the layout, if any, places the children again whenever they, the container's size
    * or their sizes change. */
  lazy val layout: Var[Option[Layout]] = Var(None)
  /** How far the children reach: bind `size.height := contentHeight` for a container that wraps its content. */
  lazy val contentWidth: Val[Double] = Val(extent(c => c.position.left() + c.size.width()))
  lazy val contentHeight: Val[Double] = Val(extent(c => c.position.top() + c.size.height()))

  /** The furthest any child reaches, by `edge`; 0 with no children. */
  private def extent(edge: Component => Double): Double = {
    var furthest = 0.0
    children().foreach(c => furthest = math.max(furthest, edge(c)))
    furthest
  }

  private var placing = false
  private val childSizes: Val[Double] = Val(children().map(c => c.size.width() + c.size.height()).sum)
  private def place(): Unit = if (!placing) {
    placing = true
    try layout().foreach(_.layout(this, children()))
    finally placing = false
  }
  layout.attach(_ => place())
  children.attach(_ => place())
  size.width.attach(_ => place())
  size.height.attach(_ => place())
  childSizes.attach(_ => place())

  override def kind: String = "Container"
  override def childComponents: Vector[Component] = children()
}
