package everyui.render

import everyui.font.{Face, Faces}
import everyui.PlatformKind
import everyui.event.PointerKind
import everyui.tvos.*

/** **The one invention.** The abstract render node, tvOS edition: a UIKit view behind a get/set/append surface
  * that `Prop` can be built against — the Roku backend's twin, facade for facade (`everyui.tvos.facades`,
  * implemented by `swift/runtime/UniversalRuntime.swift`), compiled to Swift by everyui's compiler.
  *
  * The shared UI layer names only this type. Its DOM twin
  * (`web/.../everyui/render/RenderNode.scala`) has the identical signature
  * over an `Element` — same names, same arities — so `Component` and `Label`
  * compile unchanged against either. Selection is by SOURCE ROOT at compile
  * time (the Scala.js / Scala Native pattern), not by subtyping, so neither
  * backend pays for dispatch.
  *
  * Every method takes explicit parens. A paren-less getter on another user
  * instance is ambiguous with a field read in the emitted code
  * (`node.getText` would yield the function ref, not call it).
  */
class RenderNode(val sg: SGNode) {
  // Every position change goes through this node, so it keeps its own coordinates and writes the view's
  // position in one step rather than reading it back on every write.
  private var left: Double = uiGetX(sg)
  private var top: Double = uiGetY(sg)
  // A text node's face and size, once set: its box comes from them rather than from the label's own metrics.
  private var fontUri: String = ""
  private var fontSize: Double = 0.0
  private var lineHeight: Double = 0.0

  def getText(): String = uiGetText(sg)
  def setText(text: String): Unit = uiSetText(sg, text)

  def getX(): Double = left
  def setX(x: Double): Unit = {
    left = x
    uiSetTranslation(sg, left, top)
  }

  def getY(): Double = top
  def setY(y: Double): Unit = {
    top = y
    uiSetTranslation(sg, left, top)
  }

  /** Radians, clockwise, about the node's own centre — matching youi's
    * `ImageView.rotation`, which is in TURNS (0..1); the example converts. */
  def getRotation(): Double = uiGetRotation(sg)
  def setRotation(radians: Double): Unit = uiSetRotation(sg, radians)

  /** Drawn larger or smaller about its own centre. */
  def setScale(factor: Double): Unit = uiSetScale(sg, factor)

  def setFontSize(size: Double): Unit = uiSetFontSize(sg, size)

  /** `"#rrggbb"`. */
  def setColor(hex: String): Unit = uiSetColor(sg, hex)

  /** A bundled face (`assets/fonts/`, in the app bundle): family, CSS weight, size in points. */
  def setFont(family: String, weight: Int, size: Double): Unit = {
    uiSetFont(sg, family, weight, size)
    if (family.nonEmpty) {
      val face = Faces(family, weight)
      fontUri = "fonts/" + face.file
      fontSize = size
      lineHeight = face.lineHeight(size)
    }
  }

  /** The laid-out size in points. Text is as wide as its face makes it at exactly its size (the advance width,
    * as every backend measures it) and one shared line (everyui.font.Face) tall; anything else is what was set. */
  def getWidth(): Double = if (fontSize > 0.0) uiTextWidth(sg, fontUri, fontSize) else uiGetWidth(sg)
  def getHeight(): Double = if (lineHeight > 0.0) lineHeight else uiGetHeight(sg)

  def append(child: RenderNode): Unit = uiAppend(sg, child.sg)

  /** Detach from the parent, if attached. */
  def remove(): Unit = uiRemove(sg)

  // media is not played on tvOS yet (an AVPlayer layer is to come): a video or an audio node is an empty view
  def setMediaHeaders(headers: Map[String, String]): Unit = ()
  def setMediaSource(url: String): Unit = ()
  def setMediaPlaying(playing: Boolean): Unit = ()
  def setMediaLoop(loop: Boolean): Unit = ()
  def setMediaMuted(muted: Boolean): Unit = ()
  def setMediaVolume(volume: Double): Unit = ()
  def setMediaRate(rate: Double): Unit = ()
  def mediaRates(): Boolean = false
  // neither what it holds nor what it waits for can be told here
  def setMediaBuffering(named: String): Unit = ()
  // it does not say whether it is waiting; the playhead standing still is what tells a viewer
  def mediaBuffering(): Boolean = false
  // nothing here counts what the link is carrying
  def mediaBandwidth(): Long = 0L
  def mediaSize(): (Double, Double) = (0.0, 0.0)
  /** The words on screen: "" here, where the platform draws its own subtitles. */
  def mediaCues(): String = ""

