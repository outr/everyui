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
    fontFamily = family
    fontWeight = weight
    fontPoints = size
    if (italic) uiSetItalic(sg)
    if (family.nonEmpty) {
      val face = Faces(family, weight)
      fontUri = "fonts/" + face.file
      fontSize = size
      lineHeight = face.lineHeight(size)
    }
  }

  /** The laid-out size in points. Text is as wide as its face makes it at exactly its size (the advance width,
    * as every backend measures it) and one shared line (everyui.font.Face) tall; anything else is what was set. */
  /** Always: a node here is measured where it is, in the page or not (only the web's are measured in batches). */
  def onPage: Boolean = true

  def getWidth(): Double = if (fontSize > 0.0) uiTextWidth(sg, fontUri, fontSize) else uiGetWidth(sg)
  /** How wide the words are whole, whatever width the view is given: as they are measured. */
  def textWidth(): Double = getWidth()
  def getHeight(): Double = if (lineHeight > 0.0) lineHeight else uiGetHeight(sg)

  def append(child: RenderNode): Unit = uiAppend(sg, child.sg)
  // no ordered insert on this target yet: appended, which is where most children go
  def insert(child: RenderNode, index: Int): Unit = uiAppend(sg, child.sg)

  /** Detach from the parent, if attached. */
  def remove(): Unit = uiRemove(sg)

  // media is not played on tvOS yet (an AVPlayer layer is to come): a video or an audio node is an empty view
  def setMediaHeaders(headers: Map[String, String]): Unit = ()
  def setMediaSource(url: String): Unit = ()
  def setMediaStart(seconds: Double): Unit = ()
  def setMediaSidecars(sidecars: List[everyui.component.MediaSidecar]): Unit = ()
  def setMediaPlaying(playing: Boolean): Unit = ()
  def setMediaLoop(loop: Boolean): Unit = ()
  def setMediaMuted(muted: Boolean): Unit = ()
  def setMediaVolume(volume: Double): Unit = ()
  def setMediaRate(rate: Double): Unit = ()
  def mediaRates(): Boolean = false
  /** Whether the media has enough in hand to play to its end without stopping (`Backdrop`): said once it plays here. */
  def mediaPlaysThrough(): Boolean = true
  // nothing plays here yet, so nothing is folded
  def setMediaCenterMix(db: Double): Unit = ()
  def mediaCenterMixes(): Boolean = false
  def mediaCenterMixing(): Boolean = false
  def setMediaAids(clarity: Double, night: Double): Unit = ()
  def mediaAidsSupported(): Boolean = false
  def mediaAidsHeard(): Boolean = false
  // neither what it holds nor what it waits for can be told here
  def setMediaBuffering(named: String): Unit = ()
  // nowhere of its own to keep a clip: every one is fetched as it plays
  def setMediaCached(cached: Boolean): Unit = ()
  // it does not say whether it is waiting; the playhead standing still is what tells a viewer
  def mediaBuffering(): Boolean = false
  // nothing here counts what the link is carrying
  def mediaBandwidth(): Long = 0L
  def mediaSize(): (Double, Double) = (0.0, 0.0)
  /** The words on screen: "" here, where the platform draws its own subtitles. */
  def mediaCues(): String = ""
  def mediaCueList(): List[everyui.component.MediaCue] = Nil
  def mediaRetry(): Option[everyui.component.MediaRetry] = None
  // nothing here shows what is playing, or hears it
  def setNowPlaying(about: everyui.component.NowPlaying): Unit = ()
  def setMediaSpectrum(on: Boolean): Unit = ()
  def mediaBands(): List[Double] = Nil

  // ---- a page of the web ----
  def setWebSource(url: String): Unit = ()
  def setWebAgent(agent: String): Unit = ()
  def setWebSandbox(flags: Option[Set[String]]): Unit = ()
  def setWebClear(clear: Boolean): Unit = ()
  def runWebScript(script: String): Unit = ()
  def onWebMessage(f: String => Unit): Unit = ()
  def setWebHearSandboxed(on: Boolean): Unit = ()
  def askWeb(id: Int, request: String): Boolean = false
  def onWebReply(f: (Int, String) => Unit): Unit = ()
  def onWebFrameMessage(f: Option[(String, String) => Unit]): Unit = ()
  // ---- a rich document's editing surface: none here yet (Platform.richEditing); the editor edits its Markdown ----
  def setRichDoc(doc: everyui.rich.RichDoc, selection: everyui.rich.RichRange): Unit = ()
  def setRichLook(look: everyui.rich.RichLook, placeholder: String, readOnly: Boolean): Unit = ()
  def onRichInput(f: everyui.rich.RichInput => Unit): Unit = ()
  def onRichHeight(f: Double => Unit): Unit = ()
  def focusRich(selection: everyui.rich.RichRange): Unit = ()
  def richSelection(): Option[everyui.rich.RichRange] = None
  def releaseWeb(): Unit = ()

  // ---- an embed (everyui.component.Embed): tvOS lends no web engine (WebKit is not public there), so its fallback
  // shows ----
  def embedId(): String = ""
  def embedRuntime(): String = ""
  def embedSend(message: String): Unit = ()
  def onEmbedMessage(f: String => Unit): Unit = ()
  def focusEmbed(on: Boolean): Unit = ()
  def onEmbedFocus(f: Boolean => Unit): Unit = ()
  def releaseEmbed(): Unit = ()

  /** What to hear and read this in: nothing here, where the platform chooses its own tracks. */
  def setMediaLanguages(audio: String, text: String): Unit = ()

  def mediaTracks(): List[everyui.component.MediaTrack] = Nil
  /** What was decoded: nothing this player says. */
  def mediaDiagnostics(): everyui.component.MediaDiagnostics = everyui.component.MediaDiagnostics()
  def selectMediaTrack(kind: String, id: String): Unit = ()
  def setMediaText(choice: everyui.component.TextChoice): Unit = ()
  def setMediaCover(cover: Boolean): Unit = ()
  def setMediaDirect(direct: Boolean): Unit = ()
  // an effect (AudioPlayer.effect) is as any sound here, which plays nothing yet
  def setMediaEffect(effect: Boolean): Unit = ()
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
  /** Whether the pointer is over it as what is under a still one moves: the platform's enter and leave alone here. */
  def onOver(told: Boolean => Unit): Unit = ()
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click"). */
  def onPointer(kind: PointerKind, f: (Double, Double) => Unit): Unit = uiOnPointer(sg, kind.toString, f)

  /** The shared ScrollContainer shifts the content; the node only clips. */
  def setScrollable(vertical: Boolean, horizontal: Boolean): Unit = ()
  def setScrollLeft(x: Double): Unit = ()
  /** The shared ScrollContainer moves the content itself, so the node never scrolls on its own. */
  def onScrolled(f: () => Unit): Unit = ()
  /** A scroll that runs from its left in every language: the shared ScrollContainer counts it so here. */
  def setLeftToRight(): Unit = ()
  /** The shared ScrollContainer draws no scrollbar here. */
  def setScrollbarColor(hex: String): Unit = ()
  def setScrollTop(y: Double): Unit = ()
  /** How far the node has scrolled its own overflow: never, here, where the ScrollContainer shifts the content. */
  def scrolled(horizontal: Boolean): Double = 0.0
  def scrollSmoothly(to: Double, horizontal: Boolean): Unit = ()
  /** Whether the platform draws this: what a scroll has out of sight is not drawn at all. */
  def setVisible(visible: Boolean): Unit = uiSetVisible(sg, visible)
  def setOpacity(opacity: Double): Unit = uiSetOpacity(sg, opacity)
  def setClip(width: Double, height: Double): Unit = {
    ownClip = true
    uiSetClip(sg, width, height)
  }
  // cut to its box by its container (`Container.clipsToBox`), never undoing a clip of its own (`setClip`)
  private var ownClip = false
  def setBoxClip(on: Boolean, width: Double, height: Double): Unit = if (!ownClip) {
    if (on) uiSetClip(sg, width, height) else uiClearClip(sg)
  }
  /** No pointer to rest. */
  def setTooltip(text: String): Unit = ()
  /** No reader of descriptions reaches this platform's nodes. */
  def setDescription(text: String): Unit = ()

  def setPositionType(name: String): Unit = ()

  /** A label is a single line. */
  def setWhiteSpace(value: String): Unit = ()

  def setImageSource(uri: String): Unit = uiSetImageSource(sg, uri)
  /** The picture as it is: no tint here yet. */
  def setTint(hex: String): Unit = ()

  /** Only a web page scrolls under a finger that drags: nothing to do here. */
  def setTouchDrags(on: Boolean): Unit = ()
  def setTouchScrolls(on: Boolean): Unit = ()
  /** Only a page is read for how far a list has scrolled: nothing to do here. */
  def setScrolled(at: Double, furthest: Double): Unit = ()
  /** Its words selectable: no text is selected with a pointer here (`Platform.selectsText`). */
  def setSelectable(on: Boolean): Unit = ()
  def selectText(): Unit = ()
  def holdsSelection(): Boolean = false
  /** What it is when selected words are copied (`Component.copyAs`): no text is selected here, so nothing to do. */
  def setCopyAs(tag: String): Unit = ()
  /** No pointer to show. */
  def setCursor(name: String): Unit = ()
  // what a drawing shows here, which draws no paths: its rectangles (everyui.draw.Block)
  private var blocks: List[RenderNode] = Nil
  /** A drawing's figures (everyui.draw.Drawing) as far as rectangles draw them: straight strokes across or down, and
    * filled rectangles; diagonals and curves are left out (Block.of). */
  def setFigures(figures: List[everyui.draw.Figure]): Unit = {
    blocks.foreach(_.remove())
    blocks = everyui.draw.Block.of(figures).map { b =>
      val n = Nodes.container()
      n.setX(b.x)
      n.setY(b.y)
      n.setWidth(b.width)
      n.setHeight(b.height)
      n.setBackgroundColor(everyui.component.Component.css(b.color))
      append(n)
      n
    }
  }
  /** No edge: a SceneGraph Rectangle has none to draw. */
  def setBorder(width: Double, hex: String): Unit = ()
  /** A gradient is its first colour here. */
  /** A radial gradient: filled with its first stop, as a linear one is. */
  def setRadialGradient(x: Double, y: Double, radius: Double, width: Double, height: Double, stops: String): Unit =
    setGradient(0.0, stops)
  /** No mask: drawn whole. */
  def setMask(angle: Double, x: Double, y: Double, radius: Double, stops: String): Unit = ()
  /** No shadow is drawn. */
  def setShadowLayers(layers: String): Unit = ()
  /** What is behind shows sharp. */
  def setBackdropBlur(radius: Double): Unit = ()

  def setGradient(angle: Double, stops: String): Unit = if (stops.nonEmpty) setBackgroundColor(Nodes.firstStop(stops))
  /** The letters in the gradient's first colour. */
  def setTextGradient(angle: Double, stops: String): Unit = if (stops.nonEmpty) setColor(Nodes.firstStop(stops))
  /** SceneGraph keeps no pictures of its own to ask for. */
  def setCached(cached: Boolean): Unit = ()
  /** Nothing moves on its own here yet. */
  /** What the motion is for, told when it is set (a Roku logs it for everyui_debug_motion); nothing here. */
  def setMotionOwner(owner: () => String): Unit = ()
  def setMotion(tracks: List[everyui.motion.Track]): Unit = ()
  /** No shadow is drawn. */
  def setShadow(x: Double, y: Double, blur: Double, hex: String): Unit = ()
  /** Nothing is drawn blurred here. */
  def setBlur(radius: Double): Unit = ()
  /** Lines keep the face's own spacing. */
  def setLineHeight(px: Double): Unit = ()
  /** Extra space after each letter, in points: UIKit's kern, which the measured width takes. */
  /** A variable face's optical sizing and axes: a browser's to set (`FontFeature.opticalSizing`); drawn here as the
    * platform draws the face. */
  def setFontAxes(opticalSizing: String, variations: String): Unit = ()
  def setLetterSpacing(px: Double): Unit = uiSetLetterSpacing(sg, px)
  /** Slanted: UIKit's italic of the face where it has one, else its upright letters slanted; the measured width is
    * the slanted words'. Upright again is the face set afresh. */
  def setItalic(on: Boolean): Unit = if (on != italic) {
    italic = on
    if (on) uiSetItalic(sg) else uiSetFont(sg, fontFamily, fontWeight, fontPoints)
  }
  private var italic: Boolean = false
  private var fontFamily: String = ""
  private var fontWeight: Int = 400
  private var fontPoints: Double = 0.0
  /** A link is followed by its select; there is no address to show. */
  def setHref(href: String, newTab: Boolean): Unit = ()
  /** A drop-down draws itself here (`everyui.component.Select`): the node is its words. */
  def setOptions(labels: List[String]): Unit = ()
  /** A drop-down's look, its open list in the theme's colours: a browser's only, a drop-down drawing itself here. */
  def setDropDown(background: String, text: String, highlight: String, chosen: String, edge: String,
                  inset: Double): Unit = ()
  /** Styled runs are drawn as the label's own style, their words run together. */
  def setRuns(runs: List[everyui.text.TextRun], follow: String => Unit): Unit = setText(runs.map(r => r.text).mkString)
  /** Labels sit where they are placed. */
  /** A shadow behind the letters: not drawn here. */
  def setTextShadow(x: Double, y: Double, blur: Double, hex: String): Unit = ()
  /** An edge round the letters, `width` each side, or none for "" (the runtime's OutlinedLabel). */
  def setTextOutline(width: Double, hex: String): Unit = uiSetTextOutline(sg, width, hex)
  // no reader of roles or descriptions reaches this platform's nodes
  def setSemantics(role: String, level: Int, label: String): Unit = ()
  def setSemanticState(range: Option[everyui.component.types.SemanticRange], checked: Option[Boolean],
                       expanded: Option[Boolean], selected: Option[Boolean], sort: String): Unit = ()
  /** The focus landed here: where a screen reader is on, it reads this out, and pressing there is `click`. */
  def accessibleFocus(click: () => Unit): Unit = ()
  def setAlt(text: String): Unit = ()

  def setTextAlign(align: String): Unit = ()
  def setTextDirection(value: String): Unit = ()
  def setWrapAnywhere(on: Boolean): Unit = ()
  /** Words that wrap are measured whole on one line here already, and wrap only at a width or a limit. */
  def setTextWhole(on: Boolean): Unit = ()
  /** Words cut short to fit (`TextView.maxWidth`, `maxLines`) are cut by measuring alone: nothing else holds them
    * here. */
  def setTextLimit(width: Double, lines: Int): Unit = ()
  /** Whether words held to a limit wrap at it here: not yet (no wrap width reaches the label), so words too wide for
    * their room are cut short with an ellipsis, as they were, rather than run past it. */
  def wrapsToLimit(): Boolean = false
  /** Words measure as they will be drawn from the moment they are set. */
  def textMeasurable(ready: () => Unit): Boolean = true

  /** A field here is a line of text (TextInput asks the keyboard for its value), so its contents are its text. */
  def getValue(): String = uiGetText(sg)
  def setValue(value: String): Unit = uiSetText(sg, value)
  def setPlaceholder(text: String): Unit = ()
  def setInputAction(action: String): Unit = ()
  // what a field asks for (`Autofill.token`): nothing here fills fields
  def setAutofill(token: String, section: String, name: String, passkey: Boolean, literal: Boolean): Unit = ()
  // a form (`everyui.form.Form`) is a container here: nothing sends it but the app, and nothing offers to save it
  def onSubmit(f: () => Unit): Unit = ()
  def formDone(saved: Boolean): Unit = ()
  def setHiddenUsername(username: String): Unit = ()
  def onValue(f: String => Unit): Unit = ()
  // no pasting here: a field is written on the platform's keyboard
  def onPaste(f: String => everyui.component.types.PasteAction): Unit = ()
  def onInputFocus(f: () => Unit): Unit = ()
  def onInputDone(f: () => Unit): Unit = ()
  def onInputAction(f: () => Unit): Unit = ()
  def selectAllInput(): Unit = ()
  /** No field of its own here: the caret is always after the words. */
  def getCaret(): Int = 0
  def setCaret(at: Int): Unit = ()
  /** Nothing is chosen in a field here: its words are asked for whole (`Platform.editField`), the caret after them. */
  def getSelectionStart(): Int = 0
  def getSelectionEnd(): Int = 0
  def setSelection(start: Int, end: Int): Unit = ()
  def replaceInput(start: Int, end: Int, words: String): Unit = ()
  def onSelection(f: (Int, Int) => Unit): Unit = ()
  /** Nothing reads the nodes here, and a field is only its words: the app dims what is out of use. */
  def setDisabled(disabled: Boolean): Unit = ()
  /** A field here is only its words, shown, and new ones are asked for by the app (`Platform.editField`), which a
    * read-only field does not do: there is nothing to stop. */
  def setReadOnly(on: Boolean): Unit = ()
  def focusInput(on: Boolean): Unit = ()
}

