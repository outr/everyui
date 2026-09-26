package everyui.render

import android.graphics.{Bitmap, Canvas, Paint, RectF}
import android.view.View

/** A blur drawn by hand, for Android before 12, which has no RenderEffect and drew every blur sharp: a screensaver's
  * photo sat over a sharp copy of itself rather than a soft one.
  *
  * What the view draws (`sharp`) is drawn small into a bitmap of its own, a pixel of it several of the view's, so the
  * blur is a few pixels wide however wide it is asked to be. That is blurred off the thread that draws (three box
  * passes each way, which come to a gaussian), and drawn back stretched over the view with filtering. It is taken again
  * only when what the view draws changes (`changed`) or the blur grows past it. A blur easing away (a quote's picture
  * coming clear) fades the blurred picture out over the sharp one rather than blurring every frame afresh, and is
  * blurred at its new spread once it stops moving.
  *
  * The spread is a gaussian's standard deviation in layout units, as the web's `filter: blur()` takes it. */
final class SoftBlur(view: View, sharp: Canvas => Unit) {
  private var sigma: Double = 0.0
  /** The blurred picture drawn over the view, and the spread it was blurred at. */
  private var shown: Bitmap = null
  private var shownSigma: Double = 0.0
  /** Whether what the view draws has changed since it was last taken. */
  private var stale: Boolean = true
  /** Whether a picture is being blurred. */
  private var busy: Boolean = false
  /** Whether the view is being invalidated by the blur itself, which changes nothing it draws. */
  private var redrawing: Boolean = false
  private val paint = new Paint(Paint.FILTER_BITMAP_FLAG)
  private val box = new RectF
  private val settle: Runnable = () => if (sigma > 0.0 && sigma < shownSigma) {
    stale = true
    redraw()
  }

  /** Whether there is a blur to draw. */
  def on: Boolean = sigma > 0.0

  def set(spread: Double): Unit = {
    sigma = math.max(0.0, spread)
    view.removeCallbacks(settle)
    if (sigma <= 0.0) {
      shown = null
      shownSigma = 0.0
      stale = true
    }
    // eased down, the picture fades towards the sharp one; once the spread has stayed put a moment it is blurred again
    else if (sigma < shownSigma) view.postDelayed(settle, SoftBlur.settleMillis)
    redraw()
  }

  /** What the view draws has changed (unless it is the blur's own redraw). */
  def changed(): Unit = if (!redrawing) stale = true

  /** Whether an invalidation is the blur's own. */
  def redrawn: Boolean = redrawing

  /** Draw the view blurred: the blurred picture over the sharp one while it fades, the blurred one alone otherwise. */
  def draw(canvas: Canvas): Unit = {
    val w = view.getWidth
    val h = view.getHeight
    if (w <= 0 || h <= 0) sharp(canvas)
    else {
      if ((stale || sigma > shownSigma) && !busy) take(w, h)
      if (shown != null) {
        val mix = math.min(1.0, sigma / shownSigma)
        if (mix < 1.0) sharp(canvas)
        paint.setAlpha(math.round(mix * 255.0).toInt)
        box.set(0f, 0f, w.toFloat, h.toFloat)
        canvas.drawBitmap(shown, null, box, paint)
      }
    }
  }

  private def redraw(): Unit = {
    redrawing = true
    try view.invalidate() finally redrawing = false
  }

  /** Draw the view small, and blur that on the blurring thread. */
  private def take(w: Int, h: Int): Unit = {
    val level = sigma
    // layout units a pixel: a sixth of the spread, but never finer than the screen's own pixels, nor so fine that the
    // picture is more than a few hundred thousand pixels
    val step = math.max(level / SoftBlur.pixelsPerSigma,
      math.max(1.0 / math.max(1.0, Host.scale), math.sqrt(w.toDouble * h.toDouble / SoftBlur.mostPixels)))
    val bw = math.max(1, math.ceil(w / step).toInt)
    val bh = math.max(1, math.ceil(h / step).toInt)
    val small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val canvas = new Canvas(small)
    canvas.scale(bw.toFloat / w.toFloat, bh.toFloat / h.toFloat)
    try sharp(canvas) catch { case t: Throwable => Platform.logError("Drawing to blur", t) }
    stale = false
    busy = true
    SoftBlur.blurring.execute(() => {
      try SoftBlur.blur(small, level / step)
      catch { case t: Throwable => Platform.logError("Blurring", t) }
      view.post(() => {
        busy = false
        if (sigma > 0.0) {
          shown = small
          shownSigma = level
          if (sigma < level) {
            view.removeCallbacks(settle)
            view.postDelayed(settle, SoftBlur.settleMillis)
          }
        }
        redraw()
      })
    })
  }
}