  // ---- a page of the web ----
  def setWebSource(url: String): Unit = ()
  def runWebScript(script: String): Unit = ()
  def onWebMessage(f: String => Unit): Unit = ()

  /** What to hear and read this in: nothing here, where the platform chooses its own tracks. */
  def setMediaLanguages(audio: String, text: String): Unit = ()

  def mediaTracks(): List[everyui.component.MediaTrack] = Nil
  def selectMediaTrack(kind: String, id: String): Unit = ()
  def setMediaCover(cover: Boolean): Unit = ()
  def getMediaPosition(): Double = 0.0
  def getMediaDuration(): Double = 0.0
  def seekMedia(seconds: Double): Unit = ()
  def onMediaEvent(f: String => Unit): Unit = ()
  def releaseMedia(): Unit = ()

  def setWidth(width: Double): Unit = uiSetWidth(sg, width)
  def setHeight(height: Double): Unit = uiSetHeight(sg, height)

  /** `"#rrggbb"`, or `""` for transparent. */
  def setBackgroundColor(hex: String): Unit = uiSetBackgroundColor(sg, hex)
  def setRadius(radius: Double): Unit = uiSetRadius(sg, radius)

  /** Every view is placed by its own position, so there is no position type to set. */
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
  def setOpacity(opacity: Double): Unit = uiSetOpacity(sg, opacity)
  def setClip(width: Double, height: Double): Unit = uiSetClip(sg, width, height)
  /** No pointer to rest. */
  def setTooltip(text: String): Unit = ()

  def setPositionType(name: String): Unit = ()

  /** A label is a single line. */
  def setWhiteSpace(value: String): Unit = ()

  def setImageSource(uri: String): Unit = uiSetImageSource(sg, uri)
  /** The picture as it is: no tint here yet. */
  def setTint(hex: String): Unit = ()

  /** No edge: a SceneGraph Rectangle has none to draw. */
  def setBorder(width: Double, hex: String): Unit = ()
  /** A gradient is its first colour here. */
  def setGradient(angle: Double, stops: String): Unit = if (stops.nonEmpty) setBackgroundColor(Nodes.firstStop(stops))
  /** The letters in the gradient's first colour. */
  def setTextGradient(angle: Double, stops: String): Unit = if (stops.nonEmpty) setColor(Nodes.firstStop(stops))
  /** SceneGraph keeps no pictures of its own to ask for. */
  def setCached(cached: Boolean): Unit = ()
  /** Nothing moves on its own here yet. */
  def setMotion(tracks: List[everyui.motion.Track]): Unit = ()
  /** No shadow is drawn. */
  def setShadow(x: Double, y: Double, blur: Double, hex: String): Unit = ()
  /** Nothing is drawn blurred here. */
  def setBlur(radius: Double): Unit = ()
  /** Lines keep the face's own spacing. */
  def setLineHeight(px: Double): Unit = ()
  /** A link is followed by its select; there is no address to show. */
  def setHref(href: String, newTab: Boolean): Unit = ()
  /** A drop-down draws itself here (`everyui.component.Select`): the node is its words. */
  def setOptions(labels: List[String]): Unit = ()
  /** Styled runs are drawn as the label's own style, their words run together. */
  def setRuns(runs: List[everyui.text.TextRun], follow: String => Unit): Unit = setText(runs.map(r => r.text).mkString)
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
object Nodes {
  /** A page of the web: nothing here, where the platform has no engine of its own to lend. */
  def web(bridge: String): RenderNode = container()

