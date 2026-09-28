package everyui.render

import org.scalajs.dom
import org.scalajs.dom.html
import everyui.font.{Face, Faces}
import everyui.PlatformKind
import everyui.event.PointerKind

import scala.scalajs.js

/** **The one invention.** The abstract render node, DOM edition.
  *
  * Signature-for-signature the same as its Roku, Android and JavaFX twins
  * (`roku/`, `android/`, `desktop/` …/everyui/render/RenderNode.scala) — that parallel IS the seam.
  * The shared `Component`/`Label` name only this type, and each backend's
  * source root supplies it at compile time.
  *
  * This backend is REAL Scala.js over the live DOM, not a lighter emission: the
  * web tier's whole argument (DESIGN.md) is that the browser's own semantics —
  * autofill, IME, text selection, find-in-page, the a11y tree — are worth
  * keeping, and that argument only holds if the web backend is genuinely the
  * DOM. It is also why the web project builds with scala-cli rather than sbt:
  * Scala.js has no sbt 2 plugin.
  */
class RenderNode(val el: html.Element) {
  def getText(): String = el.textContent
  def setText(text: String): Unit = el.textContent = text

  def getX(): Double = px(el.style.left)
  def setX(x: Double): Unit = el.style.left = x.toString + "px"

  def getY(): Double = px(el.style.top)
  def setY(y: Double): Unit = el.style.top = y.toString + "px"

  /** Radians, clockwise, about the node's own centre — the DOM's `rotate()`
    * is already centre-pivoted by default (`transform-origin: 50% 50%`). */
  def getRotation(): Double = rotation
  def setRotation(radians: Double): Unit = {
    rotation = radians
    transform()
  }

  /** Drawn larger or smaller about its own centre, which is what the browser scales about by default. */
  def setScale(factor: Double): Unit = {
    scaled = factor
    transform()
  }

  private var scaled: Double = 1.0
  private def transform(): Unit =
    el.style.transform = "rotate(" + rotation.toString + "rad) scale(" + scaled.toString + ")"

  def setFontSize(size: Double): Unit = el.style.fontSize = size.toString + "px"

  /** `"#rrggbb"`. */
  def setColor(hex: String): Unit = {
    textColor = hex
    if (!textFilled) el.style.color = hex
  }

  /** A bundled face (index.html's `@font-face` rules): family, CSS weight,
    * size in pixels. The line box is the shared one (everyui.font.Face), not
    * the browser's `line-height: normal`, so text is the same height on every
    * backend. */
  def setFont(family: String, weight: Int, size: Double): Unit = {
    if (family.nonEmpty) el.style.fontFamily = RenderNode.families(family)
    el.style.fontWeight = weight.toString
    el.style.fontSize = size.toString + "px"
    el.style.lineHeight = Faces(family, weight).lineHeight(size).toString + "px"
  }

  /** The laid-out size in pixels (text is measured with its real font), shown or not: a component keeps its size out
    * of sight, as it does on every other backend. */
  def getWidth(): Double = box().width
  def getHeight(): Double = box().height

  // Under `display: none` a node has no box and reads 0 by 0: a button made in a hidden panel was an empty pill once
  // the panel showed, since nothing measures it again then. What hides it is laid out, undrawn, for the read instead.
  private def box(): dom.DOMRect = {
    val r = el.getBoundingClientRect()
    if (r.width == 0.0 && r.height == 0.0 && RenderNode.reveal(el)) el.getBoundingClientRect() else r
  }

  def append(child: RenderNode): Unit = el.appendChild(child.el)
  def insert(child: RenderNode, index: Int): Unit = {
    val before = if (index >= 0 && index < el.children.length) el.children(index) else null
    if (before eq child.el) () else el.insertBefore(child.el, before)
  }

  /** Detach from the parent, if attached. */
  def remove(): Unit = if (el.parentNode != null) el.parentNode.removeChild(el)

  def setWidth(width: Double): Unit = el.style.width = width.toString + "px"
  def setHeight(height: Double): Unit = el.style.height = height.toString + "px"

  /** `"#rrggbb"`, or `""` for transparent. */
  def setBackgroundColor(hex: String): Unit =
    el.style.backgroundColor = if (hex.isEmpty) "transparent" else hex

  def setRadius(radius: Double): Unit = el.style.borderRadius = radius.toString + "px"

  /** An edge `width` pixels wide, inside the box (an inset shadow follows the corners and changes no size). */
  def setBorder(width: Double, hex: String): Unit = {
    edge = if (hex.isEmpty || width <= 0.0) "" else "inset 0 0 0 " + width.toString + "px " + hex
    paintShadows()
  }

  /** A linear gradient over the box (`everyui.Gradient`'s encoding), or none for `""`. */
  def setGradient(angle: Double, stops: String): Unit =
    el.style.backgroundImage = if (stops.isEmpty) "none" else RenderNode.linearGradient(angle, stops)

  /** The letters painted with a gradient: the box's background, clipped to the text. */
  def setTextGradient(angle: Double, stops: String): Unit = {
    textFilled = stops.nonEmpty
    el.style.backgroundImage = if (textFilled) RenderNode.linearGradient(angle, stops) else "none"
    el.style.setProperty("-webkit-background-clip", if (textFilled) "text" else "border-box")
    el.style.setProperty("background-clip", if (textFilled) "text" else "border-box")
    el.style.color = if (textFilled) "transparent" else textColor
  }

  /** A layer of its own for the compositor. */
  def setCached(cached: Boolean): Unit = el.style.setProperty("will-change", if (cached) "transform" else "auto")

  private var animations: List[js.Dynamic] = Nil

  /** Each track an animation of one of the element's own transform properties (`translate`, `rotate`, `scale`), which
    * the browser applies before `transform` and runs off the page's thread, or of its opacity. */
  def setMotion(tracks: List[everyui.motion.Track]): Unit = {
    import everyui.motion.Track
    animations.foreach(_.cancel())
    animations = tracks.map { track =>
      val frames = track.keys.map { k =>
        val frame = js.Dynamic.literal(offset = k.at)
        track.property match {
          case Track.Position => frame.updateDynamic("translate")(k.a.toString + "px " + k.b.toString + "px")
          case Track.Rotation => frame.updateDynamic("rotate")(k.a.toString + "rad")
          case Track.Opacity => frame.updateDynamic("opacity")(k.a)
          case _ => frame.updateDynamic("scale")(k.a.toString + " " + k.b.toString)
        }
        frame
      }
      val timing = js.Dynamic.literal(duration = track.seconds * 1000.0, delay = track.delay * 1000.0,
        iterations = Double.PositiveInfinity, easing = "linear")
      el.asInstanceOf[js.Dynamic].animate(js.Array(frames*), timing)
    }
  }

  /** A soft shadow under the box, or none for `""`. */
  def setBlur(radius: Double): Unit =
    el.style.setProperty("filter", if (radius <= 0.0) "" else "blur(" + radius.toString + "px)")
  def setShadow(x: Double, y: Double, blur: Double, hex: String): Unit = {
    drop = if (hex.isEmpty) "" else x.toString + "px " + y.toString + "px " + blur.toString + "px " + hex
    paintShadows()
  }

  /** The edge and the drop shadow are both box shadows, so each keeps the other. */
  private def paintShadows(): Unit = {
    val all = List(edge, drop).filter(_.nonEmpty)
    el.style.setProperty("box-shadow", if (all.isEmpty) "none" else all.mkString(", "))
  }

  /** The distance between lines of wrapped text, in pixels. */
  def setLineHeight(px: Double): Unit = el.style.lineHeight = px.toString + "px"

  /** Where a link goes (the node is an `<a>`, `Nodes.link`), in a new tab with `newTab`. */
  def setHref(href: String, newTab: Boolean): Unit = {
    el.setAttribute("href", href)
    if (newTab) {
      el.setAttribute("target", "_blank")
      el.setAttribute("rel", "noopener")
    }
    else {
      el.removeAttribute("target")
      el.removeAttribute("rel")
    }
  }

