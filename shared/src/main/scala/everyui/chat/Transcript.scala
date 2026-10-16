package everyui.chat

import everyui.async.Task
import everyui.clipboard.Clipboard
import everyui.component.{Component, Container}
import everyui.component.types.Semantics
import everyui.event.{Key, KeyEvent, Pointer}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.Spinner
import everyui.list.{ListRow, VirtualList}
import everyui.menu.{Menu, MenuItem}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A conversation's entries, oldest at the top, as a chat shows them: messages in bubbles (one's own at the end edge,
  * everybody else's at the start under their avatar and name, a run by the same author grouped), tool calls and
  * reasoning as lines that open, notes centred between them, a separator where the day changes (`dayMarkers`), and the
  * app's own components in the flow. Only what shows is made (a `VirtualList`, each row as tall as it draws), so a
  * history of thousands scrolls as easily as a screenful.
  *
  * `entries` is the app's: it adds to it and changes entries in place, and the transcript follows. While the newest
  * entry is in view the transcript stays at the bottom as entries arrive and a streaming message grows; scrolled up, it
  * stays where it is, and a "Jump to latest" control (with how many have arrived meanwhile) takes it back down, as
  * reaching the bottom does. Near the top it asks `loadOlder` for earlier entries, puts them before the rest without
  * moving what is on the screen, and shows a spinner while they come; an empty answer means there are no more.
  *
  * The transcript takes the focus: Up and Down move through the entries (scrolling through one taller than the view a
  * line at a time), Page Up and Page Down scroll a page, Home and End go to either end, and Enter opens or closes a tool
  * call or reasoning, or goes into the app's component (Right and Left open and close, as a tree's do). A new message
  * from somebody else is read out politely by a screen reader once it is whole.
  *
  * A message's words can be selected (`Component.selectable`, where the platform selects text): a drag selects them,
  * across lines and on across messages, and Ctrl or Command+C copies them, while a click still puts the cursor on the
  * message it lands on. With nothing selected, Ctrl or Command+C copies the message under the cursor; Ctrl or
  * Command+A selects every word, as Select all does. The context menu (the secondary button, the menu key, a long
  * press, OK held) offers what `menu` says for the entry, by default on a message Copy (the words selected), Copy
  * message and Select all (`menuFor`); over an entry it offers nothing for, and over a link, the browser's own shows.
  * A message copied whole goes on the clipboard as clean HTML and as plain prose (`MessageAction.copy`), so it pastes
  * into an email as its words rather than its Markdown; words selected on the web copy the same way
  * (`Component.copyAs`).
  *
  * Beside a message, a small row of buttons offers what `messageActions` says for it, by default Copy message where
  * there is a clipboard (`actionsFor`): beside the one the pointer rests on, else, with the focus on the transcript, the
  * one under its cursor (where a tap puts it). There is one row of them, drawn over the rows (`MessageActions`), so
  * nothing moves as it comes and goes. The forward arrow on a message goes onto them (Tab too, in the page's order,
  * after what the rows hold); an arrow out of them, Escape or Back, back to the message.
  *
  * `render` makes a message's words from its text and the widest they may be: Markdown by default
  * (`MessageMarkdown.render`), as narrow as a short reply, or plain text (`MessageText.plain`).
  *
  * {{{
  *   val entries = Var(Vector.empty[ChatEntry])
  *   val transcript = new Transcript(entries, loadOlder = Some(() => history.before(entries().headOption)))
  *   transcript.size.width := panel.size.width()
  *   transcript.size.height := panel.size.height()
  *   entries @= entries() :+ ChatEntry.message("m1", ada, self = false, "Hello")
  * }}} */