  /** The root view controller's view. */
  def root(): RenderNode = new RenderNode(uiRoot())
  /** A text node — a `UILabel`. */
  def text(): RenderNode = new RenderNode(uiText())
  /** A container — a `UIView` other nodes are appended to. */
  def container(): RenderNode = new RenderNode(uiContainer())
  /** No text field: a line of text, which TextInput fills from the keyboard. */
  def input(secure: Boolean): RenderNode = new RenderNode(uiText())
  /** A box of lines is a field like any other. */
  def textArea(): RenderNode = new RenderNode(uiText())
  /** A link: a container; its select follows it. */
  def link(): RenderNode = container()
  /** A drop-down draws itself here: its node is a line of text. */
  def select(): RenderNode = text()

  /** The first colour of `everyui.Gradient`'s encoding, as the seam takes a colour. */
  def firstStop(stops: String): String = {
    val space = stops.indexOf(' ')
    val hex = if (space < 0) stops else stops.substring(0, space)
    if (hex.length == 9 && hex.toLowerCase.endsWith("ff")) hex.substring(0, 7) else hex
  }

  /** A Material Icons glyph, by codepoint, `size` pixels. */
  def icon(glyph: String, size: Double): RenderNode = {
    val node = new RenderNode(uiText())
    node.setFont("Material Icons", 400, size)
    node.setText(glyph)
    node
  }
  /** A video: an empty view until tvOS plays media. */
  def video(): RenderNode = container()
  /** Sound: an empty view until tvOS plays media. */
  def audio(): RenderNode = container()

  /** An image node — a `UIImageView` loading from the app bundle. */
  def image(uri: String, width: Double, height: Double): RenderNode =
    new RenderNode(uiImage(uri, width, height))
}

/** The handful of non-render platform services the slice needs. */
object Platform {
  /** Whether this device can go on playing in a corner of whatever the viewer does next, and doing it. */
  /** Whether this platform has a browser engine to lend a page (Nodes.web): not yet here. */
  def webViews(): Boolean = false

  def corners(): Boolean = false

  def toCorner(): Boolean = false

  /** What to do when the app is at the front again after being away. Nothing here says so yet, so nothing is ever
    * called: a caller must not depend on it as its only way of noticing somebody is back. */
  def onResume(f: () => Unit): Unit = resumers = resumers :+ f

  def resumed(): Unit = resumers.foreach(f => f())

  private var resumers: List[() => Unit] = Nil

  def log(message: String): Unit = uiLog(message)
  def logError(message: String, t: Throwable): Unit = uiLog("ERROR " + message + ": " + t.getMessage)
  def ticks(): Double = uiTicks()
  def animate(tick: () => Unit): Unit = uiAnimate(tick)

  /** Assets live in the app bundle; the runtime resolves a site-absolute path against it. */
  def resolvePath(path: String): String = path

  def loadImage(uri: String, loaded: (Double, Double) => Unit): Unit = uiLoadImage(uri, loaded)

  /** Bundled faces are registered at launch. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit = loaded()

  /** Fonts are registered at launch, so this runs `f` now. */
  def whenFontsReady(f: () => Unit): Unit = f()

  /** A bundle-relative image path. */
  def imagePath(name: String): String = "/images/" + name

  def kind: PlatformKind = PlatformKind.TvOS
  /** A remote, and nothing else. */
  def pointerSupport(): Boolean = false
  def touchSupport(): Boolean = false
  def remoteNavigation(): Boolean = true
  def keyboardSupport(): Boolean = false

  /** The shared ScrollContainer scrolls. */
  def nativeScroll(): Boolean = false
  /** No browser, no clipboard. */
  def openUrl(url: String): Unit = ()
  /** An Apple TV app has no browser to go to. */
  def navigate(url: String): Unit = ()
  /** An Apple TV app has no title bar. */
  def setTitle(title: String): Unit = ()
  def copyText(text: String): Unit = ()
  /** The device keeps its own screen timing here. */
  def keepAwake(on: Boolean): Unit = ()
  /** The device names itself to the servers its pictures come from. */
  def identify(app: String): Unit = ()

  /** No address bar: no path to show, push or follow. */
  def path(): String = ""
  def pushPath(path: String): Unit = ()
  def onPath(f: String => Unit): Unit = ()

