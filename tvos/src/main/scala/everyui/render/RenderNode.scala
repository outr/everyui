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
  // Whether it is a label (Nodes.text and its kin), and the words it was last given: how many lines they were written
  // with is how tall it is where it does not wrap.
  private[render] var text: Boolean = false
  private var words: String = ""
  // The size the app gave it, -1 for none; whether its words wrap at a width (setWhiteSpace), and where words with no
  // width of their own wrap, kept to their box (setTextLimit), 0 for nowhere.
  private var explicitWidth: Double = -1.0
  private var explicitHeight: Double = -1.0
  private var wrapping: Boolean = false
  private var wrapLimit: Double = 0.0
  // where its lines sit (setTextAlign) and which way its words run (setTextDirection)
  private var textAlign: String = "start"
  private var textDirection: String = ""

  def getText(): String = uiGetText(sg)
  def setText(text: String): Unit = {
    words = text
    uiSetText(sg, text)
  }

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

  /** A label is as wide as the app made it; else as its words, whole, or as the width they wrap at where they pass it
    * (`setTextLimit`). */
  def getWidth(): Double =
    if (!text) wholeWidth()
    else if (explicitWidth >= 0.0) explicitWidth
    else {
      val whole = wholeWidth()
      if (wrapping && wrapLimit > 0.0 && whole > wrapLimit) wrapLimit else whole
    }
  private def wholeWidth(): Double = if (fontSize > 0.0) uiTextWidth(sg, fontUri, fontSize) else uiGetWidth(sg)
  /** How wide the words are whole, on their lines, whatever width the view is given: as they are measured. */
  def textWidth(): Double = wholeWidth()
  /** Its width and height, read together (the web reads a node out of the page once for both). */
  def getSize(): (Double, Double) = (getWidth(), getHeight())
  /** A label is as tall as the app made it; wrapping at a width (its own, or the limit it is held to), as tall as UIKit
    * lays its lines out there; else one shared line (everyui.font.Face) for each line its words were written with. */
  def getHeight(): Double =
    if (text && explicitHeight >= 0.0) explicitHeight
    else if (text && wrapping && (explicitWidth > 0.0 || wrapLimit > 0.0))
      uiTextHeight(sg, if (explicitWidth > 0.0) explicitWidth else wrapLimit)
    else if (lineHeight > 0.0) lineHeight * (if (text) RenderNode.lineCount(words) else 1).toDouble
    else uiGetHeight(sg)

  def append(child: RenderNode): Unit = uiAppend(sg, child.sg)
  // no ordered insert on this target yet: appended, which is where most children go
  def insert(child: RenderNode, index: Int): Unit = uiAppend(sg, child.sg)

  /** Detach from the parent, if attached. */
  def remove(): Unit = uiRemove(sg)

  // ---- media: VLCKit's player behind the view (uiMedia, swift/runtime/MediaRuntime.swift) where the app is built
  // with it; a node that is no player, or an app built without VLCKit, plays nothing and says nothing ----
  /** The headers it is opened with: VLC sends a User-Agent, a Referer and a Cookie, and says once of any other that
    * it cannot. */
  def setMediaHeaders(headers: Map[String, String]): Unit =
    uiMediaHeaders(sg, headers.toList.map(h => h._1 + ": " + h._2).mkString("\n"))
  def setMediaSource(url: String): Unit = uiMediaSource(sg, url)
  def setMediaStart(seconds: Double): Unit = uiMediaStart(sg, seconds)
  /** Offered beside the file's own tracks, as VLC's subtitle slaves, with the label and language each was given. */
  def setMediaSidecars(sidecars: List[everyui.component.MediaSidecar]): Unit =
    uiMediaSidecars(sg, sidecars.map(_.url).mkString("\n"), sidecars.map(_.label).mkString("\n"),
      sidecars.map(_.language).mkString("\n"))
  def setMediaPlaying(playing: Boolean): Unit = uiMediaPlaying(sg, playing)
  def setMediaLoop(loop: Boolean): Unit = uiMediaLoop(sg, loop)
  def setMediaMuted(muted: Boolean): Unit = uiMediaMuted(sg, muted)
  /** 0 to 1, and past it VLC's software mixer made louder, up to 4 (unlimited: it can clip). */
  def setMediaVolume(volume: Double): Unit = uiMediaVolume(sg, volume)
  def setMediaRate(rate: Double): Unit = uiMediaRate(sg, rate)
  def mediaRates(): Boolean = uiMediaPlays()
  /** Whether the media has enough in hand to play to its end without stopping (`Backdrop`): VLC fetches as it plays. */
  def mediaPlaysThrough(): Boolean = true
  // VLC folds surround to stereo itself, with no level for the centre: nothing here reaches it
  def setMediaCenterMix(db: Double): Unit = ()
  def mediaCenterMixes(): Boolean = false
  def mediaCenterMixing(): Boolean = false
  /** Clearer voices on VLC's equaliser, heard at once; night mode on its compressor, which is in the chain from the
    * opening, so a change of it opens the source again where it had reached. */
  def setMediaAids(clarity: Double, night: Double): Unit = uiMediaAids(sg, clarity, night)
  def mediaAidsSupported(): Boolean = uiMediaPlays()
  def mediaAidsHeard(): Boolean = uiMediaAidsHeard(sg)
  /** What VLC holds from the network before it plays. */
  def setMediaBuffering(named: String): Unit = uiMediaCaching(sg, named)
  // nowhere of its own to keep a clip: every one is fetched as it plays
  def setMediaCached(cached: Boolean): Unit = ()
  /** Waiting for something to play: opening, or buffering with the playhead standing still. */
  def mediaBuffering(): Boolean = uiMediaWaiting(sg)
  /** What VLC's input has been reading, in bits a second. */
  def mediaBandwidth(): Long = uiMediaBandwidth(sg)
  def mediaSize(): (Double, Double) = (uiMediaSize(sg, false), uiMediaSize(sg, true))
  /** The words on screen: "" here, where VLC draws its own subtitles (SRT and SubStation, styled, through libass). */
  def mediaCues(): String = ""
  def mediaCueList(): List[everyui.component.MediaCue] = Nil
  // VLC retries on its own and says nothing until it gives up
  def mediaRetry(): Option[everyui.component.MediaRetry] = None
  /** The system's now-playing (MPNowPlayingInfoCenter: the Apple TV's, Control Center, a phone's lock screen), its
    * transport heard as the keys of the same names (MediaPlay, MediaPause, MediaTrackNext...), and the sound taken over
    * from other apps while it plays. */
  def setNowPlaying(about: everyui.component.NowPlaying): Unit =
    uiMediaNowPlaying(sg, about.title, about.artist, about.album, about.art, about.seconds)
  // VLCKit lends no tap on the sound it plays
  def setMediaSpectrum(on: Boolean): Unit = ()
  def mediaBands(): List[Double] = Nil

  // ---- a page of the web: a WKWebView on iOS (uiWeb), which keeps its state; nothing on tvOS (no WebKit) ----
  /** Show this address: a path the bundle holds is that file, a site-absolute one it does not the app's server's. */
  def setWebSource(url: String): Unit = uiWebLoad(sg, url, Platform.origin())
  /** Who the page is told is asking, for the loads after this one; empty is the engine's own. */
  def setWebAgent(agent: String): Unit = uiWebAgent(sg, agent)
  /** The page sandboxed with `flags` allowed, as near as WebKit comes to it after the view is made. */
  def setWebSandbox(flags: Option[Set[String]]): Unit =
    uiWebSandbox(sg, flags.nonEmpty, flags.map(f => f.mkString(",")).getOrElse(""))
  /** The view drawn clear rather than black, so a transparent page shows what is behind it. */
  def setWebClear(clear: Boolean): Unit = uiWebClear(sg, clear)
  /** Say something to the page, in its own language: nothing where it is sandboxed. */
  def runWebScript(script: String): Unit = uiWebRun(sg, script)
  /** Hear what the page says back through its bridge. */
  def onWebMessage(f: String => Unit): Unit = uiWebOnMessage(sg, f)
  /** A sandboxed page heard too (`on`): lent the bridge, which is all it is lent, the page loaded again with it. */
  def setWebHearSandboxed(on: Boolean): Unit = uiWebHearSandboxed(sg, on)
  /** `request` posted to the page as `{everyui: "ask", session, id, request}`; false where it cannot be asked. */
  def askWeb(id: Int, request: String): Boolean = uiWebAsk(sg, id, request)
  def onWebReply(f: (Int, String) => Unit): Unit = uiWebOnReply(sg, f)
  def onWebFrameMessage(f: Option[(String, String) => Unit]): Unit = ()
  // ---- a rich document's editing surface: none here yet (Platform.richEditing); the editor edits its Markdown ----
  def setRichDoc(doc: everyui.rich.RichDoc, selection: everyui.rich.RichRange): Unit = ()
  def setRichLook(look: everyui.rich.RichLook, placeholder: String, readOnly: Boolean): Unit = ()
  def setRichGhost(at: everyui.rich.RichPos, lines: List[String]): Unit = ()
  def onRichInput(f: everyui.rich.RichInput => Unit): Unit = ()
  def onRichHeight(f: Double => Unit): Unit = ()
  def focusRich(selection: everyui.rich.RichRange): Unit = ()
  def richSelection(): Option[everyui.rich.RichRange] = None
  /** Heard no more, its page stopped, left on about:blank and taken off the screen: the app is done with it. */
  def releaseWeb(): Unit = uiWebRelease(sg)

  // ---- an embed (everyui.component.Embed): tvOS lends no web engine (WebKit is not public there), so its fallback
  // shows ----
  def embedId(): String = ""
  def embedRuntime(): String = ""
  def embedSend(message: String): Unit = ()
  def onEmbedMessage(f: String => Unit): Unit = ()
  def focusEmbed(on: Boolean): Unit = ()
  def onEmbedFocus(f: Boolean => Unit): Unit = ()
  def releaseEmbed(): Unit = ()

  /** What to hear and read this in: the sound's language said with each opening, and the text chosen from its tracks
    * once it is playing, where it has one in that language. */
  def setMediaLanguages(audio: String, text: String): Unit = {
    uiMediaHear(sg, audio)
    wantText = text.trim
  }
  private var wantText = ""

  def mediaTracks(): List[everyui.component.MediaTrack] = List.tabulate(uiMediaTrackCount(sg)) { i =>
    everyui.component.MediaTrack(uiMediaTrack(sg, i, 0), uiMediaTrack(sg, i, 1), uiMediaTrack(sg, i, 2),
      uiMediaTrack(sg, i, 3), uiMediaTrack(sg, i, 4))
  }
  // the text language asked for, from the tracks it turned out to have; without one found there, the captions as the
  // app chose them, else as the device has them
  private def chooseText(): Unit = {
    val tracks = mediaTracks()
    val tag = everyui.component.MediaTrack.languageTag(wantText)
    val asked =
      if (wantText.isEmpty || textChosen) None
      else tracks.find(t => t.kind == "text" && everyui.component.MediaTrack.languageTag(t.language) == tag)
    asked match {
      case Some(t) => uiMediaSelect(sg, "text", t.id)
      case None => showCaptions(tracks)
    }
  }
  /** Captions by the rule of `everyui.text.Captions`: the text track the app chose, or none; until it chooses, the
    * device's (Settings, Accessibility, Subtitles and Captioning) with its captions on, the track in its caption
    * language, else the first. A change of the setting by the viewer is theirs: the app's choice goes, and the
    * device's is followed. */
  private def showCaptions(tracks: List[everyui.component.MediaTrack]): Unit = {
    if (captionWatch.isEmpty) captionWatch = Some(everyui.text.Captions.listen(_ => {
      textChosen = false
      textChoice = ""
      showCaptions(mediaTracks())
    }))
    val choice =
      if (textChosen) everyui.component.TextChoice.fromTrack(Some(textChoice))
      else everyui.component.TextChoice.TextFromDevice
    uiMediaSelect(sg, "text", everyui.text.Captions.choose(choice, CaptionAccess.device(), tracks).getOrElse(""))
  }
  private var textChosen = false
  private var textChoice = ""
  private var captionWatch: Option[() => Unit] = None
  /** What VLC decoded: the sound's and the picture's formats, the sound tracks, and the last error. */
  def mediaDiagnostics(): everyui.component.MediaDiagnostics = everyui.component.MediaDiagnostics(
    uiMediaDiagnostics(sg, "audio"), uiMediaDiagnostics(sg, "video"), uiMediaDiagnostics(sg, "audioTracks"),
    uiMediaDiagnostics(sg, "failure"))
  /** A track to hear or read, by its id ("" reads none). The text track chosen is the app's choice from then on, put
    * to each source as it opens (showCaptions). */
  def selectMediaTrack(kind: String, id: String): Unit =
    if (kind == "text") {
      textChosen = true
      textChoice = id
      showCaptions(mediaTracks())
    }
    else if (id.nonEmpty) uiMediaSelect(sg, kind, id)
  def setMediaText(choice: everyui.component.TextChoice): Unit = choice match {
    case everyui.component.TextChoice.TextFromDevice =>
      textChosen = false
      textChoice = ""
      showCaptions(mediaTracks())
    case other => selectMediaTrack("text", everyui.component.TextChoice.asTrack(other).getOrElse(""))
  }
  def setMediaCover(cover: Boolean): Unit = uiMediaCover(sg, cover)
  // VLC draws in the app's own layers, the one way it has
  def setMediaDirect(direct: Boolean): Unit = ()
  // an effect (AudioPlayer.effect) is a player of its own, which VLC mixes with every other
  def setMediaEffect(effect: Boolean): Unit = ()
  def getMediaPosition(): Double = uiMediaPosition(sg)
  def getMediaDuration(): Double = uiMediaDuration(sg)
  def seekMedia(seconds: Double): Unit = uiMediaSeek(sg, seconds)
  def onMediaEvent(f: String => Unit): Unit = uiMediaOnEvent(sg, e => {
    if (e == "ready") chooseText()
    f(e)
  })
  def releaseMedia(): Unit = {
    uiMediaRelease(sg)
    captionWatch.foreach(stop => stop())
    captionWatch = None
  }

  def setWidth(width: Double): Unit = {
    explicitWidth = width
    uiSetWidth(sg, width)
  }
  def setHeight(height: Double): Unit = {
    explicitHeight = height
    uiSetHeight(sg, height)
  }

  /** `"#rrggbb"`, or `""` for transparent. */
  def setBackgroundColor(hex: String): Unit = uiSetBackgroundColor(sg, hex)
  def setRadius(radius: Double): Unit = uiSetRadius(sg, radius)

  /** Every view is placed by its own position, so there is no position type to set. */
  /** Whether the pointer is over it as what is under a still one moves: the platform's enter and leave alone here. */
  def onOver(told: Boolean => Unit): Unit = ()
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click"). */
  def onPointer(kind: PointerKind, f: (Double, Double) => Unit): Unit = uiOnPointer(sg, kind.toString, f)

  /** The shared ScrollContainer shifts the content; the node only clips, and takes a finger's drag along it from what
    * the finger went down on (uiTouch), as Android's does. */
  def setScrollable(vertical: Boolean, horizontal: Boolean): Unit = uiSetScrollable(sg, vertical, horizontal)
  def setScrollLeft(x: Double): Unit = ()
  /** The shared ScrollContainer moves the content itself, so the node never scrolls on its own. */
  def onScrolled(f: () => Unit): Unit = ()
  /** A scroll that runs from its left in every language: the shared ScrollContainer counts it so here. */
  def setLeftToRight(): Unit = ()
  /** The shared ScrollContainer draws no scrollbar here. */
  def setScrollbarColor(hex: String): Unit = ()
  /** No scrollbar is drawn here, and no fade: what is out of the viewport is cut at its edge. */
  def setScrollbarShown(shown: Boolean): Unit = ()
  def setEdgeFade(horizontal: Boolean, start: Double, end: Double): Unit = ()
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
  /** No guess at the words' width here: they are measured. */
  def textWidthGuess(): Double = -1.0
  def setDescription(text: String): Unit = ()
  def setInvalid(on: Boolean): Unit = ()
  def setUpdating(on: Boolean): Unit = ()

  def setPositionType(name: String): Unit = ()

  /** `"normal"`, `"pre-wrap"` and `"pre-line"` wrap a label at its width (its own, or the limit it is held to);
    * anything else keeps the lines its words were written with. */
  def setWhiteSpace(value: String): Unit = if (text) {
    wrapping = value == "normal" || value == "pre-wrap" || value == "pre-line"
    uiSetWraps(sg, wrapping)
    if (!wrapping && wrapLimit > 0.0) {
      wrapLimit = 0.0
      if (explicitWidth < 0.0) uiSetWidth(sg, 0.0)
    }
  }

  def setImageSource(uri: String): Unit = uiSetImageSource(sg, uri)
  /** The picture as it is: no tint here yet. */
  def setTint(hex: String): Unit = ()

  /** What drags itself under a finger keeps the drag from a scroll it is in (uiTouch). */
  def setTouchDrags(on: Boolean): Unit = uiSetTouchDrags(sg, on)
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
  /** Each line of wrapped text `px` below the last: the gap the face leaves, opened or closed to that. */
  def setLineHeight(px: Double): Unit = uiSetLineHeight(sg, px)
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
  /** A shadow behind the letters, `blur` its spread, or none for `""`: the label's layer's, which follows its
    * glyphs. */
  def setTextShadow(x: Double, y: Double, blur: Double, hex: String): Unit = uiSetTextShadow(sg, x, y, blur, hex)
  /** An edge round the letters, `width` each side, or none for "" (the runtime's OutlinedLabel). */
  def setTextOutline(width: Double, hex: String): Unit = uiSetTextOutline(sg, width, hex)
  // no reader of roles or descriptions reaches this platform's nodes
  def setSemantics(role: String, level: Int, label: String): Unit = ()
  def setSemanticState(range: Option[everyui.component.types.SemanticRange], checked: Option[Boolean],
                       expanded: Option[Boolean], selected: Option[Boolean], sort: String,
                       current: Boolean, description: String): Unit = ()
  /** The focus landed here: where a screen reader is on, it reads this out, and pressing there is `click`. */
  def accessibleFocus(click: () => Unit, name: () => String): Unit = ()
  /** The row under a list's cursor (`VirtualList`): no screen reader reaches this platform's nodes. */
  def setActiveRow(row: RenderNode, name: () => String): Unit = ()
  /** A field's list of completions: a remote has no typing to complete, and no screen reader reaches these nodes. */
  def setCompletionList(on: Boolean, list: Option[RenderNode], active: Option[RenderNode],
                        name: () => String): Unit = ()
  /** A press keeps a field's focus: nothing here takes it. */
  def holdFocusOnPress(): Unit = ()
  /** A ring round what the focus layer focuses: a remote's app draws its own focus. */
  def setFocusRing(hex: String, radius: Double): Unit = ()
  def setAlt(text: String): Unit = ()

  /** "start", "center" or "end", by the reading direction: the app's (`Platform.setDirection`), or the words' own. */
  def setTextAlign(align: String): Unit = if (align != textAlign) {
    textAlign = align
    uiSetTextAlign(sg, textAlign, textDirection)
  }
  /** Which way the words run: "" the app's, "auto" their own (the first letter with a direction, falling back to the
    * app's), "ltr" or "rtl". The side their lines start from follows. */
  def setTextDirection(value: String): Unit = if (value != textDirection) {
    textDirection = value
    uiSetTextAlign(sg, textAlign, textDirection)
  }
  /** A label breaks a word too long for its line where it must already. */
  def setWrapAnywhere(on: Boolean): Unit = ()
  /** Words that wrap are measured whole on one line here already, and wrap only at a width or a limit. */
  def setTextWhole(on: Boolean): Unit = ()
  /** Words cut short to fit (`TextView.maxWidth`, `maxLines`) are cut by measuring alone; words that wrap and are
    * given no width of their own wrap at `width` where they would run past it (kept to their box), the label as
    * wide. */
  def setTextLimit(width: Double, lines: Int): Unit = if (text && wrapping && explicitWidth < 0.0) {
    wrapLimit = 0.0
    if (width > 0.0 && wholeWidth() > width) wrapLimit = width
    uiSetWidth(sg, wrapLimit)
  }
  /** Whether words held to a limit (`setTextLimit`) wrap at it here: they do. */
  def wrapsToLimit(): Boolean = true
  /** Words measure as they will be drawn from the moment they are set. */
  def textMeasurable(ready: () => Unit): Boolean = true

  /** A field here is a line of text (TextInput asks the keyboard for its value), so its contents are its text. */
  def getValue(): String = uiGetText(sg)
  def setValue(value: String): Unit = setText(value)
  def setPlaceholder(text: String): Unit = ()
  def setInputAction(action: String): Unit = ()
  // what a field asks for (`Autofill.token`): nothing here fills fields
  def setAutofill(token: String, section: String, name: String, passkey: Boolean, literal: Boolean,
    numeric: Boolean): Unit = ()
  // a form (`everyui.form.Form`) is a container here: nothing sends it but the app, and nothing offers to save it
  def onSubmit(f: () => Unit): Unit = ()
  def formDone(saved: Boolean): Unit = ()
  def setHiddenUsername(username: String): Unit = ()
  def onValue(f: String => Unit): Unit = ()
  // no pasting here: a field is written on the platform's keyboard
  def onPaste(f: String => everyui.component.types.PasteAction): Unit = ()
  def onPasteFiles(wanted: () => Boolean, f: List[everyui.file.PickedFile] => Unit): Unit = ()
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
  /** Nothing reads the nodes here. */
  def setReaderHidden(hidden: Boolean): Unit = ()
  /** A field here is only its words, shown, and new ones are asked for by the app (`Platform.editField`), which a
    * read-only field does not do: there is nothing to stop. */
  def setReadOnly(on: Boolean): Unit = ()
  def focusInput(on: Boolean): Unit = ()
}

