package everyui.render

import android.graphics.{Bitmap, Canvas, Paint}
import android.graphics.drawable.{ColorDrawable, GradientDrawable}
import android.view.{PixelCopy, TextureView, View, ViewGroup}
import everyui.capture.SnapshotImage
import everyui.draw.Figure

/** Pictures of the app (`everyui.capture.Snapshot`). The window is copied from what the display shows (PixelCopy),
  * so a video on its own plane and the hardware layers are in it. A component's views are drawn into a bitmap of their
  * own, over the colour behind them, with the frame each video's TextureView holds drawn in where it is: a
  * SurfaceView's picture never passes through the app's drawing, so it is not in a component's picture. The bitmap is
  * compressed to a PNG on the work thread and answered on the main one. */
object SnapshotAccess {
  def supported(): Boolean = true

  def draws(): Boolean = true

  def node(node: RenderNode, scale: Double, done: Option[SnapshotImage] => Unit): Unit = {
    val view = node.view
    val width = math.round(node.getWidth() * scale).toInt
    val height = math.round(node.getHeight() * scale).toInt
    if (width <= 0 || height <= 0 || view.getWidth <= 0 || view.getHeight <= 0) done(None)
    else
      try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = new Canvas(bitmap)
        canvas.drawColor(behind(view))
        // the view's own pixels to the picture's: the display's density out, the scale asked for in
        val ratio = (scale / Host.scale).toFloat
        canvas.scale(ratio, ratio)
        view.draw(canvas)
        textures(view, view, canvas)
        compressed(bitmap, done)
      } catch {
        case t: Throwable =>
          Platform.logError("Snapshot: could not draw the component", t)
          done(None)
      }
  }

  def window(scale: Double, done: Option[SnapshotImage] => Unit): Unit = {
    val activity = Host.activity
    val decor = if (activity == null) null else activity.getWindow.getDecorView
    if (decor == null || decor.getWidth <= 0 || decor.getHeight <= 0) done(None)
    else if (android.os.Build.VERSION.SDK_INT < 26)
      // PixelCopy is Android 8's: before it the window as its views draw it, as a component's picture is drawn
      try {
        val ratio = scale / Host.scale
        val bitmap = Bitmap.createBitmap(math.max(1, math.round(decor.getWidth * ratio).toInt),
          math.max(1, math.round(decor.getHeight * ratio).toInt), Bitmap.Config.ARGB_8888)
        val canvas = new Canvas(bitmap)
        canvas.scale(ratio.toFloat, ratio.toFloat)
        decor.draw(canvas)
        textures(decor, decor, canvas)
        compressed(bitmap, done)
      } catch {
        case t: Throwable =>
          Platform.logError("Snapshot: could not draw the window", t)
          done(None)
      }
    else {
      val shown = Bitmap.createBitmap(decor.getWidth, decor.getHeight, Bitmap.Config.ARGB_8888)
      PixelCopy.request(activity.getWindow, shown, (result: Int) => {
        if (result != PixelCopy.SUCCESS) {
          Platform.log("Snapshot: the window could not be copied (" + result.toString + ")")
          shown.recycle()
          done(None)
        }
        else {
          val ratio = scale / Host.scale
          val width = math.max(1, math.round(shown.getWidth * ratio).toInt)
          val height = math.max(1, math.round(shown.getHeight * ratio).toInt)
          val sized = if (width == shown.getWidth && height == shown.getHeight) shown
            else {
              val scaled = Bitmap.createScaledBitmap(shown, width, height, true)
              shown.recycle()
              scaled
            }
          compressed(sized, done)
        }
      }, new android.os.Handler(android.os.Looper.getMainLooper))
    }
  }

  /** The figures as a drawing's view draws them, onto a bitmap of their own. */
  def figures(figures: List[Figure], width: Double, height: Double, scale: Double, background: Option[everyui.Color],
              done: Option[SnapshotImage] => Unit): Unit = {
    val bitmap = Bitmap.createBitmap(math.max(1, math.round(width * scale).toInt), math.max(1, math.round(height * scale).toInt),
      Bitmap.Config.ARGB_8888)
    val canvas = new Canvas(bitmap)
    background.foreach(c => canvas.drawColor(DrawingView.argb(c)))
    canvas.scale(scale.toFloat, scale.toFloat)
    val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
    paint.setStrokeCap(Paint.Cap.ROUND)
    paint.setStrokeJoin(Paint.Join.ROUND)
    figures.foreach { f =>
      val path = DrawingView.path(f)
      f.fill.foreach { c =>
        paint.setStyle(Paint.Style.FILL)
        paint.setColor(DrawingView.argb(c))
        canvas.drawPath(path, paint)
      }
      f.stroke.foreach { c =>
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeWidth(f.width.toFloat)
        paint.setColor(DrawingView.argb(c))
        canvas.drawPath(path, paint)
      }
    }
    compressed(bitmap, done)
  }

  private var previews = 0

  /** A picture's bytes drawn into a PNG `width` by `height`, kept in proportion and centred, clear round it: what
    * Android decodes (PNG, JPEG, GIF, WebP, HEIF); None for the rest, an SVG among them. */
  def fitted(bytes: Array[Byte], mime: String, width: Int, height: Int, done: Option[SnapshotImage] => Unit): Unit = {
    val source = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length)
    if (source == null || source.getWidth < 1 || source.getHeight < 1) done(None)
    else {
      val scale = math.min(width.toDouble / source.getWidth, height.toDouble / source.getHeight)
      val w = source.getWidth * scale
      val h = source.getHeight * scale
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val left = ((width - w) / 2.0).toFloat
      val top = ((height - h) / 2.0).toFloat
      val box = new android.graphics.RectF(left, top, left + w.toFloat, top + h.toFloat)
      new Canvas(bitmap).drawBitmap(source, null, box,
        new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG))
      source.recycle()
      compressed(bitmap, done)
    }
  }

  /** A file in the app's cache, which an ImageView loads as it does any file. */
  def preview(png: Array[Byte]): String = {
    val dir = new java.io.File(Host.activity.getCacheDir, "snapshots")
    dir.mkdirs()
    previews += 1
    val file = new java.io.File(dir, System.currentTimeMillis().toString + "-" + previews.toString + ".png")
    Host.writeFile(file, png)
    android.net.Uri.fromFile(file).toString
  }

  private def compressed(bitmap: Bitmap, done: Option[SnapshotImage] => Unit): Unit =
    Platform.offThread(() => {
      val out = new java.io.ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      val image = SnapshotImage(out.toByteArray, bitmap.getWidth, bitmap.getHeight)
      bitmap.recycle()
      Platform.later(() => done(Some(image)))
    })

  /** Each shown TextureView's frame drawn where it sits in `root`: a software canvas draws nothing for one. */
  private def textures(root: View, at: View, canvas: Canvas): Unit = at match {
    case t: TextureView if t.isShown && t.isAvailable && t.getWidth > 0 && t.getHeight > 0 =>
      val frame = t.getBitmap
      if (frame != null) {
        val where = new android.graphics.RectF(0f, 0f, t.getWidth.toFloat, t.getHeight.toFloat)
        var v: View = t
        val m = new android.graphics.Matrix()
        while (v != null && (v ne root)) {
          m.postConcat(v.getMatrix)
          m.postTranslate((v.getLeft - (if (v.getParent.isInstanceOf[View]) v.getParent.asInstanceOf[View].getScrollX else 0)).toFloat,
            (v.getTop - (if (v.getParent.isInstanceOf[View]) v.getParent.asInstanceOf[View].getScrollY else 0)).toFloat)
          v = v.getParent match {
            case p: View => p
            case _ => null
          }
        }
        m.mapRect(where)
        canvas.drawBitmap(frame, null, where, null)
        frame.recycle()
      }
    case g: ViewGroup if g.getVisibility == View.VISIBLE =>
      var i = 0
      while (i < g.getChildCount) {
        textures(root, g.getChildAt(i), canvas)
        i += 1
      }
    case _ => ()
  }

  /** The first solid colour a view round `view` is filled with, else the window's, else white. */
  private def behind(view: View): Int = {
    var at: View = view.getParent match {
      case p: View => p
      case _ => null
    }
    var found: Option[Int] = None
    while (at != null && found.isEmpty) {
      found = solid(at.getBackground)
      at = at.getParent match {
        case p: View => p
        case _ => null
      }
    }
    found.orElse(Option(Host.activity).flatMap(a => solid(a.getWindow.getDecorView.getBackground)))
      .getOrElse(android.graphics.Color.WHITE)
  }

  private def solid(d: android.graphics.drawable.Drawable): Option[Int] = d match {
    case c: ColorDrawable if android.graphics.Color.alpha(c.getColor) == 255 => Some(c.getColor)
    // its colours are Android 7's to read
    case g: GradientDrawable if android.os.Build.VERSION.SDK_INT >= 24 && g.getColors == null &&
      g.getColor != null && android.graphics.Color.alpha(g.getColor.getDefaultColor) == 255 =>
      Some(g.getColor.getDefaultColor)
    case _ => None
  }
}
