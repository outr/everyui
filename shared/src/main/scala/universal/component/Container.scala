package universal.component

import universal.component.feature.ContainerFeature
import universal.render.{Nodes, RenderNode}

class Container(node: RenderNode = Nodes.container()) extends Component(node):
  lazy val children: ContainerFeature = new ContainerFeature(this)

  override def kind: String = "Container"
  override def childComponents: Vector[Component] = children()
