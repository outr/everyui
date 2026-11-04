package everyui.render

import everyui.font.{Face, Faces}
import everyui.PlatformKind
import everyui.audio.{PlayerStack, SoundTurns}
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
  // The clip last set, and the MaskGroup its children are in when its corners are rounded too
  private var clipWidth: Double = 0.0
  private var clipHeight: Double = 0.0
  private var masked: Option[SGNode] = None
  // How far apart its lines are asked to be, in units (`setLineHeight`), or 0 for the face's own spacing
  private var lineAdvance: Double = 0.0
  // The size last set, which the border follows; -1 until set.
  private var boxWidth: Double = -1.0
  private var boxHeight: Double = -1.0
  // the width words wrapped to fit their room are held to (`setTextLimit`), or 0: the Label wraps at it
  private var textLimit: Double = 0.0
  // A square border: four thin rectangles along the edges, since a SceneGraph Rectangle draws none of its own.
  private var edges: Vector[SGNode] = Vector.empty
  private var edgeWidth: Double = 0.0
  private var edgeHex: String = ""
  private var fillHex: String = ""
  private var cornerRadius: Double = 0.0
  // Rounded, the fill and the edge are tinted 9-patch Posters instead (see `paint`).
  private var fillShape: Option[SGNode] = None
  // Square, the fill is a Rectangle behind the children: a container is a Group, which draws nothing (uiFill)
  private var fillRect: Option[SGNode] = None
  private var edgeShape: Option[SGNode] = None
  // The picture this node shows, if it is an image: kept because a picture is set BEFORE its size (an ImageView
  // learns the size FROM the picture), so the load can be told the size as soon as the box is known.
  private var imageUri: String = ""

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
  def setX(x: Double): Unit = if (drawn) {
    left = x
    uiSetTranslation(sg, px(left), px(top))
    if (movesPosition) animate()
  }

  def getY(): Double = top
  def setY(y: Double): Unit = if (drawn) {
    top = y
    uiSetTranslation(sg, px(left), px(top))
    if (movesPosition) animate()
  }

  /** Radians, clockwise, about the node's own centre — matching youi's
    * `ImageView.rotation`, which is in TURNS (0..1); the example converts. */
  def getRotation(): Double = uiGetRotation(sg)
  def setRotation(radians: Double): Unit = if (drawn) uiSetRotation(sg, radians)

  /** Drawn larger or smaller about its own centre, the pivot the size keeps. */
  def setScale(factor: Double): Unit = if (drawn) uiSetScale(sg, factor)

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
      val face = (if (italic) Faces.italic(family, weight) else None).getOrElse(Faces(family, weight))
      fontFamily = family
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
    * wrapped, as many lines as the Label draws; anything else is SceneGraph's bounding rect. */
  /** Always: a node here is measured where it is, in the page or not (only the web's are measured in batches). */
  def onPage: Boolean = true

  def getWidth(): Double =
    if (richWidth >= 0.0) richWidth / uiScale()
    else if (fontSize > 0.0) {
      // (held to its limit, the Label wraps within it)
      val whole = uiTextWidth(sg, fontUri, fontSize)
      if (textLimit > 0.0) math.min(whole, textLimit) else whole
    }
    else math.max(if (boxWidth >= 0.0) boxWidth else 0.0, uiGetWidth(sg) / uiScale()) // (a Group is only its children)
  /** How wide the words are whole, whatever width the Label is given or held to. */
  def textWidth(): Double =
    if (richWidth >= 0.0) richWidth / uiScale()
    else if (fontSize > 0.0) uiTextWidth(sg, fontUri, fontSize)
    else 0.0
  def getHeight(): Double =
    if (wrapping && (boxWidth > 0.0 || textLimit > 0.0) && lineHeight > 0.0) {
      // the lines the Label puts its words on at its width, worked out as it wraps them (uiWrappedLines) rather than
      // read from its bounding rect, which on a Roku TV came out lines short of what it drew; each is a line of its
      // face as the Label draws one, and the spacing after it
      uiWrappedLines(sg, fontUri, px(fontSize)) * (lineBox() + uiGetLineSpacing(sg)) / uiScale()
    }
    else if (lineHeight > 0.0) lineHeight
    else math.max(if (boxHeight >= 0.0) boxHeight else 0.0, uiGetHeight(sg) / uiScale())

  // (into the rounded clip, when there is one: see `roundClip`)
  def append(child: RenderNode): Unit = uiAppend(masked.getOrElse(sg), child.sg)
  def insert(child: RenderNode, index: Int): Unit = uiInsert(masked.getOrElse(sg), child.sg, index)

  /** Detach from the parent, if attached. */
  def remove(): Unit = uiRemove(sg)

  // ---- a Video or an Audio node (`Nodes.video`, `Nodes.audio`); SceneGraph has no volume for either and no rate, and a
  // video letterboxes rather than filling its box. An Audio node's sound is routed by its turns (SoundTurns): through
  // the one player SceneGraph plays Audio nodes through, in turn (PlayerStack.device), or as an effect over it ----
  // the headers go on the content node the source is given, and with every request the player makes for it
  def setMediaHeaders(headers: Map[String, String]): Unit =
    uiSetMediaHeaders(sg, headers.toList.map(h => h._1 + ":" + h._2))
  def setMediaSource(url: String): Unit =
    if (turns.nonEmpty) turns.get.setSource(url)
    else {
      uiSetMediaSource(sg, url)
      // captions from the start as the app chose them, else as the device has them
      if (url.nonEmpty) showCaptions(Nil)
    }
  /** Where the next source starts, in seconds: its content node's PlayStart, so the stream opens there. */
  def setMediaStart(seconds: Double): Unit = {
    turns.foreach(_.setStart(seconds))
    uiSetMediaStart(sg, seconds)
  }
  def setMediaSidecars(sidecars: List[everyui.component.MediaSidecar]): Unit =
    uiSetMediaSidecars(sg, sidecars.map(s => List(s.url, s.label, s.language)))
  def setMediaPlaying(playing: Boolean): Unit =
    if (turns.nonEmpty) turns.get.setPlaying(playing) else uiSetMediaPlaying(sg, playing)
  def setMediaLoop(loop: Boolean): Unit = uiSetMediaLoop(sg, loop)
  def setMediaMuted(muted: Boolean): Unit = uiSetMediaMuted(sg, muted)
  // an Audio node has no volume; a SoundEffect has, as a share of the device's menu volume
  def setMediaVolume(volume: Double): Unit = turns.foreach(_.setVolume(volume))
  /** Played as a SoundEffect rather than through the player (AudioPlayer.effect), where its source is a WAV here. */
  def setMediaEffect(on: Boolean): Unit = turns.foreach(_.setEffect(on))

  // Whether anything of it is drawn, and an Audio node's turns at the player
  private var drawn: Boolean = true
  private var turns: Option[SoundTurns] = None

  /** Sound alone (`Nodes.audio`): nothing of it is drawn, so nothing of a layout is written to it, and its sound takes
    * its turn at the player. */
  def soundOnly(): Unit = {
    drawn = false
    turns = Some(new SoundTurns(new AudioOutput(sg), PlayerStack.device))
  }
  def setMediaRate(rate: Double): Unit = ()
  def mediaRates(): Boolean = false
  /** Whether the media has enough in hand to play to its end without stopping (`Backdrop`): said once it plays here. */
  def mediaPlaysThrough(): Boolean = true
  // no samples to be had: the device folds a surround stream its own way
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
  def mediaBuffering(): Boolean = uiMediaBuffering(sg)
  // what the link was measured carrying when the stream was chosen
  def mediaBandwidth(): Long = uiMediaBandwidth(sg)
  def mediaSize(): (Double, Double) = {
    val size = uiMediaSize(sg)
    (size.head, size(1))
  }
  /** The words on screen: "" here, where the platform draws its own subtitles. */
  def mediaCues(): String = ""
  def mediaCueList(): List[everyui.component.MediaCue] = Nil
  // the Video node retries on its own and says nothing until it gives up
  def mediaRetry(): Option[everyui.component.MediaRetry] = None
  // a Roku's own now-playing is the channel's, and SceneGraph hands a channel none of its sound
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
  def releaseWeb(): Unit = ()

  // ---- an embed (everyui.component.Embed): nothing runs one here, so its fallback shows ----
  def embedId(): String = ""
  def embedRuntime(): String = ""
  def embedSend(message: String): Unit = ()
  def onEmbedMessage(f: String => Unit): Unit = ()
  def focusEmbed(on: Boolean): Unit = ()
  def onEmbedFocus(f: Boolean => Unit): Unit = ()
  def releaseEmbed(): Unit = ()

  /** What to hear and read this in: chosen from its tracks once it is playing, where it has one in that language. */
  def setMediaLanguages(audio: String, text: String): Unit = {
    wantAudio = audio
    wantText = text
  }
  private var wantAudio = ""
  private var wantText = ""

  def mediaTracks(): List[everyui.component.MediaTrack] = uiMediaTracks(sg).map { line =>
    val p = line.split('|')
    everyui.component.MediaTrack(if (p.length > 1) p(1) else "", p(0), if (p.length > 2) p(2) else "",
      if (p.length > 3) p(3) else "")
  }
  // the languages asked for, from the tracks it turned out to have; without a text language found there, the captions
  // as the app chose them, else as the device has them
  private def chooseLanguages(): Unit = {
    val tracks = mediaTracks()
    val chosen = List(("audio", wantAudio), ("text", wantText)).filter(w => w._2.nonEmpty).flatMap { w =>
      val tag = everyui.component.MediaTrack.languageTag(w._2)
      tracks.find(t => t.kind == w._1 && everyui.component.MediaTrack.languageTag(t.language) == tag)
    }
    chosen.foreach(t => if (t.kind == "text") uiCaptionsChosen(sg, t.id) else uiSelectMediaTrack(sg, t.kind, t.id))
    if (!chosen.exists(_.kind == "text")) showCaptions(tracks)
  }
  /** Captions by the rule of `everyui.text.Captions`: the text track the app chose, the device's caption mode written
    * On, or none, written Off, as Roku's own caption menu does (its guidance and its certification, 4.8); until it
    * chooses, the device's mode as it is, with the track in its caption language, else the first. A change of the mode
    * by the viewer (the Roku's own options menu, its settings) is theirs: the app's choice goes, and the device's is
    * followed. */
  private def showCaptions(tracks: List[everyui.component.MediaTrack]): Unit = {
    if (captionWatch.isEmpty) captionWatch = Some(everyui.text.Captions.listen(_ => {
      textChosen = false
      textChoice = ""
      showCaptions(mediaTracks())
    }))
    if (textChosen) uiCaptionsChosen(sg, textChoice)
    else uiCaptionsFollow(sg, everyui.text.Captions.deviceTrack(everyui.text.Captions.device, tracks).getOrElse(""))
  }
  private var textChosen = false
  private var textChoice = ""
  private var captionWatch: Option[() => Unit] = None
  /** What was decoded: nothing this player says. */
  def mediaDiagnostics(): everyui.component.MediaDiagnostics = everyui.component.MediaDiagnostics()
  /** A track to hear or read, by its id ("" reads none). The text track chosen is the app's choice from then on, put
    * to each source as it opens (showCaptions). */
  def selectMediaTrack(kind: String, id: String): Unit =
    if (kind == "text") {
      textChosen = true
      textChoice = id
      showCaptions(Nil)
    }
    else uiSelectMediaTrack(sg, kind, id)
  /** The captions as `MediaSupport.captions` chooses them: a track or none (selectMediaTrack), or back to the device's. */
  def setMediaText(choice: everyui.component.TextChoice): Unit = choice match {
    case everyui.component.TextChoice.TextFromDevice =>
      textChosen = false
      textChoice = ""
      showCaptions(mediaTracks())
    case other => selectMediaTrack("text", everyui.component.TextChoice.asTrack(other).getOrElse(""))
  }
  def setMediaCover(cover: Boolean): Unit = ()
  def setMediaDirect(direct: Boolean): Unit = ()
  def getMediaPosition(): Double = uiGetMediaPosition(sg)
  def getMediaDuration(): Double = uiGetMediaDuration(sg)
  def seekMedia(seconds: Double): Unit = uiSeekMedia(sg, seconds)
  def onMediaEvent(f: String => Unit): Unit = uiOnMediaEvent(sg, e => {
    if (e == "ready") chooseLanguages()
    // done with the player, which goes back to the one it was taken from
    turns.foreach(_.heard(e))
    f(e)
  })
  def releaseMedia(): Unit = {
    uiReleaseMedia(sg)
    turns.foreach(_.released())
    captionWatch.foreach(stop => stop())
    captionWatch = None
  }

  def setWidth(width: Double): Unit = {
    val rewrap = wrapping && richWidth >= 0.0 && width != boxWidth
    boxWidth = width
    uiSetWidth(sg, px(width))
    // text drawn in runs is put on its lines by the runtime, at the width it has (uiRichText)
    if (rewrap) drawText()
    if (imageUri.nonEmpty) drawImage()
    if (cornerRadius >= 1.0 || edges.nonEmpty || gradientStops.nonEmpty || fillRect.nonEmpty) paint()
  }
  def setHeight(height: Double): Unit = {
    boxHeight = height
    uiSetHeight(sg, px(height))
    if (imageUri.nonEmpty) drawImage()
    if (cornerRadius >= 1.0 || edges.nonEmpty || gradientStops.nonEmpty || fillRect.nonEmpty) paint()
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
    roundClip()
  }
  /** SceneGraph places every node by its translation, so there is no position type to set. */
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
  def setVisible(visible: Boolean): Unit = if (drawn) uiSetVisible(sg, visible)
  def setOpacity(opacity: Double): Unit = if (drawn) uiSetOpacity(sg, opacity)
  def setClip(width: Double, height: Double): Unit = if (drawn) {
    ownClip = true
    clipWidth = width
    clipHeight = height
    uiSetClip(sg, px(width), px(height))
    roundClip()
  }

  // cut to its box by its container (`Container.clipsToBox`): to a rectangle, its corners left square, where a
  // MaskGroup for every card would cost the device too much; never undoing a clip of its own (`setClip`)
  private var ownClip = false
  def setBoxClip(on: Boolean, width: Double, height: Double): Unit = if (drawn && !ownClip) {
    if (on) uiSetClip(sg, px(width), px(height)) else uiSetClip(sg, 0.0, 0.0)
  }

  /** A clip with rounded corners (a poster's): SceneGraph clips only to a rectangle, so the children go into a MaskGroup
    * whose mask is a rounded rectangle of exactly this size, drawn on the device once for each size and kept. */
  private def roundClip(): Unit =
    if (clipWidth > 0.0 && clipHeight > 0.0 && cornerRadius >= 1.0)
      masked = Some(uiRoundClip(sg, masked, px(clipWidth), px(clipHeight), px(cornerRadius)))
  /** No pointer to rest. */
  def setTooltip(text: String): Unit = ()
  /** No reader of descriptions reaches this platform's nodes. */
  def setDescription(text: String): Unit = ()

  def setPositionType(name: String): Unit = ()

  /** A Label is a single line, cut short with an ellipsis, unless told to wrap: `"normal"` (or `"pre-wrap"`, `"pre-line"`)
    * wraps it at its width. */
  def setWhiteSpace(value: String): Unit = {
    wrapping = value == "normal" || value == "pre-wrap" || value == "pre-line"
    uiSetWrap(sg, wrapping)
    spaceLines()
    if (richWidth >= 0.0) drawText()
  }
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

  def setImageSource(uri: String): Unit = {
    imageUri = uri
    drawImage()
  }
  // decoded at the size it is drawn, at every quality level: smaller, a picture already asked at about that size (a
  // poster) was shrunk and stretched back, blurred for little memory saved
  private def drawImage(): Unit =
    if (imageUri.isEmpty) uiSetImageSource(sg, "")
    else if (boxWidth > 0.0 && boxHeight > 0.0) {
      uiSetImageSized(sg, imageUri, px(boxWidth), px(boxHeight))
    }
    else uiSetImageSource(sg, imageUri)
  /** The picture in one colour, its transparency kept (a Poster's blend colour on a white picture), or as it is. */
  def setTint(hex: String): Unit = uiSetTint(sg, hex)

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
  // The gradient as the seam gave it, and the Poster that draws it (a box with square corners)
  private var gradientStops: String = ""
  private var gradientAngle: Double = 0.0
  private var gradientShape: Option[SGNode] = None
  /** A gradient: drawn as a strip behind a square box, and as its first colour in a rounded one (see `paint`). */
  /** A radial gradient: filled with its first stop, as a linear one's colour. */
  def setRadialGradient(x: Double, y: Double, radius: Double, width: Double, height: Double, stops: String): Unit =
    setGradient(0.0, if (stops.isEmpty) "" else stops.split(",")(0))
  /** No mask: drawn whole. */
  def setMask(angle: Double, x: Double, y: Double, radius: Double, stops: String): Unit = ()
  /** No shadow is drawn. */
  def setShadowLayers(layers: String): Unit = ()
  /** What is behind shows sharp. */
  def setBackdropBlur(radius: Double): Unit = ()

  def setGradient(angle: Double, stops: String): Unit = {
    gradientHex = RenderNode.firstStop(stops)
    gradientStops = stops
    gradientAngle = angle
    paint()
  }
  /** The letters in the gradient's first colour. */
  def setTextGradient(angle: Double, stops: String): Unit = if (stops.nonEmpty) setColor(RenderNode.firstStop(stops))
  /** SceneGraph keeps no pictures of its own to ask for. */
  def setCached(cached: Boolean): Unit = ()

  private var tracks: List[everyui.motion.Track] = Nil
  private var animations: List[SGNode] = Nil
  private var motionOwner: () => String = () => ""
  def setMotionOwner(owner: () => String): Unit = motionOwner = owner

  /** Each track an Animation node over one of the node's own fields, repeating: SceneGraph runs it, with no script
    * per frame. A position is the node's translation, from where it is placed. */
  def setMotion(motion: List[everyui.motion.Track]): Unit = {
    tracks = motion
    animate()
  }

  // a position track runs from where the node is placed, so placing it again makes it again; no other track does
  private def movesPosition: Boolean = tracks.exists(t => t.property == everyui.motion.Track.Position)

  private def animate(): Unit = {
    animations.foreach(a => uiStopMotion(a))
    animations = tracks.map { t =>
      val position = t.property == everyui.motion.Track.Position
      val a = t.keys.map(k => if (position) px(left + k.a) else if (t.property == everyui.motion.Track.Rotation) -k.a else k.a)
      val b = t.keys.map(k => if (position) px(top + k.b) else k.b)
      uiMotion(sg, t.property, t.seconds, t.delay, t.keys.map(k => k.at), a, b, motionOwner)
    }
  }
  /** No shadow is drawn. */
  def setShadow(x: Double, y: Double, blur: Double, hex: String): Unit = ()
  /** A Roku draws nothing blurred. */
  def setBlur(radius: Double): Unit = ()
  /** A Label spaces no letters: they sit as the face sets them, and the width is theirs. */
  /** A variable face's optical sizing and axes: a browser's to set (`FontFeature.opticalSizing`); drawn here as the
    * platform draws the face. */
  def setFontAxes(opticalSizing: String, variations: String): Unit = ()
  def setLetterSpacing(px: Double): Unit = ()
  /** Slanted: a SceneGraph font cannot be slanted, so the words are drawn, and measured, in the family's registered
    * italic face where there is one, and upright where not. */
  def setItalic(on: Boolean): Unit = if (on != italic) {
    italic = on
    if (fontFamily.nonEmpty) setFont(fontFamily, fontWeight, fontSize)
  }
  private var italic: Boolean = false
  private var fontFamily: String = ""
  /** Each line of wrapped text `advance` units below the last: the Label's own spacing, to what the face leaves. */
  def setLineHeight(advance: Double): Unit = {
    lineAdvance = advance
    spaceLines()
    if (wrapping && richWidth >= 0.0) drawText()
  }

  /** The Label's spacing between lines, set for the advance asked for at its current face and size. */
  private def spaceLines(): Unit =
    // (with no advance asked for, the face's own: the theme's spacing is far looser than any other backend's)
    if (wrapping && lineHeight > 0.0) uiSetLineSpacing(sg, math.max(0.0, px(lineAdvance) - lineBox()))

  /** A line of the face at its size, in pixels, as the Label draws one, or the shared line where that is taller: a
    * height worked out from it is never less than the Label's, nor than another backend's. */
  private def lineBox(): Double = math.max(uiLineBox(fontUri, px(fontSize)), px(lineHeight))
  /** A link is followed by its select; there is no address to show. */
  def setHref(href: String, newTab: Boolean): Unit = ()
  /** A drop-down draws itself here (`everyui.component.Select`): the node is its words. */
  def setOptions(labels: List[String]): Unit = ()
  /** A drop-down's look, its open list in the theme's colours: a browser's only, a drop-down drawing itself here. */
  def setDropDown(background: String, text: String, highlight: String, chosen: String, edge: String,
                  inset: Double): Unit = ()
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
      fillRect = uiFill(sg, fillRect, "", 0.0, 0.0)
      clearEdges()
      // (a rounded box is filled with the gradient's first colour: the strip below has square corners)
      gradientShape = dropShape(gradientShape)
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
      if (gradientStops.nonEmpty && w > 0.0 && h > 0.0) {
        // SceneGraph draws no gradient: a strip of it, drawn on the device once, stretched behind the children
        fillRect = uiFill(sg, fillRect, "", 0.0, 0.0)
        gradientShape = Some(uiGradient(sg, gradientShape, gradientAngle, gradientStops, px(w), px(h)))
      } else {
        gradientShape = dropShape(gradientShape)
        fillRect = uiFill(sg, fillRect, fill, px(w), px(h))
      }
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
      val edge = uiRect()
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
  /** A shadow behind the letters: not drawn here. */
  def setTextShadow(x: Double, y: Double, blur: Double, hex: String): Unit = ()
  def setTextOutline(width: Double, hex: String): Unit = ()
  // no reader of roles or descriptions reaches this platform's nodes
  def setSemantics(role: String, level: Int, label: String): Unit = ()
  def setSemanticState(range: Option[everyui.component.types.SemanticRange], checked: Option[Boolean],
                       expanded: Option[Boolean], selected: Option[Boolean], sort: String): Unit = ()
  /** The focus landed here: where a screen reader is on, it reads this out, and pressing there is `click`. */
  def accessibleFocus(click: () => Unit): Unit = ()
  def setAlt(text: String): Unit = ()

  /** "start", "center" or "end", by the reading direction, whatever script the words are in. */
  def setTextAlign(align: String): Unit = uiSetTextAlign(sg, align)
  /** A Label turns no words by their script: the direction is the app's. */
  def setTextDirection(value: String): Unit = ()
  /** A Label wraps where the words let it. */
  def setWrapAnywhere(on: Boolean): Unit = ()
  /** Words that wrap are measured whole on one line here already, and wrap only at a width or a limit. */
  def setTextWhole(on: Boolean): Unit = ()
  /** Words cut short to fit (`TextView.maxWidth`, `maxLines`) are cut by measuring alone: nothing else holds them
    * here. */
  /** Words that wrap held to `width`: the Label given that width, at which it wraps them (a line's room, where words wrap
    * by default rather than end in an ellipsis); 0, or words that fit, let go of it. `lines` is cut by the text itself
    * (TextView). */
  def setTextLimit(width: Double, lines: Int): Unit = if (fontSize > 0.0) {
    val was = textLimit
    textLimit = if (wrapping && width > 0.0 && uiTextWidth(sg, fontUri, fontSize) > width) width else 0.0
    if (textLimit != was) {
      uiSetWidth(sg, px(if (textLimit > 0.0) textLimit else math.max(0.0, boxWidth)))
      if (richWidth >= 0.0) drawText()
    }
  }
  /** Whether words held to a limit wrap at it here: they do, the Label given its width. */
  def wrapsToLimit(): Boolean = true
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
object RenderNode {
  /** The first colour of `everyui.Gradient`'s encoding, as the seam takes a colour. */
  def firstStop(stops: String): String = {
    val space = stops.indexOf(' ')
    val hex = if (space < 0) stops else stops.substring(0, space)
    if (hex.length == 9 && hex.toLowerCase.endsWith("ff")) hex.substring(0, 7) else hex
  }
}