object RenderNode {
  /** How many lines `text` was written with: one, and one more for each line break. */
  def lineCount(text: String): Int = {
    var lines = 1
    var i = 0
    while (i < text.length) {
      if (text.charAt(i) == '\n') lines += 1
      i += 1
    }
    lines
  }
}

/** Backend node factories. */
object Nodes {
  /** A rich document's editing surface (everyui.rich.RichTextEditor): none here yet (`Platform.richEditing`). */
  def richEditor(): RenderNode = container()

  /** A form (`everyui.form.Form`): a container here. */
  def form(kind: String): RenderNode = container()

  /** A page of the web: a WKWebView on iOS, its page lent `<bridge>.postMessage("...")`; an empty view on tvOS,
    * which has no WebKit. */
  def web(bridge: String): RenderNode = new RenderNode(uiWeb(bridge))

  /** An embed's box (everyui.component.Embed): a plain view, which holds its fallback. */
  def embed(name: String, site: everyui.component.EmbedSite): RenderNode = container()

  /** The root view controller's render root; and the drive's answers (everyui.drive.Drive) lent the runtime, where the
    * build takes a test's input (tools/apple-drive.sh). */
  def root(): RenderNode = {
    if (uiDriveOn()) uiOnDrive((line: String) => everyui.drive.Drive.answer(line))
    new RenderNode(uiRoot())
  }
  /** A paragraph of styled runs (`everyui.component.RichText`): a text node here, which draws them. */
  def richText(): RenderNode = text()

