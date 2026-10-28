package everyui.list

import everyui.component.{Container, MaterialIconView, TextView}
import everyui.component.types.TextOverflow
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** What most rows show: an icon, a line of text and a badge at the end, dressed by a `Decoration`, `width` wide and
  * `height` tall. The text keeps its width, cut short with an ellipsis only where it is wider than the row; the badge
  * has the room left between it and the end (or its tail, drawn whole at the end), cut where the decoration says to fit
  * it, and is not drawn without any; a tail without room is not drawn either, nor the badge before it. */
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
    // a list row is a line tall and cannot grow: its words are cut short, and read whole in its tip (`cut`)
    textOverflow @= TextOverflow.Ellipsis
    font.size @= points
    font.weight := look.weight
    color := look.color.getOrElse(Theme.text())
    maxWidth := math.max(width - iconRoom() - RowLabel.pad, 0.0)
    position.left := ListRow.along(iconRoom(), size.width(), width)
    position.top := (height - size.height()) / 2.0
  }
  // between the text, a gap after it, and the end
  private val room: Val[Double] = own(Val(width - RowLabel.pad - iconRoom() - label.size.width() - RowLabel.pad))
  /** The badge's tail, never cut: at the end where it fits beside the text, else not drawn, nor the badge. */
  private[everyui] val tail: TextView = new TextView {
    content := look.badgeTail
    textOverflow @= TextOverflow.Ellipsis
    font.size @= points - 2.0
    color := look.badgeColor.getOrElse(Theme.muted())
    // held to the room as the badge is, so it is measured as drawn (on the web once its face has loaded), and not drawn
    // where it would have to be cut
    maxWidth := math.max(room(), 1.0)
    visible := look.badgeTail.nonEmpty && !elided() && size.width() <= room() + 0.5
    position.left := ListRow.along(width - size.width() - RowLabel.pad, size.width(), width)
    position.top := (height - size.height()) / 2.0
  }
  // what the tail leaves the badge, and where the badge ends
  private val tailSpan: Val[Double] = own(Val(if (look.badgeTail.isEmpty) 0.0 else tail.size.width() + RowLabel.tailGap))
  private val badgeRoom: Val[Double] = own(Val(room() - tailSpan()))
  private[everyui] val badge: TextView = new TextView {
    content := look.badge
    textOverflow @= TextOverflow.Ellipsis
    font.size @= points - 2.0
    color := look.badgeColor.getOrElse(Theme.muted())
    elide := look.badgeElide
    maxWidth := math.max(badgeRoom(), 1.0)
    // cut, it is drawn only with room for its ellipsis and a little of it
    visible := look.badge.nonEmpty && badgeRoom() > 0.0 && size.width() <= badgeRoom() + 0.5 &&
      (!elided() || badgeRoom() >= 2.0 * (points - 2.0)) && (look.badgeTail.isEmpty || tail.visible())
    position.left := ListRow.along(width - size.width() - RowLabel.pad - tailSpan(), size.width(), width)
    position.top := (height - size.height()) / 2.0
  }

  size.width := width
  size.height := height
  opacity := look.opacity
  size.width.attach(_ => node.setClip(size.width(), size.height()))
  size.height.attach(_ => node.setClip(size.width(), size.height()))
  node.setClip(size.width(), size.height())
  children ++= List(glyph, label, badge, tail)

  /** Where the text starts, from the start edge. */
  def textStart: Double = iconRoom()

  /** What the row shows, whole, where any of it is cut short: its words, its badge and its tail, for the row's tip (a
    * list row is a line tall, and its words cut short are read there). None where it is all drawn. */
  val cut: Val[Option[String]] = own(Val {
    val words = text
    val badgeWords = look.badge
    val tailWords = look.badgeTail
    val short = label.elided() || (badgeWords.nonEmpty && (badge.elided() || !badge.visible())) ||
      (tailWords.nonEmpty && !tail.visible())
    if (!short || words.isEmpty) None
    else Some((List(words) ++ List(badgeWords, tailWords).filter(_.nonEmpty)).mkString("  "))
  })
}

object RowLabel {
  /** Room left after the badge, and between the text and the badge. */
  val pad: Double = 8.0
  /** Between a badge and its tail. */
  val tailGap: Double = 4.0
}
