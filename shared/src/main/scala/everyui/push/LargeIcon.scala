package everyui.push

import everyui.Color
import everyui.font.MaterialIcon

/** What Android shows large beside a notice's words, its large icon: a picture (a push's "noticePicture": a person's
  * face, a cover), or the notice's Material icon drawn white on a circle of its colour, so its kind shows in the
  * notification shade as well as the status bar. Android 16, with its notification redesign, heads each
  * notification of an app with the app's own icon, its small icon only in the status bar, so the large icon is where
  * its kind shows: at its end, or at its head within an open stack. Android 12 to 15 head it with its small icon on a
  * circle of its colour, and a stack's children with their large icons where their small icon and colour are the
  * summary's. */
enum LargeIcon {
  /** The picture at an http or https URL. */
  case Picture(url: String)
  /** `icon` white on a circle of `color`. */
  case Glyph(icon: MaterialIcon, color: Color)
}

object LargeIcon {
  /** The colour a glyph's circle is where the notice names none: Material's grey 700. */
  val defaultColor: Color = new Color(0x61, 0x61, 0x61, 255)

  /** The large icons to try, the first that is to hand shown: the picture where there is one to fetch, then the icon
    * on its colour where it names one the font has; none, and Android shows none, where it has neither. */
  def choices(picture: Option[String], icon: Option[MaterialIcon], color: Option[Color]): List[LargeIcon] =
    picture.filter(PushMessage.fetchable).map(u => Picture(u.trim)).toList :::
      icon.filter(_.nonEmpty).map(i => Glyph(i, color.getOrElse(defaultColor))).toList

  def of(message: PushMessage): List[LargeIcon] =
    choices(message.noticePicture, message.shownIcon, message.noticeColor)

  def of(notice: Notice): List[LargeIcon] = choices(None, notice.noticeIcon, notice.noticeColor)

  def of(notice: OngoingNotice): List[LargeIcon] = choices(None, notice.noticeIcon, notice.noticeColor)

  /** What `icon` drawn on a circle of `color`, `pixels` across, is kept by: its icon, its colour and its size. */
  def key(icon: MaterialIcon, color: Color, pixels: Int): String =
    icon.name + "@" + color.toHexAlpha + "@" + pixels.toString
}