/** Backend node factories. */
object Nodes {
  /** A rich document's editing surface (everyui.rich.RichTextEditor): none here yet (`Platform.richEditing`). */
  def richEditor(): RenderNode = container()

  /** A form (`everyui.form.Form`): a container here. */
  def form(kind: String): RenderNode = container()

  /** A page of the web: nothing here, where the platform has no engine of its own to lend. */
  def web(bridge: String): RenderNode = container()

  /** An embed's box (everyui.component.Embed): a plain view, which holds its fallback. */
  def embed(name: String, site: everyui.component.EmbedSite): RenderNode = container()

  /** The root view controller's view. */
  def root(): RenderNode = new RenderNode(uiRoot())
  /** A paragraph of styled runs (`everyui.component.RichText`): a text node here, which draws them. */
  def richText(): RenderNode = text()

  /** A text node — a `UILabel`. */
  def text(): RenderNode = new RenderNode(uiText())
  /** A container — a `UIView` other nodes are appended to. */
  def container(): RenderNode = new RenderNode(uiContainer())
  /** A drawing (everyui.draw.Drawing): a container its rectangles go in, since nothing here draws paths. */
  def drawing(): RenderNode = container()
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
  /** Whether a web guest runs in an embed here: no engine to run one. */
  def embeds(): Boolean = false

