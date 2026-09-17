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
  // what a video or an audio node plays (`Nodes.video`, `Nodes.audio`)
  private[render] var media: Option[AndroidMedia] = None

  if (view.getLayoutParams == null)
    view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

  def getText(): String = view match {
    case t: TextView => t.getText.toString
    case _ => ""
  }

  def setText(text: String): Unit = view match {
    case t: TextView => t.setText(text)
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
      t.setTypeface(if (family.nonEmpty) Host.typeface(face) else Typeface.create(Typeface.DEFAULT, weight, false))
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
      case _: TextView => face.lineHeight(fontSize)
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
        else paint.getRunAdvance(whole, 0, whole.length, 0, whole.length, false, whole.length).toDouble
      laidOutHeight =
        if (wrapping) {
          android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, at).setIncludePad(false)
            .setLineSpacing(t.getLineSpacingExtra, t.getLineSpacingMultiplier).build().getHeight.toDouble
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

  def append(child: RenderNode): Unit = view match {
    case g: ViewGroup => g.addView(child.view)
    case _ => ()
  }

  /** Detach from the parent, if attached. */
  def setMediaSource(url: String): Unit = media.foreach(_.setSource(url))
  def setMediaPlaying(playing: Boolean): Unit = media.foreach(_.setPlaying(playing))
  def setMediaLoop(loop: Boolean): Unit = media.foreach(_.setLoop(loop))
  def setMediaMuted(muted: Boolean): Unit = media.foreach(_.setMuted(muted))
  def setMediaVolume(volume: Double): Unit = media.foreach(_.setVolume(volume))
  def setMediaCover(cover: Boolean): Unit = media.foreach(_.setCover(cover))
  def getMediaPosition(): Double = media.map(_.position()).getOrElse(0.0)
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
    params.width = math.max(0L, math.round(width)).toInt
    view.setLayoutParams(params)
  }

  def setHeight(height: Double): Unit = {
    explicitHeight = height
    val params = view.getLayoutParams
    params.height = math.max(0L, math.round(height)).toInt
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
  /** A blur, on Android 12 and later (a RenderEffect); drawn sharp before. */
  def setBlur(radius: Double): Unit = if (android.os.Build.VERSION.SDK_INT >= 31) {
    view.setRenderEffect(if (radius <= 0.0) null
    else android.graphics.RenderEffect.createBlurEffect(radius.toFloat, radius.toFloat, android.graphics.Shader.TileMode.CLAMP))
  }
  def setShadow(x: Double, y: Double, blur: Double, hex: String): Unit =
    if (hex.isEmpty) view.setElevation(0f)
    else {
      val colour = android.graphics.Color.parseColor(hex)
      view.setOutlineSpotShadowColor(colour)
      view.setOutlineAmbientShadowColor(colour)
      view.setElevation((blur / 2.0).toFloat)
    }
  /** A hardware layer: the view is drawn into a texture once, and again only when it changes. */
  def setCached(cached: Boolean): Unit =
    view.setLayerType(if (cached) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE, null)

  private var tracks: List[everyui.motion.Track] = Nil
  private var animators: List[android.animation.Animator] = Nil

  /** Each track an animator of the view's own properties, repeating: a position moves the translation the node is
    * placed by, from where it is placed. */
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
  /** Styled runs as spans of one text: each run's colour, marking, weight, slant, size and strike (a link is drawn,
    * not followed; its family is the paragraph's). */
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
          if (bold || run.italic) span(new android.text.style.StyleSpan(
            if (bold && run.italic) Typeface.BOLD_ITALIC else if (bold) Typeface.BOLD else Typeface.ITALIC), from, to)
          if (run.size > 0.0) span(new android.text.style.AbsoluteSizeSpan(math.round(run.size).toInt), from, to)
          if (run.strike) span(new android.text.style.StrikethroughSpan(), from, to)
        }
      }
      t.setText(text)
    case _ => ()
  }

  private var gradientAt: Array[Double] = Array.empty
  private var gradientAngle: Double = 0.0

  private def paintBackground(): Unit = {
    val quarter = ((math.round(gradientAngle / 90.0) % 4 + 4) % 4).toInt
    if (gradient.length >= 2 && radius == 0f && borderWidth == 0 && math.abs(gradientAngle / 90.0 - math.round(gradientAngle / 90.0)) < 0.01)
      view.setBackground(RenderNode.strip(gradient, gradientAt, quarter))
    else paintShape()
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
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click") with its root coordinates. Touches
    * drive down/move/up and a click on an up that stays on the view; a mouse or a hover drives enter/leave. */
  def onPointer(kind: PointerKind, f: (Double, Double) => Unit): Unit = {
    pointer += kind -> f
    if (kind == PointerKind.Wheel) view.setOnGenericMotionListener { (_, e) =>
      if (e.getAction == MotionEvent.ACTION_SCROLL) {
        f(-e.getAxisValue(MotionEvent.AXIS_HSCROLL) * 40.0, -e.getAxisValue(MotionEvent.AXIS_VSCROLL) * 40.0)
        true
      }
      else false
    }
    if (!listening) {
      listening = true
      view.setOnTouchListener((_, e) => touch(e))
      view.setOnHoverListener { (_, e) =>
        if (e.getAction == MotionEvent.ACTION_HOVER_ENTER) fire(PointerKind.Enter, e)
        else if (e.getAction == MotionEvent.ACTION_HOVER_EXIT) fire(PointerKind.Leave, e)
        false
      }
    }
  }

  private val pointer = mutable.Map.empty[PointerKind, (Double, Double) => Unit]
  private var listening = false
  private def fire(kind: PointerKind, e: MotionEvent): Unit = {
    val at = Array(0, 0)
    Host.root.getLocationOnScreen(at)
    pointer.get(kind).foreach(f => f((e.getRawX - at(0)) / Host.scale, (e.getRawY - at(1)) / Host.scale))
  }
  private def touch(e: MotionEvent): Boolean = {
    val action = e.getActionMasked
    if (action == MotionEvent.ACTION_DOWN) fire(PointerKind.Down, e)
    else if (action == MotionEvent.ACTION_MOVE) fire(PointerKind.Move, e)
    else if (action == MotionEvent.ACTION_UP) {
      fire(PointerKind.Up, e)
      val inside = e.getX >= 0 && e.getY >= 0 && e.getX <= view.getWidth && e.getY <= view.getHeight
      if (inside) fire(PointerKind.Click, e)
    }
    else if (action == MotionEvent.ACTION_CANCEL) fire(PointerKind.Up, e)
    true
  }

  /** The shared ScrollContainer shifts the content; the node only clips. */
  def setScrollable(vertical: Boolean, horizontal: Boolean): Unit = ()
  def setScrollLeft(x: Double): Unit = ()
  /** The shared ScrollContainer draws no scrollbar here. */
  def setScrollbarColor(hex: String): Unit = ()
  def setScrollTop(y: Double): Unit = ()
  /** Whether the platform draws this: what a scroll has out of sight is not drawn at all. Its place and its size are
    * kept (INVISIBLE, not GONE), since the layout is ours and not the view system's. */
  def setVisible(visible: Boolean): Unit =
    view.setVisibility(if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE)
  def setOpacity(opacity: Double): Unit = view.setAlpha(opacity.toFloat)

  def setClip(width: Double, height: Double): Unit = view match {
    case g: ViewGroup => g.setClipChildren(true); g.setClipToPadding(true); g.setClipToOutline(true)
    case _ => ()
  }
  def setTooltip(text: String): Unit = view.setTooltipText(if (text.isEmpty) null else text)

  def setPositionType(name: String): Unit = ()

  /** `"normal"` wraps a text node at its width; anything else keeps it on one line. */
  def setWhiteSpace(value: String): Unit = view match {
    case _: EditText => ()
    case t: TextView =>
      wrapping = value == "normal"
      t.setMaxLines(if (wrapping) Integer.MAX_VALUE else 1)
      t.setHorizontallyScrolling(!wrapping)
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
      e.setOnEditorActionListener((_, _, _) => {
        f()
        true
      })
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

  /** "start", "center" or "end", resolved against the root's layout direction. */
  def setTextAlign(align: String): Unit = view match {
    case t: TextView => t.setTextAlignment(
      if (align == "center") View.TEXT_ALIGNMENT_CENTER else if (align == "end") View.TEXT_ALIGNMENT_VIEW_END else View.TEXT_ALIGNMENT_VIEW_START)
    case _ => ()
  }

  /** The picture in one colour (`"#rrggbb"`), its transparency kept, or as it is for `""`. */
  def setTint(hex: String): Unit = view match {
    case i: ImageView =>
      if (hex.isEmpty) i.clearColorFilter()
      else i.setColorFilter(RenderNode.color(hex), android.graphics.PorterDuff.Mode.SRC_IN)
    case _ => ()
  }

  def setImageSource(uri: String): Unit = view match {
    case i: ImageView => if (uri.nonEmpty) Host.bitmapThen(uri)(b => if (b != null) i.setImageBitmap(b))
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

  private def spec(size: Int): Int =
    if (size >= 0) View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
}

/** What the Activity hands the backend: itself, the render root, and how it was launched. */
object Host {
  var activity: Activity = null
  var root: NodeGroup = null
  var freeze: Double = -1.0
  var viewportWidth: Double = -1.0
  var viewportHeight: Double = -1.0

  lazy val settings: android.content.SharedPreferences = activity.getSharedPreferences("everyui", Context.MODE_PRIVATE)

  /** The key hook (Platform.onKey), fed by the activity. */
  var key: (String, Boolean) => Boolean = null

  /** An Android key by the web's name: the remote's keys by their names, anything typed by its character. */
  def keyName(e: KeyEvent): String = {
    val code = e.getKeyCode
    if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) "Enter"
    else if (code == KeyEvent.KEYCODE_BACK) "Back"
    else if (code == KeyEvent.KEYCODE_ESCAPE) "Escape"
    else if (code == KeyEvent.KEYCODE_DPAD_UP) "ArrowUp"
    else if (code == KeyEvent.KEYCODE_DPAD_DOWN) "ArrowDown"
    else if (code == KeyEvent.KEYCODE_DPAD_LEFT) "ArrowLeft"
    else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) "ArrowRight"
    else if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) "MediaPlayPause"
    else if (e.getUnicodeChar > 0) e.getUnicodeChar.toChar.toString
    else KeyEvent.keyCodeToString(code)
  }

  private val typefaces = mutable.Map.empty[String, Typeface]
  private val bitmaps = mutable.Map.empty[String, Bitmap]

  def typeface(face: Face): Typeface =
    typefaces.getOrElseUpdate(face.file, Typeface.createFromAsset(activity.getAssets, "fonts/" + face.file))

  /** An APK asset, decoded at its own pixel size (assets are never density-scaled). */
  def bitmap(uri: String): Bitmap = bitmaps.getOrElseUpdate(uri, {
    val in = activity.getAssets.open(uri)
    try BitmapFactory.decodeStream(in) finally in.close()
  })

  /** A few threads fetch and decode every image between them, and never every core: a screen of artwork (a TV's rails:
    * a hundred posters at once) started a thread apiece, and the interface had nothing left to draw with. */
  private lazy val loaders: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newFixedThreadPool(math.max(2, Runtime.getRuntime.availableProcessors - 1), r => {
      val thread = new Thread(r, "everyui-images")
      thread.setDaemon(true)
      thread.setPriority(Thread.MIN_PRIORITY)
      thread
    })

  /** What is on its way, and everyone waiting on it. */
  private val pending = mutable.Map.empty[String, List[Bitmap => Unit]]

  /** A bitmap by URI, then `done` on the main thread: at once from the APK or the cache, after a download on one of
    * the loading threads (`null` if it cannot be had). */
  def bitmapThen(uri: String)(done: Bitmap => Unit): Unit = bitmaps.get(uri) match {
    case Some(b) => done(b)
    case None if uri.startsWith("http") =>
      // one fetch per address, however many ask for it: a rail shows the same artwork more than once, and each
      // picture asks twice over (its natural size, then its pixels)
      pending.get(uri) match {
        case Some(waiting) => pending(uri) = done :: waiting
        case None =>
          pending(uri) = List(done)
          loaders.execute(() => {
            val b =
              try {
                val in = new java.net.URL(uri).openStream()
                try BitmapFactory.decodeStream(in) finally in.close()
              } catch {
                case t: Exception =>
                  Platform.logError("Loading " + uri, t)
                  null
              }
            activity.runOnUiThread(() => {
              if (b != null) bitmaps(uri) = b
              pending.remove(uri).getOrElse(Nil).reverse.foreach(_(b))
            })
          })
      }
    case None => done(bitmap(uri))
  }

  /** Layout units to screen pixels: the display's density, so a TV or a phone is laid out in the same units its own
    * apps are (1 on a launch that pins a viewport, whose frame is compared pixel for pixel). */
  var scale: Double = 1.0
  /** The path the app is at: `--es path /somewhere` to start there, then wherever it last pushed. */
  var path: String = ""
}

