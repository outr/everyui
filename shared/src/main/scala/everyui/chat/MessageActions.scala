package everyui.chat

import everyui.Color
import everyui.component.{Container, IconButton}
import everyui.component.types.Semantics
import everyui.event.{Key, KeyEvent}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.layout.HorizontalLayout
import everyui.list.VirtualList
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** The small row of buttons a `Transcript` shows beside one message at a time (`Transcript.messageActions`): one of
  * them for the whole transcript, moved to the message the pointer is over or the cursor is on, and drawn over the rows
  * rather than among them, so nothing moves or changes height as it comes and goes. It sits beside the bubble on the
  * side away from the edge it is at (after everybody else's, before one's own, mirrored right to left), and where there
  * is no room there, on the bubble's corner, above its words. It stays in view while any of the bubble shows.
  *
  * The arrows move along it, and on out of either end, up or down, Escape or Back go back to the transcript. An action
  * that says what it has `done` shows a tick for a moment, with the words as its tooltip, and has them read out. */
class MessageActions(list: VirtualList[ChatEntry], changed: () => Unit, away: () => Unit) extends Container {
  /** The message the actions are for, while they show. */
  val message: Var[Option[ChatEntry.Message]] = own(Var(None))
  // the row showing it, where it is made
  private val row: Var[Option[ChatRow]] = own(Var(None))
  private var shown: List[MessageAction] = Nil
  private var buttons: Vector[IconButton] = Vector.empty
  // the acknowledgement showing, if any, by its turn: a later one, or a move, puts it away
  private var turn: Int = 0

  // where it goes, against the bubble of the row, in the transcript (the list's box): None where none of it shows
  private val after: Val[Boolean] = own(Val(message().exists(_.self) == Direction.rtl()))
  private def beside(left: Double, width: Double): Double =
    if (after()) left + width + MessageActions.gap else left - MessageActions.gap - size.width()
  private def fits(left: Double, width: Double): Boolean = {
    val at = beside(left, width)
    at >= MessageActions.edge && at + size.width() <= list.size.width() - MessageActions.edge
  }
  private val across: Val[Double] = own(Val(row().flatMap(_.message).map { v =>
    val b = v.bubble
    val left = b.position.left()
    val width = b.size.width()
    if (fits(left, width)) beside(left, width)
    else if (after()) math.max(MessageActions.edge, left + width - size.width())
    else math.min(list.size.width() - size.width() - MessageActions.edge, left)
  }.getOrElse(0.0)))
  private val down: Val[Option[Double]] = own(Val(row().flatMap(r => r.message.flatMap { v =>
    val b = v.bubble
    val top = r.position.top() + b.position.top()
    val height = b.size.height()
    val h = size.height()
    if (top + height <= 0.0 || top >= list.size.height()) None
    else {
      val wanted = if (fits(b.position.left(), b.size.width())) top + math.max(0.0, math.min(4.0, (height - h) / 2.0))
        else top - h + Transcript.padY
      Some(math.max(MessageActions.edge, math.min(list.size.height() - h - MessageActions.edge, wanted)))
    }
  })))
  private val buttonsMade: Var[Boolean] = own(Var(false))
  /** Whether it shows. */
  val showing: Val[Boolean] = own(Val(down().nonEmpty && buttonsMade()))

  position.left := across()
  position.top := down().getOrElse(0.0)
  visible := showing()
  layout @= Some(HorizontalLayout(2.0, middle = true))
  padding @= 2.0
  radius @= 18.0
  backgroundColor := Theme.raised()
  borderWidth @= 1.0
  borderColor := Theme.border()
  shadow @= Some(everyui.Shadow(0.0, 1.0, 4.0, Color.Black.withAlpha(0.18)))
  semantics @= Semantics("toolbar", label = ChatWords.messageActions)
  // out of either end, up or down: back to the message
  List(Key.ArrowLeft, Key.ArrowRight, Key.ArrowUp, Key.ArrowDown).foreach(k => exitFocus(k, list))
  // (made now: the pointer going from the row onto these is still on the message)
  event.pointer.over.attach(on => if (!on) away())
  follow(Focus.focused, (_: Option[everyui.component.Component]) => changed())

  /** Whether the focus is on one of the buttons. */
  def holdsFocus: Boolean = Focus.focused().exists(f => Focus.within(f, this))

  /** Shown for `target` with `actions` (None: put away). */
  def show(target: Option[ChatEntry.Message], actions: List[MessageAction]): Unit = {
    val moved = target.map(_.id) != message().map(_.id)
    // moved by the pointer from under the focus: the focus goes back to the transcript, not on with the buttons
    if (moved && holdsFocus) list.focus()
    if (!same(actions, shown)) {
      buttons.foreach { b =>
        children -= b
        b.dispose()
      }
      buttons = actions.zipWithIndex.map { case (a, i) => button(a, i) }.toVector
      children ++= buttons
    } else if (moved) settle()
    shown = actions
    buttonsMade @= buttons.nonEmpty
    message @= target
    row @= target.flatMap(m => list.rowAt(list.indexOf(m.id)) match {
      case Some(r: ChatRow) if r.item().exists(_.id == m.id) => Some(r)
      case _ => None
    })
  }

  /** `r` handed another entry as the list scrolls: it shows the message, or no longer does. */
  def rowMoved(r: ChatRow): Unit = message().foreach { m =>
    if (r.item().exists(_.id == m.id)) {
      if (!row().exists(_ eq r)) row @= Some(r)
    } else if (row().exists(_ eq r)) row @= None
  }

  /** The action at `index` done, for the message they show for, and acknowledged where it says what it has done. */
  def run(index: Int): Unit = if (index >= 0 && index < shown.length) {
    val a = shown(index)
    a.action()
    if (a.done.nonEmpty) buttons.lift(index).foreach { b =>
      turn += 1
      val mine = turn
      b.icon.icon @= MaterialIcon.Check
      b.tooltip @= a.done
      b.semantics @= Semantics.button(a.done)
      Platform.announce(a.done)
      Platform.after(MessageActions.acknowledged, () => if (mine == turn) settle())
    }
  }

  /** The buttons, in order. */
  def actionButtons: Vector[IconButton] = buttons

  // every button as its action has it, no acknowledgement showing
  private def settle(): Unit = {
    turn += 1
    buttons.zip(shown).foreach { case (b, a) =>
      b.icon.icon @= a.icon
      b.tooltip @= a.label
      b.semantics @= Semantics.button(a.label)
    }
  }

  private def same(a: List[MessageAction], b: List[MessageAction]): Boolean = a.length == b.length &&
    a.zip(b).forall { case (x, y) => x.icon == y.icon && x.label == y.label && x.done == y.done }

  private def button(a: MessageAction, index: Int): IconButton = {
    val b = new IconButton(a.icon, a.label, () => run(index)) {
      override def keepsEvent(e: KeyEvent): Boolean = e.key == Key.Escape || e.key == Key.Back || super.keepsEvent(e)
    }
    b.focusable := showing()
    b.event.key.down.attach(e => if (e.key == Key.Escape || e.key == Key.Back) list.focus())
    b
  }

  override def kind: String = "MessageActions"
}

object MessageActions {
  /** Space between the bubble and the buttons, and kept from the transcript's edges. */
  val gap: Double = 6.0
  val edge: Double = 4.0
  /** How long an acknowledgement ("Copied") shows, in seconds. */
  val acknowledged: Double = 1.5
}
