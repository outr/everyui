package everyui.color

import everyui.Color
import everyui.component.{Component, Container, TextInput, TextView}
import everyui.i18n.Words
import everyui.layout.{FlowLayout, HorizontalLayout, VerticalLayout}
import everyui.theme.Theme
import reactify.*

/** A colour chooser, `width` wide, in shared EveryUI: it runs on every backend.
  *
  * From the top: the saturation and value area with a hue slider under it, an alpha slider where `alpha` allows
  * see-through colours, the colour itself beside its hex, its red, green and blue, then `presets` to choose with one
  * press and the colours chosen lately (`recent`, shared by every picker unless given one of its own; `remember` adds
  * the colour, which ColorDialog does as it is chosen). The fields, the area and the sliders stay in step both ways
  * with `value`, which the app gives and reads; a field only changes it once it holds a colour, and shows the colour
  * again as it is left.
  *
  * The mouse and a finger press or drag the area and the sliders. From the keyboard, Tab goes through them; the
  * arrows move the area's thumb both ways and a slider's along it (Shift ten times as far; Home and End to a slider's
  * ends). Each says what it is and where it stands to a screen reader.
  *
  * On a Roku or an Apple TV, which draw no gradients, there is no area (`area`): saturation and brightness are sliders
  * like the hue, drawn in bands of solid colour, and everything is reached by the d-pad: Up and Down from one control
  * to the next, Left and Right along a slider or a row of swatches, OK on a swatch, and a field takes its value from the
  * platform's keyboard. On Android TV (a remote, with gradients) the area stays: OK starts moving its thumb, OK or
  * Back stops.
  *
  * {{{
  *   val colour = Var(Color.fromHex("1e88e5"))
  *   val picker = new ColorPicker(colour, alpha = true, presets = ColorPicker.material)
  *   colour.attach(c => canvas.brush @= c)
  * }}} */
class ColorPicker(val value: Var[Color] = Var(Color.White), val alpha: Boolean = false, presets: List[Color] = Nil,
                  val recent: Var[List[Color]] = ColorPicker.recent, width: Double = ColorPicker.width,
                  area: Boolean = ColorTrack.gradients) extends Container {
  /** The colour as hue, saturation, value and alpha: what the area and sliders move, kept through greys and black. */
  val hsv: Var[Hsv] = own(Var(Hsv.of(value())))

  private val gap: Double = 8.0
  private val hue = new ColorSlider(ColorPicker.hueLabel, width, hsv().hue, ColorPicker.hues, hsv().pure,
    ColorText.degrees(hsv().hue), 1.0 / 360.0)(h => hsv @= hsv().copy(hue = h))
  private val preview = new Container
  private val hex = new TextInput(ColorPicker.hexLabel, width = width - ColorPicker.fieldHeight - gap)
  private val third: Double = (width - gap * 2.0) / 3.0
  private val red = channelField("R", third, c => c.redInt, (c, v) => new Color(v, c.greenInt, c.blueInt, c.alphaInt))
  private val green = channelField("G", third, c => c.greenInt, (c, v) => new Color(c.redInt, v, c.blueInt, c.alphaInt))
  private val blue = channelField("B", third, c => c.blueInt, (c, v) => new Color(c.redInt, c.greenInt, v, c.alphaInt))
  private val recentRow = new Container
  private val recentTitle = TextView(ColorPicker.recentLabel, 12.0, Theme.muted())

  size.width @= width
  layout @= Some(VerticalLayout(10.0))

  if (area) children += new ColorArea(hsv, width, math.round(width * 0.6).toDouble)
  children += hue
  if (!area) {
    children += new ColorSlider(ColorPicker.saturationLabel, width, hsv().saturation,
      List(hsv().copy(saturation = 0.0).color(false), hsv().copy(saturation = 1.0).color(false)), hsv().color(false),
      ColorText.percent(hsv().saturation))(s => hsv @= hsv().copy(saturation = s))
    children += new ColorSlider(ColorPicker.valueLabel, width, hsv().value,
      List(hsv().copy(value = 0.0).color(false), hsv().copy(value = 1.0).color(false)), hsv().color(false),
      ColorText.percent(hsv().value))(v => hsv @= hsv().copy(value = v))
  }
  if (alpha) children += new ColorSlider(ColorPicker.alphaLabel, width, hsv().alpha,
    List(hsv().color(false).withAlpha(0.0), hsv().color(false)), hsv().color(false), ColorText.percent(hsv().alpha),
    checked = true)(a => hsv @= hsv().copy(alpha = a))

  // the colour itself, over checks where it can be see-through, beside its hex
  preview.size.width @= ColorPicker.fieldHeight
  preview.size.height @= ColorPicker.fieldHeight
  if (alpha) preview.children += new ColorChecker(ColorPicker.fieldHeight, ColorPicker.fieldHeight, 7.0)
  preview.children += previewFill()
  children += ColorPicker.row(gap, List(preview, hex))
  children += ColorPicker.row(gap, List(red, green, blue))
  if (presets.nonEmpty) children += swatches(presets)
  recentRow.size.width @= width
  recentRow.layout @= Some(VerticalLayout(6.0))
  children += recentRow

  // the hex field: a colour typed in is the value at once; left, it shows the value again, in full
  hex.value @= ColorText.hex(value(), alpha)
  hex.value.attach(t => ColorText.parseHex(t).foreach(c => take(c)))
  hex.editing.attach(e => if (!e) hex.value @= ColorText.hex(value(), alpha))

  // the value and the hue, saturation and value in step, each only when the other says something new
  hsv.attach(h => if (value() != h.color(alpha)) value @= h.color(alpha))
  follow(value, (c: Color) => changed(c))
  follow(recent, (list: List[Color]) => showRecent(list))
  showRecent(recent())

  /** `colour` put first among the recent ones (once, ten at most). */
  def remember(colour: Color = value()): Unit = recent @= ColorPicker.remembered(recent(), colour)

  private def changed(c: Color): Unit = {
    if (hsv().color(alpha) != c) hsv @= Hsv.of(c, hsv())
    if (!ColorText.parseHex(hex.value()).map(p => opaque(p)).contains(c)) hex.value @= ColorText.hex(c, alpha)
  }

  // a colour from a field or a swatch, opaque unless see-through ones are allowed
  private def take(c: Color): Unit = {
    val kept = opaque(c)
    if (kept != value()) value @= kept
  }

  private def previewFill(): Container = {
    val fill = new Container
    fill.size.width @= ColorPicker.fieldHeight
    fill.size.height @= ColorPicker.fieldHeight
    if (!alpha) fill.radius @= 10.0
    fill.backgroundColor := value()
    fill.borderWidth @= 1.0
    fill.borderColor := Theme.border()
    fill
  }

  private def opaque(c: Color): Color = if (alpha) c else c.withAlpha(1.0)

  private def channelField(label: String, width: Double, read: Color => Int, write: (Color, Int) => Color): TextInput = {
    val field = new TextInput(label, width = width)
    field.value @= read(value()).toString
    field.value.attach(t => ColorText.parseChannel(t).foreach(v => take(write(value(), v))))
    field.editing.attach(e => if (!e) field.value @= read(value()).toString)
    follow(value, (c: Color) => if (!ColorText.parseChannel(field.value()).contains(read(c))) field.value @= read(c).toString)
    field
  }

  private def swatches(colours: List[Color]): Container = {
    val flow = new Container
    flow.size.width @= width
    flow.layout @= Some(FlowLayout(6.0, 6.0))
    colours.foreach(c => flow.children += swatch(c))
    flow
  }

  private def swatch(c: Color): ColorSwatch = new ColorSwatch(c, value() == opaque(c))(chosen => take(chosen))

  private def showRecent(list: List[Color]): Unit = {
    val old = recentRow.children()
    recentRow.children.clear()
    old.foreach(c => if (!(c eq recentTitle)) c.dispose())
    if (list.nonEmpty) recentRow.children ++= List(recentTitle, swatches(list))
  }

  override def dispose(): Unit = {
    recentRow.children -= recentTitle
    recentTitle.dispose()
    super.dispose()
  }

  override def kind: String = "ColorPicker"
}

