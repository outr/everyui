package everyui.chat

import everyui.Color
import everyui.component.{Component, Container, Row, TextView}
import everyui.component.types.{FontWeight, TextDirection}
import everyui.kit.Spinner
import everyui.list.ListRow
import everyui.theme.Theme
import reactify.*

/** A message in the transcript, `width` wide: a bubble of its words (`render` makes them), at the start edge under the
  * author's avatar and name, or at the end edge for the person using the app. A message that `leads` its group has the
  * avatar, the name and when it was said (`now` keeps "5m" current); the rest of the group sit close under it. Under
  * the bubble go the app's `meta` line and, while the words are still arriving, a small spinner. When it was said has
  * its date too unless the transcript names the days (`dated`). */
class MessageView(m: ChatEntry.Message, leads: Boolean, width: => Double, now: Val[Long],
                  render: (Val[String], Val[Double]) => Component, dated: Boolean = true) extends Container {
  private val top: Double = if (leads) Transcript.groupGap else Transcript.lineGap

  private val name: TextView = new TextView {
    content @= m.author.name
    direction @= TextDirection.Auto
    font.size @= 12.5
    font.weight @= FontWeight.SemiBold
    color := Theme.text()
  }
  private val when: TextView = new TextView {
    content := m.time.map(t => ChatTime.relative(t, now(), dated)).getOrElse("")
    direction @= TextDirection.Auto
    font.size @= 11.5
    color := Theme.muted()
  }
  m.time.foreach(t => when.tooltip @= everyui.util.Format.time(t))
  private val header: Container = if (m.self) Row(8.0, middle = true)(when) else Row(8.0, middle = true)(name, when)
  header.visible @= leads

  private val words: Component = render(own(Val(if (m.text().isEmpty && m.streaming()) "…" else m.text())),
    own(Val(Transcript.widest(width) - Transcript.padX * 2.0)))
  private val bubble: Container = new Container {
    size.width := math.max(Transcript.padX * 2.0 + 8.0, words.size.width() + Transcript.padX * 2.0)
    size.height := words.size.height() + Transcript.padY * 2.0
    radius @= 14.0
    backgroundColor := MessageView.fill(m.self)
    children += words
  }
  words.position.left := ListRow.along(Transcript.padX, words.size.width(), bubble.size.width())
  words.position.top @= Transcript.padY

  private val spinner: Spinner = new Spinner(12.0, () => Theme.muted())
  spinner.size.width := (if (m.streaming()) 12.0 else 0.0)
  spinner.visible := m.streaming()
  private val meta: TextView = new TextView {
    content := m.meta().getOrElse("")
    direction @= TextDirection.Auto
    font.size @= 11.5
    color := Theme.muted()
    maxWidth := Transcript.widest(width)
  }
  private val footer: Container = Row(6.0, middle = true)(spinner, meta)
  footer.visible := m.streaming() || m.meta().nonEmpty

  // along the row: the start edge, past the avatar, for everybody else's; the end edge for one's own
  private def at(length: Double): Double =
    if (m.self) ListRow.along(width - Transcript.side - length, length, width)
    else ListRow.along(Transcript.side + Transcript.lead, length, width)

  private val headerHeight: Val[Double] = own(Val(if (leads) header.size.height() + 3.0 else 0.0))
  header.position.left := at(header.size.width())
  header.position.top @= top
  bubble.position.left := at(bubble.size.width())
  bubble.position.top := top + headerHeight()
  footer.position.left := at(footer.size.width())
  footer.position.top := bubble.position.top() + bubble.size.height() + 3.0

  if (!m.self && leads) {
    val face = new Avatar(m.author, Transcript.avatar)
    face.position.left := ListRow.along(Transcript.side, Transcript.avatar, width)
    face.position.top @= top
    children += face
  }
  size.width := width
  size.height := footer.position.top() + (if (footer.visible()) footer.size.height() + 2.0 else -3.0)
  children ++= List(header, bubble, footer)

  override def kind: String = "MessageView(" + m.id + ")"
}

object MessageView {
  /** A bubble's colour: one's own a wash of the accent, everybody else's the control colour; the text colour reads on
    * either, in a light theme and a dark one. */
  def fill(self: Boolean): Color = if (self) Theme.accent().withAlpha(0.2) else Theme.control()
}
