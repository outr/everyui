package everyui.render

import android.content.Context
import android.graphics.{Bitmap, Canvas, Color, Paint, Rect, Typeface}
import android.graphics.drawable.Icon
import everyui.font.MaterialIcon
import java.util.concurrent.ConcurrentHashMap

/** The small icon of the app's notifications (push, `Platform.showNotice`, ongoing notices): the drawable
  * `everyui_notice` where the app's resources have one, the app's own icon otherwise. Android draws a small icon as a
  * silhouette, so a full-colour launcher icon shows as a blank shape: an app supplies `everyui_notice`, white on
  * transparent, at each density (CONSUMING.md).
  *
  * A notice that names a Material icon (a push's "noticeIcon", `Notice.noticeIcon`, `OngoingNotice.noticeIcon`) has
  * that icon instead, drawn from the bundled face white on transparent at the status bar's 24 dp for the screen's
  * density, its ink centred as the face pads it (20 dp of the 24), and kept by name and density. */
object NoticeIcon {
  @volatile private var found: Option[Int] = None
  @volatile private var face: Typeface = null
  private val drawn = new ConcurrentHashMap[String, Bitmap]

  def of(context: Context): Int = found.getOrElse {
    val own = context.getResources.getIdentifier("everyui_notice", "drawable", context.getPackageName)
    val icon = if (own != 0) own else context.getApplicationInfo.icon
    found = Some(icon)
    icon
  }

  /** The small icon of a notice: `icon` drawn where it has one the face draws, `of` otherwise. */
  def small(context: Context, icon: Option[MaterialIcon]): Icon =
    icon.filter(_.nonEmpty).flatMap(i => bitmap(context, i)).map(b => Icon.createWithBitmap(b))
      .getOrElse(Icon.createWithResource(context, of(context)))

  private def bitmap(context: Context, icon: MaterialIcon): Option[Bitmap] = {
    val density = context.getResources.getDisplayMetrics
    val key = icon.name + "@" + density.densityDpi.toString
    Option(drawn.get(key)).orElse {
      val made = try draw(context, icon.glyph, math.max(1, math.round(24f * density.density)))
        catch {
          case t: Throwable =>
            Platform.logError("notice: icon " + icon.name + " not drawn", t)
            None
        }
      made.foreach(b => drawn.put(key, b))
      made
    }
  }

  /** `glyph` white on transparent in a square of `pixels`, the face's em that square, its ink centred; None where the
    * face draws nothing for it. */
  private def draw(context: Context, glyph: String, pixels: Int): Option[Bitmap] = {
    if (face == null) face = Typeface.createFromAsset(context.getAssets, "fonts/MaterialIcons-Regular.ttf")
    val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
    paint.setTypeface(face)
    paint.setColor(Color.WHITE)
    paint.setTextSize(pixels.toFloat)
    val ink = new Rect
    paint.getTextBounds(glyph, 0, glyph.length, ink)
    if (ink.isEmpty) None
    else {
      val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
      new Canvas(bitmap).drawText(glyph, (pixels - ink.width) / 2f - ink.left, (pixels - ink.height) / 2f - ink.top,
        paint)
      Some(bitmap)
    }
  }
}
