package everyui.component

import reactify.Var
import everyui.component.types.Prop
import everyui.image.Image
import everyui.render.{Nodes, RenderNode}

class ImageView(node: RenderNode = Nodes.image("", 0.0, 0.0)) extends Component(node) {
  lazy val image: Var[Image] = {
    val v = Var[Image](Image.empty)
    v.attach { i =>
      if (holding()) node.setImageSource(i.source)
      if (sizedByPicture()) {
        size.width @= i.width
        size.height @= i.height
      }
    }
    v
  }

  /** Whether it takes its picture's own size as the picture is set: on by default. Off for a view the app sizes itself
    * (an avatar, a picture fitted to a box), which keeps its size whatever comes, and never, as a picture that came in
    * the middle of another change once was, drawn at the picture's own size after the app had set its own. */
  lazy val sizedByPicture: Var[Boolean] = own(Var(true))

  /** Whether the picture is drawn at all. A view out of sight lets its picture go (a rail scrolled far past keeps a
    * poster apiece otherwise, which on a television is most of the memory the app takes) and takes it up again as it
    * comes back: what it shows is remembered either way. */
  private[everyui] lazy val holding: Var[Boolean] = {
    val v = Var(true)
    v.attach(on => node.setImageSource(if (on) image().source else ""))
    v
  }

  /** What the picture shows, in words, for whatever cannot see it: a screen reader, a search engine. Empty says it is
    * only decoration. */
  lazy val alt: Prop[String] = own(new Prop[String]("", a => node.setAlt(a)))

  /** The picture in one colour, its transparency kept: a white sprite in the theme's accent, say. None draws it as it
    * is. */
  lazy val tint: Prop[Option[everyui.Color]] =
    own(new Prop[Option[everyui.Color]](None, t => node.setTint(t.map(c => c.toHex).getOrElse(""))))

  /** Its picture let go too, in case something that lives on still holds the view. */
  override def dispose(): Unit = {
    holding @= false
    super.dispose()
  }

  override def kind: String = "ImageView"
}

object ImageView {
  /** Whether what is under `c` (and `c` itself) draws its pictures: off while it is out of sight, so a rail scrolled
    * away keeps no poster, and on again as it comes back. Walked with a list rather than by recursion (a Roku's stack
    * is small). */
  def showing(c: Component, on: Boolean): Unit = {
    var pending: List[Component] = List(c)
    while (pending.nonEmpty) {
      val at = pending.head
      pending = at.childComponents.toList ::: pending.tail
      at match {
        case i: ImageView => i.holding @= on
        case _ => ()
      }
    }
  }

  /** The image at `source` (a site-absolute path or a URL), scaled to `height` pixels tall at its own proportions once
    * it has loaded: a logo, say. */
  def apply(source: String, height: Double): ImageView = {
    val view = new ImageView
    view.sizedByPicture @= false
    everyui.image.Image(source).map { loaded =>
      view.image @= loaded
      view.size.width @= (if (loaded.height > 0.0) loaded.width * height / loaded.height else 0.0)
      view.size.height @= height
    }.startUnit()
    view
  }
}
