package everyui.chat

import everyui.component.{Button, ButtonStyle, Column, Component, Container, IconButton, MaterialIconView, Row, TextView}
import everyui.font.MaterialIcon
import everyui.kit.Spinner
import everyui.theme.Theme
import reactify.*

/** What is going on, over the composer: the connection while it is not connected (connecting, or offline with why and
  * a Retry), and who is working, each with what they are doing, for how long (live, from `now`) and a control to
  * cancel it where it can be. More than two at work are one line ("3 working") that opens onto them all. With nothing
  * to say it takes no room. */
class StatusBar(working: Var[Vector[Working]], connection: Var[ConnectionState], now: Val[Long]) extends Container {
  /** Whether more than two at work are each shown, rather than counted. */
  val expanded: Var[Boolean] = own(Var(false))
  /** What a press on a worker's avatar does: their picture unless the conversation says otherwise (`ChatView` hands on
    * its transcript's `onAuthor`). */
  val onAuthor: Var[Option[AuthorPressed => Unit]] = own(Var(None))

  private val column: Container = Column(2.0)()
  column.position.left := everyui.list.ListRow.along(StatusBar.inset, column.size.width(), size.width())
  column.position.top @= 4.0

  private def words(text: => String, colour: => everyui.Color = Theme.text()): TextView = {
    val view = new TextView {
      content := text
      font.size @= 12.5
      color := colour
    }
    view.maxWidth := math.max(60.0, size.width() - StatusBar.inset * 2.0 - 120.0)
    view
  }

  private def line(parts: Component*): Container = {
    val row = Row(8.0, middle = true)(parts*)
    row.size.height @= 28.0
    row
  }

  private def connectionLine(state: ConnectionState): Option[Container] = state match {
    case ConnectionState.Connected => None
    case ConnectionState.Connecting => Some(line(new Spinner(14.0, () => Theme.muted()), words(ChatWords.connecting, Theme.muted())))
    case offline: ConnectionState.Offline =>
      val glyph = new MaterialIconView(MaterialIcon.CloudOff, 16.0)
      glyph.color := Theme.danger()
      val said = words(ChatWords.offline + offline.reason.map(r => " · " + r).getOrElse(""), Theme.danger())
      Some(offline.retry match {
        case Some(again) => line(glyph, said, new Button(ChatWords.retry, again, ButtonStyle.Text, points = 12.0))
        case None => line(glyph, said)
      })
  }

  private def workLine(w: Working): Container = {
    val who = new Avatar(w.who, 18.0)
    MessageView.pressable(who, () => onAuthor().getOrElse(AuthorPressed.showPicture)(AuthorPressed(w.who, None)),
      AuthorPressed.answers(w.who, onAuthor()))
    val what = words(w.who.name + " · " + w.what)
    val took = words(ChatTime.span(now() - w.since), Theme.muted())
    w.cancel match {
      case Some(stop) => line(who, what, took, new IconButton(MaterialIcon.Close, ChatWords.cancel, stop))
      case None => line(who, what, took)
    }
  }

  private def countLine(all: Vector[Working]): Container = {
    val toggle = new IconButton(if (expanded()) MaterialIcon.ExpandMore else MaterialIcon.ExpandLess,
      if (expanded()) ChatWords.hide else ChatWords.show, () => expanded @= !expanded())
    line(new Spinner(14.0, () => Theme.accent()),
      words(ChatWords.workingCount(all.size) + (if (expanded()) "" else " · " + all.map(_.who.name).distinct.mkString(", "))),
      toggle)
  }

  private def rebuild(): Unit = {
    val was = column.children()
    column.children.clear()
    was.foreach(_.dispose())
    val all = working()
    val work = if (all.size <= 2) all.map(workLine)
      else if (expanded()) countLine(all) +: all.map(workLine)
      else Vector(countLine(all))
    column.children ++= connectionLine(connection()).toVector ++ work
  }

  size.height := (if (column.children().isEmpty) 0.0 else column.size.height() + 8.0)
  backgroundColor := Theme.surface()
  children += column
  follow(working, (_: Vector[Working]) => rebuild())
  follow(connection, (_: ConnectionState) => rebuild())
  expanded.attach(_ => rebuild())
  rebuild()

  override def kind: String = "StatusBar"
}

object StatusBar {
  /** Space before the lines. */
  val inset: Double = 12.0
}
