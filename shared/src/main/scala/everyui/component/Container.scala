package everyui.component

import everyui.component.feature.ContainerFeature
import everyui.render.{Nodes, RenderNode}

class Container(node: RenderNode = Nodes.container()) extends Component(node):
  lazy val children: ContainerFeature = new ContainerFeature(this)

  override def kind: String = "Container"
  override def childComponents: Vector[Component] = children()
