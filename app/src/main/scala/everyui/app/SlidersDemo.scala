package everyui.app

import everyui.component.{Button, Column, Component, Row, ScrollContainer, Switch, TextView}
import everyui.i18n.Direction
import everyui.kit.Mirror
import everyui.render.Platform
import everyui.slider.{RangeSlider, Slider, SliderScale, ValueLabel}
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** Sliders, range sliders and switches: a smooth slider, a stepped one showing its value as it moves, one in divisions
  * with ticks showing it always, one out of use; a price range kept apart by a gap, and one in divisions; a vertical
  * slider; switches, one as a line of settings, one out of use. Under them, each value as it moves and what
  * `onChangeEnd` last said, as a filter would hear it. `theme=light` for the light palette, `rtl=1` for right to left.
  * Logs `sliders ok` once built, and each change's end (`sliders end volume=41`). */
object SlidersDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val width = math.min(480.0, ui.size.width() - 32.0)
    val wide = ui.size.width() >= 640.0
    val applied = Var("Nothing applied yet")

    val smooth = Var(0.3)
    val volume = Var(40.0)
    val rating = Var(3.0)
    val fixed = Var(60.0)
    val low = Var(200.0)
    val high = Var(700.0)
    val lowStep = Var(2.0)
    val highStep = Var(6.0)
    val level = Var(5.0)

    val brightness = new Slider(smooth, label = "Brightness", length = width)
    val loud = new Slider(volume, 0.0, 100.0, step = 1.0, length = width, label = "Volume", showValue = ValueLabel.Moving,
      format = v => SliderScale.text(v) + "%")
    val stars = new Slider(rating, 0.0, 5.0, divisions = Some(5), length = width, label = "Rating",
      showValue = ValueLabel.Always)
    val off = new Slider(fixed, 0.0, 100.0, length = width, label = "Out of use")
    off.disabled @= true
    val price = new RangeSlider(low, high, 0.0, 1000.0, step = 10.0, minGap = 50.0, length = width, label = "Price",
      showValue = ValueLabel.Moving, format = v => "$" + SliderScale.text(v))
    val span = new RangeSlider(lowStep, highStep, 0.0, 10.0, divisions = Some(10), minGap = 1.0, length = width,
      label = "Span", showValue = ValueLabel.Always)
    val upright = new Slider(level, 0.0, 10.0, step = 1.0, length = 160.0, label = "Level", showValue = ValueLabel.Moving,
      vertical = true)

    val wifi = new Switch("Wi-Fi", Var(true))
    val bluetooth = new Switch("Bluetooth")
    val locked = new Switch("Out of use", Var(true))
    locked.disabled @= true
    val line = new Switch("Apply filters as the slider moves", width = Some(width))

    def report(name: String, words: String): Unit = {
      applied @= "Applied " + name + ": " + words
      Platform.log("sliders end " + name + "=" + words)
    }
    brightness.onChangeEnd.attach(v => report("brightness", SliderScale.text(v)))
    loud.onChangeEnd.attach(v => report("volume", SliderScale.text(v)))
    stars.onChangeEnd.attach(v => report("rating", SliderScale.text(v)))
    price.onChangeEnd.attach(r => report("price", SliderScale.text(r._1) + "-" + SliderScale.text(r._2)))
    span.onChangeEnd.attach(r => report("span", SliderScale.text(r._1) + "-" + SliderScale.text(r._2)))
    upright.onChangeEnd.attach(v => report("level", SliderScale.text(v)))
    List(wifi, bluetooth, line).foreach(s => s.on.attach(v => Platform.log("sliders switch " + s.kind + "=" + v.toString)))

    val live = TextView.paragraph("Brightness " + SliderScale.text(smooth()) + " · Volume " + SliderScale.text(volume()) +
      "% · Rating " + SliderScale.text(rating()) + " · Price $" + SliderScale.text(low()) + "–$" + SliderScale.text(high()) +
      " · Span " + SliderScale.text(lowStep()) + "–" + SliderScale.text(highStep()) + " · Level " + SliderScale.text(level()),
      width, 12.0, Theme.muted())
    val said = TextView.bound(applied(), 12.0, Theme.muted())
    val theme = new Button("Light / dark", () =>
      Theme.palette @= (if (Theme.palette() eq Palette.light) Palette.dark else Palette.light))

    val body = Column(14.0)(
      heading("Slider"), caption("Brightness, smooth"), brightness, caption("Volume, in steps of 1"), loud,
      caption("Rating, in five divisions"), room(), stars, caption("Out of use"), off,
      heading("Range"), caption("Price, $50 apart at least"), price, caption("Span, in ten divisions"), room(), span,
      heading("Vertical"), Row(24.0, middle = true)(upright, caption("Level, 0 to 10")),
      heading("Switch"), if (wide) Row(12.0, middle = true)(wifi, bluetooth, locked) else Column(4.0)(wifi, bluetooth, locked), line,
      live, said, theme)
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    Mirror.place(body, () => ui.size.width(), () => 16.0)
    body.position.top @= 16.0
    scroll.content.children += body
    ui.children += scroll
    Platform.log("sliders ok " + width.toInt.toString)
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
  }

  private def heading(words: String): Component = TextView(words, 18.0)
  private def caption(words: String): Component = TextView(words, 13.0, Theme.muted())
  // room over a slider whose value always shows, for the label above its thumb
  private def room(): Component = new everyui.component.Container {
    size.height @= 16.0
  }
}