object Nodes {
  /** A form (`everyui.form.Form`): a container here. */
  def form(kind: String): RenderNode = container()

  /** A page of the web: nothing here, where the platform has no engine of its own to lend. */
  def web(bridge: String): RenderNode = container()

  /** An embed's box (everyui.component.Embed): a plain group, which holds its fallback. */
  def embed(name: String, site: everyui.component.EmbedSite): RenderNode = container()

  /** The channel's root Group, created by the generated `AppScene`. */
  def root(): RenderNode = new RenderNode(uiRoot())
  /** A paragraph of styled runs (`everyui.component.RichText`): a text node here, which draws them. */
  def richText(): RenderNode = text()

  /** A text node — a SceneGraph `Label`. */
  def text(): RenderNode = new RenderNode(uiText())
  /** A container — a transparent `Rectangle` other nodes are appended to. */
  def container(): RenderNode = new RenderNode(uiContainer())
  /** A drawing (everyui.draw.Drawing): a container its rectangles go in, since nothing here draws paths. */
  def drawing(): RenderNode = container()
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
  /** A video — a SceneGraph `Video` with no controls of its own. */
  def video(): RenderNode = new RenderNode(uiVideo())
  /** Sound — a SceneGraph `Audio`. */
  def audio(): RenderNode = {
    val node = new RenderNode(uiAudio())
    node.soundOnly()
    node
  }
  /** An image node — a SceneGraph `Poster` loading from `pkg:/`. */
  def image(uri: String, width: Double, height: Double): RenderNode =
    new RenderNode(uiImage(uri, width * uiScale(), height * uiScale()))
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