  def corners(): Boolean = false

  def toCorner(): Boolean = false

  /** What to do when the app is at the front again after being away: active again after it resigned (another app
    * opened over it, the TV's own home screen or app switcher, the device asleep), told once and never at launch. */
  def onResume(f: () => Unit): () => Unit = {
    listen()
    resumers = resumers :+ f
    () => resumers = resumers.filterNot(_ eq f)
  }

  def resumed(): Unit = resumers.foreach(f => f())

  private var resumers: List[() => Unit] = Nil

  /** What to do when the app goes away from the front: another app has the screen, or the device is going to sleep.
    * A player wants to know, because nobody is watching it now. Returns what stops it being told. */
  def onAway(f: () => Unit): () => Unit = {
    listen()
    aways = aways :+ f
    () => aways = aways.filterNot(_ eq f)
  }

  /** Said by the platform when it happens. */
  def away(): Unit = aways.foreach(f => f())

  private var aways: List[() => Unit] = Nil

  // the application resigning active or going into the background is away, and active again is back: once each
  // (Presence), heard from the first app that asks
  private val presence = new everyui.app.Presence(() => away(), () => resumed())
  private var listening = false

  private def listen(): Unit = if (!listening) {
    listening = true
    uiOnLifecycle(() => presence.shown(), () => presence.hidden())
  }

