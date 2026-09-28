package everyui.list

import everyui.component.{Container, MaterialIconView, TextView}
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** What most rows show: an icon, a line of text and a badge at the end, dressed by a `Decoration`, `width` wide and
  * `height` tall, the text cut off at its edge. */
class RowLabel(text: => String, look: => Decoration, width: => Double, height: => Double, points: Double = 14.0)
  extends Container {
  private val glyph: MaterialIconView = new MaterialIconView(MaterialIcon.Empty, points + 4.0) {
    icon := look.icon.getOrElse(MaterialIcon.Empty)
    color := look.iconColor.getOrElse(Theme.muted())
    visible := look.icon.nonEmpty
    position.top := (height - (points + 4.0)) / 2.0
  }
  private val iconRoom: Val[Double] = own(Val(if (look.icon.nonEmpty) points + 10.0 else 0.0))
  val label: TextView = new TextView {
    content := text
    font.size @= points
    font.weight := look.weight
    color := look.color.getOrElse(Theme.text())
    position.left := iconRoom()
    position.top := (height - size.height()) / 2.0
  }
  private val badge = new TextView {
    content := look.badge
    font.size @= points - 2.0
    color := look.badgeColor.getOrElse(Theme.muted())
    visible := look.badge.nonEmpty
    position.left := width - size.width() - 8.0
    position.top := (height - size.height()) / 2.0
  }

  size.width := width
  size.height := height
  opacity := look.opacity
  size.width.attach(_ => node.setClip(size.width(), size.height()))
  size.height.attach(_ => node.setClip(size.width(), size.height()))
  node.setClip(size.width(), size.height())
  children ++= List(glyph, label, badge)

  /** Where the text starts, from the left. */
  def textStart: Double = iconRoom()
}
