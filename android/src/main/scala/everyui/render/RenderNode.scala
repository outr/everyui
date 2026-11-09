package everyui.render

import android.app.Activity
import android.content.Context
import android.graphics.{Bitmap, BitmapFactory, Paint, Typeface}
import android.util.{Log, TypedValue}
import android.view.{Choreographer, KeyEvent, MotionEvent, View, ViewGroup}
import android.widget.{EditText, ImageView, TextView}
import everyui.font.{Face, Faces}
import everyui.PlatformKind
import everyui.event.PointerKind

import scala.collection.mutable

/** **The one invention.** The abstract render node, Android edition: a native `View` — a `TextView`, an `ImageView`,
  * or a [[NodeGroup]] holding other nodes.
  *
  * Signature-for-signature the same as its DOM, SceneGraph and JavaFX twins. Units are the screen's pixels, so a
  * 1920x1080 Android TV lays out exactly as the Roku's 1920x1080 design space does. A node's position is its
  * translation: moving it never asks Android for a layout pass, only a size or font change does.
  */
class RenderNode(val view: View) {
  private var left: Double = 0.0
  private var top: Double = 0.0
  private var rotation: Double = 0.0
  private var explicitWidth: Double = -1.0
  private var explicitHeight: Double = -1.0
  private var face: Face = Faces.OpenSansRegular
  private var fontSize: Double = 32.0
  private var wrapping: Boolean = false
  // where words with no width of their own wrap, kept to their box (setTextLimit); 0 for nowhere
  private var wrapLimit: Double = 0.0
  // where its text sits, by the reading direction (`setTextAlign`)
  private var textAlign: String = "start"
  // which way its words run (`setTextDirection`): "" the app's, "auto" their own, "ltr" or "rtl"
  private var textDirection: String = ""
  // what a video or an audio node plays (`Nodes.video`, `Nodes.audio`)
  private[render] var media: Option[ExoMedia] = None

  if (view.getLayoutParams == null)
    view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

  def getText(): String = view match {
    case t: TextView => t.getText.toString
    case _ => ""
  }

  def setText(text: String): Unit = view match {
    case t: TextView =>
      // what the face has nothing for (an emoji newer than the device's own fonts) in the app's fallback face
      t.setText(FaceSpan.fallen(text, t.getPaint))
      if (textDirection == "auto") applyAlign()
      // a string written as several lines is drawn as several lines, wrapping or not. Not wrapping means "do not
      // break these words for me", not "throw away everything after the first newline", which is what a fixed
      // maxLines of 1 did: a two-line subtitle drew its first line inside a box measured for both.
      if (!wrapping) {
        val lines = RenderNode.lineCount(text)
        t.setHorizontallyScrolling(lines <= 1)
        t.setMaxLines(lines)
      }
    case _ => ()
  }

  def getX(): Double = left
  def setX(x: Double): Unit = {
    left = x
    view.setTranslationX(x.toFloat)
    moved()
    if (tracks.nonEmpty) animate()
  }

  def getY(): Double = top
  def setY(y: Double): Unit = {
    top = y
    placeY()
    if (tracks.nonEmpty) animate()
  }

  /** Radians, clockwise, about the node's own centre — a View's rotation is degrees, clockwise, and pivots on its
    * centre unless told otherwise. */
  def getRotation(): Double = rotation
  def setRotation(radians: Double): Unit = {
    rotation = radians
    view.setRotation(math.toDegrees(radians).toFloat)
  }

  /** Drawn larger or smaller about its own centre, which is what a View pivots on unless told otherwise. */
  def setScale(factor: Double): Unit = {
    view.setScaleX(factor.toFloat)
    view.setScaleY(factor.toFloat)
  }

