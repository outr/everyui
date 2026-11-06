package everyui.kit

import everyui.component.*
import everyui.component.types.WhiteSpace
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, Shadow, ui}
import reactify.*

/** A short message over the screen for a few seconds, saying what just happened: one at a time, the newest in place of
  * the last. It sits at the foot of the screen, or its head with `top`, `margin` in from the edge, and goes after
  * `seconds`: longer for a long message, longer again where people asked for more time to read (`TimeLimits`), and
  * not while a pointer rests on it. Each message's words pass through `shown` as it shows
  * (`RemoteLog.install(toasts = true)` sends them to the server's log as warnings). A screen reader reads each out as
  * it shows, politely. */
object Toast {
  private var showing: Option[Container] = None
  private var made = 0
  /** The words of every message shown, as it shows: for an app to keep them (its log, its server's), since a message
    * goes after a few seconds whether anybody read it or not. */
  val shown: Channel[String] = Channel[String]

  def show(words: String, seconds: Double = 4.0, top: Boolean = false, margin: Double = 32.0): Unit = {
    showing.foreach(t => ui.children -= t)
    made += 1
    val mark = made
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
    shown @= words
    everyui.access.LiveRegions.say(words)
    // as long as people are to be given to read it (`TimeLimits`), longer for more words, and kept while a pointer
    // rests on it
    val stays = everyui.time.TimeLimits.scaled(math.max(seconds, words.length / 20.0))
    def expire(): Unit = if (mark == made) {
      if (plate.event.pointer.over()) Platform.after(1.0, () => expire()) else hide()
    }
    Platform.after(stays, () => expire())
  }

  /** The message showing taken away now. */
  def hide(): Unit = {
    showing.foreach(t => ui.children -= t)
    showing = None
  }
}