  /** The text as styled runs: spans, and `<a>`s for links. A click on a link to a path on this site is `follow`ed in
    * place; any other link, or one clicked with a modifier, is the browser's (a new tab). */
  def setRuns(runs: List[everyui.text.TextRun], follow: String => Unit): Unit = {
    el.textContent = ""
    runs.foreach { run =>
      val span = dom.document.createElement(if (run.href.nonEmpty) "a" else "span").asInstanceOf[html.Element]
      span.textContent = run.text
      if (run.family.nonEmpty) span.style.fontFamily = RenderNode.families(run.family)
      if (run.weight > 0) span.style.fontWeight = run.weight.toString
      if (run.italic) span.style.fontStyle = "italic"
      if (run.size > 0.0) span.style.fontSize = run.size.toString + "px"
      if (run.color.nonEmpty) span.style.color = run.color
      if (run.strike || run.underline)
        span.style.textDecoration = List(Option.when(run.underline)("underline"), Option.when(run.strike)("line-through"))
          .flatten.mkString(" ")
      if (run.background.nonEmpty) {
        span.style.backgroundColor = run.background
        span.style.padding = "0.08em 0.35em"
        span.style.borderRadius = "5px"
      }
      if (run.href.nonEmpty) {
        span.setAttribute("href", run.href)
        span.className = "everyui-link"
        val inApp = run.href.startsWith("/") && !run.href.startsWith("//")
        if (!inApp) {
          span.setAttribute("target", "_blank")
          span.setAttribute("rel", "noopener")
        }
        span.addEventListener("click", (e: dom.MouseEvent) => {
          e.stopPropagation()
          if (inApp && !RenderNode.modified(e)) {
            e.preventDefault()
            follow(run.href)
          }
        })
      }
      el.appendChild(span)
    }
  }

  /** CSS `position`; `""` is static. */
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click", or "wheel" with its deltas) with its
    * viewport coordinates. */
  def onPointer(kind: PointerKind, f: (Double, Double) => Unit): Unit =
    if (kind == PointerKind.Wheel) el.addEventListener("wheel", (e: dom.WheelEvent) => {
      Platform.pointed(e)
      f(e.deltaX, e.deltaY)
    })
    // the app's context menu in place of the browser's
    else if (kind == PointerKind.Menu) el.addEventListener("contextmenu", (e: dom.MouseEvent) => {
      e.preventDefault()
      Platform.pointed(e)
      f(e.clientX, e.clientY)
    })
    else {
      val name = if (kind == PointerKind.Click) "click" else "pointer" + kind.toString.toLowerCase
      el.addEventListener(name, (e: dom.MouseEvent) => {
        Platform.pointed(e)
        // the middle button is the app's (a tab closed), not the browser's scrolling
        if (kind == PointerKind.Down && e.button == 1) e.preventDefault()
        f(e.clientX, e.clientY)
      })
      // something that answers a click is clickable: the hand, and no text selection under it
      if (kind == PointerKind.Click) {
        el.style.cursor = "pointer"
        el.style.setProperty("user-select", "none")
      }
    }

  /** The node scrolls its own overflow: the browser's scrollbar, wheel, touch and keys. */
  def setScrollable(vertical: Boolean, horizontal: Boolean): Unit = {
    el.style.overflowY = if (vertical) "auto" else "hidden"
    el.style.overflowX = if (horizontal) "auto" else "hidden"
    el.style.setProperty("scrollbar-width", "thin")
  }
  /** The scrollbar's thumb colour, on a clear track. */
  def setScrollbarColor(hex: String): Unit = el.style.setProperty("scrollbar-color", if (hex.isEmpty) "auto" else hex + " transparent")
  def setScrollTop(y: Double): Unit = el.scrollTop = y
  def setScrollLeft(x: Double): Unit = el.scrollLeft = x
  /** How far the node has scrolled its own overflow. */
  def scrolled(horizontal: Boolean): Double = if (horizontal) el.scrollLeft else el.scrollTop
  /** Scroll its overflow to `to` as the browser glides a smooth scroll, which a newer one takes over mid-way. */
  def scrollSmoothly(to: Double, horizontal: Boolean): Unit = {
    val options = js.Dynamic.literal(behavior = "smooth")
    if (horizontal) options.left = to else options.top = to
    el.asInstanceOf[js.Dynamic].scrollTo(options)
  }
  /** Whether the platform draws this: what a scroll has out of sight is not drawn at all. */
  def setVisible(visible: Boolean): Unit = {
    el.style.display = if (visible) "" else "none"
    // no longer laid out only to be measured (RenderNode.reveal)
    if (el.style.visibility.nonEmpty) el.style.visibility = ""
  }
  def setOpacity(opacity: Double): Unit = el.style.opacity = opacity.toString

  /** Clipping is the browser's overflow; a scrollable node already clips. */
  def setClip(width: Double, height: Double): Unit = if (el.style.overflowY != "auto") el.style.overflow = "hidden"
  def setTooltip(text: String): Unit = el.title = text
  /** A drawing's figures (everyui.draw.Drawing): a `<path>` each in its `<svg>`, the elements kept and only their
    * attributes changed, so a row of a long list handed another item does no more than that. */
  def setFigures(figures: List[everyui.draw.Figure]): Unit = {
    val svg = el.firstElementChild
    if (svg != null) {
      while (svg.childElementCount > figures.size) svg.removeChild(svg.lastElementChild)
      while (svg.childElementCount < figures.size) svg.appendChild(dom.document.createElementNS(RenderNode.svgNs, "path"))
      var shape = svg.firstElementChild
      figures.foreach { f =>
        shape.setAttribute("d", f.path.svg)
        shape.setAttribute("fill", f.fill.map(_.toHex).getOrElse("none"))
        shape.setAttribute("fill-opacity", f.fill.map(_.alpha.toString).getOrElse("1"))
        shape.setAttribute("stroke", f.stroke.map(_.toHex).getOrElse("none"))
        shape.setAttribute("stroke-opacity", f.stroke.map(_.alpha.toString).getOrElse("1"))
        shape.setAttribute("stroke-width", f.width.toString)
        shape = shape.nextElementSibling
      }
    }
  }
  /** The pointer's shape over it, by its CSS name ("col-resize", "text"); "" leaves the page's own. */
  def setCursor(name: String): Unit = el.style.cursor = name

  def setPositionType(name: String): Unit = el.style.position = if (name.isEmpty) "static" else name

  def setWhiteSpace(value: String): Unit = el.style.whiteSpace = value

  def setImageSource(uri: String): Unit = el.setAttribute("src", uri)

  // ---- a `video` or `audio` element (`Nodes.video`, `Nodes.audio`); on any other node these do nothing ----
  private def media: Option[js.Dynamic] =
    if (el.tagName == "VIDEO" || el.tagName == "AUDIO") Some(el.asInstanceOf[js.Dynamic]) else None
  private var mediaPlaying = true

  /** A `<video>` fetches its own source and takes no headers with it: there is no way to add one short of fetching
    * the media ourselves and feeding it through a media source, which is a different player. Whatever a token has
    * to say must ride in the address here. */
  def setMediaHeaders(headers: Map[String, String]): Unit = ()