object SoftBlur {
  /** How many of the small picture's pixels the spread is: enough for the box passes to come to a smooth gaussian. */
  private val pixelsPerSigma: Double = 6.0
  /** The most pixels the small picture may have, however little it is blurred. */
  private val mostPixels: Double = 200000.0
  /** How long a spread eased down must stay put before it is blurred afresh. */
  private val settleMillis: Long = 200L

  /** The one thread pictures are blurred on, at a background priority. */
  private lazy val blurring: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newSingleThreadExecutor(r => {
      val thread = new Thread(() => {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        r.run()
      }, "everyui-blur")
      thread.setDaemon(true)
      thread
    })

  /** RenderEffect's radius for a gaussian's standard deviation: the platform takes a radius and makes it a deviation
    * of 0.57735 of it plus a half, so a radius passed as the deviation blurred at little more than half the spread the
    * web and the desktop draw. */
  def radius(sigma: Double): Double = math.max(0.01, (sigma - 0.5) / 0.57735)

  /** Blur `bitmap` in place by a gaussian of deviation `sigma` pixels: three box blurs each way, their widths chosen
    * to come to that gaussian (Kutskir's), the edges held (as a clamped blur has them). The pixels are the bitmap's
    * own, premultiplied, so what is see-through blurs without a dark fringe; each byte is a channel, whatever the
    * order. */
  def blur(bitmap: Bitmap, sigma: Double): Unit = {
    val w = bitmap.getWidth
    val h = bitmap.getHeight
    val sizes = boxes(sigma)
    if (sizes.exists(_ > 1)) {
      val buffer = java.nio.IntBuffer.allocate(w * h)
      bitmap.copyPixelsToBuffer(buffer)
      val a = buffer.array()
      val b = new Array[Int](w * h)
      sizes.foreach { size =>
        val r = (size - 1) / 2
        if (r > 0) {
          pass(a, b, h, w, r, 1, w)
          pass(b, a, w, h, r, w, 1)
        }
      }
      buffer.rewind()
      bitmap.copyPixelsFromBuffer(buffer)
    }
  }

  /** Three box widths, odd, whose blurs one after another come to a gaussian of deviation `sigma`. */
  private def boxes(sigma: Double): List[Int] = {
    val ideal = math.sqrt(12.0 * sigma * sigma / 3.0 + 1.0)
    val lower = { val l = math.floor(ideal).toInt; if (l % 2 == 0) l - 1 else l }
    val upper = lower + 2
    val many = math.round((12.0 * sigma * sigma - 3.0 * lower * lower - 12.0 * lower - 9.0) / (-4.0 * lower - 4.0)).toInt
    List.tabulate(3)(i => if (i < many) lower else upper).map(s => math.max(1, s))
  }

  /** One box blur of radius `r` along each of `lines` lines of `length` pixels, from `src` into `dst`: a pixel `step`
    * from the one before along a line, each line `stride` from the one before. */
  private def pass(src: Array[Int], dst: Array[Int], lines: Int, length: Int, r: Int, step: Int, stride: Int): Unit = {
    val width = 2 * r + 1
    val half = width / 2
    val last = length - 1
    var line = 0
    while (line < lines) {
      val start = line * stride
      var s0 = 0
      var s1 = 0
      var s2 = 0
      var s3 = 0
      var i = -r
      while (i <= r) {
        val p = src(start + math.min(last, math.max(0, i)) * step)
        s0 += p & 0xff
        s1 += (p >>> 8) & 0xff
        s2 += (p >>> 16) & 0xff
        s3 += p >>> 24
        i += 1
      }
      var x = 0
      while (x < length) {
        dst(start + x * step) = ((s0 + half) / width) | (((s1 + half) / width) << 8) | (((s2 + half) / width) << 16) |
          (((s3 + half) / width) << 24)
        val in = src(start + math.min(last, x + r + 1) * step)
        val out = src(start + math.max(0, x - r) * step)
        s0 += (in & 0xff) - (out & 0xff)
        s1 += ((in >>> 8) & 0xff) - ((out >>> 8) & 0xff)
        s2 += ((in >>> 16) & 0xff) - ((out >>> 16) & 0xff)
        s3 += (in >>> 24) - (out >>> 24)
        x += 1
      }
      line += 1
    }
  }
}