  /** The app asks before it is left while a guard answers true elsewhere; a TV's Back or Home from the first screen
    * leaves without asking, so nothing is held here. What removes it. */
  def leaveGuard(guard: () => Boolean): () => Unit = () => ()
  def leaveGuardWhile(flag: reactify.Val[Boolean]): () => Unit = () => ()

  def log(message: String): Unit = uiLog(message)
  def logError(message: String, t: Throwable): Unit = uiLog("ERROR " + message + ": " + t.getMessage)
  def ticks(): Double = uiTicks()
  /** Frames as the platform measured them: not here, where the app times its own frames instead. */
  def frameTimings(f: (Double, Double) => Unit): Boolean = false
  /** Every frame runs here (`everyui.task.Frames`). */
  def framesOnDemand(): Boolean = false
  def nextFrame(f: () => Unit): Unit = later(f)
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
  /** A page's description of itself (`everyui.seo.SEO`): only its title has a place here. */
  def setSEO(meta: everyui.seo.SEO): Unit = setTitle(meta.title)
  /** The first screen is drawn: nothing was standing in for it. */
  def drawn(): Unit = ()
  /** Whether a component's size is read in a batch before the frame is drawn (`MeasuredFeature`) rather than at once:
    * here it is read at once. */
  def measuresInBatches: Boolean = false
  def beforePaint(f: () => Unit): Unit = f()
  /** `f` once the page has been painted at least once: here, at once, there being no page painted before the app. */
  def afterFirstPaint(f: () => Unit): Unit = f()