  def setMediaSource(url: String): Unit = media.foreach { m =>
    if (url.isEmpty) {
      m.pause()
      el.removeAttribute("src")
      m.load()
    }
    else if (el.getAttribute("src") != url) {
      el.setAttribute("src", url)
      // before the file has said anything, a time set is where it starts once it has (its default playback start
      // position), rather than a seek after it
      if (mediaStart > 0.0) m.currentTime = mediaStart
      mediaStart = 0.0
      if (mediaPlaying) m.play().asInstanceOf[js.Promise[Unit]].`catch`((_: Any) => ())
    }
  }
  /** Where the next source starts, in seconds. */
  def setMediaStart(seconds: Double): Unit = mediaStart = seconds
  private var mediaStart = 0.0
  // a page could add a <track> for each; not yet
  def setMediaSidecars(sidecars: List[everyui.component.MediaSidecar]): Unit = ()
  def setMediaPlaying(playing: Boolean): Unit = {
    mediaPlaying = playing
    media.foreach { m =>
      if (!playing) m.pause()
      else if (el.getAttribute("src") != null) m.play().asInstanceOf[js.Promise[Unit]].`catch`((_: Any) => ())
    }
  }
  def setMediaLoop(loop: Boolean): Unit = media.foreach(_.loop = loop)
  def setMediaMuted(muted: Boolean): Unit = media.foreach(_.muted = muted)
  def setMediaVolume(volume: Double): Unit = media.foreach(_.volume = math.min(1.0, volume)) // a page cannot boost
  def setMediaRate(rate: Double): Unit = media.foreach(_.playbackRate = rate)
  def mediaRates(): Boolean = true
  // neither what it holds nor what it waits for can be told here
  def setMediaBuffering(named: String): Unit = ()
  // the browser keeps what its own cache allows, and a page has no other store for a clip
  def setMediaCached(cached: Boolean): Unit = ()
  // it does not say whether it is waiting; the playhead standing still is what tells a viewer
  def mediaBuffering(): Boolean = false
  // nothing here counts what the link is carrying
  def mediaBandwidth(): Long = 0L
  // only a video element carries a frame size; an audio one answers nothing
  def mediaSize(): (Double, Double) = media.map(_.asInstanceOf[js.Dynamic]).map { m =>
    val w = m.selectDynamic("videoWidth")
    val h = m.selectDynamic("videoHeight")
    if (js.isUndefined(w) || js.isUndefined(h)) (0.0, 0.0)
    else (w.asInstanceOf[Double], h.asInstanceOf[Double])
  }.getOrElse((0.0, 0.0))
  // the browser lists the tracks a file carries; `audioTracks` is not in every engine, so a missing list is no list
  /** The words on screen: "" here, where the browser draws the showing text track itself. */
  def mediaCues(): String = ""
  def mediaCueList(): List[everyui.component.MediaCue] = Nil
  // a `video` retries by itself and says nothing of it
  def mediaRetry(): Option[everyui.component.MediaRetry] = None
  // nothing here shows what is playing, or hears it
  def setNowPlaying(about: everyui.component.NowPlaying): Unit = ()
  def setMediaSpectrum(on: Boolean): Unit = ()
  def mediaBands(): List[Double] = Nil

  // ---- a page of the web ----
  def setWebSource(url: String): Unit = ()
  def setWebAgent(agent: String): Unit = ()
  def runWebScript(script: String): Unit = ()
  def onWebMessage(f: String => Unit): Unit = ()

  // ---- an embed (everyui.component.Embed, Nodes.embed): the guest runs in this page, drawing into this element ----
  private[render] var embedSite: everyui.component.EmbedSite = null
  private var embedHearing: List[() => Unit] = Nil

  def embedId(): String = el.id
  /** Every embed of a site shares the page's one copy of its guest. */
  def embedRuntime(): String = if (embedSite == null) el.id else "web:" + embedSite.name
  def embedSend(message: String): Unit = if (embedSite != null) WebGuests.send(embedSite.name, message)
  def onEmbedMessage(f: String => Unit): Unit =
    if (embedSite != null) embedHearing = WebGuests.listen(embedSite.name, f) :: embedHearing
  /** The page's focus taken out of the guest as the app's leaves it; the guest puts it in itself as it arrives. */
  def focusEmbed(on: Boolean): Unit = {
    val active = dom.document.activeElement
    if (!on && active != null && el.contains(active)) active.asInstanceOf[html.Element].blur()
  }
  def onEmbedFocus(f: Boolean => Unit): Unit = {
    el.addEventListener("focusin", (_: dom.FocusEvent) => f(true))
    el.addEventListener("focusout", (e: dom.FocusEvent) => {
      val to = e.relatedTarget
      if (to == null || !el.contains(to.asInstanceOf[dom.Node])) f(false)
    })
  }
  def releaseEmbed(): Unit = {
    embedHearing.foreach(stop => stop())
    embedHearing = Nil
  }

  /** What to hear and read this in: nothing here, where the platform chooses its own tracks. */
  def setMediaLanguages(audio: String, text: String): Unit = ()

  /** What was decoded: nothing a page is told. */
  def mediaDiagnostics(): everyui.component.MediaDiagnostics = everyui.component.MediaDiagnostics()

  def mediaTracks(): List[everyui.component.MediaTrack] = media.toList.flatMap { e =>
    val m = e.asInstanceOf[js.Dynamic]
    val audio = tracksOf(m, "audioTracks", everyui.component.MediaTrack.Audio)
    val text = tracksOf(m, "textTracks", everyui.component.MediaTrack.Text)
    audio ++ text
  }
  private def tracksOf(m: js.Dynamic, field: String, kind: String): List[everyui.component.MediaTrack] = {
    val list = m.selectDynamic(field)
    if (js.isUndefined(list) || list == null) Nil
    else {
      val count = list.length.asInstanceOf[Int]
      (0 until count).toList.map { i =>
        val t = list.applyDynamic("item")(i)
        val language = Option(t.language.asInstanceOf[String]).getOrElse("")
        val label = Option(t.label.asInstanceOf[String]).getOrElse("")
        everyui.component.MediaTrack(i.toString, kind, language, label)
      }
    }
  }
  def selectMediaTrack(kind: String, id: String): Unit = media.foreach { m =>
    val field = if (kind == everyui.component.MediaTrack.Audio) "audioTracks" else "textTracks"
    val list = m.asInstanceOf[js.Dynamic].selectDynamic(field)
    if (!js.isUndefined(list) && list != null) {
      val count = list.length.asInstanceOf[Int]
      (0 until count).foreach { i =>
        val t = list.applyDynamic("item")(i)
        val on = id == i.toString
        if (kind == everyui.component.MediaTrack.Audio) t.enabled = on
        else t.mode = if (on) "showing" else "disabled"
      }
    }
  }
  def setMediaCover(cover: Boolean): Unit = el.style.setProperty("object-fit", if (cover) "cover" else "contain")
  def setMediaDirect(direct: Boolean): Unit = ()
  def getMediaPosition(): Double = media.map(_.currentTime.asInstanceOf[Double]).getOrElse(0.0)
  // a browser answers NaN until the metadata is in, and Infinity for a live stream: neither is a length
  def getMediaDuration(): Double =
    media.map(_.duration.asInstanceOf[Double]).filter(d => !d.isNaN && !d.isInfinite && d > 0.0).getOrElse(0.0)
  def seekMedia(seconds: Double): Unit = media.foreach(_.currentTime = seconds)
  def onMediaEvent(f: String => Unit): Unit = if (media.nonEmpty) {
    el.addEventListener("playing", (_: dom.Event) => f("ready"))
    el.addEventListener("ended", (_: dom.Event) => f("ended"))
    el.addEventListener("error", (_: dom.Event) => f("error"))
  }
  def releaseMedia(): Unit = setMediaSource("")

  /** The picture in one colour, its transparency kept: the box painted the colour through the picture as a mask, and
    * the picture itself drawn out of sight. */
  def setTint(hex: String): Unit = {
    val tinted = hex.nonEmpty
    el.style.backgroundColor = if (tinted) hex else "transparent"
    el.style.setProperty("object-position", if (tinted) "-100000px 0" else "")
    List("mask", "-webkit-mask").foreach { p =>
      el.style.setProperty(p + "-image", if (tinted) "url(\"" + el.getAttribute("src") + "\")" else "none")
      el.style.setProperty(p + "-size", "100% 100%")
    }
  }

  /** What the element is to a screen reader or a crawler: its ARIA role, a heading's level, its name. */
  def setSemantics(role: String, level: Int, label: String): Unit = {
    def attribute(name: String, value: String): Unit =
      if (value.isEmpty) el.removeAttribute(name) else el.setAttribute(name, value)
    attribute("role", role)
    attribute("aria-level", if (role == "heading" && level > 0) level.toString else "")
    attribute("aria-label", label)
  }