  /** A text node — a `UILabel`. */
  def text(): RenderNode = label(uiText())
  /** A container — a `UIView` other nodes are appended to. */
  def container(): RenderNode = new RenderNode(uiContainer())
  /** A drawing (everyui.draw.Drawing): a container its rectangles go in, since nothing here draws paths. */
  def drawing(): RenderNode = container()
  /** No text field: a line of text, which TextInput fills from the keyboard. */
  def input(secure: Boolean): RenderNode = label(uiText())
  /** A box of lines is a field like any other. */
  def textArea(): RenderNode = label(uiText())
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
    val node = label(uiText())
    node.setFont("Material Icons", 400, size)
    node.setText(glyph)
    node
  }
  /** A video: VLCKit's player drawing into the view (swift/runtime/MediaRuntime.swift); an empty view in an app built
    * without VLCKit. */
  def video(): RenderNode = new RenderNode(uiMedia(true))
  /** Sound: VLCKit's player with nothing to draw, a view taking no room. */
  def audio(): RenderNode = new RenderNode(uiMedia(false))

  /** An image node — a `UIImageView` loading from the app bundle. */
  def image(uri: String, width: Double, height: Double): RenderNode =
    new RenderNode(uiImage(uri, width, height))

  // a label's node, which knows it is one
  private def label(sg: SGNode): RenderNode = {
    val node = new RenderNode(sg)
    node.text = true
    node
  }
}

