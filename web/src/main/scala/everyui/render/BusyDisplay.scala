package everyui.render

import everyui.Color
import everyui.app.{Busy, BusyFrame}
import everyui.theme.Theme
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** The app at work (`everyui.app.AppBusy`), in the page's icon in its tab: a ring around the app's own icon, filled
  * as far as the progress where there is one, a turning arc where there is not. A browser does not animate an icon
  * itself (a GIF, an APNG or an SVG stands still in a tab), so each picture is drawn on a canvas and given to the
  * icon's link as a data URL. Once idle, every icon link the page had has its address back as it was, and a link made
  * for a page with none is gone. Where the page's icon cannot be drawn (it did not load, or it is from another origin
  * that does not allow it), the ring is drawn alone, and the console says why once; it says so too of an SVG icon
  * without a width and height of its own, which Chrome does not show in the tab. A progress that has stood still for `AppBusy.stillAfter` has a small arc going round it.
  *
  * The spinner has a clock of its own, not the frame loop: a worker's timer, which a browser does not slow for a tab
  * in the background the way it slows the page's own (to once a second, then once a minute), with the page's own
  * timer where a worker cannot be made. One tick is asked for at a time, and the picture is chosen by the time since
  * the spinner started, so a late tick shows the frame due and none are made up. A picture is drawn only when it
  * differs from the one shown. */
object BusyDisplay {
  /** The icon's side: a tab shows it at 16 pixels, twice that on a sharp screen. */
  private val side = 32
  private val line = 4.0
  // the worker's clock: each message asks for one tick that many milliseconds on
  private val clock = "let t;onmessage=e=>{clearTimeout(t);t=setTimeout(()=>postMessage(0),e.data)}"

  private var busy: Option[Busy] = None
  private var inTitle = false
  private var stillAfter: Option[Double] = None
  private var started = 0.0
  /** The ring's fill as last shown, and when it last changed. */
  private var fill: Option[Int] = None
  private var moved = 0.0
  /** Whether the console has been told why the page's icon is not drawn inside the ring. */
  private var warned = false
  private var shown: Option[BusyFrame] = None
  /** The page's icon links, each with its address and type as the page had them. */
  private var links: List[(html.Link, Option[String], Option[String])] = Nil
  /** The link made for a page that had none. */
  private var made: Option[html.Link] = None
  private var icon: Option[html.Image] = None
  private var prefix = ""
  private var pending = false
  private var workerFailed = false

  def supported(): Boolean = true

  def permitted(): Boolean = true

  def permit(done: Boolean => Unit): Unit = done(true)

  def show(next: Option[Busy], title: Boolean, still: Option[Double]): Unit = {
    val now = dom.window.performance.now()
    if (!BusyFrame.moving(busy) && BusyFrame.moving(next)) started = now
    if (BusyFrame.fill(next) != fill) {
      fill = BusyFrame.fill(next)
      moved = now
    }
    busy = next
    inTitle = title
    stillAfter = still
    if (next.isEmpty) restore()
    else {
      if (links.isEmpty && made.isEmpty) take()
      draw()
      if (BusyFrame.moving(next, stillAfter)) schedule()
    }
    retitle()
  }

  private def ticked(): Unit = {
    pending = false
    if (BusyFrame.moving(busy, stillAfter)) {
      draw()
      schedule()
    }
  }

  /** Asks for the next tick: a picture's time on while a spinner or an orbit turns, or not until the progress will
    * have stood still long enough for its orbit to begin. */
  private def schedule(): Unit = if (!pending) {
    pending = true
    val frame = 1000.0 / BusyFrame.perSecond
    val due = stillAfter.filter(_ => fill.nonEmpty).map(a => moved + a * 1000.0 - dom.window.performance.now())
    val delay = due.filter(_ > frame).getOrElse(frame)
    worker match {
      case Some(w) if !workerFailed => w.postMessage(delay)
      case _ => dom.window.setTimeout(() => ticked(), delay)
    }
  }

  private lazy val worker: Option[dom.Worker] = try {
    val script = new dom.Blob(js.Array[dom.BlobPart](clock), new dom.BlobPropertyBag { `type` = "text/javascript" })
    val w = new dom.Worker(dom.URL.createObjectURL(script))
    w.onmessage = (_: dom.MessageEvent) => ticked()
    // a page whose policy refuses a worker made from a blob: the page's own timer from then on
    w.addEventListener("error", (_: dom.Event) => {
      workerFailed = true
      pending = false
      if (BusyFrame.moving(busy)) schedule()
    })
    Some(w)
  } catch {
    case _: Throwable => None
  }

  private def draw(): Unit = {
    val now = dom.window.performance.now()
    BusyFrame.at(busy, (now - started) / 1000.0, (now - moved) / 1000.0, stillAfter).foreach(put)
  }

  private def put(frame: BusyFrame): Unit = {
    if (!shown.contains(frame)) picture(frame).foreach { url =>
      shown = Some(frame)
      (links.map(_._1) ++ made.toList).foreach { link =>
        link.setAttribute("type", "image/png")
        link.setAttribute("href", url)
      }
    }
  }

