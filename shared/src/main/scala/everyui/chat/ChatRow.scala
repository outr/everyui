package everyui.chat

import everyui.Color
import everyui.component.Component
import everyui.component.types.Semantics
import everyui.list.ListRow
import everyui.theme.Theme
import reactify.*

/** A row of a `Transcript`, handed entry after entry as it scrolls: it makes the view for the entry it is given (a
  * message, a tool call, reasoning, a note, the app's own component) and is as tall as that is. A view is taken apart
  * when the row moves on, except what the transcript keeps (an app's component, a tool call's details), which is only
  * handed back. Under the transcript's cursor, with the focus on it, the row is lit. */
class ChatRow(transcript: Transcript) extends ListRow[ChatEntry] {
  private val view: Var[Option[Component]] = own(Var(None))
  // what the view was made for: the entry, and whether it led its group
  private var madeFor: Option[ChatEntry] = None
  private var madeLeading = false

  size.height := view().map(_.size.height()).getOrElse(0.0)
  backgroundColor := (if (current() && active()) Theme.accent().withAlpha(0.08) else Color.Clear)
  borderWidth := (if (current() && active()) 1.0 else 0.0)

  private def rebuild(): Unit = {
    val wanted = item()
    val leading = wanted.exists(e => transcript.leads(index(), e))
    val same = if (wanted.isEmpty || madeFor.isEmpty) wanted.isEmpty && madeFor.isEmpty
      else (wanted.get eq madeFor.get) && leading == madeLeading
    if (!same) {
      view().foreach { v =>
        v match {
          case c: CustomView => c.release()
          case a: ActivityView => a.release()
          case _ => ()
        }
        children -= v
        v.dispose()
      }
      madeFor = wanted
      madeLeading = leading
      val made = wanted.map(e => transcript.viewFor(e, leading, this))
      made.foreach(v => children += v)
      view @= made
    }
  }

  /** The message it shows, where it shows one. */
  private[chat] def message: Option[MessageView] = view() match {
    case Some(m: MessageView) => Some(m)
    case _ => None
  }

  semantics := item().map(e => Semantics("listitem", label = transcript.said(e))).getOrElse(Semantics.Plain)
  item.attach { _ =>
    rebuild()
    transcript.rowMoved(this)
  }
  index.attach(_ => rebuild())

  override def kind: String = "ChatRow"
}