/** The handful of non-render platform services the slice needs. */
object Platform {
  /** The sizes of `nodes` (`MeasuredFeature`'s batch), each read in turn. */
  def sizesOf(nodes: List[RenderNode]): List[(Double, Double)] = nodes.map(_.getSize())

  /** Whether this device can go on playing in a corner of whatever the viewer does next, and doing it. */
  /** Whether this platform has a browser engine to lend a page (Nodes.web): WebKit on iOS, none on tvOS. */
  def webViews(): Boolean = uiWebViews()
  /** One web view made ready, its page process started, for the next page to take (WebView.prewarm): on iOS. */
  def prewarmWeb(): Unit = uiWebPrewarm()
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

  /** Decode `uri` and report its natural size, 0 x 0 where it cannot be had or decoded (logged once). UIImage reads no
    * SVG, so one named as an SVG (`SvgImage.named`) is never asked for: it is logged once and comes as 0 x 0, and an
    * Avatar keeps its initial. */
  def loadImage(uri: String, loaded: (Double, Double) => Unit): Unit =
    if (everyui.image.SvgImage.named(uri)) {
      everyui.image.SvgImage.undrawn(uri, "an Apple TV")
      loaded(0.0, 0.0)
    } else uiLoadImage(uri, (w: Double, h: Double) => {
      if (w <= 0.0 || h <= 0.0) everyui.image.SvgImage.undecoded(uri)
      loaded(w, h)
    })
  /** A picture's cells, its average colour in each (`everyui.access.Access`): none read here, so words over a picture
    * are judged as over the worst it could be. */
  def pictureCells(uri: String, across: Int, down: Int, read: List[Int] => Unit): Unit = read(Nil)

