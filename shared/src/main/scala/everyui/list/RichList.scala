package everyui.list

import everyui.chat.{ChatTime, ChatWords, Ticker}
import everyui.component.{Component, Container}
import everyui.event.{Key, KeyEvent}
import everyui.i18n.{Direction, Words}
import everyui.kit.SearchField
import everyui.menu.{Menu, MenuItem}
import everyui.render.Platform
import reactify.*

/** The list down the side of every chat, mail and contacts app: items with an avatar or an icon, a title and a line
  * under it, and at the end how long ago, a count and a mark, in sections under headers that open and close. Only
  * the rows that show are made (a `VirtualList`), so thousands scroll as easily as ten.
  *
  * `sections` is the app's, and the list follows it; `lookOf` says how each item shows (`RichLook`). `collapsed` holds
  * the keys of the sections that are closed: the app's to keep and set, or the list's own. `selected` is the key of
  * the item that is the app's current one (the open conversation), drawn chosen; activating an item (Enter, a click or
  * a tap) chooses it and fires `activated`. The arrows move a cursor through headers and items; Enter or a click on a
  * header opens or closes it, as do Right and Left on it.
  *
  * Sections next to each other in the same `RichSection.group` go under that group's header, a level above theirs,
  * which says how many they hold between them and opens and closes them all as a section's does its items; the keys of
  * the groups closed are in `collapsedGroups`, apart from the sections', so neither can be taken for the other.
  * `groupMenu` gives a group's menu, asked for as a section's is.
  *
  * `menu` gives an item's context menu: asked for with the secondary button, a long press on a touch screen, the menu
  * key or a remote's options key, OK held, or the ⋮ at the row's end that shows with the pointer over it or the cursor
  * on it. `sectionMenu` gives a section's, asked for the same ways on its header, whose ⋮ shows with the pointer over
  * it or the cursor on it, and always where there is no pointer; a header with none only opens and closes. A section's
  * `action` is a small button in its header, always shown, that runs it without opening or closing the section: a click
  * or a tap on it, or Right (Left, right to left) from the open section's header onto it and Enter; Left takes the
  * cursor back to the header, and Up or Down on. A section's `empty` words show on a line of their own while it is
  * open and holds nothing, which the cursor passes over, a click does nothing to, and search never finds. An item's
  * `RichLook.action` (a star) is a small button at its row's end, before its time, shown with the pointer or the cursor
  * on the row or always where `actionShown`, reached and run as a section's is, and never choosing the item. With
  * `search`, a field over the list narrows it to the items `matches` finds for the words (`query`, which
  * the app can also set), under their sections, opened; an app filtering its own sections leaves it off. With
  * `reorders` on, items are dragged into another place, which `accepts` allows (within their own section, by default)
  * and `moved` hears: never into a group that is closed. Each item then shows a grip at its row's start (with the pointer
  * over it or the cursor on it, and always on a touch screen) that says it can be dragged.
  *
  * Items can expand in place (`expansion`): activating one that has a `RichExpansion` (a click or a tap on its title's
  * line, Enter, OK) opens it under its title, its whole text wrapped (as Markdown) and its actions as buttons under it,
  * rather than choosing it, and again folds it; Escape folds it too. `expanded` holds the keys of the items open, the
  * app's to keep and set, and with `expandsOne` (the default) opening one folds any other. The row is as tall as all of
  * it, kept in view as it grows (scrolled the least that shows it, or its top where it is taller than the list). Right
  * (Left, right to left) takes the cursor past the item's action onto its buttons in turn, Left back, and Enter runs the
  * one it is on; a click on the text or a button never folds it. Search finds what it always did (`matches`, the title
  * and the detail): an app that wants an item's whole text found says so in `matches`.
  *
  * An item whose `RichLook.live` is set is held somewhere else in the app (open in a tab, playing, connected): it is
  * marked apart from `selected`, with a faint accent tint and a thin accent bar at its start edge, while the item
  * chosen keeps its stronger tint, and one chosen and live has both that tint and the bar.
  *
  * `style` says how it is drawn: as a tree, by default, or (`RichStyle.Blocks`) as a sidebar's tinted blocks, a
  * group's title a caption over its sections and each section a rounded panel, its header a bar that opens and closes
  * it, its items under it; everything else (the cursor, the keys, the menus, search, dragging) is the same in both.
  *
  * {{{
  *   val chats = new RichList[Chat](sections, _.id, c => RichLook(c.title, Some(c.snippet), who = Some(c.persona),
  *     time = Some(c.updated), count = c.unread, unread = c.unread > 0, mark = if (c.pinned) Some(MaterialIcon.PushPin) else None),
  *     collapsed = app.collapsed, search = true)
  *   chats.menu @= (c => List(MenuItem("Rename…", () => rename(c)), MenuItem("Delete", () => delete(c), danger = true)))
  *   chats.activated.attach(c => open(c))
  *   chats.selected @= Some(current.id)
  *   memories.expansion @= (m => Some(RichExpansion(m.text, List(RichAction(MaterialIcon.PushPin, "Pin", () => pin(m)),
  *     RichAction(MaterialIcon.Delete, "Delete", () => delete(m), danger = true)))))
  * }}} */
