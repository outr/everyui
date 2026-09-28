package everyui.list

import everyui.Color
import everyui.component.types.FontWeight
import everyui.font.MaterialIcon

/** How the app dresses a row's label: an icon before it, the label's colour and weight, a badge after it (a count, a
  * VCS letter), and `opacity` for one shown faintly (an ignored file). Anything left out is the theme's. */
case class Decoration(icon: Option[MaterialIcon] = None,
                      iconColor: Option[Color] = None,
                      color: Option[Color] = None,
                      weight: FontWeight = FontWeight.Normal,
                      badge: String = "",
                      badgeColor: Option[Color] = None,
                      opacity: Double = 1.0)

object Decoration {
  lazy val plain: Decoration = Decoration()
}
