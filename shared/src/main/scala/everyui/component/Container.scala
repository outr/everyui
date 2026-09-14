package everyui.component

import everyui.component.feature.ContainerFeature
import everyui.layout.Layout
import everyui.render.{Nodes, RenderNode}
import reactify.*

class Container(node: RenderNode = Nodes.container()) extends Component(node) {
  lazy val children: ContainerFeature = new ContainerFeature(this)

  /** youi's `LayoutSupport`: the layout, if any, places the children again whenever they, the container's size or
    * their sizes change. A container given a layout sizes itself to its content, unless a size was set: its height
    * always, its width when the layout fits it (a column, a row; not a flow, which wraps to the width it is given). */
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

  private var placing = false
  private val childSizes: Val[Double] = Val(children().map(c => c.size.width() + c.size.height()).sum)
  /** Place the children again, here and all the way down: the reading direction changed. */
  def relayout(): Unit = {
    place()
    children().foreach {
      case c: Container => c.relayout()
      case _ => ()
    }
  }

  private def place(): Unit = if (!placing) {
    placing = true
    try layout().foreach(_.layout(this, children()))
    finally placing = false
  }
  layout.attach { l =>
    l.foreach { lay =>
      if (!size.height.explicit) size.height := contentHeight()
      if (lay.fitsWidth() && !size.width.explicit) {
        widthFollowsContent = true
        size.width := contentWidth()
      }
    }
    place()
  }
  padding.attach(_ => place())
  children.attach(_ => place())
  size.width.attach(_ => place())
  size.height.attach(_ => place())
  childSizes.attach(_ => place())

  override def kind: String = "Container"
  override def childComponents: Vector[Component] = children()
}

