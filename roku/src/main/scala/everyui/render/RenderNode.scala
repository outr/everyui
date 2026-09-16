package everyui.render

import everyui.font.{Face, Faces}
import everyui.PlatformKind
import everyui.event.PointerKind
import everyui.roku.*

/** **The one invention.** The abstract render node, Roku edition: a SceneGraph
  * node behind a get/set/append surface that `Prop` can be built against.
  *
  * The shared UI layer names only this type. Its DOM twin
  * (`web/.../everyui/render/RenderNode.scala`) has the identical signature
  * over an `Element` — same names, same arities — so `Component` and `Label`
  * compile unchanged against either. Selection is by SOURCE ROOT at compile
  * time (the Scala.js / Scala Native pattern), not by subtyping, so neither
  * backend pays for dispatch.
  *
  * Every method takes explicit parens. A paren-less getter on another user
  * instance is ambiguous with a field read in the emitted BrightScript
  * (`node.getText` would yield the function ref, not call it).
  */
class RenderNode(val sg: SGNode) {
  // Every position change goes through this node, so it keeps its own coordinates and writes SceneGraph's
  // `translation` in one step rather than reading the field back on every write.
  private var left: Double = uiGetX(sg) / uiScale()
  private var top: Double = uiGetY(sg) / uiScale()
  // A text node's face and size, once set: its box comes from them rather than from SceneGraph's bounding rect.
  private var fontUri: String = ""
  private var fontSize: Double = 0.0
  private var lineHeight: Double = 0.0
  // The text as last set, its weight and colour: text in a script the face has no glyphs for is drawn in runs
  // (uiRichText), `richWidth` pixels wide, or -1 when the label draws the text itself.
  private var textValue: String = ""
  private var fontWeight: Int = 400
  private var colorHex: String = ""
  private var richWidth: Double = -1.0
  // Whether the text wraps at its width (`setWhiteSpace`), so its height is its lines rather than one
  private var wrapping: Boolean = false
  // How far apart its lines are asked to be, in units (`setLineHeight`), or 0 for the face's own spacing
  private var lineAdvance: Double = 0.0
  // The size last set, which the border follows; -1 until set.
  private var boxWidth: Double = -1.0
  private var boxHeight: Double = -1.0
  // A square border: four thin rectangles along the edges, since a SceneGraph Rectangle draws none of its own.
  private var edges: Vector[SGNode] = Vector.empty
  private var edgeWidth: Double = 0.0
  private var edgeHex: String = ""
  private var fillHex: String = ""
  private var cornerRadius: Double = 0.0
  // Rounded, the fill and the edge are tinted 9-patch Posters instead (see `paint`).
  private var fillShape: Option[SGNode] = None
  private var edgeShape: Option[SGNode] = None

  /** Layout units to pixels: the runtime's scale (2, a TV's density), applied here rather than to the root, so text is
    * drawn at its size on the screen instead of drawn small and enlarged. */
  private def px(units: Double): Double = units * uiScale()

  def getText(): String = textValue
  def setText(text: String): Unit = {
    textValue = text
    drawText()
  }

  /** In the text's face, or in runs of faces that have its glyphs (Arabic, CJK) once a face is set. */
  private def drawText(): Unit =
    if (fontUri.isEmpty) uiSetText(sg, textValue)
    else richWidth = uiRichText(sg, textValue, fontUri, fontWeight, px(fontSize), colorHex)

  def getX(): Double = left
  def setX(x: Double): Unit = {
    left = x
    uiSetTranslation(sg, px(left), px(top))
  }

  def getY(): Double = top
  def setY(y: Double): Unit = {
    top = y
    uiSetTranslation(sg, px(left), px(top))
  }

  /** Radians, clockwise, about the node's own centre — matching youi's
    * `ImageView.rotation`, which is in TURNS (0..1); the example converts. */
  def getRotation(): Double = uiGetRotation(sg)
  def setRotation(radians: Double): Unit = uiSetRotation(sg, radians)

  /** Drawn larger or smaller about its own centre, the pivot the size keeps. */
  def setScale(factor: Double): Unit = uiSetScale(sg, factor)

  def setFontSize(size: Double): Unit = uiSetFontSize(sg, px(size))