  /** What to do when the app is at the front again after being away: Instant Resume brings a channel back from
    * behind another (or from the device's screensaver) and calls the scene's resume handler, which says so here. */
  def onResume(f: () => Unit): () => Unit = {
    uiOnLifecycle(() => resumed(), () => away())
    resumers = resumers :+ f
    () => resumers = resumers.filterNot(_ eq f)
  }

  /** Each deep link the channel is given, as Roku's deep linking has it (`contentId`, `mediaType`): the one it was
    * launched with, a turn after this is asked, and any sent to it while it runs or as it is resumed. */
  def onDeepLink(f: (String, String) => Unit): Unit = uiOnDeepLink(f)

  /** Each launch that reaches the app once it is running, with what it was launched with: here ECP's `/launch` (or a
    * deep link) to the running channel, and the launch Instant Resume brings a suspended one back with. A plain return
    * from the home screen, which says nothing of its own, is not one, and the same launch heard both ways at once is
    * told once. `launchArg` reads its values from then on. Returns what stops it being told. */
  def onLaunch(f: Map[String, String] => Unit): () => Unit = {
    uiOnLaunch((params: Map[String, String]) => launched(params))
    launchers = launchers :+ f
    () => launchers = launchers.filterNot(_ eq f)
  }

  def launched(params: Map[String, String]): Unit = launchers.foreach(f => f(params))