  /** One thread: `f` runs now. */
  /** No browser history here: the screen manager keeps its own. */
  def historyBack(): Boolean = false

  def later(f: () => Unit): Unit = f()
  /** Nothing waits here yet, so nothing comes of it. */
  def after(seconds: Double, f: () => Unit): Unit = ()

  /** An ARB file's messages (everyui.i18n.Arb). */
  def parseMessages(arb: String): Map[String, String] = everyui.i18n.Arb.parse(arb)

  /** Not read yet: the runtime is still to open packaged text files, so translations fall back to their keys. */
  def readText(path: String, done: String => Unit): Unit = done("")
  def language(): String = "en"
  def fromCodePoint(codePoint: Int): String = uiChr(codePoint)
  /** The app's language: the platform chooses its own faces for the script, so there is nothing to do. */
  def setLanguage(code: String): Unit = ()

  def setDirection(rtl: Boolean): Unit = ()

  /** Not kept yet: the registry is still to be wired to the runtime. */
  def setting(name: String): String = ""
  def saveSetting(name: String, value: String): Unit = ()

  /** The device's own store of documents (see [[everyui.store.Store]]). Nothing is kept here yet, as nothing is
    * kept by `setting` either — an Apple television would write these under Application Support, and until this
    * target is built out it answers the way an empty cache answers, which every caller already has to handle. */
  def storeGet(space: String, key: String, got: Option[String] => Unit): Unit = got(None)
  def storePut(space: String, key: String, value: String, done: () => Unit): Unit = done()
  def storeDelete(space: String, key: String, done: () => Unit): Unit = done()
  def storeKeys(space: String, got: List[String] => Unit): Unit = got(Nil)
  def storeClear(space: String, done: () => Unit): Unit = done()

  /** What it is, as its maker names it; the same as its name where the platform knows no more than that. */
  def deviceModel(): String = deviceName()

  def launchesApps(): Boolean = true
  def localAddress(): String = ""
  def deviceSerial(): String = ""

  /** Where the app came from, and which build it is: nothing this platform can say. */
  def installStore(): String = ""
  def appVersion(): Int = 0

  def deviceName(): String = "Apple TV"

  /** No text fields of its own. */
  def nativeInput(): Boolean = false
  /** The platform keyboard is still to be wired to the runtime, so no value comes back yet. */
  def editText(title: String, value: String, secure: Boolean, done: String => Unit): Unit = ()

  /** Faces are files the runtime opens by name. */
  def registerFont(face: Face): Unit = ()

  def screenWidth(): Double = uiScreenWidth()
  def screenHeight(): Double = uiScreenHeight()

  /** `--freeze=<seconds>` on the launch (tools/tvos-run.sh --freeze): step the animation that far on a virtual
    * clock and stop. Negative when absent. */
  def freezeSeconds(): Double = uiFreezeSeconds()

  /** The frozen frame is complete. (tools/tvos-run.sh reads it off the app's console.) */
  def frozen(): Unit = ()

  /** `--name=value` on the launch, or "". */
  def launchArg(name: String): String = uiLaunchArg(name)

  /** `millis` since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
  def localDateTime(millis: Long): String = uiLocalDateTime(millis.toDouble)

  /** No page to be served from: an app names its server. */
  def webSocketOrigin(): String = ""

  /** Exit the process (a demo that is done). */
  def quit(): Unit = uiQuit()

  /** An Apple TV's screen never changes size. */
  /** Every key of the remote, by the web's name (OK is "Enter"), pressed or released. */
  def onKey(f: (String, Boolean) => Boolean): Unit = uiOnKey(f)

  def onResize(f: () => Unit): Unit = ()

  // Through the runtime, as the Roku backend has them.
  /** A number from 0 up to 1, at random. */
  def random(): Double = uiRandom()
  def sqrt(x: Double): Double = uiSqrt(x)
  def pow(x: Double, y: Double): Double = uiPow(x, y)
  def sin(x: Double): Double = uiSin(x)
  def cos(x: Double): Double = uiCos(x)
}
