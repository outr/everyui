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
      // a weighted system face is Android 9's; before it, bold or not is the choice there is
      t.setTypeface(if (family.nonEmpty) Host.typeface(face)
        else if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false)
        else Typeface.create(Typeface.DEFAULT, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL))
      t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size.toFloat)
      spaceLines(t)
      placeY()
    case _ => ()
  }

  /** The laid-out size in pixels. Text is measured with its real font (its advance width, at fractional size) and is
    * one shared line (everyui.font.Face) tall. `Paint.measureText` rounds the width UP to a whole pixel; the run
    * advance is the exact one. */
  def getWidth(): Double =
    if (explicitWidth >= 0.0) explicitWidth
    else view match {
      case _: EditText => 0.0
      case t: TextView =>
        measure(t)
        laidOutWidth
      case _ => 0.0
    }

  def getHeight(): Double =
    if (explicitHeight >= 0.0) explicitHeight
    else view match {
      case t: TextView if wrapping =>
        measure(t)
        laidOutHeight
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
    val at = if (wrapping) math.max(1, math.round(explicitWidth).toInt) else 0
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
          t.measure(android.view.View.MeasureSpec.makeMeasureSpec(at, android.view.View.MeasureSpec.EXACTLY),
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
  def setMediaSource(url: String): Unit = media.foreach(_.setSource(url))
  /** Where the next source starts, in seconds: handed to the player with it, before it is prepared. */
  def setMediaStart(seconds: Double): Unit = media.foreach(_.setStart(seconds))
  def setMediaSidecars(sidecars: List[everyui.component.MediaSidecar]): Unit = media.foreach(_.setSidecars(sidecars))
  def setMediaPlaying(playing: Boolean): Unit = media.foreach(_.setPlaying(playing))
  def setMediaLoop(loop: Boolean): Unit = media.foreach(_.setLoop(loop))
  def setMediaMuted(muted: Boolean): Unit = media.foreach(_.setMuted(muted))
  def setMediaVolume(volume: Double): Unit = media.foreach(_.setVolume(volume))
  def setMediaRate(rate: Double): Unit = media.foreach(_.setRate(rate))
  def setMediaBuffering(named: String): Unit = media.foreach(_.setBuffering(named))
  def setMediaCached(cached: Boolean): Unit = media.foreach(_.setCached(cached))
  def mediaRates(): Boolean = true
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
  def setWebSource(url: String): Unit = view match {
    case w: android.webkit.WebView => if (url.trim.nonEmpty) w.loadUrl(url.trim)
    case _ => ()
  }

  /** Who the page is told is asking, for the loads after this one; empty is the engine's own. */
  def setWebAgent(agent: String): Unit = view match {
    case w: android.webkit.WebView => w.getSettings.setUserAgentString(if (agent.trim.isEmpty) null else agent)
    case _ => ()
  }

  /** Say something to the page, in its own language. */
  def runWebScript(script: String): Unit = view match {
    case w: android.webkit.WebView => w.evaluateJavascript(script, null)
    case _ => ()
  }

  /** Hear what the page says back. */
  def onWebMessage(f: String => Unit): Unit = webListener = f

  private[render] var webListener: String => Unit = _ => ()

  private[render] def webSaid(message: String): Unit = webListener(message)

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
  def setMediaCover(cover: Boolean): Unit = media.foreach(_.setCover(cover))
  def setMediaDirect(direct: Boolean): Unit = media.foreach(_.setDirect(direct))
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
  def setGradient(angle: Double, stops: String): Unit = {
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
  private var animators: List[android.animation.Animator] = Nil

  /** Each track an animator of the view's own properties, repeating: a position moves the translation the node is
    * placed by, from where it is placed. */
  /** What the motion is for, told when it is set (a Roku logs it for everyui_debug_motion); nothing here. */
  def setMotionOwner(owner: () => String): Unit = ()
  def setMotion(motion: List[everyui.motion.Track]): Unit = {
    tracks = motion
    animate()
  }

  private def animate(): Unit = {
    import android.animation.{Keyframe, ObjectAnimator, PropertyValuesHolder, ValueAnimator}
    import everyui.motion.Track
    animators.foreach(_.cancel())
    animators = tracks.map { track =>
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
      animator.start()
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

  /** Extra space after each letter, in pixels. Android spaces letters in ems, so it is the pixels over the size (which
    * `FontFeature` sets first); the measured width takes it. */
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
    if (gradient.length >= 2 && radius == 0f && borderWidth == 0 && math.abs(gradientAngle / 90.0 - math.round(gradientAngle / 90.0)) < 0.01)
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
      drawable.setColors(gradient)
    }
    else drawable.setColor(if (gradient.length == 1) gradient(0) else background)
    drawable.setCornerRadius(radius)
    if (borderWidth > 0) drawable.setStroke(borderWidth, borderColor)
    view.setBackground(drawable)
  }

  /** Every node is placed by its translation, so there is no position type to set. */
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click", "menu") with its root coordinates.
    * Touches drive down/move/up, a click on an up that stays on the view, and the menu on a long press or a mouse's
    * secondary button; a mouse or a hover drives enter/leave. */
  def onPointer(kind: PointerKind, f: (Double, Double) => Unit): Unit = {
    pointer += kind -> f
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
    val meta = e.getMetaState
    Host.modifiers = everyui.event.Modifiers((meta & android.view.KeyEvent.META_CTRL_ON) != 0,
      (meta & android.view.KeyEvent.META_META_ON) != 0, (meta & android.view.KeyEvent.META_ALT_ON) != 0,
      (meta & android.view.KeyEvent.META_SHIFT_ON) != 0)
    val at = Array(0, 0)
    Host.root.getLocationOnScreen(at)
    pointer.get(kind).foreach(f => f((e.getRawX - at(0)) / Host.scale, (e.getRawY - at(1)) / Host.scale))
  }
  // A finger held still on a view that has a context menu (something listens for PointerKind.Menu) asks for it, as a
  // long press does anywhere on Android: where it went down, after the platform's long-press time, unless it moved
  // further than a tap may or was lifted first. What it asked for is not also a click as it lifts.
  private var pressFrom: (Float, Float) = (0f, 0f)
  private var pressHeld: Option[Runnable] = None
  private var pressedLong = false
  private def letGo(): Unit = {
    pressHeld.foreach(r => view.removeCallbacks(r))
    pressHeld = None
  }
  private def touch(e: MotionEvent): Boolean = {
    touchHook.foreach(f => f(e))
    val action = e.getActionMasked
    if (action == MotionEvent.ACTION_DOWN) {
      fire(PointerKind.Down, e)
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
      fire(PointerKind.Move, e)
    }
    else if (action == MotionEvent.ACTION_UP) {
      letGo()
      fire(PointerKind.Up, e)
      val inside = e.getX >= 0 && e.getY >= 0 && e.getX <= view.getWidth && e.getY <= view.getHeight
      if (inside && !pressedLong) fire(PointerKind.Click, e)
    }
    else if (action == MotionEvent.ACTION_CANCEL) {
      letGo()
      fire(PointerKind.Up, e)
    }
    true
  }

  /** The shared ScrollContainer shifts the content; the node only clips. */
  def setScrollable(vertical: Boolean, horizontal: Boolean): Unit = ()
  def setScrollLeft(x: Double): Unit = ()
  /** A scroll that runs from its left in every language: the shared ScrollContainer counts it so here. */
  def setLeftToRight(): Unit = ()
  /** The shared ScrollContainer draws no scrollbar here. */
  def setScrollbarColor(hex: String): Unit = ()
  def setScrollTop(y: Double): Unit = ()
  /** How far the node has scrolled its own overflow: never, here, where the ScrollContainer shifts the content. */
  def scrolled(horizontal: Boolean): Double = 0.0
  def scrollSmoothly(to: Double, horizontal: Boolean): Unit = ()
  /** Whether the platform draws this: what a scroll has out of sight is not drawn at all. Its place and its size are
    * kept (INVISIBLE, not GONE), since the layout is ours and not the view system's. */
  def setVisible(visible: Boolean): Unit =
    view.setVisibility(if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE)
  def setOpacity(opacity: Double): Unit = view.setAlpha(opacity.toFloat)

  def setClip(width: Double, height: Double): Unit = view match {
    case g: ViewGroup => g.setClipChildren(true); g.setClipToPadding(true); g.setClipToOutline(true)
    case _ => ()
  }
  def setTooltip(text: String): Unit =
    if (android.os.Build.VERSION.SDK_INT >= 26) view.setTooltipText(if (text.isEmpty) null else text)
  /** A drawing's figures (everyui.draw.Drawing), where this is one. */
  def setFigures(figures: List[everyui.draw.Figure]): Unit = view match {
    case d: DrawingView => d.figures(figures)
    case _ => ()
  }
  /** Only a web page scrolls under a finger that drags: nothing to do here. */
  def setTouchDrags(on: Boolean): Unit = ()
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
    * web's; Android has nothing for them. */
  def setAutofill(token: String, section: String, name: String, passkey: Boolean): Unit = view match {
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
        (e.getInputType & T.TYPE_MASK_VARIATION) == T.TYPE_TEXT_VARIATION_WEB_PASSWORD
      val kind = token match {
        case "current-password" | "new-password" => T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_WEB_PASSWORD
        case _ if secret => T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_WEB_PASSWORD
        case "email" => T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        case "username" => T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        case "tel" => T.TYPE_CLASS_PHONE
        case "url" => T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_URI
        case "one-time-code" | "cc-number" | "cc-csc" => T.TYPE_CLASS_NUMBER
        case "name" | "given-name" | "additional-name" | "family-name" | "nickname" | "cc-name" =>
          T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_PERSON_NAME | T.TYPE_TEXT_FLAG_CAP_WORDS
        case "street-address" | "address-line1" | "address-line2" | "address-level1" | "address-level2" | "country" =>
          T.TYPE_CLASS_TEXT | T.TYPE_TEXT_VARIATION_POSTAL_ADDRESS | T.TYPE_TEXT_FLAG_CAP_WORDS
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
  /** Out of use, or back in it: a field takes no typing and TalkBack reads it as disabled. Other views are only drawn
    * dimmed by the app: disabling one would stop the touches that move the focus to it. */
  def setDisabled(disabled: Boolean): Unit = view match {
    case e: EditText => e.setEnabled(!disabled)
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

  def setAlt(text: String): Unit = view.setContentDescription(if (text.isEmpty) null else text)

  /** The focus landed here (the app's own focus, which Android's does not follow). With a screen reader on, this is
    * where TalkBack's focus goes: read out by its label, or else by the words inside it, as a button that its double
    * tap (or OK) presses. Without one, nothing is done: the checks and the delegate cost nothing on an ordinary run. */
  def accessibleFocus(click: () => Unit): Unit = if (RenderNode.reading) {
    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
    if (android.os.Build.VERSION.SDK_INT >= 28) view.setScreenReaderFocusable(true)
    view.setAccessibilityDelegate(new View.AccessibilityDelegate {
      override def onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo): Unit = {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.setClassName("android.widget.Button")
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

  /** Text on the side the app reads from, whatever script its words are in: a TextView's own start is where its
    * first letter reads from, so a Latin line in Arabic sat on the left. A field is mirrored with the root already, so
    * its start stays logical. */
  private[render] def applyAlign(): Unit = view match {
    case e: EditText => e.setTextAlignment(if (textAlign == "center") View.TEXT_ALIGNMENT_CENTER
      else if (textAlign == "end") View.TEXT_ALIGNMENT_VIEW_END else View.TEXT_ALIGNMENT_VIEW_START)
    case t: TextView =>
      t.setTextDirection(textDirection match {
        case "auto" => if (Host.rtl) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
        case "ltr" => View.TEXT_DIRECTION_LTR
        case "rtl" => View.TEXT_DIRECTION_RTL
        case _ => View.TEXT_DIRECTION_INHERIT
      })
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
  private def placeY(): Unit = view.setTranslationY(restY.toFloat)

  // Where the view sits down its parent: a line of text by its baseline
  private def restY: Double = view match {
    case _: EditText => top
    case t: TextView => top + face.baseline(fontSize) + t.getPaint.getFontMetricsInt.ascent
    case _ => top
  }
}

object RenderNode {
  /** The Autofill Framework's hint for an `autocomplete` token: View's own constants where it has one, else androidx's
    * `HintConstants` (the same strings, which every service knows); "" where neither names it. */
  def autofillHint(token: String): String = token match {
    case "username" => View.AUTOFILL_HINT_USERNAME
    case "current-password" => View.AUTOFILL_HINT_PASSWORD
    case "new-password" => "newPassword"
    case "one-time-code" => "smsOTPCode"
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
    setMeasuredDimension(View.getDefaultSize(0, widthSpec), View.getDefaultSize(0, heightSpec))
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

  /** A blur drawn by hand before Android 12 (SoftBlur), made when one is first asked for. */
  private var soft: SoftBlur = null

  def blur(sigma: Double): Unit = {
    if (soft == null) soft = new SoftBlur(this, c => drawSharp(c))
    soft.set(sigma)
  }

  private def drawSharp(canvas: android.graphics.Canvas): Unit = super.dispatchDraw(canvas)

  override def dispatchDraw(canvas: android.graphics.Canvas): Unit =
    if (soft != null && soft.on) soft.draw(canvas) else super.dispatchDraw(canvas)

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
  var freeze: Double = -1.0
  var viewportWidth: Double = -1.0
  var viewportHeight: Double = -1.0

  lazy val settings: android.content.SharedPreferences = activity.getSharedPreferences("everyui", Context.MODE_PRIVATE)

  /** The key hook (Platform.onKey), fed by the activity. */
  var key: (String, Boolean) => Boolean = null
  /** The modifiers held with the key the activity is reporting (`Platform.modifiers`). */
  var modifiers: everyui.event.Modifiers = everyui.event.Modifiers(false, false, false, false)
  /** The button of the pointer event being reported (`Platform.pointerButton`). */
  var pointerButton: Int = 0
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
    else if (code == KeyEvent.KEYCODE_ESCAPE) "Escape"
    else if (code == KeyEvent.KEYCODE_DEL) "Backspace"
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
    while (bitmapBytes > bitmapBudget && bitmaps.size > 1) {
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
    * had). */
  def bitmapThen(uri: String)(done: Bitmap => Unit): Unit = held(uri) match {
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
        try BitmapFactory.decodeStream(in) finally in.close()
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
    case None if pixels(uri) <= decodedHere => done(bitmap(uri))
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
    * apps are (1 on a launch that pins a viewport, whose frame is compared pixel for pixel). */
  var scale: Double = 1.0
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
  /** A form (`everyui.form.Form`): a container here. */
  def form(kind: String): RenderNode = container()

  /** The Activity's content view. */
  def root(): RenderNode = new RenderNode(Host.root)

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
    val view = new android.webkit.WebView(Host.activity)
    val settings = view.getSettings
    settings.setJavaScriptEnabled(true)
    settings.setDomStorageEnabled(true)
    // an embed that will not start until it is touched is an embed that never starts on a television
    settings.setMediaPlaybackRequiresUserGesture(false)
    view.setBackgroundColor(android.graphics.Color.BLACK)
    // the keys are the app's: a page that took them would swallow the remote
    view.setFocusable(false)
    view.setFocusableInTouchMode(false)
    val node = new RenderNode(view)
    view.addJavascriptInterface(new WebBridge(node), bridge)
    node
  }

  /** An embed's box (everyui.component.Embed): for a `site`, an [[EmbedView]] running it; otherwise a plain group
    * for the app to put a view of its own in. */
  def embed(name: String, site: everyui.component.EmbedSite): RenderNode =
    if (site == null) container() else new RenderNode(new EmbedView(Host.activity, name, site))

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
  /** Logcat, tag `everyui`. */
  def log(message: String): Unit = Log.i("everyui", "[ui] " + message)

  /** A failure with its cause: logcat gets the stack. */
  def logError(message: String, t: Throwable): Unit = {
    Log.i("everyui", "[ui] ERROR " + message + ": " + t.toString)
    Log.e("everyui", message, t)
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
  /** `words` read out by TalkBack when it next can, where it is on (a new message in a chat). */
  def announce(words: String): Unit = if (RenderNode.reading && Host.root != null) Host.root.announceForAccessibility(words)
  /** The app named (`NaboTV (tv.nabo.app)`) to the servers its pictures come from, as their User-Agent: a map's tile
    * server refuses the platform's own `Dalvik/…`. */
  def identify(app: String): Unit = Host.agent = app
  /** The screen kept on (while something is shown that is meant to be watched without a touch), or let sleep. */
  /** Whether this device can go on playing in a corner of whatever the viewer does next. */
  /** Whether this platform has a browser engine to lend a page (Nodes.web). Only what is actually implemented
    * says yes: a platform that answered yes and drew nothing would leave a viewer on a black screen. */
  def webViews(): Boolean = true
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

  /** Said by the platform when it happens. */
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

  /** Every colour on the screen scaled, channel by channel (1 each leaves it as it is): a picture dimmed, or its blue
    * pulled down for the night, the video with it. A colour matrix on the root's layer, which the video's TextureView
    * is drawn into like everything else; left off entirely when there is nothing to scale, a layer costing a pass. */
  def tint(red: Double, green: Double, blue: Double): Boolean = {
    if (red >= 0.999 && green >= 0.999 && blue >= 0.999) Host.root.setLayerType(View.LAYER_TYPE_NONE, null)
    else {
      val paint = new Paint()
      paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(Array[Float](red.toFloat, 0f, 0f, 0f, 0f,
        0f, green.toFloat, 0f, 0f, 0f, 0f, 0f, blue.toFloat, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
      Host.root.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }
    true
  }

  def keepAwake(on: Boolean): Unit = {
    val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    if (on) Host.activity.getWindow.addFlags(flag) else Host.activity.getWindow.clearFlags(flag)
  }

  /** Whether copyText reaches a clipboard: it does. */
  def clipboard(): Boolean = true
  def copyText(text: String): Unit = {
    val manager = Host.activity.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[android.content.ClipboardManager]
    manager.setPrimaryClip(android.content.ClipData.newPlainText("everyui", text))
  }

  /** No address bar: no path to show, push or follow. */
  def path(): String = Host.path
  def pushPath(path: String): Unit = Host.path = path
  def onPath(f: String => Unit): Unit = ()

  /** Run `f` on the main thread, soon: how a reply from another thread reaches the components. */
  /** No browser history here: the screen manager keeps its own. */
  def historyBack(): Boolean = false

  /** An ARB file's messages (everyui.i18n.Arb). */
  def parseMessages(arb: String): Map[String, String] = everyui.i18n.Arb.parse(arb)

  /** A text file among the APK's assets (a translation), or "" if it is not there. */
  def readText(path: String, done: String => Unit): Unit = done(
    try {
      val in = Host.activity.getAssets.open(path)
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
    } catch { case _: Exception => "" })

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
  /** `f` on the UI thread once `seconds` have passed. */
  def after(seconds: Double, f: () => Unit): Unit =
    new android.os.Handler(android.os.Looper.getMainLooper).postDelayed(() => f(), math.max(0L, (seconds * 1000.0).toLong))

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
    val dir = new java.io.File(Host.activity.getCacheDir, "everyui-store/" + storeSafe(space))
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

  def screenWidth(): Double =
    if (Host.viewportWidth > 0.0) Host.viewportWidth else windowPixels()._1.toDouble / Host.scale
  def screenHeight(): Double =
    if (Host.viewportHeight > 0.0) Host.viewportHeight else windowPixels()._2.toDouble / Host.scale

  /** Screen pixels to a layout unit: the window's real width over the width it is laid out in — the display's density,
    * or whatever a `--es viewport` launch set the layout to. */
  def pixelRatio(): Double = {
    val laid = screenWidth()
    if (laid > 0.0) windowPixels()._1.toDouble / laid else 1.0
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
  def onKey(f: (String, Boolean) => Boolean): Unit = Host.key = f
  /** The modifiers held with the key being reported (a keyboard's; a remote holds none), as the activity reads them. */
  def modifiers(): everyui.event.Modifiers = Host.modifiers
  /** The button of the pointer event being reported: 0 the primary (a finger), 1 the middle, 2 the secondary. */
  def pointerButton(): Int = Host.pointerButton
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

  def onResize(f: () => Unit): Unit =
    Host.root.addOnLayoutChangeListener((_, l, t, r, b, oldL, oldT, oldR, oldB) =>
      if (r - l != oldR - oldL || b - t != oldB - oldT) f()
    )

  /** A number from 0 up to 1, at random. */
  def random(): Double = math.random()
  def sqrt(x: Double): Double = math.sqrt(x)
  def pow(x: Double, y: Double): Double = math.pow(x, y)
  def sin(x: Double): Double = math.sin(x)
  def cos(x: Double): Double = math.cos(x)

  /** A site-absolute asset path (`/images/icon.png`) is that path inside the APK's assets. */
  def resolvePath(path: String): String = path.stripPrefix("/")

  /** Decode `uri` and report its natural size. */
  def loadImage(uri: String, loaded: (Double, Double) => Unit): Unit =
    Host.bitmapThen(uri)(b => if (b == null) loaded(0.0, 0.0) else loaded(b.getWidth.toDouble, b.getHeight.toDouble))

  /** Bundled faces load synchronously from the APK. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit = {
    Host.typeface(Faces(family, weight))
    loaded()
  }

  def whenFontsReady(f: () => Unit): Unit = f()

  /** Run `tick` once per display frame. */
  def animate(tick: () => Unit): Unit = {
    val callback = new Choreographer.FrameCallback {
      override def doFrame(frameTimeNanos: Long): Unit = {
        tick()
        Choreographer.getInstance.postFrameCallback(this)
      }
    }
    Choreographer.getInstance.postFrameCallback(callback)
  }
}
