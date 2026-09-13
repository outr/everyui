package everyui.component

import reactify.Var
import everyui.image.Image
import everyui.render.{Nodes, RenderNode}

class ImageView(node: RenderNode = Nodes.image("", 0.0, 0.0)) extends Component(node):
  lazy val image: Var[Image] =
    val v = Var[Image](Image.empty)
    v.attach { i =>
      node.setImageSource(i.source)
      size.width @= i.width
      size.height @= i.height
    }
    v

  override def kind: String = "ImageView"
