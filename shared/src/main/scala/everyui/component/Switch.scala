package everyui.component

import everyui.Color
import everyui.component.types.Semantics
import everyui.easing.Easing
import everyui.event.{Key, KeyEvent, PointerDrag}
import everyui.i18n.Direction
import everyui.kit.Mirror
import everyui.render.Platform
import everyui.task.*
import everyui.theme.Theme
import reactify.*

import scala.concurrent.duration.*

/** On or off, as Material's switch: a track 52 by 32, filled with the accent while `on`, its thumb sliding across and
  * growing as it goes on (and while it is pressed), with `label` before it. A click, a tap, OK, Enter or Space flips
  * it; the thumb can be dragged across too, and lands on the side it is let go nearer. Given a `width`, the label
  * starts at one end and the switch sits at the other, as a line in a list of settings; without one, it is as wide as
  * the two. The focus on it is a ring in the accent. It reads from the right in a right-to-left language, the thumb
  * on the left when on. A screen reader hears it as a switch, with its label and whether it is on. `disabled` puts it
  * out of use: faded, and the focus passes it by.
  *
  * {{{
  * val wifi = new Switch("Wi-Fi", Var(true))
  * wifi.on.attach(v => network.enable(v))
  * }}} */
class Switch(label: String = "", val on: Var[Boolean] = Var(false), width: Option[Double] = None) extends Container {
  // how far on the thumb is drawn: 0 off, 1 on, between while it slides or is dragged
  private val progress: Var[Double] = own(Var(if (on()) 1.0 else 0.0))
  private val pressed: Var[Boolean] = own(Var(false))
  private var sliding: Option[TaskInstance] = None
  private var pressX: Double = 0.0
  private var moved: Boolean = false
  private var releasedAt: Double = -1000.0

  private val title = new TextView {
    content @= label
    font.size @= 14.0
    color := Theme.text()
  }
  private val track = new Container
  private val knob = new Container

  focusable := !disabled()
  radius @= 8.0
  width match {
    case Some(w) => size.width @= w
    case None => size.width := Switch.pad * 2.0 + (if (label.isEmpty) 0.0 else title.size.width() + 16.0) + Switch.trackWidth
  }
  size.height @= Switch.height
  opacity := (if (disabled()) 0.38 else 1.0)
  backgroundColor := (if (!disabled() && (focused() || event.pointer.over())) Theme.controlLit() else Color.Clear)
  borderWidth := (if (focused()) 2.5 else 0.0)
  borderColor := Theme.accent()
  semantics := Semantics.switch(label, on())

  Mirror.place(title, () => size.width(), () => Switch.pad)
  title.position.top := (Switch.height - title.size.height()) / 2.0
  track.size.width @= Switch.trackWidth
  track.size.height @= Switch.trackHeight
  track.radius @= Switch.trackHeight / 2.0
  Mirror.place(track, () => size.width(), () => size.width() - Switch.pad - Switch.trackWidth)
  track.position.top @= (Switch.height - Switch.trackHeight) / 2.0
  track.backgroundColor := (if (on()) Theme.accent() else Theme.control())
  track.borderWidth := (if (on()) 0.0 else 2.0)
  track.borderColor := Theme.muted()
  knob.size.width := diameter
  knob.size.height := diameter
  knob.radius := diameter / 2.0
  knob.position.left := {
    val middle = Switch.trackHeight / 2.0 + progress() * (Switch.trackWidth - Switch.trackHeight)
    (if (Direction.rtl()) Switch.trackWidth - middle else middle) - diameter / 2.0
  }
  knob.position.top := (Switch.trackHeight - diameter) / 2.0
  knob.backgroundColor := (if (on()) Theme.onAccent() else Theme.muted())
  track.children += knob
  if (label.nonEmpty) children += title
  children += track

  on.attach(v => slide(if (v) 1.0 else 0.0))
  // once a press: a held key's repeats flip nothing
  event.key.down.attach(e => if (keepsEvent(e) && !Platform.keyRepeat()) on @= !on())
  tapSlop @= true
  // what a screen reader's press, or a platform's click after a tap the drag already took, arrives as
  event.click.attach { e =>
    e.consume()
    if (!disabled() && Platform.ticks() - releasedAt > 400.0) on @= !on()
  }

  private val drag = new PointerDrag(this, (x, _) => follow(x), () => release(), (x, _) => {
    val take = !disabled()
    if (take) {
      pressed @= true
      pressX = x
      moved = false
    }
    take
  })

  override def keepsEvent(e: KeyEvent): Boolean = !disabled() && (e.key == Key.Enter || e.key == Key.Space)

  private def diameter: Double =
    if (pressed()) Switch.pressedThumb else Switch.offThumb + progress() * (Switch.onThumb - Switch.offThumb)

  // a drag past a few pixels takes the thumb along with it
  private def follow(x: Double): Unit = {
    if (math.abs(x - pressX) > 4.0) moved = true
    if (moved) {
      sliding.foreach(_.cancel())
      val travel = Switch.trackWidth - Switch.trackHeight
      val f = (x - track.position.left() - Switch.trackHeight / 2.0) / travel
      progress @= math.max(0.0, math.min(1.0, if (Direction.rtl()) 1.0 - f else f))
    }
  }

  private def release(): Unit = {
    pressed @= false
    releasedAt = Platform.ticks()
    if (moved) {
      val landed = progress() >= 0.5
      if (landed != on()) on @= landed else slide(if (landed) 1.0 else 0.0)
    }
    else on @= !on()
  }

  private def slide(to: Double): Unit = {
    sliding.foreach(_.cancel())
    sliding = Some(progress.to(to).in(Switch.slideTime).easing(Easing.cubicInOut).start())
  }

  override def kind: String = "Switch(" + label + ")"
}

object Switch {
  val trackWidth: Double = 52.0
  val trackHeight: Double = 32.0
  /** The thumb's size off, on, and while pressed. */
  val offThumb: Double = 16.0
  val onThumb: Double = 24.0
  val pressedThumb: Double = 28.0
  /** How tall a switch's line is, and the room at its ends. */
  val height: Double = 40.0
  val pad: Double = 8.0
  def slideTime: FiniteDuration = 150.millis
}