class Transcript(val entries: Var[Vector[ChatEntry]],
                 val loadOlder: Option[() => Task[Vector[ChatEntry]]] = None,
                 val render: (Val[String], Val[Double]) => Component = MessageMarkdown.render) extends Container {
  /** Whether a separator names the day wherever it changes between entries. */
  val dayMarkers: Var[Boolean] = own(Var(true))
  /** How many lines of a tool call's `body` show before it is opened. */
  val bodyLines: Var[Int] = own(Var(3))
  /** Whether a new message from somebody else is read out by a screen reader. */
  val announces: Var[Boolean] = own(Var(true))
  /** The clock "5m" and a live elapsed time follow, moving on while this is on the screen. */
  val ticker: Ticker = Ticker.of(this, 1.0)
  /** Whether it is kept at the newest entry, and how many have arrived below while it is not. */
  val pin: BottomPin = new BottomPin()
  /** What a press on a message's avatar or author's name does (a click, a tap, or the author's entry in its context
    * menu): the app's handler, or with none their picture over the screen (`AuthorPressed.showPicture`), where they
    * have one. */
  val onAuthor: Var[Option[AuthorPressed => Unit]] = own(Var(None))
  /** What the context menu on an entry offers: `menuFor` unless the app says otherwise; nothing, no menu. */
  val menu: Var[ChatEntry => List[MenuItem]] = own(Var((e: ChatEntry) => menuFor(e)))
  /** What the small row of buttons beside a message offers (`MessageActions`): `actionsFor` unless the app says
    * otherwise (Retry, Quote, Edit as well); nothing, no buttons. Asked of messages only. */
  val messageActions: Var[ChatEntry => List[MessageAction]] = own(Var((e: ChatEntry) => actionsFor(e)))
  /** Whether room is kept at the start edge for the entries' rails (`ChatEntry.rail`), so the rows line up whether or
    * not theirs has one: on for a conversation of threads or topics, before it shows (turned on later, the rows draw a
    * little narrower, the one at the top of the view kept where it is). Off, the default, the rows are as they always
    * were, and a rail is drawn in the margin they keep anyway. Narrower than a phone's (`Transcript.narrow`) no room is
    * kept either way: the rails draw in that margin, and the rows keep all the width. */
  val rails: Var[Boolean] = own(Var(false))
  // the room kept for the rails, at the start edge (none on a phone: they draw in the side margin)
  private[chat] val gutter: Val[Double] = own(Val(Transcript.gutter(size.width(), rails())))
  /** Whether earlier entries are being fetched. */
  val loading: Var[Boolean] = own(Var(false))
  /** Whether `loadOlder` has said there is nothing earlier. */
  val exhausted: Var[Boolean] = own(Var(loadOlder.isEmpty))
  /** Who is at work, each a row at the end, after the newest entry (`WorkingView`, as `ChatView.workingInline` has
    * it): their avatar and name, for how long, what they are doing (cut to `workingLines` lines until opened) and a
    * control to cancel it where it can be. Empty by default. They are not entries: kept at the bottom while it is
    * pinned, as entries are, but never counted as new, read out as they arrive, offered a menu or copied. */
  val working: Var[Vector[Working]] = own(Var(Vector.empty))
  /** How many lines of what somebody at work is doing show before Show more. */
  val workingLines: Var[Int] = own(Var(3))

  // what the list shows: the entries with a separator for each day, then who is at work
  private val shown: Var[Vector[ChatEntry]] = own(Var(Vector.empty))
  // the entries with their separators, and who is at work, by their rows' keys
  private var dated: Vector[ChatEntry] = Vector.empty
  private var slots: Vector[WorkingSlot] = Vector.empty
  // (immutable maps: a Roku keeps one keyed by strings as an associative array, and has no mutable one)
  private var days: Map[String, ChatEntry.Marker] = Map.empty
  private var dayOfEntry: Map[String, Option[String]] = Map.empty
  // what is kept by entry, made once: the app's components, tool calls' details
  private var kept: Map[String, Component] = Map.empty
  // the entries that were first when earlier ones by the same author came in above them: they keep their header
  private var headed: Set[String] = Set.empty

  val list: VirtualList[ChatEntry] = new VirtualList[ChatEntry](shown, _.id, _ => "", Transcript.guess,
    measuredRows = true)(() => new ChatRow(this))

  /** The widest a message's bubble, a tool call or the app's component may be in a transcript this wide. */
  val widest: Val[Double] = own(Val(Transcript.widest(list.size.width() - gutter())))

  private[chat] val jump: JumpButton = new JumpButton(pin.unseen, () => toLatest())
  jump.visible := !pin.pinned() && entries().nonEmpty
  // in the bottom end corner, clear of the middle where the newest entry's buttons are (and gone once at the bottom)
  jump.position.left := ListRow.along(size.width() - JumpButton.margin - JumpButton.diameter, JumpButton.diameter,
    size.width())
  jump.position.top := size.height() - JumpButton.diameter - JumpButton.margin

  private val spinner: Spinner = new Spinner(18.0, () => Theme.accent())
  spinner.visible := loading()
  spinner.position.left := (size.width() - 18.0) / 2.0
  spinner.position.top @= 8.0
  spinner.semantics @= Semantics("status", label = ChatWords.loadingOlder)

  list.size.width := size.width()
  list.size.height := size.height()
  list.estimate = (e: ChatEntry) =>
    if (Grouping.isWork(e)) Transcript.workGuess else Transcript.estimate(e, list.size.width() - gutter(), bodyLines())
  list.semantics @= Semantics("list", label = ChatWords.conversation)
  size.width @= 400.0
  size.height @= 400.0
  children ++= List(list, spinner, jump)

  // ---- what shows ----

  /** Whether the entry at `index` of the list starts a group (see `Grouping.leads`), with the avatar and the name. The
    * entry that was the first there was keeps them when earlier ones by the same author come in above it, so nothing
    * that shows changes or moves as history arrives. */
  def leads(index: Int, entry: ChatEntry): Boolean = headed.contains(entry.id) ||
    Grouping.leads(if (index > 0 && index <= list.count) Some(list(index - 1)) else None, entry)

  /** How an entry is read out: a message by its author and words, a tool call by what it does, its body and how it
    * stands; then its rail's `railLabel`, where it has one. */
  def said(entry: ChatEntry): String = {
    val label = entry.railLabel()
    if (label.isEmpty) saidOf(entry) else saidOf(entry) + ", " + label.get
  }

  private def saidOf(entry: ChatEntry): String = entry match {
    case w: ChatEntry.Marker if Grouping.isWork(w) => slotOf(w).map { s =>
      val work = s.working()
      work.who.name + ", " + ChatWords.busy + ", " + work.what
    }.getOrElse("")
    case m: ChatEntry.Message => m.author.name + ": " + m.text()
    case a: ChatEntry.Activity => a.summary() + (if (a.body().isEmpty) "" else ": " + a.body()) + ", " +
      ActivityView.said(a.status())
    case t: ChatEntry.Thinking => t.done().map(d => ChatWords.thoughtFor(ChatTime.span(d))).getOrElse(ChatWords.thinking)
    case m: ChatEntry.Marker => m.text()
    case _: ChatEntry.Custom => ""
  }

  /** The view for `entry` in `row`, which is taken apart again when the row moves on. */
  private[chat] def viewFor(entry: ChatEntry, leading: Boolean, row: ChatRow): Component = entry match {
    case m: ChatEntry.Message => new MessageView(m, leading, row.inner, ticker.now, render, !dayMarkers(),
      () => pressAuthor(AuthorPressed(m.author, Some(m.id))), AuthorPressed.answers(m.author, onAuthor()))
    case a: ChatEntry.Activity =>
      new ActivityView(a, row.inner, () => a.details.map(make => keep(a.id, make)), render, bodyLines())
    case t: ChatEntry.Thinking => new ThinkingView(t, row.inner, ticker.now)
    case w: ChatEntry.Marker if Grouping.isWork(w) && slotOf(w).nonEmpty =>
      val s = slotOf(w).get
      val view = new WorkingView(s, row.inner, ticker.now, workingLines(),
        () => pressAuthor(AuthorPressed(s.working().who, None)), AuthorPressed.answers(s.working().who, onAuthor()))
      view.exitFocus(Key.ArrowUp, list)
      view.exitFocus(Key.ArrowDown, list)
      view
    case m: ChatEntry.Marker => new MarkerView(m, row.inner)
    case c: ChatEntry.Custom => new CustomView(made(c), row.inner, list)
  }

  private def made(c: ChatEntry.Custom): Component = keep(c.id, c.make)

  // what was made for entry `id`, made by `make` the first time
  private def keep(id: String, make: () => Component): Component = kept.get(id) match {
    case Some(c) => c
    case None =>
      val c = make()
      kept = kept.updated(id, c)
      c
  }

  private def dayOf(e: ChatEntry): Option[String] = dayOfEntry.get(e.id) match {
    case Some(day) => day
    case None =>
      val day = e.time.map(ChatTime.dayOf)
      dayOfEntry = dayOfEntry.updated(e.id, day)
      day
  }

  private def separator(day: String): ChatEntry = days.get(day) match {
    case Some(m) => m
    case None =>
      val m = new ChatEntry.Marker(Grouping.dayKey(day))
      m.text := ChatTime.dayName(day, ticker.now())
      days = days.updated(day, m)
      m
  }

  private var lastIds: Vector[String] = Vector.empty

  private def reshow(): Unit = {
    val all = entries()
    dated = if (dayMarkers()) Grouping.withDays(all, dayOf, separator) else all
    showAll()
    val ids = all.map(_.id)
    // what arrived after the newest there was is read out; what came before it (earlier history) is not
    if (announces() && lastIds.nonEmpty) {
      val newest = BottomPin.lastIndex(ids, lastIds.last)
      if (newest >= 0) all.drop(newest + 1).foreach(announce)
    }
    lastIds = ids
    pin.entries(ids)
    // what is kept for entries that have gone goes with them
    if (kept.nonEmpty || headed.nonEmpty || dayOfEntry.size > ids.length) {
      val here = ids.toSet
      val gone = kept.filter { case (id, _) => !here.contains(id) }
      if (gone.nonEmpty) {
        gone.values.foreach(_.dispose())
        kept = kept.filter { case (id, _) => here.contains(id) }
      }
      if (dayOfEntry.size > ids.length) dayOfEntry = dayOfEntry.filter { case (id, _) => here.contains(id) }
      if (headed.nonEmpty) headed = headed.filter(here.contains)
    }
    // and a day no entry is on any longer loses its separator, which follows the clock
    if (days.nonEmpty) {
      val on = all.flatMap(e => dayOf(e).toList).toSet
      val gone = days.filter { case (day, _) => !on.contains(day) }
      if (gone.nonEmpty) {
        gone.values.foreach { m =>
          Component.unbind(m.text)
          Component.unbind(m.rail)
        }
        days = days.filter { case (day, _) => on.contains(day) }
      }
    }
  }

  private def showAll(): Unit = {
    shown @= dated ++ slots.map(_.entry)
    bridge()
  }

  private def slotOf(entry: ChatEntry): Option[WorkingSlot] = slots.find(_.entry eq entry)

  // who is at work, each kept by id (what they are doing changed in place), the rows shown again where who it is changed
  private def atWork(): Unit = {
    val now = working()
    val was = slots
    slots = now.map { w =>
      was.find(_.working().id == w.id) match {
        case Some(s) =>
          if (s.working() != w) s.working @= w
          s
        case None => new WorkingSlot(w)
      }
    }
    if (slots.map(_.entry.id) != was.map(_.entry.id)) showAll()
  }

  // a day's separator between two entries of one thread carries its rail on, so that the line runs through it unbroken
  private def bridge(): Unit = if (days.nonEmpty) {
    val s = shown()
    var i = 1
    while (i < s.length - 1) {
      val e = s(i)
      if (Grouping.isDay(e)) {
        val before = s(i - 1)
        val after = s(i + 1)
        e.rail := {
          val r = before.rail()
          if (r.nonEmpty && r == after.rail()) r else None
        }
      }
      i += 1
    }
  }

  private def announce(e: ChatEntry): Unit = e match {
    case m: ChatEntry.Message if !m.self =>
      if (!m.streaming()) Platform.announce(said(m))
      // once, when it is whole: a reaction kept on it until the transcript went held every message ever streamed
      else m.streaming.once(_ => Platform.announce(said(m)), (on: Boolean) => !on)
    case _ => ()
  }

  /** `pressed` answered: by the app's `onAuthor`, else their picture. */
  def pressAuthor(pressed: AuthorPressed): Unit = onAuthor().getOrElse(AuthorPressed.showPicture)(pressed)

  // the entry for a message's author in its menu, where a press on them does anything: the keyboard's and a remote's
  // way to them, the transcript being one stop for the focus
  private def authorItem(m: ChatEntry.Message): List[MenuItem] =
    if (m.self || !AuthorPressed.answers(m.author, onAuthor())) Nil
    else List(MenuItem.separator, MenuItem(ChatWords.about(m.author.name),
      () => pressAuthor(AuthorPressed(m.author, Some(m.id))), MaterialIcon.Person))

  // ---- copying ----

  /** What a context menu on `entry` offers by default: on a message, where there is a clipboard, Copy (the words
    * selected, where the platform selects them: only while some are, as the Copy key copies them), Copy message (its
    * words, as `MessageAction.copy` puts them) and Select all (the words of the entries drawn), and About the author
    * (`onAuthor`) where a press on them does anything; nothing on anything else. */
  def menuFor(entry: ChatEntry): List[MenuItem] = entry match {
    case m: ChatEntry.Message if Clipboard.copies =>
      val whole = MenuItem(ChatWords.copyMessage, () => MessageAction.copy(m))
      if (!Platform.selectsText()) whole :: authorItem(m)
      else {
        val selected = Platform.selectedText()
        // as the Copy key copies them: clean HTML and prose where they lie in Markdown
        val rich = Clipboard.selection()
        val copy = () => rich match {
          case Some(r) => Clipboard.copyRich(r._1, r._2)
          case None => Clipboard.copy(selected)
        }
        List(MenuItem(ChatWords.copy, copy, enabled = selected.nonEmpty), whole,
          MenuItem(ChatWords.selectAll, () => list.selectText())) ++ authorItem(m)
      }
    case m: ChatEntry.Message => authorItem(m).drop(1)
    case _ => Nil
  }

  list.menu.attach { p =>
    val items = if (Grouping.isWork(p.item)) Nil else menu()(p.item)
    if (items.nonEmpty) {
      if (Platform.remoteNavigation()) Menu.show(items) else Menu.at(p.x, p.y, items)
    }
  }

  // ---- a message's actions ----

  /** What the buttons beside a message offer by default: Copy message (`MessageAction.copyText`) where there is a
    * clipboard; nothing elsewhere, and on anything but a message. An app can add Copy as Markdown
    * (`MessageAction.copyMarkdownSource`), the text as it was written. */
  def actionsFor(entry: ChatEntry): List[MessageAction] = entry match {
    case m: ChatEntry.Message if Clipboard.copies => List(MessageAction.copyText(m))
    case _ => Nil
  }

  // the message under the pointer, and where the pointer is down the list while it is over it (a scroll under a
  // still pointer brings another under it)
  private val hovered: Var[Option[String]] = own(Var(None))
  private var pointerAt: Option[Double] = None
  private var bar: Option[MessageActions] = None
  // whether the cursor was last moved, or the focus brought here or onto the buttons, by a key (or a tap, where there
  // is no pointer to rest): only then do the buttons stay by the message once the pointer has gone, a click leaving
  // them to the pointer
  private var keyed: Boolean = false
  private def byKey(): Unit = keyed = !Pointer.recent

  /** The buttons shown beside a message, made the first time there are any to show. */
  def actions: Option[MessageActions] = bar

  private def messageOf(key: String): Option[ChatEntry.Message] = Transcript.message(list.itemAt(list.indexOf(key)))

  private def hoverAt(): Unit = {
    val next = pointerAt.flatMap { y =>
      val down = y - list.absoluteTop + list.offset()
      if (list.count == 0 || down < 0.0 || down >= list.total) None
      else Transcript.message(list.itemAt(list.indexAt(down))).map(_.id)
    }
    if (next != hovered()) hovered @= next
  }

  // the pointer off the rows, and off the buttons too a moment later (it goes from one to the other across the gap)
  private def unhover(): Unit = Platform.after(0.15, () =>
    if (pointerAt.isEmpty && !bar.exists(_.event.pointer.over()) && hovered().nonEmpty) hovered @= None)

  /** Which message the buttons are beside: the one the pointer is over; else, where the keys (or a tap) brought the
    * focus, the one they are for while it is on them, or the one under the transcript's cursor while it is on that. */
  private def retarget(): Unit = {
    val wanted = hovered().flatMap(messageOf).orElse {
      if (!keyed && Platform.pointerSupport()) None
      else if (bar.exists(_.holdsFocus)) bar.flatMap(_.message())
      else if (list.focused()) Transcript.message(list.currentItem)
      else None
    }.filter(m => list.indexOf(m.id) >= 0)
    val offered = wanted.map(m => messageActions()(m)).getOrElse(Nil)
    if (offered.nonEmpty && bar.isEmpty) {
      val made = new MessageActions(list, () => {
        if (bar.exists(_.holdsFocus)) byKey()
        retarget()
      }, () => unhover())
      // over the rows, under the spinner and "Jump to latest"
      children @= children().flatMap(c => if (c eq list) List(c, made) else List(c))
      bar = Some(made)
    }
    bar.foreach(_.show(if (offered.isEmpty) None else wanted, offered))
  }

  private[chat] def rowMoved(row: ChatRow): Unit = bar.foreach(_.rowMoved(row))

  // a forward arrow on a message: onto its buttons
  private def toActions(entry: ChatEntry): Option[() => Unit] = entry match {
    case m: ChatEntry.Message if messageActions()(m).nonEmpty => Some(() => {
      keyed = true
      retarget()
      bar.filter(_.showing()).flatMap(_.actionButtons.headOption).foreach(_.focus())
    })
    case _ => None
  }

  // Ctrl or Command+C with no words selected: the message under the cursor, whole; Ctrl or Command+A: every word, as
  // Select all
  private def copyKey(e: KeyEvent): Option[() => Unit] =
    if (!(if (Platform.commandKey()) e.meta else e.ctrl) || e.alt || e.shift) None
    else if (e.name.toLowerCase == "a" && Platform.selectsText()) Some(() => list.selectText())
    else if (e.name.toLowerCase != "c" || !Clipboard.copies || Platform.selectedText().nonEmpty) None
    else list.currentItem match {
      case Some(m: ChatEntry.Message) => Some(() => MessageAction.copy(m))
      case _ => None
    }

  // ---- the bottom, and what is earlier ----

  private def furthest: Double = math.max(0.0, list.total - list.size.height())

  /** Scrolled to the newest entry, and kept there. */
  def toLatest(): Unit = {
    pin.pin()
    list.stopFling()
    list.scrollTop(list.total)
    list.current @= (if (list.count > 0) Some(list.keyAt(list.count - 1)) else None)
  }

  private def older(): Unit = loadOlder.foreach { fetch =>
    if (!loading() && !exhausted()) {
      loading @= true
      fetch().map { earlier =>
        loading @= false
        if (earlier.isEmpty) exhausted @= true
        else prepend(earlier)
      }.start()
    }
  }

  /** `earlier` put before the entries, what shows kept where it is (unless it is kept at the bottom). */
  private def prepend(earlier: Vector[ChatEntry]): Unit = {
    val anchor = if (pin.pinned() || list.count == 0) None else {
      var i = list.indexAt(list.offset())
      while (i < list.count - 1 && Grouping.isDay(list(i))) i += 1
      Some((list.keyAt(i), list.top(i) - list.offset()))
    }
    // the entry that was first keeps its header, though it now continues a group
    entries().headOption.foreach(first => if (!Grouping.leads(earlier.lastOption, first)) headed = headed + first.id)
    entries @= earlier ++ entries()
    anchor.foreach { case (key, dy) =>
      val i = list.indexOf(key)
      if (i >= 0) list.scrollTop(list.top(i) - dy)
    }
    // still near the top (a short history): more
    Platform.later(() => nearTop())
  }

  private def nearTop(): Unit = if (list.offset() < math.max(Transcript.loadAhead, list.size.height())) older()

  private def moved(): Unit = {
    pin.scrolled(list.offset(), furthest)
    nearTop()
    if (pointerAt.nonEmpty) hoverAt()
  }

  // ---- keys ----

  private def indexOfCurrent: Int = list.current().map(list.indexOf).getOrElse(-1)

  // whether row `i` shows, at least in part
  private def inView(i: Int): Boolean = i >= 0 && i < list.count && list.top(i) + list.heightAt(i) > list.offset() &&
    list.top(i) < list.offset() + list.size.height()

  private def cursorTo(i: Int): Unit = if (i >= 0 && i < list.count) list.current @= Some(list.keyAt(i))

  // row `i` brought into view: its top where it is taller than the view and it is gone down to, else all of it
  private def bring(i: Int, down: Boolean): Unit = {
    val h = list.size.height()
    if (list.heightAt(i) > h) list.scrollTop(if (down) list.top(i) else list.top(i) + list.heightAt(i) - h)
    else list.reveal(i)
  }

  private def step(down: Boolean): Unit = if (list.count > 0) {
    val h = list.size.height()
    val at = indexOfCurrent
    if (!inView(at)) {
      // the cursor was elsewhere: it starts from what shows
      val first = list.indexAt(list.offset())
      val last = list.indexAt(list.offset() + h - 1.0)
      cursorTo(if (down) first else last)
    } else if (down) {
      val bottom = list.top(at) + list.heightAt(at)
      if (bottom > list.offset() + h + 1.0) list.scrollBy(math.min(Transcript.lineStep, bottom - list.offset() - h))
      else if (at + 1 < list.count) {
        cursorTo(at + 1)
        bring(at + 1, down = true)
      } else list.scrollTop(list.total)
    } else {
      val top = list.top(at)
      if (top < list.offset() - 1.0) list.scrollBy(-math.min(Transcript.lineStep, list.offset() - top))
      else if (at > 0) {
        cursorTo(at - 1)
        bring(at - 1, down = false)
      }
    }
  }

  private def page(down: Boolean): Unit = {
    val h = list.size.height()
    list.scrollBy(if (down) h - Transcript.lineStep else Transcript.lineStep - h)
    cursorTo(if (down) list.indexAt(list.offset() + h - 1.0) else list.indexAt(list.offset()))
  }

  /** Enter on an entry: a tool call or reasoning opened or closed, the app's component gone into. */
  private def act(entry: ChatEntry): Option[() => Unit] = entry match {
    case w: ChatEntry.Marker if Grouping.isWork(w) => workView(w).flatMap(v => Focus.candidates(v).headOption)
      .map(c => () => c.focus())
    case a: ChatEntry.Activity if a.opens => Some(() => a.expanded @= !a.expanded())
    case t: ChatEntry.Thinking => Some(() => t.expanded @= !t.expanded())
    case c: ChatEntry.Custom => Some(() => Focus.candidates(made(c)).headOption.foreach(_.focus()))
    case _ => None
  }

  // the view drawn for somebody at work, where its row is drawn
  private def workView(w: ChatEntry): Option[WorkingView] =
    list.rowAt(list.indexOf(w.id)).flatMap {
      case r: ChatRow => r.shown match {
        case Some(v: WorkingView) => Some(v)
        case _ => None
      }
      case _ => None
    }

  private def open(entry: ChatEntry, on: Boolean): Option[() => Unit] = entry match {
    case w: ChatEntry.Marker if Grouping.isWork(w) => slotOf(w).filter(s => s.expanded() != on &&
      (s.expanded() || workView(w).exists(_.words.elided()))).map(s => () => s.expanded @= on)
    case a: ChatEntry.Activity if a.opens && a.expanded() != on => Some(() => a.expanded @= on)
    case t: ChatEntry.Thinking if t.expanded() != on => Some(() => t.expanded @= on)
    case _ => None
  }

  list.keys = (e: KeyEvent) => if (list.count == 0) None else if (e.ctrl || e.meta || e.alt) copyKey(e) else {
    val forward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
    val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
    val at = indexOfCurrent
    val entry = if (inView(at)) list.currentItem else None
    // at either end, an arrow on out leaves the transcript, as the focus layer says
    val atEnd = at == list.count - 1 && list.offset() >= furthest - 1.0
    val atStart = at == 0 && list.offset() <= 0.0
    e.key match {
      case Key.ArrowDown if !e.shift && !atEnd => Some(() => step(down = true))
      case Key.ArrowUp if !e.shift && !atStart => Some(() => step(down = false))
      case Key.PageDown => Some(() => page(down = true))
      case Key.PageUp => Some(() => page(down = false))
      case Key.Other if e.name == "End" => Some(() => toLatest())
      case Key.Other if e.name == "Home" => Some(() => {
        list.scrollTop(0.0)
        cursorTo(0)
      })
      case Key.Enter => entry.flatMap(act)
      case Key.Space => entry.flatMap(act)
      case k if k == forward => entry.flatMap(e => open(e, on = true).orElse(toActions(e)))
      case k if k == back => entry.flatMap(open(_, on = false))
      case _ => None
    }
  }
  // a click opens what it lands on itself (a tool call's line) and puts the cursor on its entry where it is, leaving the
  // scroll be (and the words a drag it ended selected)
  list.clicks = (i: Int, _: Double) => {
    cursorTo(i)
    true
  }

  // ---- following ----

  follow(entries, (_: Vector[ChatEntry]) => reshow())
  follow(working, (_: Vector[Working]) => atWork())
  dayMarkers.attach { _ =>
    reshow()
    list.redecorateAll()
  }
  // (heard before the list lays its rows out for the move, which can draw some at new heights: whether the move
  // reached the bottom is judged against the rows it was made against)
  list.offset.attach(_ => moved(), Priority.Low)
  // kept at the bottom as rows arrive and grow, and as the view changes size, while it is pinned
  list.followsEnd := pin.pinned()
  // a message's buttons follow the pointer where there is one to rest, and the cursor while the transcript has the focus
  if (Platform.pointerSupport()) {
    list.event.pointer.move.attach { e =>
      pointerAt = Some(e.y)
      hoverAt()
    }
    list.event.pointer.leave.attach { _ =>
      pointerAt = None
      unhover()
    }
  }
  hovered.attach(_ => retarget())
  list.focused.attach { on =>
    if (on) byKey()
    retarget()
  }
  list.current.attach { _ =>
    byKey()
    retarget()
  }
  shown.attach(_ => retarget())
  messageActions.attach(_ => retarget())
  onDispose(() => {
    kept.values.foreach(_.dispose())
    kept = Map.empty
  })
  reshow()
  list.scrollTop(list.total)
  Platform.later(() => nearTop())

  override def focus(): Unit = list.focus()
  override def kind: String = "Transcript"
}

