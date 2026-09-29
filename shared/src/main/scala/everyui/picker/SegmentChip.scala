package everyui.picker

import everyui.Color
import everyui.component.{Container, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.theme.Theme
import reactify.*

/** A segment's chip, over a picker's list: lit in the accent while it is the one `chosen`, an outline otherwise, and
  * lit a little while the focus is on it. A click, a tap or Enter chooses it. */
class SegmentChip(val segment: Segment, chosen: Var[String]) extends Container {
  private def lit: Boolean = chosen() == segment.key
  private val words = new TextView {
    content @= segment.label
    font.size @= SegmentChip.points
    font.weight @= FontWeight.SemiBold
    color := (if (lit) Theme.onAccent() else if (SegmentChip.this.focused()) Theme.text() else Theme.muted())
    position.left @= SegmentChip.padH
  }

  focusable @= true
  semantics @= Semantics.button()
  size.width := words.size.width() + SegmentChip.padH * 2.0
  size.height @= SegmentChip.height
  words.position.top := (SegmentChip.height - words.size.height()) / 2.0
  radius @= SegmentChip.height / 2.0
  borderWidth @= 1.0
  borderColor := (if (lit) Theme.accent() else if (focused()) Theme.accent() else Theme.border())
  backgroundColor := (if (lit) (if (focused()) Theme.accentLit() else Theme.accent())
    else if (focused()) Theme.controlLit() else Color.Clear)
  children += words
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
}