  /** `"#rrggbb"`. */
  def setColor(hex: String): Unit = {
    colorHex = hex
    uiSetColor(sg, hex)
    if (richWidth >= 0.0) drawText()
  }

  /** A bundled face (`assets/fonts/`, copied to `pkg:/fonts/`): family, CSS
    * weight, size in pixels. */
  def setFont(family: String, weight: Int, size: Double): Unit = {
    if (family.nonEmpty) {
      // drawn in the face it is measured by: the registered or bundled file every other backend draws with
      val face = Faces(family, weight)
      fontUri = "pkg:/fonts/" + face.file
      fontSize = size
      lineHeight = face.lineHeight(size)
      spaceLines()
      fontWeight = weight
      uiSetFontFile(sg, fontUri, px(size))
      if (textValue.nonEmpty || richWidth >= 0.0) drawText()
    } else uiSetFont(sg, family, weight, px(size))
  }

  /** The laid-out size in pixels. Text is as wide as its face makes it at exactly its size (a `Label` draws at whole
    * pixel sizes, so its own bounding rect is a few pixels off) and one shared line (everyui.font.Face) tall;
    * anything else is SceneGraph's bounding rect. */
  def getWidth(): Double =
    if (richWidth >= 0.0) richWidth / uiScale()
    else if (fontSize > 0.0) uiTextWidth(sg, fontUri, fontSize)
    else uiGetWidth(sg) / uiScale()
  def getHeight(): Double =
    if (wrapping && boxWidth > 0.0 && lineHeight > 0.0) {
      // as many lines as the Label wrapped its words into, at the width it was given: n lines are n faces tall with
      // n - 1 gaps between them
      val advance = if (lineAdvance > lineHeight) lineAdvance else lineHeight
      val gap = advance - lineHeight
      val drawn = uiGetHeight(sg) / uiScale()
      val lines = math.max(1.0, math.round((drawn + gap) / advance).toDouble)
      lines * advance
    }
    else if (lineHeight > 0.0) lineHeight else uiGetHeight(sg) / uiScale()

  def append(child: RenderNode): Unit = uiAppend(sg, child.sg)

  /** Detach from the parent, if attached. */
  def remove(): Unit = uiRemove(sg)

  def setWidth(width: Double): Unit = {
    boxWidth = width
    uiSetWidth(sg, px(width))
    if (cornerRadius >= 1.0 || edges.nonEmpty) paint()
  }
  def setHeight(height: Double): Unit = {
    boxHeight = height
    uiSetHeight(sg, px(height))
    if (cornerRadius >= 1.0 || edges.nonEmpty) paint()
  }

  /** `"#rrggbb"`, or `""` for transparent. */
  def setBackgroundColor(hex: String): Unit = {
    fillHex = hex
    paint()
  }
  /** Rounded corners, which a SceneGraph Rectangle does not have: see `paint`. */
  def setRadius(radius: Double): Unit = {
    cornerRadius = radius
    paint()
  }
  /** SceneGraph places every node by its translation, so there is no position type to set. */
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click"). */
  def onPointer(kind: PointerKind, f: (Double, Double) => Unit): Unit = uiOnPointer(sg, kind.toString, f)

  /** The shared ScrollContainer shifts the content; the node only clips. */
  def setScrollable(vertical: Boolean, horizontal: Boolean): Unit = ()
  def setScrollLeft(x: Double): Unit = ()
  /** The shared ScrollContainer draws no scrollbar here. */
  def setScrollbarColor(hex: String): Unit = ()
  def setScrollTop(y: Double): Unit = ()
  /** Whether the platform draws this: what a scroll has out of sight is not drawn at all. */
  def setVisible(visible: Boolean): Unit = uiSetVisible(sg, visible)
  def setClip(width: Double, height: Double): Unit = uiSetClip(sg, px(width), px(height))
  /** No pointer to rest. */
  def setTooltip(text: String): Unit = ()

  def setPositionType(name: String): Unit = ()

  /** A Label is a single line, cut short with an ellipsis, unless told to wrap: `"normal"` wraps it at its width. */
  def setWhiteSpace(value: String): Unit = {
    wrapping = value == "normal"
    uiSetWrap(sg, wrapping)
    spaceLines()
  }

