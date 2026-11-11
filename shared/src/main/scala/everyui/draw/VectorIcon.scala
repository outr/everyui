package everyui.draw

import everyui.render.Platform
import reactify.*

/** A monochrome mark (an icon, a provider's logo) `points` pixels square, drawn whole in `color`, as a
  * `MaterialIconView` is: bind the colour to the theme or to the words beside it and the mark follows. Its `art` is
  * given (`VectorArt.path` for path data), or read from an SVG file (`VectorIcon.load`).
  *
  * {{{
  *   val mark = VectorIcon(VectorArt.path("M12 2 2 22h20Z"), 14.0)
  *   mark.color := Theme.text()
  *   VectorIcon.load("/providers/acme.svg", 14.0)
  * }}}
  *
  * The web draws it as inline SVG, the desktop as JavaFX shapes and Android on a canvas, each crisp at any scale. A Roku
  * and an Apple TV draw no paths (`Drawing`): there a mark is left out (`VectorIcon.drawn` is false) rather than drawn
  * as the few rectangles they could show of it. */
class VectorIcon(points: Double) extends Drawing {
  /** What it draws; nothing until it is given, or until a file it was to read has come. */
  val art: Var[Option[VectorArt]] = own(Var(None))

  size.width @= points
  size.height @= points
  figures := art().map(_.figures(points, color())).getOrElse(Nil)

  override def kind: String = "VectorIcon"
}

object VectorIcon {
  /** Whether this platform draws a mark at all: the web, the desktop and Android do; a Roku and an Apple TV draw no
    * paths. */
  def drawn: Boolean = Platform.kind != everyui.PlatformKind.Roku && Platform.kind != everyui.PlatformKind.TvOS

  def apply(art: VectorArt, points: Double): VectorIcon = {
    val icon = new VectorIcon(points)
    icon.art @= Some(art)
    icon
  }

  /** A mark read from the SVG file at `url` (`VectorArt.parse` says what of one is drawn), empty until it comes, and
    * empty where it cannot be had (logged; the next mark of it tries again). On the web the page fetches it, so it is
    * its own origin's, or another's that allows it (CORS): a file the browser may not read is never drawn. Elsewhere it
    * is an http(s) URL, or a path of the app's own assets (`Platform.resolvePath`). A file is read once and kept for
    * every mark of it. */
  def load(url: String, points: Double): VectorIcon = {
    val icon = new VectorIcon(points)
    read(url, art => icon.art @= art)
    icon
  }

  // each file's art as it came, and those still waiting on one being read
  private var kept: Map[String, Option[VectorArt]] = Map.empty
  private var waiting: Map[String, List[Option[VectorArt] => Unit]] = Map.empty

  /** The art of the SVG file at `url`, to `got` once it is read (None where it could not be read, or holds no SVG). */
  def read(url: String, got: Option[VectorArt] => Unit): Unit = kept.get(url) match {
    case Some(art) => got(art)
    case None if waiting.contains(url) => waiting = waiting.updated(url, got :: waiting(url))
    case None =>
      waiting = waiting.updated(url, List(got))
      val web = Platform.kind == everyui.PlatformKind.Web
      val where = if (web || url.contains("://")) url else Platform.resolvePath(url)
      Platform.readText(where, text => {
        val art = if (text == null || text.isEmpty) None else VectorArt.parse(text)
        if (art.isEmpty) Platform.log("VectorIcon: no SVG could be read from " + everyui.log.LoggedAddress(url))
        if (art.nonEmpty) kept = kept.updated(url, art)
        val all = waiting.getOrElse(url, Nil)
        waiting = waiting - url
        all.reverse.foreach(f => f(art))
      })
  }
}
