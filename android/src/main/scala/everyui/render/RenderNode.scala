package everyui.render

import android.app.Activity
import android.content.Context
import android.graphics.{Bitmap, BitmapFactory, Paint, Typeface}
import android.util.{Log, TypedValue}
import android.view.{Choreographer, View, ViewGroup}
import android.widget.{ImageView, TextView}
import everyui.font.{Face, Faces}

import scala.collection.mutable

/** **The one invention.** The abstract render node, Android edition: a native `View` — a `TextView`, an `ImageView`,
  * or a [[NodeGroup]] holding other nodes.
  *
  * Signature-for-signature the same as its DOM, SceneGraph and JavaFX twins. Units are the screen's pixels, so a
  * 1920x1080 Android TV lays out exactly as the Roku's 1920x1080 design space does. A node's position is its
  * translation: moving it never asks Android for a layout pass, only a size or font change does.
  */
class RenderNode(val view: View):
  private var left: Double = 0.0
  private var top: Double = 0.0
  private var rotation: Double = 0.0
  private var explicitWidth: Double = -1.0
  private var explicitHeight: Double = -1.0
  private var face: Face = Faces.OpenSansRegular
  private var fontSize: Double = 32.0

  if view.getLayoutParams == null then
    view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

  def getText(): String = view match
    case t: TextView => t.getText.toString
    case _ => ""

  def setText(text: String): Unit = view match
    case t: TextView => t.setText(text)
    case _ => ()

  def getX(): Double = left
  def setX(x: Double): Unit =
    left = x
    view.setTranslationX(x.toFloat)

  def getY(): Double = top
  def setY(y: Double): Unit =
    top = y
    placeY()

  /** Radians, clockwise, about the node's own centre — a View's rotation is degrees, clockwise, and pivots on its
    * centre unless told otherwise. */
  def getRotation(): Double = rotation
  def setRotation(radians: Double): Unit =
    rotation = radians
    view.setRotation(math.toDegrees(radians).toFloat)

  def setFontSize(size: Double): Unit = view match
    case t: TextView =>
      fontSize = size
      t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size.toFloat)
      placeY()
    case _ => ()

  /** `"#rrggbb"`. */
  def setColor(hex: String): Unit = view match
    case t: TextView => t.setTextColor(android.graphics.Color.parseColor(hex))
    case _ => ()

  /** A bundled face (`assets/fonts/`, packaged as the APK's `assets/fonts/`): family, CSS weight, size in pixels. */
  def setFont(family: String, weight: Int, size: Double): Unit = view match
    case t: TextView =>
      face = Faces(family, weight)
      fontSize = size
      t.setTypeface(if family.nonEmpty then Host.typeface(face) else Typeface.create(Typeface.DEFAULT, weight, false))
      t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size.toFloat)
      placeY()
    case _ => ()

  /** The laid-out size in pixels. Text is measured with its real font (its advance width, at fractional size) and is
    * one shared line (everyui.font.Face) tall. `Paint.measureText` rounds the width UP to a whole pixel; the run
    * advance is the exact one. */
  def getWidth(): Double =
    if explicitWidth >= 0.0 then explicitWidth
    else view match
      case t: TextView =>
        val text = t.getText.toString
        t.getPaint.getRunAdvance(text, 0, text.length, 0, text.length, false, text.length).toDouble
      case _ => 0.0

  def getHeight(): Double =
    if explicitHeight >= 0.0 then explicitHeight
    else view match
      case _: TextView => face.lineHeight(fontSize)
      case _ => 0.0

  def append(child: RenderNode): Unit = view match
    case g: ViewGroup => g.addView(child.view)
    case _ => ()

  /** Detach from the parent, if attached. */
  def remove(): Unit = view.getParent match
    case g: ViewGroup => g.removeView(view)
    case _ => ()

  def setWidth(width: Double): Unit =
    explicitWidth = width
    val params = view.getLayoutParams
    params.width = math.max(0L, math.round(width)).toInt
    view.setLayoutParams(params)

  def setHeight(height: Double): Unit =
    explicitHeight = height
    val params = view.getLayoutParams
    params.height = math.max(0L, math.round(height)).toInt
    view.setLayoutParams(params)

  /** `"#rrggbb"`, or `""` for transparent. */
  private var background: Int = 0
  private var radius: Float = 0f
  def setBackgroundColor(hex: String): Unit =
    background = if hex.isEmpty then 0 else android.graphics.Color.parseColor(hex)
    paintBackground()
  def setRadius(r: Double): Unit =
    radius = r.toFloat
    paintBackground()
  private def paintBackground(): Unit =
    val drawable = new android.graphics.drawable.GradientDrawable()
    drawable.setColor(background)
    drawable.setCornerRadius(radius)
    view.setBackground(drawable)

  /** Every node is placed by its translation, so there is no position type to set. */
  def setPositionType(name: String): Unit = ()

  /** Text nodes are single lines. */
  def setWhiteSpace(value: String): Unit = ()

  def setImageSource(uri: String): Unit = view match
    case i: ImageView => if uri.nonEmpty then i.setImageBitmap(Host.bitmap(uri))
    case _ => ()

  /** A `TextView` draws its baseline `-ascent` (whole pixels) below its top: shift it so the baseline lands where the
    * shared line box puts it. */
  private def placeY(): Unit = view match
    case t: TextView =>
      val ascent = -t.getPaint.getFontMetricsInt.ascent
      view.setTranslationY((top + face.baseline(fontSize) - ascent).toFloat)
    case _ => view.setTranslationY(top.toFloat)

/** A container: each child is laid out at its own size at the origin and placed by its translation — absolute
  * positioning, as the DOM and SceneGraph backends have it. Children are never squeezed to fit the group, and draw
  * outside its bounds, like a `div` without `overflow`. */
