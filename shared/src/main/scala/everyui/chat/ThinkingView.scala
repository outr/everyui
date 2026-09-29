package everyui.chat

import everyui.component.{Container, MaterialIconView, Row, TextView}
import everyui.component.types.WhiteSpace
import everyui.font.MaterialIcon
import everyui.kit.Spinner
import everyui.list.ListRow
import everyui.theme.Theme
import reactify.*

/** An agent's reasoning in the transcript, `width` wide: one quiet line saying how long it has been thinking (live,
  * from `now`) or how long it thought, which a click (or Enter, from the transcript) opens onto the words themselves. */
class ThinkingView(t: ChatEntry.Thinking, width: => Double, now: Val[Long]) extends Container {
  private val widest: Val[Double] = own(Val(Transcript.widest(width)))

  private val spinner: Spinner = new Spinner(14.0, () => Theme.muted())
  spinner.visible := t.done().isEmpty
  private val glyph: MaterialIconView = new MaterialIconView(MaterialIcon.Psychology, 16.0)
  glyph.color := Theme.muted()
  glyph.visible := t.done().nonEmpty
  private val mark: Container = new Container {
    size.width @= 16.0
    size.height @= 16.0
    children ++= List(spinner, glyph)
  }
  spinner.position.left @= 1.0
  spinner.position.top @= 1.0
  private val label: TextView = new TextView {
    content := (t.done() match {
      case Some(took) => ChatWords.thoughtFor(ChatTime.span(took))
      case None => ChatWords.thinking + " · " + ChatTime.span(now() - t.started)
    })
    font.size @= 13.0
    color := Theme.muted()
  }
  private val chevron: MaterialIconView = new MaterialIconView(MaterialIcon.ExpandMore, 18.0)
  chevron.icon := (if (t.expanded()) MaterialIcon.ExpandLess else MaterialIcon.ExpandMore)
  chevron.color := Theme.muted()
  private val line: Container = Row(8.0, middle = true)(mark, label, chevron)

  private val words: TextView = new TextView {
    content := t.text()
    font.size @= 12.5
    whiteSpace @= WhiteSpace.PreWrap
    color := Theme.muted()
    visible := t.expanded()
  }
  words.size.width := widest() - Transcript.padX * 2.0

  private val box: Container = new Container {
    size.width := (if (t.expanded()) widest() else math.min(widest(), line.size.width() + Transcript.padX * 2.0))
    size.height := line.size.height() + 12.0 + (if (t.expanded()) words.size.height() + 8.0 else 0.0)
    radius @= 10.0
    borderWidth @= 1.0
    borderColor := Theme.border()
    cursor @= "pointer"
    children ++= List(line, words)
  }
  line.position.left := ListRow.along(Transcript.padX, line.size.width(), box.size.width())
  line.position.top @= 6.0
  words.position.left := ListRow.along(Transcript.padX, words.size.width(), box.size.width())
  words.position.top := line.size.height() + 12.0
  box.event.click.attach { e =>
    e.consume()
    t.expanded @= !t.expanded()
  }

  box.position.left := ListRow.along(Transcript.side + Transcript.lead, box.size.width(), width)
  box.position.top @= Transcript.lineGap + 2.0
  size.width := width
  size.height := box.size.height() + Transcript.lineGap * 2.0 + 2.0
  children += box

  override def kind: String = "ThinkingView(" + t.id + ")"
}