  /** Whether a `Backdrop` plays a video behind a screen (`videoSource`): not here, where it keeps its picture. */
  def backdropVideo(): Boolean = false

  /** Whether people ask for less motion (`everyui.motion.ReduceMotion`): Reduce Motion in Accessibility. */
  def reduceMotion(): Boolean = uiReduceMotion()
  /** The setting as it changes, read again as the app comes back to the front. */
  def watchReduceMotion(f: Boolean => Unit): Unit = onResume(() => f(reduceMotion()))
  /** Words for a screen reader to read out (a new message in a chat): not yet said here. */
  def announce(words: String): Unit = ()
  def setTitle(title: String): Unit = ()
  /** A count of what is new on the app's icon: no badge is shown here. */
  def setBadge(count: Int): Unit = ()
  /** Whether the person lets the app show notifications (`showNotice`): there are none here, so never. */
  def notificationPermission: reactify.Val[everyui.Permission] = noNotices
  private val noNotices = reactify.Var[everyui.Permission](everyui.Permission.Refused)
  /** Asking for notifications: there is nothing to ask for here. */
  def askNotificationPermission(): Unit = ()
  /** A notification: none is shown here. */
  def showNotice(notice: everyui.push.Notice, onClick: () => Unit): Unit = ()
  /** Whether copyText reaches a clipboard: the television has none to paste from, so a caller offers something else. */
  def clipboard(): Boolean = false
  /** Whether people select text with a pointer (`Component.selectable`): not here. */
  def selectsText(): Boolean = false
  def selectedText(): String = ""
  /** The words selected, as HTML and as text (`Clipboard.selection`): none here. */
  def selectedRich(): Option[(String, String)] = None
  def copyText(text: String): Unit = ()
  def copyRich(html: String, text: String): Unit = ()
  /** The device keeps its own screen timing here. */
  def keepAwake(on: Boolean): Unit = ()
  /** The device names itself to the servers its pictures come from. */
  def identify(app: String): Unit = ()
  /** Nothing here can scale the screen's colours: an app that wants it dimmed does it itself. */
  def tint(red: Double, green: Double, blue: Double): Boolean = false

