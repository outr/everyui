package everyui.color

import everyui.component.Container
import everyui.component.types.Semantics
import everyui.event.{Key, KeyEvent}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, Gradient}
import reactify.*

/** The square of a picker where saturation runs left to right and value (brightness) bottom to top, for `hsv`'s hue,
  * `width` by `height`. A press or a drag sets both; the arrows move the thumb a hundredth of the way (a tenth with
  * Shift). With a keyboard at hand the arrows are always the area's (Tab leaves it); on a remote, where the arrows are
  * also how the focus moves, OK starts moving the thumb and OK or Back stops, so the focus can pass over it.
  *
  * It is drawn with gradients, so a platform that draws none (a Roku, an Apple TV) has no use for it: ColorPicker
  * shows saturation and value sliders there instead. */
class ColorArea(hsv: Var[Hsv], width: Double, height: Double) extends Container {
  /** Whether the arrows move the thumb, on a remote: OK turns it on and off. */
  val adjusting: Var[Boolean] = own(Var(false))
  private val keyboard: Boolean = Platform.keyboardSupport() && !Platform.remoteNavigation()
  private val white = new Container
  private val black = new Container
  private val knob = new ColorThumb(hsv().color(false))

  focusable @= true
  size.width @= width
  size.height @= height
  radius @= 8.0
  backgroundColor := hsv().pure
  List(white, black).foreach { layer =>
    layer.size.width @= width
    layer.size.height @= height
    layer.radius @= 8.0
  }
  white.gradient @= Some(Gradient(90.0, Color.White, Color.White.withAlpha(0.0)))
  black.gradient @= Some(Gradient(0.0, Color.Black, Color.Black.withAlpha(0.0)))
  borderWidth := (if (focused()) (if (adjusting()) 3.0 else 2.0) else 0.0)
  borderColor := Theme.accent()
  semantics := Semantics("slider", label = ColorPicker.saturationLabel + " " + ColorText.percent(hsv().saturation) + ", " +
    ColorPicker.valueLabel + " " + ColorText.percent(hsv().value))
  knob.position.left := hsv().saturation * width - ColorThumb.diameter / 2.0
  knob.position.top := (1.0 - hsv().value) * height - ColorThumb.diameter / 2.0
  children ++= List(white, black, knob)
  focused.attach(f => if (!f) adjusting @= false)

  private val drag = new ColorDrag(this, (x, y) => hsv @= hsv().copy(saturation = Hsv.unit(x / width), value = Hsv.unit(1.0 - y / height)))

  event.key.down.attach(e => key(e))

  override def keepsEvent(e: KeyEvent): Boolean =
    if (keyboard || adjusting()) e.key == Key.ArrowLeft || e.key == Key.ArrowRight || e.key == Key.ArrowUp ||
      e.key == Key.ArrowDown || (adjusting() && (e.key == Key.Enter || e.key == Key.Back))
    else e.key == Key.Enter

  private def key(e: KeyEvent): Unit = {
    val by = if (e.shift) 0.1 else 0.01
    val now = hsv()
    if (e.key == Key.ArrowLeft) hsv @= now.copy(saturation = Hsv.unit(now.saturation - by))
    else if (e.key == Key.ArrowRight) hsv @= now.copy(saturation = Hsv.unit(now.saturation + by))
    else if (e.key == Key.ArrowUp) hsv @= now.copy(value = Hsv.unit(now.value + by))
    else if (e.key == Key.ArrowDown) hsv @= now.copy(value = Hsv.unit(now.value - by))
    else if (e.key == Key.Enter && !keyboard) adjusting @= !adjusting()
    else if (e.key == Key.Back) adjusting @= false
  }

  override def kind: String = "ColorArea"
}