  /** Bundled faces are registered at launch. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit = loaded()

  /** Fonts are registered at launch, so this runs `f` now. */
  def whenFontsReady(f: () => Unit): Unit = f()

  /** A bundle-relative image path. */
  def imagePath(name: String): String = "/images/" + name

  /** What the app runs on, by UIKit's idiom ("tv", "phone", "pad", "mac"): one emitted app serves an Apple TV, an
    * iPhone and an iPad, so it is asked as the app runs, once. */
  def idiom(): String = {
    if (idiomRead.isEmpty) idiomRead = uiIdiom()
    idiomRead
  }
  private var idiomRead: String = ""
  /** Whether this is an Apple TV, driven by its remote. */
  def tv(): Boolean = idiom() == "tv"

  /** An Apple TV is TvOS; an iPhone, an iPad (and an iPad app on a Mac) are IOS. */
  def kind: PlatformKind = if (tv()) PlatformKind.TvOS else PlatformKind.IOS
  /** An Apple TV has a remote and nothing else. An iPhone or an iPad is touched, and focuses as Android's phones and
    * tablets do (the pointer-focus ruling: nothing shown focused until keys move the focus); an iPad with a trackpad or
    * a mouse has a pointer too, and one with a keyboard keys. */
  def pointerSupport(): Boolean = !tv() && uiHasPointer()
  def touchSupport(): Boolean = !tv()
  def remoteNavigation(): Boolean = tv()
  def keyboardSupport(): Boolean = !tv() && uiHasKeyboard()

