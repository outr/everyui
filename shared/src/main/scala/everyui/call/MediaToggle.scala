package everyui.call

import everyui.Color
import everyui.component.types.Semantics
import everyui.component.{Container, MaterialIconView}
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A round button turning a microphone, a camera or the sound (`device`) on and off (`on`, both ways), as a call's
  * controls are: its icon, crossed out and on the danger colour while off. A click, a tap, or Enter on it turns it
  * over; its tooltip is its name. A screen reader hears it as a switch, named (`CallWords.microphoneSwitch`,
  * `cameraSwitch`, `soundSwitch`) and on or off.
  *
  * {{{
  *   val mic = new MediaToggle(preview.micOn, DeviceKind.Microphone)
  *   val cam = new MediaToggle(preview.cameraOn, DeviceKind.Camera)
  * }}} */
class MediaToggle(val on: Var[Boolean], val device: DeviceKind, diameter: Double = 48.0) extends Container {
  private val lit: Val[Boolean] = own(Val(focusShown() || event.pointer.over()))
  private val name: String = device match {
    case DeviceKind.Microphone => CallWords.microphoneSwitch
    case DeviceKind.Camera => CallWords.cameraSwitch
    case DeviceKind.Speaker => CallWords.soundSwitch
  }
  private val mark = new MaterialIconView(MediaToggle.glyph(device, on()), 24.0)
  mark.icon := MediaToggle.glyph(device, on())
  mark.color := (if (on()) Theme.text() else Color.White)
  mark.position.center := diameter / 2.0
  mark.position.middle := diameter / 2.0

  focusable @= true
  tapSlop @= true
  tooltip @= name
  size.width @= diameter
  size.height @= diameter
  radius @= diameter / 2.0
  backgroundColor := (if (!on()) Theme.danger() else if (lit()) Theme.controlLit() else Theme.raised())
  borderWidth := (if (focusShown()) 2.5 else 0.0)
  borderColor := Theme.accent()
  semantics := Semantics.switch(name, on())
  children += mark
  event.click.attach { e =>
    e.consume()
    on @= !on()
  }

  override def kind: String = "MediaToggle(" + name + ")"
}

object MediaToggle {
  /** The icon of a `kind` turned on or off. */
  def glyph(kind: DeviceKind, on: Boolean): MaterialIcon = kind match {
    case DeviceKind.Microphone => if (on) MaterialIcon.Mic else MaterialIcon.MicOff
    case DeviceKind.Camera => if (on) MaterialIcon.Videocam else MaterialIcon.VideocamOff
    case DeviceKind.Speaker => if (on) MaterialIcon.VolumeUp else MaterialIcon.VolumeOff
  }
}
