package everyui.chat

import everyui.Color
import everyui.component.{Component, Container}
import everyui.component.types.Semantics
import everyui.list.ListRow
import everyui.theme.Theme
import reactify.*

/** A row of a `Transcript`, handed entry after entry as it scrolls: it makes the view for the entry it is given (a
  * message, a tool call, reasoning, a note, the app's own component) and is as tall as that is. A view is taken apart
  * when the row moves on, except what the transcript keeps (an app's component, a tool call's details), which is only
  * handed back. Under the transcript's cursor, with the focus on it, the row is lit. An entry with a rail
  * (`ChatEntry.rail`) has it drawn the row's whole height at its start edge, the view after the room the transcript
  * keeps for it (`Transcript.rails`). */
class ChatRow(transcript: Transcript) extends ListRow[ChatEntry] {
  private val view: Var[Option[Component]] = own(Var(None))
  // the entry's rail, where it has one
  private val railColor: Val[Option[Color]] = own(Val(item().flatMap(_.rail())))
  // what the view was made for: the entry, and whether it led its group
  private var madeFor: Option[ChatEntry] = None
  private var madeLeading = false

  size.height := view().map(_.size.height()).getOrElse(0.0)
  backgroundColor := (if (current() && cursorShown()) Theme.accent().withAlpha(0.08) else Color.Clear)
  borderWidth := (if (current() && cursorShown()) 1.0 else 0.0)

  // (the list hands a row its place and its item one after the other: a view is made once both are this entry's, not
  // for the entry at the place it had, then again)
  private def rebuild(): Unit = if (item().forall(e => transcript.list.itemAt(index()).exists(_ eq e))) {
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
      // laid out once it is in the row: in the page, where the web reads its sizes with the rest of the turn's, rather
      // than part by part as it is made out of it, each read laying the page out again
      everyui.layout.LayoutPass.batch {
        val made = wanted.map(e => transcript.viewFor(e, leading, this))
        made.foreach { v =>
          v.position.left := ListRow.along(transcript.gutter(), v.size.width(), size.width())
          children += v
        }
        view @= made
      }
    }
  }

  /** How wide the view is: the row, less the room kept for the rails. */
  private[chat] def inner: Double = size.width() - transcript.gutter()

  // ---- a rail (ChatEntry.rail): its line made the first time the row shows one, so others have none ----
  /** The line down the row's start edge in its entry's rail colour, the row's whole height (the space above the entry
    * too), so that rows one after another of the same colour draw one unbroken line. */
  private[chat] lazy val railLine: Container = new Container {
    size.width @= Transcript.railWidth
    size.height := ChatRow.this.size.height()
    backgroundColor := railColor().getOrElse(Color.Clear)
    visible := railColor().nonEmpty
    position.left := ListRow.along(Transcript.railInset, Transcript.railWidth, ChatRow.this.size.width())
  }
  private var railMade = false
  private def makeRail(): Unit = if (!railMade) {
    railMade = true
    children += railLine
  }
  railColor.attach(c => if (c.nonEmpty) makeRail())

  /** The rail's line, where the row has made one. */
  private[chat] def rail: Option[Container] = if (railMade) Some(railLine) else None
  /** The view of its entry, where it has one. */
  private[chat] def shown: Option[Component] = view()

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