  private var launchers: List[Map[String, String] => Unit] = Nil

  /** Every error the app throws from a handler the device calls (a timer, a key, a download…) and nothing caught: the
    * message and the backtrace. The channel goes on rather than stopping. */
  def onError(f: (String, String) => Unit): Unit = {
    hearErrors()
    errorHooks = errorHooks :+ f
  }

  /** What nothing caught, as `onError` hears it: the message and the device's backtrace. */
  def onUncaught(f: (String, String) => Unit): Unit = onError(f)
  /** A throwable's stack: none to be had here, where the device's own backtrace comes with `onError`. */
  def stackOf(t: Throwable): String = ""

  /** The first screen is up and can be used: the launch beacon a certified channel sends. */
  def launchComplete(): Unit = uiLaunchComplete()

  def resumed(): Unit = resumers.foreach(f => f())

  private var resumers: List[() => Unit] = Nil

  /** What to do when the app goes away from the front: another app has the screen, or the device is going to sleep.
    * A player wants to know, because nobody is watching it now. Returns what stops it being told. */
  def onAway(f: () => Unit): () => Unit = {
    uiOnLifecycle(() => resumed(), () => away())
    aways = aways :+ f
    () => aways = aways.filterNot(_ eq f)
  }

  /** Said by the platform when it happens. */
  def away(): Unit = aways.foreach(f => f())

