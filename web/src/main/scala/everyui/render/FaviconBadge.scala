package everyui.render

import everyui.push.Badge
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** A count on the tab's icon (`Platform.setBadge`), as a mail app shows its unread: the page's own icon drawn on a
  * canvas with a red circle and the count over its corner ("9+" over 9), put in place of the page's icon links, which
  * come back as they were, the same elements in the same places, at 0. An icon the canvas may not read (another
  * origin that does not allow it) or none at all gives a red circle with the count alone. An installed app's badge
  * (`navigator.setAppBadge`) is set as well, where the browser has one. */
object FaviconBadge {
  private val size = 64
  /** The page's icon links taken out, each with the node it stood before, while a count is shown. */
  private var originals: List[(dom.Element, dom.Node)] = Nil
  private var badged: Option[dom.Element] = None
  /** The page's icon once drawn from (None: it could not be read, so the circle stands alone), and the latest count. */
  private var base: Option[Option[dom.html.Image]] = None
  private var loading = false
  private var count = 0

  def set(n: Int): Unit = {
    count = math.max(0, n)
    appBadge()
    if (count == 0) restore()
    else base match {
      case Some(image) => draw(image)
      case None => if (!loading) load()
    }
  }

  private def appBadge(): Unit = {
    val navigator = dom.window.navigator.asInstanceOf[js.Dynamic]
    val call = if (count > 0) navigator.setAppBadge else navigator.clearAppBadge
    if (js.typeOf(call) == "function") {
      val result = if (count > 0) navigator.setAppBadge(count) else navigator.clearAppBadge()
      if (result != null && !js.isUndefined(result)) result.asInstanceOf[js.Promise[Any]].`catch`[Any]((_: Any) => ())
    }
  }

  private def links(): List[dom.Element] = {
    val found = dom.document.head.querySelectorAll("link[rel~='icon' i]")
    (0 until found.length).map(found(_)).filterNot(e => badged.contains(e)).toList
  }

  /** The icon to draw from: an SVG one where the page has one (it draws sharp at any size), else the first. */
  private def source(): Option[String] = {
    val icons = (if (originals.nonEmpty) originals.map(_._1) else links()).map(_.asInstanceOf[html.Link])
    icons.find(l => Option(l.getAttribute("type")).contains("image/svg+xml") || l.href.toLowerCase.split('?').head.endsWith(".svg"))
      .orElse(icons.headOption).map(_.href)
  }

  private def load(): Unit = source() match {
    case None => base = Some(None); draw(None)
    case Some(url) =>
      loading = true
      val image = dom.document.createElement("img").asInstanceOf[html.Image]
      if (!url.startsWith("data:")) image.setAttribute("crossorigin", "anonymous")
      val done = (found: Option[html.Image]) => {
        loading = false
        base = Some(found)
        if (count > 0) draw(found)
      }
      image.addEventListener("load", (_: dom.Event) => done(Some(image)))
      image.addEventListener("error", (_: dom.Event) => done(None))
      image.src = url
  }

  private def draw(image: Option[html.Image]): Unit = {
    val canvas = dom.document.createElement("canvas").asInstanceOf[html.Canvas]
    canvas.width = size
    canvas.height = size
    val g = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    val drawn = image.exists { i =>
      try { g.drawImage(i, 0, 0, size, size); true } catch { case _: Throwable => false }
    }
    val url = try picture(g, canvas, drawn) catch {
      case _: Throwable =>
        g.clearRect(0, 0, size, size)
        picture(g, canvas, drawn = false)
    }
    show(url)
  }

  /** The circle and the count: over the icon's top-right corner, or filling the picture where there is no icon. */
  private def picture(g: dom.CanvasRenderingContext2D, canvas: html.Canvas, drawn: Boolean): String = {
    val text = Badge.text(count)
    val radius = if (drawn) size * 0.3 else size / 2.0
    val (cx, cy) = if (drawn) (size - radius, radius) else (size / 2.0, size / 2.0)
    g.beginPath()
    g.arc(cx, cy, radius, 0, 2 * math.Pi)
    g.fillStyle = "#d93025"
    g.fill()
    if (drawn) {
      g.lineWidth = 3
      g.strokeStyle = "#ffffff"
      g.stroke()
    }
    g.fillStyle = "#ffffff"
    val fontSize = radius * (if (text.length > 1) 1.15 else 1.45)
    g.font = s"bold ${fontSize.round}px system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif"
    g.textAlign = "center"
    g.textBaseline = "middle"
    g.fillText(text, cx, cy + radius * 0.06, radius * 1.8)
    canvas.toDataURL("image/png")
  }

  /** The page's icon links out (once) and one link with `url` in their place. */
  private def show(url: String): Unit = {
    if (originals.isEmpty) originals = links().map(l => (l, l.nextSibling))
    originals.foreach { case (l, _) => if (l.parentNode != null) l.parentNode.removeChild(l) }
    val link = badged.getOrElse {
      val made = dom.document.createElement("link")
      made.setAttribute("rel", "icon")
      made.setAttribute("type", "image/png")
      made.setAttribute("data-everyui-badge", "")
      badged = Some(made)
      made
    }
    link.setAttribute("href", url)
    if (link.parentNode == null) dom.document.head.appendChild(link)
  }

  /** The page's icon links back where they stood, and the drawn one gone. */
  private def restore(): Unit = {
    badged.foreach(l => if (l.parentNode != null) l.parentNode.removeChild(l))
    badged = None
    originals.reverse.foreach { case (l, next) =>
      if (next != null && next.parentNode == dom.document.head) dom.document.head.insertBefore(l, next)
      else dom.document.head.appendChild(l)
    }
    originals = Nil
    base = None
  }
}