  /** The focus landed here. The page keeps its own focus rather than the browser's, and says what things are by
    * their roles and labels (setSemantics), so there is nothing more to do. */
  def accessibleFocus(click: () => Unit): Unit = ()

  /** An `<img>`'s words for what it shows; empty marks it decoration. */
  def setAlt(text: String): Unit = el.setAttribute("alt", text)

  /** "start", "center" or "end": CSS's own logical values, so the page's direction resolves them. */
  /** A shadow behind the letters, as CSS draws one; an empty colour, none. */
  // an edge as the Flutter screensaver draws one: eight hard copies of the letters about them, no blur
  def setTextOutline(width: Double, hex: String): Unit =
    el.style.setProperty("text-shadow", if (hex.isEmpty) "none" else List((-0.8, -0.8), (0.8, -0.8), (-0.8, 0.8), (0.8, 0.8),
      (0.0, -1.0), (0.0, 1.0), (-1.0, 0.0), (1.0, 0.0)).map(p => s"${p._1 * width}px ${p._2 * width}px 0 $hex").mkString(", "))

  def setTextShadow(x: Double, y: Double, blur: Double, hex: String): Unit =
    el.style.setProperty("text-shadow", if (hex.isEmpty) "none" else s"${x}px ${y}px ${blur}px $hex")

  def setTextAlign(align: String): Unit = el.style.textAlign = align

  /** An `<input>`'s contents. */
  def getValue(): String = el match {
    case i: html.Input => i.value
    case t: html.TextArea => t.value
    case s: html.Select => s.value
    case _ => ""
  }
  def setValue(value: String): Unit = el match {
    case i: html.Input => i.value = value
    case t: html.TextArea => t.value = value
    case s: html.Select => s.value = value
    case _ => ()
  }
  /** A drop-down's choices (`Nodes.select`), each answering with its index as its value. */
  def setOptions(labels: List[String]): Unit = {
    val chosen = getValue()
    el.textContent = ""
    labels.zipWithIndex.foreach { case (label, i) =>
      val option = dom.document.createElement("option").asInstanceOf[html.Option]
      option.value = i.toString
      option.textContent = label
      el.appendChild(option)
    }
    setValue(chosen)
  }
  def setPlaceholder(text: String): Unit = el.setAttribute("placeholder", text)
  /** What the keyboard's enter key says it does ("done", "search", "next", "go", "send"). */
  def setInputAction(action: String): Unit = el.setAttribute("enterkeyhint", action)
  /** Every edit, with the new contents. */
  def onValue(f: String => Unit): Unit = el.addEventListener("input", (_: dom.Event) => f(getValue()))
  /** The field taking the keyboard itself (a click, a tap, the browser's own Tab). */
  def onInputFocus(f: () => Unit): Unit = el.addEventListener("focus", (_: dom.Event) => f())
  /** The field losing the keyboard. */
  def onInputDone(f: () => Unit): Unit = el.addEventListener("blur", (_: dom.Event) => f())
  /** Enter in the field, its keyboard's action key. */
  def onInputAction(f: () => Unit): Unit =
    el.addEventListener("keydown", (e: dom.KeyboardEvent) => if (e.key == "Enter" && !e.shiftKey) f())
  /** All of what the field holds chosen, so what is typed next replaces it. */
  def selectAllInput(): Unit = el.asInstanceOf[js.Dynamic].select()
  /** Give the field the keyboard, or take it away. */
  def focusInput(on: Boolean): Unit =
    if (on && dom.document.activeElement != el) el.focus()
    else if (!on && dom.document.activeElement == el) el.blur()

  private var rotation: Double = 0.0
  private var edge: String = ""
  private var drop: String = ""
  private var textColor: String = ""
  private var textFilled: Boolean = false

  private def px(value: String): Double =
    value.stripSuffix("px").toDoubleOption.getOrElse(0.0)
}

object RenderNode {
  /** SVG's namespace, which its elements are made in. */
  val svgNs: String = "http://www.w3.org/2000/svg"

  // what `reveal` has laid out, undrawn, until the end of the turn
  private var revealed: List[html.Element] = Nil

  /** Lay out what hides `el` (it, or a container above it, `display: none`) without drawing it, until the end of the
    * script's turn, so its size can be read; whether there was anything to. The sizes a turn asks for are read together
    * (MeasuredFeature), so a hidden panel is laid out once for all of them, and it is hidden again before the frame is
    * drawn. A node not on the page is measured when it is put there. */
  def reveal(el: html.Element): Boolean = el.isConnected && el.getClientRects().length == 0 && {
    val before = revealed
    var at: dom.Node = el
    while (at != null && !(at eq dom.document)) {
      val e = at.asInstanceOf[html.Element]
      if (e.style.display == "none") {
        e.style.display = ""
        e.style.visibility = "hidden"
        revealed = e :: revealed
      }
      at = at.parentNode
    }
    if (before.isEmpty && revealed.nonEmpty) Platform.beforePaint(() => hide())
    !(revealed eq before)
  }

  // hidden again, unless it was shown or hidden since (setVisible clears the visibility)
  private def hide(): Unit = {
    revealed.foreach { e =>
      if (e.style.visibility == "hidden") {
        e.style.display = "none"
        e.style.visibility = ""
      }
    }
    revealed = Nil
  }

  /** CSS for `everyui.Gradient`'s encoding. */
  def linearGradient(angle: Double, stops: String): String =
    "linear-gradient(" + angle.toString + "deg, " +
      everyui.Gradient.decode(stops).map { case (hex, at) => hex + " " + (at * 100.0).toString + "%" }.mkString(", ") + ")"
  /** A CSS font-family for `family`: it, then the fallback faces (`Faces.fallbackFaces`), for what it has nothing to
    * draw with. */
  def families(family: String): String =
    ("'" + family + "'" :: everyui.font.Faces.fallbackFaces().toList.map(f => "'" + f.family + "'")).mkString(", ")


  /** The embed keys at `el` (the page's focused element) belong to, "" for none: the name given by the nearest element
    * marked `data-everyui-embed` (an editor or other page embedded in the app; "embed" when it gives none), else
    * "field" for a field, text area or editable element the app did not make (outside `#app`). The focus layer leaves
    * such a key to it (`Platform.keyEmbed`), rather than moving the focus on an arrow or keeping Tab from it. */
  def embedOf(el: dom.Element): String =
    if (el == null || (el eq dom.document.body)) ""
    else {
      val embed = el.closest("[data-everyui-embed]")
      val tag = el.tagName.toLowerCase
      val editable = tag == "input" || tag == "textarea" || tag == "select" ||
        el.asInstanceOf[html.Element].isContentEditable
      val app = dom.document.getElementById("app")
      if (embed != null) {
        val name = embed.getAttribute("data-everyui-embed")
        if (name == null || name.isEmpty) "embed" else name
      }
      else if (editable && (app == null || !app.contains(el))) "field"
      else ""
    }

  /** A click the browser should keep: another button, or a modifier asking for a new tab or window. */
  def modified(e: dom.MouseEvent): Boolean = e.button != 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey
}

/** Backend node factories. */
object Nodes {
  /** A page of the web: nothing here, where the platform has no engine of its own to lend. */
  def web(bridge: String): RenderNode = container()

  /** An embed's box (everyui.component.Embed): a `<div>` marked `data-everyui-embed`, so the keys are the guest's while
    * the page's focus is inside it, with an id of its own for the guest to find it by. A `site`'s scripts are loaded
    * into the page the first time one of its embeds is made. */
  def embed(name: String, site: everyui.component.EmbedSite): RenderNode = {
    val node = container()
    node.el.id = WebGuests.nextId()
    node.el.setAttribute("data-everyui-embed", name)
    node.el.style.overflow = "hidden"
    if (site != null) {
      node.embedSite = site
      WebGuests.load(site)
    }
    node
  }