  /** No address bar: no path to show, push or follow. */
  def path(): String = ""
  def pushPath(path: String): Unit = ()
  def onPath(f: String => Unit): Unit = ()

  /** One thread: `f` runs now. */
  /** No browser history here: the screen manager keeps its own. */
  def historyBack(): Boolean = false

  /** No address bar, and no path to replace. */
  def replacePath(path: String): Unit = ()

  /** No browser history here: the screen manager keeps its own. */
  def historyForward(): Boolean = false

  /** A value kept for this window alone: the app has one, so for this run of it. "" when there is none. */
  def windowSetting(name: String): String = windowSettings.getOrElse(name, "")

  /** Keep `value` under `name` for this run; "" forgets it. */
  def saveWindowSetting(name: String, value: String): Unit =
    windowSettings = if (value.isEmpty) windowSettings - name else windowSettings + (name -> value)

  private var windowSettings: Map[String, String] = Map.empty

  /** The time zone the device is set to, by its name for it. */
  def timeZone(): String = uiTimeZone()

  /** `f` told as the time zone changes (everyui.date.TimeZones). What stops it being told. */
  def onTimeZone(f: () => Unit): () => Unit = everyui.date.TimeZones.onChange(f)

  /** Nothing is opened outside the app here. */
  def openUrlLater(waiting: String = ""): everyui.app.UrlTarget =
    new everyui.app.UrlTarget(url => openUrl(url), () => ())

  /** A moment marked (everyui.perf.Marks), logged where the app was launched with `marks=1`, for a check to read. */
  def marked(name: String, at: Double): Unit = if (launchArg("marks") == "1") log("[mark] " + name + " " + at.toString)
  /** A check a harness reads (`everyui.access.Access.expose`): a web page's alone, so nothing here. */
  def expose(name: String, f: () => String, json: Boolean): Unit = ()

  /** The app has the screen, or it does not: its focus is coming back to the front (`onResume`). */
  def onFocus(f: () => Unit): () => Unit = onResume(f)

  /** Its focus lost is its going away (`onAway`). */
  def onBlur(f: () => Unit): () => Unit = onAway(f)

  /** Nothing says the app is closed for good here: it is sent away (`onAway`), and ended there. */
  def onClose(f: () => Unit): () => Unit = () => ()

  /** One screen, one app: a second window is not opened. */
  def openWindow(path: String): Boolean = false

  /** Whether a rich document is edited in place here (everyui.rich.RichTextEditor): not yet, its Markdown instead. */
  def richEditing(): Boolean = false

  /** Whether a page declares the theme's colour scheme: there is no page here, so nothing is declared. */
  def declareColorScheme(on: Boolean): Unit = ()

  /** The app cannot start itself again into a new build here. */
  def reload(): Boolean = false

  /** Which build is running: the version its bundle says, where it says one. */
  def buildId(): String = appVersionName()

  private val onlineNow = reactify.Var[Boolean](true)

  /** Whether the device has a network: always here, where nothing follows it; a socket finds out by trying. */
  def online: reactify.Val[Boolean] = onlineNow