  def setFontSize(size: Double): Unit = view match {
    case t: TextView =>
      fontSize = size
      t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size.toFloat)
      spaceLines(t)
      placeY()
    case _ => ()
  }

  /** `"#rrggbb"`. */
  def setColor(hex: String): Unit = view match {
    case t: TextView => t.setTextColor(android.graphics.Color.parseColor(hex))
    case _ => ()
  }

  /** A bundled face (`assets/fonts/`, packaged as the APK's `assets/fonts/`): family, CSS weight, size in pixels. */
  def setFont(family: String, weight: Int, size: Double): Unit = view match {
    case t: TextView =>
      face = Faces(family, weight)
      fontSize = size
      fontFamily = family
      fontWeight = weight
      // a weighted system face is Android 9's; before it, bold or not is the choice there is
      val upright = if (family.nonEmpty) Host.typeface(face)
        else if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false)
        else Typeface.create(Typeface.DEFAULT, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
      // slanted: the family's own italic where one is registered, else the face made italic, which TextView slants
      // itself where the face has none (NORMAL takes that slant off again)
      if (!italic) t.setTypeface(upright, Typeface.NORMAL)
      else Faces.italic(family, weight) match {
        case Some(f) => t.setTypeface(Host.typeface(f), Typeface.NORMAL)
        case None => t.setTypeface(upright, Typeface.ITALIC)
      }
      t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size.toFloat)
      spaceLines(t)
      placeY()
    case _ => ()
  }

  /** The laid-out size in pixels. Text is measured with its real font (its advance width, at fractional size) and is
    * one shared line (everyui.font.Face) tall. `Paint.measureText` rounds the width UP to a whole pixel; the run
    * advance is the exact one. */
  /** Always: a node here is measured where it is, in the page or not (only the web's are measured in batches). */
  def onPage: Boolean = true

  def getWidth(): Double =
    if (explicitWidth >= 0.0) explicitWidth
    else view match {
      case _: EditText => 0.0
      case t: TextView =>
        measure(t)
        if (wrapping && wrapLimit > 0.0 && laidOutWidth > wrapLimit) wrapLimit else laidOutWidth
      case _ => 0.0
    }

  /** How wide the words are whole, on their lines, whatever width the view is given. */
  def textWidth(): Double = view match {
    case _: EditText => 0.0
    case t: TextView =>
      measure(t)
      laidOutWidth
    case _ => 0.0
  }

  /** Its width and height, read together (the web reads a node out of the page once for both). */
  def getSize(): (Double, Double) = (getWidth(), getHeight())
  def getHeight(): Double =
    if (explicitHeight >= 0.0) explicitHeight
    else view match {
      case t: TextView if wrapping =>
        measure(t)
        // and as far as its first line is set down to the shared baseline (`placeY`): the lines are drawn that much
        // lower in the box, and the last one's descenders (g, y) were cut by the edge of what held it (a chat
        // bubble's, on a phone at 390 dpi, where the face's baseline and its rounded ascent come a few units apart)
        laidOutHeight + math.max(0.0, restY - top)
      case t: TextView => face.lineHeight(fontSize) * RenderNode.lineCount(t.getText).toDouble
      case _ => 0.0
    }

  private var laidOutText: String = null
  private var laidOutAt: Int = -1
  private var laidOutSize: Float = -1.0f
  private var laidOutFace: Typeface = null
  private var laidOutWidth: Double = 0.0
  private var laidOutHeight: Double = 0.0

  /** What the words come to: measured once, and again only when they, the face or the width they wrap at change.
    * Laying text out is the costliest measurement there is, and a screen of cards asks for it thousands of times a
    * second while it loads. */
  private def measure(t: TextView): Unit = {
    val text = t.getText
    val paint = t.getPaint
    // wrapped at its width, or at the limit it is held to (setTextLimit); with neither, on the lines its words make (-1)
    val at = if (!wrapping) 0
      else if (explicitWidth >= 0.0) math.max(1, math.round(explicitWidth).toInt)
      else if (wrapLimit > 0.0) math.max(1, math.round(wrapLimit).toInt)
      else -1
    if (at != laidOutAt || paint.getTextSize != laidOutSize || (paint.getTypeface ne laidOutFace) ||
      !sameText(text, laidOutText)) {
      val whole = text.toString
      laidOutText = whole
      laidOutAt = at
      laidOutSize = paint.getTextSize
      laidOutFace = paint.getTypeface
      laidOutWidth =
        if (wrapping) paint.measureText(whole).toDouble
        // styled runs are measured with their styles: an italic or a bold line is wider than its plain letters
        else if (text.isInstanceOf[android.text.Spanned])
          math.max(RenderNode.widest(paint, whole), android.text.Layout.getDesiredWidth(text, paint).toDouble)
        else RenderNode.widest(paint, whole)
      laidOutHeight =
        if (wrapping) {
          // the view measures itself, rather than a StaticLayout built to look like it: a TextView breaks its lines
          // its own way, and words a layout of ours put on two lines were drawn on three
          t.measure(if (at > 0) android.view.View.MeasureSpec.makeMeasureSpec(at, android.view.View.MeasureSpec.EXACTLY)
            else android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
          t.getMeasuredHeight.toDouble
        } else 0.0
    }
  }

  private def sameText(text: CharSequence, was: String): Boolean = was != null && text.length == was.length && {
    var i = 0
    var same = true
    while (same && i < text.length) {
      if (text.charAt(i) != was.charAt(i)) same = false
      i += 1
    }
    same
  }

  /** Add `child` at the end, MOVING it here if it is somewhere else — as a browser's appendChild, JavaFX and a Roku's
    * SceneGraph all do. Android alone throws for a view that already has a parent, and on a television that throw
    * was the app gone: a component put back into a panel built again (a search's results, reopened) did exactly that. */
  def append(child: RenderNode): Unit = view match {
    case g: ViewGroup =>
      child.view.getParent match {
        case old: ViewGroup => old.removeView(child.view)
        case _ => ()
      }
      g.addView(child.view)
    case _ => ()
  }

  /** `child` at `index` among this node's children, drawn over those before it and under those after. */
  def insert(child: RenderNode, index: Int): Unit = view match {
    case g: ViewGroup =>
      child.view.getParent match {
        case old: ViewGroup => old.removeView(child.view)
        case _ => ()
      }
      g.addView(child.view, math.max(0, math.min(index, g.getChildCount)))
    case _ => ()
  }

  /** Detach from the parent, if attached. */
  def setMediaHeaders(headers: Map[String, String]): Unit = media.foreach(_.setHeaders(headers))
  /** A site-absolute path is the APK's asset (Media3's `asset:///`), or the app's server's where it holds none
    * (`Host.address`). */
  def setMediaSource(url: String): Unit = media.foreach { m =>
    val at = Host.address(url)
    m.setSource(if (url.startsWith("/") && !url.startsWith("//") && at.indexOf("://") < 0) "asset:///" + at else at)
  }
  /** Where the next source starts, in seconds: handed to the player with it, before it is prepared. */
  def setMediaStart(seconds: Double): Unit = media.foreach(_.setStart(seconds))
  def setMediaSidecars(sidecars: List[everyui.component.MediaSidecar]): Unit = media.foreach(_.setSidecars(sidecars))
  def setMediaPlaying(playing: Boolean): Unit = media.foreach(_.setPlaying(playing))
  def setMediaLoop(loop: Boolean): Unit = media.foreach(_.setLoop(loop))
  def setMediaMuted(muted: Boolean): Unit = media.foreach(_.setMuted(muted))
  def setMediaVolume(volume: Double): Unit = media.foreach(_.setVolume(volume))
  def setMediaRate(rate: Double): Unit = media.foreach(_.setRate(rate))
  /** The centre of a surround stream `db` decibels over the usual, folded to stereo or lifted in place in the player's
    * audio path ([[CenterMixer]]), whether it can reach what is playing, and whether it is heard now. */
  def setMediaCenterMix(db: Double): Unit = media.foreach(_.setCenterMix(everyui.audio.Downmix.clamp(db)))
  def mediaCenterMixes(): Boolean = media.exists(_.centerMixSupported())
  def mediaCenterMixing(): Boolean = media.exists(_.centerMixing())
  /** Voice clarity and night mode, 0 to 1 each, after the centre in the player's audio path ([[ListeningProcessor]]):
    * where the centre mix can reach what is playing, and heard while the sound goes out as PCM through them. */
  def setMediaAids(clarity: Double, night: Double): Unit =
    media.foreach(_.setAids(everyui.audio.VoiceClarity.clamp(clarity), everyui.audio.NightMode.clamp(night)))
  def mediaAidsSupported(): Boolean = media.exists(_.centerMixSupported())
  def mediaAidsHeard(): Boolean = media.exists(_.aidsHeard())
  def setMediaBuffering(named: String): Unit = media.foreach(_.setBuffering(named))
  def setMediaCached(cached: Boolean): Unit = media.foreach(_.setCached(cached))
  def mediaRates(): Boolean = true
  /** Whether the media has enough in hand to play to its end without stopping (`Backdrop`): said once it plays here. */
  def mediaPlaysThrough(): Boolean = true
  def mediaBandwidth(): Long = media.map(_.bandwidth()).getOrElse(0L)
  def mediaBuffering(): Boolean = media.exists(_.buffering())
  def mediaSize(): (Double, Double) = media.map(_.size()).getOrElse((0.0, 0.0))
  /** What is being said on screen at this moment, where this platform hands the words over rather than drawing
    * them itself: on Android nothing draws them, since that is media3-ui and this build merges no resources. */
  def mediaCues(): String = media.map(_.cues()).getOrElse("")
  /** The same, as the file places and styles each one. */
  def mediaCueList(): List[everyui.component.MediaCue] = media.map(_.cueList()).getOrElse(Nil)
  /** The last load the player retried on its own. */
  def mediaRetry(): Option[everyui.component.MediaRetry] = media.flatMap(_.retry())
  /** What is playing, for Android's own now-playing: a media session, with the sound's focus (NowPlayingSession). */
  def setNowPlaying(about: everyui.component.NowPlaying): Unit = media.foreach(_.setNowPlaying(about))
  /** Hear the sound as it plays, for a visualizer: a tap on the decoded audio (SpectrumTap). */
  def setMediaSpectrum(on: Boolean): Unit = media.foreach(_.setSpectrum(on))
  def mediaBands(): List[Double] = media.map(_.bands()).getOrElse(Nil)

  // ---- a page of the web ----

  /** Show this address. */
  def setWebSource(url: String): Unit = if (webSandbox != null) {
    webSession = java.lang.Long.toString((math.random() * 1.0e12).toLong, 36)
    // a site-absolute path is the APK's page, or the app's server's where it holds none (Host.address)
    val at = Host.address(url)
    val packaged = url.startsWith("/") && !url.startsWith("//") && at.indexOf("://") < 0
    webSandbox.load(if (packaged) "file:///android_asset/" + at else at)
  }

  /** Who the page is told is asking, for the loads after this one; empty is the engine's own. */
  def setWebAgent(agent: String): Unit = view match {
    case w: android.webkit.WebView => w.getSettings.setUserAgentString(if (agent.trim.isEmpty) null else agent)
    case _ => ()
  }

  /** The page sandboxed with `flags` allowed, as near as Android's WebView comes to it (WebSandbox). */
  def setWebSandbox(flags: Option[Set[String]]): Unit = if (webSandbox != null) webSandbox.set(flags)

  /** The view drawn clear rather than black, so a transparent page shows what is behind it. */
  def setWebClear(clear: Boolean): Unit = view match {
    case w: android.webkit.WebView =>
      w.setBackgroundColor(if (clear) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK)
    case _ => ()
  }

  /** Say something to the page, in its own language. */
  def runWebScript(script: String): Unit = view match {
    case w: android.webkit.WebView => if (webSandbox == null || !webSandbox.sandboxed) w.evaluateJavascript(script, null)
    case _ => ()
  }

  /** Hear what the page says back. */
  def onWebMessage(f: String => Unit): Unit = webListener = f

  /** A sandboxed page heard too (`on`): lent the bridge, which is all it is lent, the page loaded again with it. */
  def setWebHearSandboxed(on: Boolean): Unit = if (webSandbox != null) webSandbox.hear(on)

  /** `request` posted to the page as `{everyui: "ask", session, id, request}`, as a page of the web is posted it; its
    * reply comes back through the bridge (`webSaid`). False where there is no page, or a sandboxed one is not heard. */
  def askWeb(id: Int, request: String): Boolean = view match {
    case w: android.webkit.WebView if webSandbox != null && webSandbox.heard && webSession.nonEmpty =>
      val message = new org.json.JSONObject().put("everyui", "ask").put("session", webSession).put("id", id)
        .put("request", request)
      w.evaluateJavascript("window.postMessage(" + message.toString + ", '*')", null)
      true
    case _ => false
  }

  def onWebReply(f: (Int, String) => Unit): Unit = webReplier = f

  // no frame inside the page is heard apart from it here
  def onWebFrameMessage(f: Option[(String, String) => Unit]): Unit = ()
  // ---- a rich document's editing surface: none here yet (Platform.richEditing); the editor edits its Markdown ----
  def setRichDoc(doc: everyui.rich.RichDoc, selection: everyui.rich.RichRange): Unit = ()
  def setRichLook(look: everyui.rich.RichLook, placeholder: String, readOnly: Boolean): Unit = ()
  def setRichGhost(at: everyui.rich.RichPos, lines: List[String]): Unit = ()
  def onRichInput(f: everyui.rich.RichInput => Unit): Unit = ()
  def onRichHeight(f: Double => Unit): Unit = ()
  def focusRich(selection: everyui.rich.RichRange): Unit = ()
  def richSelection(): Option[everyui.rich.RichRange] = None

  /** Heard no more, and the view destroyed: the app is done with the page. Taken off the screen only, a web view
    * kept its page running (its script, its timers, what it said over the bridge) and its page process alive and
    * holding its memory until the collector happened on the view, if it ever did: on a television short of memory the
    * system went on killing processes and the app on collecting for half a minute after a trailer had gone. It is
    * taken out of its parent first, as a web view destroyed while still in a window is not let go of whole. */
  def releaseWeb(): Unit = {
    webListener = _ => ()
    webReplier = (_, _) => ()
    view match {
      // an embed's web view lets itself go (EmbedView.release)
      case w: android.webkit.WebView if !webReleased && !w.isInstanceOf[EmbedView] =>
        webReleased = true
        try {
          w.stopLoading()
          w.loadUrl("about:blank")
          w.onPause()
          remove()
          w.removeAllViews()
          w.destroy()
        }
        catch { case t: Throwable => Platform.log("[web] the page could not be let go of: " + t.toString) }
      case _ => ()
    }
  }

  private var webReleased = false

  private[render] var webListener: String => Unit = _ => ()
  private[render] var webSandbox: WebSandbox = null
  private var webReplier: (Int, String) => Unit = (_, _) => ()
  private var webSession: String = ""

  // a reply to `askWeb` (its JSON) goes to whatever asked, where it is this page's; anything else is heard
  private[render] def webSaid(message: String): Unit =
    if (message.startsWith("{\"everyui\":\"reply\"")) {
      try {
        val reply = new org.json.JSONObject(message)
        if (reply.optString("session") == webSession && reply.has("id"))
          webReplier(reply.getInt("id"), Option(reply.opt("reply")).map(_.toString).getOrElse(""))
      }
      catch { case _: org.json.JSONException => webListener(message) }
    }
    else webListener(message)

  // ---- an embed (everyui.component.Embed, Nodes.embed): a web guest in an EmbedView of its own ----
  private def embedView: Option[EmbedView] = view match {
    case e: EmbedView => Some(e)
    case _ => None
  }
  def embedId(): String = embedView.map(_.id).getOrElse("")
  def embedRuntime(): String = embedView.map(_.id).getOrElse("")
  def embedSend(message: String): Unit = embedView.foreach(_.send(message))
  def onEmbedMessage(f: String => Unit): Unit = embedView.foreach(_.hear(f))
  def focusEmbed(on: Boolean): Unit = embedView.foreach(_.focus(on))
  def onEmbedFocus(f: Boolean => Unit): Unit = embedView.foreach(_.onFocus(f))
  def releaseEmbed(): Unit = embedView.foreach(_.release())

  /** What to hear and read this in, where the file carries a choice. Said before the source, so the selector picks
    * with it rather than picking the file's own default and switching afterwards. */
  def setMediaLanguages(audio: String, text: String): Unit = media.foreach(_.languages(audio, text))

  def mediaTracks(): List[everyui.component.MediaTrack] = media.map(_.tracks()).getOrElse(Nil)
  def mediaDiagnostics(): everyui.component.MediaDiagnostics =
    media.map(_.diagnostics()).getOrElse(everyui.component.MediaDiagnostics())
  def selectMediaTrack(kind: String, id: String): Unit = media.foreach(_.selectTrack(kind, id))
  def setMediaText(choice: everyui.component.TextChoice): Unit = media.foreach(_.chooseText(choice))
  def setMediaCover(cover: Boolean): Unit = media.foreach(_.setCover(cover))
  def setMediaDirect(direct: Boolean): Unit = media.foreach(_.setDirect(direct))
  // an effect (AudioPlayer.effect) plays here as any sound does, mixed with the rest
  def setMediaEffect(effect: Boolean): Unit = ()
  def getMediaPosition(): Double = media.map(_.position()).getOrElse(0.0)
  def getMediaDuration(): Double = media.map(_.duration()).getOrElse(0.0)
  def seekMedia(seconds: Double): Unit = media.foreach(_.seek(seconds))
  def onMediaEvent(f: String => Unit): Unit = media.foreach(_.onEvent(f))
  def releaseMedia(): Unit = media.foreach(_.release())

  def remove(): Unit = view.getParent match {
    case g: ViewGroup => g.removeView(view)
    case _ => ()
  }

  def setWidth(width: Double): Unit = {
    explicitWidth = width
    val params = view.getLayoutParams
    params.width = math.max(0L, math.round(width * EmbedView.scaleOf(view))).toInt
    view.setLayoutParams(params)
  }

  def setHeight(height: Double): Unit = {
    explicitHeight = height
    val params = view.getLayoutParams
    params.height = math.max(0L, math.round(height * EmbedView.scaleOf(view))).toInt
    view.setLayoutParams(params)
  }

  /** `"#rrggbb"`, or `""` for transparent. */
  private var background: Int = 0
  private var radius: Float = 0f
  private var borderWidth: Int = 0
  private var borderColor: Int = 0
  def setBackgroundColor(hex: String): Unit = {
    // Android wants #aarrggbb; the seam says #rrggbb or #rrggbbaa
    background = if (hex.isEmpty) 0 else RenderNode.color(hex)
    paintBackground()
  }
  def setRadius(r: Double): Unit = {
    radius = r.toFloat
    paintBackground()
  }
  /** An edge `width` pixels wide, inside the box. */
  def setBorder(width: Double, hex: String): Unit = {
    borderWidth = if (hex.isEmpty) 0 else math.round(width).toInt
    borderColor = if (hex.isEmpty) 0 else RenderNode.color(hex)
    paintBackground()
  }
  private var gradient: Array[Int] = Array.empty
  private var orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
  /** A linear gradient over the box (`everyui.Gradient`'s encoding, its stops evenly spaced here), or none for `""`. */
  /** A radial gradient, its centre `x`, `y` and its radius in pixels (the box `width` by `height`), its stops from the
    * centre out: the platform's own radial drawable. */
  def setRadialGradient(x: Double, y: Double, radius: Double, width: Double, height: Double, stops: String): Unit = {
    setGradient(0.0, stops)
    radial = if (stops.isEmpty) None
      else Some(((if (width > 0.0) x / width else 0.5).toFloat, (if (height > 0.0) y / height else 0.5).toFloat,
        math.max(0.01, radius * Host.scale).toFloat))
    paintBackground()
  }
  // a radial gradient's centre (fractions of the box) and radius (pixels), where the gradient is one
  private var radial: Option[(Float, Float, Float)] = None
  /** No mask: drawn whole. */
  def setMask(angle: Double, x: Double, y: Double, radius: Double, stops: String): Unit = ()
  /** The first layer is drawn, as the view's elevation (`setShadow`). */
  def setShadowLayers(layers: String): Unit = ()
  /** What is behind shows sharp: a view blurs only its own drawing. */
  def setBackdropBlur(radius: Double): Unit = ()

  def setGradient(angle: Double, stops: String): Unit = {
    radial = None
    val decoded = everyui.Gradient.decode(stops)
    gradient = decoded.map { case (hex, _) => RenderNode.color(hex) }.toArray
    gradientAt = decoded.map(_._2).toArray
    gradientAngle = angle
    orientation = RenderNode.orientation(angle)
    paintBackground()
  }
  /** The letters in the gradient's first colour. */
  def setTextGradient(angle: Double, stops: String): Unit =
    everyui.Gradient.decode(stops).headOption.foreach { case (hex, _) => setColor(hex) }
  /** No shadow is drawn. */
  /** A View's own shadow, which the platform draws from its outline: the colour is the glow's, and the blur its
    * elevation. A raised node also draws above its neighbours, which is what a card growing under the focus wants. */
  /** A gaussian blur of deviation `radius` (the web's `filter: blur()`): a RenderEffect on Android 12 and later, and
    * before it a container or a picture blurred by hand (SoftBlur); anything else is drawn sharp there. */
  def setBlur(radius: Double): Unit =
    if (android.os.Build.VERSION.SDK_INT >= 31) {
      val r = SoftBlur.radius(radius).toFloat
      view.setRenderEffect(if (radius <= 0.0) null
      else android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP))
    }
    else view match {
      case g: NodeGroup => g.blur(radius)
      case p: PictureView => p.blur(radius)
      case _ => ()
    }
  def setShadow(x: Double, y: Double, blur: Double, hex: String): Unit =
    if (hex.isEmpty) view.setElevation(0f)
    else {
      val colour = android.graphics.Color.parseColor(hex)
      // a coloured shadow is Android 9's; before it the glow is the platform's own dark one, which still lifts
      if (android.os.Build.VERSION.SDK_INT >= 28) {
        view.setOutlineSpotShadowColor(colour)
        view.setOutlineAmbientShadowColor(colour)
      }
      view.setElevation((blur / 2.0).toFloat)
    }
  /** A hardware layer: the view is drawn into a texture once, and again only when it changes. */
  def setCached(cached: Boolean): Unit =
    view.setLayerType(if (cached) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE, null)

  private var tracks: List[everyui.motion.Track] = Nil
  /** The motion's animators, run only while the view is in a window and shown (Motion): made on first use. */
  private var motion: Motion = null
  private def moves: Motion = {
    if (motion == null) motion = new Motion(view, () => animators())
    motion
  }

  /** Each track an animator of the view's own properties, repeating: a position moves the translation the node is
    * placed by, from where it is placed. */
  /** What the motion is for, told when it is set (a Roku logs it for everyui_debug_motion; the idle audit here). */
  def setMotionOwner(owner: () => String): Unit = moves.owner = owner
  def setMotion(motion: List[everyui.motion.Track]): Unit = {
    tracks = motion
    animate()
  }

  private def animate(): Unit = if (tracks.nonEmpty || motion != null) moves.set(tracks.nonEmpty)

  private def animators(): List[android.animation.Animator] = {
    import android.animation.{Keyframe, ObjectAnimator, PropertyValuesHolder, ValueAnimator}
    import everyui.motion.Track
    tracks.map { track =>
      def holder(name: String, value: everyui.motion.Keyframe => Double): PropertyValuesHolder =
        PropertyValuesHolder.ofKeyframe(name, track.keys.map(k => Keyframe.ofFloat(k.at.toFloat, value(k).toFloat))*)
      val holders = track.property match {
        case Track.Position => List(holder("translationX", k => left + k.a), holder("translationY", k => restY + k.b))
        case Track.Rotation => List(holder("rotation", k => math.toDegrees(k.a)))
        case Track.Opacity => List(holder("alpha", k => k.a))
        case _ => List(holder("scaleX", k => k.a), holder("scaleY", k => k.b))
      }
      val animator = ObjectAnimator.ofPropertyValuesHolder(view, holders*)
      animator.setDuration((track.seconds * 1000.0).toLong)
      animator.setStartDelay((track.delay * 1000.0).toLong)
      animator.setRepeatCount(ValueAnimator.INFINITE)
      animator.setInterpolator(new android.view.animation.LinearInterpolator)
      animator
    }
  }

  /** Each line of wrapped text `advance` pixels below the last: the gap the face leaves, opened or closed to that. */
  def setLineHeight(advance: Double): Unit = view match {
    case t: TextView =>
      lineAdvance = advance
      spaceLines(t)
    case _ => ()
  }

  private var lineAdvance: Double = 0.0

  /** Slanted or upright (the face set again, `setFont`); the measured width is the slanted words'. */
  def setItalic(on: Boolean): Unit = if (on != italic) {
    italic = on
    setFont(fontFamily, fontWeight, fontSize)
  }
  private var italic: Boolean = false
  private var fontFamily: String = ""
  private var fontWeight: Int = 400

  /** Extra space after each letter, in pixels. Android spaces letters in ems, so it is the pixels over the size (which
    * `FontFeature` sets first); the measured width takes it. */
  /** A variable face's optical sizing and axes: a browser's to set (`FontFeature.opticalSizing`); drawn here as the
    * platform draws the face. */
  def setFontAxes(opticalSizing: String, variations: String): Unit = ()
  def setLetterSpacing(px: Double): Unit = view match {
    case t: TextView =>
      t.setLetterSpacing(if (fontSize > 0.0) (px / fontSize).toFloat else 0f)
      laidOutText = null
    case _ => ()
  }

  /** The TextView's own line spacing, to the advance asked for at its current face and size (which it depends on). */
  private def spaceLines(t: TextView): Unit = if (lineAdvance > 0.0) {
    val metrics = t.getPaint.getFontMetrics
    t.setLineSpacing((lineAdvance - (metrics.descent - metrics.ascent)).toFloat, 1.0f)
    laidOutAt = -1 // the words take a different height now
  }
  /** A link is followed by its click; there is no address to show. */
  def setHref(href: String, newTab: Boolean): Unit = ()
  /** A drop-down draws itself here (`everyui.component.Select`): the node is its words. */
  def setOptions(labels: List[String]): Unit = ()
  /** A drop-down's look, its open list in the theme's colours: a browser's only, a drop-down drawing itself here. */
  def setDropDown(background: String, text: String, highlight: String, chosen: String, edge: String,
                  inset: Double): Unit = ()
  /** Styled runs as spans of one text: each run's colour, marking, weight, slant, size, strike and underline, and a
    * link followed by a tap (its family is the paragraph's). */
  def setRuns(runs: List[everyui.text.TextRun], follow: String => Unit): Unit = view match {
    case t: TextView =>
      val text = new android.text.SpannableStringBuilder()
      val span: (Any, Int, Int) => Unit = (what, from, to) =>
        text.setSpan(what, from, to, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      runs.foreach { run =>
        val from = text.length
        text.append(run.text)
        val to = text.length
        if (to > from) {
          if (run.color.nonEmpty) span(new android.text.style.ForegroundColorSpan(RenderNode.color(run.color)), from, to)
          if (run.background.nonEmpty)
            span(new android.text.style.BackgroundColorSpan(RenderNode.color(run.background)), from, to)
          val bold = run.weight >= 600
          // the family's own italic where it has one, rather than its upright letters slanted
          val slanted =
            if (run.italic) everyui.font.Faces.italic(face.family, if (run.weight > 0) run.weight else face.weight) else None
          slanted.foreach(f => span(new FaceSpan(Host.typeface(f)), from, to))
          if (bold || (run.italic && slanted.isEmpty)) span(new android.text.style.StyleSpan(
            if (bold && run.italic && slanted.isEmpty) Typeface.BOLD_ITALIC else if (bold) Typeface.BOLD
            else Typeface.ITALIC), from, to)
          if (run.size > 0.0) span(new android.text.style.AbsoluteSizeSpan(math.round(run.size).toInt), from, to)
          run.letterSpacing.foreach(px => span(new SpacingSpan(px), from, to))
          // a picture in place of the words, drawn again once it is in
          if (run.image.nonEmpty) span(new PictureSpan(run.image, () => t.invalidate()), from, to)
          if (run.strike) span(new android.text.style.StrikethroughSpan(), from, to)
          if (run.underline) span(new android.text.style.UnderlineSpan(), from, to)
          // a link followed by a tap on it; its colour and underline are the run's own
          if (run.href.nonEmpty) {
            val href = run.href
            span(new android.text.style.ClickableSpan {
              override def onClick(widget: android.view.View): Unit = follow(href)
              override def updateDrawState(paint: android.text.TextPaint): Unit = ()
            }, from, to)
          }
        }
      }
      // a tap reaches a link's span only through a movement method, which leaves the focus to the focus layer
      if (runs.exists(_.href.nonEmpty) && !t.getMovementMethod.isInstanceOf[android.text.method.LinkMovementMethod]) {
        t.setMovementMethod(android.text.method.LinkMovementMethod.getInstance())
        t.setHighlightColor(android.graphics.Color.TRANSPARENT)
        t.setFocusable(false)
      }
      t.setText(FaceSpan.fallen(text, t.getPaint))
      if (textDirection == "auto") applyAlign()
      // as many lines as the runs are written as, where they are not wrapped (see setText)
      if (!wrapping) {
        val lines = RenderNode.lineCount(text)
        t.setHorizontallyScrolling(lines <= 1)
        t.setMaxLines(lines)
      }
    case _ => ()
  }

  private var gradientAt: Array[Double] = Array.empty
  private var gradientAngle: Double = 0.0

  private def paintBackground(): Unit = {
    val quarter = ((math.round(gradientAngle / 90.0) % 4 + 4) % 4).toInt
    if (gradient.length >= 2 && radial.isEmpty && radius == 0f && borderWidth == 0 && math.abs(gradientAngle / 90.0 - math.round(gradientAngle / 90.0)) < 0.01)
      view.setBackground(RenderNode.strip(gradient, gradientAt, quarter))
    else paintShape()
    // the app's own ground, opaque, over the whole window: the window's launch background under it is never seen
    // again, and drawing it anyway was a whole screen's worth of the GPU's time in every frame. With the washes kept
    // in a layer (ScreenManager.washes), a Google TV Streamer's GPU went from 12ms a frame to 8 or 9
    if ((view eq Host.root) && Host.activity != null) {
      val opaque =
        if (gradient.length >= 2) gradient.forall(c => android.graphics.Color.alpha(c) == 255)
        else android.graphics.Color.alpha(if (gradient.length == 1) gradient(0) else background) == 255
      if (opaque && radius == 0f) Host.activity.getWindow.setBackgroundDrawable(null)
    }
  }

  // A square gradient is a strip of it stretched over the box: a weak TV GPU drew three full-screen gradient shapes at
  // a tenth of the frame rate it draws the same three bitmaps
  private def paintShape(): Unit = {
    val drawable = new android.graphics.drawable.GradientDrawable()
    if (gradient.length >= 2) {
      drawable.setOrientation(orientation)
      // where the stops lie, on Android 10 on; before it evenly apart
      if (android.os.Build.VERSION.SDK_INT >= 29) drawable.setColors(gradient, gradientAt.map(_.toFloat))
      else drawable.setColors(gradient)
      radial.foreach { case (cx, cy, r) =>
        drawable.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT)
        drawable.setGradientCenter(cx, cy)
        drawable.setGradientRadius(r)
      }
    }
    else drawable.setColor(if (gradient.length == 1) gradient(0) else background)
    drawable.setCornerRadius(radius)
    if (borderWidth > 0) drawable.setStroke(borderWidth, borderColor)
    view.setBackground(drawable)
  }

  /** Every node is placed by its translation, so there is no position type to set. */
  /** Whether the pointer is over it as what is under a still one moves: the platform's enter and leave alone here. */
  def onOver(told: Boolean => Unit): Unit = ()
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click", "menu") with its root coordinates.
    * Touches drive down/move/up, a click on an up that stays on the view, and the menu on a long press or a mouse's
    * secondary button; a mouse or a hover drives enter/leave. */
  def onPointer(kind: PointerKind, f: (Double, Double) => Unit): Unit = {
    pointer += kind -> f
    if (kind == PointerKind.Click) PressOwner.mark(view)
    if (kind == PointerKind.Wheel) view.setOnGenericMotionListener { (_, e) =>
      if (e.getAction == MotionEvent.ACTION_SCROLL) {
        f(-e.getAxisValue(MotionEvent.AXIS_HSCROLL) * 40.0, -e.getAxisValue(MotionEvent.AXIS_VSCROLL) * 40.0)
        true
      }
      else false
    }
    listen()
  }

  /** The view's touches and hovers heard, once. */
  private[render] def listen(): Unit = if (!listening) {
    listening = true
    view.setOnTouchListener((_, e) => touch(e))
    view.setOnHoverListener { (_, e) =>
      if (e.getAction == MotionEvent.ACTION_HOVER_ENTER) fire(PointerKind.Enter, e)
      else if (e.getAction == MotionEvent.ACTION_HOVER_EXIT) fire(PointerKind.Leave, e)
      false
    }
  }

  /** What hears every touch on the view before its pointer events are made of it: a pinch (Gestures). */
  private[render] var touchHook: Option[MotionEvent => Unit] = None

  private val pointer = mutable.Map.empty[PointerKind, (Double, Double) => Unit]
  private var listening = false
  private def fire(kind: PointerKind, e: MotionEvent): Unit = {
    // what the pointer holds, for the event about to be heard: a finger is the primary button; a mouse says its own
    val buttons = e.getButtonState
    Host.pointerButton = if ((buttons & MotionEvent.BUTTON_SECONDARY) != 0) 2 else if ((buttons & MotionEvent.BUTTON_TERTIARY) != 0) 1 else 0
    // a finger or a stylus on the screen, not a mouse or a trackpad's pointer
    Host.pointerFinger = e.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE
    val meta = e.getMetaState
    Host.modifiers = everyui.event.Modifiers((meta & android.view.KeyEvent.META_CTRL_ON) != 0,
      (meta & android.view.KeyEvent.META_META_ON) != 0, (meta & android.view.KeyEvent.META_ALT_ON) != 0,
      (meta & android.view.KeyEvent.META_SHIFT_ON) != 0)
    val at = Array(0, 0)
    Host.root.getLocationOnScreen(at)
    pointer.get(kind).foreach(f => f((e.getRawX - at(0)) / Host.touchScale, (e.getRawY - at(1)) / Host.touchScale))
  }
  // A finger held still on a view that has a context menu (something listens for PointerKind.Menu) asks for it, as a
  // long press does anywhere on Android: where it went down, after the platform's long-press time, unless it moved
  // further than a tap may or was lifted first. What it asked for is not also a click as it lifts.
  private var pressFrom: (Float, Float) = (0f, 0f)
  // where the touch went down, and whether a finger has since gone further from there than a tap may
  private var downAt: (Float, Float) = (0f, 0f)
  private var strayed = false
  private var pressHeld: Option[Runnable] = None
  private var pressedLong = false
  private def letGo(): Unit = {
    pressHeld.foreach(r => view.removeCallbacks(r))
    pressHeld = None
  }
  // a move or the lift of a touch this holds, heard at the root too, as the web's and the desktop's are heard there on
  // their way up: what follows a drag from the root (Pointers.track: a list's rows under a finger, a scroll's thumb)
  // heard nothing on Android, where the view a finger goes down on holds the whole touch, and a chat could not be swiped
  private def toRoot(kind: PointerKind, e: MotionEvent): Unit = {
    val root = RenderNode.rootNode
    if (root != null && !(root eq this)) root.fire(kind, e)
  }
  private def touch(e: MotionEvent): Boolean = {
    touchHook.foreach(f => f(e))
    val action = e.getActionMasked
    if (action == MotionEvent.ACTION_DOWN) {
      // what drags itself keeps the drag from a scroll it is in (Android lets go of the ask as the touch ends)
      if (keepsDrag && view.getParent != null) view.getParent.requestDisallowInterceptTouchEvent(true)
      fire(PointerKind.Down, e)
      downAt = (e.getRawX, e.getRawY)
      strayed = false
      pressedLong = false
      letGo()
      if (pointer.contains(PointerKind.Menu)) {
        // a mouse's secondary button asks at once
        if ((e.getButtonState & MotionEvent.BUTTON_SECONDARY) != 0) {
          pressedLong = true
          fire(PointerKind.Menu, e)
        }
        else {
          pressFrom = (e.getRawX, e.getRawY)
          val held: Runnable = () => {
            pressHeld = None
            pressedLong = true
            val at = Array(0, 0)
            Host.root.getLocationOnScreen(at)
            pointer.get(PointerKind.Menu).foreach(f => f((pressFrom._1 - at(0)) / Host.scale, (pressFrom._2 - at(1)) / Host.scale))
          }
          pressHeld = Some(held)
          view.postDelayed(held, android.view.ViewConfiguration.getLongPressTimeout.toLong)
        }
      }
    }
    else if (action == MotionEvent.ACTION_MOVE) {
      val slop = android.view.ViewConfiguration.get(view.getContext).getScaledTouchSlop
      if (math.abs(e.getRawX - pressFrom._1) > slop || math.abs(e.getRawY - pressFrom._2) > slop) letGo()
      if (math.abs(e.getRawX - downAt._1) > slop || math.abs(e.getRawY - downAt._2) > slop) strayed = true
      fire(PointerKind.Move, e)
      toRoot(PointerKind.Move, e)
    }
    else if (action == MotionEvent.ACTION_UP) {
      letGo()
      fire(PointerKind.Up, e)
      toRoot(PointerKind.Up, e)
      // nor where it moved further than a tap may, onto something in it that answers a click itself (PressOwner)
      val slop = android.view.ViewConfiguration.get(view.getContext).getScaledTouchSlop
      val moved = math.abs(e.getRawX - downAt._1) > slop || math.abs(e.getRawY - downAt._2) > slop
      // a finger lifted where it went down is a tap on what it went down on, though that moved from under it meanwhile:
      // a phone's keyboard closing as a button under a field was tapped moved the form, and the tap was no click
      val inside = !moved || (e.getX >= 0 && e.getY >= 0 && e.getX <= view.getWidth && e.getY <= view.getHeight)
      // and a finger that went further than a tap may, even once and back, was a scroll or a swipe, not a tap, as on
      // Android's own lists: one dragged down a picker's rows chose the row it lifted from (a mouse may still drag on a
      // button and click it, as a desktop's does)
      val swiped = strayed && e.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE
      if (inside && !pressedLong && !swiped && !(moved && PressOwner.elsewhere(view, e.getRawX, e.getRawY)))
        fire(PointerKind.Click, e)
    }
    else if (action == MotionEvent.ACTION_CANCEL) {
      letGo()
      fire(PointerKind.Up, e)
      toRoot(PointerKind.Up, e)
    }
    true
  }

  /** The shared ScrollContainer shifts the content; the node only clips, and takes a finger's drag along it from what
    * the finger went down on (`NodeGroup.onInterceptTouchEvent`): it hears the finger go down where it did, then the
    * move it was taken on, and every move after as its own. */
  def setScrollable(vertical: Boolean, horizontal: Boolean): Unit = view match {
    case g: NodeGroup =>
      g.scrollsAlong = if (vertical) 1 else if (horizontal) 2 else 0
      g.takeDrag = (moved, x, y) => {
        val down = MotionEvent.obtain(moved.getDownTime, moved.getEventTime, MotionEvent.ACTION_DOWN, x, y, 0)
        try touch(down) finally down.recycle()
        touch(moved)
      }
    case _ => ()
  }
  def setScrollLeft(x: Double): Unit = ()
  /** The shared ScrollContainer moves the content itself, so the node never scrolls on its own. */
  def onScrolled(f: () => Unit): Unit = ()
  /** A scroll that runs from its left in every language: the shared ScrollContainer counts it so here. */
  def setLeftToRight(): Unit = ()
  /** The shared ScrollContainer draws no scrollbar here. */
  def setScrollbarColor(hex: String): Unit = ()
  def setScrollbarShown(shown: Boolean): Unit = ()
  /** Its ends faded to clear over `start` and `end` (its left and right, or its top and bottom), showing what is
    * behind: the view's own fading edges, as Android's scrolling views fade theirs, told which ends have more
    * (`NodeGroup.fades`) since the content is moved within it rather than scrolled by it. */
  def setEdgeFade(horizontal: Boolean, start: Double, end: Double): Unit = view match {
    case g: NodeGroup =>
      val on = start > 0.0 || end > 0.0
      g.fades = (if (start > 0.0) 1f else 0f, if (end > 0.0) 1f else 0f)
      if (on) {
        g.setWillNotDraw(false)
        g.setFadingEdgeLength(math.round(math.max(start, end)).toInt)
      }
      g.setVerticalFadingEdgeEnabled(on && !horizontal)
      g.setHorizontalFadingEdgeEnabled(on && horizontal)
      g.invalidate()
    case _ => ()
  }
  def setScrollTop(y: Double): Unit = ()
  /** How far the node has scrolled its own overflow: never, here, where the ScrollContainer shifts the content. */
  def scrolled(horizontal: Boolean): Double = 0.0
  def scrollSmoothly(to: Double, horizontal: Boolean): Unit = ()
  /** Whether the platform draws this: what a scroll has out of sight is not drawn at all. Its place and its size are
    * kept (INVISIBLE, not GONE), since the layout is ours and not the view system's. */
  def setVisible(visible: Boolean): Unit = {
    val next = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE
    if (view.getVisibility != next) {
      view.setVisibility(next)
      // what moves in it, or is it, runs only while it can be seen (Motion)
      Motion.visibilityChanged()
    }
  }
  def setOpacity(opacity: Double): Unit = view.setAlpha(opacity.toFloat)

  def setClip(width: Double, height: Double): Unit = view match {
    case g: ViewGroup =>
      ownClip = true
      clipAtEdge(g, true)
    case _ => ()
  }
  // cut to its box by its container (`Container.clipsToBox`), as `setClip` cuts it, never undoing a clip of its own
  private var ownClip = false
  def setBoxClip(on: Boolean, width: Double, height: Double): Unit = view match {
    case g: ViewGroup if !ownClip => clipAtEdge(g, on)
    case _ => ()
  }
  // What it draws, its children with it, cut at its own edge, as the web's overflow and the desktop's clip cut it: to
  // its outline, its background's rounded box, or its bounds where it has none. Never by Android's clipChildren, which
  // cuts each child at the child's own edge instead: a badge hanging out of a button in a plain column, in a rail that
  // clips, was cut at the column's edge though the rail had room for it, and showed whole on the web. So a container
  // of ours never clips its children to themselves, clipping or not; another kind of group (a video's frame) keeps its
  // own rule.
  private def clipAtEdge(g: ViewGroup, on: Boolean): Unit = {
    if (g.isInstanceOf[NodeGroup]) {
      g.setClipChildren(false)
      g.setClipToPadding(false)
    }
    g.setClipToOutline(on)
  }
  def setTooltip(text: String): Unit =
    if (android.os.Build.VERSION.SDK_INT >= 26) view.setTooltipText(if (text.isEmpty) null else text)
  // what TalkBack reads after the view's name, and whether accessibleFocus's delegate (which says it too) is on the view
  private var hint = ""
  private var focusDelegate = false
  /** What TalkBack reads after the view's name (a list row's tip), as its hint (Android 8 on). */
  /** No guess at the words' width here: they are measured. */
  def textWidthGuess(): Double = -1.0
  def setDescription(text: String): Unit = if (android.os.Build.VERSION.SDK_INT >= 26) {
    hint = text
    if (!focusDelegate) view.setAccessibilityDelegate(if (text.isEmpty) null else new View.AccessibilityDelegate {
      override def onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo): Unit = {
        super.onInitializeAccessibilityNodeInfo(host, info)
        if (hint.nonEmpty) info.setHintText(hint)
        if (wrong) info.setContentInvalid(true)
      }
    })
  }
  /** A drawing's figures (everyui.draw.Drawing), where this is one. */
  def setFigures(figures: List[everyui.draw.Figure]): Unit = view match {
    case d: DrawingView => d.figures(figures)
    case _ => ()
  }
  /** Only a web page scrolls under a finger that drags: nothing to do here. */
  /** A finger dragged on it drags it, rather than scrolling what it is in: as it goes down, a scroll holding it is told
    * not to take the drag (`NodeGroup.onInterceptTouchEvent`), for that touch. */
  def setTouchDrags(on: Boolean): Unit = keepsDrag = on
  private var keepsDrag = false
  def setTouchScrolls(on: Boolean): Unit = ()
  /** Only a page is read for how far a list has scrolled: nothing to do here. */
  def setScrolled(at: Double, furthest: Double): Unit = ()
  /** Its words selectable: no text is selected with a pointer here (`Platform.selectsText`). */
  def setSelectable(on: Boolean): Unit = ()
  def selectText(): Unit = ()
  def holdsSelection(): Boolean = false
  /** What it is when selected words are copied (`Component.copyAs`): no text is selected here, so nothing to do. */
  def setCopyAs(tag: String): Unit = ()
  /** A mouse on Android keeps its own arrow. */
  def setCursor(name: String): Unit = ()

  def setPositionType(name: String): Unit = ()

  /** `"normal"` (or `"pre-wrap"`, `"pre-line"`: a TextView keeps the words' own line breaks anyway) wraps a text node
    * at its width; anything else breaks it only where the words themselves do. */
  def setWhiteSpace(value: String): Unit = view match {
    case _: EditText => ()
    case t: TextView =>
      wrapping = value == "normal" || value == "pre-wrap" || value == "pre-line"
      if (wrapping) {
        t.setMaxLines(Integer.MAX_VALUE)
        t.setHorizontallyScrolling(false)
      } else {
        // not wrapping still means as many lines as the words were written with (see setText)
        val lines = RenderNode.lineCount(t.getText)
        t.setHorizontallyScrolling(lines <= 1)
        t.setMaxLines(lines)
      }
    case _ => ()
  }

  /** A text field's contents. */
  def getValue(): String = view match {
    case e: EditText => e.getText.toString
    case _ => ""
  }
  def setValue(value: String): Unit = view match {
    case e: EditText =>
      e.setText(value)
      e.setSelection(value.length)
    case _ => ()
  }
  def setPlaceholder(text: String): Unit = view match {
    case e: EditText => e.setHint(text)
    case _ => ()
  }
  /** What the field asks for, by its `autocomplete` token (`everyui.component.types.Autofill.token`), as the Autofill
    * Framework hears it (Android 8 and later): its `autofillHints`, the keyboard for it (`inputType`: an email address,
    * a phone number, digits, a password), and whether it is to be filled at all ("off" is not). Every autofill service
    * the user picked (Google's, 1Password, LastPass, Bitwarden) goes by these. `section`, `name` and `passkey` are the
    * web's; Android has nothing for them. `numeric` makes it a number field whatever the token, a hidden one a number
    * password (`TYPE_NUMBER_VARIATION_PASSWORD`), which takes digits alone. */
  def setAutofill(token: String, section: String, name: String, passkey: Boolean, literal: Boolean,
    numeric: Boolean): Unit = view match {
    case e: EditText if android.os.Build.VERSION.SDK_INT >= 26 =>
      import android.text.InputType as T
      if (token == "off") e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO)
      else if (token.isEmpty) e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_AUTO)
      else {
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES)
        val hint = RenderNode.autofillHint(token)
        if (hint.nonEmpty) e.setAutofillHints(hint)
      }
      val lines = e.getInputType & T.TYPE_TEXT_FLAG_MULTI_LINE
      val secret = (e.getInputType & T.TYPE_MASK_VARIATION) == T.TYPE_TEXT_VARIATION_PASSWORD ||
        (e.getInputType & T.TYPE_MASK_VARIATION) == T.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
        (e.getInputType & (T.TYPE_MASK_CLASS | T.TYPE_MASK_VARIATION)) ==
          (T.TYPE_CLASS_NUMBER | T.TYPE_NUMBER_VARIATION_PASSWORD)
      val password = secret || token == "current-password" || token == "new-password"
      val kind = token match {
        // digits on the number pad, hidden as a password is where it is one (a PIN)
        case _ if numeric => T.TYPE_CLASS_NUMBER | (if (password) T.TYPE_NUMBER_VARIATION_PASSWORD else 0)
        // a password and a username as a stock sign-in's are, not as a web page's: 1Password offered at once for those
        case "current-password" | "new-password" => T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_PASSWORD
        case _ if secret => T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_PASSWORD
        case "email" => T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        case "username" => T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        case "tel" => T.TYPE_CLASS_PHONE
        case "url" => T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_URI
        // a code of letters and digits on the whole keyboard, as typed: nothing suggested or corrected, no capitals
        case "one-time-code-text" =>
          T.TYPE_CLASS_TEXT | T.TYPE_TEXT_FLAG_NO_SUGGESTIONS | T.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        case "one-time-code" | "cc-number" | "cc-csc" => T.TYPE_CLASS_NUMBER
        case "name" | "given-name" | "additional-name" | "family-name" | "nickname" | "cc-name" =>
          T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_PERSON_NAME | T.TYPE_TEXT_FLAG_CAP_WORDS
        case "street-address" | "address-line1" | "address-line2" | "address-level1" | "address-level2" | "country" =>
          T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_POSTAL_ADDRESS | T.TYPE_TEXT_FLAG_CAP_WORDS
        // held literal: no suggestions or corrections, and no capitals (no CAP flag), told as plain text rather than as
        // a password, the keyboards' usual trick, which would mislead autofill (a path, a command, code)
        case _ if literal => T.TYPE_CLASS_TEXT | T.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        case _ => T.TYPE_CLASS_TEXT
      }
      if (e.getInputType != (kind | lines)) {
        // a password's type would set its own face: the field keeps the one it was given
        val face = e.getTypeface
        e.setInputType(kind | lines)
        e.setTypeface(face)
      }
    case _ => ()
  }
  /** A form (`everyui.form.Form`) is a container here, sent by the app alone. */
  def onSubmit(f: () => Unit): Unit = ()
  /** A form is done with: its autofill session is committed, when accepted, which is the service's cue to offer to
    * save what was typed (in an app of one activity nothing else ends the session), or cancelled. */
  def formDone(saved: Boolean): Unit = if (android.os.Build.VERSION.SDK_INT >= 26) {
    val manager = Host.activity.getSystemService(classOf[android.view.autofill.AutofillManager])
    if (manager != null) { if (saved) manager.commit() else manager.cancel() }
  }
  // no hidden field here: the service saves against the username field it saw
  def setHiddenUsername(username: String): Unit = ()
  /** What the keyboard's action key says it does ("done", "search", "next", "go", "send"): the key a TV's full-screen
    * keyboard shows beside its letters. */
  def setInputAction(action: String): Unit = view match {
    case e: EditText =>
      import android.view.inputmethod.EditorInfo
      val code = if (action == "search") EditorInfo.IME_ACTION_SEARCH else if (action == "next") EditorInfo.IME_ACTION_NEXT
        else if (action == "go") EditorInfo.IME_ACTION_GO else if (action == "send") EditorInfo.IME_ACTION_SEND
        else EditorInfo.IME_ACTION_DONE
      e.setImeOptions(code | EditorInfo.IME_FLAG_NO_EXTRACT_UI)
    case _ => ()
  }
  /** Every edit, with the new contents. */
  def onValue(f: String => Unit): Unit = view match {
    case e: EditText => e.addTextChangedListener(new android.text.TextWatcher {
      override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = ()
      override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit = ()
      override def afterTextChanged(s: android.text.Editable): Unit = f(s.toString)
    })
    case _ => ()
  }
  /** A paste into the field (its menu's Paste, Ctrl+V, a keyboard's), put to `f` with its words before they land
    * (`FieldView.pasting`). */
  def onPaste(f: String => everyui.component.types.PasteAction): Unit = view match {
    case e: FieldView => e.pasting = Some(f)
    case _ => ()
  }
  /** The pictures and files of a paste into the field, and a keyboard's pictures, handed to `f` where `wanted` says
    * (`FieldView.pastingFiles`). */
  def onPasteFiles(wanted: () => Boolean, f: List[everyui.file.PickedFile] => Unit): Unit = view match {
    case e: FieldView => e.pastingFiles = Some((wanted, f))
    case _ => ()
  }
  /** The field taking the keyboard itself (a tap on it). */
  def onInputFocus(f: () => Unit): Unit = view match {
    case e: EditText => e.setOnFocusChangeListener((_, has) => if (has) f())
    case _ => ()
  }
  /** The field's keyboard finished: its Done (or Next, or Go), or Back putting it away. */
  def onInputDone(f: () => Unit): Unit = view match {
    case e: FieldView =>
      e.done = f
      RenderNode.acting(e)
    case _ => ()
  }
  /** The keyboard's action key (Done, Search, Next), after `onInputDone` has heard the keyboard put away. */
  def onInputAction(f: () => Unit): Unit = view match {
    case e: FieldView =>
      e.action = f
      RenderNode.acting(e)
    case _ => ()
  }
  /** All of what the field holds chosen, so what is typed next replaces it. */
  def selectAllInput(): Unit = view match {
    case e: EditText => e.selectAll()
    case _ => ()
  }
  /** Where the field's caret is, in characters from the start: the start of any selection. */
  def getCaret(): Int = view match {
    case e: EditText => math.max(0, e.getSelectionStart)
    case _ => 0
  }
  /** The field's caret put at `at`, nothing chosen. */
  def setCaret(at: Int): Unit = view match {
    case e: EditText => e.setSelection(math.max(0, math.min(at, e.length())))
    case _ => ()
  }
  /** Where what is chosen in the field starts and ends, in characters: the caret, both, where nothing is. */
  def getSelectionStart(): Int = getCaret()
  def getSelectionEnd(): Int = view match {
    case e: EditText => math.max(0, e.getSelectionEnd)
    case _ => 0
  }
  /** `start` to `end` chosen in the field. */
  def setSelection(start: Int, end: Int): Unit = view match {
    case e: EditText =>
      val n = e.length()
      e.setSelection(math.max(0, math.min(start, n)), math.max(0, math.min(end, n)))
    case _ => ()
  }
  /** `start` to `end` replaced by `words` in the field's own text, as a keyboard puts what it types; the caret after
    * them. Android keeps no undo of its own for a field to take it back with. */
  def replaceInput(start: Int, end: Int, words: String): Unit = view match {
    case e: EditText =>
      val n = e.length()
      val from = math.max(0, math.min(start, n))
      e.getText.replace(from, math.max(from, math.min(end, n)), words)
      e.setSelection(math.min(from + words.length, e.length()))
    case _ => ()
  }
  /** What is chosen in the field, told as it changes (FieldView.onSelectionChanged). */
  def onSelection(f: (Int, Int) => Unit): Unit = view match {
    case e: FieldView => e.selecting = f
    case _ => ()
  }
  /** Out of use, or back in it: a field takes no typing and TalkBack reads it as disabled. Other views are only drawn
    * dimmed by the app: disabling one would stop the touches that move the focus to it. */
  // out of use (`Component.disabled`): TalkBack reads a button so, though it stays where the focus can reach it
  private var outOfUse = false
  def setDisabled(disabled: Boolean): Unit = {
    outOfUse = disabled
    view match {
      case e: EditText => e.setEnabled(!disabled)
      case _ => ()
    }
  }
  /** Read-only, or editable again: the field's words can be chosen and copied, but it takes no typing or paste and
    * brings up no keyboard, and TalkBack reads it as a field that cannot be edited (`FieldView.readOnly`). */
  def setReadOnly(on: Boolean): Unit = view match {
    case e: FieldView => e.readOnly(on)
    case _ => ()
  }
  /** Give the field the keyboard (and the soft keyboard where there is no other), or take both away. */
  def focusInput(on: Boolean): Unit = view match {
    case e: FieldView =>
      val keyboard = Host.activity.getSystemService(Context.INPUT_METHOD_SERVICE).asInstanceOf[android.view.inputmethod.InputMethodManager]
      if (on) e.edit(true)
      if (on && !e.hasFocus) {
        e.requestFocus()
        e.post(() => keyboard.showSoftInput(e, 0))   // once the focus has landed, or the keyboard ignores it
      }
      else if (!on) keyboard.hideSoftInputFromWindow(e.getWindowToken, 0)
      if (!on && e.hasFocus) {
        // to the root rather than clearFocus, which hands Android's focus straight back to the first field
        Host.root.setFocusableInTouchMode(true)
        Host.root.requestFocus()
      }
      if (!on) e.edit(false)
    case _ => ()
  }

  /** A shadow behind the letters (a subtitle's edge, as a device's caption settings ask for one); an empty colour, none. */
  /** An edge round the letters, `width` each side, or none for "" ([[OutlinedText]]). */
  def setTextOutline(width: Double, hex: String): Unit = view match {
    case t: OutlinedText =>
      if (hex.isEmpty) t.outline(0f, 0)
      else t.outline((width * Host.scale).toFloat, android.graphics.Color.parseColor(
        if (hex.length == 9) "#" + hex.substring(7) + hex.substring(1, 7) else hex))
    case _ => ()
  }

  def setTextShadow(x: Double, y: Double, blur: Double, hex: String): Unit = view match {
    case t: TextView =>
      if (hex.isEmpty) t.setShadowLayer(0f, 0f, 0f, 0)
      else t.setShadowLayer(math.max(0.01, blur * Host.scale).toFloat, (x * Host.scale).toFloat, (y * Host.scale).toFloat,
        android.graphics.Color.parseColor(
          // Android reads a translucent colour alpha first, where the CSS form this comes in puts it last
          if (hex.length == 9) "#" + hex.substring(7) + hex.substring(1, 7) else hex))
    case _ => ()
  }

  /** Read out by TalkBack: a label as the view's description, and a heading as a heading, where the platform knows
    * one (Android 9 on). */
  def setSemantics(role: String, level: Int, label: String): Unit = {
    view.setContentDescription(if (label.isEmpty) null else label)
    if (android.os.Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(role == "heading")
  }

  // whether what it holds is wrong (`setInvalid`), and whether it is being updated (`setUpdating`)
  private var wrong = false
  private var busyNow = false
  /** What it holds is wrong: TalkBack says so, and why from its description (`Component.invalid`), where it reads
    * one. */
  def setInvalid(on: Boolean): Unit = wrong = on
  /** Being updated: TalkBack hears "busy" in its state (Android 11 on). */
  def setUpdating(on: Boolean): Unit = {
    busyNow = on
    sayState()
  }
  // the state set last (`setSemanticState`), said with "busy" while it is being updated
  private var stateSaid: String = null
  private def sayState(): Unit = if (android.os.Build.VERSION.SDK_INT >= 30) view.setStateDescription(
    if (busyNow) List(Option(stateSaid).getOrElse(""), everyui.i18n.Words.busy).filter(_.nonEmpty).mkString(", ")
    else stateSaid)

  /** Where a slider stands, or whether what a button opens is open, read out by TalkBack as the view's state (Android 11
    * on). */
  def setSemanticState(range: Option[everyui.component.types.SemanticRange], checked: Option[Boolean],
                       expanded: Option[Boolean], selected: Option[Boolean], sort: String,
                       current: Boolean, description: String): Unit = {
    selected.foreach(s => view.setSelected(s))
    stateSaid = range.map(_.text)
      .orElse(expanded.map(open => if (open) "expanded" else "collapsed"))
      .orElse(if (sort.isEmpty) None else Some("sorted " + sort))
      .orElse(if (current) Some("current") else None).orNull
    sayState()
  }

  /** A ring in `hex` round the view's edge, where the focus layer draws one for it (`Focus.ring`), as a stroke over
    * what it draws; "" takes it away. */
  def setFocusRing(hex: String, radius: Double): Unit = if (android.os.Build.VERSION.SDK_INT >= 23) {
    if (hex.isEmpty) {
      if (focusRing != null && (view.getForeground eq focusRing)) view.setForeground(null)
      focusRing = null
    }
    else if (view.getForeground == null || (view.getForeground eq focusRing)) {
      val ring = new android.graphics.drawable.GradientDrawable()
      // in the app's units, as its edges and corners are (`setBorder`, `setRadius`)
      ring.setStroke(3, android.graphics.Color.parseColor(hex))
      ring.setCornerRadius(radius.toFloat)
      focusRing = ring
      view.setForeground(ring)
    }
  }
  private var focusRing: android.graphics.drawable.Drawable = null

  /** The row under a list's cursor (`VirtualList`), read out by TalkBack as it moves, where it is on. */
  def setActiveRow(row: RenderNode, name: () => String): Unit = if (RenderNode.reading) Platform.announce(name())
  def setAlt(text: String): Unit = view.setContentDescription(if (text.isEmpty) null else text)

  /** The focus landed here (the app's own focus, which Android's does not follow). With a screen reader on, this is
    * where TalkBack's focus goes: read out by its label, or else by the words inside it, as a button that its double
    * tap (or OK) presses. Without one, nothing is done: the checks and the delegate cost nothing on an ordinary run. */
  def accessibleFocus(click: () => Unit, name: () => String): Unit = if (RenderNode.reading) {
    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
    if (android.os.Build.VERSION.SDK_INT >= 28) view.setScreenReaderFocusable(true)
    focusDelegate = true
    view.setAccessibilityDelegate(new View.AccessibilityDelegate {
      override def onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo): Unit = {
        super.onInitializeAccessibilityNodeInfo(host, info)
        if (hint.nonEmpty && android.os.Build.VERSION.SDK_INT >= 26) info.setHintText(hint)
        info.setClassName("android.widget.Button")
        if (outOfUse) info.setEnabled(false)
        if (wrong) info.setContentInvalid(true)
        info.setFocusable(true)
        info.setClickable(true)
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
      }
      override def performAccessibilityAction(host: View, action: Int, args: android.os.Bundle): Boolean =
        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
          click()
          true
        }
        else super.performAccessibilityAction(host, action, args)
    })
    view.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
  }

  /** "start", "center" or "end", by the reading direction (`Direction`). */
  def setTextAlign(align: String): Unit = {
    textAlign = align
    applyAlign()
  }

  /** Which way the words run: "" the app's, "auto" their own (the first letter with a direction, falling back to the
    * app's), "ltr" or "rtl". The side their lines start from follows. */
  def setTextDirection(value: String): Unit = {
    textDirection = value
    applyAlign()
  }

  /** A TextView breaks a word too long for its line where it must already. */
  def setWrapAnywhere(on: Boolean): Unit = ()
  /** Words that wrap are measured whole on one line here already, and wrap only at a width or a limit. */
  def setTextWhole(on: Boolean): Unit = ()
  /** Words cut short to fit (`TextView.maxWidth`, `maxLines`) are cut by measuring alone; words that wrap and are
    * given no width of their own wrap at `width` where they would run past it (kept to their box), the view as wide. */
  /** Whether words held to a limit (`setTextLimit`) wrap at it here: they do. */
  def wrapsToLimit(): Boolean = true
  def setTextLimit(width: Double, lines: Int): Unit = view match {
    case _: EditText => ()
    case t: TextView if wrapping && explicitWidth < 0.0 =>
      wrapLimit = 0.0
      measure(t)
      if (width > 0.0 && laidOutWidth > width) wrapLimit = width
      val params = view.getLayoutParams
      params.width = if (wrapLimit > 0.0) math.round(wrapLimit * EmbedView.scaleOf(view)).toInt
        else ViewGroup.LayoutParams.WRAP_CONTENT
      view.setLayoutParams(params)
    case _ => ()
  }
  /** Words measure as they will be drawn from the moment they are set. */
  def textMeasurable(ready: () => Unit): Boolean = true

  /** Text on the side the app reads from, whatever script its words are in: a TextView's own start is where its
    * first letter reads from, so a Latin line in Arabic sat on the left. A field is mirrored with the root already, so
    * its start stays logical. */
  private[render] def applyAlign(): Unit = view match {
    case e: EditText =>
      // words given a direction of their own (a path, an address, a query by its first letter) start on their own side
      e.setTextDirection(RenderNode.textDirection(textDirection))
      val own = textDirection.nonEmpty
      e.setTextAlignment(if (textAlign == "center") View.TEXT_ALIGNMENT_CENTER
        else if (textAlign == "end") { if (own) View.TEXT_ALIGNMENT_TEXT_END else View.TEXT_ALIGNMENT_VIEW_END }
        else if (own) View.TEXT_ALIGNMENT_TEXT_START else View.TEXT_ALIGNMENT_VIEW_START)
    case t: TextView =>
      t.setTextDirection(RenderNode.textDirection(textDirection))
      val rtl = everyui.text.Bidi.rightToLeft(textDirection, t.getText.toString, Host.rtl)
      val side = if (textAlign == "center") android.view.Gravity.CENTER_HORIZONTAL
        else if ((textAlign == "end") != rtl) android.view.Gravity.RIGHT else android.view.Gravity.LEFT
      t.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY)
      t.setGravity(android.view.Gravity.TOP | side)
    case _ => ()
  }

  /** The picture in one colour (`"#rrggbb"`), its transparency kept, or as it is for `""`. */
  def setTint(hex: String): Unit = view match {
    case i: ImageView =>
      if (hex.isEmpty) i.clearColorFilter()
      else i.setColorFilter(RenderNode.color(hex), android.graphics.PorterDuff.Mode.SRC_IN)
    case _ => ()
  }

  /** The picture the view draws, or none (which lets the one it was drawing go). */
  def setImageSource(uri: String): Unit = view match {
    case i: ImageView =>
      if (uri.isEmpty) i.setImageDrawable(null)
      else Host.bitmapThen(uri)(b => if (b != null) i.setImageBitmap(b))
    case _ => ()
  }

  /** A `TextView` draws its baseline `-ascent` (whole pixels) below its top: shift it so the baseline lands where the
    * shared line box puts it. */
  private def placeY(): Unit = {
    view.setTranslationY(restY.toFloat)
    moved()
  }
  // the group it is in, where that is laid out over its children's reach (NodeGroup.childMoved), told it moved
  private def moved(): Unit = view.getParent match {
    case g: NodeGroup => g.childMoved()
    case _ => ()
  }

  // Where the view sits down its parent: a line of text by its baseline
  private def restY: Double = view match {
    case _: EditText => top
    case t: TextView => top + face.baseline(fontSize) + t.getPaint.getFontMetricsInt.ascent
    case _ => top
  }
}