  /** The page's mount point. */
  def root(): RenderNode = {
    val existing = dom.document.getElementById("app")
    val app =
      if (existing != null) existing.asInstanceOf[html.Element]
      else {
        val created = dom.document.createElement("div").asInstanceOf[html.Element]
        created.id = "app"
        dom.document.body.appendChild(created)
        created
      }
    app.style.position = "relative"
    // The render root is a fixed VIEWPORT on every other backend — SceneGraph
    // clips to the screen and there is no document to grow. The DOM is the only
    // one that reflows, so a node animating past an edge (an elastic curve
    // overshooting to x = -3, a row travelling right) grows the page and the
    // browser shows a scrollbar. That is not just cosmetic: the scrollbar
    // narrows the layout viewport, which shifts every subsequent measurement.
    // Clip, and the DOM behaves like the other backends.
    app.style.overflow = "hidden"
    app.style.width = "100%"
    app.style.height = "100%"
    // a link in running text shows it is one under the pointer
    val style = dom.document.createElement("style")
    style.textContent = ".everyui-link:hover { text-decoration: underline; text-underline-offset: 3px; }"
    dom.document.head.appendChild(style)
    new RenderNode(app)
  }

  /** A video: an absolutely positioned `<video>`, filling its box, playing inline without controls. A browser starts
    * one with sound by itself only once the page has been used; a muted one it always starts. */
  def video(): RenderNode = media("video")

  /** Sound: an `<audio>` with nothing to draw. */
  def audio(): RenderNode = media("audio")

  private def media(tag: String): RenderNode = {
    val el = dom.document.createElement(tag).asInstanceOf[html.Element]
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    el.style.setProperty("object-fit", "cover")
    el.setAttribute("playsinline", "")
    el.setAttribute("preload", "auto")
    new RenderNode(el)
  }

  /** A container — an absolutely positioned `<div>` other nodes are appended to. */
  def container(): RenderNode = {
    val el = dom.document.createElement("div").asInstanceOf[html.Element]
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    new RenderNode(el)
  }

  /** A drawing (everyui.draw.Drawing): a box holding an inline `<svg>` as large as it, whose paths are drawn by the
    * browser and redrawn only when they change. */
  def drawing(): RenderNode = {
    val node = container()
    val svg = dom.document.createElementNS(RenderNode.svgNs, "svg")
    svg.setAttribute("width", "100%")
    svg.setAttribute("height", "100%")
    svg.setAttribute("style", "position:absolute;left:0;top:0;overflow:visible;pointer-events:none")
    svg.setAttribute("stroke-linecap", "round")
    svg.setAttribute("stroke-linejoin", "round")
    node.el.appendChild(svg)
    node
  }

  /** A link — an absolutely positioned `<a>` holding other nodes. A plain click is the app's (`Link` follows it); one
    * with a modifier, another button, or on a link to a new tab is the browser's, and the app never hears of it. */
  def link(): RenderNode = {
    val el = dom.document.createElement("a").asInstanceOf[html.Element]
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    el.style.display = "block"
    el.style.color = "inherit"
    el.style.textDecoration = "none"
    el.addEventListener("click", (e: dom.MouseEvent) => {
      if (RenderNode.modified(e) || el.getAttribute("target") == "_blank") e.stopImmediatePropagation()
      else e.preventDefault()
    })
    new RenderNode(el)
  }

  /** A drop-down — a `<select>` — with no box of its own, its words in the node's own font and colour. */
  def select(): RenderNode = {
    val el = dom.document.createElement("select").asInstanceOf[html.Element]
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    el.style.border = "none"
    el.style.outline = "none"
    el.style.margin = "0px"
    el.style.background = "transparent"
    el.style.setProperty("box-sizing", "border-box")
    el.style.cursor = "pointer"
    new RenderNode(el)
  }

  /** A box of lines to type in — a `<textarea>` — with no box of its own. */
  def textArea(): RenderNode = {
    val el = dom.document.createElement("textarea").asInstanceOf[html.Element]
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    el.style.border = "none"
    el.style.outline = "none"
    el.style.padding = "0px"
    el.style.margin = "0px"
    el.style.background = "transparent"
    el.style.setProperty("resize", "none")
    el.style.setProperty("box-sizing", "border-box")
    new RenderNode(el)
  }

  /** A text node — an absolutely positioned `<div>`. */
  def text(): RenderNode = {
    val el = dom.document.createElement("div").asInstanceOf[html.Element]
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    el.style.font = "600 32px system-ui, sans-serif"
    el.style.color = "#1c1b1f"
    el.style.whiteSpace = "nowrap"
    new RenderNode(el)
  }

  /** A Material Icons glyph, by codepoint, `size` pixels — youi's
    * `<i class="material-icons">`. The face's ascent is its whole em, so the
    * line box is the glyph's `size` square, as youi's `line-height: 1` has it. */
  def icon(glyph: String, size: Double): RenderNode = {
    val node = text()
    node.setFont("Material Icons", 400, size)
    node.setText(glyph)
    node
  }

  /** A text field — an `<input>`, `type=password` for a secret — with no box of its own: the component draws that. */
  def input(secure: Boolean): RenderNode = {
    val el = dom.document.createElement("input").asInstanceOf[html.Input]
    el.`type` = if (secure) "password" else "text"
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    el.style.border = "none"
    el.style.outline = "none"
    el.style.padding = "0px"
    el.style.margin = "0px"
    el.style.background = "transparent"
    el.style.setProperty("box-sizing", "border-box")
    new RenderNode(el)
  }

  /** An image node — an `<img>`. */
  def image(uri: String, width: Double, height: Double): RenderNode = {
    val el = dom.document.createElement("img").asInstanceOf[html.Image]
    el.src = uri
    // decoration until the app says what it shows (`ImageView.alt`): a picture with no alt at all is read out by its file name
    el.setAttribute("alt", "")
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    el.width = width.toInt
    el.height = height.toInt
    new RenderNode(el)
  }
}

/** The handful of non-render platform services the slice needs. */
object Platform {
  /** Whether this device can go on playing in a corner of whatever the viewer does next, and doing it. */
  /** Whether this platform has a browser engine to lend a page (Nodes.web): not yet here. */
  def webViews(): Boolean = false
  /** Whether a web guest runs in an embed here: in this very page. */
  def embeds(): Boolean = true

  def corners(): Boolean = false

  def toCorner(): Boolean = false

  /** What to do when the app is at the front again after being away: the page shown again. */
  def onResume(f: () => Unit): () => Unit = {
    resumers = resumers :+ f
    () => resumers = resumers.filterNot(_ eq f)
  }

  def resumed(): Unit = resumers.foreach(f => f())

  private var resumers: List[() => Unit] = Nil

  /** What to do when the app goes away from the front: another app has the screen, or the device is going to sleep.
    * A player wants to know, because nobody is watching it now. Returns what stops it being told. */
  def onAway(f: () => Unit): () => Unit = {
    aways = aways :+ f
    () => aways = aways.filterNot(_ eq f)
  }

  /** Said by the platform when it happens. */
  def away(): Unit = aways.foreach(f => f())

  private var aways: List[() => Unit] = Nil

  // a page hidden is one nobody is watching — another tab, the window minimised, a television's own home screen
  // over the app on webOS and Tizen — and shown again is somebody back
  dom.document.addEventListener("visibilitychange", (_: dom.Event) =>
    if (dom.document.visibilityState == "hidden") away() else resumed())

  def log(message: String): Unit = dom.console.log("[ui] " + message)

  /** A failure with its cause: the console gets the stack. */
  def logError(message: String, t: Throwable): Unit = {
    dom.console.log("[ui] ERROR " + message + ": " + t.toString)
    dom.console.error(t.asInstanceOf[js.Any])
  }

  def ticks(): Double = dom.window.performance.now()

  /** Served next to index.html. */
  def imagePath(name: String): String = "images/" + name

