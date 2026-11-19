package everyui.render

import android.content.Context
import android.graphics.{Bitmap, BitmapFactory}
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.net.{HttpURLConnection, URL}
import java.util.concurrent.{Callable, Executors, ThreadFactory, TimeUnit}

/** The picture a push shows large beside its words ("noticePicture", `PushMessage.fetchedPicture`): fetched over http
  * or https within `budget` of the push's time (`PushService` has ten seconds, `BackgroundAccess.receiverDeadline`, and
  * the notice is still to be posted), no more than `most` bytes, decoded at the sampling nearest the large icon's size
  * and cut to it from the middle. Late, too large, not a picture or not there, the notice is shown without it. The few
  * fetched last are kept, so a notice posted again (a reply's) has its picture at once. */
private[render] object NoticePicture {
  /** How long a fetch has, in milliseconds. */
  val budget: Long = 5000L
  /** The most bytes a picture may be. */
  val most: Int = 2 * 1024 * 1024

  private val pool = Executors.newCachedThreadPool(new ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r, "everyui-notice-picture")
      t.setDaemon(true)
      t
    }
  })
  private val kept = new java.util.LinkedHashMap[String, Bitmap](8, 0.75f, true) {
    override def removeEldestEntry(eldest: java.util.Map.Entry[String, Bitmap]): Boolean = this.size > 8
  }

  private def onMain: Boolean = Looper.myLooper == Looper.getMainLooper

  /** Whether `url`'s picture is to hand now: fetched already, or fetched here, off the main thread. */
  def ready(context: Context, url: String): Boolean = !onMain || cached(key(context, url)).nonEmpty

  /** `task` run off the main thread, where a picture may be fetched, any failure logged. */
  def later(what: String, task: () => Unit): Unit = pool.execute(() =>
    try task() catch { case t: Throwable => Platform.logError(what, t) })

  /** The picture at `url` at the large icon's size, within `budget`; on the main thread only one fetched already. */
  def of(context: Context, url: String): Option[Bitmap] = {
    val at = key(context, url)
    cached(at).orElse {
      if (onMain) None
      else {
        val (width, height) = size(context)
        val task = pool.submit(new Callable[Option[Bitmap]] {
          override def call(): Option[Bitmap] = fetch(url, width, height)
        })
        val got = try task.get(budget, TimeUnit.MILLISECONDS) catch {
          case t: Throwable =>
            task.cancel(true)
            val why = Option(t.getCause).getOrElse(t)
            Platform.log("notice: picture not shown (" + why.getClass.getSimpleName + ": " + why.getMessage + "): " + url)
            None
        }
        got.foreach(b => kept.synchronized(kept.put(at, b)))
        got
      }
    }
  }

  private def cached(at: String): Option[Bitmap] = Option(kept.synchronized(kept.get(at)))

  private def key(context: Context, url: String): String = {
    val (width, height) = size(context)
    url + "@" + width.toString + "x" + height.toString
  }

  private def size(context: Context): (Int, Int) = {
    val r = context.getResources
    (r.getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
      r.getDimensionPixelSize(android.R.dimen.notification_large_icon_height))
  }

  private def fetch(url: String, width: Int, height: Int): Option[Bitmap] = {
    val connection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(budget.toInt)
    connection.setReadTimeout(budget.toInt)
    try {
      if (connection.getResponseCode != 200 || connection.getContentLength > most) None
      else {
        val in = connection.getInputStream
        val bytes = try read(in) finally in.close()
        bytes.flatMap(b => decode(b, width, height))
      }
    }
    finally connection.disconnect()
  }

  // its bytes, None past `most`
  private def read(in: java.io.InputStream): Option[Array[Byte]] = {
    val out = new ByteArrayOutputStream
    val chunk = new Array[Byte](16384)
    var n = in.read(chunk)
    while (n >= 0 && out.size <= most) {
      out.write(chunk, 0, n)
      n = in.read(chunk)
    }
    if (out.size > most) None else Some(out.toByteArray)
  }

  /** `bytes` decoded at the largest sampling still as large as `width` by `height`, scaled to cover it and cut to it
    * from the middle. */
  private def decode(bytes: Array[Byte], width: Int, height: Int): Option[Bitmap] = {
    val bounds = new BitmapFactory.Options
    bounds.inJustDecodeBounds = true
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) None
    else {
      var sample = 1
      while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
      val options = new BitmapFactory.Options
      options.inSampleSize = sample
      Option(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options)).map { b =>
        val scale = math.max(width.toDouble / b.getWidth, height.toDouble / b.getHeight)
        val scaled = Bitmap.createScaledBitmap(b, math.max(width, math.round(b.getWidth * scale).toInt),
          math.max(height, math.round(b.getHeight * scale).toInt), true)
        Bitmap.createBitmap(scaled, (scaled.getWidth - width) / 2, (scaled.getHeight - height) / 2, width, height)
      }
    }
  }
}