object RenderNode {
  /** The render root's node (`root`), which hears every touch's moves and lift as well (`toRoot`). */
  private[render] var rootNode: RenderNode = null
  /** A view's text direction for `setTextDirection`'s value: "auto" by the first letter with one (the app's where
    * there is none), "ltr", "rtl", or the app's for "". */
  def textDirection(value: String): Int = value match {
    case "auto" => if (Host.rtl) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
    case "ltr" => View.TEXT_DIRECTION_LTR
    case "rtl" => View.TEXT_DIRECTION_RTL
    case _ => View.TEXT_DIRECTION_INHERIT
  }

  /** The Autofill Framework's hint for an `autocomplete` token: View's own constants where it has one, else androidx's
    * `HintConstants` (the same strings, which every service knows); "" where neither names it. */
  def autofillHint(token: String): String = token match {
    case "username" => View.AUTOFILL_HINT_USERNAME
    case "current-password" => View.AUTOFILL_HINT_PASSWORD
    case "new-password" => "newPassword"
    case "one-time-code" | "one-time-code-text" => "smsOTPCode"
    case "email" => View.AUTOFILL_HINT_EMAIL_ADDRESS
    case "tel" => View.AUTOFILL_HINT_PHONE
    case "name" => View.AUTOFILL_HINT_NAME
    case "given-name" => "personGivenName"
    case "additional-name" => "personMiddleName"
    case "family-name" => "personFamilyName"
    case "street-address" => View.AUTOFILL_HINT_POSTAL_ADDRESS
    case "address-line1" => "streetAddress"
    case "address-line2" => "extendedAddress"
    case "address-level2" => "addressLocality"
    case "address-level1" => "addressRegion"
    case "postal-code" => View.AUTOFILL_HINT_POSTAL_CODE
    case "country" => "addressCountry"
    case "cc-name" => "creditCardHolderName"
    case "cc-number" => View.AUTOFILL_HINT_CREDIT_CARD_NUMBER
    case "cc-exp" => View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE
    case "cc-exp-month" => View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_MONTH
    case "cc-exp-year" => View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_YEAR
    case "cc-csc" => View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE
    case "bday" => "birthDateFull"
    case _ => ""
  }