  def setImageSource(uri: String): Unit = uiSetImageSource(sg, uri)

  /** An edge `width` pixels wide inside the node's box. A zero width or no colour takes it away. */
  def setBorder(width: Double, hex: String): Unit = {
    edgeWidth = width
    edgeHex = hex
    paint()
  }

  /** The fill and the edge, for the box as it is now, the same on the Roku as elsewhere. Square: the Rectangle's own
    * colour, and four thin rectangles along the sides. Rounded: the Rectangle clear, and Posters behind the children, a
    * filled 9-patch and an edge one, white and tinted to their colours (`images/rr`, made by the channel build, for
    * whole radii up to 64 pixels and edges up to 8). */
  private var gradientHex: String = ""
  /** A gradient is its first colour here: SceneGraph draws none. */
  def setGradient(angle: Double, stops: String): Unit = {
    gradientHex = RenderNode.firstStop(stops)
    paint()
  }
  /** The letters in the gradient's first colour. */
  def setTextGradient(angle: Double, stops: String): Unit = if (stops.nonEmpty) setColor(RenderNode.firstStop(stops))
  /** No shadow is drawn. */
  def setShadow(x: Double, y: Double, blur: Double, hex: String): Unit = ()
  /** Each line of wrapped text `advance` units below the last: the Label's own spacing, to what the face leaves. */
  def setLineHeight(advance: Double): Unit = {
    lineAdvance = advance
    spaceLines()
  }

  /** The Label's spacing between lines, set for the advance asked for at its current face and size. */
  private def spaceLines(): Unit =
    // (with no advance asked for, the face's own: the theme's spacing is far looser than any other backend's)
    if (wrapping && lineHeight > 0.0) uiSetLineSpacing(sg, px(math.max(0.0, lineAdvance - lineHeight)))
  /** A link is followed by its select; there is no address to show. */
  def setHref(href: String, newTab: Boolean): Unit = ()
  /** A drop-down draws itself here (`everyui.component.Select`): the node is its words. */
  def setOptions(labels: List[String]): Unit = ()
  /** Styled runs are drawn as the paragraph's own style, their words run together. */
  def setRuns(runs: List[everyui.text.TextRun], follow: String => Unit): Unit = setText(runs.map(r => r.text).mkString)

  private def paint(): Unit = {
    val fill = if (gradientHex.nonEmpty) gradientHex else fillHex
    val w = if (boxWidth >= 0.0) boxWidth else getWidth()
    val h = if (boxHeight >= 0.0) boxHeight else getHeight()
    val edged = edgeWidth > 0.0 && edgeHex.nonEmpty
    // A corner image is chosen in the panel's pixels: SceneGraph draws a 9-patch's corners at the image's size, not
    // scaled with the node (a 1080p design on a 720p panel would squeeze them into ellipses).
    val panel = uiPanelScale()
    val r = math.min(math.min(math.round(px(cornerRadius) * panel).toInt, math.floor(px(math.min(w, h)) * panel / 2.0).toInt), 64)
    if (r >= 1) {
      uiSetBackgroundColor(sg, "")
      clearEdges()
      if (fill.nonEmpty) fillShape = Some(uiShape(sg, fillShape, "pkg:/images/rr/fill-" + r.toString + ".9.png", fill, px(w), px(h), 0))
      else fillShape = dropShape(fillShape)
      if (edged) {
        val stroke = math.max(1, math.min(8, math.round(px(edgeWidth) * panel).toInt))
        val uri = "pkg:/images/rr/ring-" + math.max(r, stroke).toString + "-" + stroke.toString + ".9.png"
        edgeShape = Some(uiShape(sg, edgeShape, uri, edgeHex, px(w), px(h), if (fillShape.nonEmpty) 1 else 0))
      } else edgeShape = dropShape(edgeShape)
    } else {
      fillShape = dropShape(fillShape)
      edgeShape = dropShape(edgeShape)
      uiSetBackgroundColor(sg, fill)
      if (edged) placeEdges(w, h) else clearEdges()
    }
  }

  private def dropShape(shape: Option[SGNode]): Option[SGNode] = {
    shape.foreach(s => uiRemove(s))
    None
  }

