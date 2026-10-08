package everyui.list

import everyui.Color
import everyui.component.types.{Elide, FontWeight}
import everyui.font.MaterialIcon

/** How the app dresses a row's label: an icon before it, the label's colour and weight, a badge after it (a count, a
  * VCS letter, a file's folder), and `opacity` for one shown faintly (an ignored file). Anything left out is the theme's.
  * The label keeps its room and the badge has what is left after it: one too wide for that is cut where `badgeElide`
  * says, from its start by default, so a path keeps its end ("…/backend/app +39 −7"), and one with no room is not
  * drawn. `badgeTail` is drawn after the badge, at the end, and never cut (a file's counts after its folder: the
  * badge "…/backend/app" and the tail "+1 −1"): the badge has the room left before it, and goes first where there is
  * too little; the tail goes too where even it does not fit beside the label. `tooltip` is words for more than the row
  * shows (a file's whole path): a tip as the pointer rests on the row, the cursor rests on it in a list with the focus,
  * or a finger is held on it where that opens no menu, and its description to a screen reader. */
case class Decoration(icon: Option[MaterialIcon] = None,
                      iconColor: Option[Color] = None,
                      color: Option[Color] = None,
                      weight: FontWeight = FontWeight.Normal,
                      badge: String = "",
                      badgeColor: Option[Color] = None,
                      opacity: Double = 1.0,
                      badgeElide: Elide = Elide.Start,
                      badgeTail: String = "",
                      tooltip: Option[String] = None)

object Decoration {
  lazy val plain: Decoration = Decoration()
}
