package everyui.list

import everyui.component.{Container, MaterialIconView, TextView}
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** What most rows show: an icon, a line of text and a badge at the end, dressed by a `Decoration`, `width` wide and
  * `height` tall. The text keeps its width, cut short with an ellipsis only where it is wider than the row; the badge
  * has the room left between it and the end, cut where the decoration says to fit it, and is not drawn without any. */
class RowLabel(text: => String, look: => Decoration, width: => Double, height: => Double, points: Double = 14.0)
  extends Container {
  private val glyph: MaterialIconView = new MaterialIconView(MaterialIcon.Empty, points + 4.0) {
    icon := look.icon.getOrElse(MaterialIcon.Empty)
    color := look.iconColor.getOrElse(Theme.muted())
    visible := look.icon.nonEmpty
    position.left := ListRow.along(0.0, points + 4.0, width)
    position.top := (height - (points + 4.0)) / 2.0
  }
  private val iconRoom: Val[Double] = own(Val(if (look.icon.nonEmpty) points + 10.0 else 0.0))
  val label: TextView = new TextView {
    content := text
    font.size @= points
    font.weight := look.weight
    color := look.color.getOrElse(Theme.text())
    maxWidth := math.max(width - iconRoom() - RowLabel.pad, 0.0)
    position.left := ListRow.along(iconRoom(), size.width(), width)
    position.top := (height - size.height()) / 2.0
  }
  // between the text, a gap after it, and the end
  private val badgeRoom: Val[Double] = own(Val(width - RowLabel.pad - iconRoom() - label.size.width() - RowLabel.pad))
  private[everyui] val badge: TextView = new TextView {
    content := look.badge
    font.size @= points - 2.0
    color := look.badgeColor.getOrElse(Theme.muted())
    elide := look.badgeElide
    maxWidth := math.max(badgeRoom(), 1.0)
    // cut, it is drawn only with room for its ellipsis and a little of it
    visible := look.badge.nonEmpty && badgeRoom() > 0.0 && size.width() <= badgeRoom() + 0.5 &&
      (!elided() || badgeRoom() >= 2.0 * (points - 2.0))
    position.left := ListRow.along(width - size.width() - RowLabel.pad, size.width(), width)
    position.top := (height - size.height()) / 2.0
  }

  size.width := width
  size.height := height
  opacity := look.opacity
  size.width.attach(_ => node.setClip(size.width(), size.height()))
  size.height.attach(_ => node.setClip(size.width(), size.height()))
  node.setClip(size.width(), size.height())
  children ++= List(glyph, label, badge)

  /** Where the text starts, from the start edge. */
  def textStart: Double = iconRoom()
}

object RowLabel {
  /** Room left after the badge, and between the text and the badge. */
  val pad: Double = 8.0
}