  // The window's inner size, as youi's `ui` measures it. The root clips its
  // content, so no scrollbar ever takes space out of it.
  def kind: PlatformKind = PlatformKind.Web
  /** A TV browser (webOS, Tizen, a set-top box) is driven by a remote; anything else by a pointer, touch, or both. */
  def remoteNavigation(): Boolean = {
    val agent = dom.window.navigator.userAgent
    agent.contains("Web0S") || agent.contains("Tizen") || agent.contains("SmartTV") || agent.contains("SMART-TV") || agent.contains("Roku")
  }
  def touchSupport(): Boolean = {
    val points = dom.window.navigator.asInstanceOf[js.Dynamic].maxTouchPoints
    !js.isUndefined(points) && points.asInstanceOf[Int] > 0
  }
  def pointerSupport(): Boolean = !remoteNavigation() && dom.window.matchMedia("(pointer: fine)").matches
  def keyboardSupport(): Boolean = !remoteNavigation()

  /** The DOM scrolls a scrollable node itself. */
  def nativeScroll(): Boolean = true
  /** `url` in a new tab. */
  def openUrl(url: String): Unit = dom.window.open(url, "_blank")
  /** Leave for `url`, in this tab. */
  def navigate(url: String): Unit = dom.window.location.assign(url)
  /** What the tab is called. */
  def setTitle(title: String): Unit = dom.document.title = title

  /** The page's head says what the page now shown is (`everyui.seo.SEO`): its title, description, canonical
    * address, preview, other forms and structured data, replacing what the page before it said. What the server wrote
    * into the page first is replaced the same way, so the head never holds two of anything. */
  def setSEO(meta: everyui.seo.SEO): Unit = {
    val head = dom.document.head
    val written = head.querySelectorAll("[data-everyui-meta]")
    (0 until written.length).map(written(_)).foreach(e => head.removeChild(e))
    def add(tag: String, attributes: (String, String)*): Unit = {
      val e = dom.document.createElement(tag)
      attributes.foreach { case (k, v) => e.setAttribute(k, v) }
      e.setAttribute("data-everyui-meta", "")
      head.appendChild(e)
    }
    def absolute(url: String): String =
      if (url.isEmpty || url.contains("://")) url else dom.window.location.origin + (if (url.startsWith("/")) url else "/" + url)
    setTitle(meta.title)
    if (meta.description.nonEmpty) add("meta", "name" -> "description", "content" -> meta.description)
    if (meta.canonical.nonEmpty) add("link", "rel" -> "canonical", "href" -> absolute(meta.canonical))
    if (meta.noindex) add("meta", "name" -> "robots", "content" -> "noindex")
    if (meta.siteName.nonEmpty) add("meta", "property" -> "og:site_name", "content" -> meta.siteName)
    add("meta", "property" -> "og:title", "content" -> meta.title)
    if (meta.description.nonEmpty) add("meta", "property" -> "og:description", "content" -> meta.description)
    add("meta", "property" -> "og:type", "content" -> meta.kind)
    if (meta.canonical.nonEmpty) add("meta", "property" -> "og:url", "content" -> absolute(meta.canonical))
    if (meta.image.nonEmpty) add("meta", "property" -> "og:image", "content" -> absolute(meta.image))
    add("meta", "name" -> "twitter:card", "content" -> (if (meta.image.nonEmpty) "summary_large_image" else "summary"))
    meta.alternates.foreach { a =>
      add("link", "rel" -> "alternate", "type" -> a.contentType, "href" -> a.href, "title" -> a.title)
    }
    meta.jsonLd.foreach { json =>
      val script = dom.document.createElement("script")
      script.setAttribute("type", "application/ld+json")
      script.setAttribute("data-everyui-meta", "")
      script.textContent = json
      head.appendChild(script)
    }
  }

  /** A component's size is read in a batch (`MeasuredFeature`): every size asked for while the script runs is read
    * together at the end of its turn, before the browser draws, so the page is laid out once for all of them rather than
    * once for each read that follows a change. Nothing is drawn in between, so nothing shows the difference. */
  def measuresInBatches: Boolean = true
  /** `f` once the page has been painted at least once: after the next frame the browser draws. What the server wrote
    * into the page (its content, as plain HTML) is then on the screen before the app starts building over it, and a
    * page's first and largest paints are that content's, not the app's; an app with nothing written in waits one frame.
    * A tab opened in the background draws no frames until it is shown, so a moment later it starts regardless. */
  def afterFirstPaint(f: () => Unit): Unit = {
    var started = false
    val go: js.Function0[Unit] = () => if (!started) {
      started = true
      f()
    }
    dom.window.requestAnimationFrame(_ => dom.window.setTimeout(go, 0))
    dom.window.setTimeout(go, 100)
  }

  /** `f` at the end of the script's turn, before the browser draws: a microtask. */
  def beforePaint(f: () => Unit): Unit = {
    val task: js.Function0[Unit] = () => f()
    js.Dynamic.global.queueMicrotask(task)
  }

  /** The first screen is drawn: what the server wrote into the page to stand in for it until then (the page's content
    * as plain HTML, for whatever reads the page without running it) goes. */
  def drawn(): Unit = {
    val snapshot = dom.document.getElementById("everyui-snapshot")
    if (snapshot != null && snapshot.parentNode != null) snapshot.parentNode.removeChild(snapshot)
  }
  /** The browser names itself (and sends the page as the referrer), as a tile server asks. */
  def identify(app: String): Unit = ()
  /** `text` onto the clipboard: the async clipboard where the page is a secure context, else the selection command. */
  /** Every colour on the page scaled, channel by channel (1 each leaves it as it is): an SVG colour matrix the page's
    * root is filtered through, or no filter. */
  def tint(red: Double, green: Double, blue: Double): Boolean = {
    val app = dom.document.getElementById("app").asInstanceOf[html.Element]
    if (app != null) {
      if (red >= 0.999 && green >= 0.999 && blue >= 0.999) app.style.removeProperty("filter")
      else {
        var svg = dom.document.getElementById("everyui-tint")
        if (svg == null) {
          svg = dom.document.createElementNS("http://www.w3.org/2000/svg", "svg")
          svg.setAttribute("id", "everyui-tint")
          svg.setAttribute("width", "0")
          svg.setAttribute("height", "0")
          svg.setAttribute("style", "position:absolute")
          dom.document.body.appendChild(svg)
        }
        svg.innerHTML = "<filter id=\"everyui-tint-matrix\" color-interpolation-filters=\"sRGB\"><feColorMatrix " +
          "type=\"matrix\" values=\"" + red.toString + " 0 0 0 0 0 " + green.toString + " 0 0 0 0 0 " + blue.toString +
          " 0 0 0 0 0 1 0\"/></filter>"
        app.style.setProperty("filter", "url(#everyui-tint-matrix)")
      }
    }
    true
  }

  /** The screen kept on through the browser's wake lock, where it has one (and the page is showing), or let sleep. */
  def keepAwake(on: Boolean): Unit = {
    val wakeLock = dom.window.navigator.asInstanceOf[js.Dynamic].wakeLock
    if (on && wakeSentinel.isEmpty && !js.isUndefined(wakeLock))
      wakeLock.request("screen").asInstanceOf[js.Promise[js.Dynamic]].`then`[Unit](s => wakeSentinel = Some(s))
    else if (!on) {
      wakeSentinel.foreach(_.release())
      wakeSentinel = None
    }
  }
  private var wakeSentinel: Option[js.Dynamic] = None

  /** Whether copyText reaches a clipboard: it does. */
  def clipboard(): Boolean = true
  def copyText(text: String): Unit = {
    val clipboard = dom.window.navigator.asInstanceOf[js.Dynamic].clipboard
    if (!js.isUndefined(clipboard)) clipboard.writeText(text)
    else {
      val area = dom.document.createElement("textarea").asInstanceOf[html.TextArea]
      area.value = text
      dom.document.body.appendChild(area)
      area.select()
      dom.document.execCommand("copy")
      dom.document.body.removeChild(area)
    }
  }