  /** The shared ScrollContainer scrolls. */
  def nativeScroll(): Boolean = false
  /** The browser (or the app that owns the address) at `url`, by the system (UIApplication.open). An Apple TV has no
    * browser, so a web address opens nothing there, and an app's own scheme only where that app is installed: what
    * nothing opens is logged and nothing happens, as a link that cannot open does nothing elsewhere. */
  def openUrl(url: String): Unit = uiOpenUrl(url)
  /** An app has no page to leave: the system opens `url`. */
  def navigate(url: String): Unit = openUrl(url)
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
  /** The text size the person chose on an iPhone or an iPad (Larger Text, Dynamic Type), as a scale of the default:
    * body text's size at their size over its size at Large, as Android's font scale. 1 on an Apple TV. */
  def systemTextScale(): Double = if (tv()) 1.0 else uiTextScale()
  /** The text size as the person changes it, told at once (the system says so as it changes). */
  def watchTextScale(f: Double => Unit): Unit = if (!tv()) uiOnTextScale(f)
  /** No setting for more time to read or act is read here: 1. */
  def timeScale(): Double = 1.0
  /** The system's light or dark on an iPhone or an iPad, as it changes; a television's apps are dark, and the Apple
    * TV's appearance is not read. */
  def systemColorScheme(): String = if (tv()) "dark" else uiColorScheme()
  def watchColorScheme(f: String => Unit): Unit = if (!tv()) uiOnColorScheme(f)
  /** `words` read out by VoiceOver when it next can, where it is on (a new message in a chat). */
  def announce(words: String): Unit = uiAnnounce(words)
  /** At once: VoiceOver has no other way to say it than `announce`. */
  def alert(words: String): Unit = announce(words)
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
  /** Whether copyText reaches a clipboard: the pasteboard on an iPhone or an iPad; an Apple TV has none to paste from,
    * so a caller offers something else there. */
  def clipboard(): Boolean = !tv()
  /** Whether people select text with a pointer (`Component.selectable`): not here. */
  def selectsText(): Boolean = false
  def selectedText(): String = ""
  /** The words selected, as HTML and as text (`Clipboard.selection`): none here. */
  def selectedRich(): Option[(String, String)] = None
  /** `text` on the pasteboard, in place of what was there: nothing on an Apple TV. */
  def copyText(text: String): Unit = if (!tv()) uiClipboardSet(text, "")
  /** `html` and `text` on the pasteboard together: an app that takes styled words pastes the HTML, any other the
    * text. */
  def copyRich(html: String, text: String): Unit = if (!tv()) uiClipboardSet(text, html)
  /** The screen kept on (while something is shown that is meant to be watched without a touch), or let dim, lock or
    * go to the screen saver again: the system's idle timer off, or on. */
  def keepAwake(on: Boolean): Unit = uiKeepAwake(on)
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