object Transcript {
  /** A row's height before it has drawn itself. */
  val guess: Double = 64.0
  /** About how tall a row for somebody at work (`working`) draws, with a line of what they are doing. */
  val workGuess: Double = 68.0
  /** Space at either side of the rows. */
  val side: Double = 12.0
  /** An avatar's size, and the room it and the gap after it take before everybody else's bubbles. */
  val avatar: Double = 32.0
  val lead: Double = 40.0
  /** An avatar's size on a phone (`narrow`), where it sits small at the start of the name's line, the bubble under
    * both, and the gap between it and the name. */
  val smallAvatar: Double = 20.0
  val smallGap: Double = 6.0
  /** Space inside a bubble: across and down. */
  val padX: Double = 12.0
  val padY: Double = 8.0
  /** Space above a group, and between the messages in one. */
  val groupGap: Double = 10.0
  val lineGap: Double = 2.0
  /** An entry's rail (`ChatEntry.rail`): how wide, how far in from the start edge, and the room kept for it before the
    * rows where the transcript keeps some (`rails`). */
  val railWidth: Double = 3.0
  val railInset: Double = 4.0
  val railGutter: Double = 6.0
  /** How far an arrow scrolls through an entry taller than the view. */
  val lineStep: Double = 48.0
  /** How near the top earlier entries are asked for. */
  val loadAhead: Double = 600.0
  /** Below this width, a phone's: everything in the flow takes all of it but the side margins, the avatar goes small
    * beside the name, and no room is kept for the rails. */
  val narrow: Double = 600.0

