package everyui.chat

import everyui.component.{Container, TextView}
import everyui.component.types.TextDirection
import everyui.theme.Theme
import reactify.*

/** A note between the rest of the transcript, `width` wide: its words centred and quiet, on a faint pill. */
class MarkerView(marker: ChatEntry.Marker, width: => Double) extends Container {
  private val words: TextView = new TextView {
    content := marker.text()
    direction @= TextDirection.Auto
    font.size @= 11.5
    color := Theme.muted()
    maxWidth := math.max(40.0, width - Transcript.side * 2.0 - 20.0)
  }
  private val pill: Container = new Container {
    size.width := words.size.width() + 20.0
    size.height := words.size.height() + 6.0
    radius := size.height() / 2.0
    backgroundColor := Theme.control().withAlpha(0.6)
    children += words
  }
  words.position.left @= 10.0
  words.position.top @= 3.0
  pill.position.left := (width - pill.size.width()) / 2.0
  pill.position.top @= Transcript.groupGap
  size.width := width
  size.height := pill.size.height() + Transcript.groupGap * 2.0
  children += pill

  override def kind: String = "MarkerView(" + marker.id + ")"
}