/** Backend node factories. */
object Nodes {
  /** The Activity's content view. */
  def root(): RenderNode = new RenderNode(Host.root)

  /** A video: a `TextureView` the player draws into. */
  def video(): RenderNode = {
    val texture = new android.view.TextureView(Host.activity)
    val node = new RenderNode(texture)
    node.media = Some(new AndroidMedia(Some(texture)))
    node
  }

  /** Sound: an empty view, with nothing to draw. */
  def audio(): RenderNode = {
    val node = new RenderNode(new View(Host.activity))
    node.media = Some(new AndroidMedia(None))
    node
  }

  /** A container — a [[NodeGroup]] other nodes are appended to. */
  def container(): RenderNode = new RenderNode(new NodeGroup(Host.activity))

  /** A text node — a single-line `TextView` with no padding of its own. */
  def text(): RenderNode = {
    val t = new TextView(Host.activity)
    t.setIncludeFontPadding(false)
    t.setFallbackLineSpacing(false)
    t.setPadding(0, 0, 0, 0)
    t.setMaxLines(1)
    t.setHorizontallyScrolling(true)
    t.setTextColor(android.graphics.Color.parseColor("#1c1b1f"))
    t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32.0f)
    // fractional glyph advances, so text measures as it does in a browser rather than snapped to whole pixels
    t.setPaintFlags(t.getPaintFlags | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG)
    new RenderNode(t)
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
    val i = new ImageView(Host.activity)
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

