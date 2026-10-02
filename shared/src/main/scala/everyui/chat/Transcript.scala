package everyui.chat

import everyui.async.Task
import everyui.clipboard.Clipboard
import everyui.component.{Button, ButtonStyle, Component, Container}
import everyui.component.types.Semantics
import everyui.event.{Key, KeyEvent}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.Spinner
import everyui.list.VirtualList
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
  /** The clock "5m" and a live elapsed time follow. */
  val ticker: Ticker = new Ticker(1.0)
  /** Whether it is kept at the newest entry, and how many have arrived below while it is not. */
  val pin: BottomPin = new BottomPin()
  /** What a press on a message's avatar or author's name does (a click, a tap, or the author's entry in its context
    * menu): the app's handler, or with none their picture over the screen (`AuthorPressed.showPicture`), where they
    * have one. */
  val onAuthor: Var[Option[AuthorPressed => Unit]] = own(Var(None))
  /** What the context menu on an entry offers: `menuFor` unless the app says otherwise; nothing, no menu. */
  val menu: Var[ChatEntry => List[MenuItem]] = own(Var((e: ChatEntry) => menuFor(e)))
  /** Whether earlier entries are being fetched. */
  val loading: Var[Boolean] = own(Var(false))
  /** Whether `loadOlder` has said there is nothing earlier. */
  val exhausted: Var[Boolean] = own(Var(loadOlder.isEmpty))

  // what the list shows: the entries with a separator for each day
  private val shown: Var[Vector[ChatEntry]] = own(Var(Vector.empty))
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
  val widest: Val[Double] = own(Val(Transcript.widest(list.size.width())))

  private val jump: Button = Button.bound(
    if (pin.unseen() > 0) ChatWords.newCount(pin.unseen()) else ChatWords.jumpToLatest,
    () => toLatest(), ButtonStyle.Filled, Some(MaterialIcon.ArrowDownward), 12.5)
  jump.visible := !pin.pinned() && entries().nonEmpty
  jump.shadow @= Some(everyui.Shadow(0.0, 2.0, 8.0, everyui.Color.Black.withAlpha(0.3)))
  jump.position.left := (size.width() - jump.size.width()) / 2.0
  jump.position.top := size.height() - jump.size.height() - 12.0

  private val spinner: Spinner = new Spinner(18.0, () => Theme.accent())
  spinner.visible := loading()
  spinner.position.left := (size.width() - 18.0) / 2.0
  spinner.position.top @= 8.0
  spinner.semantics @= Semantics("status", label = ChatWords.loadingOlder)

  list.size.width := size.width()
  list.size.height := size.height()
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
    * stands. */
  def said(entry: ChatEntry): String = entry match {
    case m: ChatEntry.Message => m.author.name + ": " + m.text()
    case a: ChatEntry.Activity => a.summary() + (if (a.body().isEmpty) "" else ": " + a.body()) + ", " +
      ActivityView.said(a.status())
    case t: ChatEntry.Thinking => t.done().map(d => ChatWords.thoughtFor(ChatTime.span(d))).getOrElse(ChatWords.thinking)
    case m: ChatEntry.Marker => m.text()
    case _: ChatEntry.Custom => ""
  }

  /** The view for `entry` in `row`, which is taken apart again when the row moves on. */
  private[chat] def viewFor(entry: ChatEntry, leading: Boolean, row: ChatRow): Component = entry match {
    case m: ChatEntry.Message => new MessageView(m, leading, row.size.width(), ticker.now, render, !dayMarkers(),
      () => pressAuthor(AuthorPressed(m.author, Some(m.id))), AuthorPressed.answers(m.author, onAuthor()))
    case a: ChatEntry.Activity =>
      new ActivityView(a, row.size.width(), () => a.details.map(make => keep(a.id, make)), render, bodyLines())
    case t: ChatEntry.Thinking => new ThinkingView(t, row.size.width(), ticker.now)
    case m: ChatEntry.Marker => new MarkerView(m, row.size.width())
    case c: ChatEntry.Custom => new CustomView(made(c), row.size.width(), list)
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
    shown @= (if (dayMarkers()) Grouping.withDays(all, dayOf, separator) else all)
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
        gone.values.foreach(m => Component.unbind(m.text))
        days = days.filter { case (day, _) => on.contains(day) }
      }
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
    * selected, where the platform selects them: only while some are), Copy message (its text, as it was written) and
    * Select all (the words of the entries drawn), and About the author (`onAuthor`) where a press on them does
    * anything; nothing on anything else. */
  def menuFor(entry: ChatEntry): List[MenuItem] = entry match {
    case m: ChatEntry.Message if Clipboard.copies =>
      val whole = MenuItem(ChatWords.copyMessage, () => Clipboard.copy(m.text()))
      if (!Platform.selectsText()) whole :: authorItem(m)
      else {
        val selected = Platform.selectedText()
        List(MenuItem(ChatWords.copy, () => Clipboard.copy(selected), enabled = selected.nonEmpty), whole,
          MenuItem(ChatWords.selectAll, () => list.selectText())) ++ authorItem(m)
      }
    case m: ChatEntry.Message => authorItem(m).drop(1)
    case _ => Nil
  }

  list.menu.attach { p =>
    val items = menu()(p.item)
    if (items.nonEmpty) {
      if (Platform.remoteNavigation()) Menu.show(items) else Menu.at(p.x, p.y, items)
    }
  }

  // Ctrl or Command+C with no words selected: the message under the cursor, whole; Ctrl or Command+A: every word, as
  // Select all
  private def copyKey(e: KeyEvent): Option[() => Unit] =
    if (!(if (Platform.commandKey()) e.meta else e.ctrl) || e.alt || e.shift) None
    else if (e.name.toLowerCase == "a" && Platform.selectsText()) Some(() => list.selectText())
    else if (e.name.toLowerCase != "c" || !Clipboard.copies || Platform.selectedText().nonEmpty) None
    else list.currentItem match {
      case Some(m: ChatEntry.Message) => Some(() => Clipboard.copy(m.text()))
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
    case a: ChatEntry.Activity if a.opens => Some(() => a.expanded @= !a.expanded())
    case t: ChatEntry.Thinking => Some(() => t.expanded @= !t.expanded())
    case c: ChatEntry.Custom => Some(() => Focus.candidates(made(c)).headOption.foreach(_.focus()))
    case _ => None
  }

  private def open(entry: ChatEntry, on: Boolean): Option[() => Unit] = entry match {
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
      case k if k == forward => entry.flatMap(open(_, on = true))
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
  dayMarkers.attach { _ =>
    reshow()
    list.redecorateAll()
  }
  // (heard before the list lays its rows out for the move, which can draw some at new heights: whether the move
  // reached the bottom is judged against the rows it was made against)
  list.offset.attach(_ => moved(), Priority.Low)
  // kept at the bottom as rows arrive and grow, and as the view changes size, while it is pinned
  list.followsEnd := pin.pinned()
  onDispose(() => {
    ticker.stop()
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
  /** Space at either side of the rows. */
  val side: Double = 12.0
  /** An avatar's size, and the room it and the gap after it take before everybody else's bubbles. */
  val avatar: Double = 32.0
  val lead: Double = 40.0
  /** Space inside a bubble: across and down. */
  val padX: Double = 12.0
  val padY: Double = 8.0
  /** Space above a group, and between the messages in one. */
  val groupGap: Double = 10.0
  val lineGap: Double = 2.0
  /** How far an arrow scrolls through an entry taller than the view. */
  val lineStep: Double = 48.0
  /** How near the top earlier entries are asked for. */
  val loadAhead: Double = 600.0
  /** Below this width, a phone's: bubbles take nearly all of it. */
  val narrow: Double = 600.0

  /** The widest a bubble may be in a transcript `width` wide: three quarters of it, or on a phone all but the edges and
    * the avatar's room. */
  def widest(width: Double): Double =
    if (width < narrow) math.max(80.0, width - side * 2.0 - lead) else width * 0.75
}
