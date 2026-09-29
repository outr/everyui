package everyui.chat

import everyui.component.{Component, Container, MaterialIconView, Row, TextView}
import everyui.component.types.{FontWeight, TextDirection, WhiteSpace}
import everyui.font.MaterialIcon
import everyui.kit.Spinner
import everyui.list.ListRow
import everyui.theme.Theme
import reactify.*

/** A tool call in the transcript, `width` wide: one line in a quiet box (a spinner while it runs, a tick or a cross once
  * it has, what it does, and a chevron where there is more), which a click (or Enter, from the transcript) opens onto
  * its details. `details` hands over what the entry's `details` made, the first time it is opened: the transcript keeps
  * it, so it is not made again, and takes it back (`release`) before this is taken apart. */
class ActivityView(a: ChatEntry.Activity, width: => Double, details: () => Option[Component]) extends Container {
  private val widest: Val[Double] = own(Val(Transcript.widest(width)))

  private val spinner: Spinner = new Spinner(14.0, () => Theme.accent())
  spinner.visible := a.status() == ActivityStatus.Running
  private val glyph: MaterialIconView = new MaterialIconView(MaterialIcon.CheckCircle, 16.0)
  glyph.icon := (if (a.status() == ActivityStatus.Failed) MaterialIcon.Error else MaterialIcon.CheckCircle)
  glyph.color := (if (a.status() == ActivityStatus.Failed) Theme.danger() else Theme.success())
  glyph.visible := a.status() != ActivityStatus.Running
  private val status: Container = new Container {
    size.width @= 16.0
    size.height @= 16.0
    children ++= List(spinner, glyph)
  }
  spinner.position.left @= 1.0
  spinner.position.top @= 1.0
  private val summary: TextView = new TextView {
    content := a.summary()
    direction @= TextDirection.Auto
    font.size @= 13.0
    color := Theme.text()
    maxWidth := widest() - Transcript.padX * 2.0 - 16.0 - 18.0 - 16.0
  }
  private val chevron: MaterialIconView = new MaterialIconView(MaterialIcon.ExpandMore, 18.0)
  chevron.icon := (if (a.expanded()) MaterialIcon.ExpandLess else MaterialIcon.ExpandMore)
  chevron.color := Theme.muted()
  chevron.visible @= a.details.nonEmpty
  chevron.size.width @= (if (a.details.nonEmpty) 18.0 else 0.0)
  private val line: Container = Row(8.0, middle = true)(status, summary, chevron)

  private val holder: Container = new Container
  private var shown: Option[Component] = None
  private val open: Val[Boolean] = own(Val(a.expanded() && a.details.nonEmpty))

  private val box: Container = new Container {
    size.width := (if (open()) widest() else math.min(widest(), line.size.width() + Transcript.padX * 2.0))
    size.height := line.size.height() + 12.0 + (if (open()) holder.size.height() + 8.0 else 0.0)
    radius @= 10.0
    borderWidth @= 1.0
    borderColor := Theme.border()
    backgroundColor := Theme.surface()
    cursor @= (if (a.details.nonEmpty) "pointer" else "")
    children ++= List(line, holder)
  }
  line.position.left := ListRow.along(Transcript.padX, line.size.width(), box.size.width())
  line.position.top @= 6.0
  holder.size.width := box.size.width() - Transcript.padX * 2.0
  holder.position.left @= Transcript.padX
  holder.position.top := line.size.height() + 12.0
  holder.visible := open()
  holder.size.height := holder.children().headOption.map(_.size.height()).getOrElse(0.0)
  // what is wider than the box is cut at its edge
  holder.size.width.attach(_ => holder.node.setClip(holder.size.width(), holder.size.height()))
  holder.size.height.attach(_ => holder.node.setClip(holder.size.width(), holder.size.height()))
  box.event.click.attach { e =>
    if (a.details.nonEmpty) {
      e.consume()
      a.expanded @= !a.expanded()
    }
  }

  private def place(on: Boolean): Unit = if (on && shown.isEmpty) {
    shown = details()
    shown.foreach { c =>
      c.parent().foreach {
        case p: Container => p.children -= c
        case _ => ()
      }
      holder.children += c
    }
  }
  open.attach(on => place(on))
  place(open())

  box.position.left := ListRow.along(Transcript.side + Transcript.lead, box.size.width(), width)
  box.position.top @= Transcript.lineGap + 2.0
  size.width := width
  size.height := box.size.height() + Transcript.lineGap * 2.0 + 2.0
  children += box

  /** The details put back where the transcript keeps them, so taking this apart does not take them with it. */
  def release(): Unit = shown.foreach { c =>
    holder.children -= c
    shown = None
  }

  override def kind: String = "ActivityView(" + a.id + ")"
}

object ActivityView {
  /** Input and output as plain text under small headings, wrapped to the width it is given (its parent's). */
  def textDetails(input: String, output: String): Component = {
    def heading(words: String): TextView = new TextView {
      content @= words
      font.size @= 11.0
      font.weight @= FontWeight.SemiBold
      color := Theme.muted()
    }
    def body(words: String): TextView = new TextView {
      content @= words
      font.size @= 12.5
      whiteSpace @= WhiteSpace.PreWrap
      direction @= TextDirection.Auto
      wrapAnywhere @= true
      color := Theme.text()
    }
    val in = body(input)
    val out = body(output)
    val column = everyui.component.Column(4.0)(heading(ChatWords.input), in, heading(ChatWords.output), out)
    val box = new Container {
      size.width := parent().map(_.size.width()).getOrElse(320.0)
      size.height := column.size.height()
      children += column
    }
    in.size.width := box.size.width()
    out.size.width := box.size.width()
    box
  }

  /** How a status is read out. */
  def said(status: ActivityStatus): String = status match {
    case ActivityStatus.Running => ChatWords.running
    case ActivityStatus.Succeeded => ChatWords.succeeded
    case ActivityStatus.Failed => ChatWords.failed
  }
}
