package everyui.kit

import everyui.component.*
import everyui.component.types.WhiteSpace
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, Shadow, ui}
import reactify.*

/** A short message over the screen for a few seconds, saying what just happened: one at a time, the newest in place of
  * the last. It sits at the foot of the screen, or its head with `top`, `margin` in from the edge, and goes after
  * `seconds`. */
object Toast {
  private var showing: Option[Container] = None
  private var shown = 0

  def show(words: String, seconds: Double = 4.0, top: Boolean = false, margin: Double = 32.0): Unit = {
    showing.foreach(t => ui.children -= t)
    shown += 1
    val mark = shown
    val text = new TextView {
      whiteSpace @= WhiteSpace.Normal
      size.width := math.min(560.0, ui.size.width() - 160.0)
      font.size @= 14.0
      color := Theme.text()
      content @= words
    }
    val plate = new Container {
      size.width := text.size.width() + 40.0
      size.height := text.size.height() + 28.0
      backgroundColor := Theme.raised()
      radius @= 8.0
      shadow @= Some(Shadow(0.0, 4.0, 16.0, Color.Black.withAlpha(0.5)))
      children += text
    }
    text.position.left @= 20.0
    text.position.top @= 14.0
    plate.position.left := (ui.size.width() - plate.size.width()) / 2.0
    plate.position.top := (if (top) margin else ui.size.height() - plate.size.height() - margin)
    ui.children += plate
    showing = Some(plate)
    Platform.after(seconds, () => if (mark == shown) hide())
  }

  /** The message showing taken away now. */
  def hide(): Unit = {
    showing.foreach(t => ui.children -= t)
    showing = None
  }
}
