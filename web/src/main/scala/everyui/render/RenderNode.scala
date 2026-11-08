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
  def setText(text: String): Unit = {
    counted("text", text)
    el.textContent = text
  }

  // a write to the page, counted where `PerfCounters` counts: whether it wrote what this last wrote there
  private def counted(property: String, value: String): Unit = if (everyui.perf.PerfCounters.on) {
    val d = el.asInstanceOf[js.Dynamic]
    val had = d.selectDynamic("__everyuiWrote")
    val book = if (js.isUndefined(had)) {
      val made = js.Dictionary[String]()
      d.updateDynamic("__everyuiWrote")(made)
      made
    } else had.asInstanceOf[js.Dictionary[String]]
    val same = book.get(property).contains(value)
    // named, for a read after it, with whether it was to a node off the page (laid out as it is lent to the page to
    // be read)
    val what = if (el.isConnected) property else property + " off the page"
    if (property == "text") everyui.perf.PerfCounters.wroteText(same, what)
    else everyui.perf.PerfCounters.wrote(same, what)
    book(property) = value
  }

  def getX(): Double = px(el.style.left)
  def setX(x: Double): Unit = {
    counted("left", x.toString)
    el.style.left = x.toString + "px"
    WebHover.changed()
  }

  def getY(): Double = px(el.style.top)
  def setY(y: Double): Unit = {
    counted("top", y.toString)
    el.style.top = y.toString + "px"
    WebHover.changed()
  }

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
  private def transform(): Unit = {
    turning(rotation != 0.0 || scaled != 1.0, turnedMoving)
    counted("transform", rotation.toString + " " + scaled.toString)
    el.style.transform = "rotate(" + rotation.toString + "rad) scale(" + scaled.toString + ")"
    WebHover.changed()
  }

  def setFontSize(size: Double): Unit = {
    counted("font-size", size.toString)
    el.style.fontSize = size.toString + "px"
  }

  /** `"#rrggbb"`. */
  def setColor(hex: String): Unit = {
    counted("color", hex)
    textColor = hex
    if (!textFilled) el.style.color = hex
    // a field keeps its colour through the browser's autofill look, which would paint its words in its own
    if (el.isInstanceOf[html.Input] || el.isInstanceOf[html.TextArea]) el.style.setProperty("--everyui-fill", hex)
  }

  /** A bundled face (index.html's `@font-face` rules): family, CSS weight,
    * size in pixels. The line box is the shared one (everyui.font.Face), not
    * the browser's `line-height: normal`, so text is the same height on every
    * backend. */
  def setFont(family: String, weight: Int, size: Double): Unit = {
    if (family.nonEmpty) el.style.fontFamily = RenderNode.families(family)
    guessFont =
      weight.toString + " " + size.toString + "px " + (if (family.nonEmpty) RenderNode.families(family) else "")
    guessDefault = family.isEmpty
    el.style.fontWeight = weight.toString
    el.style.fontSize = size.toString + "px"
    el.style.lineHeight = Faces(family, weight).lineHeight(size).toString + "px"
  }

  /** The laid-out size in pixels (text is measured with its real font), shown or not: a component keeps its size out
    * of sight, as it does on every other backend. */
  def getWidth(): Double =
    if (!onPage && RenderNode.movable(el)) RenderNode.offPage(el)._1
    else {
      val r = box()
      if (transformed(r)) laidOut(horizontal = true) else r.width / RenderNode.drawnAt
    }
  /** How wide the words are whole, whatever width the element is given: what they take of the line, past it if they run
    * on (`scrollWidth`), read where it is in the page. */
  def textWidth(): Double = if (onPage) {
    everyui.perf.PerfCounters.read()
    el.scrollWidth / RenderNode.drawnAt
  } else getWidth()
  /** Its width and height, read together: a node out of the page lent to it once for both, where reading each laid
    * the page out for each (the words of a screen being built, read as its rows are laid out). */
  def getSize(): (Double, Double) =
    if (!onPage && RenderNode.movable(el)) RenderNode.offPage(el)
    else (getWidth(), getHeight())
  def getHeight(): Double =
    if (!onPage && RenderNode.movable(el)) RenderNode.offPage(el)._2
    else {
      val r = box()
      if (transformed(r)) laidOut(horizontal = false) else r.height / RenderNode.drawnAt
    }
  /** Whether it is in the page: a node that is not (a screen being built, before it arrives) has no box, and is
    * measured at once, on its own, out of sight (`RenderNode.offPage`), rather than with the turn's batch of reads
    * (`MeasuredFeature`), which would find it still off the page. */
  def onPage: Boolean = el.isConnected

  // Under `display: none` a node has no box and reads 0 by 0: a button made in a hidden panel was an empty pill once
  // the panel showed, since nothing measures it again then. What hides it is laid out, undrawn, for the read instead.
  private def box(): dom.DOMRect = {
    everyui.perf.PerfCounters.read()
    val r = el.getBoundingClientRect()
    if (r.width == 0.0 && r.height == 0.0 && RenderNode.reveal(el)) el.getBoundingClientRect() else r
  }

  // The box on the screen is the laid-out one turned and scaled by every transform on the way up (a row of words in a
  // container scaled by 1.25 measured 1.25 wide, and was spread apart by as much again as the layout placed it), which
  // the whole-pixel layout size (offsetWidth) is not: a box more than a pixel from it is under a transform, past the
  // root's own (the size the app is drawn at, which the box is read over).
  // Only looked at where the node, or one holding it, is turned or scaled (`RenderNode.turned`, marked
  // `data-everyui-turned`): elsewhere its box is under no transform but the root's, and the second read (offsetWidth,
  // which makes the browser lay the page out) is spared.
  private def transformed(r: dom.DOMRect): Boolean =
    RenderNode.turned > 0 && el.closest("[data-everyui-turned]") != null && {
      val at = RenderNode.drawnAt
      math.abs(r.width / at - el.offsetWidth) > 1.0 || math.abs(r.height / at - el.offsetHeight) > 1.0
    }

  // whether it is turned or scaled now, by its own transform (`setRotation`, `setScale`) or its motion's, each counted
  // once in `RenderNode.turned`
  private var turnedStill = false
  private var turnedMoving = false
  private def turning(still: Boolean, moving: Boolean): Unit = {
    val was = turnedStill || turnedMoving
    turnedStill = still
    turnedMoving = moving
    val now = still || moving
    if (now != was) {
      RenderNode.turned += (if (now) 1 else -1)
      if (now) el.setAttribute("data-everyui-turned", "") else el.removeAttribute("data-everyui-turned")
    }
  }

  // The size as laid out, to the fraction of a pixel (what cutting words to fit measures by), untouched by any
  // transform: the used width or height, with the padding and edge a content box leaves out of it.
  private def laidOut(horizontal: Boolean): Double = {
    val s = dom.window.getComputedStyle(el)
    def px(v: String): Double = if (v == null || !v.endsWith("px")) 0.0 else v.dropRight(2).toDoubleOption.getOrElse(0.0)
    val size = px(if (horizontal) s.width else s.height)
    if (size <= 0.0) (if (horizontal) el.offsetWidth else el.offsetHeight)
    else if (s.boxSizing == "border-box") size
    else if (horizontal) size + px(s.paddingLeft) + px(s.paddingRight) + px(s.borderLeftWidth) + px(s.borderRightWidth)
    else size + px(s.paddingTop) + px(s.paddingBottom) + px(s.borderTopWidth) + px(s.borderBottomWidth)
  }

  // the first of the elements a node keeps after its children (a form's submit button), which children go before
  private[render] var tail: dom.Element = null
  def append(child: RenderNode): Unit = place(child.el, tail)
  def insert(child: RenderNode, index: Int): Unit = {
    val before = if (index >= 0 && index < el.children.length) el.children(index) else tail
    if (before eq child.el) () else place(child.el, before)
  }
  // A node already on the page is moved where the browser has a move that keeps it as it is (`moveBefore`): a field
  // moved by insertBefore loses the focus, and with it the menu a password manager had open under it.
  private def place(child: dom.Element, before: dom.Element): Unit = {
    val move = el.asInstanceOf[js.Dynamic].moveBefore
    val moved = !js.isUndefined(move) && child.isConnected && el.isConnected &&
      (try {
        el.asInstanceOf[js.Dynamic].moveBefore(child, before)
        true
      } catch {
        case _: Throwable => false
      })
    if (!moved) el.insertBefore(child, before)
    WebHover.changed()
  }

  /** Detach from the parent, if attached. */
  def remove(): Unit = if (el.parentNode != null) {
    el.parentNode.removeChild(el)
    WebHover.changed()
  }

  def setWidth(width: Double): Unit = {
    counted("width", width.toString)
    el.style.width = width.toString + "px"
    WebHover.changed()
  }
  def setHeight(height: Double): Unit = {
    counted("height", height.toString)
    el.style.height = height.toString + "px"
    WebHover.changed()
  }

  /** `"#rrggbb"`, or `""` for transparent. */
  def setBackgroundColor(hex: String): Unit = {
    counted("background", hex)
    el.style.backgroundColor = if (hex.isEmpty) "transparent" else hex
  }

  def setRadius(radius: Double): Unit = {
    counted("radius", radius.toString)
    el.style.borderRadius = radius.toString + "px"
  }

  /** An edge `width` pixels wide, inside the box (an inset shadow follows the corners and changes no size). */
  def setBorder(width: Double, hex: String): Unit = {
    edge = if (hex.isEmpty || width <= 0.0) "" else "inset 0 0 0 " + width.toString + "px " + hex
    paintShadows()
  }

  /** A linear gradient over the box (`everyui.Gradient`'s encoding), or none for `""`. */
  /** A radial gradient, its centre `x`, `y` and its radius in pixels (the box `width` by `height`), its stops from the
    * centre out. */
  def setRadialGradient(x: Double, y: Double, radius: Double, width: Double, height: Double, stops: String): Unit =
    el.style.backgroundImage = if (stops.isEmpty) "none" else RenderNode.radialGradient(x, y, radius, stops)
  /** How much of it shows, by a gradient's alpha (CSS `mask-image`): linear at `angle` where `radius` is negative, else
    * radial about `x`, `y`; none for `""`. */
  def setMask(angle: Double, x: Double, y: Double, radius: Double, stops: String): Unit = {
    val image = if (stops.isEmpty) "" else if (radius < 0.0) RenderNode.linearGradient(angle, stops)
      else RenderNode.radialGradient(x, y, radius, stops)
    if (image.isEmpty) {
      el.style.removeProperty("mask-image")
      el.style.removeProperty("-webkit-mask-image")
    } else {
      el.style.setProperty("mask-image", image)
      el.style.setProperty("-webkit-mask-image", image)
    }
  }
  /** Every shadow layer, each with its spread (`Shadow.encode`), in place of the one `setShadow` drew. */
  def setShadowLayers(layers: String): Unit = {
    drop = everyui.Shadow.decode(layers).map { case (x, y, blur, spread, hex) =>
      x.toString + "px " + y.toString + "px " + blur.toString + "px " + spread.toString + "px " + hex
    }.mkString(", ")
    paintShadows()
  }
  /** What shows through it blurred (CSS `backdrop-filter`), its own content sharp; 0 for none. */
  def setBackdropBlur(radius: Double): Unit = {
    val value = if (radius <= 0.0) "" else "blur(" + radius.toString + "px)"
    el.style.setProperty("backdrop-filter", value)
    el.style.setProperty("-webkit-backdrop-filter", value)
  }

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
  /** What the motion is for, told when it is set (a Roku logs it for everyui_debug_motion); nothing here. */
  def setMotionOwner(owner: () => String): Unit = ()
  def setMotion(tracks: List[everyui.motion.Track]): Unit = {
    import everyui.motion.Track
    turning(turnedStill, tracks.exists(t => t.property == Track.Rotation || t.property == Track.Scale))
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

  /** Extra space after each letter, in pixels (0 for the face's own); the box, and so the measured width, takes it. */
  /** A variable face's optical sizing ("auto", "none", or "" for the page's) and axes (`font-variation-settings`,
    * "" for none), set on the node itself: a field's own font shorthand (the browser's for an input) would otherwise
    * put them back to auto, and words are measured by their box, so they measure as they are drawn. */
  def setFontAxes(opticalSizing: String, variations: String): Unit = {
    guessOff = opticalSizing == "auto" || variations.nonEmpty
    if (opticalSizing.isEmpty) el.style.removeProperty("font-optical-sizing")
    else el.style.setProperty("font-optical-sizing", opticalSizing)
    if (variations.isEmpty) el.style.removeProperty("font-variation-settings")
    else el.style.setProperty("font-variation-settings", variations)
  }
  def setLetterSpacing(px: Double): Unit = {
    guessSpacing = px
    el.style.letterSpacing = if (px == 0.0) "" else px.toString + "px"
  }

  /** Slanted or upright: CSS font-style, drawn in the family's italic face where one is registered and slanted by the
    * browser where not; the box, and so the measured width, takes it. */
  def setItalic(on: Boolean): Unit = {
    guessItalic = on
    el.style.fontStyle = if (on) "italic" else ""
  }

  /** Where a link goes (the node is an `<a>`, `Nodes.link`), in a new tab with `newTab`. */
  def setHref(href: String, newTab: Boolean): Unit = {
    if (everyui.component.NavigationUrl.safe(href)) el.setAttribute("href", href)
    else el.removeAttribute("href")
    if (newTab) {
      el.setAttribute("target", "_blank")
      el.setAttribute("rel", "noopener")
    }
    else {
      el.removeAttribute("target")
      el.removeAttribute("rel")
    }
  }

  /** The text as styled runs: spans, and `<a>`s for links. A click on a link is `follow`ed (a path on this site shown in
    * place, any other opened, unless the app says otherwise); one clicked with a modifier is the browser's (a new tab). */
  def setRuns(runs: List[everyui.text.TextRun], follow: String => Unit): Unit = {
    el.textContent = ""
    runs.foreach { run =>
      val span = dom.document.createElement(if (run.href.nonEmpty) "a" else "span").asInstanceOf[html.Element]
      if (run.image.isEmpty) span.textContent = run.text
      else {
        // a picture in place of the words, a square as tall as the letters, so the lines are the same before it loads;
        // its words are what is read out, shown on a hover and copied (RichCopy)
        val img = dom.document.createElement("img").asInstanceOf[html.Image]
        img.src = run.image
        img.alt = run.text
        img.title = run.text
        img.setAttribute("draggable", "false")
        img.className = "everyui-run-picture"
        img.style.width = everyui.text.TextRun.pictureEm.toString + "em"
        img.style.height = everyui.text.TextRun.pictureEm.toString + "em"
        img.style.setProperty("object-fit", "contain")
        img.style.verticalAlign = "-0.3em"
        span.appendChild(img)
      }
      // what it is when it is copied (RichCopy): bold, slanted, struck, code
      val marks = (if (run.weight >= 600) "b" else "") + (if (run.italic) "i" else "") + (if (run.strike) "s" else "") +
        (if (run.family.toLowerCase.contains("mono")) "c" else "")
      if (marks.nonEmpty) span.setAttribute("data-run", marks)
      if (run.family.nonEmpty) span.style.fontFamily = RenderNode.families(run.family)
      if (run.weight > 0) span.style.fontWeight = run.weight.toString
      if (run.italic) span.style.fontStyle = "italic"
      if (run.size > 0.0) span.style.fontSize = run.size.toString + "px"
      run.letterSpacing.foreach(px => span.style.letterSpacing = px.toString + "px")
      if (run.color.nonEmpty) span.style.color = run.color
      if (run.strike || run.underline)
        span.style.textDecoration = List(Option.when(run.underline)("underline"), Option.when(run.strike)("line-through"))
          .flatten.mkString(" ")
      if (run.background.nonEmpty) {
        span.style.backgroundColor = run.background
        span.style.padding = "0.08em 0.35em"
        span.style.borderRadius = "5px"
      }
      if (run.href.nonEmpty && everyui.component.NavigationUrl.safe(run.href)) {
        span.setAttribute("href", run.href)
        span.className = "everyui-link"
        val inApp = run.href.startsWith("/") && !run.href.startsWith("//")
        if (!inApp) {
          span.setAttribute("target", "_blank")
          span.setAttribute("rel", "noopener")
        }
        span.addEventListener("click", (e: dom.MouseEvent) => {
          e.stopPropagation()
          if (!RenderNode.modified(e)) {
            e.preventDefault()
            follow(run.href)
          }
        })
        // its right click is the browser's, whatever menu the app has around it: open in a new tab, copy the address
        span.addEventListener("contextmenu", (e: dom.MouseEvent) => e.stopPropagation())
      }
      // the app's to answer (a mention, a link of a scheme of its own: `Link.handle`): pressed as a link is, but no
      // address, to open or to copy; one the app does not answer either goes nowhere, as on every platform
      else if (run.href.nonEmpty) {
        span.setAttribute("role", "link")
        span.style.cursor = "pointer"
        span.addEventListener("click", (e: dom.MouseEvent) => {
          e.stopPropagation()
          e.preventDefault()
          follow(run.href)
        })
      }
      el.appendChild(span)
    }
  }

  /** CSS `position`; `""` is static. */
  /** Whether the mouse is over it, told again as what is under a still mouse moves (WebHover): `over`'s, beside its
    * enter and leave. */
  def onOver(told: Boolean => Unit): Unit = WebHover.watch(el, told)
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click", or "wheel" with its deltas) with its
    * place in the app's units: the viewport's over the size the app is drawn at. */
  def onPointer(kind: PointerKind, f: (Double, Double) => Unit): Unit =
    if (kind == PointerKind.Wheel) el.addEventListener("wheel", (e: dom.WheelEvent) => {
      Platform.pointed(e)
      WheelDelta(e, el, f)
    })
    // the app's context menu in place of the browser's, where it put one up (Pointer.menuAnswered): elsewhere, and
    // with Shift held, the browser's own (Copy, Look Up, Inspect)
    else if (kind == PointerKind.Menu) el.addEventListener("contextmenu", (e: dom.MouseEvent) => if (!e.shiftKey) {
      Platform.pointed(e)
      f(e.clientX / RenderNode.heardAt(e), e.clientY / RenderNode.heardAt(e))
      if (everyui.event.Pointer.menuAnswered) e.preventDefault()
    })
    else {
      val name = if (kind == PointerKind.Click) "click" else "pointer" + kind.toString.toLowerCase
      // a click made of a press on one thing and a release on another is not one (PressOwner)
      if (kind == PointerKind.Click) PressOwner.mark(el)
      el.addEventListener(name, (e: dom.MouseEvent) => {
        if (kind != PointerKind.Click || !PressOwner.splits(e)) {
          Platform.pointed(e)
          // the middle button is the app's (a tab closed), not the browser's scrolling
          if (kind == PointerKind.Down && e.button == 1) e.preventDefault()
          f(e.clientX / RenderNode.heardAt(e), e.clientY / RenderNode.heardAt(e))
        }
      })
      // something that answers a click is clickable: the hand (unless it has a shape of its own, a split's handle's),
      // and no text selection under it
      if (kind == PointerKind.Click) {
        if (el.style.cursor.isEmpty) el.style.cursor = "pointer"
        clickable = true
        if (!selectable) el.style.setProperty("user-select", "none")
      }
    }
  private var clickable = false
  private var selectable = false
  private var hearsWords = false

  /** Its words, and those under it, selected by a drag and copied as the browser does (CSS user-select), under the
    * I-beam; what answers a click in it keeps its own "none". A press on the words is told to `Pointer.pressedText`,
    * so that a drag of the app's (a list's row) does not start there. */
  def setSelectable(on: Boolean): Unit = {
    selectable = on
    val value = if (on) "text" else if (clickable) "none" else ""
    el.style.setProperty("user-select", value)
    el.style.setProperty("-webkit-user-select", value)
    if (on && el.style.cursor.isEmpty) el.style.cursor = "auto"
    if (on && !hearsWords) {
      hearsWords = true
      el.addEventListener("pointerdown", (e: dom.MouseEvent) => if (selectable && onWords(e.target)) everyui.event.Pointer.pressedText())
    }
  }

  // whether `target` is in this with nothing that answers a click between: a link in the words answers one (a press on
  // it is a click, not a selection begun, so a list does not put its rows in reading order under it, which moved the
  // row in the page between the press and the release, and the browser then had no click to give)
  private def onWords(target: dom.EventTarget): Boolean = {
    var at = target.asInstanceOf[dom.Node]
    while (at != null && at != el && !(at.isInstanceOf[html.Element] && {
      val e = at.asInstanceOf[html.Element]
      e.style.getPropertyValue("user-select") == "none" || e.classList.contains("everyui-link") ||
        e.getAttribute("role") == "link"
    })) at = at.parentNode
    at == el
  }

  /** Every word in it selected, as the browser's Select All selects a page's. */
  def selectText(): Unit = dom.window.getSelection().selectAllChildren(el)

  /** Whether words are selected and the selection reaches into it. */
  def holdsSelection(): Boolean = {
    val s = dom.window.getSelection()
    s != null && !s.isCollapsed && s.rangeCount > 0 &&
      s.getRangeAt(0).asInstanceOf[js.Dynamic].intersectsNode(el).asInstanceOf[Boolean]
  }

  /** What it is when selected words are copied (`Component.copyAs`): its `data-copy`, which `RichCopy` reads off the
    * page as a copy is made. */
  def setCopyAs(tag: String): Unit = {
    if (tag.isEmpty) el.removeAttribute("data-copy") else el.setAttribute("data-copy", tag)
    if (tag.nonEmpty) RichCopy.listen()
  }

  /** The node scrolls its own overflow: the browser's scrollbar, wheel, touch and keys. */
  def setScrollable(vertical: Boolean, horizontal: Boolean): Unit = {
    el.style.overflowY = if (vertical) "auto" else "hidden"
    el.style.overflowX = if (horizontal) "auto" else "hidden"
    el.style.setProperty("scrollbar-width", "thin")
    // a scrollbar on the left, in a right-to-left page, kept off the layout
    if (vertical && !el.classList.contains("everyui-scroll")) ScrollGutter.watch(el)
  }
  /** The scrollbar's thumb colour, on a clear track. */
  def setScrollbarColor(hex: String): Unit = el.style.setProperty("scrollbar-color", if (hex.isEmpty) "auto" else hex + " transparent")
  def setScrollTop(y: Double): Unit = el.scrollTop = y
  /** Scrolled `x` sideways from its start edge, which is the right in a right-to-left page. */
  def setScrollLeft(x: Double): Unit = el.scrollLeft = fromStart(x)
  /** Hear the node scroll its own overflow (a wheel, a finger, the scrollbar, or a scroll the app asked for): a
    * ScrollContainer keeps its offset with it. */
  def onScrolled(f: () => Unit): Unit = el.addEventListener("scroll", (_: dom.Event) => f())
  /** A scroll that runs from its left in every language (a timeline): the browser counts it left to right, while what
    * it holds still reads the page's way. */
  def setLeftToRight(): Unit = {
    leftToRight = true
    el.classList.add("everyui-ltr")
  }
  private var leftToRight = false
  /** How far the node has scrolled its own overflow, from its start edge. */
  def scrolled(horizontal: Boolean): Double = if (horizontal) fromStart(el.scrollLeft) else el.scrollTop
  /** Scroll its overflow to `to` as the browser glides a smooth scroll, which a newer one takes over mid-way. */
  def scrollSmoothly(to: Double, horizontal: Boolean): Unit = {
    val options = js.Dynamic.literal(behavior = "smooth")
    if (horizontal) options.left = fromStart(to) else options.top = to
    el.asInstanceOf[js.Dynamic].scrollTo(options)
  }
  // a sideways scroll from its start edge as the browser counts it, and back again: in a right-to-left page the start
  // is the right, which a browser counts down from 0 (as the spec has it), up from 0 (the old Edge), or down from the
  // far end (Chrome before 85, as on an older television)
  private def fromStart(x: Double): Double =
    if (leftToRight || dom.document.documentElement.getAttribute("dir") != "rtl") x
    else RenderNode.rtlScroll match {
      case RenderNode.Negative => -x
      case RenderNode.Reverse => x
      case _ => el.scrollWidth - el.clientWidth - x
    }
  /** Whether the platform draws this: what a scroll has out of sight is not drawn at all. */
  def setVisible(visible: Boolean): Unit = {
    counted("display", visible.toString)
    el.style.display = if (visible) "" else "none"
    WebHover.changed()
    // no longer laid out only to be measured (RenderNode.reveal)
    if (el.style.visibility.nonEmpty) el.style.visibility = ""
  }
  def setOpacity(opacity: Double): Unit = {
    counted("opacity", opacity.toString)
    el.style.opacity = opacity.toString
  }

  /** Clipping is the browser's overflow; a scrollable node already clips. */
  def setClip(width: Double, height: Double): Unit = if (el.style.overflowY != "auto") el.style.overflow = "hidden"
  /** Cut to its box by its container (`Container.clipsToBox`): `overflow: clip`, which unlike `hidden` makes no scroll
    * box, so nothing in it is scrolled into sight within it as it takes the focus, and its own shadow is drawn whole. A
    * clip of its own (`setClip`, a scroll's) is never undone; a browser without `clip` shows it whole, as before. */
  def setBoxClip(on: Boolean, width: Double, height: Double): Unit =
    if (on) {
      val unset = el.style.overflow.isEmpty && el.style.overflowX.isEmpty && el.style.overflowY.isEmpty
      if (unset) el.style.overflow = "clip"
    }
    else if (el.style.overflow == "clip") el.style.overflow = ""
  def setTooltip(text: String): Unit = el.title = text
  // the hidden element holding its description, last among its own, where it has one
  private var described: Option[html.Element] = None
  /** What a screen reader hears of it after its name (a list row's tip): `aria-describedby` pointing at a hidden element of
    * its own, kept after its children, which a reader of every browser takes a description from and which goes with it. */
  def setDescription(text: String): Unit = if (text.isEmpty) {
    described.foreach { d =>
      if (tail eq d) tail = null
      el.removeChild(d)
      el.removeAttribute("aria-describedby")
    }
    described = None
  } else {
    val d = described.getOrElse {
      val made = dom.document.createElement("div").asInstanceOf[html.Element]
      made.id = RenderNode.describedId()
      made.setAttribute("hidden", "")
      el.appendChild(made)
      if (tail == null) tail = made
      el.setAttribute("aria-describedby", made.id)
      described = Some(made)
      made
    }
    d.textContent = text
  }
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
        shape.setAttribute("fill-rule", if (f.evenOdd) "evenodd" else "nonzero")
        shape.setAttribute("stroke", f.stroke.map(_.toHex).getOrElse("none"))
        shape.setAttribute("stroke-opacity", f.stroke.map(_.alpha.toString).getOrElse("1"))
        shape.setAttribute("stroke-width", f.width.toString)
        shape = shape.nextElementSibling
      }
    }
  }
  /** A finger dragged on it drags it, rather than scrolling or zooming the page (CSS touch-action). */
  def setTouchDrags(on: Boolean): Unit = el.style.setProperty("touch-action", if (on) "none" else "")
  /** A finger dragged up or down on it is the app's (a list that scrolls itself), while one dragged across, or two
    * pinching, are still the browser's: left to the browser, a vertical drag was taken for the page's own pan a few
    * pixels in, and the app heard no more of it (pointercancel). */
  def setTouchScrolls(on: Boolean): Unit = el.style.setProperty("touch-action", if (on) "pan-x pinch-zoom" else "")
  /** How far a list that scrolls itself has scrolled, and how far it can, in pixels, for a test or a tool to read off
    * the page where it has no `scrollTop` of its own: `data-everyui-scroll` and `data-everyui-scroll-max`. */
  def setScrolled(at: Double, furthest: Double): Unit = {
    el.setAttribute("data-everyui-scroll", (math.round(at * 10.0) / 10.0).toString)
    el.setAttribute("data-everyui-scroll-max", (math.round(furthest * 10.0) / 10.0).toString)
  }
  /** The pointer's shape over it, by its CSS name ("col-resize", "text"); "" leaves the page's own. */
  def setCursor(name: String): Unit = el.style.cursor = name

  def setPositionType(name: String): Unit = el.style.position = if (name.isEmpty) "static" else name

  def setWhiteSpace(value: String): Unit = el.style.whiteSpace = value

  def setImageSource(uri: String): Unit = {
    el.setAttribute("src", uri)
    // a tint set before the picture came masks with it now: set first, it had masked with no picture, and drew nothing
    if (tint.nonEmpty) masked()
  }

  // ---- a `video` or `audio` element (`Nodes.video`, `Nodes.audio`); on any other node these do nothing ----
  private def media: Option[js.Dynamic] =
    if (el.tagName == "VIDEO" || el.tagName == "AUDIO") Some(el.asInstanceOf[js.Dynamic]) else None
  // the playing itself, the browser's own or hls.js's (WebMedia)
  private lazy val webMedia = new WebMedia(el)

  /** Sent with every request an HLS stream makes through hls.js; the browser's own player sends none, so a plain
    * file's token must ride in its address. */
  def setMediaHeaders(headers: Map[String, String]): Unit = if (media.nonEmpty) webMedia.headers = headers

  def setMediaSource(url: String): Unit = if (media.nonEmpty) webMedia.source(url)
  /** Where the next source starts, in seconds. */
  def setMediaStart(seconds: Double): Unit = if (media.nonEmpty) webMedia.start = seconds
  // a page could add a <track> for each; not yet
  def setMediaSidecars(sidecars: List[everyui.component.MediaSidecar]): Unit = ()
  def setMediaPlaying(playing: Boolean): Unit = if (media.nonEmpty) webMedia.play(playing)
  def setMediaLoop(loop: Boolean): Unit = media.foreach(_.loop = loop)
  def setMediaMuted(muted: Boolean): Unit = media.foreach(_.muted = muted)
  def setMediaVolume(volume: Double): Unit = media.foreach(_.volume = math.min(1.0, volume)) // a page cannot boost
  def setMediaRate(rate: Double): Unit = media.foreach(_.playbackRate = rate)
  def mediaRates(): Boolean = true
  /** Whether the media has enough in hand to play to its end without stopping, as the browser judges it: its
    * `canplaythrough` (HAVE_ENOUGH_DATA). A `Backdrop` waits for it before its video takes the picture's place. */
  def mediaPlaysThrough(): Boolean = media.exists(_.readyState.asInstanceOf[Int] >= 4)
  /** The centre of a surround source folded to stereo `db` dB over the usual, through Web Audio where the page may hear
    * it (CenterFold), and whether that can be and is. */
  def setMediaCenterMix(db: Double): Unit = if (media.nonEmpty) webMedia.centerMix(everyui.audio.Downmix.clamp(db))
  def mediaCenterMixes(): Boolean = media.nonEmpty && webMedia.centerMixes
  def mediaCenterMixing(): Boolean = media.nonEmpty && webMedia.centerMixing
  /** Voice clarity and night mode, 0 to 1 each, in the same Web Audio graph as the centre ([[ListeningNodes]]): where
    * the centre can be mixed, and heard once the sound is taken into it. */
  def setMediaAids(clarity: Double, night: Double): Unit =
    if (media.nonEmpty) webMedia.aids(everyui.audio.VoiceClarity.clamp(clarity), everyui.audio.NightMode.clamp(night))
  def mediaAidsSupported(): Boolean = media.nonEmpty && webMedia.centerMixes
  def mediaAidsHeard(): Boolean = media.nonEmpty && webMedia.aidsHeard
  // what hls.js holds ahead; the browser's own player cannot be told
  def setMediaBuffering(named: String): Unit = if (media.nonEmpty) webMedia.buffering = named
  // the browser keeps what its own cache allows, and a page has no other store for a clip
  def setMediaCached(cached: Boolean): Unit = ()
  def mediaBuffering(): Boolean = media.nonEmpty && webMedia.waiting
  // what hls.js has measured; the browser's own player does not count
  def mediaBandwidth(): Long = if (media.nonEmpty) webMedia.bandwidth else 0L
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
  // hls.js says what it retries; a `video` retries by itself and says nothing of it
  def mediaRetry(): Option[everyui.component.MediaRetry] = if (media.nonEmpty) webMedia.retry else None
  // nothing here shows what is playing, or hears it
  def setNowPlaying(about: everyui.component.NowPlaying): Unit = ()
  def setMediaSpectrum(on: Boolean): Unit = ()
  def mediaBands(): List[Double] = Nil

  // ---- a page of the web (Nodes.web): an iframe, told things and heard through a WebFrame ----
  private[render] var webFrame: WebFrame = null
  def setWebSource(url: String): Unit = if (webFrame != null) webFrame.source(url)
  // the browser says who is asking, and a page cannot be told otherwise
  def setWebAgent(agent: String): Unit = ()
  def setWebSandbox(flags: Option[Set[String]]): Unit = if (webFrame != null) webFrame.sandbox(flags)
  /** The frame itself drawn clear rather than black, so a transparent page shows what is behind it. */
  def setWebClear(clear: Boolean): Unit = if (webFrame != null) el.style.background = if (clear) "transparent" else "#000"
  def runWebScript(script: String): Unit = if (webFrame != null) webFrame.run(script)
  def onWebMessage(f: String => Unit): Unit = if (webFrame != null) webFrame.hear(f)
  def setWebHearSandboxed(on: Boolean): Unit = if (webFrame != null) webFrame.hearSandboxed(on)
  def askWeb(id: Int, request: String): Boolean = webFrame != null && webFrame.ask(id, request)
  def onWebReply(f: (Int, String) => Unit): Unit = if (webFrame != null) webFrame.hearReplies(f)
  def onWebFrameMessage(f: Option[(String, String) => Unit]): Unit = if (webFrame != null) webFrame.nested(f)
  def releaseWeb(): Unit = if (webFrame != null) webFrame.release()

  // ---- a rich document's editing surface (Nodes.richEditor): a WebRichEditor ----
  private[render] var richEditor: WebRichEditor = null
  def setRichDoc(doc: everyui.rich.RichDoc, selection: everyui.rich.RichRange): Unit =
    if (richEditor != null) richEditor.draw(doc, selection)
  def setRichLook(look: everyui.rich.RichLook, placeholder: String, readOnly: Boolean): Unit =
    if (richEditor != null) richEditor.look(look, placeholder, readOnly)
  /** A suggestion drawn dimmed after `at` (`WebRichEditor.setGhost`); no lines takes it away. */
  def setRichGhost(at: everyui.rich.RichPos, lines: List[String]): Unit =
    if (richEditor != null) richEditor.setGhost(at, lines)
  def onRichInput(f: everyui.rich.RichInput => Unit): Unit = if (richEditor != null) richEditor.hear(f)
  def onRichHeight(f: Double => Unit): Unit = if (richEditor != null) richEditor.heights(f)
  def focusRich(selection: everyui.rich.RichRange): Unit = if (richEditor != null) richEditor.focus(selection)
  def richSelection(): Option[everyui.rich.RichRange] = Option(richEditor).map(_.selection())

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

  /** What to hear and read this in: chosen as an HLS stream opens through hls.js; the browser's own player chooses
    * its own. */
  def setMediaLanguages(audio: String, text: String): Unit = if (media.nonEmpty) webMedia.languages(audio, text)

  /** What hls.js decoded, and the last error. */
  def mediaDiagnostics(): everyui.component.MediaDiagnostics =
    if (media.nonEmpty) webMedia.diagnostics else everyui.component.MediaDiagnostics()

  def mediaTracks(): List[everyui.component.MediaTrack] = if (media.nonEmpty) webMedia.tracks else Nil
  def selectMediaTrack(kind: String, id: String): Unit = if (media.nonEmpty) webMedia.select(kind, id)
  // no device setting to follow here: the device's is none
  def setMediaText(choice: everyui.component.TextChoice): Unit =
    if (media.nonEmpty)
      webMedia.select(everyui.component.MediaTrack.Text, everyui.component.TextChoice.asTrack(choice).getOrElse(""))
  def setMediaCover(cover: Boolean): Unit = el.style.setProperty("object-fit", if (cover) "cover" else "contain")
  def setMediaDirect(direct: Boolean): Unit = ()
  // an effect (AudioPlayer.effect) plays here as any sound does, mixed with the rest
  def setMediaEffect(effect: Boolean): Unit = ()
  def getMediaPosition(): Double = media.map(_.currentTime.asInstanceOf[Double]).getOrElse(0.0)
  // a browser answers NaN until the metadata is in, and Infinity for a live stream: neither is a length
  def getMediaDuration(): Double =
    media.map(_.duration.asInstanceOf[Double]).filter(d => !d.isNaN && !d.isInfinite && d > 0.0).getOrElse(0.0)
  def seekMedia(seconds: Double): Unit = media.foreach(_.currentTime = seconds)
  def onMediaEvent(f: String => Unit): Unit = if (media.nonEmpty) {
    el.addEventListener("playing", (_: dom.Event) => f("ready"))
    el.addEventListener("ended", (_: dom.Event) => f("ended"))
    webMedia.listen(f)
  }
  def releaseMedia(): Unit = setMediaSource("")

  /** The picture in one colour, its transparency kept: the box painted the colour through the picture as a mask, and
    * the picture itself drawn out of sight. */
  def setTint(hex: String): Unit = {
    tint = hex
    masked()
  }
  // the tint, and the picture masked by it as it is now (none until it has a source, which masks it as it comes)
  private var tint = ""
  private def masked(): Unit = {
    val source = Option(el.getAttribute("src")).getOrElse("")
    val tinted = tint.nonEmpty
    el.style.backgroundColor = if (tinted && source.nonEmpty) tint else "transparent"
    el.style.setProperty("object-position", if (tinted) "-100000px 0" else "")
    List("mask", "-webkit-mask").foreach { p =>
      el.style.setProperty(p + "-image", if (tinted && source.nonEmpty) "url(\"" + source + "\")" else "none")
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

  /** Whether what it holds is wrong (`aria-invalid`); why is said in its description (`Component.invalid`). */
  def setInvalid(on: Boolean): Unit =
    if (on) el.setAttribute("aria-invalid", "true") else el.removeAttribute("aria-invalid")
  /** Whether it is being updated (`aria-busy`). */
  def setUpdating(on: Boolean): Unit = if (on) el.setAttribute("aria-busy", "true") else el.removeAttribute("aria-busy")

  /** Where a slider stands (`aria-valuenow`, its range and its words), whether a switch is on (`aria-checked`),
    * whether a toggle button is pressed (`aria-pressed`), whether what a button opens is open (`aria-expanded`),
    * whether a row is chosen (`aria-selected`), which way a column header sorts (`aria-sort`), and whether it is the
    * current one of its set (`aria-current`). */
  def setSemanticState(range: Option[everyui.component.types.SemanticRange], checked: Option[Boolean],
                       expanded: Option[Boolean], selected: Option[Boolean], sort: String,
                       current: Boolean, description: String): Unit = {
    def attribute(name: String, value: Option[String]): Unit = value match {
      case Some(v) => el.setAttribute(name, v)
      case None => el.removeAttribute(name)
    }
    attribute("aria-valuenow", range.map(_.now.toString))
    attribute("aria-valuemin", range.map(_.min.toString))
    attribute("aria-valuemax", range.map(_.max.toString))
    attribute("aria-valuetext", range.map(_.text))
    // a button's checked is whether it stays pressed (a toggle button), a switch's whether it is on
    val button = el.getAttribute("role") == "button"
    attribute("aria-checked", if (button) None else checked.map(_.toString))
    attribute("aria-pressed", if (button) checked.map(_.toString) else None)
    attribute("aria-expanded", expanded.map(_.toString))
    attribute("aria-selected", selected.map(_.toString))
    attribute("aria-sort", if (sort.isEmpty) None else Some(sort))
    attribute("aria-current", if (current) Some("true") else None)
    attribute("aria-description", if (description.isEmpty) None else Some(description))
  }

  /** The focus landed here: the page's own focus put on this element too, so a screen reader follows it and reads it
    * by its role and label (setSemantics), while the keys are still heard by the app (`onKey`, on the document). The
    * element is focusable only while it holds it (tabindex -1, out of the browser's Tab order, so a click elsewhere
    * focuses it no more than before), draws no outline of the browser's (the app draws its own) and scrolls nothing.
    * A field, an editor or an embed takes the page's focus itself, and a frame or a password manager's menu holding
    * it keeps it; once a key being pressed has done what it does (`afterKey`). */
  /** A ring in `hex` round the element, outside its box, where the focus layer draws one for it (`Focus.ring`); ""
    * takes it away. */
  def setFocusRing(hex: String, radius: Double): Unit =
    if (hex.isEmpty) {
      el.style.removeProperty("outline")
      el.style.removeProperty("outline-offset")
    }
    else {
      el.style.outline = "2.5px solid " + hex
      el.style.setProperty("outline-offset", "2px")
    }

  def accessibleFocus(click: () => Unit, name: () => String): Unit = RenderNode.afterKey(() => {
    val active = dom.document.activeElement
    if (!(RenderNode.reading eq el)) RenderNode.release()
    val takes = RenderNode.editable(el) || el.hasAttribute("data-everyui-embed") ||
      el.querySelector("input, textarea, select, [contenteditable], [data-everyui-embed]") != null
    val holds = active != null && el.contains(active)
    if (el.isConnected && !takes && !holds && RenderNode.embedOf(active) != "field") {
      if (!(RenderNode.reading eq el) && !el.hasAttribute("tabindex")) {
        RenderNode.reading = el
        el.setAttribute("tabindex", "-1")
        el.setAttribute("data-everyui-held", "")
      }
      el.asInstanceOf[js.Dynamic].focus(js.Dynamic.literal(preventScroll = true))
    }
  })

  /** The row under a list's cursor (`VirtualList`) said to a screen reader as the list's active one
    * (`aria-activedescendant`), the page's focus staying on the list: where the list is one a reader moves through so
    * (a list box, a tree, a grid); else, as a list of a conversation, its words said aloud (`Platform.announce`). */
  def setActiveRow(row: RenderNode, name: () => String): Unit = el.getAttribute("role") match {
    case "listbox" | "tree" | "grid" | "treegrid" =>
      if (row.el.id.isEmpty) {
        RenderNode.rowIds += 1
        row.el.id = "everyui-row-" + RenderNode.rowIds
      }
      // set afresh, so the same element showing another row (scrolled into its place) is said again
      el.removeAttribute("aria-activedescendant")
      el.setAttribute("aria-activedescendant", row.el.id)
    case _ => Platform.announce(name())
  }

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
  /** Which way the words run: "" the page's, "auto" their own (the first letter with a direction), "ltr" or "rtl". */
  def setTextDirection(value: String): Unit = if (value.isEmpty) el.removeAttribute("dir") else el.setAttribute("dir", value)
  /** A word too long for its line broken where it must be (a URL, a path), so it never runs past the box. */
  def setWrapAnywhere(on: Boolean): Unit = el.style.setProperty("overflow-wrap", if (on) "anywhere" else "")
  /** Words cut short to fit (`TextView.maxWidth`, `maxLines`) held to their room by the page until the cut is made
    * (TextFit). */
  /** Whether words held to a limit (`setTextLimit`) wrap at it here: they do. */
  def wrapsToLimit(): Boolean = true
  def setTextLimit(width: Double, lines: Int): Unit = TextFit.limit(el, width, lines)
  /** Words that wrap measured whole on one line, and wrapped only at the limit they are held to (`setTextLimit`), not at
    * the width of what holds them: a label in a box sized to it would otherwise be squeezed with it word by word. */
  def setTextWhole(on: Boolean): Unit =
    if (on) el.style.width = "max-content" else if (el.style.width == "max-content") el.style.width = ""
  /** Whether the words measure as they will be drawn: on the page, in a face that has loaded (TextFit); `ready` once a
    * face they wait for has. */
  def textMeasurable(ready: () => Unit): Boolean = TextFit.ready(el, ready)

  // what a guess at the words' width measures them in (`textWidthGuess`): the face, weight and size as set, whether the
  // face is the page's own (set on none), the slant and the spacing, and whether something the canvas does not follow
  // is set (variable axes, optical sizing)
  private var guessFont = ""
  private var guessDefault = true
  private var guessItalic = false
  private var guessSpacing = 0.0
  private var guessOff = false

  /** How wide the words are on one line, worked out on a canvas without the page laying anything out, in the app's
    * units; -1 where it cannot be (no size set yet, axes set, words on more than one line, not on the page). For telling
    * words that surely fit from those to be measured on the page (`TextView`): a guess, not a measure. */
  def textWidthGuess(): Double =
    if (guessFont.isEmpty || guessOff || !el.isConnected) -1.0
    else {
      val words = el.textContent
      if (words.contains("\n")) -1.0
      else {
        val font = (if (guessItalic) "italic " else "") +
          (if (guessDefault) guessFont + RenderNode.pageFamily(el) else guessFont)
        val ctx = RenderNode.guessing
        if (ctx.font != font) ctx.font = font
        ctx.measureText(words).width + guessSpacing * words.length
      }
    }

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
  /** A drop-down's look: its words set in `inset` from its edges (the select fills its box, so a click anywhere on it
    * opens it and its list lines up with it), and its open list in the theme's colours (`"#rrggbb"`), for the page's
    * rules (`RenderNode.selectStyle`): its options on `background` in `text`, the one under the pointer or the keys on
    * `highlight`, the chosen one in `chosen`, the list's edge `edge`. The browser is told whether the list is dark
    * (`color-scheme`), for what it draws of its own: a select's own background is clear, which left its list white
    * under the theme's light words. */
  def setDropDown(background: String, text: String, highlight: String, chosen: String, edge: String,
                  inset: Double): Unit = {
    el.style.setProperty("padding-inline", inset.toString + "px")
    el.style.setProperty("--everyui-list", background)
    el.style.setProperty("--everyui-list-text", text)
    el.style.setProperty("--everyui-list-lit", highlight)
    el.style.setProperty("--everyui-list-chosen", chosen)
    el.style.setProperty("--everyui-list-edge", edge)
    el.style.setProperty("color-scheme", if (RenderNode.light(background)) "light" else "dark")
  }
  def setPlaceholder(text: String): Unit = el.setAttribute("placeholder", text)
  /** What the keyboard's enter key says it does ("done", "search", "next", "go", "send"). */
  def setInputAction(action: String): Unit = el.setAttribute("enterkeyhint", action)
  // whether the field was made for a secret: a password hint hides what is typed, and dropping the hint does not show it
  private var madeSecret: Option[Boolean] = None

  /** What the field asks for, by its `autocomplete` token (`everyui.component.types.Autofill.token`), in `section`
    * ("shipping", "billing", or the app's own name for a group), named `name` (else by the token), and for a username
    * whether it offers passkeys too (`webauthn`). The browser's autofill and a password manager go by the
    * `autocomplete`, by the `name` and `id` that match it and by a password's type; a phone shows the keyboard for it
    * (`inputmode`: the type otherwise stays text, which keeps the caret and the selection the page's to set). "off"
    * keeps them all out (a search box, notes, a guest's name), with the markers 1Password, LastPass, Bitwarden and
    * Dashlane go by; "" says nothing. `numeric` asks for a phone's number pad whatever the token, a password's too. */
  def setAutofill(asked: String, section: String, name: String, passkey: Boolean, literal: Boolean,
    numeric: Boolean): Unit = {
    def attribute(key: String, value: String): Unit =
      if (value.isEmpty) el.removeAttribute(key) else el.setAttribute(key, value)
    // a code of letters and digits is a one-time code to the browser, typed on the whole keyboard
    val lettered = asked == "one-time-code-text"
    val token = if (lettered) "one-time-code" else asked
    val off = token == "off"
    val known = token.nonEmpty && !off
    val group = if (section.isEmpty || !known) "" else if (section == "shipping" || section == "billing") section + " "
      else "section-" + section + " "
    attribute("autocomplete", if (token.isEmpty) "" else group + token + (if (passkey && known) " webauthn" else ""))
    val named = if (name.nonEmpty) name else if (known) token else ""
    if (named.nonEmpty) {
      el.setAttribute("name", named)
      // an id kept once given, so a manager that saw the field knows it again; two asking the same are kept apart
      if (name.nonEmpty) el.id = name else if (el.id.isEmpty) el.id = RenderNode.fieldId(token)
    }
    el match {
      case i: html.Input =>
        if (madeSecret.isEmpty) madeSecret = Some(i.`type` == "password")
        i.`type` = if (madeSecret.contains(true) || token == "current-password" || token == "new-password") "password" else "text"
      case _ => ()
    }
    attribute("inputmode", if (numeric) "numeric" else token match {
      case "email" => "email"
      case "tel" => "tel"
      case "url" => "url"
      case "one-time-code" | "cc-number" | "cc-csc" => if (lettered) "" else "numeric"
      case _ => ""
    })
    // what is typed as it is meant, not as the phone would correct it: what these fill, or a field held
    // literal or numeric
    val asTyped = literal || numeric ||
      Set("username", "email", "url", "current-password", "new-password", "one-time-code").contains(token)
    attribute("spellcheck", if (asTyped) "false" else "")
    attribute("autocapitalize", if (asTyped) "none" else "")
    attribute("autocorrect", if (asTyped) "off" else "")
    List("data-1p-ignore", "data-lpignore", "data-bwignore").foreach(a => attribute(a, if (off) "true" else ""))
    attribute("data-form-type", if (off) "other" else "")
  }
  /** A form's sending (`Nodes.form`): Enter in one of its fields, or a password manager clicking its submit button.
    * The page is never sent anywhere; `f` is told instead. */
  def onSubmit(f: () => Unit): Unit = el.addEventListener("submit", (e: dom.Event) => {
    e.preventDefault()
    f()
  })
  /** A form is done with (`everyui.form.Form`): accepted, its fields leave the page, which the browser and the password
    * managers take as a sign-in done and offer to save; given up, nothing more is said. */
  def formDone(saved: Boolean): Unit =
    if (saved) el.querySelectorAll("input, textarea").foreach(f => if (f.parentNode != null) f.parentNode.removeChild(f))
  /** The account a password-only step is for: a hidden username field in the form, which the managers save the
    * password against. Empty takes it away. */
  def setHiddenUsername(username: String): Unit = {
    val existing = el.querySelector("input[data-everyui-username]")
    if (username.isEmpty) {
      if (existing != null) {
        if (tail eq existing) tail = existing.nextElementSibling
        el.removeChild(existing)
      }
    }
    else {
      val field = if (existing != null) existing.asInstanceOf[html.Input] else {
        val made = dom.document.createElement("input").asInstanceOf[html.Input]
        made.`type` = "text"
        made.name = "username"
        made.setAttribute("autocomplete", "username")
        made.setAttribute("data-everyui-username", "")
        made.setAttribute("aria-hidden", "true")
        made.tabIndex = -1
        made.style.display = "none"
        el.insertBefore(made, tail)
        tail = made
        made
      }
      field.value = username
    }
  }
  /** Every edit, with the new contents: typed (`input`), or filled by the browser or a password manager, which say
    * so with `input` and `change`, or only by Chrome's autofill look (the `everyui-autofill` animation starting); and
    * read again as the field is left, for a value set with no word at all. */
  def onValue(f: String => Unit): Unit = {
    val changed: js.Function1[dom.Event, Unit] = (_: dom.Event) => f(getValue())
    List("input", "change", "blur").foreach(name => el.addEventListener(name, changed))
    el.addEventListener("animationstart", (e: dom.Event) =>
      if (e.asInstanceOf[js.Dynamic].animationName.asInstanceOf[String] == "everyui-autofill") f(getValue()))
  }
  /** A paste into the field, put to `f` with its words before they land (`Pasting`). */
  def onPaste(f: String => everyui.component.types.PasteAction): Unit = Pasting.listen(el, f)
  /** The field taking the keyboard itself (a click, a tap, the browser's own Tab). */
  // focusin, which a rich editor's editable element inside the box sends up as a field sends it of itself
  def onInputFocus(f: () => Unit): Unit = el.addEventListener("focusin", (_: dom.Event) => f())
  /** The field losing the keyboard; and, where the person put the page's focus on nothing, the focus layer's let go of
    * too (`FieldBlur`). */
  def onInputDone(f: () => Unit): Unit = {
    el.addEventListener("blur", (_: dom.Event) => f())
    FieldBlur.watch(el)
  }
  /** Enter in the field, its keyboard's action key. */
  def onInputAction(f: () => Unit): Unit =
    el.addEventListener("keydown", (e: dom.KeyboardEvent) => if (e.key == "Enter" && !e.shiftKey) f())
  /** All of what the field holds chosen, so what is typed next replaces it. */
  def selectAllInput(): Unit = el.asInstanceOf[js.Dynamic].select()
  /** Where the field's caret is, counted in the words' UTF-16 units from the start: the start of any selection. */
  def getCaret(): Int = el match {
    case i: html.Input => i.selectionStart
    case t: html.TextArea => t.selectionStart
    case _ => 0
  }
  /** The field's caret put at `at` (as `getCaret` counts), nothing chosen. */
  def setCaret(at: Int): Unit = el match {
    case i: html.Input => i.setSelectionRange(at, at)
    case t: html.TextArea => t.setSelectionRange(at, at)
    case _ => ()
  }
  /** Where what is chosen in the field starts and ends, as `getCaret` counts: the caret, both, where nothing is. */
  def getSelectionStart(): Int = getCaret()
  def getSelectionEnd(): Int = el match {
    case i: html.Input => i.selectionEnd
    case t: html.TextArea => t.selectionEnd
    case _ => 0
  }
  /** `start` to `end` chosen in the field. */
  def setSelection(start: Int, end: Int): Unit = el match {
    case i: html.Input => i.setSelectionRange(start, end)
    case t: html.TextArea => t.setSelectionRange(start, end)
    case _ => ()
  }
  /** `start` to `end` replaced by `words` as if typed, so the browser's undo takes it back: chosen, then the browser's
    * own insertText where it still has it (the one way into a field's undo), else set in place, the field told of it
    * as typing tells it. The caret after the words. */
  def replaceInput(start: Int, end: Int, words: String): Unit = el match {
    case _: html.Input | _: html.TextArea =>
      val was = dom.document.activeElement
      if (!(was eq el)) el.asInstanceOf[html.Element].focus()
      setSelection(start, end)
      val typed = try dom.document.asInstanceOf[js.Dynamic].execCommand("insertText", false, words).asInstanceOf[Boolean]
      catch { case _: Throwable => false }
      if (!typed) {
        el.asInstanceOf[js.Dynamic].setRangeText(words, start, end, "end")
        el.dispatchEvent(new dom.Event("input", new dom.EventInit { bubbles = true }))
      }
    case _ => ()
  }
  /** What is chosen, told as it changes: by the pointer, the keys, typing or the app; once for each change. */
  def onSelection(f: (Int, Int) => Unit): Unit = el match {
    case _: html.Input | _: html.TextArea =>
      var last = (-1, -1)
      val look: js.Function1[dom.Event, Unit] = _ => {
        val now = (getSelectionStart(), getSelectionEnd())
        if (now != last) {
          last = now
          f(now._1, now._2)
        }
      }
      // the field's own selectionchange where the browser has it, and where it does not, what moves the selection
      List("selectionchange", "select", "input", "keyup", "pointerup", "focus").foreach(el.addEventListener(_, look))
    case _ => ()
  }
  /** Out of use, or back in it: a screen reader hears so (`aria-disabled`), and a field keeps its words and its caret
    * but takes no typing (read-only, which unlike disabled leaves it where the focus can reach it). */
  def setDisabled(disabled: Boolean): Unit = {
    if (disabled) el.setAttribute("aria-disabled", "true") else el.removeAttribute("aria-disabled")
    fieldDisabled = disabled
    el match {
      case i: html.Input => i.readOnly = disabled || fieldReadOnly
      case t: html.TextArea => t.readOnly = disabled || fieldReadOnly
      case _ => ()
    }
  }
  /** Read-only, or editable again: a field shows its words, which can be chosen and copied, but takes no typing and
    * no paste (the readonly attribute, which a screen reader hears); unlike disabled it is not out of use. */
  def setReadOnly(on: Boolean): Unit = {
    fieldReadOnly = on
    if (on) el.setAttribute("aria-readonly", "true") else el.removeAttribute("aria-readonly")
    el match {
      case i: html.Input => i.readOnly = on || fieldDisabled
      case t: html.TextArea => t.readOnly = on || fieldDisabled
      case _ => ()
    }
  }
  private var fieldDisabled: Boolean = false
  private var fieldReadOnly: Boolean = false
  /** Give the field the keyboard, or take it away. */
  // never taken back from something the app did not make (a password manager's menu, a frame) while the page's focus
  // is in it
  def focusInput(on: Boolean): Unit =
    if (on && dom.document.activeElement != el && RenderNode.embedOf(dom.document.activeElement) != "field")
      RenderNode.afterKey(() => el.focus())
    else if (!on && dom.document.activeElement == el) {
      FieldBlur.blurring = true
      try el.blur() finally FieldBlur.blurring = false
    }

  private var rotation: Double = 0.0
  private var edge: String = ""
  private var drop: String = ""
  private var textColor: String = ""
  private var textFilled: Boolean = false

  private def px(value: String): Double =
    value.stripSuffix("px").toDoubleOption.getOrElse(0.0)
}

object RenderNode {
  /** How many of the app's nodes are turned or scaled now, by their own transform or a motion's: while none is, a box
    * read is the laid-out one, and nothing is read twice to tell (`transformed`). */
  private[render] var turned: Int = 0
  // the canvas words are guessed at on (`textWidthGuess`), and the page's own face, read once
  private[render] lazy val guessing: dom.CanvasRenderingContext2D = dom.document.createElement("canvas")
    .asInstanceOf[html.Canvas].getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private var pageFace = ""
  private[render] def pageFamily(el: html.Element): String = {
    if (pageFace.isEmpty) pageFace = dom.window.getComputedStyle(el).fontFamily
    pageFace
  }
  /** Whether a key is being pressed now (`Platform.onKey`): the browser does what the key does by default once the
    * app has heard it, to whatever has the page's focus then. */
  private[render] var inKey: Boolean = false

  /** `f` once the key being pressed has done what it does, where one is (else at once): the focus put in a field or an
    * editor as a key is heard (a dialog's Enter closing it, the focus going back to the editor it was opened from)
    * would otherwise take the key's own action (Enter's new line) after it. */
  def afterKey(f: () => Unit): Unit = if (inKey) dom.window.setTimeout(() => f(), 0) else f()

  /** The element made focusable to hold the page's focus for the app's (`accessibleFocus`). */
  private[render] var reading: html.Element = null
  // how many rows have been given ids to be a list's active one (`setActiveRow`)
  private[render] var rowIds: Int = 0

  /** The element that held the page's focus for the app's put back as it was, as the app's focus moves on. */
  private[render] def release(): Unit = if (reading != null) {
    reading.removeAttribute("tabindex")
    reading.removeAttribute("data-everyui-held")
    reading = null
  }

  /** SVG's namespace, which its elements are made in. */
  val svgNs: String = "http://www.w3.org/2000/svg"

  private var descriptions = 0
  /** A new id for an element holding a description (`setDescription`). */
  private def describedId(): String = {
    descriptions += 1
    "everyui-described-" + descriptions.toString
  }

  /** The size the app is drawn at (everyui.theme.UiScale): what a box on the screen, and a place on it, are read over. */
  def drawnAt: Double = everyui.theme.UiScale()

  /** The size `e`'s place is read over: the size drawn at as the first node heard it, kept on the event, so one that
    * changes the size as it is heard (a click on a + that turns it up) is the same place to every node it climbs
    * through, and the one that consumed it is still known for it. */
  def heardAt(e: dom.Event): Double = {
    val kept = e.asInstanceOf[js.Dynamic].everyuiDrawnAt
    if (js.isUndefined(kept)) {
      e.asInstanceOf[js.Dynamic].everyuiDrawnAt = drawnAt
      drawnAt
    }
    else kept.asInstanceOf[Double]
  }

  private val Negative = -1
  private val Reverse = 1
  /** How this browser counts a right-to-left scroll: `Negative`, `Reverse`, or 0 (from the far left), found once on a
    * small scroll of its own. */
  private lazy val rtlScroll: Int = {
    val outer = dom.document.createElement("div").asInstanceOf[html.Div]
    outer.setAttribute("dir", "rtl")
    outer.style.cssText = "position:absolute;top:-100px;width:4px;height:1px;overflow:hidden;visibility:hidden"
    val inner = dom.document.createElement("div").asInstanceOf[html.Div]
    inner.style.cssText = "width:8px;height:1px"
    outer.appendChild(inner)
    dom.document.body.appendChild(outer)
    val kind = if (outer.scrollLeft > 0.0) 0 else {
      outer.scrollLeft = 1.0
      if (outer.scrollLeft < 0.5) Negative else Reverse
    }
    dom.document.body.removeChild(outer)
    kind
  }

  // what `reveal` has laid out, undrawn, until the end of the turn
  private var revealed: List[html.Element] = Nil

  /** Lay out what hides `el` (it, or a container above it, `display: none`) without drawing it, until the end of the
    * script's turn, so its size can be read; whether there was anything to. The sizes a turn asks for are read together
    * (MeasuredFeature), so a hidden panel is laid out once for all of them, and it is hidden again before the frame is
    * drawn. A node not on the page is measured when it is put there. */
  /** Whether `"#rrggbb"` is a light colour (its luminance over half): the words of a dark theme. */
  private[render] def light(hex: String): Boolean = {
    val h = hex.stripPrefix("#")
    h.length >= 6 && {
      def channel(i: Int): Double = Integer.parseInt(h.substring(i, i + 2), 16) / 255.0
      0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4) > 0.5
    }
  }

  // where a node not on the page is measured: on it, out of sight and as wide as anything, under no transform but the
  // one the page may have, which is read off it (its width over what it was given)
  private lazy val offPageHost: html.Element = {
    val d = dom.document.createElement("div").asInstanceOf[html.Element]
    d.style.position = "absolute"
    d.style.left = "-200000px"
    d.style.top = "0px"
    d.style.width = "100000px"
    d.style.height = "0px"
    d.style.visibility = "hidden"
    d.style.pointerEvents = "none"
    d.setAttribute("aria-hidden", "true")
    val app = dom.document.getElementById("app")
    (if (app != null) app else dom.document.body).appendChild(d)
    d
  }

  /** Whether `el` can be lent to the page to be measured: not if it is or holds a frame, which would load again. */
  def movable(el: html.Element): Boolean = el.tagName != "IFRAME" && el.querySelector("iframe") == null

  /** The size of `el`, which is not in the page, as it would be laid out: lent to the page for the read, out of sight,
    * and put back where it was. A node out of the page has no box, and read 0 by 0: words measured as a screen was built
    * before it arrived (a column as wide as its widest name) came out as nothing. One made hidden is laid out for the
    * read (the host is out of sight), or it read 0 by 0 and was that size as it was shown. */
  def offPage(el: html.Element): (Double, Double) = {
    everyui.perf.PerfCounters.read()
    val parent = el.parentNode
    val next = el.nextSibling
    val hidden = el.style.display == "none"
    if (hidden) el.style.display = ""
    offPageHost.appendChild(el)
    val r = el.getBoundingClientRect()
    val scale = offPageHost.getBoundingClientRect().width / 100000.0
    if (hidden) el.style.display = "none"
    if (parent != null) parent.insertBefore(el, next) else offPageHost.removeChild(el)
    val at = if (scale > 0.0) scale else 1.0
    (r.width / at, r.height / at)
  }

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

  /** `el`, in the page under something hidden (`display: none`), laid out for a read, undrawn, until the frame is
    * drawn, as `reveal` does: found by its styles alone, written and not read, so the reads of a batch (`sizesOf`)
    * follow every reveal rather than each read laying the page out again after the reveal before it. */
  def unhide(el: html.Element): Unit = {
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
  }

  /** The sizes of `els`, none of them in the page, as `offPage` reads one: all lent to it at once, read, and all put
    * back, so the page is laid out once for them rather than once for each; `meanwhile` (the batch's reads of what is
    * in the page) run before they are put back, so those are read in the same layout. */
  def offPage(els: List[html.Element], meanwhile: () => Unit): List[(Double, Double)] = if (els.isEmpty) {
    meanwhile()
    Nil
  } else {
    everyui.perf.PerfCounters.read()
    // only the outermost are moved: one inside another goes with it, and taken out of it would be read without it
    def within(el: html.Element): Boolean = {
      var at = el.parentNode
      var found = false
      while (at != null && !found) {
        found = els.exists(_ eq at)
        at = at.parentNode
      }
      found
    }
    val moved = els.filterNot(within)
    val places = moved.map(el => (el.parentNode, el.nextSibling, el.style.display == "none"))
    moved.foreach { el =>
      if (el.style.display == "none") el.style.display = ""
      offPageHost.appendChild(el)
    }
    // (one inside another hidden, as an avatar's picture is until it loads, shown for the read as `unhide` shows it)
    els.foreach(el => if (!moved.exists(_ eq el)) unhide(el))
    val boxes = els.map(_.getBoundingClientRect())
    val scale = offPageHost.getBoundingClientRect().width / 100000.0
    val at = if (scale > 0.0) scale else 1.0
    // the rest of the batch read now, with these still lent: the page laid out once for all of them
    meanwhile()
    // each put back before the sibling it was before once that sibling is back where it was, whatever order they
    // came in: put back in turn, one whose sibling was lent too and is not back yet went before a node no longer its
    // parent's, which threw, and every page of an app failed as it loaded
    var left = moved.zip(places)
    var placing = true
    while (left.nonEmpty && placing) {
      val (ready, waiting) = left.partition { case (_, (parent, next, _)) =>
        parent == null || next == null || (next.parentNode eq parent)
      }
      ready.foreach { case (el, (parent, next, hidden)) =>
        if (hidden) el.style.display = "none"
        if (parent != null) parent.insertBefore(el, next) else offPageHost.removeChild(el)
      }
      placing = ready.nonEmpty
      left = waiting
    }
    // (a sibling gone from its parent meanwhile: put back at its end)
    left.foreach { case (el, (parent, _, hidden)) =>
      if (hidden) el.style.display = "none"
      parent.appendChild(el)
    }
    boxes.map(r => (r.width / at, r.height / at))
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
  /** A CSS radial gradient: a circle `radius` pixels about `x`, `y`, its stops from the centre out. */
  def radialGradient(x: Double, y: Double, radius: Double, stops: String): String =
    "radial-gradient(circle " + math.max(0.01, radius).toString + "px at " + x.toString + "px " + y.toString + "px, " +
      everyui.Gradient.decode(stops).map { case (hex, at) => hex + " " + (at * 100.0).toString + "%" }.mkString(", ") + ")"
  def linearGradient(angle: Double, stops: String): String =
    "linear-gradient(" + angle.toString + "deg, " +
      everyui.Gradient.decode(stops).map { case (hex, at) => hex + " " + (at * 100.0).toString + "%" }.mkString(", ") + ")"
  /** A CSS font-family for `family`: it, then the fallback faces (`Faces.fallbackFaces`), for what it has nothing to
    * draw with, then the system's colour emoji faces (`emojiFaces`), so an emoji anywhere draws in colour. */
  def families(family: String): String =
    ("'" + family + "'" :: everyui.font.Faces.fallbackFaces().toList.map(f => "'" + f.family + "'")).mkString(", ") +
      ", " + emojiFaces

  /** The systems' colour emoji faces, by name: macOS's and iOS's, Windows', Android's and Linux's, then any the app
    * registered for where a system has none (`Faces.colorEmoji`). An app that wants its own drawn before the system's
    * registers it as a fallback face (`Faces.fallback`). */
  def emojiFaces: String = systemEmoji + Faces.colorEmojiFaces().map(f => ", '" + f.family + "'").mkString
  private val systemEmoji: String = "'Apple Color Emoji', 'Segoe UI Emoji', 'Noto Color Emoji'"


  /** The embed keys at `el` (the page's focused element) belong to, "" for none: the name given by the nearest element
    * marked `data-everyui-embed` (an editor or other page embedded in the app; "embed" when it gives none), else
    * "field" for anything the app did not make (outside `#app`): a field, a frame, a password manager's menu. The focus
    * layer leaves such a key to it (`Platform.keyEmbed`), rather than moving the focus on an arrow or keeping Tab from it. */
  def embedOf(el: dom.Element): String =
    if (el == null || (el eq dom.document.body) || (el eq dom.document.documentElement)) ""
    else {
      val embed = el.closest("[data-everyui-embed]")
      val app = dom.document.getElementById("app")
      if (embed != null) {
        val name = embed.getAttribute("data-everyui-embed")
        if (name == null || name.isEmpty) "embed" else name
      }
      else if (app == null || !app.contains(el)) "field"
      else ""
    }

  /** Whether `el` takes typing itself: a field, a box of lines, a drop-down or an editable element. */
  def editable(el: dom.Element): Boolean = el != null && {
    val tag = el.tagName.toLowerCase
    tag == "input" || tag == "textarea" || tag == "select" || el.asInstanceOf[html.Element].isContentEditable
  }

  /** A key's name, the DOM's (`KeyboardEvent.key`); but a letter or a digit held with both Command and Option, which
    * types nothing, by its key ("c") rather than the character Option makes of it on a Mac ("ç"), so that a chord of
    * Cmd+Alt and a letter (`everyui.keymap.Chord`) matches there. */
  def keyName(e: dom.KeyboardEvent): String =
    if (e.metaKey && e.altKey && "Key[A-Z]|Digit[0-9]".r.matches(e.code)) e.code.last.toLower.toString else e.key

  /** Whether `e` is a key the page's focused field types or edits with (a character, Backspace, Delete, the caret's
    * keys along the line), which the browser must be left to act on whatever the app made of it: kept from the field,
    * nothing could be typed. Enter, Escape, Tab and the up and down arrows stay the app's to take. AltGr is Ctrl+Alt.
    * Alt and Left or Right moves no caret off a Mac (it is Back and Forward there), so it is not the field's; on a Mac
    * Option and Command with them move a word and a line, and are. */
  def fieldKey(e: dom.KeyboardEvent): Boolean = editable(dom.document.activeElement) && {
    val character = e.key.codePointCount(0, e.key.length) == 1 && !e.metaKey && (!e.ctrlKey || e.altKey)
    val history = e.altKey && (e.key == "ArrowLeft" || e.key == "ArrowRight") && !Platform.commandKey()
    character || (fieldKeys.contains(e.key) && !history)
  }
  private val fieldKeys = Set("Backspace", "Delete", "ArrowLeft", "ArrowRight", "Home", "End")

  private var fields = Map.empty[String, Int]

  /** An id for a field that asks for `token`: the token itself for the first, then "email-2" and on, so a page with
    * two keeps them apart. */
  def fieldId(token: String): String = {
    val n = fields.getOrElse(token, 0) + 1
    fields += token -> n
    if (n == 1) token else token + "-" + n.toString
  }

  /** The page's rules for its fields under the browser's autofill: the words keep the field's colour (`--everyui-fill`)
    * and the box keeps its own, where the browser would paint both, and the `everyui-autofill` animation starts, so a
    * value filled without a word to the page is heard (`onValue`). */
  val autofillStyle: String =
    " @keyframes everyui-autofill { from { opacity: 1; } to { opacity: 1; } }" +
      List("autofill", "-webkit-autofill").map(s =>
        s" #app input:$s, #app textarea:$s { animation: everyui-autofill 1ms; -webkit-text-fill-color: var(--everyui-fill, currentColor);" +
          " caret-color: var(--everyui-fill, auto); transition: background-color 0s 86400s, color 0s 86400s; }").mkString

  /** The page's rules for a drop-down's open list, in the colours its select names (`setListColors`): in every browser
    * its options on the list's colour in its words (what a browser drawing its own list takes of them); where the
    * list can be drawn by the page (`appearance: base-select`, Chromium's), drawn as a menu of the theme is, raised on
    * an edge with a shadow, the option under the pointer or the keys lit, the chosen one in the accent. */
  val selectStyle: String = {
    val s = "#app select.everyui-select"
    s" $s option { background-color: var(--everyui-list); color: var(--everyui-list-text); }" +
      " @supports (appearance: base-select) {" +
      s" $s, $s::picker(select) { appearance: base-select; }" +
      // its words in the middle of its box, as the browser's own has them, and a chevron at its end that turns over
      // while it is open
      s" $s { align-items: center; }" +
      s" $s::picker-icon { content: ''; width: 0.4em; height: 0.4em; margin-inline: auto 0.2em;" +
      " border-right: 1.5px solid currentColor; border-bottom: 1.5px solid currentColor;" +
      " transform: translateY(-25%) rotate(45deg); transition: transform 0.15s; }" +
      s" $s:open::picker-icon { transform: translateY(25%) rotate(225deg); }" +
      s" $s::picker(select) { background: var(--everyui-list); color: var(--everyui-list-text);" +
      " border: 1px solid var(--everyui-list-edge); border-radius: 0.5em; padding: 0.25em; margin-block: 0.25em;" +
      // drawn above the page, out of its scale (`Platform.setUiScale`): its words scaled as the box's are
      " font-size: calc(100% * var(--everyui-ui-scale, 1));" +
      " box-shadow: 0 6px 24px rgb(0 0 0 / 45%); }" +
      s" $s option { padding: 0.375em 0.625em; border-radius: 0.375em; background-color: transparent;" +
      " outline: none; }" +
      s" $s option:hover, $s option:focus-visible { background-color: var(--everyui-list-lit); }" +
      s" $s option:checked { color: var(--everyui-list-chosen); } }"
  }

  /** A click the browser should keep: another button, or a modifier asking for a new tab or window. */
  def modified(e: dom.MouseEvent): Boolean = e.button != 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey
}

/** Backend node factories. */
object Nodes {
  /** A page of the web: an `<iframe>` filling its box, allowed to play by itself, full screen and protected media, and
    * out of the page's tab order, since the keys are the app's (WebFrame). */
  /** A rich document's editing surface (everyui.rich.RichTextEditor): a box that scrolls, holding the editable
    * element (WebRichEditor). */
  def richEditor(): RenderNode = {
    val el = dom.document.createElement("div").asInstanceOf[html.Element]
    el.style.cssText = "position:absolute;left:0;top:0;margin:0;padding:0;box-sizing:border-box"
    val node = new RenderNode(el)
    node.richEditor = new WebRichEditor(el)
    node
  }

  def web(bridge: String): RenderNode = {
    val frame = dom.document.createElement("iframe").asInstanceOf[html.IFrame]
    frame.setAttribute("allow", "autoplay; encrypted-media; fullscreen")
    frame.setAttribute("allowfullscreen", "")
    frame.tabIndex = -1
    frame.style.cssText = "position:absolute;left:0;top:0;border:0;margin:0;padding:0;display:block;background:#000"
    val node = new RenderNode(frame)
    node.webFrame = new WebFrame(frame, bridge)
    node
  }

  /** An embed's box (everyui.component.Embed): a `<div>` marked `data-everyui-embed`, so the keys are the guest's while
    * the page's focus is inside it, with an id of its own for the guest to find it by, and a stacking context of its own,
    * so nothing in it is drawn over what the app puts over it. A `site`'s scripts are loaded into the page the first
    * time one of its embeds is made. */
  def embed(name: String, site: everyui.component.EmbedSite): RenderNode = {
    val node = container()
    node.el.id = WebGuests.nextId()
    node.el.setAttribute("data-everyui-embed", name)
    node.el.style.overflow = "hidden"
    // a stacking context of its own: whatever the guest raises inside it (Monaco's sticky lines and widgets, a
    // terminal's layers) stays under the menus, tooltips and dialogs the app draws over it
    node.el.style.setProperty("isolation", "isolate")
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
    // drawn at the app's size from its top left (Platform.setUiScale), by the `scale` property, which leaves the root's
    // own `transform` (Component.scale) to it; at the window's left in a right-to-left page too, where a box narrower
    // than the page would sit at its right
    app.style.setProperty("transform-origin", "0 0")
    app.style.marginRight = "auto"
    // a link in running text shows it is one under the pointer
    val style = dom.document.createElement("style")
    style.textContent = ".everyui-link:hover { text-decoration: underline; text-underline-offset: 3px; }" +
      // a scroll run from its left in every language, and what it holds in the page's direction
      " .everyui-ltr { direction: ltr; } .everyui-ltr > :not([dir]) { direction: var(--everyui-dir, ltr); }" +
      // what holds the page's focus for the app's draws no outline of the browser's: the app draws its focus, or the
      // focus layer a ring in the element's own style (`setFocusRing`), which outranks this
      " [data-everyui-held]:focus { outline: none; }" +
      RenderNode.autofillStyle + RenderNode.selectStyle
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

  /** A form (`everyui.form.Form`), a real `<form>` placed as a container is, so the browser and password managers see
    * its fields as one: it validates nothing itself, and holds a submit button out of sight, so Enter in a field and
    * a manager's click both send it (`onSubmit`). `kind` ("signin", "signup", ...) is kept on it for them. */
  def form(kind: String): RenderNode = {
    val el = dom.document.createElement("form").asInstanceOf[html.Element]
    el.setAttribute("novalidate", "")
    el.setAttribute("data-everyui-form", kind)
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    el.style.margin = "0px"
    val send = dom.document.createElement("button").asInstanceOf[html.Button]
    send.`type` = "submit"
    send.tabIndex = -1
    send.setAttribute("aria-hidden", "true")
    send.style.cssText = "position:absolute;left:0;top:0;width:1px;height:1px;padding:0;border:0;opacity:0;pointer-events:none"
    el.appendChild(send)
    val node = new RenderNode(el)
    node.tail = send
    node
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
    el.className = "everyui-select"
    // the family a text node starts with, until the app's (`Theme.family`) is set: a select drawn by the page has the
    // page's, a serif
    el.style.fontFamily = "system-ui, " + RenderNode.emojiFaces + ", sans-serif"
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    el.style.border = "none"
    el.style.outline = "none"
    el.style.padding = "0px"
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
    // the family a text node starts with, until the app's (`Theme.family`) is set: a textarea's own is monospace
    el.style.fontFamily = "system-ui, " + RenderNode.emojiFaces + ", sans-serif"
    el.style.setProperty("box-sizing", "border-box")
    el.style.setProperty("user-select", "text")
    new RenderNode(el)
  }

  /** A paragraph of styled runs (`everyui.component.RichText`): a text node here, which draws them. */
  def richText(): RenderNode = text()

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
    el.style.setProperty("user-select", "text")
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
  /** The sizes of `nodes` (`MeasuredFeature`'s batch), read with the page laid out once for them all: every one
    * hidden in the page shown for the read and every one out of it lent to it (`RenderNode.unhide`, `offPage`), all
    * written before any is read, where reading each in turn laid the page out again for each. */
  def sizesOf(nodes: List[RenderNode]): List[(Double, Double)] = {
    // read where they are (in the page, or one that cannot be moved), or lent to it
    val stays = nodes.map(n => n.onPage || !RenderNode.movable(n.el))
    nodes.zip(stays).foreach { case (n, s) => if (s && n.onPage) RenderNode.unhide(n.el) }
    // those in the page read after every one is written (shown, lent), before the lent are put back
    var staying: List[(Double, Double)] = Nil
    val lent = RenderNode.offPage(nodes.zip(stays).filterNot(_._2).map(_._1.el),
      () => staying = nodes.zip(stays).filter(_._2).map(_._1.getSize())).iterator
    val stayed = staying.iterator
    stays.map(s => if (s) stayed.next() else lent.next())
  }

  /** Whether this device can go on playing in a corner of whatever the viewer does next, and doing it. */
  /** Whether this platform has a browser engine to lend a page (Nodes.web): the browser's own, in an iframe. */
  def webViews(): Boolean = true
  /** Whether a web guest runs in an embed here: in this very page. */
  def embeds(): Boolean = true

  def corners(): Boolean = false

  def toCorner(): Boolean = false

  /** What to do when the app is at the front again after being away: the page shown again, or brought back from the
    * back/forward cache. Not as it is first loaded, which is a launch. */
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

  /** The app asks before it is left while `guard` answers true, or any other guard does: here the browser asks with its
    * own "Leave site?" as the page is closed, reloaded or sent to another address (PageLeave), in its own words and
    * only once the person has clicked or typed on the page; a move within the app (`pushPath`) is never asked. What
    * removes it.
    *
    * {{{
    *   val unguard = Platform.leaveGuard(() => signedIn())
    *   Platform.leaveGuardWhile(editor.dirty)
    * }}} */
  def leaveGuard(guard: () => Boolean): () => Unit = {
    PageLeave.follow
    everyui.app.LeaveGuards.add(guard)
  }
  /** Guarded while `flag` is set, and the page's listener standing only while some guard might answer true. */
  def leaveGuardWhile(flag: reactify.Val[Boolean]): () => Unit = {
    PageLeave.follow
    everyui.app.LeaveGuards.addWhile(flag)
  }

  // a page hidden is one nobody is watching — another tab, the window minimised, a television's own home screen
  // over the app on webOS and Tizen, the page left for another — and shown again, or brought back from the
  // back/forward cache, is somebody back; told once each way (Presence). Never its focus: devtools or another window
  // in front leaves the page in sight
  private val presence = new everyui.app.Presence(() => away(), () => resumed())
  dom.document.addEventListener("visibilitychange", (_: dom.Event) =>
    if (dom.document.visibilityState == "hidden") presence.hidden() else presence.shown())
  dom.window.addEventListener("pagehide", (_: dom.Event) => presence.hidden())
  dom.window.addEventListener("pageshow", (e: dom.PageTransitionEvent) => if (e.persisted) presence.shown())

  // the window's own focus, which a page in sight may still not have: another window in front of it, a popup it
  // opened. Focus gone into one of the page's own frames is the page's still (`document.hasFocus`), so a click into a
  // web view is not the window left
  private var windowFocused: Boolean = dom.document.hasFocus()
  dom.window.addEventListener("focus", (_: dom.Event) => if (!windowFocused) {
    windowFocused = true
    focusers.foreach(f => f())
  })
  dom.window.addEventListener("blur", (_: dom.Event) => dom.window.setTimeout(() =>
    if (windowFocused && !dom.document.hasFocus()) {
      windowFocused = false
      blurrers.foreach(f => f())
    }, 0))
  private var focusers: List[() => Unit] = Nil
  private var blurrers: List[() => Unit] = Nil

  /** What to do as the app's window gets the focus back: the person back from another window in front of it (a popup
    * the app opened to connect an account). Not as it is first loaded. On a phone or a television it is the same as
    * `onResume`. What stops it being told. */
  def onFocus(f: () => Unit): () => Unit = {
    focusers = focusers :+ f
    () => focusers = focusers.filterNot(_ eq f)
  }

  /** What to do as the app's window loses the focus to another, though it may still be in sight. */
  def onBlur(f: () => Unit): () => Unit = {
    blurrers = blurrers :+ f
    () => blurrers = blurrers.filterNot(_ eq f)
  }

  // the network's coming and going, as the browser says it: offline is sure, online only that there is a network
  private val onlineNow = reactify.Var[Boolean](dom.window.navigator.onLine)
  dom.window.addEventListener("online", (_: dom.Event) => onlineNow @= true)
  dom.window.addEventListener("offline", (_: dom.Event) => onlineNow @= false)

  /** Whether the device has a network: false is sure (none at all), true only that there is one, not that the server
    * answers. Changes as it comes and goes, so a socket can reconnect the moment it is back rather than at its next
    * try. */
  def online: reactify.Val[Boolean] = onlineNow

  // the page going for good, or into the back/forward cache, from which it may come back (onResume)
  dom.window.addEventListener("pagehide", (_: dom.Event) => closers.foreach(f => f()))
  private var closers: List[() => Unit] = Nil

  /** What to do as the app's window is closed or left: the tab closed, the page reloaded or sent elsewhere. Only what
    * can be done at once, then and there (a mark cleared, a beacon sent): nothing after it is sure to run. A page a
    * browser keeps to go back to may come back from it (`onResume`). What stops it being told. */
  def onClose(f: () => Unit): () => Unit = {
    closers = closers :+ f
    () => closers = closers.filterNot(_ eq f)
  }

  // the theme's colour scheme declared on the page (Theme.colorScheme), followed as it changes, unless the app says
  // not: the browser's own scrollbars, controls and selection follow it, and it draws a frame of the same scheme clear
  // over it rather than on an opaque canvas of its own
  private var declaresScheme = true
  everyui.theme.Theme.colorScheme.attach(_ => declareScheme())
  declareScheme()

  /** The screen's safe area: none here (a browser keeps a page inside its own; a window, a television, have none). */
  def safeArea(): everyui.layout.Insets = everyui.layout.Insets.none

  /** Whether the page declares the theme's colour scheme (`color-scheme` on its root and in a `<meta>`), as it does
    * unless this turns it off: an app that declares its own. */
  def declareColorScheme(on: Boolean): Unit = {
    declaresScheme = on
    if (on) declareScheme()
    else {
      dom.document.documentElement.asInstanceOf[html.Element].style.removeProperty("color-scheme")
      Option(dom.document.querySelector("meta[name=color-scheme][data-everyui]"))
        .foreach(m => m.parentNode.removeChild(m))
    }
  }

  private def declareScheme(): Unit = if (declaresScheme) {
    val scheme = everyui.theme.Theme.colorScheme()
    dom.document.documentElement.asInstanceOf[html.Element].style.setProperty("color-scheme", scheme)
    val meta = Option(dom.document.querySelector("meta[name=color-scheme]")).getOrElse {
      val made = dom.document.createElement("meta")
      made.setAttribute("name", "color-scheme")
      made.setAttribute("data-everyui", "")
      dom.document.head.appendChild(made)
      made
    }
    meta.setAttribute("content", scheme)
  }

  // a reload into a new build named its address afresh (`reload`): the address goes back to what it was
  private val reloadParam = "_b"
  locally {
    val search = dom.window.location.search
    if (search.contains(reloadParam + "=")) {
      val kept = search.stripPrefix("?").split("&").filterNot(_.startsWith(reloadParam + "=")).mkString("&")
      dom.window.history.replaceState(dom.window.history.state, "",
        dom.window.location.pathname + (if (kept.isEmpty) "" else "?" + kept) + dom.window.location.hash)
    }
  }

  def log(message: String): Unit = dom.console.log("[ui] " + message)

  /** A failure with its cause: the console gets the stack. */
  def logError(message: String, t: Throwable): Unit = {
    dom.console.log("[ui] ERROR " + message + ": " + t.toString)
    dom.console.error(t.asInstanceOf[js.Any])
  }

  def ticks(): Double = dom.window.performance.now()

  /** Served next to index.html (under a sealed bundle's base: WebAssets). */
  def imagePath(name: String): String = WebAssets.url("images/" + name)

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
  def openUrl(url: String): Unit =
    if (everyui.component.NavigationUrl.safe(url)) { dom.window.open(url, "_blank", "noopener,noreferrer"); () }
  /** Leave for `url`, in this tab. */
  def navigate(url: String): Unit = if (everyui.component.NavigationUrl.safe(url)) dom.window.location.assign(url)
  /** What the tab is called. */
  def setTitle(title: String): Unit = dom.document.title = title
  /** A count of what is new (the unread), on the tab's icon: a red circle with the count over the page's own icon
    * ("9+" over 9), and the page's icon back as it was at 0; an installed app's badge too, where the browser has one
    * (`FaviconBadge`). The app keeps the count; this only shows it. */
  def setBadge(count: Int): Unit = FaviconBadge.set(count)
  /** Whether the person lets the app show notifications (`showNotice`): followed as they change it in the browser. */
  def notificationPermission: reactify.Val[everyui.Permission] = Notices.permission
  /** The browser's prompt for notifications. Only from a click or a key the person made (never as the page loads), or
    * the browser refuses without asking; `notificationPermission` changes with the answer. */
  def askNotificationPermission(): Unit = Notices.ask()
  /** `notice` shown as a notification, through the page's service worker where it has one, so it shows while the tab
    * is in the background; nothing while the permission is not given, or while the person is looking at the page and
    * `notice.onlyWhenHidden`. A click on it brings the app's window to the front and runs `onClick` (`Notices`). */
  def showNotice(notice: everyui.push.Notice, onClick: () => Unit): Unit = Notices.show(notice, onClick)

  /** Whether a `Backdrop` plays a video behind a screen (`videoSource`): a browser does, muted, inline and looping. */
  def backdropVideo(): Boolean = true

  /** Whether people ask for less motion (`everyui.motion.ReduceMotion`): the page told `prefers-reduced-motion`. */
  def reduceMotion(): Boolean = dom.window.matchMedia(lessMotion).matches
  /** The setting as it changes, which the browser says at once. */
  def watchReduceMotion(f: Boolean => Unit): Unit = {
    val query = dom.window.matchMedia(lessMotion)
    query.addEventListener("change", (_: dom.Event) => f(query.matches))
  }
  private val lessMotion = "(prefers-reduced-motion: reduce)"
  /** "dark" or "light": what the person has asked the browser and the system for (`prefers-color-scheme`). */
  /** No setting for more time to read or act is read here: 1. */
  def timeScale(): Double = 1.0
  def systemColorScheme(): String = if (dom.window.matchMedia(darkScheme).matches) "dark" else "light"
  /** The scheme as the person changes it, told as the browser says it changed. */
  def watchColorScheme(f: String => Unit): Unit = {
    val query = dom.window.matchMedia(darkScheme)
    query.addEventListener("change", (_: dom.Event) => f(if (query.matches) "dark" else "light"))
  }
  private val darkScheme = "(prefers-color-scheme: dark)"
  /** How large the person asked the browser to draw text: its default size (`font-size: medium`) over 16 pixels, 1 as
    * it comes. Browser zoom is not in it: the page's pixels are zoomed already. */
  def systemTextScale(): Double = {
    val probe = dom.document.createElement("div").asInstanceOf[html.Element]
    probe.style.cssText = "position:absolute;visibility:hidden;font-size:medium"
    dom.document.body.appendChild(probe)
    val px = dom.window.getComputedStyle(probe).fontSize.stripSuffix("px").toDoubleOption.getOrElse(16.0)
    dom.document.body.removeChild(probe)
    if (px > 0.0) px / 16.0 else 1.0
  }
  /** The text size as the person changes it: read again as the page comes back into sight (a browser says nothing
    * as its settings change). */
  def watchTextScale(f: Double => Unit): Unit =
    dom.document.addEventListener("visibilitychange", (_: dom.Event) =>
      if (dom.document.visibilityState == "visible") f(systemTextScale()))

  /** `words` read out by a screen reader when it next can, not cutting in on what it is reading (a new message in a
    * chat): a polite live region, made on first use, clipped to nothing at the page's corner. Not pushed off the
    * page's edge: in a right-to-left page on a phone, a region far off its left widened the page the browser lays out,
    * and everything moved off the screen. The same words again are still read. */
  def announce(words: String): Unit = speak("everyui-announce", "polite", "status", words)
  /** `words` read out by a screen reader at once, cutting in on what it is reading (an error): an assertive live
    * region beside the polite one. */
  def alert(words: String): Unit = speak("everyui-alert", "assertive", "alert", words)
  private def speak(id: String, live: String, role: String, words: String): Unit = {
    val region = Option(dom.document.getElementById(id)).getOrElse {
      val made = dom.document.createElement("div").asInstanceOf[html.Element]
      made.id = id
      made.setAttribute("aria-live", live)
      made.setAttribute("role", role)
      made.style.cssText = "position:absolute;left:0;top:0;width:1px;height:1px;margin:-1px;padding:0;border:0;" +
        "overflow:hidden;clip:rect(0 0 0 0);clip-path:inset(50%);white-space:nowrap"
      dom.document.body.appendChild(made)
      made
    }
    region.textContent = ""
    dom.window.setTimeout(() => region.textContent = words, 50.0)
  }

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
  /** Whether people select text with the pointer (`Component.selectable`): not on a television's browser. */
  def selectsText(): Boolean = !remoteNavigation()
  /** The words selected on the page, or "". */
  def selectedText(): String = Option(dom.window.getSelection()).map(_.toString).getOrElse("")
  /** The words selected as clean HTML and plain prose, where they lie in views that say how they copy
    * (`Component.copyAs`, `RichCopy`). */
  def selectedRich(): Option[(String, String)] = RichCopy.selection()
  /** `html` and `text` on the clipboard together (`RichCopy.write`). */
  def copyRich(html: String, text: String): Unit = RichCopy.write(html, text)
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

  /** Show `path` in the address bar in place of the entry showing, without a new one and without loading anything:
    * what an address used once becomes (`/join/<token>` as `/join`), so the token is not left in the history. */
  def replacePath(path: String): Unit = dom.window.history.replaceState(dom.window.history.state, "", path)

  /** Forward is the browser's own, as back is. */
  def historyForward(): Boolean = {
    dom.window.history.forward()
    true
  }

  /** A value kept for this window alone, across a reload of it (`sessionStorage`): each of two windows of the app its
    * own, and gone with the window. "" when there is none, or the browser keeps nothing. */
  def windowSetting(name: String): String =
    try Option(dom.window.sessionStorage.getItem(name)).getOrElse("") catch { case _: Throwable => "" }

  /** Keep `value` under `name` for this window; "" forgets it. */
  def saveWindowSetting(name: String, value: String): Unit =
    try {
      if (value.isEmpty) dom.window.sessionStorage.removeItem(name) else dom.window.sessionStorage.setItem(name, value)
    }
    catch { case _: Throwable => () }

  /** Whether a rich document is edited in place here (everyui.rich.RichTextEditor): it is, in an editable element. */
  def richEditing(): Boolean = true

  /** A new window of this app at `path` (in the app: `/w/3`, never another site), the size of this one and just
    * below and to the right of it. False where it was not opened: refused by the browser (best from a click or a key),
    * or a path that is not the app's. */
  def openWindow(path: String): Boolean =
    if (!path.startsWith("/") || path.startsWith("//")) false
    else {
      val w = dom.window
      val features = "popup,width=" + w.outerWidth.toString + ",height=" + w.outerHeight.toString + ",left=" +
        (w.screenX + 32).toString + ",top=" + (w.screenY + 32).toString
      val opened = w.open(path, "_blank", features)
      opened != null && !js.isUndefined(opened)
    }

  /** A window for an address the app does not have yet, reserved now, in the person's press, where a browser opens
    * nothing after it (an OAuth address the server answers with): blank until `go`, closed by `cancel`. Kept apart
    * from the app (no `opener`) before it goes anywhere, showing `waiting` (its title and words) until then. Where the
    * browser would not open one, the target says so (`held` false), and `go` opens the address as `openUrl` does,
    * which it will likely refuse in turn: offer a link to press instead. */
  def openUrlLater(waiting: String = ""): everyui.app.UrlTarget = {
    val held = dom.window.open("", "_blank")
    if (held == null || js.isUndefined(held)) new everyui.app.UrlTarget(url => openUrl(url), () => (), held = false)
    else {
      if (waiting.nonEmpty) try {
        val page = held.document
        page.title = waiting
        val words = page.createElement("p")
        words.textContent = waiting
        words.setAttribute("style", "font: 16px system-ui, sans-serif; margin: 3em; text-align: center; color: #555")
        page.body.appendChild(words)
      }
      catch { case _: Throwable => () }
      held.asInstanceOf[js.Dynamic].opener = null
      new everyui.app.UrlTarget(url =>
        if (everyui.component.NavigationUrl.safe(url)) held.location.href = url else held.close(),
        () => held.close())
    }
  }

  /** Which build of the app this page loaded: the version its script was loaded by (`main.js?v=…`, or the WebAssembly
    * loader's, which `everyuiWebScripts` writes from what it holds), "" where it was loaded by none. A server serving a
    * newer one answers with another, and the page can `reload` into it. */
  def buildId(): String = {
    val scripts = dom.document.querySelectorAll("script[src]")
    val version = """[?&]v=([^&#]+)""".r
    (0 until scripts.length).map(i => Option(scripts(i).getAttribute("src")).getOrElse("")).collectFirst {
      case src if src.contains("main.js?") || src.contains("everyui-loader.js?") => version.findFirstMatchIn(src)
    }.flatten.map(_.group(1)).getOrElse("")
  }

  /** The page loaded again, its address named afresh so no cache can answer it with the build it has (the address
    * goes back to what it was as it loads). True: a page can always be. */
  def reload(): Boolean = {
    val url = new dom.URL(dom.window.location.href)
    url.searchParams.set(reloadParam, js.Date.now().toLong.toString)
    dom.window.location.replace(url.href)
    true
  }

  /** The device's time zone, as IANA names it ("America/Chicago"); "UTC" where the browser does not say. */
  def timeZone(): String =
    try {
      val zone = js.Dynamic.newInstance(js.Dynamic.global.Intl.DateTimeFormat)().resolvedOptions().timeZone
      if (js.isUndefined(zone) || zone == null) "UTC" else zone.toString
    }
    catch { case _: Throwable => "UTC" }

  /** `f` told as the time zone changes (everyui.date.TimeZones). What stops it being told. */
  def onTimeZone(f: () => Unit): () => Unit = everyui.date.TimeZones.onChange(f)

  /** A moment marked (everyui.perf.Marks) where a check can read it: `window.everyuiMarks`, `{name, at}` each, the
    * last 200. */
  def marked(name: String, at: Double): Unit = {
    val window = dom.window.asInstanceOf[js.Dynamic]
    if (js.isUndefined(window.everyuiMarks)) window.everyuiMarks = js.Array[js.Any]()
    val marks = window.everyuiMarks.asInstanceOf[js.Array[js.Any]]
    marks.push(js.Dynamic.literal(name = name, at = at))
    if (marks.length > everyui.perf.Marks.kept) marks.shift()
  }

  /** A check a harness reads (`everyui.access.Access.expose`): `window.<name>()`, answering what `f` says now, read
    * as JSON into objects where `json`. */
  def expose(name: String, f: () => String, json: Boolean): Unit = {
    val read: js.Function0[js.Any] = () => if (json) js.JSON.parse(f()) else f()
    dom.window.asInstanceOf[js.Dynamic].updateDynamic(name)(read)
  }

  /** An ARB file's messages (everyui.i18n.Arb). */
  def parseMessages(arb: String): Map[String, String] = everyui.i18n.Arb.parse(arb)

  /** A text file served with the page (a translation, an SVG icon), or at another origin that allows the page to read
    * it (CORS); "" if it cannot be had. */
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
  def setDirection(rtl: Boolean): Unit = {
    val dir = if (rtl) "rtl" else "ltr"
    dom.document.documentElement.setAttribute("dir", dir)
    // for what a scroll run from its left in every language holds (.everyui-ltr)
    dom.document.documentElement.asInstanceOf[html.Element].style.setProperty("--everyui-dir", dir)
  }
  /** The page's language (`zh-Hant`), which the browser chooses CJK faces by. */
  def setLanguage(code: String): Unit = dom.document.documentElement.setAttribute("lang", code.replace("_", "-"))

  /** A page has one thread: the work is done at once, and what it hands back by `later` comes on a turn after. */
  def offThread(f: () => Unit): Unit = f()

  def later(f: () => Unit): Unit = dom.window.setTimeout(() => f(), 0)
  /** `f` once what is running now is done, before the browser draws (a microtask): what follows from it is drawn in
    * the same frame, where `later` can come after a frame drawn in between. */
  def afterTurn(f: () => Unit): Unit = beforePaint(f)
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
    * changes when it moves; only these five bodies.
    *
    * A browser may refuse the storage outright (a private window, or site data blocked): reaching for it throws,
    * and then nothing is kept and every read comes back empty, which each caller already draws a screen from. */
  private def storeKey(space: String, key: String): String = "everyui-store:" + space + ":" + key

  private def storage[T](refused: T)(f: dom.Storage => T): T =
    try f(dom.window.localStorage) catch { case _: Throwable => refused }

  def storeGet(space: String, key: String, got: Option[String] => Unit): Unit = got(storage[Option[String]](None) { s =>
    val value = s.getItem(storeKey(space, key))
    if (value == null || value.isEmpty) None else Some(value)
  })

  def storePut(space: String, key: String, value: String, done: () => Unit): Unit = {
    // a browser refuses a write once its quota is met, and a cache that cannot be written is still a working app
    storage(())(_.setItem(storeKey(space, key), value))
    done()
  }

  def storeDelete(space: String, key: String, done: () => Unit): Unit = {
    storage(())(_.removeItem(storeKey(space, key)))
    done()
  }

  def storeKeys(space: String, got: List[String] => Unit): Unit = got(storage[List[String]](Nil) { s =>
    val prefix = storeKey(space, "")
    var found: List[String] = Nil
    var i = 0
    while (i < s.length) {
      val name = s.key(i)
      if (name != null && name.startsWith(prefix)) found = name.substring(prefix.length) :: found
      i += 1
    }
    found
  })

  def storeClear(space: String, done: () => Unit): Unit = {
    storeKeys(space, keys => storage(())(s => keys.foreach(k => s.removeItem(storeKey(space, k)))))
    done()
  }

  /** The browser and the system it runs on, as its user agent says them. */
  /** What it is, as its maker names it; the same as its name where the platform knows no more than that. */
  def deviceModel(): String = deviceName()

  def launchesApps(): Boolean = false
  /** Whether the browser plays `codec`, by a media server's name for it ("vp9", "hevc", "aac") or a whole MIME type
    * with its codecs (WebCodecs). */
  def decodes(codec: String): Boolean = WebCodecs.plays(codec)
  // an app here is updated by whatever put it here, never by itself
  def canInstallApps(): Boolean = false
  def openInstallSettings(): Unit = ()
  def installApp(url: String, progress: Double => Unit, done: Option[String] => Unit): Unit =
    done(Some("Updates install only on Android"))
  /** The hardware facts a performance report is read with: none this platform will say. */
  def deviceProfile(): List[(String, String)] = Nil

  /** What the browser says of the device at launch (`everyui.perf.Perf.device`): its memory, rounded by the browser to a
    * few sizes (`navigator.deviceMemory`, Chrome's), and its cores, where it says them. */
  def deviceHint(): everyui.perf.DeviceHint = {
    val navigator = dom.window.navigator.asInstanceOf[js.Dynamic]
    val memory = navigator.deviceMemory.asInstanceOf[js.UndefOr[Double]].toOption
    val cores = navigator.hardwareConcurrency.asInstanceOf[js.UndefOr[Double]].toOption
    val model = deviceModel()
    everyui.perf.DeviceHint(
      model = if (model.isEmpty) None else Some(model),
      ramMb = memory.map(gb => (gb * 1024.0).toInt),
      cores = cores.map(_.toInt))
  }
  /** A browser says nothing of the device's memory running low. */
  def memoryLevel(): String = ""
  /** The share of the page's script heap in use, where the browser says (`performance.memory`, Chrome's), or -1. */
  def memoryUsedPct(): Int = {
    val memory = dom.window.performance.asInstanceOf[js.Dynamic].memory
    if (js.isUndefined(memory)) -1
    else {
      val limit = memory.jsHeapSizeLimit.asInstanceOf[Double]
      if (limit <= 0.0) -1 else (memory.usedJSHeapSize.asInstanceOf[Double] * 100.0 / limit).toInt
    }
  }

  /** How often the display is drawn, in Hz, for where a frame counts as dropped (`everyui.perf.QualityPolicy`): a
    * browser does not say, so it is read from the shortest gap between 30 animation frames running one after another
    * (a frame's time is its vsync's, so none comes sooner than one refresh after the last), asked the first time this
    * is; 0 until then, and in a hidden page, which runs none. */
  def refreshRate(): Double = {
    if (!probing) {
      probing = true
      probe(30, -1.0, Double.MaxValue)
    }
    refreshHz
  }
  private var probing: Boolean = false
  private var refreshHz: Double = 0.0
  private def probe(left: Int, last: Double, shortest: Double): Unit = dom.window.requestAnimationFrame { (at: Double) =>
    val least = if (last >= 0.0 && at > last) math.min(shortest, at - last) else shortest
    if (left > 0) probe(left - 1, at, least)
    else if (least < 1000.0) refreshHz = math.max(24.0, math.min(360.0, 1000.0 / least))
  }
  /** `f` told the message and the stack of every error nothing caught (`error` on the window), and of every promise
    * rejected with nothing to hear it (`unhandledrejection`: "promise: " and its reason). */
  def onUncaught(f: (String, String) => Unit): Unit = {
    dom.window.addEventListener("error", (e: dom.ErrorEvent) => f(e.message, jsStack(e.asInstanceOf[js.Dynamic].error)))
    dom.window.addEventListener("unhandledrejection", (e: dom.Event) => {
      val reason = e.asInstanceOf[js.Dynamic].reason
      f("promise: " + WebErrors.message(reason), jsStack(reason))
    })
  }
  /** `t`'s stack as the browser keeps it (a Scala.js throwable is a JS error), with what caused it. */
  def stackOf(t: Throwable): String = {
    val own = jsStack(t.asInstanceOf[js.Dynamic])
    val cause = t.getCause
    if (cause == null || (cause eq t)) own else own + "\nCaused by: " + cause.toString + "\n" + stackOf(cause)
  }
  private def jsStack(error: js.Dynamic): String =
    if (js.isUndefined(error) || error == null || js.isUndefined(error.stack)) "" else String.valueOf(error.stack)
  /** How many errors nothing caught this page has seen (`error` and `unhandledrejection` on the window), counted from
    * the first time it is asked. */
  def errorsSeen(): Int = {
    if (!hearing) {
      hearing = true
      dom.window.addEventListener("error", (_: dom.Event) => uncaught += 1)
      dom.window.addEventListener("unhandledrejection", (_: dom.Event) => uncaught += 1)
    }
    uncaught
  }
  /** Turns cut off by the platform (a Roku's): none here. */
  def turnsCutOff(): Int = 0
  private var hearing: Boolean = false
  private var uncaught: Int = 0

  /** What the app is showing, as a PNG in base64: nothing here can take one. */
  def screenshot(): String = ""

  /** Something large was let go: nothing to do where the runtime collects on its own. */
  def collect(): Unit = ()
  /** Whether `blur` is drawn: it is. */
  def blurs(): Boolean = true
  /** Whether text can hold emoji: the system's fonts draw them. */
  def emoji(): Boolean = true
  // nothing here turns speech into words
  /** Whether it can listen: the browser's speech recognizer (the Web Speech API's `SpeechRecognition`: Chrome, Edge,
    * Safari), none in Firefox. Chrome's sends what is said to Google's servers to be turned into words; Safari's may do
    * it on the device. */
  def dictation(): Boolean = !js.isUndefined(recognizer)
  private def recognizer: js.Dynamic = {
    val w = dom.window.asInstanceOf[js.Dynamic]
    if (!js.isUndefined(w.SpeechRecognition)) w.SpeechRecognition else w.webkitSpeechRecognition
  }

  /** One utterance, as words, in the app's language: `heard` hears them as they come (each time the whole of what is
    * heard so far) and whether they are settled, `ended` that it is over (said, stopped, gone wrong, or the microphone
    * refused, which the browser asks for the first time, from the press that starts it). What comes back stops it,
    * what was heard settling. */
  def dictate(heard: (String, Boolean) => Unit, ended: () => Unit): () => Unit =
    if (!dictation()) {
      ended()
      () => ()
    }
    else {
      val r = js.Dynamic.newInstance(recognizer)()
      r.lang = everyui.i18n.Strings.language().code.replace('_', '-')
      r.interimResults = true
      r.continuous = false
      r.maxAlternatives = 1
      var over = false
      def finish(): Unit = if (!over) {
        over = true
        ended()
      }
      r.onresult = (e: js.Dynamic) => {
        val results = e.results
        val n = results.length.asInstanceOf[Int]
        var words = ""
        var settled = n > 0
        var i = 0
        while (i < n) {
          val result = results.selectDynamic(i.toString)
          words = words + result.selectDynamic("0").transcript.asInstanceOf[String]
          if (!result.isFinal.asInstanceOf[Boolean]) settled = false
          i += 1
        }
        heard(words.trim, settled)
      }
      r.onerror = (_: js.Dynamic) => finish()
      r.onend = (_: js.Dynamic) => finish()
      try r.start() catch { case _: Throwable => finish() }
      () => try r.stop() catch { case _: Throwable => finish() }
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
  /** `editText` for a field asking for `autofill` (`Autofill.token`): the same nothing here. */
  def editField(title: String, value: String, secure: Boolean, autofill: String, done: String => Unit): Unit = ()

  /** An app's face, served beside the bundled ones in `fonts/`, added to the page's fonts. Where the page preloads it
    * (`<link rel="preload" as="font">`), from that very address, versioned or not, so the preloaded download is the one
    * used rather than a second. A face the page's build declared (PageFaces) is the page's already, with its rules and
    * preload, and is left to them. */
  def registerFont(face: Face): Unit = if (!PageFaces.has(face)) {
    if (PageFaces.declared)
      log(face.family + " " + face.weight.toString + " is not among the faces the build declared (everyuiWebFaces): " +
        "loaded from fonts/" + face.file)
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

  /** A colour emoji face (`Faces.colorEmoji`) added to the page's fonts, where the browser draws COLRv1 in colour
    * (`font-tech(color-COLRv1)`), which is the answer: elsewhere it is not added, so it is never drawn in black. */
  def registerColorEmoji(face: Face): Boolean = {
    val css = js.Dynamic.global.CSS
    val colour = try !js.isUndefined(css) && css.supports("font-tech(color-COLRv1)").asInstanceOf[Boolean]
    catch { case _: Throwable => false }
    if (colour) {
      val options = js.Dynamic.literal(weight = face.weight.toString, style = "normal")
      val source = "url(" + fontAddress(face.file) + ") tech(color-COLRv1)"
      val font = js.Dynamic.newInstance(js.Dynamic.global.FontFace)(face.family, source, options)
      dom.document.asInstanceOf[js.Dynamic].fonts.add(font)
      registered.push(font)
      font.load().`catch`((_: js.Any) => log(face.family + " could not be loaded from fonts/" + face.file))
    }
    colour
  }

  // the page's preload of `file`, as it wrote it (a version after it, say), else the bundled faces' own place
  private def fontAddress(file: String): String = {
    val links = dom.document.querySelectorAll("link[rel=preload][as=font]")
    (0 until links.length).map(links(_).getAttribute("href")).find { href =>
      href != null && href.takeWhile(_ != '?').endsWith("/fonts/" + file)
    }.getOrElse(WebAssets.url("fonts/" + file))
  }

  /** The window in the app's units: its CSS pixels over the size the app is drawn at (everyui.theme.UiScale), less
    * what a phone's on-screen keyboard covers (VisibleArea). */
  def screenWidth(): Double = dom.window.innerWidth.toDouble / RenderNode.drawnAt
  def screenHeight(): Double = VisibleArea.height() / RenderNode.drawnAt
  /** Screen pixels to a layout unit: the browser's device pixel ratio (2 on a HiDPI screen, often 1.5 or 2 on a
    * television whose page is laid out smaller than its panel), times the size the app is drawn at. */
  def pixelRatio(): Double =
    (if (dom.window.devicePixelRatio > 0.0) dom.window.devicePixelRatio else 1.0) * RenderNode.drawnAt

  /** The app drawn `factor` times its size (everyui.theme.UiScale), as the browser's zoom draws a page: the root
    * scaled from its top left and laid out in the window over it, its edges the window's. The browser's own zoom is
    * under it, multiplied. Under a transform a node's box on the screen is scaled with it, and so is read over it
    * (`getWidth`); a pointer's place too. The page's stylesheets have it too, as `--everyui-ui-scale` on the page's
    * root, for what a guest draws over the page outside the app's root (Monaco's tooltips and menus). */
  def setUiScale(factor: Double): Unit = {
    dom.document.documentElement.asInstanceOf[html.Element].style.setProperty("--everyui-ui-scale", factor.toString)
    val app = dom.document.getElementById("app").asInstanceOf[html.Element]
    if (app != null) {
      if (factor == 1.0) {
        app.style.removeProperty("scale")
        app.style.width = "100%"
        app.style.height = "100%"
      }
      else {
        app.style.setProperty("scale", factor.toString)
        app.style.width = "calc(100% / " + factor.toString + ")"
        app.style.height = "calc(100% / " + factor.toString + ")"
      }
    }
  }

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

  /** Each launch that reaches the app once it is running, with what it was launched with (a Roku's resume or deep
    * link, an Android intent). Nothing launches a running app again here, so it is never called. */
  def onLaunch(f: Map[String, String] => Unit): () => Unit = {
    launchers = launchers :+ f
    () => launchers = launchers.filterNot(_ eq f)
  }

  def launched(params: Map[String, String]): Unit = launchers.foreach(f => f(params))

  private var launchers: List[Map[String, String] => Unit] = Nil

  /** Which build of the app is running: "wasm" when it was linked to WebAssembly, "js" otherwise. Fixed when the
    * app is linked, so it cannot disagree with what the page loaded; `everyui.sbt.WasmLoader`'s loader says the same
    * in `window.everyuiBuild`. For an about box or a log line. */
  val build: String = if (scala.scalajs.LinkingInfo.isWebAssembly) "wasm" else "js"

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
      repeating = e.repeat
      // an input method's words being composed: Chrome and Firefox say so, and Safari's Enter that ends them says 229
      composing = e.asInstanceOf[js.Dynamic].isComposing.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false) || e.keyCode == 229
      // a press timed to the frame that answers it, where it is measured
      if (pressed && everyui.perf.Perf.on) everyui.perf.Perf.keyed()
      // what a field of the app's types, or moves its caret by, is the field's whatever the app made of it: the field the
      // key came to, asked before the app hears it, since a key the app takes may put the focus in a field (E on a
      // queued message edits it in the composer's field), which must not then type it as well
      val typed = RenderNode.fieldKey(e)
      RenderNode.inKey = pressed
      try { if (f(RenderNode.keyName(e), pressed) && !typed) e.preventDefault() }
      finally RenderNode.inKey = false
    }
    dom.document.addEventListener("keydown", (e: dom.KeyboardEvent) => hear(e, true))
    dom.document.addEventListener("keyup", (e: dom.KeyboardEvent) => hear(e, false))
    // a mouse's back and forward buttons are keys too, never an embed's (NavButtons)
    NavButtons.hear((name, pressed, e) => {
      held = everyui.event.Modifiers(e.ctrlKey, e.metaKey, e.altKey, e.shiftKey)
      embedded = ""
      repeating = false
      composing = false
      f(name, pressed)
    })
    NavButtons.listen(dom.window)
  }

  private var held = everyui.event.Modifiers(false, false, false, false)
  private var button = 0

  /** The button of the pointer event being reported: 0 the primary, 1 the middle, 2 the secondary. */
  /** Which of the page's pointer events is being reported: the same number for each component one climbs through (the
    * event marked with it as it is first heard), a new one for the next (`Pointer.blocks`). */
  def pointerEvent(): Long = pointerSerial
  private var pointerSerial = 0L
  private var pointerSerials = 0L
  def pointerButton(): Int = button
  private var finger = false
  /** Whether the pointer event being reported is a finger on the screen (`PointerEvent.finger`): its pointer type,
    * whatever the device has besides (a touch laptop, a tablet with a trackpad, a browser's phone emulation). */
  def pointerFinger(): Boolean = finger
  // whether the last press heard was a finger's
  private var pressedByFinger = false

  /** A pointer event about to be heard: its button, the modifiers held with it, and whether a finger made it. A click
    * or a context menu is a pointer event of its own in today's browsers, saying what made it (none, from the keys);
    * a browser where it is a plain mouse event says nothing, and it is taken to be the last press's: a tap's click is
    * a finger's. */
  private[render] def pointed(e: dom.MouseEvent): Unit = {
    val marked = e.asInstanceOf[js.Dynamic]
    if (js.isUndefined(marked.everyuiSerial)) {
      pointerSerials += 1
      marked.everyuiSerial = pointerSerials.toDouble
    }
    pointerSerial = marked.everyuiSerial.asInstanceOf[Double].toLong
    held = everyui.event.Modifiers(e.ctrlKey, e.metaKey, e.altKey, e.shiftKey)
    button = if (e.button == 1 || e.button == 2) e.button else 0
    val kind = e.asInstanceOf[js.Dynamic].pointerType.asInstanceOf[js.UndefOr[String]]
    finger = kind.map(_ == "touch").getOrElse(pressedByFinger)
    if (e.`type` == "pointerdown") pressedByFinger = finger
  }
  private var embedded = ""

  /** The modifiers held with the key being reported. */
  def modifiers(): everyui.event.Modifiers = held
  private var repeating = false
  /** Whether the key being reported is one held down repeating (`KeyboardEvent.repeat`). */
  def keyRepeat(): Boolean = repeating
  private var composing = false
  /** Whether the key being reported belongs to an input method composing words (`KeyboardEvent.isComposing`): its
    * Enter chooses the words rather than sending them. */
  def keyComposing(): Boolean = composing

  /** The embed the key being reported is meant for, "" for none (`RenderNode.embedOf`). */
  def keyEmbed(): String = embedded

  /** Whether shortcuts are made with Command rather than Ctrl here: a Mac, an iPhone or an iPad. */
  def commandKey(): Boolean = {
    val agent = dom.window.navigator.userAgent
    agent.contains("Mac OS") || agent.contains("Macintosh") || agent.contains("iPhone") || agent.contains("iPad")
  }

  /** `f` told whenever the screen changes: the window resized, a phone's keyboard opened or closed. */
  def onResize(f: () => Unit): Unit = VisibleArea.onChange(f)

  // The same four the Roku backend reaches through BrightScript intrinsics.
  /** A number from 0 up to 1, at random. */
  def random(): Double = math.random()
  def sqrt(x: Double): Double = math.sqrt(x)
  def pow(x: Double, y: Double): Double = math.pow(x, y)
  def sin(x: Double): Double = math.sin(x)
  def cos(x: Double): Double = math.cos(x)

  /** A site-absolute asset path (`/images/icon.png`), made relative to the page so the bundle works wherever
    * it is served, and found under a sealed bundle's base (WebAssets). */
  def resolvePath(path: String): String = WebAssets.url(path.stripPrefix("/"))

  /** Load `uri` and report its natural size (0 x 0 if it cannot). */
  def loadImage(uri: String, loaded: (Double, Double) => Unit): Unit = {
    val img = dom.document.createElement("img").asInstanceOf[html.Image]
    img.addEventListener("load", (_: dom.Event) => loaded(img.naturalWidth.toDouble, img.naturalHeight.toDouble))
    // one that will not load says so, as 0 x 0, as the other backends do: left unanswered, a picture that failed could
    // not be told from one still coming
    img.addEventListener("error", (_: dom.Event) => loaded(0.0, 0.0))
    img.src = uri
  }

  /** The picture at `uri` as `across` by `down` cells, each its average colour (0xRRGGBB, row by row): what words over
    * it are judged against (`everyui.access.Access`). Drawn on a canvas eight times as fine and each cell's pixels
    * averaged, so a star does not make a cell. Nothing where it cannot be read: one that will not load, or from
    * another site that does not allow its pixels to be read (the canvas is tainted). */
  def pictureCells(uri: String, across: Int, down: Int, read: List[Int] => Unit): Unit = {
    val img = dom.document.createElement("img").asInstanceOf[html.Image]
    img.asInstanceOf[js.Dynamic].crossOrigin = "anonymous"
    img.addEventListener("load", (_: dom.Event) => read(try {
      val (w, h) = (across * 8, down * 8)
      val canvas = dom.document.createElement("canvas").asInstanceOf[html.Canvas]
      canvas.width = w
      canvas.height = h
      val g = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
      g.drawImage(img, 0.0, 0.0, w.toDouble, h.toDouble)
      val px = g.getImageData(0.0, 0.0, w.toDouble, h.toDouble).data
      (0 until across * down).toList.map { cell =>
        val (cx, cy) = (cell % across, cell / across)
        var (r, gr, b) = (0, 0, 0)
        var y = cy * 8
        while (y < cy * 8 + 8) {
          var x = cx * 8
          while (x < cx * 8 + 8) {
            val at = (y * w + x) * 4
            r += px(at)
            gr += px(at + 1)
            b += px(at + 2)
            x += 1
          }
          y += 1
        }
        ((r / 64) << 16) | ((gr / 64) << 8) | (b / 64)
      }
    } catch {
      case _: Throwable => Nil
    }))
    img.addEventListener("error", (_: dom.Event) => read(Nil))
    img.src = uri
  }

  /** Call `loaded` once a bundled face (index.html's `@font-face`) is usable. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit = {
    val done: js.Function1[js.Any, Unit] = _ => loaded()
    dom.document.asInstanceOf[js.Dynamic].fonts
      .load(weight.toString + " 24px '" + family + "'")
      .applyDynamic("then")(done, done)
  }

  /** Run `f` once the faces the app starts with have loaded, so text measures with its
    * real font rather than a fallback. (The Roku loads fonts synchronously.) */
  def whenFontsReady(f: () => Unit): Unit = {
    val fonts = dom.document.asInstanceOf[js.Dynamic].fonts
    // those the page's build said to wait for, else the bundled faces a page writing its own rules has
    val start = if (PageFaces.declared) js.Array(PageFaces.start*)
      else js.Array("400 24px 'Open Sans'", "600 24px 'Open Sans'", "400 24px 'Material Icons'")
    val loads = start.map(face => fonts.load(face)) ++ registered.map(font => font.load())
    // and then a frame drawn with them: what the server wrote into the page is painted in its own faces (a headline
    // that waited for its face) before the app, built at once, draws over it
    val done: js.Function1[js.Any, Unit] = _ => afterFirstPaint(f)
    js.Dynamic.global.Promise.all(loads).applyDynamic("then")(done, done)
  }

  /** Frames as the platform measured them: not here, where the app times its own frames instead. */
  def frameTimings(f: (Double, Double) => Unit): Boolean = false
  /** Frames on demand (`everyui.task.Frames`): the app asks for each frame it needs, and asks for none while nothing
    * moves or changes. */
  def framesOnDemand(): Boolean = true
  /** `f` once, just before the browser draws its next frame. */
  def nextFrame(f: () => Unit): Unit = dom.window.requestAnimationFrame(_ => f())
  def animate(tick: () => Unit): Unit = {
    lazy val frame: Double => Unit = _ =>
      tick()
      dom.window.requestAnimationFrame(frame)
    dom.window.requestAnimationFrame(frame)
  }
}