  /** Packaged as APK assets. */
  def imagePath(name: String): String = "images/" + name

  /** The window, in pixels — or the viewport the launch asked for (`--es viewport 1920x1080`). */
  def kind: PlatformKind = PlatformKind.Android
  /** An Android TV is driven by its remote; a phone or tablet by touch. */
  def remoteNavigation(): Boolean =
    Host.activity.getSystemService(Context.UI_MODE_SERVICE).asInstanceOf[android.app.UiModeManager].getCurrentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
  def touchSupport(): Boolean = Host.activity.getPackageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
  def pointerSupport(): Boolean = false
  def keyboardSupport(): Boolean = Host.activity.getResources.getConfiguration.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS

  /** The shared ScrollContainer scrolls. */
  def nativeScroll(): Boolean = false
  def openUrl(url: String): Unit =
    Host.activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
  /** An app has no page to leave: the browser opens `url`. */
  def navigate(url: String): Unit = openUrl(url)
  /** The activity's title (what the recents screen calls it). */
  def setTitle(title: String): Unit = Host.activity.setTitle(title)
  /** The screen kept on (while something is shown that is meant to be watched without a touch), or let sleep. */
  def keepAwake(on: Boolean): Unit = {
    val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    if (on) Host.activity.getWindow.addFlags(flag) else Host.activity.getWindow.clearFlags(flag)
  }

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
      try new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) finally in.close()
    } catch { case _: Exception => "" })

  /** The character at a Unicode code point, as a string. */
  def fromCodePoint(codePoint: Int): String = new String(Character.toChars(codePoint))

  /** The device's language, as a BCP 47 tag (`en-US`). */
  def language(): String = java.util.Locale.getDefault.toLanguageTag

  /** The app's language: the platform chooses its own faces for the script, so there is nothing to do. */
  def setLanguage(code: String): Unit = ()

  /** The root's layout direction: text and fields start where the language reads from. */
  def setDirection(rtl: Boolean): Unit =
    Host.root.setLayoutDirection(if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)

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

  /** The device's model name. */
  def deviceName(): String = android.os.Build.MODEL

  /** Android has text fields of its own. */
  def nativeInput(): Boolean = true
  /** Only a platform without text fields asks its keyboard for a value. */
  def editText(title: String, value: String, secure: Boolean, done: String => Unit): Unit = ()

  /** A registered face loads from the APK's `assets/fonts/` when first used. */
  def registerFont(face: Face): Unit = ()

  def screenWidth(): Double =
    if (Host.viewportWidth > 0.0) Host.viewportWidth
    else Host.activity.getWindowManager.getCurrentWindowMetrics.getBounds.width.toDouble / Host.scale
  def screenHeight(): Double =
    if (Host.viewportHeight > 0.0) Host.viewportHeight
    else Host.activity.getWindowManager.getCurrentWindowMetrics.getBounds.height.toDouble / Host.scale

  /** `--es freeze <seconds>` on the launch intent: step the animation that far on a virtual clock and stop
    * (tools/check.sh). Negative when absent. */
  def freezeSeconds(): Double = Host.freeze

  /** `--es name value` on the launch intent, or "". */
  def launchArg(name: String): String = {
    val value = Host.activity.getIntent.getStringExtra(name)
    if (value == null) "" else value
  }

  /** Finish the activity (a demo that is done); the process follows (EveryUIActivity.onDestroy). */
  def quit(): Unit = Host.activity.finish()

  /** `millis` since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
  def localDateTime(millis: Long): String =
    java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
      .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

  /** No page to be served from: an app names its server (`--es live`). */
  def webSocketOrigin(): String = ""

  /** The frozen frame is complete. The check reads it from logcat and screenshots the device itself. */
  def frozen(): Unit = ()

  /** Called whenever the root changes size (a rotation, a resized window). */
  /** Every key the activity sees, by the web's name, pressed or released. */
  def onKey(f: (String, Boolean) => Boolean): Unit = Host.key = f

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