  private var aways: List[() => Unit] = Nil

  /** The app asks before it is left while a guard answers true elsewhere; a TV's Back or Home from the first screen
    * leaves without asking, so nothing is held here. What removes it. */
  def leaveGuard(guard: () => Boolean): () => Unit = () => ()
  def leaveGuardWhile(flag: reactify.Val[Boolean]): () => Unit = () => ()

  def log(message: String): Unit = uiLog(message)
  def logError(message: String, t: Throwable): Unit = uiLog("ERROR " + message + ": " + t.getMessage)
  def ticks(): Double = uiTicks()
  /** Frames as the platform measured them: not here, where the app times its own frames instead. */
  def frameTimings(f: (Double, Double) => Unit): Boolean = false
  /** Every frame runs here (`everyui.task.Frames`): the render thread's Timer already idles itself when nothing is
    * drawn (uiAnimate). */
  def framesOnDemand(): Boolean = false
  def nextFrame(f: () => Unit): Unit = uiLater(f)
  def animate(tick: () => Unit): Unit = uiAnimate(tick)

  /** Assets live in the channel package: `/images/icon.png` is `pkg:/images/icon.png`. A path already in the package
    * is left as it is: `imagePath` hands one out, and resolving it again made `pkg:pkg:/…`, which nothing opens. */
  def resolvePath(path: String): String = if (path.startsWith("pkg:")) path else "pkg:" + path

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
  /** A page's description of itself (`everyui.seo.SEO`): a channel has no title bar. */
  def setSEO(meta: everyui.seo.SEO): Unit = ()
  /** The first screen is drawn: nothing was standing in for it. */
  def drawn(): Unit = ()
  /** Whether a component's size is read in a batch before the frame is drawn (`MeasuredFeature`) rather than at once:
    * here it is read at once. */
  def measuresInBatches: Boolean = false
  def beforePaint(f: () => Unit): Unit = f()
  /** `f` once the page has been painted at least once: here, at once, there being no page painted before the app. */
  def afterFirstPaint(f: () => Unit): Unit = f()