  /** Whether a picture's address is one that is fetched (a download or a file), rather than one of the APK's own. */
  def fetched(uri: String): Boolean = uri.startsWith("http") || uri.startsWith("file:")

  /** A field's action key: the keyboard put away, then the action. */
  def acting(e: FieldView): Unit = e.setOnEditorActionListener((_, _, _) => {
    e.done()
    e.action()
    true
  })

  /** The maker and the model as one name, the maker left off where the model already starts with it ("Google Streamer",
    * not "Google Google Streamer"). `named` is for what a device calls itself: the maker capitalised, and a name even
    * when there is no model. */
  def maker(manufacturer: String, model: String, named: Boolean): String = {
    val m = Option(manufacturer).map(_.trim).getOrElse("")
    val maker = if (named && m.nonEmpty) m.substring(0, 1).toUpperCase + m.substring(1) else m
    val kind = Option(model).map(_.trim).getOrElse("")
    if (kind.isEmpty) { if (maker.nonEmpty) maker else if (named) "Android device" else "" }
    else if (maker.isEmpty || kind.toLowerCase.startsWith(maker.toLowerCase)) kind
    else maker + " " + kind
  }

  /** Whether a screen reader (TalkBack, or any service that reads the screen) is on now. */
  def reading: Boolean = {
    val manager = Host.activity.getSystemService(Context.ACCESSIBILITY_SERVICE).asInstanceOf[android.view.accessibility.AccessibilityManager]
    // on a television there is no touch to explore, so a service that speaks counts as well
    manager != null && manager.isEnabled && (manager.isTouchExplorationEnabled ||
      !manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN).isEmpty)
  }

  /** How many lines a string is written as: one, plus a line for every newline in it. */
  def lineCount(text: CharSequence): Int = {
    var lines = 1
    var i = 0
    while (i < text.length) {
      if (text.charAt(i) == '\n') lines += 1
      i += 1
    }
    lines
  }

  /** The widest of the lines a string is written as.
    *
    * Measured line by line rather than as one run: a run advance over the whole string walks straight past the
    * newlines and answers with every line laid end to end, which is a box wide enough for all of them and a
    * subtitle sitting in the left third of it.
    */
  def widest(paint: Paint, whole: String): Double = {
    var best = 0.0
    var from = 0
    var more = true
    while (more) {
      val at = whole.indexOf('\n', from)
      val end = if (at < 0) whole.length else at
      if (end > from) {
        // a right-to-left run measured as left-to-right comes out short of what is drawn: Arabic words overran their
        // box by a letter or two, off the end of a line placed by its width
        val run = paint.getRunAdvance(whole, from, end, from, end, RenderNode.rightToLeft(whole, from, end), end).toDouble
        if (run > best) best = run
      }
      if (at < 0) more = false else from = at + 1
    }
    best
  }

  /** Whether the first letter with a direction in `from` to `end` reads right to left, as a TextView takes the line. */
  def rightToLeft(whole: String, from: Int, end: Int): Boolean = {
    var i = from
    var found = 0
    while (found == 0 && i < end) {
      val d = Character.getDirectionality(whole.charAt(i))
      if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) found = 2
      else if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) found = 1
      i += 1
    }
    found == 2
  }

  /** The nearest of a `GradientDrawable`'s eight directions to a CSS angle (0 runs bottom to top, 90 left to right). */
  def orientation(angle: Double): android.graphics.drawable.GradientDrawable.Orientation = {
    import android.graphics.drawable.GradientDrawable.Orientation
    val all = Array(Orientation.BOTTOM_TOP, Orientation.BL_TR, Orientation.LEFT_RIGHT, Orientation.TL_BR, Orientation.TOP_BOTTOM,
      Orientation.TR_BL, Orientation.RIGHT_LEFT, Orientation.BR_TL)
    all((((math.round(angle / 45.0) % 8) + 8) % 8).toInt)
  }

  /** A gradient along one axis as a 256-pixel strip of it, stretched over the box: `quarter` turns from bottom-to-top
    * (CSS's 0deg), the stops at `at` (0 to 1) in `colors`. */
  def strip(colors: Array[Int], at: Array[Double], quarter: Int): android.graphics.drawable.Drawable = {
    val n = 256
    val pixels = Array.tabulate(n) { i =>
      val p = (i + 0.5) / n
      val k = math.max(0, math.min(at.length - 2, at.lastIndexWhere(_ <= p)))
      val span = at(k + 1) - at(k)
      val f = if (span <= 0.0) 0.0 else math.max(0.0, math.min(1.0, (p - at(k)) / span))
      def mix(shift: Int): Int = {
        val a = (colors(k) >>> shift) & 255
        val b = (colors(k + 1) >>> shift) & 255
        math.round(a + (b - a) * f).toInt
      }
      (mix(24) << 24) | (mix(16) << 16) | (mix(8) << 8) | mix(0)
    }
    // along the strip in the gradient's direction: bottom to top, left to right, top to bottom, right to left
    val ordered = if (quarter == 0 || quarter == 3) pixels.reverse else pixels
    val across = quarter == 1 || quarter == 3
    val bitmap = android.graphics.Bitmap.createBitmap(ordered, if (across) n else 1, if (across) 1 else n, Bitmap.Config.ARGB_8888)
    val drawable = new android.graphics.drawable.BitmapDrawable(Host.activity.getResources, bitmap)
    drawable.setFilterBitmap(true)
    drawable
  }

  /** The seam's `"#rrggbb"` or `"#rrggbbaa"` as Android's ARGB. */
  def color(hex: String): Int =
    if (hex.length == 9) android.graphics.Color.parseColor("#" + hex.substring(7) + hex.substring(1, 7)) else android.graphics.Color.parseColor(hex)
}