class RichList[T](val sections: Var[Vector[RichSection[T]]], val keyOf: T => String, val lookOf: T => RichLook,
                  val collapsed: Var[Set[String]] = Var(Set.empty[String]), search: Boolean = false,
                  val collapsedGroups: Var[Set[String]] = Var(Set.empty[String])) extends Container {
  override def kind: String = "RichList"

  /** An item activated: Enter on it, a click, a tap. It is chosen (`selected`) first. */
  val activated: Channel[T] = Channel[T]
  /** The key of the item that is the app's current one, drawn chosen. */
  val selected: Var[Option[String]] = own(Var(None))
  /** An item's context menu, made afresh each time it opens: none (and no ⋮) where it is empty. */
  val menu: Var[T => List[MenuItem]] = own(Var((_: T) => Nil))
  /** A section's menu, on its header, made afresh each time it opens: none (and no ⋮) where it is empty. */
  val sectionMenu: Var[RichSection[T] => List[MenuItem]] = own(Var((_: RichSection[T]) => Nil))
  /** A group's menu, on its header, made afresh each time it opens: none (and no ⋮) where it is empty. */
  val groupMenu: Var[RichGroup => List[MenuItem]] = own(Var((_: RichGroup) => Nil))
  /** The words the list is narrowed by, where they are not empty. */
  val query: Var[String] = own(Var(""))
  /** Whether an item is one the words find: by default, one whose title or detail holds them, in any case. */
  val matches: Var[(T, String) => Boolean] = own(Var((t: T, words: String) => RichList.holds(lookOf(t), words)))
  /** The time the items' times are said against ("5m" ago), moved on every `RichList.tick` seconds while
    * the list is on the screen, and at once as it comes back (`Ticker`). */
  val now: Var[Long] = own(Var(everyui.util.Clock.real()))
  /** How an item's time is said, seen at a moment: by default how long ago (`ChatTime.ago`). */
  var timeText: (Long, Long) => String = (time: Long, at: Long) => ChatTime.ago(time, at)
  /** Whether items can be dragged into another place (never while the list is narrowed by words). */
  val reorders: Var[Boolean] = own(Var(false))
  /** Whether a drag may land where it would: within the section it came from, by default. */
  val accepts: Var[RichMove => Boolean] = own(Var((m: RichMove) => m.section == m.from))
  /** Items dragged into another place, where `accepts` let them. */
  val moved: Channel[RichMove] = Channel[RichMove]
  /** Whether its items can be dragged now: `reorders` on and the list not narrowed by words. Each item's row then has a
    * grip at its start (`RichRow`). */
  val dragsItems: Val[Boolean] = own(Val(reorders() && query().trim.isEmpty))
  /** How the sections are drawn: as a tree (`RichStyle.Tree`, the default) or as tinted blocks (`RichStyle.Blocks`). */
  val style: Var[RichStyle] = own(Var(RichStyle.Tree))
  /** What an item shows under its title once expanded, where it expands: none (the default) for every item, which are
    * then activated as they always were. Asked only of the items in view, and of those expanded. */
  val expansion: Var[T => Option[RichExpansion]] = own(Var((_: T) => None))
  /** The keys (`keyOf`) of the items expanded: the app's to keep and set. */
  val expanded: Var[Set[String]] = own(Var(Set.empty[String]))
  /** Whether expanding an item folds the one expanded before (the default), rather than leaving several open. */
  val expandsOne: Var[Boolean] = own(Var(true))

  private val lines: Var[Vector[RichLine[T]]] = own(Var(Vector.empty))
  /** Whether any section's or group's header has a menu: then every header's count sits short of the ⋮, in one line
    * down the list, and in a list with none each is where it always was. */
  val headersHaveMenus: Val[Boolean] = own(Val(sections().exists(s => sectionMenu()(s).nonEmpty ||
    s.group.exists(g => groupMenu()(g).nonEmpty))))
  /** Whether any section has an action (`RichSection.action`): then, in a tree, every header's count sits short of it,
    * in one line down the list. */
  val headersHaveActions: Val[Boolean] = own(Val(sections().exists(_.action.nonEmpty)))
  /** Whether the cursor is on its row's action rather than the row: Right (Left, right to left) from an open section's
    * header, or from an item, puts it there, where it has one, and Left back; Enter runs it. */
  private[list] val atAction: Var[Boolean] = own(Var(false))
  /** Which of an expanded item's buttons the cursor is on, -1 where it is on none: Right (Left, right to left) from the
    * item, past its action where it has one, and on through them; Left back; Enter runs it. */
  private[list] val atButton: Var[Int] = own(Var(-1))
  /** The item just expanded, whose text fades in as its row shows it. */
  private[list] var opening: Option[String] = None
  // where the pointer last went down on the rows (from the first's top), for the click it makes: one on an expanded
  // item's text or buttons rather than its title's line
  private var pressedAt: Option[Double] = None
  val list: VirtualList[RichLine[T]] = new VirtualList[RichLine[T]](lines, _.key,
    l => if (l.empty) "" else l.item.map(t => lookOf(t).title).getOrElse(l.groupHead.map(_.title).getOrElse(l.section.title)),
    RichRow.tallHeight, measuredRows = true)(() => new RichRow[T](this))
  /** The field over the list that narrows it, with `search`: a search box on one line (`SearchField`). */
  val field: Option[SearchField] = if (search) Some(new SearchField(Words.search)) else None

  /** Where each item's line is, by the item's key. */
  private var lineOf: Map[String, String] = Map.empty

  def expand(section: String): Unit = if (collapsed().contains(section)) collapsed @= collapsed() - section
  /** Section `section` closed; a cursor on one of its items goes up to its header. */
  def collapse(section: String): Unit = if (!collapsed().contains(section)) {
    if (list.currentItem.exists(l => l.section.key == section && l.item.nonEmpty))
      list.current @= Some(GroupedList.headerKey(section))
    collapsed @= collapsed() + section
  }
  def toggle(section: String): Unit = if (collapsed().contains(section)) expand(section) else collapse(section)

  def expandGroup(group: String): Unit = if (collapsedGroups().contains(group)) collapsedGroups @= collapsedGroups() - group
  /** Group `group` closed, and every section in it; a cursor on one of their lines goes up to its header. */
  def collapseGroup(group: String): Unit = if (!collapsedGroups().contains(group)) {
    if (list.currentItem.exists(l => l.groupHead.isEmpty && l.section.group.exists(_.key == group)))
      list.current @= Some(RichList.groupLineKey(group))
    collapsedGroups @= collapsedGroups() + group
  }
  def toggleGroup(group: String): Unit =
    if (collapsedGroups().contains(group)) expandGroup(group) else collapseGroup(group)

  /** Item `key` expanded, where it expands (`expansion`): alone, where `expandsOne`; its row kept in view as it grows. */
  def expandItem(key: String): Unit = if (!expanded().contains(key)) {
    opening = Some(key)
    expanded @= (if (expandsOne()) Set(key) else expanded() + key)
    keepInView(key, RichList.settleTries, -1.0)
  }
  /** Item `key` folded to its row again, kept in view. */
  def foldItem(key: String): Unit = if (expanded().contains(key)) {
    expanded @= expanded() - key
    keepInView(key, RichList.settleTries, -1.0)
  }
  def toggleItem(key: String): Unit = if (expanded().contains(key)) foldItem(key) else expandItem(key)

  /** Whether item `t` expands (`expansion`). */
  def expands(t: T): Boolean = expansion()(t).nonEmpty

  // item `key`'s row shown, once it is drawn at its new height: scrolled the least that shows it whole, or that shows
  // its top where it is taller than the list (the rows' heights are read after the turn, on the web in a batch, so it
  // waits for the list to have the row's height, and for it to stay so a turn)
  private def keepInView(key: String, tries: Int, was: Double): Unit = Platform.afterTurn(() => if (!takenApart) {
    val i = lineOf.get(key).map(list.indexOf).getOrElse(-1)
    val now = if (i < 0) -1.0 else list.rowAt(i).map(_.size.height()).getOrElse(list.heightAt(i))
    val drawn = now == was && now == list.heightAt(i)
    if (i >= 0 && !drawn && tries > 0) keepInView(key, tries - 1, now)
    else if (i >= 0) {
      val start = list.top(i)
      val end = start + list.heightAt(i)
      val view = list.size.height()
      if (start < list.offset()) list.scrollTop(start)
      else if (end > list.offset() + view) list.scrollTop(math.min(start, end - view))
    }
  })

  // a header's line opened, closed, or either: its group's, or its section's
  private def openLine(l: RichLine[T]): Unit = l.groupHead match {
    case Some(g) => expandGroup(g.key)
    case None => expand(l.section.key)
  }
  private def closeLine(l: RichLine[T]): Unit = l.groupHead match {
    case Some(g) => collapseGroup(g.key)
    case None => collapse(l.section.key)
  }
  private def toggleLine(l: RichLine[T]): Unit = if (l.open) closeLine(l) else openLine(l)
  // what a line's action is: its item's, or its section's on its header
  private def actionOf(l: RichLine[T]): Option[RichAction] = l.item match {
    case Some(t) => lookOf(t).action
    case None => if (l.header && l.groupHead.isEmpty) l.section.action else None
  }
  // an expanded item's actions, shown as buttons under its text
  private def buttonsOf(l: RichLine[T]): List[RichAction] =
    if (!l.expanded) Nil else l.item.flatMap(t => expansion()(t)).map(_.actions).getOrElse(Nil)

  /** The cursor onto item `key`, scrolled into view, where it shows. */
  def reveal(key: String): Unit = lineOf.get(key).foreach { line =>
    list.current @= Some(line)
    list.reveal(list.indexOf(line))
  }

  /** What the app does with a title typed in place (`rename`): `None` when it has taken it (and changed its sections to
    * match), or a message saying why not, shown under the field, which stays open. */
  val commit: Var[(T, String) => everyui.async.Task[Option[String]]] =
    own(Var((_: T, _: String) => everyui.async.Task.pure(None: Option[String])))

  /** Item `key`'s title typed in place, over its row (see `VirtualList.edit`), for the app's `commit` to take. */
  def rename(key: String): Unit = lineOf.get(key).foreach(line => list.edit(line, Some(lineAt(line).flatMap(_.item)
    .map(t => lookOf(t).title).getOrElse(""))))

  /** Item `t`'s menu, where it has one: dropped from `anchor` (the ⋮), else at `x`, `y`, or in the middle of the screen
    * where a remote drives the app. */
  def openMenu(t: T, anchor: Option[Component], x: Double, y: Double): Unit =
    drop(menu()(t), lookOf(t).title, anchor, x, y)

  /** Section `s`'s menu, where it has one: dropped from `anchor` (its header's ⋮, or its header), else at `x`, `y`, or in
    * the middle of the screen where a remote drives the app. */
  def openSectionMenu(s: RichSection[T], anchor: Option[Component], x: Double, y: Double): Unit =
    drop(sectionMenu()(s), s.title, anchor, x, y)

  /** Group `g`'s menu, where it has one, as `openSectionMenu` opens a section's. */
  def openGroupMenu(g: RichGroup, anchor: Option[Component], x: Double, y: Double): Unit =
    drop(groupMenu()(g), g.title, anchor, x, y)

  private def drop(items: List[MenuItem], title: String, anchor: Option[Component], x: Double, y: Double): Unit = {
    if (items.nonEmpty) {
      if (Platform.remoteNavigation()) Menu.show(items, title)
      else anchor match {
        case Some(a) => Menu.below(a, items)
        case None => Menu.at(x, y, items)
      }
    }
  }

  private def flatten(): Unit = {
    val shut = collapsed()
    val shutGroups = collapsedGroups()
    val words = query().trim
    val narrowed = words.nonEmpty
    val test = matches()
    // the items expanded, and what they show (an expanded item has a line of its own key, kept apart from its folded
    // line's, so each has its own height in the list, read as it was last drawn)
    val opened = expanded()
    val grows = expansion()
    // a section's lines (its header, and its items where it is open) and what its header says, or none where the words
    // find nothing in it
    def part(s: RichSection[T], nested: Boolean): Option[(Vector[RichLine[T]], Int)] = {
      val items = if (narrowed) s.items.filter(t => test(t, words)) else s.items
      if (narrowed && items.isEmpty) None
      else {
        // what the words find shows whether its section was closed or not
        val open = narrowed || s.title.isEmpty || !shut.contains(s.key)
        val count = if (narrowed) items.size else s.count.getOrElse(items.size)
        // an open section that holds nothing says so where it has words for it (never while narrowed: it found nothing)
        val saysEmpty = open && items.isEmpty && s.empty.nonEmpty
        val head = if (s.title.isEmpty) Vector.empty[RichLine[T]]
          else Vector(RichLine(GroupedList.headerKey(s.key), s, None, open, count, None, nested, blockStart = true,
            blockEnd = !open || (items.isEmpty && !saysEmpty)))
        var shown = if (saysEmpty) Vector(RichLine(RichList.emptyLineKey(s.key), s, None, open, 0, None, nested, empty = true))
          else if (open) items.map { t =>
            val k = keyOf(t)
            val wide = opened.nonEmpty && opened.contains(k) && grows(t).nonEmpty
            RichLine(GroupedList.itemKey(s.key, k) + (if (wide) RichList.expandedMark else ""), s, Some(t), open, 0, None,
              nested, expanded = wide)
          }
          else Vector.empty[RichLine[T]]
        // the first and the last of its block's lines (the header its first, where it has one)
        if (shown.nonEmpty) {
          if (head.isEmpty) shown = shown.updated(0, shown(0).copy(blockStart = true))
          shown = shown.updated(shown.size - 1, shown(shown.size - 1).copy(blockEnd = true))
        }
        Some((head ++ shown, count))
      }
    }
    // a section at a time, or a group's run of them (no builder: a Roku has none)
    val all = sections()
    var out = Vector.empty[RichLine[T]]
    var i = 0
    while (i < all.size) {
      val s = all(i)
      s.group match {
        case None =>
          part(s, false).foreach(p => out = out ++ p._1)
          i += 1
        case Some(g) =>
          var j = i
          while (j < all.size && all(j).group.exists(_.key == g.key)) j += 1
          val parts = all.slice(i, j).flatMap(s => part(s, true).toList)
          // a group the words find nothing in is not shown; one they find something in is, opened
          if (parts.nonEmpty) {
            val open = narrowed || !shutGroups.contains(g.key)
            out = out :+ RichLine(RichList.groupLineKey(g.key), s, None, open, parts.foldLeft(0)((n, p) => n + p._2), Some(g))
            if (open) parts.foreach(p => out = out ++ p._1)
          }
          i = j
      }
    }
    // each item's line, the first where it shows, as pairs made into a map in one go (a Roku's map copies itself for
    // each key added one at a time): reversed, so the first of an item's lines is the one kept
    lineOf = out.flatMap(l => l.item.map(t => keyOf(t) -> l.key).toList).reverse.toMap
    lines @= out
  }

  // a drop, as the app hears it: the items dragged, the section they would go into, and the item they would go before
  private def moveFor(d: Drop): Option[RichMove] = if (!(d.from eq list)) None else {
    val from = d.keys.flatMap(k => lineAt(k).toList)
    if (from.isEmpty || from.exists(_.item.isEmpty) || from.size != d.keys.size) None
    else {
      val keys = from.flatMap(_.item.toList).map(keyOf)
      val moving = d.keys.toSet
      // the first item at or after line `i` in `section`'s, other than those moving
      def firstFrom(i: Int, section: String): Option[String] = {
        var at = i
        var found: Option[String] = None
        var more = true
        while (more && at < list.count) {
          val l = list(at)
          if (l.section.key != section) more = false
          else if (l.item.nonEmpty && !moving.contains(l.key)) {
            found = l.item.map(keyOf)
            more = false
          }
          at += 1
        }
        found
      }
      val target = d.target.map(k => list.indexOf(k)).filter(_ >= 0)
      val place: Option[(String, Option[String])] = target.flatMap { i =>
        val l = list(i)
        // the start of the section on line `at`
        def startOf(at: Int): (String, Option[String]) = (list(at).section.key, firstFrom(at, list(at).section.key))
        // onto an empty section's line is into it
        if (l.empty) Some((l.section.key, None))
        else if (l.groupHead.nonEmpty) d.position match {
          // before a group's header is the end of the section above it, where there is one and it is not a closed group
          case DropPosition.Before => if (i > 0 && list(i - 1).groupHead.isEmpty) Some((list(i - 1).section.key, None)) else None
          // onto or after it is the start of its first section, where it is open: never into a closed group
          case _ => if (l.open && i + 1 < list.count) Some(startOf(i + 1)) else None
        }
        else (l.item.nonEmpty, d.position) match {
          case (true, DropPosition.Before) => Some((l.section.key, firstFrom(i, l.section.key)))
          case (true, _) => Some((l.section.key, firstFrom(i + 1, l.section.key)))
          // before a header is the end of the section above it, where there is one: under a group's header, the start of
          // its own
          case (false, DropPosition.Before) if i > 0 =>
            if (list(i - 1).groupHead.nonEmpty) Some(startOf(i)) else Some((list(i - 1).section.key, None))
          case (false, _) => Some((l.section.key, firstFrom(i + 1, l.section.key)))
        }
      }
      place.map { case (section, before) => RichMove(keys, from.head.section.key, section, before) }
    }
  }

  private def lineAt(key: String): Option[RichLine[T]] = {
    val i = list.indexOf(key)
    if (i < 0) None else Some(list(i))
  }

  list.selection.multiple @= false
  list.clickActivates @= Some(true)
  // (an item's action is reached as a header's is: Right onto it, Left back)
  list.keys = (e: KeyEvent) => list.currentItem.filter(l => !l.empty && !e.shift && !e.ctrl && !e.meta && !e.alt)
    .flatMap { line =>
      val forward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
      val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
      val acts = actionOf(line).nonEmpty
      // an expanded item's buttons come after its action, on along the arrow
      val more = buttonsOf(line).size
      if (e.key == Key.Escape && line.expanded) Some(() => line.item.foreach(t => foldItem(keyOf(t))))
      else if (e.key == forward && line.header && !line.open) Some(() => openLine(line))
      else if (e.key == forward && atButton() >= 0) {
        if (atButton() + 1 < more) Some(() => atButton @= atButton() + 1) else None
      }
      else if (e.key == forward && acts && !atAction()) Some(() => atAction @= true)
      else if (e.key == forward && more > 0) Some(() => {
        atAction @= false
        atButton @= 0
      })
      else if (e.key == back && atButton() > 0) Some(() => atButton @= atButton() - 1)
      else if (e.key == back && atButton() == 0) Some(() => {
        atButton @= -1
        atAction @= acts
      })
      else if (e.key == back && atAction()) Some(() => atAction @= false)
      else if (e.key == back && line.header && line.open) Some(() => closeLine(line))
      else None
    }
  // a click on an empty section's line does nothing, and one on a row's action is the action's alone (it has run it,
  // and the list may hear it as well, where running it took longer than a click's climb is held); one on an item that
  // expands opens or folds it, but one on its text or its buttons while it is expanded only puts the cursor there
  list.clicks = (i: Int, along: Double) => {
    val line = list(i)
    val pressed = pressedAt
    pressedAt = None
    if (line.empty) true
    else if (list.rowAt(i).exists(_.asInstanceOf[RichRow[T]].onActionAt(along))) true
    else if (line.header) {
      list.moveTo(i)
      atAction @= false
      toggleLine(line)
      true
    }
    else line.item match {
      case Some(t) if expands(t) =>
        val head = list.rowAt(i).map(_.asInstanceOf[RichRow[T]].headHeight).getOrElse(list.heightAt(i))
        if (line.expanded && pressed.exists(y => y - list.top(i) >= head)) list.current @= Some(line.key)
        else {
          list.moveTo(i)
          atAction @= false
          toggleItem(keyOf(t))
        }
        true
      case _ => false
    }
  }
  list.event.pointer.down.attach(e => pressedAt = Some(e.y - list.absoluteTop + list.offset()))
  list.activated.attach(line => line.item match {
    case _ if line.empty => ()
    case _ if atAction() && actionOf(line).nonEmpty => actionOf(line).foreach(_.run())
    case _ if atButton() >= 0 && line.expanded => buttonsOf(line).drop(atButton()).headOption.foreach(_.run())
    case Some(t) if expands(t) => toggleItem(keyOf(t))
    case Some(t) =>
      selected @= Some(keyOf(t))
      activated @= t
    case None => toggleLine(line)
  })
  // the cursor never rests on an empty section's line: it goes on past it the way it was going, or back where there is
  // nothing past it; and off the header's action (and an expanded item's buttons) as it moves
  private var lastAt = -1
  list.current.attach { k =>
    atAction @= false
    atButton @= -1
    val i = k.map(list.indexOf).getOrElse(-1)
    if (i >= 0 && list(i).empty) {
      val step = if (i < lastAt) -1 else 1
      def from(dir: Int): Int = {
        var j = i + dir
        while (j >= 0 && j < list.count && list(j).empty) j += dir
        if (j >= 0 && j < list.count) j else -1
      }
      val to = if (from(step) >= 0) from(step) else from(-step)
      if (to >= 0) list.moveTo(to)
    }
    else lastAt = i
  }
  list.commit := ((e: ListEdit[RichLine[T]]) => e.item.item match {
    case Some(t) => commit()(t, e.text)
    case None => everyui.async.Task.pure(None: Option[String])
  })
  // a header's menu at the pointer, or dropped from the header for the keys
  list.menu.attach { p =>
    val anchor = if (p.pointer) None else list.rowAt(list.indexOf(p.key))
    (p.item.item, p.item.groupHead) match {
      case _ if p.item.empty => ()
      case (Some(t), _) => openMenu(t, None, p.x, p.y)
      case (None, Some(g)) => openGroupMenu(g, anchor, p.x, p.y)
      case (None, None) => openSectionMenu(p.item.section, anchor, p.x, p.y)
    }
  }
  list.draggable := dragsItems()
  list.accepts := ((d: Drop) => moveFor(d).exists(m => accepts()(m)))
  list.dropped.attach(d => moveFor(d).filter(m => accepts()(m)).foreach(m => moved @= m))
  list.dragLabel @= ((rows: Vector[RichLine[T]]) => rows.headOption.flatMap(_.item).map(t => lookOf(t).title).getOrElse("") +
    (if (rows.size > 1) "  +" + (rows.size - 1).toString else ""))
  // the app choosing another item: the cursor goes to it
  selected.attach(k => k.foreach(reveal))

  sections.attach(_ => flatten())
  collapsed.attach(_ => flatten())
  collapsedGroups.attach(_ => flatten())
  query.attach(_ => flatten())
  matches.attach(_ => flatten())
  expanded.attach(_ => flatten())
  expansion.attach(_ => flatten())

  private val ticker = Ticker.of(this, RichList.tick)
  ticker.now.attach(t => now @= t)

  field.foreach { f =>
    f.value.attach(v => if (query() != v) query @= v)
    query.attach(q => if (f.value() != q) f.value @= q)
    f.submitted.attach(_ => list.focus())
    f.exitFocus(Key.ArrowDown, list)
    list.exitFocus(Key.ArrowUp, f)
    f.position.left @= 8.0
    f.position.top @= 8.0
    f.size.width := size.width() - 16.0
  }
  private val top: Val[Double] = own(Val(field.map(_.size.height() + 16.0).getOrElse(0.0)))
  list.position.top := top()
  list.size.width := size.width()
  list.size.height := math.max(0.0, size.height() - top())
  size.width @= 320.0
  size.height @= 480.0
  children ++= field.toList ++ List(list)
  flatten()

  override def focus(): Unit = list.focus()
}