  /** Words for a screen reader to read out (a new message in a chat): a channel's nodes are not read, so nothing is. */
  /** Whether a `Backdrop` plays a video behind a screen (`videoSource`): not here, where it keeps its picture. */
  def backdropVideo(): Boolean = false

  /** Whether people ask for less motion (`everyui.motion.ReduceMotion`): a Roku has no such setting, so never. */
  def reduceMotion(): Boolean = false
  /** The setting as it changes: it never does here. */
  def watchReduceMotion(f: Boolean => Unit): Unit = ()
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
  /** Whether copyText reaches a clipboard: a channel has none, so a caller offers something else or nothing. */
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
  /** Nothing here can scale the screen's colours: an app that wants it dimmed does it itself. */
  def tint(red: Double, green: Double, blue: Double): Boolean = false
  /** The device names itself to the servers its pictures come from. */
  def identify(app: String): Unit = ()

  /** No address bar: no path to show, push or follow. */
  def path(): String = ""
  def pushPath(path: String): Unit = ()
  def onPath(f: String => Unit): Unit = ()

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
  def openUrlLater(): everyui.app.UrlTarget = new everyui.app.UrlTarget(url => openUrl(url), () => ())

  /** A moment marked (everyui.perf.Marks), logged where the app was launched with `marks=1`, for a check to read. */
  def marked(name: String, at: Double): Unit = if (launchArg("marks") == "1") log("[mark] " + name + " " + at.toString)

  /** The app has the screen, or it does not: its focus is coming back to the front (`onResume`). */
  def onFocus(f: () => Unit): () => Unit = onResume(f)

  /** Its focus lost is its going away (`onAway`). */
  def onBlur(f: () => Unit): () => Unit = onAway(f)

  /** Nothing says the app is closed for good here: it is sent away (`onAway`), and ended there. */
  def onClose(f: () => Unit): () => Unit = () => ()

  /** One screen, one app: a second window is not opened. */
  def openWindow(path: String): Boolean = false

  /** The app cannot start itself again into a new build here. */
  def reload(): Boolean = false

  /** Which build is running: the channel's version, as its manifest says it. */
  def buildId(): String = appVersionName()

  private val onlineNow = reactify.Var[Boolean](true)

  /** Whether the device has a network: always here, where nothing follows it; a socket finds out by trying. */
  def online: reactify.Val[Boolean] = onlineNow

  /** A channel's interface has the render thread alone: the work is done at once, and what it hands back by `later`
    * comes on a turn after. */
  def offThread(f: () => Unit): Unit = f()