object ColorPicker {
  /** How wide a picker is unless it says. */
  val width: Double = 280.0
  /** How tall its fields are (TextInput's height). */
  val fieldHeight: Double = 56.0

  /** What its parts are called, in the language in use (Words' "color.hue", …). */
  def hueLabel: String = Words("color.hue")
  def saturationLabel: String = Words("color.saturation")
  def valueLabel: String = Words("color.brightness")
  def alphaLabel: String = Words("color.opacity")
  def hexLabel: String = Words("color.hex")
  def recentLabel: String = Words("color.recent")

  /** The colours chosen lately, the latest first: every picker's unless it is given its own. Save and restore it as
    * the app likes (everyui.store). */
  val recent: Var[List[Color]] = Var(Nil)

  /** How many recent colours are kept. */
  val remembers: Int = 10

  /** `list` with `colour` first, once, cut to `remembers`. */
  def remembered(list: List[Color], colour: Color): List[Color] = (colour :: list.filterNot(_ == colour)).take(remembers)

  /** A dozen presets from Material's palette, for an app with none of its own. */
  lazy val material: List[Color] = List("f44336", "e91e63", "9c27b0", "673ab7", "3f51b5", "2196f3", "03a9f4", "00bcd4",
    "009688", "4caf50", "8bc34a", "cddc39", "ffeb3b", "ffc107", "ff9800", "ff5722", "795548", "9e9e9e", "607d8b",
    "000000", "ffffff").map(h => Color.fromHex(h))

  /** Around the wheel, red back to red: the hue slider's track. */
  lazy val hues: List[Color] = List("ff0000", "ffff00", "00ff00", "00ffff", "0000ff", "ff00ff", "ff0000").map(h => Color.fromHex(h))

  private def row(gap: Double, parts: List[Component]): Container = {
    val row = new Container
    row.layout @= Some(HorizontalLayout(gap, middle = true))
    row.children ++= parts
    row
  }
}