  /** The frame as a PNG's data URL. An icon from another origin that did not allow it leaves the canvas unreadable:
    * the rings are then drawn alone, and the console says why once. */
  private def picture(frame: BusyFrame): Option[String] = {
    val canvas = dom.document.createElement("canvas").asInstanceOf[html.Canvas]
    canvas.width = side
    canvas.height = side
    val g = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    val centre = side / 2.0
    val radius = (side - line) / 2.0
    icon.foreach { image =>
      val inset = line + 1.0
      g.drawImage(image, inset, inset, side - 2.0 * inset, side - 2.0 * inset)
    }
    g.lineWidth = line
    g.strokeStyle = "rgba(128, 128, 128, 0.4)"
    g.beginPath()
    g.arc(centre, centre, radius, 0.0, 2.0 * math.Pi)
    g.stroke()
    if (frame.sweep > 0.0) {
      val from = (frame.start - 0.25) * 2.0 * math.Pi
      g.strokeStyle = tone()
      g.lineCap = if (frame.filled.isEmpty) "round" else "butt"
      g.beginPath()
      g.arc(centre, centre, radius, from, from + frame.sweep * 2.0 * math.Pi)
      g.stroke()
    }
    // the orbit round a progress that stands still: a pale light over the fill, the accent half seen over the empty
    // ring (so it is not taken for more fill)
    val accent = tone()
    frame.orbitPieces.foreach { piece =>
      val from = (piece._1 - 0.25) * 2.0 * math.Pi
      g.strokeStyle = if (piece._3) "rgba(255, 255, 255, 0.75)" else accent
      g.globalAlpha = if (piece._3) 1.0 else 0.5
      g.lineCap = "butt"
      g.beginPath()
      g.arc(centre, centre, radius, from, from + piece._2 * 2.0 * math.Pi)
      g.stroke()
    }
    g.globalAlpha = 1.0
    try Some(canvas.toDataURL("image/png")) catch {
      case _: Throwable if icon.nonEmpty =>
        warn("it is from another origin that did not allow it (CORS), which leaves the canvas unreadable")
        icon = None
        picture(frame)
      case _: Throwable => None
    }
  }

  /** Says once in the console why the page's icon is not inside the ring (`ring`), or not right, so a ring around
    * nothing points at the page's markup. */
  private def warn(why: String, ring: Boolean = true): Unit = if (!warned) {
    warned = true
    dom.console.warn(if (ring) s"[everyui] AppBusy draws its ring without the page's icon: $why" else s"[everyui] AppBusy: $why")
  }

  /** The theme's accent as a tone that reads on a light tab strip and a dark one alike: a pale accent made for a dark
    * theme is deepened, a grey one kept grey, mid-tone. */
  private def tone(): String = {
    val accent = Theme.accent()
    if (accent.saturation < 0.1) Color.fromHSB(0.0, 0.0, 0.55).toHex
    else Color.fromHSB(accent.hue, math.max(accent.saturation, 0.75), 0.9).toHex
  }

  /** The page's icon links, remembered as they are, or one made where there are none; and the app's icon, loaded to
    * draw inside the ring. */
  private def take(): Unit = {
    val found = dom.document.querySelectorAll("link[rel~='icon']")
    links = (0 until found.length).toList.map(i => found(i).asInstanceOf[html.Link])
      .map(l => (l, Option(l.getAttribute("href")), Option(l.getAttribute("type"))))
    if (links.isEmpty) {
      val link = dom.document.createElement("link").asInstanceOf[html.Link]
      link.rel = "icon"
      dom.document.head.appendChild(link)
      made = Some(link)
    }
    links.headOption.filter(_._2.nonEmpty).foreach { case (link, _, kind) =>
      val image = dom.document.createElement("img").asInstanceOf[html.Image]
      image.setAttribute("crossorigin", "anonymous")
      val href = link.href
      image.onload = (_: dom.Event) =>
        if (image.naturalWidth == 0 || image.naturalHeight == 0) warn(s"$href has no size to draw at$unsized")
        else {
          if (svg(href, kind)) sized(href)
          if (busy.nonEmpty) {
            icon = Some(image)
            shown = None
            draw()
          }
        }
      image.addEventListener("error", (_: dom.Event) =>
        if (new dom.URL(href).origin != dom.window.location.origin)
          warn(s"$href did not load; from another origin it must allow this page (CORS: Access-Control-Allow-Origin)")
        else warn(s"$href did not load"))
      image.src = href
    }
  }

  private val unsized = " (an SVG icon needs width and height on its <svg>, not only a viewBox)"

  /** Whether the icon at `href`, of the link's `kind` as the page had it, is an SVG. */
  private def svg(href: String, kind: Option[String]): Boolean =
    kind.contains("image/svg+xml") || href.split('?').head.endsWith(".svg") || href.startsWith("data:image/svg")

  /** An SVG icon whose <svg> has no width and height of its own is drawn inside the ring (at a size the browser makes
    * up), but Chrome shows no icon for it in the tab at all: said once, since the page looks broken while idle. */
  private def sized(href: String): Unit =
    dom.fetch(href).`then`[String](_.text()).`then`[Unit] { (text: String) =>
      val root = new dom.DOMParser().parseFromString(text, dom.MIMEType.`image/svg+xml`).documentElement
      if (root.localName == "svg" && !(root.hasAttribute("width") && root.hasAttribute("height")))
        warn(s"$href has no width and height of its own, so Chrome shows no icon for it in the tab$unsized", ring = false)
    }

  private def restore(): Unit = {
    links.foreach { case (link, href, kind) =>
      href.fold(link.removeAttribute("href"))(link.setAttribute("href", _))
      kind.fold(link.removeAttribute("type"))(link.setAttribute("type", _))
    }
    made.foreach(link => if (link.parentNode != null) link.parentNode.removeChild(link))
    links = Nil
    made = None
    icon = None
    shown = None
  }

  private def retitle(): Unit = {
    val base = Busy.untitled(dom.document.title, prefix)
    prefix = if (inTitle) Busy.titlePrefix(busy) else ""
    if (dom.document.title != prefix + base) dom.document.title = prefix + base
  }
}