/** A container: each child is laid out at its own size at the origin and placed by its translation — absolute
  * positioning, as the DOM and SceneGraph backends have it. Children are never squeezed to fit the group, and draw
  * outside its bounds, like a `div` without `overflow`. */
final class NodeGroup(context: Context) extends ViewGroup(context) {
  setClipChildren(false)
  setClipToPadding(false)

  override def onMeasure(widthSpec: Int, heightSpec: Int): Unit = {
    var i = 0
    while (i < getChildCount) {
      val child = getChildAt(i)
      val params = child.getLayoutParams
      child.measure(spec(params.width), spec(params.height))
      i += 1
    }
    // the render root drawn smaller than a unit to a pixel is laid out that much larger, so its edges are the window's
    val grown = if ((this eq Host.root) && Host.scale > 0.0 && Host.scale < 1.0) 1.0 / Host.scale else 1.0
    acrossToReach = View.MeasureSpec.getMode(widthSpec) == View.MeasureSpec.UNSPECIFIED
    downToReach = View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED
    val (across, down) = reach()
    setMeasuredDimension(
      if (acrossToReach) across else math.ceil(View.getDefaultSize(0, widthSpec) * grown - 0.001).toInt,
      if (downToReach) down else math.ceil(View.getDefaultSize(0, heightSpec) * grown - 0.001).toInt)
  }

  // A group given no size (a container sized by nothing, a form) would be 0 by 0, and Android draws nothing in a view
  // with no room and sends it no touch, so what is in it was never seen: it is laid out over where its children reach
  // instead, as a `div` of no size shows what is in it. Its size to EveryUI is 0 all the same (`getWidth`).
  private var acrossToReach = false
  private var downToReach = false
  private def reach(): (Int, Int) = {
    var across = 0f
    var down = 0f
    var i = 0
    while (i < getChildCount) {
      val child = getChildAt(i)
      across = math.max(across, child.getTranslationX + child.getMeasuredWidth)
      down = math.max(down, child.getTranslationY + child.getMeasuredHeight)
      i += 1
    }
    (math.ceil(across).toInt, math.ceil(down).toInt)
  }

  /** A child moved (its translation): a group laid out over its children's reach is laid out again where they now
    * reach past it, or no longer to its edge. */
  private[render] def childMoved(): Unit = if (acrossToReach || downToReach) {
    val (across, down) = reach()
    if ((acrossToReach && across != getMeasuredWidth) || (downToReach && down != getMeasuredHeight)) requestLayout()
  }

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    var i = 0
    while (i < getChildCount) {
      val child = getChildAt(i)
      child.layout(0, 0, child.getMeasuredWidth, child.getMeasuredHeight)
      i += 1
    }
  }

  override def shouldDelayChildPressedState(): Boolean = false

  /** Where this is a scroll's (`RenderNode.setScrollable`), the way it scrolls: 1 down, 2 across, 0 not at all; and
    * what takes a finger's drag along it from whatever under it the finger went down on, told where it went down. */
  private[render] var scrollsAlong: Int = 0
  /** How strongly its ends fade (`RenderNode.setEdgeFade`): its start (left or top) and its end, 0 or 1. */
  private[render] var fades: (Float, Float) = (0f, 0f)
  override protected def getTopFadingEdgeStrength(): Float = fades._1
  override protected def getBottomFadingEdgeStrength(): Float = fades._2
  override protected def getLeftFadingEdgeStrength(): Float = fades._1
  override protected def getRightFadingEdgeStrength(): Float = fades._2
  private[render] var takeDrag: (MotionEvent, Float, Float) => Unit = null
  // where the finger went down, as each touch passes on to what is under it
  private var downX = 0f
  private var downY = 0f

  /** A finger that moves along the scroll further than a tap may takes the drag from what it went down on, as a
    * ScrollView does: that is told it was cancelled (no tap), and the scroll hears it go down where it did and every
    * move after. Without it, a card a tap is heard on held every touch that began on it, and a swipe scrolled nothing
    * unless it began between the cards. Only a move mostly along the scroll is taken: a page lets a rail in it have a
    * sideways swipe, and a slider across it its drag. */
  override def onInterceptTouchEvent(e: MotionEvent): Boolean = scrollsAlong != 0 && takeDrag != null && {
    val action = e.getActionMasked
    if (action == MotionEvent.ACTION_DOWN) {
      downX = e.getRawX
      downY = e.getRawY
      false
    }
    else if (action == MotionEvent.ACTION_MOVE) {
      val across = math.abs(e.getRawX - downX)
      val down = math.abs(e.getRawY - downY)
      val (along, other) = if (scrollsAlong == 1) (down, across) else (across, down)
      val taken = along > android.view.ViewConfiguration.get(getContext).getScaledTouchSlop && along > other
      if (taken) takeDrag(e, downX, downY)
      taken
    }
    else false
  }

  /** A blur drawn by hand before Android 12 (SoftBlur), made when one is first asked for. */
  private var soft: SoftBlur = null

  def blur(sigma: Double): Unit = {
    if (soft == null) soft = new SoftBlur(this, c => drawSharp(c))
    soft.set(sigma)
  }

  private def drawSharp(canvas: android.graphics.Canvas): Unit = super.dispatchDraw(canvas)

  override def dispatchDraw(canvas: android.graphics.Canvas): Unit = {
    if (soft != null && soft.on) soft.draw(canvas) else super.dispatchDraw(canvas)
    // the root's tint, over everything drawn under it (Platform.tint)
    if (Host.tint != null && (this eq Host.root)) canvas.drawPaint(Host.tint)
  }

  // what is inside changing means the blurred picture of it has to be taken again: Android 8 on says so here, and
  // draws only the child again unless the group asks to be drawn again too
  override def onDescendantInvalidated(child: View, target: View): Unit = {
    super.onDescendantInvalidated(child, target)
    if (soft != null && soft.on) {
      soft.changed()
      invalidate()
    }
  }

  // before Android 8 it is said here, on the way up, and every group on the way is drawn again anyway
  override def invalidateChildInParent(location: Array[Int], dirty: android.graphics.Rect): android.view.ViewParent = {
    if (soft != null && soft.on) soft.changed()
    super.invalidateChildInParent(location, dirty)
  }

  override def onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int): Unit = {
    super.onSizeChanged(w, h, oldw, oldh)
    if (soft != null) soft.changed()
  }

  private def spec(size: Int): Int =
    if (size >= 0) View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
}

/** What the Activity hands the backend: itself, the render root, and how it was launched. */
object Host {
  var activity: Activity = null
  /** The User-Agent pictures are fetched with (Platform.identify); the platform's own when empty. */
  var agent: String = ""
  var root: NodeGroup = null
  /** The colours scaled over the whole root (`Platform.tint`), drawn last by it, or null where nothing is scaled. */
  var tint: Paint = null
  var freeze: Double = -1.0
  var viewportWidth: Double = -1.0
  var viewportHeight: Double = -1.0
  /** How much of the window the on-screen keyboard covers, in pixels from the bottom: what the app is laid out above
    * (`Platform.screenHeight`), as the web lays a page out in what the keyboard leaves (`interactive-widget`). */
  var keyboard: Int = 0
  /** Told as the keyboard comes, goes or changes height (`Platform.onResize`). */
  var keyboardMoved: List[() => Unit] = Nil

  /** What the window's status and navigation bars, a cutout and the screen's rounded corners leave of it, in pixels
    * from each edge (left, top, right, bottom): the safe area the app is laid out inside (`Platform.screenWidth`,
    * `screenHeight`), its root moved in by it, so nothing of it lies under a bar, the camera or a curve. None on a
    * television (its whole screen is the app's) or a pinned viewport. */
  var safe: (Int, Int, Int, Int) = (0, 0, 0, 0)

  /** The safe area from the window's insets, the app told where it changed. A rounded corner reaches in from the side
    * where the bars above and below leave less than its radius: by as much as its curve does at the bar's edge. */
  def safeFrom(insets: android.view.WindowInsets): Unit = {
    val found =
      if (Platform.remoteNavigation() || viewportWidth > 0.0) (0, 0, 0, 0)
      else if (android.os.Build.VERSION.SDK_INT >= 30) {
        val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() |
          android.view.WindowInsets.Type.displayCutout())
        var (left, right) = (bars.left, bars.right)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
          import android.view.RoundedCorner.*
          def reach(position: Int, bar: Int): Int = Option(insets.getRoundedCorner(position)).map { corner =>
            val r = corner.getRadius.toDouble
            val dy = r - bar
            if (dy <= 0.0) 0 else math.ceil(r - math.sqrt(r * r - dy * dy)).toInt
          }.getOrElse(0)
          left = math.max(left, math.max(reach(POSITION_TOP_LEFT, bars.top), reach(POSITION_BOTTOM_LEFT, bars.bottom)))
          right = math.max(right, math.max(reach(POSITION_TOP_RIGHT, bars.top), reach(POSITION_BOTTOM_RIGHT, bars.bottom)))
        }
        (left, bars.top, right, bars.bottom)
      }
      else {
        val cut = if (android.os.Build.VERSION.SDK_INT >= 28) Option(insets.getDisplayCutout) else None
        (math.max(insets.getSystemWindowInsetLeft, cut.map(_.getSafeInsetLeft).getOrElse(0)),
          math.max(insets.getSystemWindowInsetTop, cut.map(_.getSafeInsetTop).getOrElse(0)),
          math.max(insets.getSystemWindowInsetRight, cut.map(_.getSafeInsetRight).getOrElse(0)),
          math.max(insets.getSystemWindowInsetBottom, cut.map(_.getSafeInsetBottom).getOrElse(0)))
      }
    if (found != safe) {
      safe = found
      if (root != null) {
        root.setTranslationX(safe._1.toFloat)
        root.setTranslationY(safe._2.toFloat)
      }
      keyboardMoved.foreach(f => f())
    }
  }

  // whether the edges follow the theme yet
  private var paintingEdges = false
  /** The window's ground behind the bars and round the safe area in the theme's background, and the bars' icons dark
    * on a light one, light on a dark one, followed as the palette changes. */
  def paintEdges(): Unit = if (activity != null) {
    if (!paintingEdges) {
      paintingEdges = true
      everyui.theme.Theme.palette.attach(_ => paintEdges())
    }
    val window = activity.getWindow
    window.getDecorView.setBackgroundColor(android.graphics.Color.parseColor(everyui.theme.Theme.background().toHex))
    val light = everyui.theme.Theme.colorScheme() == "light"
    if (android.os.Build.VERSION.SDK_INT >= 30) Option(window.getInsetsController).foreach { bars =>
      val both = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
      bars.setSystemBarsAppearance(if (light) both else 0, both)
    }
    else if (android.os.Build.VERSION.SDK_INT >= 23) {
      val decor = window.getDecorView
      val flag = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
      decor.setSystemUiVisibility(if (light) decor.getSystemUiVisibility | flag else decor.getSystemUiVisibility & ~flag)
    }
  }

  /** The keyboard's height from the window's insets, the app told where it changed. */
  def keyboardInset(pixels: Int): Unit = if (pixels != keyboard) {
    keyboard = pixels
    keyboardMoved.foreach(f => f())
  }

  // the application's, not the activity's: the same file, and there with no activity (a home-screen widget's receiver)
  lazy val settings: android.content.SharedPreferences =
    everyui.activity.ServiceRuntime.context.getSharedPreferences("everyui", Context.MODE_PRIVATE)

  /** The key hook (Platform.onKey), fed by the activity. */
  var key: (String, Boolean) => Boolean = null
  /** The modifiers held with the key the activity is reporting (`Platform.modifiers`). */
  var modifiers: everyui.event.Modifiers = everyui.event.Modifiers(false, false, false, false)
  /** The button of the pointer event being reported (`Platform.pointerButton`). */
  var pointerButton: Int = 0
  /** Whether the pointer event being reported is a touch on the screen (`Platform.pointerFinger`). */
  var pointerFinger: Boolean = false
  /** The embed the key the activity is reporting is meant for (`Platform.keyEmbed`). */
  var embedded: String = ""
  /** Whether the key the activity is reporting is one held down repeating (`Platform.keyRepeat`). */
  var repeat: Boolean = false
  /** Whether the app reads right to left (`Platform.setDirection`), and the text nodes that follow it. */
  var rtl: Boolean = false
  val aligned: java.util.Set[RenderNode] = java.util.Collections.newSetFromMap(new java.util.WeakHashMap[RenderNode, java.lang.Boolean]())

  /** An Android key by the web's name: the remote's keys by their names, anything typed by its character. */
  def keyName(e: KeyEvent): String = {
    val code = e.getKeyCode
    if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) "Enter"
    else if (code == KeyEvent.KEYCODE_BACK) "Back"
    // a mouse's forward button (a keyboard's Forward key), as a browser names it
    else if (code == KeyEvent.KEYCODE_FORWARD) "BrowserForward"
    else if (code == KeyEvent.KEYCODE_ESCAPE) "Escape"
    else if (code == KeyEvent.KEYCODE_DEL) "Backspace"
    else if (code == KeyEvent.KEYCODE_FORWARD_DEL) "Delete"
    else if (code == KeyEvent.KEYCODE_DPAD_UP) "ArrowUp"
    else if (code == KeyEvent.KEYCODE_DPAD_DOWN) "ArrowDown"
    else if (code == KeyEvent.KEYCODE_DPAD_LEFT) "ArrowLeft"
    else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) "ArrowRight"
    // a remote's transport keys: a television's own play/pause button, and the ones a media remote adds
    else if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) "MediaPlayPause"
    else if (code == KeyEvent.KEYCODE_MEDIA_PLAY) "MediaPlay"
    else if (code == KeyEvent.KEYCODE_MEDIA_PAUSE) "MediaPause"
    else if (code == KeyEvent.KEYCODE_MEDIA_STOP) "MediaStop"
    else if (code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) "MediaFastForward"
    else if (code == KeyEvent.KEYCODE_MEDIA_REWIND) "MediaRewind"
    else if (code == KeyEvent.KEYCODE_MEDIA_NEXT) "MediaTrackNext"
    else if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) "MediaTrackPrevious"
    // the modifiers on their own, as the web names them (a double-tap is one pressed twice)
    else if (code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_CTRL_RIGHT) "Control"
    else if (code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT) "Shift"
    else if (code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT) "Alt"
    else if (code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT) "Meta"
    else if (e.getUnicodeChar > 0) e.getUnicodeChar.toChar.toString
    else KeyEvent.keyCodeToString(code)
  }

  private val typefaces = mutable.Map.empty[String, Typeface]
  /** The pictures decoded so far, the ones wanted longest ago first. */
  private val bitmaps = mutable.LinkedHashMap.empty[String, Bitmap]
  private var bitmapBytes = 0L

  /** How much of the device's memory the pictures may hold between them. A television goes through hundreds of them
    * (a screen of rails is a hundred at once), and keeping every one for the life of the app was most of what it
    * took. What a view is drawing stays whole: letting a picture go here only drops this hold on it. */
  private lazy val bitmapBudget: Long = {
    val manager = activity.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[android.app.ActivityManager]
    val memory = new android.app.ActivityManager.MemoryInfo
    manager.getMemoryInfo(memory)
    math.max(32L * 1024L * 1024L, math.min(64L * 1024L * 1024L, memory.totalMem / 32L))
  }

    def typeface(face: Face): Typeface =
    typefaces.getOrElseUpdate(face.file, Typeface.createFromAsset(activity.getAssets, "fonts/" + face.file))

  /** An APK asset, decoded at its own pixel size (assets are never density-scaled). */
  def bitmap(uri: String): Bitmap = held(uri).getOrElse {
    val in = activity.getAssets.open(uri)
    val decoded = try BitmapFactory.decodeStream(in) finally in.close()
    keep(uri, decoded)
    decoded
  }

  /** The picture at `uri`, now the most recently wanted. */
  private def held(uri: String): Option[Bitmap] = bitmaps.remove(uri).map { b =>
    bitmaps.put(uri, b)
    b
  }

  /** Keep `b` under `uri`, letting the longest-unwanted go until the rest fit the budget. */
  private def keep(uri: String, b: Bitmap): Unit = if (b != null && !bitmaps.contains(uri)) {
    bitmaps.put(uri, b)
    bitmapBytes += b.getAllocationByteCount.toLong
    // less of it kept on a slower device (`everyui.perf.Quality`)
    val budget = (bitmapBudget.toDouble * everyui.perf.Quality.cache).toLong
    while (bitmapBytes > budget && bitmaps.size > 1) {
      // a downloaded picture goes before one of the APK's own: those are decoded again whenever they are wanted, the
      // smaller ones on the thread that draws, and a theme's backdrop let go under a rail of posters was decoded again
      // each time Home was walked
      val oldest = bitmaps.find(b => RenderNode.fetched(b._1)).getOrElse(bitmaps.head)
      bitmaps.remove(oldest._1)
      bitmapBytes -= oldest._2.getAllocationByteCount.toLong
    }
  }

  /** A few threads fetch and decode every image between them, and never every core: a screen of artwork (a TV's rails:
    * a hundred posters at once) started a thread apiece, and the interface had nothing left to draw with. */
  private lazy val loaders: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newFixedThreadPool(math.max(2, Runtime.getRuntime.availableProcessors - 1), r => {
      val thread = new Thread(r, "everyui-images")
      thread.setDaemon(true)
      thread.setPriority(Thread.MIN_PRIORITY)
      thread
    })

  /** The one thread that does [[Platform.offThread]]'s work, at a background priority, so what it does never takes
    * the time the interface draws with. */
  lazy val work: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newSingleThreadExecutor(r => {
      val thread = new Thread(() => {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        r.run()
      }, "everyui-work")
      thread.setDaemon(true)
      thread
    })

  /** What is on its way, and everyone waiting on it. */
  private val pending = mutable.Map.empty[String, List[Bitmap => Unit]]

  /** A bitmap by URI, then `done` on the main thread: at once from the cache or, for a small one, the APK; after a
    * download, or the decoding of a large one of the APK's own, on one of the loading threads (`null` if it cannot be
    * had). A site-absolute path is the APK's asset, or the app's server's where the APK holds none (`address`). */
  def bitmapThen(asked: String)(done: Bitmap => Unit): Unit = loaded(address(asked), done)

  /** Where `uri` is read from: a site-absolute path (`/api/portrait/3`) from the app's server (`Platform.origin`) where
    * the APK holds no asset there and there is a server to ask, else the asset; anything else as it is. */
  def address(uri: String): String =
    if (!uri.startsWith("/") || uri.startsWith("//")) uri
    else {
      // a packaged file by its path alone: a query (`?v=3`) is the server's to read
      val packaged = uri.stripPrefix("/").takeWhile(c => c != '?' && c != '#')
      val origin = Platform.origin()
      if (origin.isEmpty || packagedHere(packaged)) packaged else origin.stripSuffix("/") + uri
    }

  // whether the APK holds the asset at `path`, asked once each
  private def packagedHere(path: String): Boolean = packaged.getOrElseUpdate(path,
    try {
      activity.getAssets.open(path).close()
      true
    }
    catch { case _: java.io.IOException => false })
  private val packaged = mutable.Map.empty[String, Boolean]

  private def loaded(uri: String, done: Bitmap => Unit): Unit = held(uri) match {
    case Some(b) => done(b)
    case None if uri.startsWith("http") || uri.startsWith("file:") =>
      elsewhere(uri, done) {
        val connection = new java.net.URL(uri).openConnection()
        if (agent.nonEmpty) connection.setRequestProperty("User-Agent", agent)
        // a network gone away leaves a read waiting for ever otherwise, and the loading threads with it: no
        // picture loads again until the app is restarted
        connection.setConnectTimeout(10000)
        connection.setReadTimeout(20000)
        val in = connection.getInputStream
        // below Full (`everyui.perf.Quality`) a picture with no transparency (a poster, a photo) is decoded in 16 bits
        // a pixel, half the memory and half the texture to upload; one with transparency is decoded as it is all the same
        val options = new BitmapFactory.Options()
        if (everyui.perf.Quality.compactPixels) options.inPreferredConfig = Bitmap.Config.RGB_565
        try BitmapFactory.decodeStream(in, null, options) finally in.close()
      }
    // a document the person chose (FileAccess): a photo's full size is far more than a preview needs, so it is decoded
    // scaled down by halves to no more than twice `previewSide`
    case None if uri.startsWith("content:") =>
      elsewhere(uri, done) {
        val address = android.net.Uri.parse(uri)
        val bounds = new BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        val probe = activity.getContentResolver.openInputStream(address)
        try BitmapFactory.decodeStream(probe, null, bounds) finally probe.close()
        val options = new BitmapFactory.Options()
        while (math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize max 1) > previewSide * 2)
          options.inSampleSize = (options.inSampleSize max 1) * 2
        val in = activity.getContentResolver.openInputStream(address)
        try BitmapFactory.decodeStream(in, null, options) finally in.close()
      }
    // one the APK does not hold measures 0 and is read on a loading thread, where its failure is a picture missing
    // rather than an exception out of whatever asked (an activity starting)
    case None if pixels(uri) > 0L && pixels(uri) <= decodedHere => done(bitmap(uri))
    case None =>
      elsewhere(uri, done) {
        val in = activity.getAssets.open(uri)
        try BitmapFactory.decodeStream(in) finally in.close()
      }
  }

  /** `load` on one of the loading threads, then everyone waiting on `uri` on the main thread: one load per address,
    * however many ask for it, since a rail shows the same artwork more than once, and each picture asks twice over
    * (its natural size, then its pixels). */
  private def elsewhere(uri: String, done: Bitmap => Unit)(load: => Bitmap): Unit = pending.get(uri) match {
    case Some(waiting) => pending(uri) = done :: waiting
    case None =>
      pending(uri) = List(done)
      loaders.execute(() => {
        val b =
          try load
          catch {
            case t: Exception =>
              Platform.logError("Loading " + uri, t)
              null
          }
        activity.runOnUiThread(() => {
          keep(uri, b)
          pending.remove(uri).getOrElse(Nil).reverse.foreach(_(b))
        })
      })
  }

  /** The most pixels one of the APK's own pictures may have and still be decoded on the thread that asked for it: an
    * icon, a mark or a weather glyph is, so it is drawn along with whatever asked for it. A picture the size of the
    * screen (a theme's backdrop, 1920 by 1080) is decoded on a loading thread as a download is: decoding it on the
    * thread that draws held a television's frame for 40ms, and what shows one keeps what it had until it has come. */
  private val decodedHere: Long = 1000L * 1000L
  /** The side a chosen document's picture is shown at, at most (a preview beside its name). */
  private val previewSide: Int = 256

  /** Each asset's size in pixels, read once from its header. */
  private val assetPixels = mutable.Map.empty[String, Long]

  private def pixels(uri: String): Long = assetPixels.getOrElseUpdate(uri,
    try {
      val in = activity.getAssets.open(uri)
      try {
        val bounds = new BitmapFactory.Options
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeStream(in, null, bounds)
        bounds.outWidth.toLong * bounds.outHeight.toLong
      } finally in.close()
    } catch { case _: java.io.IOException => 0L })

  /** Layout units to screen pixels: the display's density, so a TV or a phone is laid out in the same units its own
    * apps are (1 on a launch that pins a viewport, whose frame is compared pixel for pixel), times the size the app is
    * drawn at (everyui.theme.UiScale). */
  var scale: Double = 1.0
  /** The display's density, or 1 on a launch that pins a viewport: `scale` before the size the app is drawn at. */
  var density: Double = 1.0
  /** `scale` as the touch or the pointer's move being heard began (the activity takes it), so one that changes the size
    * the app is drawn at as it is heard is the same place to every view it reaches. */
  var touchScale: Double = 1.0

  /** The render root scaled from its top left by `density` times the size the app is drawn at, and what is drawn at
    * the screen's own pixels under it (an embed's web view, a video's surface) shrunk back by as much again. */
  def scaleRoot(): Unit = if (root != null) {
    scale = density * everyui.theme.UiScale()
    root.setPivotX(0f)
    root.setPivotY(0f)
    root.setScaleX(scale.toFloat)
    root.setScaleY(scale.toFloat)
    unscale(root)
    root.requestLayout()
  }
  /** Each web view under `view` drawn into the window while the screen is tinted, so the tint reaches its video too.
    * From Android 13 a web view's video can be a plane of its own, under the window where the page shows it, which
    * nothing the window draws can scale: a layer of the web view's own keeps its video in the window, and costs a
    * frame of the app's for each of the video's, so it is there only while there is a tint. Before Android 13 a web
    * view's video is always in the window. */
  def tintPages(view: View): Unit = if (android.os.Build.VERSION.SDK_INT >= 33) view match {
    case page: android.webkit.WebView =>
      val layer = if (tint != null) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
      if (page.getLayerType != layer) page.setLayerType(layer, null)
    case group: ViewGroup =>
      var i = 0
      while (i < group.getChildCount) {
        tintPages(group.getChildAt(i))
        i += 1
      }
    case _ => ()
  }

  private def unscale(view: View): Unit = view match {
    case embed: EmbedView =>
      embed.setScaleX((1.0 / scale).toFloat)
      embed.setScaleY((1.0 / scale).toFloat)
    case surface: android.view.SurfaceView if surface.getParent.isInstanceOf[VideoFrame] =>
      surface.setScaleX((1.0 / scale).toFloat)
      surface.setScaleY((1.0 / scale).toFloat)
      surface.getParent.requestLayout()
    case group: ViewGroup =>
      var i = 0
      while (i < group.getChildCount) {
        unscale(group.getChildAt(i))
        i += 1
      }
    case _ => ()
  }
  /** The path the app is at: `--es path /somewhere` to start there, then wherever it last pushed. */
  var path: String = ""
}

