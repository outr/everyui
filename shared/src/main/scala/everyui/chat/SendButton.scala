package everyui.chat

import everyui.Color
import everyui.component.{Container, MaterialIconView}
import everyui.component.types.Semantics
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A [[Composer]]'s round button at the end of its line: Send, filled in the accent while there is something to send;
  * Stop while a reply is coming (`stopping`). Out of use (`usable` false) it is muted, and a press does nothing. */
class SendButton(sendLabel: String, stopLabel: String, points: Double = 36.0) extends Container {
  /** Whether a press does anything. */
  val usable: Var[Boolean] = own(Var(false))
  /** Whether it stops rather than sends. */
  val stopping: Var[Boolean] = own(Var(false))
  /** A press, while it is usable. */
  val pressed: Channel[Unit] = Channel[Unit]

  private val lit: Val[Boolean] = own(Val(usable() && (focused() || event.pointer.over())))
  private val glyph = new MaterialIconView(MaterialIcon.Send, points * 0.55)

  focusable @= true
  size.width @= points
  size.height @= points
  radius @= points / 2.0
  glyph.icon := (if (stopping()) MaterialIcon.Stop else MaterialIcon.Send)
  // a glyph is its size square, so it is placed by its size rather than by what it measures as it changes
  glyph.position.left @= points * 0.225
  glyph.position.top @= points * 0.225
  glyph.color := (if (!usable()) Theme.muted() else if (focused()) Theme.background() else Theme.onAccent())
  backgroundColor := (if (!usable()) Theme.control() else if (focused()) Theme.text() else if (lit()) Theme.accentLit()
    else Theme.accent())
  tooltip := (if (stopping()) stopLabel else sendLabel)
  semantics := Semantics.button(if (stopping()) stopLabel else sendLabel)
  disabled := !usable()
  children += glyph
  event.click.attach { e =>
    e.consume()
    if (usable()) pressed @= (())
  }

  override def kind: String = "SendButton"
}
