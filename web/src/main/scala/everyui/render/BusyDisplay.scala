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
  * for a page with none is gone.
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
  private var started = 0.0
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

  def show(next: Option[Busy], title: Boolean): Unit = {
    if (!BusyFrame.moving(busy) && BusyFrame.moving(next)) started = dom.window.performance.now()
    busy = next
    inTitle = title
    if (next.isEmpty) restore()
    else {
      if (links.isEmpty && made.isEmpty) take()
      draw()
      if (BusyFrame.moving(next)) schedule()
    }
    retitle()
  }

  private def ticked(): Unit = {
    pending = false
    if (BusyFrame.moving(busy)) {
      draw()
      schedule()
    }
  }

  private def schedule(): Unit = if (!pending) {
    pending = true
    val delay = 1000.0 / BusyFrame.perSecond
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

  private def draw(): Unit = BusyFrame.at(busy, (dom.window.performance.now() - started) / 1000.0).foreach { frame =>
    if (!shown.contains(frame)) picture(frame).foreach { url =>
      shown = Some(frame)
      (links.map(_._1) ++ made.toList).foreach { link =>
        link.setAttribute("type", "image/png")
        link.setAttribute("href", url)
      }
    }
  }

  /** The frame as a PNG's data URL. An icon from another origin that did not allow it leaves the canvas unreadable:
    * the rings are then drawn alone. */
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
    try Some(canvas.toDataURL("image/png")) catch {
      case _: Throwable if icon.nonEmpty =>
        icon = None
        picture(frame)
      case _: Throwable => None
    }
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
    links.headOption.filter(_._2.nonEmpty).foreach { case (link, _, _) =>
      val image = dom.document.createElement("img").asInstanceOf[html.Image]
      image.setAttribute("crossorigin", "anonymous")
      image.onload = (_: dom.Event) => if (busy.nonEmpty) {
        icon = Some(image)
        shown = None
        draw()
      }
      image.src = link.href
    }
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