  /** Nothing runs beside the interface here yet: the work is done at once. */
  def offThread(f: () => Unit): Unit = f()

  def later(f: () => Unit): Unit = f()
  /** `f` once what is running now is done: `later` here. */
  def afterTurn(f: () => Unit): Unit = later(f)
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
  // nowhere safer than the settings to keep a secret here
  def secret(name: String): String = setting(name)
  def saveSecret(name: String, value: String): Unit = saveSetting(name, value)

  def saveSetting(name: String, value: String): Unit = ()

  /** The device's own store of documents (see [[everyui.store.Store]]). Files under the app's Caches directory: a
    * television app has no other place of its own to keep them, and the system may empty it whenever it wants the
    * room, so a read can always come back empty. The work is done before the callback, on the thread that draws; one
    * small document at a time is what this is for. */
  def storeGet(space: String, key: String, got: Option[String] => Unit): Unit = {
    val text = uiStoreGet(space, key)
    got(if (text.isEmpty) None else Some(text))
  }
  def storePut(space: String, key: String, value: String, done: () => Unit): Unit = {
    uiStorePut(space, key, value)
    done()
  }
  def storeDelete(space: String, key: String, done: () => Unit): Unit = {
    uiStoreDelete(space, key)
    done()
  }
  def storeKeys(space: String, got: List[String] => Unit): Unit = got(uiStoreKeys(space))
  def storeClear(space: String, done: () => Unit): Unit = {
    uiStoreClear(space)
    done()
  }

  /** What it is, as its maker names it; the same as its name where the platform knows no more than that. */
  def deviceModel(): String = deviceName()

  def launchesApps(): Boolean = true
  // nothing here says which codecs it has, so none is ruled out: a wrong no costs a transcode nobody needed
  /** The hardware facts a performance report is read with: none this platform will say. */
  def deviceProfile(): List[(String, String)] = Nil

  /** What the device says of itself at launch (`everyui.perf.Perf.device`): its model. */
  def deviceHint(): everyui.perf.DeviceHint = {
    val model = deviceModel()
    everyui.perf.DeviceHint(model = if (model.isEmpty) None else Some(model))
  }
  /** Nothing is read of its memory here. */
  def memoryLevel(): String = ""
  def memoryUsedPct(): Int = -1

  /** How often the display is drawn, in Hz, for where a frame counts as dropped (`everyui.perf.QualityPolicy`): not
    * read here, 0. */
  def refreshRate(): Double = 0.0
  /** Errors nothing caught: one ends the app here, and no run reports it. */
  def errorsSeen(): Int = 0
  /** What nothing caught: not heard here, where one ends the app. */
  def onUncaught(f: (String, String) => Unit): Unit = ()
  /** A throwable's stack: none to be had here. */
  def stackOf(t: Throwable): String = ""
  /** Turns cut off by the platform (a Roku's): none here. */
  def turnsCutOff(): Int = 0

  /** What the app is showing, as a PNG in base64: nothing here can take one. */
  def screenshot(): String = ""

  def decodes(codec: String): Boolean = true
  // an app here is updated by whatever put it here, never by itself
  def canInstallApps(): Boolean = false
  def openInstallSettings(): Unit = ()
  def installApp(url: String, progress: Double => Unit, done: Option[String] => Unit): Unit =
    done(Some("Updates install only on Android"))
  /** Something large was let go: nothing to do where the runtime collects on its own. */
  def collect(): Unit = ()
  /** Whether `blur` is drawn: not here, where a picture is drawn sharp. An app that wants a blurred picture draws a tiny
    * copy stretched over its box, which the scaling blurs. */
  def blurs(): Boolean = false
  /** Whether text can hold emoji: the system draws them. */
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

  def deviceName(): String = "Apple TV"

  /** No text fields of its own. */
  def nativeInput(): Boolean = false
  /** New words from the platform's keyboard: a text field in a dialog over the app, headed `title`, holding `value`,
    * hiding what is typed when `secure`; its Done hands the words to `done`, and Cancel hands nothing. */
  def editText(title: String, value: String, secure: Boolean, done: String => Unit): Unit =
    editField(title, value, secure, "", done)
  /** `editText` for a field asking for `autofill` (`Autofill.token`): the dialog's field says so (its
    * `textContentType`), so a sign-in offers AutoFill from the Keychain and an iPhone nearby can fill it (the
    * Continuity keyboard). */
  def editField(title: String, value: String, secure: Boolean, autofill: String, done: String => Unit): Unit =
    uiEditText(title, value, secure, autofill, done)