  /** The screen's safe area, in the app's units: on an iPhone or an iPad what the status bar, the Dynamic Island or the
    * camera's cutout, the home indicator and the rounded corners take from each edge, which the app is already laid out
    * inside (the root is placed there, as Android's is), following a rotation and an iPad's split view. None on an Apple
    * TV, whose whole screen is the app's as a Roku's is: an app keeps its own margins from a television's edges. */
  def safeArea(): everyui.layout.Insets = {
    val scale = everyui.theme.UiScale()
    everyui.layout.Insets(uiSafeInset(0) / scale, uiSafeInset(1) / scale, uiSafeInset(2) / scale, uiSafeInset(3) / scale)
  }

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
  /** `f` once `seconds` have passed, on the main queue (uiAfter). */
  def after(seconds: Double, f: () => Unit): Unit = uiAfter(seconds, f)

  /** An ARB file's messages, parsed by JSONSerialization (uiArbMessages): the Scala parser (everyui.i18n.Arb) took an
    * app's thousands of words minutes here, as it did a Roku most of a minute. */
  def parseMessages(arb: String): Map[String, String] = uiArbMessages(arb)

  /** A text file in the app's bundle (a translation, `/l10n/app_en.arb`: a leading `/` is the bundle's root, as an
    * Android app's assets and a Roku's `pkg:/` are), a `file:` URL, or one at an http(s) URL (an SVG icon), read off
    * the main thread and answered on it; "" if it is not there, which the console says. */
  def readText(path: String, done: String => Unit): Unit = uiReadTextThen(path, done)
  /** The first language the person prefers, as a BCP 47 tag ("en-US"). */
  def language(): String = uiLanguage()
  def fromCodePoint(codePoint: Int): String = uiChr(codePoint)
  /** The app's language: the platform chooses its own faces for the script, so there is nothing to do. */
  def setLanguage(code: String): Unit = ()

  /** Every label's start on the side the app reads from (uiSetDirection). */
  def setDirection(rtl: Boolean): Unit = uiSetDirection(rtl)

  /** A value kept across launches (the app's UserDefaults), or "" when there is none. */
  def setting(name: String): String = uiSettingGet(name)
  /** Keep `value` under `name`; "" forgets it. */
  def saveSetting(name: String, value: String): Unit = uiSettingPut(name, value)

  /** A value that must not be readable off the device (a session's token), kept across launches in the Keychain,
    * never moved to another device, or "" when there is none; in the settings where the Keychain refuses the app (one
    * signed with no entitlements), which is said once in the log. */
  def secret(name: String): String = uiSecretGet(name)
  /** Keep `value` under `name` in the Keychain; "" forgets it. */
  def saveSecret(name: String, value: String): Unit = uiSecretPut(name, value)

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

