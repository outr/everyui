package everyui.render

import android.content.Context
import android.graphics.{Bitmap, Canvas, Color as AndroidColor, Paint, Rect, Typeface}
import android.graphics.drawable.Icon
import everyui.Color
import everyui.font.MaterialIcon
import everyui.push.LargeIcon
import java.util.concurrent.ConcurrentHashMap

/** The small icon of the app's notifications (push, `Platform.showNotice`, ongoing notices): the drawable
  * `everyui_notice` where the app's resources have one, the app's own icon otherwise. Android draws a small icon as a
  * silhouette, so a full-colour launcher icon shows as a blank shape: an app supplies `everyui_notice`, white on
  * transparent, at each density (CONSUMING.md).
  *
  * A notice that names a Material icon (a push's "noticeIcon", `Notice.noticeIcon`, `OngoingNotice.noticeIcon`) has
  * that icon instead, drawn from the bundled face white on transparent at the status bar's 24 dp for the screen's
  * density, its ink centred as the face pads it (20 dp of the 24), kept by name and density as a PNG. It is the
  * notice's large icon too where it has no picture (`large`): white on a circle of its colour, as wide as Android
  * draws a large icon (`notification_large_icon_width`), kept by name, colour and size. */
object NoticeIcon {
  @volatile private var found: Option[Int] = None
  @volatile private var face: Typeface = null
  private val drawn = new ConcurrentHashMap[String, Bitmap]
  private val encoded = new ConcurrentHashMap[String, Array[Byte]]

  def of(context: Context): Int = found.getOrElse {
    val own = context.getResources.getIdentifier("everyui_notice", "drawable", context.getPackageName)
    val icon = if (own != 0) own else context.getApplicationInfo.icon
    found = Some(icon)
    icon
  }

  /** The small icon of a notice: `icon` drawn where it has one the face draws, `of` otherwise. It is a PNG's bytes
    * (`Icon.createWithData`), which Android tells the same as another's of those bytes, as it does a resource and not
    * a bitmap: in an open stack on Android 12 to 16, a child whose small icon and colour are its summary's shows its
    * large icon where its small icon was, so each of a kind's stack (`PushMessage.lookingAs`) is headed by its kind. */
  def small(context: Context, icon: Option[MaterialIcon]): Icon = {
    val pixels = math.max(1, math.round(24f * context.getResources.getDisplayMetrics.density))
    icon.filter(_.nonEmpty)
      .flatMap(i => kept(encoded, i.name + "@" + pixels.toString, draw(context, i, pixels, pixels, None).map(png)))
      .map(b => Icon.createWithData(b, 0, b.length))
      .getOrElse(Icon.createWithResource(context, of(context)))
  }

  /** The large icon of a notice: the first of `choices` to hand, a picture fetched (`NoticePicture`) or an icon drawn
    * white on a circle of its colour; None, and no large icon, where none is. */
  def large(context: Context, choices: List[LargeIcon]): Option[Bitmap] = choices.iterator.flatMap {
    case LargeIcon.Picture(url) => NoticePicture.of(context, url)
    case g: LargeIcon.Glyph =>
      val pixels = context.getResources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
      // the glyph's em three fifths of the circle, its ink (20/24 of the em) half the circle across
      kept(drawn, LargeIcon.key(g.icon, g.color, pixels),
        draw(context, g.icon, pixels, math.round(pixels * 0.6f), Some(argb(g.color))))
  }.nextOption()

  /** `color` as Android writes one, alpha first. */
  def argb(color: Color): Int = (color.alphaInt << 24) | (color.redInt << 16) | (color.greenInt << 8) | color.blueInt

  private def png(bitmap: Bitmap): Array[Byte] = {
    val out = new java.io.ByteArrayOutputStream
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    out.toByteArray
  }

  private def kept[A](in: ConcurrentHashMap[String, A], key: String, make: => Option[A]): Option[A] =
    Option(in.get(key)).orElse {
      val made = try make catch {
        case t: Throwable =>
          Platform.logError("notice: icon " + key + " not drawn", t)
          None
      }
      made.foreach(b => in.put(key, b))
      made
    }

  /** `icon` white in a square of `pixels`, the face's em `em`, its ink centred, on a circle of `circle` filling the
    * square where there is one; None where the face draws nothing for it. */
  private def draw(context: Context, icon: MaterialIcon, pixels: Int, em: Int, circle: Option[Int]): Option[Bitmap] = {
    if (face == null) face = Typeface.createFromAsset(context.getAssets, "fonts/MaterialIcons-Regular.ttf")
    val glyph = icon.glyph
    val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
    paint.setTypeface(face)
    paint.setTextSize(em.toFloat)
    val ink = new Rect
    paint.getTextBounds(glyph, 0, glyph.length, ink)
    if (ink.isEmpty) None
    else {
      val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
      val canvas = new Canvas(bitmap)
      circle.foreach { c =>
        val fill = new Paint(Paint.ANTI_ALIAS_FLAG)
        fill.setColor(c)
        canvas.drawCircle(pixels / 2f, pixels / 2f, pixels / 2f, fill)
      }
      paint.setColor(AndroidColor.WHITE)
      canvas.drawText(glyph, (pixels - ink.width) / 2f - ink.left, (pixels - ink.height) / 2f - ink.top, paint)
      Some(bitmap)
    }
  }
}