  /** Faces are files the runtime opens by name. */
  def registerFont(face: Face): Unit = ()
  /** A colour emoji face for where the system has none (`Faces.colorEmoji`): the system draws its own here, so none
    * is taken. */
  def registerColorEmoji(face: Face): Boolean = false

  /** The screen in the app's units, over the size the app is drawn at (everyui.theme.UiScale). */
  def screenWidth(): Double = uiScreenWidth() / everyui.theme.UiScale()
  def screenHeight(): Double = uiScreenHeight() / everyui.theme.UiScale()
  /** Screen pixels to a layout unit: a point is taken as a pixel, the screen's own scale not yet being asked for, times
    * the size the app is drawn at. */
  def pixelRatio(): Double = everyui.theme.UiScale()
  /** The app drawn `factor` times its size (everyui.theme.UiScale): the root view's layers scaled from its top left. */
  def setUiScale(factor: Double): Unit = uiSetRootScale(factor)

  /** `--freeze=<seconds>` on the launch (tools/tvos-run.sh --freeze): step the animation that far on a virtual
    * clock and stop. Negative when absent. */
  def freezeSeconds(): Double = uiFreezeSeconds()

  /** The frozen frame is complete. (tools/tvos-run.sh reads it off the app's console.) */
  def frozen(): Unit = ()

  /** No other apps to know of here. */
  def installedApps(ids: List[String]): List[String] = Nil

  /** None yet. tvOS can ask, but only with the com.apple.developer.networking.multicast entitlement (granted by
    * Apple on request) and an NSLocalNetworkUsageDescription, and the system asks the viewer for the local network
    * the first time. */
  def findMediaServers(seconds: Double, found: List[everyui.MediaServer] => Unit): Unit = later(() => found(Nil))

  /** `--name=value` on the launch, or "". */
  def launchArg(name: String): String = uiLaunchArg(name)

  /** Each launch that reaches the app once it is running, with what it was launched with (a Roku's resume or deep
    * link, an Android intent). Nothing launches a running app again here, so it is never called. */
  def onLaunch(f: Map[String, String] => Unit): () => Unit = {
    launchers = launchers :+ f
    () => launchers = launchers.filterNot(_ eq f)
  }

  def launched(params: Map[String, String]): Unit = launchers.foreach(f => f(params))

  private var launchers: List[Map[String, String] => Unit] = Nil

  /** `millis` since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
  def localDateTime(millis: Long): String = uiLocalDateTime(millis.toDouble)

  /** No page to be served from: an app names its server. */
  def webSocketOrigin(): String = ""
  /** Where this app's server is, to resolve its relative URLs against: nothing here comes from a server, so what the app
    * set as the setting "everyui.origin" ("https://example.com"), else "". */
  def origin(): String = setting("everyui.origin")

  /** Exit the process (a demo that is done). */
  def quit(): Unit = uiQuit()

  /** An Apple TV's screen never changes size. */
  /** Every key of the remote, by the web's name (OK is "Enter"), pressed or released. */
  def onKey(f: (String, Boolean) => Boolean): Unit = uiOnKey((name: String, pressed: Boolean) => {
    // a press timed to the frame that answers it, where it is measured
    if (pressed && everyui.perf.Perf.on) everyui.perf.Perf.keyed()
    f(name, pressed)
  })
  /** A remote holds no modifiers. */
  def modifiers(): everyui.event.Modifiers = everyui.event.Modifiers(false, false, false, false)
  /** Whether the key being reported is one held down repeating: a remote never says so. */
  def keyRepeat(): Boolean = false
  /** A remote composes no words. */
  def keyComposing(): Boolean = false
  /** The button of the pointer event being reported: none here, so always the primary (0). */
  def pointerButton(): Int = 0
  /** No finger touches a television: the remote's touch surface moves the focus. */
  def pointerFinger(): Boolean = false
  /** Every key is the focus layer's: nothing here handles its own. */
  def keyEmbed(): String = ""
  /** No shortcuts are made on a remote. */
  def commandKey(): Boolean = false

  def onResize(f: () => Unit): Unit = ()

  // Through the runtime, as the Roku backend has them.
  /** A number from 0 up to 1, at random. */
  def random(): Double = uiRandom()
  def sqrt(x: Double): Double = uiSqrt(x)
  def pow(x: Double, y: Double): Double = uiPow(x, y)
  def sin(x: Double): Double = uiSin(x)
  def cos(x: Double): Double = uiCos(x)
}