  private def clearEdges(): Unit = {
    edges.foreach(e => uiRemove(e))
    edges = Vector.empty
  }

  private def placeEdges(w: Double, h: Double): Unit = {
    while (edges.length < 4) {
      val edge = uiContainer()
      uiAppend(sg, edge)
      edges = edges :+ edge
    }
    edges.foreach(e => uiSetBackgroundColor(e, edgeHex))
    val b = math.min(edgeWidth, math.min(w, h) / 2.0)
    placeEdge(edges(0), 0.0, 0.0, w, b)
    placeEdge(edges(1), 0.0, h - b, w, b)
    placeEdge(edges(2), 0.0, b, b, h - b * 2.0)
    placeEdge(edges(3), w - b, b, b, h - b * 2.0)
  }

  private def placeEdge(edge: SGNode, x: Double, y: Double, width: Double, height: Double): Unit = {
    uiSetTranslation(edge, px(x), px(y))
    uiSetWidth(edge, px(width))
    uiSetHeight(edge, px(height))
  }
  /** Labels sit where they are placed. */
  def setTextAlign(align: String): Unit = ()

  /** A field here is a line of text (TextInput asks the keyboard for its value), so its contents are its text. */
  def getValue(): String = uiGetText(sg)
  def setValue(value: String): Unit = uiSetText(sg, value)
  def setPlaceholder(text: String): Unit = ()
  def onValue(f: String => Unit): Unit = ()
  def onInputFocus(f: () => Unit): Unit = ()
  def onInputDone(f: () => Unit): Unit = ()
  def focusInput(on: Boolean): Unit = ()
}

/** Backend node factories. */
object RenderNode {
  /** The first colour of `everyui.Gradient`'s encoding, as the seam takes a colour. */
  def firstStop(stops: String): String = {
    val space = stops.indexOf(' ')
    val hex = if (space < 0) stops else stops.substring(0, space)
    if (hex.length == 9 && hex.toLowerCase.endsWith("ff")) hex.substring(0, 7) else hex
  }
}

object Nodes {
  /** The channel's root Group, created by the generated `AppScene`. */
  def root(): RenderNode = new RenderNode(uiRoot())
  /** A text node — a SceneGraph `Label`. */
  def text(): RenderNode = new RenderNode(uiText())
  /** A container — a transparent `Rectangle` other nodes are appended to. */
  def container(): RenderNode = new RenderNode(uiContainer())
  /** No text field: a line of text, which TextInput fills from the keyboard. */
  def input(secure: Boolean): RenderNode = new RenderNode(uiText())
  /** A box of lines is a field like any other: the Roku keyboard edits it. */
  def textArea(): RenderNode = new RenderNode(uiText())
  /** A link: a container; its select follows it. */
  def link(): RenderNode = container()
  /** A drop-down draws itself here: its node is a line of text. */
  def select(): RenderNode = text()

  /** A Material Icons glyph, by codepoint, `size` pixels. */
  def icon(glyph: String, size: Double): RenderNode = {
    val node = new RenderNode(uiText())
    node.setFont("Material Icons", 400, size)
    node.setText(glyph)
    node
  }
  /** An image node — a SceneGraph `Poster` loading from `pkg:/`. */
  def image(uri: String, width: Double, height: Double): RenderNode =
    new RenderNode(uiImage(uri, width * uiScale(), height * uiScale()))
}

/** The handful of non-render platform services the slice needs. */
object Platform {
  def log(message: String): Unit = uiLog(message)
  def logError(message: String, t: Throwable): Unit = uiLog("ERROR " + message + ": " + t.getMessage)
  def ticks(): Double = uiTicks()
  def animate(tick: () => Unit): Unit = uiAnimate(tick)

  /** Assets live in the channel package: `/images/icon.png` is `pkg:/images/icon.png`. */
  def resolvePath(path: String): String = "pkg:" + path

  def loadImage(uri: String, loaded: (Double, Double) => Unit): Unit = uiLoadImage(uri, loaded)

  /** Bundled faces load synchronously from the channel package. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit = loaded()

  /** Fonts load synchronously from the channel package, so this runs `f` now. */
  def whenFontsReady(f: () => Unit): Unit = f()

  /** Channel-package URI — Roku loads assets from `pkg:/`. */
  def imagePath(name: String): String = "pkg:/images/" + name