  /** What it is, as its maker names it: the hardware's own name ("iPhone18,1", "AppleTV14,1"), on the simulator the
    * device it stands for. */
  def deviceModel(): String = uiDeviceModel()

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
  /** How many errors nothing caught this run has seen: a handler the platform called (a key, a touch, a frame, a timer,
    * a request), a started task's failure, an Objective-C exception. */
  def errorsSeen(): Int = uiErrorsSeen()
  /** Every error the app throws from a handler the platform calls and nothing caught, as a Roku's `onError` hears it:
    * the message ("RuntimeException: what went wrong") and where it was thrown out of, the app going on. An
    * Objective-C exception is told with its stack, and the crash the last run ended on (a signal: a Swift trap, a bad
    * access) is told once at the first call, as "the app ended on signal N". */
  def onError(f: (String, String) => Unit): Unit = {
    errorHooks = errorHooks :+ f
    if (!hearing) {
      hearing = true
      uiOnError((message: String, stack: String) => errorHooks.foreach(h => h(message, stack)))
    }
  }
  /** What nothing caught, as `onError` hears it. */
  def onUncaught(f: (String, String) => Unit): Unit = onError(f)
  private var errorHooks: List[(String, String) => Unit] = Nil
  private var hearing: Boolean = false
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

  /** Where the app came from: nothing this platform can say. Which build it is: the Info.plist's, as AppleApp.version
    * wrote it from the app's release, CFBundleVersion as the build's number (Android's versionCode) and
    * CFBundleShortVersionString as the release (its versionName, "2.0.0"). */
  def installStore(): String = ""
  def appVersion(): Int = uiAppVersion()
  def appVersionName(): String = uiAppVersionName()

  /** The device's own caption settings, where it has any: not here. */
  def captionStyle(): Option[everyui.text.CaptionStyle] = None

  /** What the device calls itself, as UIDevice names it: "Apple TV", "iPhone", "iPad". */
  def deviceName(): String = uiDeviceName()

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

  /** The screen in the app's units, over the size the app is drawn at (everyui.theme.UiScale): on an iPhone or an iPad
    * the part inside the safe area, which the root is placed in. */
  def screenWidth(): Double = uiScreenWidth() / everyui.theme.UiScale()
  def screenHeight(): Double = uiScreenHeight() / everyui.theme.UiScale()
  /** Screen pixels to a layout unit: on an iPhone or an iPad the screen's scale (2 or 3 pixels a point) times the size
    * the app is drawn at; on an Apple TV a point is taken as a pixel, as before. */
  def pixelRatio(): Double = if (tv()) everyui.theme.UiScale() else uiScreenScale() * everyui.theme.UiScale()
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

  /** Every key of the remote, by the web's name (OK is "Enter"), pressed or released; on an iPhone or an iPad a
    * keyboard's, where one is attached. */
  def onKey(f: (String, Boolean) => Boolean): Unit = uiOnKey((name: String, pressed: Boolean) => {
    // a press timed to the frame that answers it, where it is measured
    if (pressed && everyui.perf.Perf.on) everyui.perf.Perf.keyed()
    f(name, pressed)
  })
  /** The modifiers held for the key or the pointer event being reported: a remote holds none, a keyboard says its own. */
  def modifiers(): everyui.event.Modifiers =
    everyui.event.Modifiers(uiModifier(0), uiModifier(1), uiModifier(2), uiModifier(3))
  /** Whether the key being reported is one held down repeating: a remote never says so. */
  def keyRepeat(): Boolean = false
  /** A remote composes no words. */
  def keyComposing(): Boolean = false
  /** Which of the platform's pointer events is being reported, the same for each component one climbs through: not
    * told here (-1), so a moment's time stands for it (`Pointer.blocks`). */
  def pointerEvent(): Long = -1L
  /** The button of the pointer event being reported: a finger's is the primary (0), a trackpad's secondary click 2. */
  def pointerButton(): Int = uiPointerButton()
  /** Whether a finger made the pointer event being reported, and not an iPad's trackpad or mouse. No finger touches a
    * television, whose remote's touch surface moves the focus. */
  def pointerFinger(): Boolean = !tv() && uiPointerFinger()
  /** Every key is the focus layer's: nothing here handles its own. */
  def keyEmbed(): String = ""
  /** No shortcuts are made on a remote. */
  def commandKey(): Boolean = false

  /** `f` as the root changes size: an iPhone or an iPad turned, an iPad's split view or Slide Over. An Apple TV's
    * screen never changes size. */
  def onResize(f: () => Unit): Unit = uiOnResize(f)

  // Through the runtime, as the Roku backend has them.
  /** A number from 0 up to 1, at random. */
  def random(): Double = uiRandom()
  def sqrt(x: Double): Double = uiSqrt(x)
  def pow(x: Double, y: Double): Double = uiPow(x, y)
  def sin(x: Double): Double = uiSin(x)
  def cos(x: Double): Double = uiCos(x)
}