  /** The page's path and query (`/runs?state=x`) — what a screen is reached at. */
  def path(): String = dom.window.location.pathname + dom.window.location.search
  /** Show `path` in the address bar as a new history entry, without loading anything. */
  def pushPath(path: String): Unit = dom.window.history.pushState(null, "", path)
  /** The path after the browser's back or forward. */
  def onPath(f: String => Unit): Unit = dom.window.addEventListener("popstate", (_: dom.Event) => f(path()))

  /** Run `f` after the current event: one thread, but the same order as every other backend. */
  /** Back is the browser's own, so the address bar and its back button stay in step (the screen manager hears the
    * new path). */
  def historyBack(): Boolean = {
    dom.window.history.back()
    true
  }

  /** An ARB file's messages (everyui.i18n.Arb). */
  def parseMessages(arb: String): Map[String, String] = everyui.i18n.Arb.parse(arb)

  /** A text file served with the page (a translation), or "" if it cannot be had. */
  def readText(path: String, done: String => Unit): Unit = {
    val text: js.Function1[js.Dynamic, js.Any] = r => if (r.ok.asInstanceOf[Boolean]) r.text() else ""
    val ok: js.Function1[js.Any, Unit] = t => done(t.toString)
    val failed: js.Function1[js.Any, Unit] = _ => done("")
    js.Dynamic.global.fetch(path).applyDynamic("then")(text).applyDynamic("then")(ok, failed)
  }

  /** The character at a Unicode code point, as a string. */
  def fromCodePoint(codePoint: Int): String = js.Dynamic.global.String.fromCodePoint(codePoint).asInstanceOf[String]

  /** The browser's language, as a BCP 47 tag (`en-US`). */
  def language(): String = dom.window.navigator.language

  /** The page's reading direction: text, fields and alignment follow it. */
  def setDirection(rtl: Boolean): Unit = dom.document.documentElement.setAttribute("dir", if (rtl) "rtl" else "ltr")
  /** The page's language (`zh-Hant`), which the browser chooses CJK faces by. */
  def setLanguage(code: String): Unit = dom.document.documentElement.setAttribute("lang", code.replace("_", "-"))

  /** A page has one thread: the work is done at once, and what it hands back by `later` comes on a turn after. */
  def offThread(f: () => Unit): Unit = f()

  def later(f: () => Unit): Unit = dom.window.setTimeout(() => f(), 0)
  /** `f` once `seconds` have passed. */
  def after(seconds: Double, f: () => Unit): Unit = dom.window.setTimeout(() => f(), seconds * 1000.0)

  /** A value kept across visits (`localStorage`), or "" when there is none. */
  def setting(name: String): String = {
    val v = dom.window.localStorage.getItem(name)
    if (v == null) "" else v
  }
  /** Keep `value` under `name`; "" forgets it. */
  // nowhere safer than the settings to keep a secret here
  def secret(name: String): String = setting(name)
  def saveSecret(name: String, value: String): Unit = saveSetting(name, value)

  def saveSetting(name: String, value: String): Unit =
    if (value.isEmpty) dom.window.localStorage.removeItem(name) else dom.window.localStorage.setItem(name, value)

  /** The device's own store of documents (see [[everyui.store.Store]]).
    *
    * `localStorage` for now, which is the reason the surface above is written in callbacks rather than returns:
    * this is the backend that will not stay synchronous. A browser gives `localStorage` a few megabytes and hands
    * it over as strings, which is enough to cache a screen but not a library — IndexedDB is where this goes (via
    * LightDB, which speaks it), and the callback is already shaped to answer later when it does. Nothing above
    * changes when it moves; only these five bodies. */
  private def storeKey(space: String, key: String): String = "everyui-store:" + space + ":" + key

  def storeGet(space: String, key: String, got: Option[String] => Unit): Unit = {
    val value = dom.window.localStorage.getItem(storeKey(space, key))
    got(if (value == null || value.isEmpty) None else Some(value))
  }

  def storePut(space: String, key: String, value: String, done: () => Unit): Unit = {
    // a browser refuses a write once its quota is met, and a cache that cannot be written is still a working app
    try dom.window.localStorage.setItem(storeKey(space, key), value) catch { case _: Throwable => () }
    done()
  }

  def storeDelete(space: String, key: String, done: () => Unit): Unit = {
    dom.window.localStorage.removeItem(storeKey(space, key))
    done()
  }

  def storeKeys(space: String, got: List[String] => Unit): Unit = {
    val prefix = storeKey(space, "")
    var found: List[String] = Nil
    var i = 0
    while (i < dom.window.localStorage.length) {
      val name = dom.window.localStorage.key(i)
      if (name != null && name.startsWith(prefix)) found = name.substring(prefix.length) :: found
      i += 1
    }
    got(found)
  }

  def storeClear(space: String, done: () => Unit): Unit = {
    storeKeys(space, keys => keys.foreach(k => dom.window.localStorage.removeItem(storeKey(space, k))))
    done()
  }

  /** The browser and the system it runs on, as its user agent says them. */
  /** What it is, as its maker names it; the same as its name where the platform knows no more than that. */
  def deviceModel(): String = deviceName()

  def launchesApps(): Boolean = false
  // nothing here says which codecs it has, so none is ruled out: a wrong no costs a transcode nobody needed
  def decodes(codec: String): Boolean = true
  // an app here is updated by whatever put it here, never by itself
  def canInstallApps(): Boolean = false
  def openInstallSettings(): Unit = ()
  def installApp(url: String, progress: Double => Unit, done: Option[String] => Unit): Unit =
    done(Some("Updates install only on Android"))
  /** The hardware facts a performance report is read with: none this platform will say. */
  def deviceProfile(): List[(String, String)] = Nil

  /** What the app is showing, as a PNG in base64: nothing here can take one. */
  def screenshot(): String = ""

  /** Something large was let go: nothing to do where the runtime collects on its own. */
  def collect(): Unit = ()
  /** Whether `blur` is drawn: it is. */
  def blurs(): Boolean = true
  /** Whether text can hold emoji: the system's fonts draw them. */
  def emoji(): Boolean = true
  // nothing here turns speech into words
  def dictation(): Boolean = false
  def dictate(heard: (String, Boolean) => Unit, ended: () => Unit): () => Unit = {
    ended()
    () => ()
  }
  def localAddress(): String = ""
  def deviceSerial(): String = ""

  /** Where the app came from, and which build it is: nothing this platform can say. */
  def installStore(): String = ""
  def appVersion(): Int = 0
  def appVersionName(): String = ""

  /** The device's own caption settings, where it has any: not here. */
  def captionStyle(): Option[everyui.text.CaptionStyle] = None

  def deviceName(): String = {
    val agent = dom.window.navigator.userAgent
    val browser = if (agent.contains("Edg/")) "Edge" else if (agent.contains("Chrome/")) "Chrome" else if (agent.contains("Firefox/")) "Firefox" else if (agent.contains("Safari/")) "Safari" else "Browser"
    val system = if (agent.contains("Windows")) "Windows" else if (agent.contains("Android")) "Android" else if (agent.contains("iPhone") || agent.contains("iPad")) "iOS" else if (agent.contains("Mac OS")) "macOS" else if (agent.contains("Linux")) "Linux" else ""
    if (system.isEmpty) browser else browser + " on " + system
  }

  /** A browser has text fields of its own. */
  def nativeInput(): Boolean = true
  /** Only a platform without text fields asks its keyboard for a value. */
  def editText(title: String, value: String, secure: Boolean, done: String => Unit): Unit = ()

  /** An app's face, served beside the bundled ones in `fonts/`, added to the page's fonts. Where the page preloads it
    * (`<link rel="preload" as="font">`), from that very address, versioned or not, so the preloaded download is the one
    * used rather than a second. */
  def registerFont(face: Face): Unit = {
    // an italic face answers for its family's italic runs; a fallback is a family of its own, named after the others
    val options = js.Dynamic.literal(weight = face.weight.toString, style = if (face.italic) "italic" else "normal")
    val font = js.Dynamic.newInstance(js.Dynamic.global.FontFace)(face.family, "url(" + fontAddress(face.file) + ")", options)
    dom.document.asInstanceOf[js.Dynamic].fonts.add(font)
    registered.push(font)
    // fetched from now, not when the first screen waits on it: an app registers its faces as it starts, and whatever
    // it does before its first screen (asking its server for something) no longer holds the download back
    font.load()
  }
  private val registered = js.Array[js.Dynamic]()