  def kind: PlatformKind = PlatformKind.Roku
  /** A remote, and nothing else. */
  def pointerSupport(): Boolean = false
  def touchSupport(): Boolean = false
  def remoteNavigation(): Boolean = true
  def keyboardSupport(): Boolean = false

  /** The shared ScrollContainer scrolls. */
  def nativeScroll(): Boolean = false
  /** No browser, no clipboard. */
  def openUrl(url: String): Unit = ()
  /** A channel has no browser to go to. */
  def navigate(url: String): Unit = ()
  /** A channel has no title bar. */
  def setTitle(title: String): Unit = ()
  def copyText(text: String): Unit = ()

  /** No address bar: no path to show, push or follow. */
  def path(): String = ""
  def pushPath(path: String): Unit = ()
  def onPath(f: String => Unit): Unit = ()

  /** No browser history here: the screen manager keeps its own. */
  def historyBack(): Boolean = false

  /** On a later turn of the render thread, from a fresh stack (a Timer): what a JavaFX runLater or a browser's
    * setTimeout gives, and what keeps a chain of callbacks from nesting into a Roku's small stack. */
  def later(f: () => Unit): Unit = uiLater(f)

  /** A packaged file (`pkg:/l10n/app_en.arb`), read at once. */
  def readText(path: String, done: String => Unit): Unit = done(uiReadText(path))
  /** An ARB file's messages, parsed by the Roku's own JSON parser into a keyed table: a Scala parser and an entry-list
    * Map take the Roku most of a minute over a translation file of a few thousand entries. */
  def parseMessages(arb: String): Map[String, String] = uiArbMessages(arb)
  def language(): String = uiLanguage()
  def fromCodePoint(codePoint: Int): String = uiChr(codePoint)
  def setDirection(rtl: Boolean): Unit = ()
  /** The app's language: which CJK glyphs text takes (Traditional for zh_Hant). */
  def setLanguage(code: String): Unit = uiSetLanguage(code)

  /** Kept in the registry, across launches. */
  def setting(name: String): String = uiSetting(name)
  /** Keep `value` under `name`; "" forgets it. */
  def saveSetting(name: String, value: String): Unit = uiSaveSetting(name, value)

  def deviceName(): String = uiDeviceName()

  /** No text fields of its own. */
  def nativeInput(): Boolean = false
  /** The platform keyboard is still to be wired to the runtime, so no value comes back yet. */
  def editText(title: String, value: String, secure: Boolean, done: String => Unit): Unit = ()

  /** Faces are files the runtime opens by name. */
  def registerFont(face: Face): Unit = ()

  def screenWidth(): Double = uiScreenWidth()
  def screenHeight(): Double = uiScreenHeight()

  /** `pkg:/freeze.txt` in the channel (added by tools/check.sh to a test sideload): step the animation that far on
    * a virtual clock and stop. Negative when absent. */
  def freezeSeconds(): Double = uiFreezeSeconds()

  /** The frozen frame is complete. (tools/check.sh reads it off the debug console.) */
  def frozen(): Unit = ()

  /** `pkg:/<name>.txt` in the channel (a test sideload adds it, `roku-install --add name=value`), or "". */
  def launchArg(name: String): String = uiLaunchArg(name)

  /** `millis` since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
  def localDateTime(millis: Long): String = uiLocalDateTime(millis.toDouble)

  /** No page to be served from: an app names its server. */
  def webSocketOrigin(): String = ""

  /** A channel cannot exit itself from the render thread; a demo that is done just stops. */
  def quit(): Unit = ()

  /** A channel's design resolution never changes. */
  /** Every key of the remote, by the web's name (OK is "Enter"), pressed or released. */
  def onKey(f: (String, Boolean) => Boolean): Unit = uiOnKey(f)

  def onResize(f: () => Unit): Unit = ()

  // BrightScript intrinsics, through everyui's runtime. The web backend
  // supplies the same four from the real Scala stdlib.
  def sqrt(x: Double): Double = uiSqrt(x)
  def pow(x: Double, y: Double): Double = uiPow(x, y)
  def sin(x: Double): Double = uiSin(x)
  def cos(x: Double): Double = uiCos(x)
}
