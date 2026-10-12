package everyui.chat

import everyui.component.{Button, ButtonStyle, Component, Container, IconButton, Row, ScrollContainer, TextView}
import everyui.event.{Key, KeyEvent}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.kit.Mirror
import everyui.menu.{Menu, MenuItem}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A [[Composer]]'s queue as it shows over its field: a line saying how many messages wait (and, while the queue is
  * `paused`, that it is, with its actions), then a [[QueueChip]] for each, in the order they will go, which scrolls
  * past `maxHeight`. A chip is dragged by its handle into another place, or moved with Alt+Up and Alt+Down; the arrows
  * go from chip to chip, and Down from the last (or Escape from any) to `field`. What a chip asks for, an edit or a
  * removal, is the composer's (`edit`, `remove`); a move it makes itself, on `queue`.
  *
  * Paused, the line ends in Resume, Send next (while `nextReady`), Clear queue and the app's `actions`, in that order;
  * where they do not all fit beside the words, Resume stays and those after it that do not fit go, in order, into a ⋮
  * menu at the end. Left and Right go along them, Down to the first chip and Up from it to Resume. */
class DraftQueue(queue: Var[List[Draft]], paused: Val[Boolean], maxHeight: Val[Double], field: Component,
                 edit: Draft => Unit, remove: Draft => Unit, resume: () => Unit, sendNext: () => Unit,
                 nextReady: Val[Boolean], clear: () => Unit, actions: Val[List[QueueAction]]) extends Container {
  private val header = new Container
  private val said: Val[String] = own(Val(if (paused()) ChatWords.queuePaused + " · " + ChatWords.queued(queue().length)
    else ChatWords.queued(queue().length)))
  private val label = TextView.bound(said(), 12.0, if (paused()) Theme.text() else Theme.muted())
  // the same words whole and out of sight: the room they ask, by which the actions that fit are chosen, whatever the
  // line is cut to
  private val whole = TextView.bound(said(), 12.0)
  /** What sends the queue on again while it is paused. */
  val resumeButton: Button = action(ChatWords.resume, Some(MaterialIcon.PlayArrow), () => resume())
  /** What sends the queue's next message while it is paused, the rest still waiting. */
  val sendNextButton: Button = action(ChatWords.sendNext, Some(MaterialIcon.Send), () => sendNext())
  /** What takes every message off the queue. */
  val clearButton: Button = action(ChatWords.clearQueue, Some(MaterialIcon.ClearAll), () => clear())
  /** The actions after Resume that do not fit beside the words, as a menu. */
  val moreButton: IconButton = new IconButton(MaterialIcon.MoreVert, ChatWords.queueMore, () => more()) {
    size.width @= 26.0
    size.height @= 26.0
    radius @= 13.0
    icon.position.left @= 2.0
    icon.position.top @= 2.0
  }
  // the app's actions as buttons, made again as they change
  private val appButtons: Var[List[Button]] = own(Var(Nil))
  /** Every action after Resume, in order: Send next, Clear queue, and the app's. */
  val rest: Val[List[Button]] = own(Val(sendNextButton :: clearButton :: appButtons()))
  /** How many of `rest` show beside Resume: as many as fit, in order, with room for the ⋮ where any do not. */
  val fitting: Val[Int] = own(Val {
    val widths = rest().map(_.size.width() + DraftQueue.spacing)
    val room = size.width() - whole.size.width() - 8.0 - resumeButton.size.width()
    if (widths.sum <= room) widths.length
    else {
      val left = room - DraftQueue.spacing - moreButton.size.width()
      var n = 0
      var used = 0.0
      while (n < widths.length && used + widths(n) <= left) {
        used += widths(n)
        n += 1
      }
      n
    }
  })
  private val bar = Row(DraftQueue.spacing, middle = true)()
  /** The chips, scrolled past `maxHeight`. */
  val list: ScrollContainer = new ScrollContainer
  private val divider = new Container
  // the chips in the queue's order, each kept while its draft is queued so the focus stays on it as the queue changes
  private var chips: List[QueueChip] = Nil
  // a chip being dragged: where it was, where its top is in the list, and where the pointer took hold of it
  private var dragged: Option[QueueChip] = None
  private var dragFrom = 0
  private var dragTop = 0.0
  private var grabbed = 0.0
  private val pitch = QueueChip.height + QueueChip.gap

  header.size.width := size.width()
  header.size.height @= 26.0
  label.position.middle := header.size.middle()
  label.maxWidth := math.max(40.0, size.width() - (if (paused()) bar.size.width() + 8.0 else 0.0))
  Mirror.place(label, () => header.size.width(), () => 2.0)
  whole.visible @= false
  sendNextButton.enabled := nextReady()
  bar.position.middle := header.size.middle()
  Mirror.place(bar, () => header.size.width(), () => header.size.width() - bar.size.width())
  header.children ++= List(label, whole)
  actions.attach(_ => made())
  made()
  rest.attach(_ => arrange())
  fitting.attach(_ => arrange())
  arrange()
  paused.attach { on =>
    if (on) header.children += bar else header.children -= bar
    build()
  }
  if (paused()) header.children += bar

  list.position.top @= 28.0
  list.size.width := size.width()
  list.size.height := math.max(0.0, math.min(maxHeight(), queue().length * pitch - QueueChip.gap))
  divider.size.width := size.width()
  divider.size.height @= 1.0
  divider.backgroundColor := Theme.border()
  divider.position.top := list.position.top() + list.size.height() + 8.0
  size.height := divider.position.top() + 1.0
  children ++= List(header, list, divider)

  queue.attach(_ => build())
  build()

  /** The chip of each queued message, in order. */
  def all: List[QueueChip] = chips

  /** The focus on the last chip: Up from the start of the field. */
  def focusLast(): Unit = chips.lastOption.foreach(_.focus())

  /** The chip at `index` given the focus, else the one before it, else the field: where it goes once one leaves. */
  def focusAt(index: Int): Unit =
    if (chips.isEmpty) field.focus()
    else chips(math.max(0, math.min(index, chips.length - 1))).focus()

  // the chips made to match the queue: one kept for each draft still in it, one made for each new one
  private def build(): Unit = {
    var spare = chips
    val made = queue().map { d =>
      spare.find(_.draft eq d) match {
        case Some(c) =>
          spare = spare.filterNot(_ eq c)
          c
        case None => chip(d)
      }
    }
    spare.foreach { c =>
      list.content.children -= c
      c.dispose()
    }
    chips = made
    made.foreach(c => if (!list.content.children().exists(_ eq c)) list.content.children += c)
    var i = 0
    made.foreach { c =>
      c.index @= i
      c.count @= made.length
      if (i > 0) c.exitFocus(Key.ArrowUp, made(i - 1))
      else if (paused()) c.exitFocus(Key.ArrowUp, resumeButton)
      else c.dropExit(Key.ArrowUp)
      c.exitFocus(Key.ArrowDown, if (i < made.length - 1) made(i + 1) else field)
      i += 1
    }
    made.headOption.foreach(c => header.exitFocus(Key.ArrowDown, c))
    place()
  }

  private def chip(d: Draft): QueueChip = {
    var made: Option[QueueChip] = None
    val c = new QueueChip(d, () => edit(d), () => removed(d), e => made.foreach(m => key(m, e)),
      y => made.foreach(m => drag(m, y)), () => made.foreach(m => drop(m)))
    made = Some(c)
    c.size.width := list.size.width()
    c
  }

  // each chip in its place: in order, but for one being dragged, which follows the pointer, the rest making room for it
  private def place(): Unit = {
    val to = target()
    var i = 0
    chips.foreach { c =>
      val slot = dragged match {
        case Some(d) if d eq c => -1
        case Some(_) if dragFrom < to && i > dragFrom && i <= to => i - 1
        case Some(_) if to < dragFrom && i >= to && i < dragFrom => i + 1
        case _ => i
      }
      c.position.top @= (if (slot < 0) dragTop else slot * pitch)
      i += 1
    }
  }

  // where the chip being dragged would go, by where its middle is
  private def target(): Int =
    if (dragged.isEmpty) dragFrom
    else math.max(0, math.min(chips.length - 1, math.floor((dragTop + QueueChip.height / 2.0) / pitch).toInt))

  // the pointer at `y` on the screen, holding chip `c`
  private def drag(c: QueueChip, y: Double): Unit = {
    val at = y - list.content.absoluteTop
    if (dragged.isEmpty) {
      dragged = Some(c)
      dragFrom = chips.indexWhere(_ eq c)
      grabbed = at - c.position.top()
      // drawn over the others as it passes them
      list.content.children @= list.content.children().filterNot(_ eq c) :+ c
    }
    dragTop = math.max(0.0, math.min((chips.length - 1) * pitch, at - grabbed))
    place()
  }

  private def drop(c: QueueChip): Unit = if (dragged.exists(_ eq c)) {
    val to = target()
    dragged = None
    if (to != dragFrom) move(c, to) else place()
  }

  // a chip's keys: Delete or Backspace takes it off, E edits it, Alt+Up and Alt+Down move it, Escape goes to the field
  private def key(c: QueueChip, e: KeyEvent): Unit = {
    val at = chips.indexWhere(_ eq c)
    if (e.alt && e.key == Key.ArrowUp) move(c, at - 1)
    else if (e.alt && e.key == Key.ArrowDown) move(c, at + 1)
    else if (e.key == Key.Escape) field.focus()
    else if (e.key == Key.Backspace || e.name == "Delete") removed(c.draft)
    // a turn later, once the key is done with: the letter typed with it is the chip's, not the field's it goes to
    else Platform.later(() => edit(c.draft))
  }

  // a message taken off: the focus, where it was on the queue, on the chip that took its place
  private def removed(d: Draft): Unit = {
    val at = chips.indexWhere(_.draft eq d)
    val inside = Focus.focused().exists(f => chips.exists(_ eq f))
    remove(d)
    if (inside) focusAt(at)
  }

  /** The message of chip `c` moved to `to` in the queue, the focus kept on it, and said. */
  def move(c: QueueChip, to: Int): Unit = {
    val q = queue()
    val from = q.indexWhere(_ eq c.draft)
    if (from >= 0 && to >= 0 && to < q.length && to != from) {
      val rest = q.filterNot(_ eq c.draft)
      queue @= rest.take(to) ++ (c.draft :: rest.drop(to))
      Platform.announce(ChatWords.moved(to + 1, q.length))
    }
    else place()
  }

  /** What shows on the line now: Resume, those of `rest` that fit, and the ⋮ where any do not. */
  def shown: List[Component] = bar.children().toList

  // an action on the line: the focus, if it was on the line and the action took that away (the queue resumed, sent or
  // cleared), back to the field
  private def action(words: String, glyph: Option[MaterialIcon], run: () => Unit): Button =
    new Button(words, () => acted(run), ButtonStyle.Text, glyph, 11.5)

  private def acted(run: () => Unit): Unit = {
    val inside = Focus.focused().exists(f => (moreButton :: resumeButton :: rest()).exists(_ eq f))
    run()
    if (inside && !Focus.focused().exists(f => Focus.showing(f))) field.focus()
  }

  // the app's actions made into buttons, the last ones let go
  private def made(): Unit = {
    val was = appButtons()
    appButtons @= actions().map(a => action(a.label, a.icon, a.action))
    was.foreach { b =>
      bar.children -= b
      b.dispose()
    }
  }

  // the line's actions as many as fit, in order, the ⋮ after them where any do not
  private def arrange(): Unit = {
    val all = rest()
    val n = fitting()
    bar.children @= ((resumeButton :: all.take(n)) ++ (if (n < all.length) List(moreButton) else Nil)).toVector
  }

  // the actions that did not fit, as a menu under the ⋮, in their order
  private def more(): Unit = {
    val app = actions().map(a => MenuItem(a.label, () => acted(a.action), a.icon.getOrElse(MaterialIcon.Empty)))
    val items = MenuItem(ChatWords.sendNext, () => acted(() => sendNext()), MaterialIcon.Send, nextReady()) ::
      MenuItem(ChatWords.clearQueue, () => acted(() => clear()), MaterialIcon.ClearAll) :: app
    Menu.below(moreButton, items.drop(fitting()))
  }

  override def kind: String = "DraftQueue"
}

object DraftQueue {
  /** The room between two of the line's actions. */
  val spacing: Double = 2.0
}
