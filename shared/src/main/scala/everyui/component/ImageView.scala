package everyui.component

import reactify.Var
import everyui.component.types.Prop
import everyui.file.Files
import everyui.image.Image
import everyui.render.{Nodes, Platform, RenderNode}

class ImageView(node: RenderNode = Nodes.image("", 0.0, 0.0)) extends Component(node) {
  lazy val image: Var[Image] = {
    val v = Var[Image](Image.empty)
    v.attach { i =>
      // the source made for it from bytes let go, as another picture takes its place
      if (made.nonEmpty && i.source != made) {
        Files.release(made)
        made = ""
      }
      if (holding()) node.setImageSource(i.source)
      if (sizedByPicture()) {
        size.width @= i.width
        size.height @= i.height
      }
    }
    own(v)
  }

  /** Whether it takes its picture's own size as the picture is set: on by default. Off for a view the app sizes itself
    * (an avatar, a picture fitted to a box), which keeps its size whatever comes, and never, as a picture that came in
    * the middle of another change once was, drawn at the picture's own size after the app had set its own. */
  lazy val sizedByPicture: Var[Boolean] = own(Var(true))

  /** Whether the picture is drawn at all. A view out of sight lets its picture go (a rail scrolled far past keeps a
    * poster apiece otherwise, which on a television is most of the memory the app takes, and a tab not shown its
    * every picture) and takes it up again as it comes back: what it shows is remembered either way. */
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

  // the source made for it from bytes (`showBytes`), and one still loading
  private var made = ""
  private var waiting = ""

  /** `bytes`, a picture of type `mime`, shown once it has loaded, from a source made for this view alone
    * (`Files.source`: an object URL on the web, a file elsewhere): it is let go (`Files.release`) as another picture
    * takes its place or the view is disposed of, so the app has none to release. Nothing shows where the platform
    * shows nothing from bytes (a Roku, tvOS). */
  def showBytes(bytes: Array[Byte], mime: String): Unit = {
    letWaitingGo()
    val source = Files.source(bytes, mime)
    if (source.nonEmpty) {
      waiting = source
      Platform.loadImage(source, (w, h) => if (waiting == source) {
        waiting = ""
        image @= new Image(source, w, h)
        made = source
      })
    }
  }

  private def letWaitingGo(): Unit = if (waiting.nonEmpty) {
    Files.release(waiting)
    waiting = ""
  }

  /** Its picture let go too, in case something that lives on still holds the view, and the source made for it. */
  override def dispose(): Unit = {
    holding @= false
    letWaitingGo()
    if (made.nonEmpty) {
      Files.release(made)
      made = ""
    }
    super.dispose()
  }

  override def kind: String = "ImageView"
}

object ImageView {
  /** Whether what is under `c` (and `c` itself) draws its pictures: off while it is out of sight, so a rail scrolled
    * away keeps no poster and a tab not shown no picture, and on again as it comes back, but for a card still out of
    * its rail's view, which its rail turns on as it comes into it (`ScrollContainer`). What drops a picture is the
    * platform's: the page's `img` its `src`, JavaFX's and Android's view their bitmap (held on only by the platform's
    * cache of pictures, which lets the longest unwanted go), a Roku's Poster and tvOS's view their texture. Walked with
    * a list rather than by recursion (a Roku's stack is small). */
  def showing(c: Component, on: Boolean): Unit = {
    var pending: List[Component] = List(c)
    while (pending.nonEmpty) {
      val at = pending.head
      pending = pending.tail
      val scrolledAway = on && !(at eq c) && at.parent().flatMap(_.parent()).exists {
        case s: ScrollContainer => s.outOfView(at)
        case _ => false
      }
      if (!scrolledAway) {
        pending = at.childComponents.toList ::: pending
        at match {
          case i: ImageView => i.holding @= on
          case _ => ()
        }
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
