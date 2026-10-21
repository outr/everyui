package everyui.picker

import everyui.Color
import everyui.component.{Component, Container, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.kit.Mirror
import everyui.theme.Theme
import reactify.*

/** A segment's chip, over a picker's list: lit in the accent while it is the one `chosen`, an outline otherwise, and
  * lit a little while the focus is on it. A click, a tap or Enter chooses it. A segment's icon goes before its words,
  * in their colour, and the chip is wider by the icon and the gap after it. */
class SegmentChip(val segment: Segment, chosen: Var[String]) extends Container {
  private def lit: Boolean = chosen() == segment.key
  /** The colour of its words, and of its icon. */
  val ink: Val[Color] = own(Val(if (lit) Theme.onAccent() else if (focusShown()) Theme.text() else Theme.muted()))
  /** The segment's icon, where it has one this platform draws. */
  val icon: Option[Component] = segment.icon.flatMap(_.view(SegmentChip.iconPoints))
  // how much further in the words start for the icon before them
  private val lead = icon.map(_ => SegmentChip.iconPoints + SegmentChip.gap).getOrElse(0.0)
  private val words = new TextView {
    content @= segment.label
    font.size @= SegmentChip.points
    font.weight @= FontWeight.SemiBold
    color := ink()
  }

  focusable @= true
  semantics @= Semantics.button()
  size.width := words.size.width() + SegmentChip.padH * 2.0 + lead
  size.height @= SegmentChip.height
  words.position.top := (SegmentChip.height - words.size.height()) / 2.0
  icon match {
    case Some(i) =>
      // the icon at the start, the words after it: from the right in a right-to-left language
      i.color := ink()
      i.position.top @= (SegmentChip.height - SegmentChip.iconPoints) / 2.0
      Mirror.place(i, () => size.width(), () => SegmentChip.padH)
      Mirror.place(words, () => size.width(), () => SegmentChip.padH + lead)
      children ++= List(i, words)
    case None =>
      words.position.left @= SegmentChip.padH
      children += words
  }
  radius @= SegmentChip.height / 2.0
  borderWidth @= 1.0
  borderColor := (if (lit) Theme.accent() else if (focusShown()) Theme.accent() else Theme.border())
  backgroundColor := (if (lit) (if (focusShown()) Theme.accentLit() else Theme.accent())
    else if (focusShown()) Theme.controlLit() else Color.Clear)
  tapSlop @= true
  event.click.attach { e =>
    e.consume()
    chosen @= segment.key
  }

  override def kind: String = "SegmentChip(" + segment.label + ")"
}

object SegmentChip {
  val height: Double = 26.0
  val padH: Double = 12.0
  val points: Double = 12.5
  /** An icon's square: a little larger than the words, as icons beside words are, so that its ink (a 24 box leaves a
    * margin round a mark) comes to the height of their capitals or a little more (9 to 12 pixels against capitals of
    * 9); and a whole number of pixels, centred in the chip with 6 above and below, so it sits on the pixel grid at 1x. */
  val iconPoints: Double = 14.0
  /** The space between an icon and the words after it: about half the words' size, as a space and a bit. */
  val gap: Double = 6.0
}