  /** On a later turn of the render thread, from a fresh stack (a Timer): what a JavaFX runLater or a browser's
    * setTimeout gives, and what keeps a chain of callbacks from nesting into a Roku's small stack. */
  def later(f: () => Unit): Unit = uiLater(f)
  /** `f` once what is running now is done: `later` here. */
  def afterTurn(f: () => Unit): Unit = later(f)
  /** `f` once `seconds` have passed, from a Timer of its own. */
  def after(seconds: Double, f: () => Unit): Unit = uiAfter(seconds, f)

  /** A packaged file (`pkg:/l10n/app_en.arb`), read at once. */
  def readText(path: String, done: String => Unit): Unit = done(uiReadText(path))
  /** An ARB file's messages, parsed by the Roku's own JSON parser into a keyed table: a Scala parser and an entry-list
    * Map take the Roku most of a minute over a translation file of a few thousand entries. */
  def parseMessages(arb: String): Map[String, String] = uiArbMessages(arb)
  def language(): String = uiLanguage()
  def fromCodePoint(codePoint: Int): String = uiChr(codePoint)
  /** Every text starts on the side the language reads from. */
  def setDirection(rtl: Boolean): Unit = uiSetDirection(rtl)
  /** The app's language: which CJK glyphs text takes (Traditional for zh_Hant). */
  def setLanguage(code: String): Unit = uiSetLanguage(code)

  /** Kept in the registry, across launches. */
  def setting(name: String): String = uiSetting(name)
  /** Keep `value` under `name`; "" forgets it. */
  // nowhere safer than the settings to keep a secret here
  def secret(name: String): String = setting(name)
  def saveSecret(name: String, value: String): Unit = saveSetting(name, value)

  def saveSetting(name: String, value: String): Unit = uiSaveSetting(name, value)

  /** The device's own store of documents (see [[everyui.store.Store]]). `cachefs:` here: it outlives a channel
    * restart where `tmp:` does not, and the box may clear it whenever it likes — so a read can always come back
    * empty. The work is done before the callback, which is to say on the thread that draws; one small document at
    * a time is what this is for, and a whole cache read at once is not. */
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

  /** The hardware facts a performance report is read with: none this platform will say. */
  def deviceProfile(): List[(String, String)] = Nil

  /** What the box says of itself at launch (`everyui.perf.Perf.device`): its model number, kind (TV or STB) and name,
    * its graphics (opengl or directfb), and the most memory the channel may use: its foreground limit (what it had left
    * at that moment was read before, and read like the app's size). A Roku says nothing of its cores or its RAM. */
  def deviceHint(): everyui.perf.DeviceHint = {
    val model = uiDeviceFact("model")
    val graphics = uiDeviceFact("graphics")
    val app = uiDeviceFact("appLimitKb")
    everyui.perf.DeviceHint(
      model = if (model.isEmpty) None else Some(model),
      graphicsPlatform = if (graphics.isEmpty) None else Some(graphics),
      appMemoryMb = if (app.isEmpty) None else Some(app.toInt / 1024))
  }
  /** The box's general memory level: "normal", "low" or "critical" ("" where it does not say). */
  def memoryLevel(): String = uiDeviceFact("memoryLevel")
  /** The share of the channel's memory limit in use (roAppMemoryMonitor), or -1. */
  def memoryUsedPct(): Int = {
    val pct = uiDeviceFact("memoryPct")
    if (pct.isEmpty) -1 else pct.toInt
  }

  /** How often the display is drawn, in Hz, for where a frame counts as dropped (`everyui.perf.QualityPolicy`): the
    * rate the video mode names (roDeviceInfo.GetVideoMode: "720p50", "1080p30"), 60 for a mode that names none after
    * its "p" ("1080p"), as Matt ruled, and 0 where the mode says nothing of it. */
  def refreshRate(): Double = {
    val hz = uiDeviceFact("refreshHz")
    if (hz.isEmpty) 0.0 else hz.toInt.toDouble
  }
  /** How many errors nothing caught this run has seen (RtGuard's reports: a handler's throw, the device's own stack
    * overflow or execution timeout), with the turns RtTurnCheck found cut off, counted from the first time it or
    * `onError` is asked. */
  def errorsSeen(): Int = {
    hearErrors()
    errors
  }
  /** How many turns the device cut off: a handler begun on one turn that never returned (RtTurnCheck's "a turn was cut
    * off"), the device having stopped it without a word. */
  def turnsCutOff(): Int = {
    hearErrors()
    cutOff
  }
  private var errors: Int = 0
  private var cutOff: Int = 0
  private var hearing: Boolean = false
  private var errorHooks: List[(String, String) => Unit] = Nil
  private def hearErrors(): Unit = if (!hearing) {
    hearing = true
    uiOnError((message: String, stack: String) => {
      errors += 1
      if (message.startsWith("a turn was cut off")) cutOff += 1
      errorHooks.foreach(f => f(message, stack))
    })
  }

  /** What the app is showing, as a PNG in base64: nothing here can take one. */
  def screenshot(): String = ""

