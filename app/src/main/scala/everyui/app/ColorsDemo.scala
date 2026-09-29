package everyui.app

import everyui.Color
import everyui.color.{ColorDialog, ColorPicker, ColorText, ColorTrack}
import everyui.component.{Button, ButtonStyle, Column, Container, Row, ScrollContainer, TextView}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** The colour picker: one on the page, with alpha and Material's presets, a card painted in what it holds, and a
  * button that asks for a colour in a dialog. `theme=light` shows the light palette, `dialog=1` opens the dialog at once,
  * and `area=0` shows the sliders a Roku and an Apple TV have in place of the area. Logs `colors ok` once built, and
  * `colors chose <hex>` as the dialog answers. */
object ColorsDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val colour = Var(Color.fromHex("1e88e5cc"))
    val chosen = Var("")
    val wide = ui.size.width() >= 640.0
    val picker = new ColorPicker(colour, alpha = true, presets = ColorPicker.material,
      width = if (wide) 320.0 else math.min(360.0, ui.size.width() - 32.0),
      area = ColorTrack.gradients && Platform.launchArg("area") != "0")
    val card = new Container {
      size.width @= (if (wide) 240.0 else picker.size.width())
      size.height @= 140.0
      radius @= 14.0
      backgroundColor := colour()
      borderWidth @= 1.0
      borderColor := Theme.border()
    }
    val said = TextView.bound(ColorText.hex(colour(), alpha = true), 13.0, Theme.muted())
    val side = Column(8.0)(card, said)
    val open = new Button("Pick in a dialog", () => ask(colour, chosen), style = ButtonStyle.Filled)
    val theme = new Button("Light / dark", () =>
      Theme.palette @= (if (Theme.palette() eq Palette.light) Palette.dark else Palette.light))
    val bar = Row(10.0, middle = true)(open, theme)
    val answer = TextView.bound(chosen(), 12.0, Theme.muted())
    val body = if (wide) Row(24.0)(picker, side) else Column(16.0)(picker, side)
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height() - 64.0
      position.top @= 64.0
    }
    bar.position.left @= 16.0
    bar.position.top @= 12.0
    answer.position.left := bar.position.left() + bar.size.width() + 12.0
    answer.position.top @= 24.0
    body.position.left @= 16.0
    body.position.top @= 8.0
    scroll.content.children += body
    ui.children ++= List(bar, answer, scroll)
    Platform.log("colors ok " + picker.size.width().toInt.toString + "x" + picker.size.height().toInt.toString)
    if (Platform.launchArg("dialog") == "1") Platform.after(0.2, () => ask(colour, chosen))
    // a frame saved for a check (--freeze with --screenshot on the desktop), once the dialog is up
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
  }

  private def ask(colour: Var[Color], chosen: Var[String]): Unit =
    ColorDialog.pick("Choose a colour", colour(), presets = ColorPicker.material).map { answer =>
      answer.foreach(c => colour @= c)
      chosen @= answer.map(c => "Chose " + c.toHex).getOrElse("Cancelled")
      Platform.log("colors chose " + answer.map(c => c.toHex).getOrElse("nothing"))
    }.start()
}
