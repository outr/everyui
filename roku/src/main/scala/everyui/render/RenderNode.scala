package everyui.render

import everyui.font.Faces
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
class RenderNode(val sg: SGNode):
  // Every position change goes through this node, so it keeps its own coordinates and writes SceneGraph's
  // `translation` in one step rather than reading the field back on every write.
  private var left: Double = uiGetX(sg)
  private var top: Double = uiGetY(sg)
  // A text node's face and size, once set: its box comes from them rather than from SceneGraph's bounding rect.
  private var fontUri: String = ""
  private var fontSize: Double = 0.0
  private var lineHeight: Double = 0.0

  def getText(): String = uiGetText(sg)
  def setText(text: String): Unit = uiSetText(sg, text)

  def getX(): Double = left
  def setX(x: Double): Unit =
    left = x
    uiSetTranslation(sg, left, top)

  def getY(): Double = top
  def setY(y: Double): Unit =
    top = y
    uiSetTranslation(sg, left, top)

  /** Radians, clockwise, about the node's own centre — matching youi's
    * `ImageView.rotation`, which is in TURNS (0..1); the example converts. */
  def getRotation(): Double = uiGetRotation(sg)
  def setRotation(radians: Double): Unit = uiSetRotation(sg, radians)

  def setFontSize(size: Double): Unit = uiSetFontSize(sg, size)

  /** `"#rrggbb"`. */
  def setColor(hex: String): Unit = uiSetColor(sg, hex)

  /** A bundled face (`assets/fonts/`, copied to `pkg:/fonts/`): family, CSS
    * weight, size in pixels. */
  def setFont(family: String, weight: Int, size: Double): Unit =
    uiSetFont(sg, family, weight, size)
    if family.nonEmpty then
      val face = Faces(family, weight)
      fontUri = "pkg:/fonts/" + face.file
      fontSize = size
      lineHeight = face.lineHeight(size)

  /** The laid-out size in pixels. Text is as wide as its face makes it at exactly its size (a `Label` draws at whole
    * pixel sizes, so its own bounding rect is a few pixels off) and one shared line (everyui.font.Face) tall;
    * anything else is SceneGraph's bounding rect. */
  def getWidth(): Double = if fontSize > 0.0 then uiTextWidth(sg, fontUri, fontSize) else uiGetWidth(sg)
  def getHeight(): Double = if lineHeight > 0.0 then lineHeight else uiGetHeight(sg)

  def append(child: RenderNode): Unit = uiAppend(sg, child.sg)

  /** Detach from the parent, if attached. */
  def remove(): Unit = uiRemove(sg)

  def setWidth(width: Double): Unit = uiSetWidth(sg, width)
  def setHeight(height: Double): Unit = uiSetHeight(sg, height)

  /** `"#rrggbb"`, or `""` for transparent. */
  def setBackgroundColor(hex: String): Unit = uiSetBackgroundColor(sg, hex)
  def setRadius(radius: Double): Unit = uiSetRadius(sg, radius)

  /** SceneGraph places every node by its translation, so there is no position type to set. */
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click"). */
  def onPointer(kind: String, f: (Double, Double) => Unit): Unit = uiOnPointer(sg, kind, f)

  /** The shared ScrollContainer shifts the content; the node only clips. */
  def setScrollable(vertical: Boolean): Unit = ()
  def setScrollTop(y: Double): Unit = ()
  def setClip(width: Double, height: Double): Unit = uiSetClip(sg, width, height)
  /** No pointer to rest. */
  def setTooltip(text: String): Unit = ()

  def setPositionType(name: String): Unit = ()

  /** A Label is a single line unless told to wrap. */
  def setWhiteSpace(value: String): Unit = ()

  def setImageSource(uri: String): Unit = uiSetImageSource(sg, uri)

/** Backend node factories. */
object Nodes:
  /** The channel's root Group, created by the generated `AppScene`. */
  def root(): RenderNode = new RenderNode(uiRoot())
  /** A text node — a SceneGraph `Label`. */
  def text(): RenderNode = new RenderNode(uiText())
  /** A container — a transparent `Rectangle` other nodes are appended to. */
  def container(): RenderNode = new RenderNode(uiContainer())

  /** A Material Icons glyph, by codepoint, `size` pixels. */
  def icon(glyph: String, size: Double): RenderNode =
    val node = new RenderNode(uiText())
    node.setFont("Material Icons", 400, size)
    node.setText(glyph)
    node
  /** An image node — a SceneGraph `Poster` loading from `pkg:/`. */
  def image(uri: String, width: Double, height: Double): RenderNode =
    new RenderNode(uiImage(uri, width, height))

/** The handful of non-render platform services the slice needs. */
object Platform:
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

  /** The shared ScrollContainer scrolls. */
  def nativeScroll(): Boolean = false
  /** No browser, no clipboard. */
  def openUrl(url: String): Unit = ()
  def copyText(text: String): Unit = ()

  /** No address bar: no path to show, push or follow. */
  def path(): String = ""
  def pushPath(path: String): Unit = ()
  def onPath(f: String => Unit): Unit = ()

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
  def onKey(f: (String, Boolean) => Unit): Unit = uiOnKey(f)

  def onResize(f: () => Unit): Unit = ()

  // BrightScript intrinsics, through everyui's runtime. The web backend
  // supplies the same four from the real Scala stdlib.
  def sqrt(x: Double): Double = uiSqrt(x)
  def pow(x: Double, y: Double): Double = uiPow(x, y)
  def sin(x: Double): Double = uiSin(x)
  def cos(x: Double): Double = uiCos(x)