/** Backend node factories. */

/** What a page calls to say something back to the app.
  *
  * A class of its own rather than an anonymous one: `addJavascriptInterface` finds the method by reflection, which
  * means the name has to survive the shrinker (see the keep rule in proguard-rules.pro), and a named class is what
  * a rule can name. The page is on its own thread; the app is not, so what it says is handed to the one that draws.
  */
class WebBridge(node: RenderNode) {
  @android.webkit.JavascriptInterface
  def postMessage(message: String): Unit =
    Host.activity.runOnUiThread(new Runnable {
      override def run(): Unit = node.webSaid(message)
    })
}
object Nodes {
  /** A rich document's editing surface (everyui.rich.RichTextEditor): none here yet (`Platform.richEditing`). */
  def richEditor(): RenderNode = container()

  /** A form (`everyui.form.Form`): a container here. */
  def form(kind: String): RenderNode = container()

  /** The Activity's content view. */
  def root(): RenderNode = {
    val made = new RenderNode(Host.root)
    RenderNode.rootNode = made
    made
  }

  /** A video: a [[VideoFrame]], holding the view the player draws into. */
  def video(): RenderNode = {
    val frame = new VideoFrame(Host.activity)
    val node = new RenderNode(frame)
    node.media = Some(new ExoMedia(Some(frame)))
    node
  }

  /** A page of the web, drawn in place.
    *
    * `bridge` is the name of an object the page can call to say things back: `<bridge>.postMessage("...")`. It is
    * named by whoever builds the view rather than fixed here, since the page and the app have to agree on it and
    * neither of them is this layer. */
  def web(bridge: String): RenderNode = {
    // the one made ready by WebView.prewarm, where there is one
    val view = WebWarm.take().getOrElse(new android.webkit.WebView(Host.activity))
    val settings = view.getSettings
    settings.setJavaScriptEnabled(true)
    settings.setDomStorageEnabled(true)
    // an embed that will not start until it is touched is an embed that never starts on a television
    settings.setMediaPlaybackRequiresUserGesture(false)
    view.setBackgroundColor(android.graphics.Color.BLACK)
    // the keys are the app's: a page that took them would swallow the remote
    view.setFocusable(false)
    view.setFocusableInTouchMode(false)
    // in the window while the screen is tinted (Host.tintPages)
    Host.tintPages(view)
    val node = new RenderNode(view)
    val said = new WebBridge(node)
    view.addJavascriptInterface(said, bridge)
    node.webSandbox = new WebSandbox(view, bridge, said)
    node
  }

  /** An embed's box (everyui.component.Embed): for a `site`, an [[EmbedView]] running it; otherwise a plain group
    * for the app to put a view of its own in. */
  def embed(name: String, site: everyui.component.EmbedSite): RenderNode =
    if (site == null) container()
    else {
      val view = new EmbedView(Host.activity, name, site)
      Host.tintPages(view)
      new RenderNode(view)
    }

  /** Sound: an empty view, with nothing to draw. */
  def audio(): RenderNode = {
    val node = new RenderNode(new View(Host.activity))
    node.media = Some(new ExoMedia(None))
    node
  }

  /** A container — a [[NodeGroup]] other nodes are appended to. */
  def container(): RenderNode = new RenderNode(new NodeGroup(Host.activity))

  /** A drawing (everyui.draw.Drawing): paths on a canvas of its own. */
  def drawing(): RenderNode = new RenderNode(new DrawingView(Host.activity))

  /** A paragraph of styled runs (`everyui.component.RichText`): a text node here, which draws them. */
  def richText(): RenderNode = text()

  /** A text node — a single-line `TextView` with no padding of its own. */
  def text(): RenderNode = {
    val t = new OutlinedText(Host.activity)
    t.setIncludeFontPadding(false)
    if (android.os.Build.VERSION.SDK_INT >= 28) t.setFallbackLineSpacing(false)
    t.setPadding(0, 0, 0, 0)
    t.setMaxLines(1)
    t.setHorizontallyScrolling(true)
    // wrapped the plain greedy way every other backend wraps, rather than by a TextView's own high-quality strategy,
    // which breaks the same words somewhere else
    t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE)
    t.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE)
    t.setTextColor(android.graphics.Color.parseColor("#1c1b1f"))
    t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32.0f)
    // fractional glyph advances, so text measures as it does in a browser rather than snapped to whole pixels
    t.setPaintFlags(t.getPaintFlags | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG)
    val node = new RenderNode(t)
    Host.aligned.add(node)
    node.applyAlign()
    node
  }

  /** A Material Icons glyph, by codepoint, `size` pixels. */
  def icon(glyph: String, size: Double): RenderNode = {
    val node = text()
    node.setFont("Material Icons", 400, size)
    node.setText(glyph)
    node
  }

  /** A text field — an `EditText`, hiding what is typed for a secret — with no box or padding of its own. */
  def input(secure: Boolean): RenderNode = {
    val e = new FieldView(Host.activity)
    e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI)
    e.setBackground(null)
    e.setPadding(0, 0, 0, 0)
    e.setIncludeFontPadding(false)
    e.setSingleLine(true)
    e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | (if (secure) android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD else 0))
    e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16.0f)
    // from where the root reads, not the edit style's left: an empty field's caret starts at the right in Arabic
    e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START)
    new RenderNode(e)
  }

  /** A link: a container; its click follows it. */
  def link(): RenderNode = container()

  /** A drop-down draws itself here: its node is a line of text. */
  def select(): RenderNode = text()

  /** A box of lines to type in — a multi-line `EditText` — with no box or padding of its own. */
  def textArea(): RenderNode = {
    val node = input(false)
    node.view match {
      case e: EditText =>
        e.setSingleLine(false)
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        e.setGravity(android.view.Gravity.TOP | android.view.Gravity.START)
      case _ => ()
    }
    node
  }

  /** An image node — an `ImageView` stretched to its box. */
  def image(uri: String, width: Double, height: Double): RenderNode = {
    val i = new PictureView(Host.activity)
    i.setScaleType(ImageView.ScaleType.FIT_XY)
    val node = new RenderNode(i)
    node.setWidth(width)
    node.setHeight(height)
    node.setImageSource(uri)
    node
  }
}

/** The handful of non-render platform services the slice needs. */
object Platform {
  /** The sizes of `nodes` (`MeasuredFeature`'s batch), each read in turn. */
  def sizesOf(nodes: List[RenderNode]): List[(Double, Double)] = nodes.map(_.getSize())

  /** Logcat, tag `everyui`. */
  // (an address's secrets masked, a stream's token with them, whatever is logged: everyui.log.Redact)
  def log(message: String): Unit = Log.i("everyui", everyui.log.Redact("[ui] " + message))

  /** A failure with its cause: logcat gets the stack, an address's secrets in it masked as well. */
  def logError(message: String, t: Throwable): Unit = {
    Log.i("everyui", everyui.log.Redact("[ui] ERROR " + message + ": " + t.toString))
    Log.e("everyui", everyui.log.Redact(message + "\n" + Log.getStackTraceString(t)))
  }

  def ticks(): Double = System.nanoTime() / 1.0e6

  /** Hear each frame drawn as Android measured it, `f(ms, buildMs)`, on a thread of its own ([[FrameTimings]]); false
    * before Android 7, where the app times its own frames instead. */
  def frameTimings(f: (Double, Double) => Unit): Boolean = FrameTimings.watch(f)

  /** Packaged as APK assets. */
  def imagePath(name: String): String = "images/" + name

  /** The window, in pixels — or the viewport the launch asked for (`--es viewport 1920x1080`). */
  def kind: PlatformKind = PlatformKind.Android
  /** An Android TV is driven by its remote; a phone or tablet by touch. */
  // a box that is a television by its features (leanback) though its UI mode says otherwise counts as one too, as
  // Flutter's probe has it
  def remoteNavigation(): Boolean =
    Host.activity.getSystemService(Context.UI_MODE_SERVICE).asInstanceOf[android.app.UiModeManager].getCurrentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
      Host.activity.getPackageManager.hasSystemFeature("android.software.leanback")
  def touchSupport(): Boolean = Host.activity.getPackageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
  def pointerSupport(): Boolean = false
  def keyboardSupport(): Boolean = Host.activity.getResources.getConfiguration.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS

  /** The shared ScrollContainer scrolls. */
  def nativeScroll(): Boolean = false
  /** The browser (or the app that owns the address) at `url`. A television may have no browser at all, and then there
    * is nothing to open it with: nothing happens, as a link that cannot open does nothing elsewhere, rather than the
    * app coming down. */
  def openUrl(url: String): Unit =
    try Host.activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    catch { case t: Throwable => log("openUrl: nothing opens " + url + " (" + t.toString + ")") }
  /** An app has no page to leave: the browser opens `url`. */
  def navigate(url: String): Unit = openUrl(url)
  /** The activity's title (what the recents screen calls it). */
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

  def setTitle(title: String): Unit = Host.activity.setTitle(title)
  /** Whether a `Backdrop` plays a video behind a screen (`videoSource`): not here, where it keeps its picture. */
  def backdropVideo(): Boolean = false

