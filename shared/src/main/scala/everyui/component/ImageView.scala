package everyui.component

import reactify.Var
import everyui.component.types.Prop
import everyui.image.Image
import everyui.render.{Nodes, RenderNode}

class ImageView(node: RenderNode = Nodes.image("", 0.0, 0.0)) extends Component(node) {
  lazy val image: Var[Image] = {
    val v = Var[Image](Image.empty)
    v.attach { i =>
      node.setImageSource(i.source)
      size.width @= i.width
      size.height @= i.height
    }
    v
  }

  /** The picture in one colour, its transparency kept: a white sprite in the theme's accent, say. None draws it as it
    * is. */
  lazy val tint: Prop[Option[everyui.Color]] =
    new Prop[Option[everyui.Color]](None, t => node.setTint(t.map(c => c.toHex).getOrElse("")))

  override def kind: String = "ImageView"
}

object ImageView {
  /** The image at `source` (a site-absolute path or a URL), scaled to `height` pixels tall at its own proportions once
    * it has loaded: a logo, say. */
  def apply(source: String, height: Double): ImageView = {
    val view = new ImageView
    everyui.image.Image(source).map { loaded =>
      view.image @= loaded
      view.size.width @= (if (loaded.height > 0.0) loaded.width * height / loaded.height else 0.0)
      view.size.height @= height
    }.startUnit()
    view
  }
}