object RichList {
  /** How often the times are said again, in seconds. */
  val tick: Double = 20.0

  /** The line of group `group`'s header, apart from any section's or item's. */
  def groupLineKey(group: String): String = "s\u0000" + group

  /** The line an empty section `section` shows in place of its items (`RichSection.empty`). */
  def emptyLineKey(section: String): String = "e\u0000" + section

  /** What an expanded item's line key ends in, apart from its folded line's. */
  val expandedMark: String = "\u0000+"

  /** How many turns an item expanded or folded waits for its row to be drawn at its new height before it is kept in view. */
  val settleTries: Int = 12

  /** A count as a badge says it: "99+" past 99. */
  def countText(n: Int): String = if (n > 99) "99+" else n.toString

  /** Whether `look`'s title or detail holds `words`, in any case. */
  def holds(look: RichLook, words: String): Boolean = {
    val w = words.toLowerCase
    look.title.toLowerCase.contains(w) || look.detail.exists(_.toLowerCase.contains(w))
  }

  /** What a screen reader says for a row: its title, detail, time, count, whether it is unread or busy, and its
    * `liveLabel` while it is live. */
  def said(look: RichLook, now: Long, timeText: (Long, Long) => String): String =
    (List(look.title) ++ look.detail.toList ++ look.time.map(t => timeText(t, now)).toList ++
      (if (look.count > 0) List(countText(look.count)) else Nil) ++
      (if (look.unread) List(unreadLabel) else Nil) ++
      (if (look.busy) ChatWords.busy :: (if (look.busyDetail.isEmpty) Nil else List(look.busyDetail)) else Nil) ++
      (if (look.live) look.liveLabel.filter(_.nonEmpty).toList else Nil))
      .mkString(", ")

  def moreLabel: String = Words("list.more")
  def unreadLabel: String = Words("list.unread")
}