  def launchesApps(): Boolean = false
  // nothing here says which codecs it has, so none is ruled out: a wrong no costs a transcode nobody needed
  def decodes(codec: String): Boolean = true
  // an app here is updated by whatever put it here, never by itself
  def canInstallApps(): Boolean = false
  def openInstallSettings(): Unit = ()
  def installApp(url: String, progress: Double => Unit, done: Option[String] => Unit): Unit =
    done(Some("Updates install only on Android"))
  /** Something large was let go (a screen, a scene): free it. BrightScript frees what nothing refers to at once, but a
    * component refers to itself (its features point back at it), and only the cycle collector frees that; it costs
    * half a second on a television, so it is run once things go quiet rather than on a clock. */
  def collect(): Unit = uiCollect()
  /** Whether `blur` is drawn: not here, where a picture is drawn sharp. An app that wants a blurred picture draws a tiny
    * copy stretched over its box, which the scaling blurs. */
  def blurs(): Boolean = false
  /** Whether text can hold emoji: not here, where the box's own font has none. The runtime draws the ones a Material icon
    * means the same as, and leaves out the rest (uiEmojiText); an app puts a word where one carried it. */
  def emoji(): Boolean = false
  // nothing here turns speech into words
  def dictation(): Boolean = false
  def dictate(heard: (String, Boolean) => Unit, ended: () => Unit): () => Unit = {
    ended()
    () => ()
  }
  def localAddress(): String = uiLocalAddress()
  def deviceSerial(): String = uiDeviceSerial()

  /** Where the app came from ("roku", the channel store, or "dev", sideloaded), and which build it is: the release in
    * the manifest, and the build's number as the app's other platforms count it, which the build writes there too. */
  def installStore(): String = uiInstallStore()
  def appVersion(): Int = uiAppVersion()
  def appVersionName(): String = uiAppVersionName()

  /** The device's own caption settings, where it has any: not here. */
  def captionStyle(): Option[everyui.text.CaptionStyle] = None

  def deviceName(): String = uiDeviceName()

  /** No text fields of its own. */
  def nativeInput(): Boolean = false
  /** The platform keyboard is still to be wired to the runtime, so no value comes back yet. */
  def editText(title: String, value: String, secure: Boolean, done: String => Unit): Unit = ()
  /** `editText` for a field asking for `autofill` (`Autofill.token`), which says nothing more here. */
  def editField(title: String, value: String, secure: Boolean, autofill: String, done: String => Unit): Unit =
    editText(title, value, secure, done)

  /** Faces are files the runtime opens by name. */
  def registerFont(face: Face): Unit = ()
  /** A colour emoji face for where the system has none (`Faces.colorEmoji`): the system draws its own here, so none
    * is taken. */
  def registerColorEmoji(face: Face): Boolean = false

  /** The screen in the app's units, over the size the app is drawn at (everyui.theme.UiScale). */
  def screenWidth(): Double = uiScreenWidth() / everyui.theme.UiScale()
  def screenHeight(): Double = uiScreenHeight() / everyui.theme.UiScale()
  /** Screen pixels to a layout unit. A Roku lays out at 1920 by 1080 whatever its panel, and says nothing here of a
    * 4K one: one pixel to a unit, times the size the app is drawn at. */
  def pixelRatio(): Double = everyui.theme.UiScale()
  /** The app drawn `factor` times its size (everyui.theme.UiScale): the root group scaled from the screen's top left,
    * its words enlarged as drawn, so a little soft above 1. */
  def setUiScale(factor: Double): Unit = uiSetRootScale(factor)

  /** `pkg:/freeze.txt` in the channel (added by tools/check.sh to a test sideload): step the animation that far on
    * a virtual clock and stop. Negative when absent. */
  def freezeSeconds(): Double = uiFreezeSeconds()

  /** The frozen frame is complete. (tools/check.sh reads it off the debug console.) */
  def frozen(): Unit = ()

  /** What the channel was launched with (`/launch/dev?name=value` over ECP), else `pkg:/<name>.txt` in the channel (a test
    * sideload adds it, `roku-install --add name=value`), or "". */
  def launchArg(name: String): String = uiLaunchArg(name)

  /** Which of `ids`, Roku channel ids, are installed on this Roku. */
  def installedApps(ids: List[String]): List[String] =
    if (ids.isEmpty) Nil else uiAppsInstalled(ids.mkString(",")).split(',').toList.filter(_.nonEmpty)

  /** None yet. A Roku can ask: an roDatagramSocket with SetBroadcast(true) sends the Jellyfin and Emby questions to
    * 255.255.255.255:7359 and the Plex GDM M-SEARCH to 239.0.0.250:32414, and hears the unicast answers on an
    * roMessagePort within the time given. */
  def findMediaServers(seconds: Double, found: List[everyui.MediaServer] => Unit): Unit = later(() => found(Nil))

  /** `millis` since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
  def localDateTime(millis: Long): String = uiLocalDateTime(millis)

  /** No page to be served from: an app names its server. */
  def webSocketOrigin(): String = ""
  /** Where this app's server is, to resolve its relative URLs against: nothing here comes from a server, so what the app
    * set as the setting "everyui.origin" ("https://example.com"), else "". */
  def origin(): String = setting("everyui.origin")

  /** A channel cannot exit itself from the render thread; a demo that is done just stops. */
  def quit(): Unit = ()

  /** A channel's design resolution never changes. */
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
  /** No finger touches a television. */
  def pointerFinger(): Boolean = false
  /** Every key is the focus layer's: nothing here handles its own. */
  def keyEmbed(): String = ""
  /** No shortcuts are made on a remote. */
  def commandKey(): Boolean = false

  def onResize(f: () => Unit): Unit = ()

  // BrightScript intrinsics, through everyui's runtime. The web backend
  // supplies the same four from the real Scala stdlib.
  /** A number from 0 up to 1, at random. */
  def random(): Double = uiRandom()
  def sqrt(x: Double): Double = uiSqrt(x)
  def pow(x: Double, y: Double): Double = uiPow(x, y)
  def sin(x: Double): Double = uiSin(x)
  def cos(x: Double): Double = uiCos(x)
}