  /** Where everybody else's bubbles, tool calls, reasoning and the app's components start along a row `width` wide:
    * past the avatar's room, or on a phone (`narrow`) just inside the side margin. */
  def indent(width: Double): Double = if (width < narrow) side else side + lead

  /** The widest a bubble may be in a transcript `width` wide: three quarters of it, or on a phone all but the side
    * margins. */
  def widest(width: Double): Double =
    if (width < narrow) math.max(80.0, width - side * 2.0) else width * 0.75

  /** The room kept before the rows for the rails in a transcript `width` wide that keeps some (`rails`): none where
    * the rows would then be a phone's, the rails drawing in the side margin. */
  def gutter(width: Double, rails: Boolean): Double =
    if (rails && width - railGutter >= narrow) railGutter else 0.0

  // a leading message's header (its name and time, and on a phone the small avatar), near enough
  private def headerGuess(width: Double): Double = if (width < narrow) smallAvatar + 4.0 else 22.0

  /** About how tall `entry` draws in a transcript `width` wide, before it has (`VirtualList.estimate`): from the length
    * of its lines, its code, tables and pictures, rounded up rather than down, so that a transcript opened at its newest
    * makes about the rows that show rather than a dozen more it then puts away. */
  def estimate(entry: ChatEntry, width: Double, bodyLines: Int): Double = entry match {
    case m: ChatEntry.Message => groupGap + headerGuess(width) + padY * 2.0 +
      estimateText(m.text(), widest(width) - padX * 2.0) + (if (m.meta().nonEmpty || m.streaming()) 18.0 else 0.0)
    case a: ChatEntry.Activity => 48.0 + (if (a.body().isEmpty) 0.0
      else math.min(estimateText(a.body(), widest(width) - padX * 2.0 - 40.0), 20.0 * math.max(1, bodyLines)))
    case _: ChatEntry.Thinking => 44.0
    case _: ChatEntry.Marker => 42.0
    case _: ChatEntry.Custom => guess
  }

  // Markdown's height in a bubble's words `across` wide, near enough: a line of words is about 7 pixels a letter and 20
  // tall, a line of code 17, and a code block, a table's rows and a picture take what they do around them
  private def estimateText(text: String, across: Double): Double = {
    val perLine = math.max(8.0, across / 7.0)
    var height = 0.0
    var code = false
    var at = 0
    while (at <= text.length) {
      var end = text.indexOf('\n', at)
      if (end < 0) end = text.length
      val line = text.substring(at, end).trim
      if (line.startsWith("```")) {
        if (!code) height += 46.0
        code = !code
      }
      else if (code) height += 17.0
      else if (line.isEmpty) height += 9.0
      else if (line.startsWith("|")) height += 31.0
      else if (line.startsWith("![")) height += 220.0
      else height += 20.0 * math.ceil(line.length / perLine)
      at = end + 1
    }
    height
  }

  /** `entry`, where it is a message. */
  def message(entry: Option[ChatEntry]): Option[ChatEntry.Message] = entry match {
    case Some(m: ChatEntry.Message) => Some(m)
    case _ => None
  }
}
