package everyui.chat

import everyui.async.Task
import everyui.component.{Component, Container}
import everyui.event.Key
import everyui.theme.Theme
import reactify.*

/** A whole conversation: the transcript, a status bar over the composer (who is working, and the connection while it
  * is not connected), and the composer, which is the app's (`composer`: whatever it types with; none where the
  * conversation is only read, as on a television). Everything it shows is plain reactive data, and everything it does
  * goes to the app's callbacks (a working agent's cancel, the connection's retry), so it knows nothing of what is at
  * the other end.
  *
  * {{{
  *   val chat = new ChatView(entries, loadOlder = Some(() => server.before(entries().headOption)),
  *     working = agentsAtWork, connection = socketState, composer = Some(new Composer(...)))
  *   chat.size.width := ui.size.width()
  *   chat.size.height := ui.size.height()
  *   ui.children += chat
  * }}} */
class ChatView(val entries: Var[Vector[ChatEntry]],
               loadOlder: Option[() => Task[Vector[ChatEntry]]] = None,
               val working: Var[Vector[Working]] = Var(Vector.empty),
               val connection: Var[ConnectionState] = Var(ConnectionState.Connected),
               composer: Option[Component] = None,
               render: (Val[String], Val[Double]) => Component = MessageMarkdown.render) extends Container {
  val transcript: Transcript = new Transcript(entries, loadOlder, render)
  val status: StatusBar = new StatusBar(working, connection, transcript.ticker.now)
  status.onAuthor := transcript.onAuthor()
  /** What a press on an author's avatar or name does, in the transcript and the status bar: the app's handler, or with
    * none their picture over the screen (`AuthorPressed.showPicture`). */
  def onAuthor: Var[Option[AuthorPressed => Unit]] = transcript.onAuthor
  /** What types the messages: the app's, filling the width at the bottom; none, and the transcript takes the room. */
  val composerSlot: Var[Option[Component]] = own(Var(composer))

  private val below: Val[Double] = own(Val(composerSlot().map(_.size.height()).getOrElse(0.0)))

  transcript.size.width := size.width()
  transcript.size.height := math.max(0.0, size.height() - status.size.height() - below())
  status.size.width := size.width()
  status.position.top := transcript.size.height()
  backgroundColor := Theme.background()
  size.width @= 400.0
  size.height @= 600.0
  children ++= List(transcript, status)

  private var placed: Option[Component] = None
  private def place(next: Option[Component]): Unit = {
    placed.foreach { c =>
      children -= c
      Component.unbind(c.size.width)
      Component.unbind(c.position.top)
    }
    placed = next
    next.foreach { c =>
      c.size.width := size.width()
      c.position.left @= 0.0
      c.position.top := size.height() - c.size.height()
      children += c
      transcript.exitFocus(Key.ArrowDown, c)
    }
    // the status bar sits on the composer's card: rounded as it is, so round corners are not set against square ones
    next match {
      case Some(c: Composer) => status.radius := c.cornerRadius()
      case _ => status.radius @= 0.0
    }
    if (next.isEmpty) transcript.dropExit(Key.ArrowDown)
  }
  composerSlot.attach(c => place(c))
  place(composerSlot())

  override def focus(): Unit = transcript.focus()
  override def kind: String = "ChatView"
}
