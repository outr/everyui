package everyui.list

import everyui.component.{Component, Container, MaterialIconView, TextView}
import everyui.component.types.TextOverflow
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** What most rows show: an icon, a line of text and a badge at the end, dressed by a `Decoration`, `width` wide and
  * `height` tall. The text keeps its width, cut short with an ellipsis only where it is wider than the row; the badge
  * has the room left between it and the end (or its tail, drawn whole at the end), cut where the decoration says to fit
  * it, and is not drawn without any; a tail without room is not drawn either, nor the badge before it.
  *
  * The icon, the badge and its tail are made only once a decoration has one, and kept from then on: most rows have
  * none, and a list of plain rows made three views for each row it drew that drew nothing. */
class RowLabel(text: => String, look: => Decoration, width: => Double, height: => Double, points: Double = 14.0)
  extends Container {
  private val glyphMade: Var[Option[MaterialIconView]] = own(Var(None))
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
  private val tailMade: Var[Option[TextView]] = own(Var(None))
  // what the tail leaves the badge, and where the badge ends
  private val tailSpan: Val[Double] = own(Val(if (look.badgeTail.isEmpty) 0.0
    else tailMade().map(_.size.width()).getOrElse(0.0) + RowLabel.tailGap))
  private val badgeRoom: Val[Double] = own(Val(room() - tailSpan()))
  private val badgeMade: Var[Option[TextView]] = own(Var(None))
  // whether the tail is drawn: made, and drawn
  private val tailShown: Val[Boolean] = own(Val(tailMade().exists(_.visible())))

  /** The badge's tail, never cut: at the end where it fits beside the text, else not drawn, nor the badge. (Made as
    * it is asked for, where the decoration has had none.) */
  private[everyui] def tail: TextView = tailMade.get.getOrElse(makeTail())
  /** The badge, cut to the room between the text and the tail. (Made as it is asked for, where the decoration has had
    * none.) */
  private[everyui] def badge: TextView = badgeMade.get.getOrElse(makeBadge())

  private def makeGlyph(): MaterialIconView = {
    val g = new MaterialIconView(MaterialIcon.Empty, points + 4.0) {
      icon := look.icon.getOrElse(MaterialIcon.Empty)
      color := look.iconColor.getOrElse(Theme.muted())
      visible := look.icon.nonEmpty
      position.left := ListRow.along(0.0, points + 4.0, width)
      position.top := (height - (points + 4.0)) / 2.0
    }
    glyphMade @= Some(g)
    arrange()
    g
  }

  private def makeTail(): TextView = {
    val t = new TextView {
      content := look.badgeTail
      textOverflow @= TextOverflow.Ellipsis
      font.size @= points - 2.0
      color := look.badgeColor.getOrElse(Theme.muted())
      // held to the room as the badge is, so it is measured as drawn (on the web once its face has loaded), and not
      // drawn where it would have to be cut
      maxWidth := math.max(room(), 1.0)
      visible := look.badgeTail.nonEmpty && !elided() && size.width() <= room() + 0.5
      position.left := ListRow.along(width - size.width() - RowLabel.pad, size.width(), width)
      position.top := (height - size.height()) / 2.0
    }
    tailMade @= Some(t)
    arrange()
    t
  }

  private def makeBadge(): TextView = {
    val b = new TextView {
      content := look.badge
      textOverflow @= TextOverflow.Ellipsis
      font.size @= points - 2.0
      color := look.badgeColor.getOrElse(Theme.muted())
      elide := look.badgeElide
      maxWidth := math.max(badgeRoom(), 1.0)
      // cut, it is drawn only with room for its ellipsis and a little of it
      visible := look.badge.nonEmpty && badgeRoom() > 0.0 && size.width() <= badgeRoom() + 0.5 &&
        (!elided() || badgeRoom() >= 2.0 * (points - 2.0)) && (look.badgeTail.isEmpty || tailShown())
      position.left := ListRow.along(width - size.width() - RowLabel.pad - tailSpan(), size.width(), width)
      position.top := (height - size.height()) / 2.0
    }
    badgeMade @= Some(b)
    arrange()
    b
  }

  // the parts made, in the order they read
  private def arrange(): Unit = children @= (glyphMade.get.toVector ++ Vector[Component](label) ++
    badgeMade.get.toVector ++ tailMade.get.toVector)

  // each part made as the decoration first has it (the tail before the badge, which is held short of it)
  private val wanted: Val[(Boolean, Boolean, Boolean)] = own(Val((look.icon.nonEmpty, look.badgeTail.nonEmpty,
    look.badge.nonEmpty)))
  private def makeWanted(): Unit = {
    val (icon, tailWanted, badgeWanted) = wanted()
    if (icon && glyphMade.get.isEmpty) makeGlyph()
    if (tailWanted && tailMade.get.isEmpty) makeTail()
    if (badgeWanted && badgeMade.get.isEmpty) makeBadge()
  }
  wanted.attach(_ => makeWanted())

  size.width := width
  size.height := height
  opacity := look.opacity
  size.width.attach(_ => clipTo(size.width(), size.height()))
  size.height.attach(_ => clipTo(size.width(), size.height()))
  clipTo(size.width(), size.height())
  arrange()
  makeWanted()

  /** Where the text starts, from the start edge. */
  def textStart: Double = iconRoom()

  /** The badge's width where it is drawn, else 0. */
  private[everyui] val badgeShown: Val[Double] = own(Val(badgeMade().filter(_.visible()).map(_.size.width())
    .getOrElse(0.0)))

  /** What the row shows, whole, where any of it is cut short: its words, its badge and its tail, for the row's tip (a
    * list row is a line tall, and its words cut short are read there). None where it is all drawn. */
  val cut: Val[Option[String]] = own(Val {
    val words = text
    val badgeWords = look.badge
    val tailWords = look.badgeTail
    val short = label.elided() ||
      (badgeWords.nonEmpty && badgeMade().forall(b => b.elided() || !b.visible())) ||
      (tailWords.nonEmpty && !tailShown())
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