  /** Whether people ask for less motion (`everyui.motion.ReduceMotion`): animations removed, or their duration scale
    * set to none, in the accessibility or developer settings. */
  def reduceMotion(): Boolean =
    try android.provider.Settings.Global.getFloat(Host.activity.getContentResolver,
      android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f) == 0.0f
    catch { case _: Throwable => false }
  /** The setting as it changes, read again as the app comes back to the front from the settings. */
  def watchReduceMotion(f: Boolean => Unit): Unit = onResume(() => f(reduceMotion()))
  /** "dark" or "light": the system's dark theme on or off (the configuration's night mode). */
  def systemColorScheme(): String =
    try {
      val night = Host.activity.getResources.getConfiguration.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK
      if (night == android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
    } catch { case _: Throwable => "dark" }
  /** The scheme as the person changes it: told as the configuration changes (the app takes a change of night mode
    * itself, `configChanges`), and read again as it comes back to the front. */
  def watchColorScheme(f: String => Unit): Unit = {
    configured = configured :+ (() => f(systemColorScheme()))
    onResume(() => f(systemColorScheme()))
  }
  private var configured: List[() => Unit] = Nil
  /** How much longer than an app's own time people asked to be given to read or act (Android's "Time to take action",
    * Android 10 on): 1 as it comes. */
  def timeScale(): Double =
    try {
      if (android.os.Build.VERSION.SDK_INT >= 29) {
        val am = Host.activity.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
          .asInstanceOf[android.view.accessibility.AccessibilityManager]
        math.max(1.0, am.getRecommendedTimeoutMillis(10000,
          android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT |
            android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS) / 10000.0)
      } else 1.0
    } catch { case _: Throwable => 1.0 }
  /** The font size the person chose in the system's settings, 1 as it comes (the configuration's font scale). */
  def systemTextScale(): Double =
    try Host.activity.getResources.getConfiguration.fontScale.toDouble catch { case _: Throwable => 1.0 }
  /** The font size as the person changes it: told as the configuration changes (where the app takes `fontScale` among
    * its `configChanges`; else the activity is made again), and read again as it comes back to the front. */
  def watchTextScale(f: Double => Unit): Unit = {
    configured = configured :+ (() => f(systemTextScale()))
    onResume(() => f(systemTextScale()))
  }
  /** The activity's configuration changed (`EveryUIActivity.onConfigurationChanged`). */
  def configurationChanged(): Unit = configured.foreach(_())
  /** `words` read out by TalkBack when it next can, where it is on (a new message in a chat). */
  def announce(words: String): Unit = if (RenderNode.reading && Host.root != null) Host.root.announceForAccessibility(words)
  /** At once: TalkBack has no other way to say it than `announce`. */
  def alert(words: String): Unit = announce(words)
  /** The app named (`NaboTV (tv.nabo.app)`) to the servers its pictures come from, as their User-Agent: a map's tile
    * server refuses the platform's own `Dalvik/…`. */
  def identify(app: String): Unit = Host.agent = app
  /** The screen kept on (while something is shown that is meant to be watched without a touch), or let sleep. */
  /** Whether this device can go on playing in a corner of whatever the viewer does next. */
  /** Whether this platform has a browser engine to lend a page (Nodes.web). Only what is actually implemented
    * says yes: a platform that answered yes and drew nothing would leave a viewer on a black screen. */
  def webViews(): Boolean = true
  /** The engine started now and a web view made ready for the next page (WebWarm), at a moment the app chooses
    * rather than as the first page is shown. */
  def prewarmWeb(): Unit = WebWarm.prewarm()
  /** Whether a web guest runs in an embed here: in a web view of its own (EmbedView). */
  def embeds(): Boolean = true

  def corners(): Boolean =
    Host.activity.getPackageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

  /** Put what is playing into that corner. True when the platform took it. */
  def toCorner(): Boolean =
    try Host.activity.enterPictureInPictureMode(new android.app.PictureInPictureParams.Builder().build())
    catch {
      case t: Throwable =>
        log("corner: " + t.getMessage)
        false
    }

  /** What to do when the app is at the front again after being away — another app had the screen, or the device
    * came back from sleep. An app that was left showing something it only shows when nobody is there (a
    * screensaver) wants to know, because somebody is there now. */
  def onResume(f: () => Unit): () => Unit = {
    resumers = resumers :+ f
    () => resumers = resumers.filterNot(_ eq f)
  }

  /** Said by the platform when it happens: a frame first, which also runs a frame loop left waiting while away. */
  def resumed(): Unit = {
    everyui.task.Frames.resumed()
    resumers.foreach(f => f())
  }

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

  /** The app asks before it is left while `guard` answers true, or any other guard does: here Back from the first
    * screen (one nothing in the app takes) asks Stay or Leave, and Leave sends the app to the background, as Back
    * there does from Android 12. Home and the recent apps leave it without asking. What removes it. */
  def leaveGuard(guard: () => Boolean): () => Unit = everyui.app.LeaveGuards.add(guard)
  /** Guarded while `flag` is set. */
  def leaveGuardWhile(flag: reactify.Val[Boolean]): () => Unit = everyui.app.LeaveGuards.addWhile(flag)

  /** Every colour on the screen scaled, channel by channel (1 each leaves it as it is): a picture dimmed, or its blue
    * pulled down for the night, the video with it. The root draws the scale last, over all it holds, as a colour
    * multiplied into what is under it (PorterDuff's MULTIPLY, Skia's modulate: the blend unit's own, with nothing
    * copied), so a video's TextureView is scaled like everything else. Not a colour filter on a layer of the root:
    * a layer drew the whole window offscreen and then again, a web view's page offscreen once more within it, and
    * on Android 13 and later took a web view's video off its overlay, so every frame of a playing page became a
    * whole frame of the app's. Nothing is drawn where nothing is scaled. */
  def tint(red: Double, green: Double, blue: Double): Boolean = {
    def channel(v: Double): Int = math.round(math.max(0.0, math.min(1.0, v)) * 255.0).toInt
    Host.tint =
      if (red >= 0.999 && green >= 0.999 && blue >= 0.999) null
      else {
        val paint = new Paint()
        paint.setColor(android.graphics.Color.rgb(channel(red), channel(green), channel(blue)))
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY))
        paint
      }
    Host.tintPages(Host.root)
    // the root drawn again, with the scale or without
    Host.root.invalidate()
    true
  }

  def keepAwake(on: Boolean): Unit = {
    val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    if (on) Host.activity.getWindow.addFlags(flag) else Host.activity.getWindow.clearFlags(flag)
  }
  /** A count of what is new on the app's icon: none set here, a launcher showing a dot or a count for the app's
    * notifications (`showNotice`) on its own. */
  def setBadge(count: Int): Unit = ()
  /** Whether the person lets the app show notifications (`showNotice`): read again as the app comes back to the front.
    * Refused on a television, which shows none. */
  def notificationPermission: reactify.Val[everyui.Permission] = Notices.permission
  /** The system's prompt for notifications (Android 13 and later; before, they are on unless the person turned them
    * off); best from something the person did. `notificationPermission` changes with the answer. */
  def askNotificationPermission(): Unit = Notices.ask()
  /** `notice` posted as a notification; nothing while the permission is not given, or while the app is in front and
    * `notice.onlyWhenHidden`. A tap on it brings the app to the front and runs `onClick` (`Notices`). */
  def showNotice(notice: everyui.push.Notice, onClick: () => Unit): Unit = Notices.show(notice, onClick)

  /** Whether copyText reaches a clipboard: it does. */
  def clipboard(): Boolean = true
  /** Whether people select text with a pointer (`Component.selectable`): not here. */
  def selectsText(): Boolean = false
  def selectedText(): String = ""
  /** The words selected, as HTML and as text (`Clipboard.selection`): none here. */
  def selectedRich(): Option[(String, String)] = None
  def copyText(text: String): Unit = {
    val manager = Host.activity.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[android.content.ClipboardManager]
    manager.setPrimaryClip(android.content.ClipData.newPlainText("everyui", text))
  }
  /** `html` and `text` on the clipboard together: an app that takes styled words pastes the HTML, any other the text. */
  def copyRich(html: String, text: String): Unit = {
    val manager = Host.activity.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[android.content.ClipboardManager]
    manager.setPrimaryClip(android.content.ClipData.newHtmlText("everyui", text, html))
  }

  /** No address bar: no path to show, push or follow. */
  def path(): String = Host.path
  def pushPath(path: String): Unit = Host.path = path
  def onPath(f: String => Unit): Unit = ()

  /** Run `f` on the main thread, soon: how a reply from another thread reaches the components. */
  /** No browser history here: the screen manager keeps its own. */
  def historyBack(): Boolean = false

  /** No address bar: the path is the app's alone, set in place of the one it had. */
  def replacePath(path: String): Unit = Host.path = path

  /** No browser history here: the screen manager keeps its own. */
  def historyForward(): Boolean = false

  /** A value kept for this window alone: the app has one, so for this run of it. "" when there is none. */
  def windowSetting(name: String): String = windowSettings.getOrElse(name, "")

  /** Keep `value` under `name` for this run; "" forgets it. */
  def saveWindowSetting(name: String, value: String): Unit =
    windowSettings = if (value.isEmpty) windowSettings - name else windowSettings + (name -> value)

  private var windowSettings: Map[String, String] = Map.empty

  /** The time zone the device is in, as IANA names it ("America/Chicago"). */
  def timeZone(): String = java.util.TimeZone.getDefault.getID

  /** `f` told as the time zone changes (everyui.date.TimeZones). What stops it being told. */
  def onTimeZone(f: () => Unit): () => Unit = everyui.date.TimeZones.onChange(f)

  /** An address the app will have only later opened once it does: nothing here refuses an open that comes late. */
  def openUrlLater(waiting: String = ""): everyui.app.UrlTarget =
    new everyui.app.UrlTarget(url => openUrl(url), () => ())

  /** A moment marked (everyui.perf.Marks), logged where the app was launched with `marks=1`, for a check to read. */
  def marked(name: String, at: Double): Unit = if (launchArg("marks") == "1") log("[mark] " + name + " " + at.toString)
  /** A check a harness reads (`everyui.access.Access.expose`): a web page's alone, so nothing here. */
  def expose(name: String, f: () => String, json: Boolean): Unit = ()

  /** What to do as the app's window gets the focus back from another window in front of it. What stops it being
    * told. */
  def onFocus(f: () => Unit): () => Unit = {
    focusers = focusers :+ f
    () => focusers = focusers.filterNot(_ eq f)
  }

  /** What to do as the app's window loses the focus to another, though it may still be in sight. */
  def onBlur(f: () => Unit): () => Unit = {
    blurrers = blurrers :+ f
    () => blurrers = blurrers.filterNot(_ eq f)
  }

  /** Said by the platform as the window gets the focus (`has`) or loses it; told once each way. */
  def windowFocus(has: Boolean): Unit = if (has != windowFocused) {
    windowFocused = has
    (if (has) focusers else blurrers).foreach(f => f())
  }

  private var windowFocused: Boolean = true
  private var focusers: List[() => Unit] = Nil
  private var blurrers: List[() => Unit] = Nil

  /** What to do as the app is closed for good. Only what can be done at once, then and there. What stops it being
    * told. */
  def onClose(f: () => Unit): () => Unit = {
    closers = closers :+ f
    () => closers = closers.filterNot(_ eq f)
  }

  /** Said by the platform as the app is closed. */
  def closing(): Unit = closers.foreach(f => f())

  private var closers: List[() => Unit] = Nil

  /** One activity draws the app: a second window of it is not opened. */
  def openWindow(path: String): Boolean = false

  /** Whether a rich document is edited in place here (everyui.rich.RichTextEditor): not yet, its Markdown instead. */
  def richEditing(): Boolean = false

  /** Whether a page declares the theme's colour scheme: there is no page here, so nothing is declared. */
  def declareColorScheme(on: Boolean): Unit = ()

  /** Which build is running: the version its package was installed with. */
  def buildId(): String = appVersionName()

  /** The app cannot start itself again into a new build here: an update is installed (`installApp`). */
  def reload(): Boolean = false

  private val onlineNow = reactify.Var[Boolean](true)
  private var followingNetwork = false

  /** Whether the device has a network, as Android says (its default network there or gone): false is sure, true only
    * that there is one, not that the server answers. Followed only where the app's manifest asks for
    * ACCESS_NETWORK_STATE, and always true where it does not. */
  def online: reactify.Val[Boolean] = {
    if (!followingNetwork && Host.activity != null) {
      followingNetwork = true
      followNetwork()
    }
    onlineNow
  }

  private def followNetwork(): Unit = try {
    val activity = Host.activity
    val allowed = activity.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE) ==
      android.content.pm.PackageManager.PERMISSION_GRANTED
    if (allowed && android.os.Build.VERSION.SDK_INT >= 24) {
      val manager = activity.getSystemService(Context.CONNECTIVITY_SERVICE)
        .asInstanceOf[android.net.ConnectivityManager]
      onlineNow @= manager.getActiveNetwork != null
      manager.registerDefaultNetworkCallback(new android.net.ConnectivityManager.NetworkCallback {
        override def onAvailable(network: android.net.Network): Unit = later(() => onlineNow @= true)
        override def onLost(network: android.net.Network): Unit = later(() => onlineNow @= false)
      })
    }
  }
  catch { case t: Throwable => logError("network: it cannot be followed", t) }

  /** An ARB file's messages (everyui.i18n.Arb). */
  def parseMessages(arb: String): Map[String, String] = everyui.i18n.Arb.parse(arb)

  /** A text file among the APK's assets (a translation), at once; or one at an http(s) URL (an SVG icon), read off the
    * thread that draws and answered on it; "" if it is not there. */
  def readText(path: String, done: String => Unit): Unit =
    if (path.startsWith("http://") || path.startsWith("https://")) offThread(() => {
      val text =
        try {
          val connection = new java.net.URL(path).openConnection()
          connection.setConnectTimeout(10000)
          connection.setReadTimeout(30000)
          textOf(connection.getInputStream)
        } catch { case _: Exception => "" }
      later(() => done(text))
    })
    else done(try textOf(Host.activity.getAssets.open(path)) catch { case _: Exception => "" })

  // all of a stream, as UTF-8, closed after
  private def textOf(in: java.io.InputStream): String = {
    // not InputStream.readAllBytes: Android has no such method before API 33, and the televisions this runs
    // on are older than that
    try {
      val out = new java.io.ByteArrayOutputStream()
      val buffer = new Array[Byte](8192)
      var read = in.read(buffer)
      while (read != -1) {
        out.write(buffer, 0, read)
        read = in.read(buffer)
      }
      new String(out.toByteArray, java.nio.charset.StandardCharsets.UTF_8)
    } finally in.close()
  }

  /** The character at a Unicode code point, as a string. */
  def fromCodePoint(codePoint: Int): String = new String(Character.toChars(codePoint))

  /** The device's language, as a BCP 47 tag (`en-US`). */
  /** What it is, as its maker names it ("NVIDIA SHIELD Android TV"). */
  def deviceModel(): String = RenderNode.maker(android.os.Build.MANUFACTURER, android.os.Build.MODEL, false)

  /** Whether this device opens a streaming service's own app itself, rather than needing another to do it. */
  /** The hardware facts a performance report cannot be read without, each as a name and a value: the processor's
    * instruction set (a 32-bit stick and a 64-bit box can share a name), the Android release, the memory there is,
    * what is free, what this process may use, whether the device says it is short, and the display's refresh rate.
    * Whatever the platform will not say is left out. */
  def deviceProfile(): List[(String, String)] = {
    var facts: List[(String, String)] = Nil
    try Option(android.os.Build.SUPPORTED_ABIS).flatMap(_.headOption).foreach(a => facts = facts :+ ("abi" -> a))
    catch { case _: Throwable => () }
    facts = facts :+ ("sdk" -> android.os.Build.VERSION.SDK_INT.toString)
    Option(android.os.Build.VERSION.RELEASE).foreach(r => facts = facts :+ ("release" -> r))
    try {
      val manager = Host.activity.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[android.app.ActivityManager]
      val memory = new android.app.ActivityManager.MemoryInfo
      manager.getMemoryInfo(memory)
      facts = facts ++ List("ramMb" -> (memory.totalMem / (1024L * 1024L)).toString,
        "availMb" -> (memory.availMem / (1024L * 1024L)).toString, "lowMem" -> memory.lowMemory.toString,
        "heapMb" -> manager.getMemoryClass.toString)
    } catch { case _: Throwable => () }
    try facts = facts :+ ("refreshHz" -> math.round(Host.activity.getWindowManager.getDefaultDisplay.getRefreshRate).toString)
    catch { case _: Throwable => () }
    facts
  }

  /** What the device says of itself at launch (`everyui.perf.Perf.device`): its model, the OpenGL ES version it draws
    * with, its memory and what this app may use of it, its cores, and whether it calls itself a low-memory device. */
  def deviceHint(): everyui.perf.DeviceHint = {
    val manager = Host.activity.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[android.app.ActivityManager]
    val memory = new android.app.ActivityManager.MemoryInfo
    manager.getMemoryInfo(memory)
    val gl = try Option(manager.getDeviceConfigurationInfo).map(c => "GLES " + c.getGlEsVersion) catch { case _: Throwable => None }
    everyui.perf.DeviceHint(
      model = Some(deviceModel()),
      graphicsPlatform = gl,
      ramMb = Some((memory.totalMem / (1024L * 1024L)).toInt),
      appMemoryMb = Some(manager.getMemoryClass),
      cores = Some(Runtime.getRuntime.availableProcessors),
      lowRam = Some(manager.isLowRamDevice))
  }
  /** "low" while Android says the device is short of memory (and would start ending background processes), else
    * "normal". */
  def memoryLevel(): String = try {
    val manager = Host.activity.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[android.app.ActivityManager]
    val memory = new android.app.ActivityManager.MemoryInfo
    manager.getMemoryInfo(memory)
    if (memory.lowMemory) "low" else "normal"
  } catch { case _: Throwable => "" }
  /** The share of the heap this app may have that is in use. */
  def memoryUsedPct(): Int = {
    val r = Runtime.getRuntime
    ((r.totalMemory - r.freeMemory).toDouble * 100.0 / r.maxMemory.toDouble).toInt
  }

  /** How often the display is drawn, in Hz, for where a frame counts as dropped (`everyui.perf.QualityPolicy`): the
    * display's own (Display.getRefreshRate), or 0 where it cannot be read. */
  def refreshRate(): Double =
    try Host.activity.getWindowManager.getDefaultDisplay.getRefreshRate.toDouble catch { case _: Throwable => 0.0 }
  /** How many errors nothing caught this run has seen (a thread's uncaught exception), counted from the first time it
    * is asked: what was uncaught before still goes where it went (the handler there was, else the console). */
  def errorsSeen(): Int = {
    hearUncaught
    uncaught.get
  }
  /** Turns cut off by the platform (a Roku's): none here. */
  def turnsCutOff(): Int = 0
  /** `f` told the message and the stack of every exception a thread throws and nothing catches, before it goes where
    * it went (the handler there was: Android's crash, else the console). */
  def onUncaught(f: (String, String) => Unit): Unit = {
    hearUncaught
    uncaughtHooks = uncaughtHooks :+ f
  }
  /** `t`'s stack as the JVM prints it, with what caused it. */
  def stackOf(t: Throwable): String = {
    val out = new java.io.StringWriter
    t.printStackTrace(new java.io.PrintWriter(out))
    out.toString
  }
  @volatile private var uncaughtHooks: List[(String, String) => Unit] = Nil
  private val uncaught = new java.util.concurrent.atomic.AtomicInteger(0)
  private lazy val hearUncaught: Unit = {
    val before = Thread.getDefaultUncaughtExceptionHandler
    Thread.setDefaultUncaughtExceptionHandler((thread: Thread, t: Throwable) => {
      uncaught.incrementAndGet()
      uncaughtHooks.foreach(f => try f(t.toString, stackOf(t)) catch { case _: Throwable => () })
      if (before != null) before.uncaughtException(thread, t)
      else {
        System.err.print("Exception in thread \"" + thread.getName + "\" ")
        t.printStackTrace(System.err)
      }
    })
  }

  /** What the app is showing, as a PNG in base64, no more than 1920 across; "" where it cannot be had. The app's own
    * drawing: a video on the display's own plane never passes through it, and a TextureView's draws nothing into a
    * canvas of the app's, so the picture is not in it (as a Flutter app's platform views are not in its shot). */
  def screenshot(): String =
    try {
      val root = Host.root
      if (root.getWidth <= 0 || root.getHeight <= 0) ""
      else {
        val scale = math.min(1.0, 1920.0 / root.getWidth)
        val bitmap = Bitmap.createBitmap(math.max(1, (root.getWidth * scale).toInt),
          math.max(1, (root.getHeight * scale).toInt), Bitmap.Config.ARGB_8888)
        val canvas = new android.graphics.Canvas(bitmap)
        canvas.scale(scale.toFloat, scale.toFloat)
        root.draw(canvas)
        val out = new java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        android.util.Base64.encodeToString(out.toByteArray, android.util.Base64.NO_WRAP)
      }
    } catch { case t: Throwable => logError("screenshot: could not draw the app", t); "" }

  def launchesApps(): Boolean = true

  /** Whether it can play `codec` (a media server's name for it: "hevc", "ac3"), with a decoder of its own or by passing
    * the sound through: see [[Codecs]]. */
  def decodes(codec: String): Boolean = Codecs.plays(codec)

  /** Whether this copy may install a newer build of itself ([[AppInstaller]]): the viewer's "install unknown apps". */
  def canInstallApps(): Boolean = AppInstaller.allowed
  /** Where that is given. */
  def openInstallSettings(): Unit = AppInstaller.openSettings()
  /** The APK at `url`, fetched and handed to the system's installer, which asks the viewer; `done` hears nothing when it
    * has it, or what went wrong. */
  def installApp(url: String, progress: Double => Unit, done: Option[String] => Unit): Unit =
    AppInstaller.install(url, progress, done)

  /** Something large was let go: nothing to do where the runtime collects on its own. */
  def collect(): Unit = ()
  /** Whether `blur` is drawn: it is. */
  def blurs(): Boolean = true
  /** Whether text can hold emoji: yes, where the app ships an emoji face for what the television's own fonts lack. */
  def emoji(): Boolean = true
  /** Whether it can listen: a speech recognizer to turn what is said into words ([[Dictation]]). */
  def dictation(): Boolean = Dictation.available

  /** One utterance, as words: `heard` hears them as they come (each time the whole of what is heard so far) and
    * whether they are settled, `ended` that it is over. What comes back stops it. */
  def dictate(heard: (String, Boolean) => Unit, ended: () => Unit): () => Unit = Dictation.listen(heard, ended)

  /** Where this device can be reached on its own network, for another to play to; "" where it has no such need. */
  def localAddress(): String = ""
  def deviceSerial(): String = ""

  /** Where the app came from (a store's package name), and which build it is. */
  def installStore(): String =
    try Option(Host.activity.getPackageManager.getInstallerPackageName(Host.activity.getPackageName))
      .getOrElse("")
    catch { case _: Throwable => "" }

  def appVersion(): Int =
    try Host.activity.getPackageManager.getPackageInfo(Host.activity.getPackageName, 0).versionCode
    catch { case _: Throwable => 0 }

  /** The release the build says it is (the manifest's versionName, "2.0.0"), or "" where it cannot be read. */
  def appVersionName(): String =
    try Option(Host.activity.getPackageManager.getPackageInfo(Host.activity.getPackageName, 0).versionName).getOrElse("")
    catch { case _: Throwable => "" }

  /** The device's own caption settings (Settings → Accessibility → Captions): its colours, the edge round the letters and the
    * size, as the system's own players draw subtitles. */
  def captionStyle(): Option[everyui.text.CaptionStyle] =
    try {
      val manager = Host.activity.getSystemService(android.content.Context.CAPTIONING_SERVICE)
        .asInstanceOf[android.view.accessibility.CaptioningManager]
      val style = manager.getUserStyle
      def colour(argb: Int): everyui.Color =
        new everyui.Color((argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff, (argb >>> 24) & 0xff)
      val edge = style.edgeType match {
        case 1 => "outline"
        case 2 => "shadow"
        case 3 => "raised"
        case 4 => "depressed"
        case _ => "none"
      }
      Some(everyui.text.CaptionStyle(colour(style.foregroundColor), colour(style.backgroundColor),
        colour(style.windowColor), edge, colour(style.edgeColor), manager.getFontScale.toDouble))
    }
    catch { case _: Throwable => None }

  def language(): String = java.util.Locale.getDefault.toLanguageTag

  /** The app's language: the platform chooses its own faces for the script, so there is nothing to do. */
  def setLanguage(code: String): Unit = ()

  /** The root's layout direction, which fields take, and the side every text starts on. */
  def setDirection(rtl: Boolean): Unit = {
    Host.rtl = rtl
    Host.root.setLayoutDirection(if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
    Host.aligned.forEach(_.applyAlign())
  }

  /** `f` on a thread that does not draw, for work a frame should not wait on (a large document parsed or written);
    * whatever it hands back goes by `later`. A failure is logged and goes no further: it must not take the app down. */
  def offThread(f: () => Unit): Unit = Host.work.execute(() =>
    try f() catch { case t: Throwable => logError("Off the UI thread", t) })

  def later(f: () => Unit): Unit = new android.os.Handler(android.os.Looper.getMainLooper).post(() => f())
  /** `f` once what is running now is done: `later` here. */
  def afterTurn(f: () => Unit): Unit = later(f)
  /** `f` on the UI thread once `seconds` have passed. */
  def after(seconds: Double, f: () => Unit): Unit = {
    if (IdleAudit.on) IdleAudit.after(seconds)
    new android.os.Handler(android.os.Looper.getMainLooper).postDelayed(() => f(), math.max(0L, (seconds * 1000.0).toLong))
  }

  /** A value kept across launches (the app's shared preferences), or "" when there is none. */
  def setting(name: String): String = Host.settings.getString(name, "")
  /** Keep `value` under `name`; "" forgets it. */
  def saveSetting(name: String, value: String): Unit = {
    val edit = Host.settings.edit()
    if (value.isEmpty) edit.remove(name) else edit.putString(name, value)
    edit.apply()
  }

  /** A value that must not be readable off the device (a session's token), kept across launches encrypted under a
    * keystore key ([[Secrets]]), or "" when there is none. */
  def secret(name: String): String = Secrets.get(name)
  /** Keep `value` under `name`, sealed; "" forgets it. */
  def saveSecret(name: String, value: String): Unit = Secrets.put(name, value)

  /** The device's own store of documents (see [[everyui.store.Store]]). Files under the app's cache directory
    * rather than the settings, which hold a handful of small values and read the whole of themselves to do it.
    * Android empties a cache directory when it wants the room back, which is what a cache is for. */
  private def storeSafe(name: String): String =
    name.map(c => if (c.isLetterOrDigit || c == '.' || c == '-') c else '_')
  private def storeDir(space: String): java.io.File = {
    val dir = new java.io.File(everyui.activity.ServiceRuntime.context.getCacheDir, "everyui-store/" + storeSafe(space))
    dir.mkdirs()
    dir
  }
  private def storeFile(space: String, key: String): java.io.File =
    new java.io.File(storeDir(space), storeSafe(key) + ".json")

  def storeGet(space: String, key: String, got: Option[String] => Unit): Unit = got(
    try {
      val file = storeFile(space, key)
      if (!file.isFile) None
      else {
        val text = readUtf8(file)
        if (text.isEmpty) None else Some(text)
      }
    } catch { case _: Throwable => None })

  /** A file's text, by plain `java.io`: `java.nio.file` is Android 8's, and the store must keep documents back to 6. */
  private def readUtf8(file: java.io.File): String = {
    val in = new java.io.FileInputStream(file)
    try {
      val out = new java.io.ByteArrayOutputStream
      val buffer = new Array[Byte](8192)
      var n = in.read(buffer)
      while (n > 0) {
        out.write(buffer, 0, n)
        n = in.read(buffer)
      }
      new String(out.toByteArray, "UTF-8")
    } finally in.close()
  }

  private def writeUtf8(file: java.io.File, text: String): Unit = {
    val out = new java.io.FileOutputStream(file)
    try out.write(text.getBytes("UTF-8")) finally out.close()
  }

  def storePut(space: String, key: String, value: String, done: () => Unit): Unit = {
    try writeUtf8(storeFile(space, key), value)
    catch { case _: Throwable => () }
    done()
  }

  def storeDelete(space: String, key: String, done: () => Unit): Unit = {
    try storeFile(space, key).delete() catch { case _: Throwable => () }
    done()
  }

  def storeKeys(space: String, got: List[String] => Unit): Unit = got(
    Option(storeDir(space).listFiles()).getOrElse(Array.empty[java.io.File]).toList
      .map(_.getName).filter(_.endsWith(".json")).map(n => n.substring(0, n.length - 5)))

  def storeClear(space: String, done: () => Unit): Unit = {
    Option(storeDir(space).listFiles()).getOrElse(Array.empty[java.io.File])
      .foreach(f => try f.delete() catch { case _: Throwable => () })
    done()
  }

  /** What this device calls itself: the name its owner gave it ("Bedroom TV") where there is one, and what it is
    * otherwise. The name is what a person picking a television out of a list is looking for. */
  def deviceName(): String = {
    val named =
      try Option(android.provider.Settings.Global.getString(Host.activity.getContentResolver, "device_name"))
        .map(_.trim).getOrElse("")
      catch { case _: Throwable => "" }
    if (named.nonEmpty) named else RenderNode.maker(android.os.Build.MANUFACTURER, android.os.Build.MODEL, true)
  }

  /** Android has text fields of its own. */
  def nativeInput(): Boolean = true
  /** Only a platform without text fields asks its keyboard for a value. */
  def editText(title: String, value: String, secure: Boolean, done: String => Unit): Unit = ()
  /** `editText` for a field asking for `autofill` (`Autofill.token`), which says nothing more here. */
  def editField(title: String, value: String, secure: Boolean, autofill: String, done: String => Unit): Unit =
    editText(title, value, secure, done)

  /** A registered face loads from the APK's `assets/fonts/` when first used. */
  def registerFont(face: Face): Unit = ()
  /** A colour emoji face for where the system has none (`Faces.colorEmoji`): the system draws its own here, so none
    * is taken. */
  def registerColorEmoji(face: Face): Boolean = false

  /** The window in the app's units: its pixels over `Host.scale` (the density times the size the app is drawn at), or
    * a pinned viewport over the size the app is drawn at. */
  def screenWidth(): Double =
    if (Host.viewportWidth > 0.0) Host.viewportWidth / everyui.theme.UiScale()
    else (windowPixels()._1 - Host.safe._1 - Host.safe._3).toDouble / Host.scale
  /** Less what the on-screen keyboard covers while it is up (Host.keyboard), so a field at the bottom (a composer) is
    * laid out above it; and both, less what the bars, a cutout and the corners take (`safeArea`). */
  def screenHeight(): Double =
    if (Host.viewportHeight > 0.0) Host.viewportHeight / everyui.theme.UiScale()
    else (windowPixels()._2 - Host.safe._2 - math.max(Host.safe._4, Host.keyboard)).toDouble / Host.scale
  /** The screen's safe area, in the app's units: what a phone's bars, its camera's cutout and its rounded corners take
    * from each edge (`Host.safe`), which the app is already laid out inside. None on a television. */
  def safeArea(): everyui.layout.Insets = everyui.layout.Insets(Host.safe._2 / Host.scale, Host.safe._3 / Host.scale,
    Host.safe._4 / Host.scale, Host.safe._1 / Host.scale)
  /** The app drawn `factor` times its size (everyui.theme.UiScale), over the density it is already scaled by. */
  def setUiScale(factor: Double): Unit = Host.scaleRoot()

  /** Screen pixels to a layout unit: the window's real width over the width it is laid out in — the display's density,
    * or whatever a `--es viewport` launch set the layout to. */
  def pixelRatio(): Double = {
    val laid = screenWidth()
    if (laid > 0.0) (windowPixels()._1 - Host.safe._1 - Host.safe._3).toDouble / laid else 1.0
  }

  /** The window's size in pixels: Android 11's window metrics, and before them the display's real size — which, the
    * app being edge to edge and full screen, is the same thing. */
  private def windowPixels(): (Int, Int) =
    if (android.os.Build.VERSION.SDK_INT >= 30) {
      val bounds = Host.activity.getWindowManager.getCurrentWindowMetrics.getBounds
      (bounds.width, bounds.height)
    }
    else {
      val metrics = new android.util.DisplayMetrics
      Host.activity.getWindowManager.getDefaultDisplay.getRealMetrics(metrics)
      (metrics.widthPixels, metrics.heightPixels)
    }

  /** `--es freeze <seconds>` on the launch intent: step the animation that far on a virtual clock and stop
    * (tools/check.sh). Negative when absent. */
  def freezeSeconds(): Double = Host.freeze

  /** `--es name value` on the launch intent, or "". */
  def launchArg(name: String): String = {
    val value = Host.activity.getIntent.getStringExtra(name)
    if (value == null) "" else value
  }

  /** Each launch that reaches the app once it is running, with what it was launched with: here an intent to the
    * running activity (`onNewIntent`) with extras, as their names and values. One with none (the launcher's icon
    * again) is not one. `launchArg` reads the new intent's extras from then on. Returns what stops it being told. */
  def onLaunch(f: Map[String, String] => Unit): () => Unit = {
    launchers = launchers :+ f
    () => launchers = launchers.filterNot(_ eq f)
  }

  /** Said by the activity for a new intent's string extras. */
  def launched(params: Map[String, String]): Unit = if (params.nonEmpty) launchers.foreach(f => f(params))

  private var launchers: List[Map[String, String] => Unit] = Nil

  /** Which of `ids`, Android package names, are installed here: only those the app's manifest names in its
    * `<queries>` can be seen from Android 11 on, the rest read as absent. */
  def installedApps(ids: List[String]): List[String] = ids.filter { id =>
    try {
      Host.activity.getPackageManager.getApplicationInfo(id, 0)
      true
    }
    catch {
      case _: Throwable => false
    }
  }

  /** The Jellyfin, Emby and Plex servers on the local network that answer within `seconds` ([[MediaServerSearch]]),
    * heard by `found` on the UI thread. */
  def findMediaServers(seconds: Double, found: List[everyui.MediaServer] => Unit): Unit =
    MediaServerSearch.find(seconds, found)

  /** Finish the activity (a demo that is done); the process follows (EveryUIActivity.onDestroy). */
  def quit(): Unit = Host.activity.finish()

  /** `millis` since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
  def localDateTime(millis: Long): String = {
    // java.text rather than java.time, which is Android 8's
    val format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
    format.setTimeZone(java.util.TimeZone.getDefault)
    format.format(new java.util.Date(millis))
  }

  /** No page to be served from: an app names its server (`--es live`). */
  def webSocketOrigin(): String = ""
  /** Where this app's server is, to resolve its relative URLs against: nothing here comes from a server, so what the app
    * set as the setting "everyui.origin" ("https://example.com"), else "". */
  def origin(): String = setting("everyui.origin")

  /** The frozen frame is complete. The check reads it from logcat and screenshots the device itself. */
  def frozen(): Unit = ()

  /** Called whenever the root changes size (a rotation, a resized window). */
  /** Every key the activity sees, by the web's name, pressed or released. */
  def onKey(f: (String, Boolean) => Boolean): Unit = Host.key = (name: String, pressed: Boolean) => {
    // a press timed to the frame that answers it, where it is measured
    if (pressed && everyui.perf.Perf.on) everyui.perf.Perf.keyed()
    f(name, pressed)
  }
  /** The modifiers held with the key being reported (a keyboard's; a remote holds none), as the activity reads them. */
  def modifiers(): everyui.event.Modifiers = Host.modifiers
  /** The button of the pointer event being reported: 0 the primary (a finger), 1 the middle, 2 the secondary. */
  /** Which of the platform's pointer events is being reported, the same for each component one climbs through: not
    * told here (-1), so a moment's time stands for it (`Pointer.blocks`). */
  def pointerEvent(): Long = -1L
  def pointerButton(): Int = Host.pointerButton
  /** Whether the pointer event being reported is a finger (or a stylus) on the screen (`PointerEvent.finger`). */
  def pointerFinger(): Boolean = Host.pointerFinger
  /** The embed the key being reported is meant for, "" for none: the one whose web view has the focus
    * (`Host.embedded`, as the activity reads it; never for Back, which is the app's). */
  def keyEmbed(): String = Host.embedded
  /** Whether the key being reported is one held down repeating: a press whose repeat count is past 0. */
  def keyRepeat(): Boolean = Host.repeat
  /** Whether the key being reported belongs to an input method composing words: never, since a keyboard on the screen
    * composes through the field itself and sends no keys for it. */
  def keyComposing(): Boolean = false
  /** Shortcuts are made with Ctrl on Android. */
  def commandKey(): Boolean = false

  /** `f` told as the window changes size, and as the on-screen keyboard comes, goes or changes height. */
  def onResize(f: () => Unit): Unit = {
    Host.root.addOnLayoutChangeListener((_, l, t, r, b, oldL, oldT, oldR, oldB) =>
      if (r - l != oldR - oldL || b - t != oldB - oldT) f()
    )
    Host.keyboardMoved = Host.keyboardMoved :+ f
  }

  /** A number from 0 up to 1, at random. */
  def random(): Double = math.random()
  def sqrt(x: Double): Double = math.sqrt(x)
  def pow(x: Double, y: Double): Double = math.pow(x, y)
  def sin(x: Double): Double = math.sin(x)
  def cos(x: Double): Double = math.cos(x)

  /** A site-absolute asset path (`/images/icon.png`) is that path inside the APK's assets. */
  /** A site-absolute path (`/images/icon.png`, `/api/portrait/3`): the APK's asset at that path where it holds one,
    * else the app's server's (`Host.address`). */
  def resolvePath(path: String): String = if (path.startsWith("/")) Host.address(path) else path

  /** Decode `uri` and report its natural size. */
  def loadImage(uri: String, loaded: (Double, Double) => Unit): Unit =
    Host.bitmapThen(uri)(b => if (b == null) loaded(0.0, 0.0) else loaded(b.getWidth.toDouble, b.getHeight.toDouble))

  /** The picture at `uri` as `across` by `down` cells, each its average colour (0xRRGGBB, row by row): what words over
    * it are judged against (`everyui.access.Access`). Scaled to eight times as fine and each cell's pixels averaged,
    * so a star does not make a cell. Nothing where it will not load. */
  def pictureCells(uri: String, across: Int, down: Int, read: List[Int] => Unit): Unit =
    Host.bitmapThen(uri)(b => if (b == null) read(Nil) else {
      val (w, h) = (across * 8, down * 8)
      val small = Bitmap.createScaledBitmap(b, w, h, true)
      val px = new Array[Int](w * h)
      small.getPixels(px, 0, w, 0, 0, w, h)
      if (!(small eq b)) small.recycle()
      read((0 until across * down).toList.map { cell =>
        val (cx, cy) = (cell % across, cell / across)
        var (r, g, bl) = (0, 0, 0)
        for (j <- 0 until 8; i <- 0 until 8) {
          val argb = px((cy * 8 + j) * w + cx * 8 + i)
          r += (argb >> 16) & 0xff
          g += (argb >> 8) & 0xff
          bl += argb & 0xff
        }
        ((r / 64) << 16) | ((g / 64) << 8) | (bl / 64)
      })
    })

  /** Bundled faces load synchronously from the APK. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit = {
    Host.typeface(Faces(family, weight))
    loaded()
  }

  def whenFontsReady(f: () => Unit): Unit = f()

  /** Run `tick` once per display frame. */
  /** Frames on demand (`everyui.task.Frames`): the app asks for each frame it needs, and the Choreographer calls on
    * it for none while nothing moves or changes. */
  def framesOnDemand(): Boolean = true
  /** `f` once, on the next display frame, on the main thread whatever thread asks. A Choreographer is a thread's own:
    * asked for on a thread without a Looper (OkHttp's, a pool's) it throws, and on one with a Looper the frame goes
    * to that thread's, and is lost with it. Either left the frame loop waiting on a frame that never came. */
  def nextFrame(f: () => Unit): Unit = onMainFrame(new Choreographer.FrameCallback {
    override def doFrame(frameTimeNanos: Long): Unit = f()
  })
  def animate(tick: () => Unit): Unit = onMainFrame(new Choreographer.FrameCallback {
    override def doFrame(frameTimeNanos: Long): Unit = {
      tick()
      Choreographer.getInstance.postFrameCallback(this)
    }
  })

  private lazy val mainHandler = new android.os.Handler(android.os.Looper.getMainLooper)
  /** How many frames were asked for off the main thread, the first few logged with where from. */
  private var offMain = 0

  private def onMainFrame(callback: Choreographer.FrameCallback): Unit =
    if (android.os.Looper.myLooper() eq android.os.Looper.getMainLooper) Choreographer.getInstance.postFrameCallback(callback)
    else {
      // what woke the loop changed what is drawn off the main thread, which Android does not allow: said, so it is found
      offMain += 1
      if (offMain <= 5) log("frames: woken off the main thread, the frame asked for there: " + new WokeFrom)
      mainHandler.post(() => Choreographer.getInstance.postFrameCallback(callback))
    }
}