  // the page's preload of `file`, as it wrote it (a version after it, say), else the bundled faces' own place
  private def fontAddress(file: String): String = {
    val links = dom.document.querySelectorAll("link[rel=preload][as=font]")
    (0 until links.length).map(links(_).getAttribute("href")).find { href =>
      href != null && href.takeWhile(_ != '?').endsWith("/fonts/" + file)
    }.getOrElse("fonts/" + file)
  }

  def screenWidth(): Double = dom.window.innerWidth.toDouble
  def screenHeight(): Double = dom.window.innerHeight.toDouble
  /** Screen pixels to a layout unit: the browser's device pixel ratio (2 on a HiDPI screen, often 1.5 or 2 on a
    * television whose page is laid out smaller than its panel). */
  def pixelRatio(): Double = if (dom.window.devicePixelRatio > 0.0) dom.window.devicePixelRatio else 1.0

  /** `?freeze=<seconds>` in the page URL: step the animation that far on a virtual clock and stop (tools/check.sh).
    * Negative when absent. */
  def freezeSeconds(): Double = {
    val value = new dom.URLSearchParams(dom.window.location.search).get("freeze")
    if (value == null) -1.0 else value.toDoubleOption.getOrElse(-1.0)
  }

  /** The frozen frame is complete. (tools/check.sh screenshots the page itself.) */
  def frozen(): Unit = ()

  /** No other apps to know of here. */
  def installedApps(ids: List[String]): List[String] = Nil

  /** No media servers to find from a page: a browser can send no UDP. */
  def findMediaServers(seconds: Double, found: List[everyui.MediaServer] => Unit): Unit = later(() => found(Nil))

  /** `?name=value` in the page URL, or "" — how a launch chooses a demo (`demo`) and hands it a URL (`socket`). */
  def launchArg(name: String): String = {
    val value = new dom.URLSearchParams(dom.window.location.search).get(name)
    if (value == null) "" else value
  }

  /** A page has nowhere to exit to; a demo that is done just stops. */
  def quit(): Unit = ()

  /** `millis` since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
  def localDateTime(millis: Long): String = {
    val d = new js.Date(millis.toDouble)
    def two(n: Int): String = if (n < 10) "0" + n.toString else n.toString
    d.getFullYear().toInt.toString + "-" + two(d.getMonth().toInt + 1) + "-" + two(d.getDate().toInt) + " " +
      two(d.getHours().toInt) + ":" + two(d.getMinutes().toInt) + ":" + two(d.getSeconds().toInt)
  }

  /** The WebSocket origin of the page itself (`ws://host:port`), so an app served by its own server needs no address. */
  /** Where this app's server is, to resolve its relative URLs against: the page's origin ("https://example.com"). */
  def origin(): String = dom.window.location.origin

  def webSocketOrigin(): String = {
    val l = dom.window.location
    (if (l.protocol == "https:") "wss://" else "ws://") + l.host
  }

  /** Called whenever the window changes size. */
  /** Every key on the page, by its DOM name, pressed or released; a key the hook takes is kept from the browser. */
  def onKey(f: (String, Boolean) => Boolean): Unit = {
    def hear(e: dom.KeyboardEvent, pressed: Boolean): Unit = {
      held = everyui.event.Modifiers(e.ctrlKey, e.metaKey, e.altKey, e.shiftKey)
      embedded = RenderNode.embedOf(dom.document.activeElement)
      if (f(e.key, pressed)) e.preventDefault()
    }
    dom.document.addEventListener("keydown", (e: dom.KeyboardEvent) => hear(e, true))
    dom.document.addEventListener("keyup", (e: dom.KeyboardEvent) => hear(e, false))
  }

  private var held = everyui.event.Modifiers(false, false, false, false)
  private var button = 0

  /** The button of the pointer event being reported: 0 the primary, 1 the middle, 2 the secondary. */
  def pointerButton(): Int = button

  /** A pointer event about to be heard: its button and the modifiers held with it. */
  private[render] def pointed(e: dom.MouseEvent): Unit = {
    held = everyui.event.Modifiers(e.ctrlKey, e.metaKey, e.altKey, e.shiftKey)
    button = if (e.button == 1 || e.button == 2) e.button else 0
  }
  private var embedded = ""

  /** The modifiers held with the key being reported. */
  def modifiers(): everyui.event.Modifiers = held

  /** The embed the key being reported is meant for, "" for none (`RenderNode.embedOf`). */
  def keyEmbed(): String = embedded

  /** Whether shortcuts are made with Command rather than Ctrl here: a Mac, an iPhone or an iPad. */
  def commandKey(): Boolean = {
    val agent = dom.window.navigator.userAgent
    agent.contains("Mac OS") || agent.contains("Macintosh") || agent.contains("iPhone") || agent.contains("iPad")
  }

  def onResize(f: () => Unit): Unit = dom.window.addEventListener("resize", (_: dom.Event) => f())

  // The same four the Roku backend reaches through BrightScript intrinsics.
  /** A number from 0 up to 1, at random. */
  def random(): Double = math.random()
  def sqrt(x: Double): Double = math.sqrt(x)
  def pow(x: Double, y: Double): Double = math.pow(x, y)
  def sin(x: Double): Double = math.sin(x)
  def cos(x: Double): Double = math.cos(x)

  /** A site-absolute asset path (`/images/icon.png`), made relative to the page so the bundle works wherever
    * it is served. */
  def resolvePath(path: String): String = path.stripPrefix("/")

  /** Load `uri` and report its natural size (0 x 0 if it cannot). */
  def loadImage(uri: String, loaded: (Double, Double) => Unit): Unit = {
    val img = dom.document.createElement("img").asInstanceOf[html.Image]
    img.addEventListener("load", (_: dom.Event) => loaded(img.naturalWidth.toDouble, img.naturalHeight.toDouble))
    // one that will not load says so, as 0 x 0, as the other backends do: left unanswered, a picture that failed could
    // not be told from one still coming
    img.addEventListener("error", (_: dom.Event) => loaded(0.0, 0.0))
    img.src = uri
  }

  /** Call `loaded` once a bundled face (index.html's `@font-face`) is usable. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit = {
    val done: js.Function1[js.Any, Unit] = _ => loaded()
    dom.document.asInstanceOf[js.Dynamic].fonts
      .load(weight.toString + " 24px '" + family + "'")
      .applyDynamic("then")(done, done)
  }

  /** Run `f` once the bundled faces have loaded, so text measures with its
    * real font rather than a fallback. (The Roku loads fonts synchronously.) */
  def whenFontsReady(f: () => Unit): Unit = {
    val fonts = dom.document.asInstanceOf[js.Dynamic].fonts
    val loads = js.Array("400 24px 'Open Sans'", "600 24px 'Open Sans'", "400 24px 'Material Icons'")
      .map(face => fonts.load(face)) ++ registered.map(font => font.load())
    // and then a frame drawn with them: what the server wrote into the page is painted in its own faces (a headline
    // that waited for its face) before the app, built at once, draws over it
    val done: js.Function1[js.Any, Unit] = _ => afterFirstPaint(f)
    js.Dynamic.global.Promise.all(loads).applyDynamic("then")(done, done)
  }

  /** Frames as the platform measured them: not here, where the app times its own frames instead. */
  def frameTimings(f: (Double, Double) => Unit): Boolean = false
  def animate(tick: () => Unit): Unit = {
    lazy val frame: Double => Unit = _ =>
      tick()
      dom.window.requestAnimationFrame(frame)
    dom.window.requestAnimationFrame(frame)
  }
}