final class NodeGroup(context: Context) extends ViewGroup(context):
  setClipChildren(false)
  setClipToPadding(false)

  override def onMeasure(widthSpec: Int, heightSpec: Int): Unit =
    var i = 0
    while i < getChildCount do
      val child = getChildAt(i)
      val params = child.getLayoutParams
      child.measure(spec(params.width), spec(params.height))
      i += 1
    setMeasuredDimension(View.getDefaultSize(0, widthSpec), View.getDefaultSize(0, heightSpec))

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit =
    var i = 0
    while i < getChildCount do
      val child = getChildAt(i)
      child.layout(0, 0, child.getMeasuredWidth, child.getMeasuredHeight)
      i += 1

  override def shouldDelayChildPressedState(): Boolean = false

  private def spec(size: Int): Int =
    if size >= 0 then View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

/** What the Activity hands the backend: itself, the render root, and how it was launched. */
object Host:
  var activity: Activity = null
  var root: NodeGroup = null
  var freeze: Double = -1.0
  var viewportWidth: Double = -1.0
  var viewportHeight: Double = -1.0

  private val typefaces = mutable.Map.empty[String, Typeface]
  private val bitmaps = mutable.Map.empty[String, Bitmap]

  def typeface(face: Face): Typeface =
    typefaces.getOrElseUpdate(face.file, Typeface.createFromAsset(activity.getAssets, "fonts/" + face.file))

  /** An APK asset, decoded at its own pixel size (assets are never density-scaled). */
  def bitmap(uri: String): Bitmap = bitmaps.getOrElseUpdate(uri, {
    val in = activity.getAssets.open(uri)
    try BitmapFactory.decodeStream(in) finally in.close()
  })

/** Backend node factories. */
object Nodes:
  /** The Activity's content view. */
  def root(): RenderNode = new RenderNode(Host.root)

  /** A container — a [[NodeGroup]] other nodes are appended to. */
  def container(): RenderNode = new RenderNode(new NodeGroup(Host.activity))

  /** A text node — a single-line `TextView` with no padding of its own. */
  def text(): RenderNode =
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

  /** A Material Icons glyph, by codepoint, `size` pixels. */
  def icon(glyph: String, size: Double): RenderNode =
    val node = text()
    node.setFont("Material Icons", 400, size)
    node.setText(glyph)
    node

  /** An image node — an `ImageView` stretched to its box. */
  def image(uri: String, width: Double, height: Double): RenderNode =
    val i = new ImageView(Host.activity)
    i.setScaleType(ImageView.ScaleType.FIT_XY)
    val node = new RenderNode(i)
    node.setWidth(width)
    node.setHeight(height)
    node.setImageSource(uri)
    node

/** The handful of non-render platform services the slice needs. */
object Platform:
  /** Logcat, tag `everyui`. */
  def log(message: String): Unit = Log.i("everyui", "[ui] " + message)

  /** A failure with its cause: logcat gets the stack. */
  def logError(message: String, t: Throwable): Unit =
    Log.i("everyui", "[ui] ERROR " + message + ": " + t.toString)
    Log.e("everyui", message, t)

  def ticks(): Double = System.nanoTime() / 1.0e6

  /** Packaged as APK assets. */
  def imagePath(name: String): String = "images/" + name

  /** The window, in pixels — or the viewport the launch asked for (`--es viewport 1920x1080`). */
  def screenWidth(): Double =
    if Host.viewportWidth > 0.0 then Host.viewportWidth
    else Host.activity.getWindowManager.getCurrentWindowMetrics.getBounds.width.toDouble
  def screenHeight(): Double =
    if Host.viewportHeight > 0.0 then Host.viewportHeight
    else Host.activity.getWindowManager.getCurrentWindowMetrics.getBounds.height.toDouble

  /** `--es freeze <seconds>` on the launch intent: step the animation that far on a virtual clock and stop
    * (tools/check.sh). Negative when absent. */
  def freezeSeconds(): Double = Host.freeze

  /** `--es name value` on the launch intent, or "". */
  def launchArg(name: String): String =
    val value = Host.activity.getIntent.getStringExtra(name)
    if value == null then "" else value

  /** Finish the activity (a demo that is done); the process follows (MainActivity.onDestroy). */
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
  def onResize(f: () => Unit): Unit =
    Host.root.addOnLayoutChangeListener((_, l, t, r, b, oldL, oldT, oldR, oldB) =>
      if r - l != oldR - oldL || b - t != oldB - oldT then f()
    )

  def sqrt(x: Double): Double = math.sqrt(x)
  def pow(x: Double, y: Double): Double = math.pow(x, y)
  def sin(x: Double): Double = math.sin(x)
  def cos(x: Double): Double = math.cos(x)

  /** A site-absolute asset path (`/images/icon.png`) is that path inside the APK's assets. */
  def resolvePath(path: String): String = path.stripPrefix("/")

  /** Decode `uri` and report its natural size. */
  def loadImage(uri: String, loaded: (Double, Double) => Unit): Unit =
    val bitmap = Host.bitmap(uri)
    loaded(bitmap.getWidth.toDouble, bitmap.getHeight.toDouble)

  /** Bundled faces load synchronously from the APK. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit =
    Host.typeface(Faces(family, weight))
    loaded()

  def whenFontsReady(f: () => Unit): Unit = f()

  /** Run `tick` once per display frame. */
  def animate(tick: () => Unit): Unit =
    val callback = new Choreographer.FrameCallback:
      override def doFrame(frameTimeNanos: Long): Unit =
        tick()
        Choreographer.getInstance.postFrameCallback(this)
    Choreographer.getInstance.postFrameCallback(callback)
