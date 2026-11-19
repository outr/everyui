package everyui.image

import everyui.component.ImageView
import everyui.render.Platform
import reactify.*

/** What pictures out of sight may keep decoded. A picture scrolled out of its rail, on a tab not shown or on a screen
  * left behind (`ImageView.showing`) is kept, its source still set, while the pictures so kept take no more than
  * `megabytes` between them (each counted as 4 bytes a pixel at its own size, as it is decoded); past that, the one
  * longest out of sight is let go (the page's `img` its `src`, the others' view its bitmap), and taken up again from
  * its source as it comes back. Kept, a picture comes back at once, as it was; let go, the platform may free it.
  *
  * The budget is the device's (`byDevice`): a browser's on a TV 16 MB, any other browser's 32, and none elsewhere,
  * where the platform keeps a cache of decoded pictures of its own (Android's, JavaFX's) or has the memory for none
  * (a Roku), so a picture out of sight is let go at once. Below `Quality`'s Full it is halved, and quartered at Minimal
  * (`Quality.cache`). An app sets its own: `ImageBudget.megabytes @= 8.0`, or 0 to let each go at once.
  *
  * What pictures hold is counted on every platform (`heldBytes`, `keptBytes`), for the memory line of
  * `everyui.perf.FrameLog`. */
object ImageBudget {
  /** The budget, in megabytes, for pictures out of sight. */
  lazy val megabytes: Var[Double] = Var(byDevice())

  /** The device's own budget, in megabytes: 16 in a TV's browser, 32 in any other, and 0 off the web. */
  def byDevice(): Double =
    if (!everyui.PlatformKinds.browser(Platform.kind)) 0.0 else if (Platform.remoteNavigation()) 16.0 else 32.0

  // those kept out of sight, the one longest out of it first, and the bytes they hold
  private var kept: List[ImageView] = Nil
  private var keptTotal: Double = 0.0
  // every picture drawn or kept, and the bytes they hold
  private var heldTotal: Double = 0.0
  private var heldCount: Int = 0

  /** The bytes, decoded, of every picture drawn or kept out of sight, and how many they are. */
  def heldBytes: Double = heldTotal
  def held: Int = heldCount
  /** The bytes of those kept out of sight, and how many. */
  def keptBytes: Double = keptTotal
  def keptCount: Int = kept.size

  /** The bytes `image` takes decoded: 4 a pixel at its own size. */
  def bytesOf(image: Image): Double = math.max(0.0, image.width) * math.max(0.0, image.height) * 4.0

  /** How long, in ms, a picture is in sight before it is kept as it goes out of it. */
  var seenMs: Double = 100.0

  /** What pictures out of sight may hold now, in bytes. */
  private def budget(): Double = math.max(0.0, megabytes()) * 1048576.0 * everyui.perf.Quality.cache

  /** `view`'s picture counted as drawn, by `bytes` where it was counted at `was`. */
  private[everyui] def counted(was: Double, bytes: Double): Unit = {
    heldTotal += bytes - was
    heldCount += (if (bytes > 0.0) 1 else 0) - (if (was > 0.0) 1 else 0)
  }

  /** `view` gone out of sight with `bytes` drawn, after `shownMs` in sight: kept while there is room, the longest kept
    * let go to make it; whether it is kept. One in sight for less than `seenMs` (built out of sight, and culled the turn
    * it was made; passed in a fling) is not kept. */
  private[everyui] def aside(view: ImageView, bytes: Double, shownMs: Double): Boolean = {
    val room = budget()
    if (bytes <= 0.0 || bytes > room || shownMs < seenMs) false
    else {
      kept = kept :+ view
      keptTotal += bytes
      while (keptTotal > room && kept.nonEmpty) {
        val oldest = kept.head
        kept = kept.tail
        keptTotal -= oldest.drawnBytes
        oldest.letGo()
      }
      true
    }
  }

  /** `view` back in sight, or its picture changed or let go: no longer kept. */
  private[everyui] def back(view: ImageView, bytes: Double): Unit = if (kept.exists(_ eq view)) {
    kept = kept.filterNot(_ eq view)
    keptTotal -= bytes
  }

  // how many pictures larger than drawn have been said (`sized`), and the most that are
  private var oversized: Int = 0
  private val oversizedMost: Int = 20

  /** `image` drawn by `view`: while frames are logged (`FrameLog`), one with more than twice the pixels it is drawn at
    * (its box in the app's units times `Platform.pixelRatio`) is said, the first 20 of them: a picture is decoded at
    * its own size, so a 4K backdrop on a 720p page holds nine times the memory it shows. Ask for pictures at the size
    * they are drawn. */
  private[everyui] def sized(view: ImageView, image: Image): Unit =
    if (everyui.perf.FrameLog.on && oversized < oversizedMost) {
      val ratio = Platform.pixelRatio()
      val across = view.size.width() * ratio
      val down = view.size.height() * ratio
      if (across >= 1.0 && down >= 1.0 && image.width * image.height > across * down * 2.0) {
        oversized += 1
        everyui.scribe.info("images: " + math.round(image.width).toString + "x" + math.round(image.height).toString +
          " drawn at " + math.round(across).toString + "x" + math.round(down).toString + ", " +
          math.round(image.width * image.height / (across * down)).toString + "x the pixels: " +
          (if (image.source.length > 160) image.source.substring(0, 160) else image.source))
      }
    }

  /** Every picture kept out of sight let go now: on a warning that memory is short, say. */
  def trim(): Unit = {
    val all = kept
    kept = Nil
    keptTotal = 0.0
    all.foreach(_.letGo())
  }

  /** As at launch: a spec's. */
  private[everyui] def forget(): Unit = {
    kept = Nil
    keptTotal = 0.0
    heldTotal = 0.0
    heldCount = 0
    oversized = 0
  }
}
