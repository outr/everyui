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
  * it or the cursor on it, and always where there is no pointer; a header with none only opens and closes. With
  * `search`, a field over the list narrows it to the items `matches` finds for the words (`query`, which
  * the app can also set), under their sections, opened; an app filtering its own sections leaves it off. With
  * `reorders` on, items are dragged into another place, which `accepts` allows (within their own section, by default)
  * and `moved` hears: never into a group that is closed.
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
  /** The time the items' times are said against ("5m" ago), moved on every `RichList.tick` seconds. */
  val now: Var[Long] = own(Var(everyui.util.Clock.real()))
  /** How an item's time is said, seen at a moment: by default how long ago (`ChatTime.ago`). */
  var timeText: (Long, Long) => String = (time: Long, at: Long) => ChatTime.ago(time, at)
  /** Whether items can be dragged into another place (never while the list is narrowed by words). */
  val reorders: Var[Boolean] = own(Var(false))
  /** Whether a drag may land where it would: within the section it came from, by default. */
  val accepts: Var[RichMove => Boolean] = own(Var((m: RichMove) => m.section == m.from))
  /** Items dragged into another place, where `accepts` let them. */
  val moved: Channel[RichMove] = Channel[RichMove]
  /** How the sections are drawn: as a tree (`RichStyle.Tree`, the default) or as tinted blocks (`RichStyle.Blocks`). */
  val style: Var[RichStyle] = own(Var(RichStyle.Tree))

  private val lines: Var[Vector[RichLine[T]]] = own(Var(Vector.empty))
  /** Whether any section's or group's header has a menu: then every header's count sits short of the ⋮, in one line
    * down the list, and in a list with none each is where it always was. */
  val headersHaveMenus: Val[Boolean] = own(Val(sections().exists(s => sectionMenu()(s).nonEmpty ||
    s.group.exists(g => groupMenu()(g).nonEmpty))))
  val list: VirtualList[RichLine[T]] = new VirtualList[RichLine[T]](lines, _.key,
    l => l.item.map(t => lookOf(t).title).getOrElse(l.groupHead.map(_.title).getOrElse(l.section.title)), RichRow.tallHeight, measuredRows = true)(() =>
    new RichRow[T](this))
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
    // a section's lines (its header, and its items where it is open) and what its header says, or none where the words
    // find nothing in it
    def part(s: RichSection[T], nested: Boolean): Option[(Vector[RichLine[T]], Int)] = {
      val items = if (narrowed) s.items.filter(t => test(t, words)) else s.items
      if (narrowed && items.isEmpty) None
      else {
        // what the words find shows whether its section was closed or not
        val open = narrowed || s.title.isEmpty || !shut.contains(s.key)
        val count = if (narrowed) items.size else s.count.getOrElse(items.size)
        val head = if (s.title.isEmpty) Vector.empty[RichLine[T]]
          else Vector(RichLine(GroupedList.headerKey(s.key), s, None, open, count, None, nested, blockStart = true,
            blockEnd = !open || items.isEmpty))
        var shown = if (open) items.map(t => RichLine(GroupedList.itemKey(s.key, keyOf(t)), s, Some(t), open, 0, None, nested))
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
        if (l.groupHead.nonEmpty) d.position match {
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
  list.clickActivates @= true
  list.keys = (e: KeyEvent) => list.currentItem.filter(l => l.item.isEmpty && !e.shift && !e.ctrl && !e.meta && !e.alt)
    .flatMap { line =>
      val forward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
      val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
      if (e.key == forward && !line.open) Some(() => openLine(line))
      else if (e.key == back && line.open) Some(() => closeLine(line))
      else None
    }
  list.clicks = (i: Int, _: Double) => {
    val line = list(i)
    if (line.item.isEmpty) {
      list.moveTo(i)
      toggleLine(line)
      true
    }
    else false
  }
  list.activated.attach(line => line.item match {
    case Some(t) =>
      selected @= Some(keyOf(t))
      activated @= t
    case None => toggleLine(line)
  })
  list.commit := ((e: ListEdit[RichLine[T]]) => e.item.item match {
    case Some(t) => commit()(t, e.text)
    case None => everyui.async.Task.pure(None: Option[String])
  })
  // a header's menu at the pointer, or dropped from the header for the keys
  list.menu.attach { p =>
    val anchor = if (p.pointer) None else list.rowAt(list.indexOf(p.key))
    (p.item.item, p.item.groupHead) match {
      case (Some(t), _) => openMenu(t, None, p.x, p.y)
      case (None, Some(g)) => openGroupMenu(g, anchor, p.x, p.y)
      case (None, None) => openSectionMenu(p.item.section, anchor, p.x, p.y)
    }
  }
  list.draggable := reorders() && query().trim.isEmpty
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

  private val ticker = new Ticker(RichList.tick)
  ticker.now.attach(t => now @= t)
  onDispose(() => ticker.stop())

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

  /** A count as a badge says it: "99+" past 99. */
  def countText(n: Int): String = if (n > 99) "99+" else n.toString

  /** Whether `look`'s title or detail holds `words`, in any case. */
  def holds(look: RichLook, words: String): Boolean = {
    val w = words.toLowerCase
    look.title.toLowerCase.contains(w) || look.detail.exists(_.toLowerCase.contains(w))
  }

  /** What a screen reader says for a row: its title, detail, time, count, and whether it is unread. */
  def said(look: RichLook, now: Long, timeText: (Long, Long) => String): String =
    (List(look.title) ++ look.detail.toList ++ look.time.map(t => timeText(t, now)).toList ++
      (if (look.count > 0) List(countText(look.count)) else Nil) ++
      (if (look.unread) List(unreadLabel) else Nil) ++
      (if (look.busy) ChatWords.busy :: (if (look.busyDetail.isEmpty) Nil else List(look.busyDetail)) else Nil))
      .mkString(", ")

  def moreLabel: String = word("list.more", "More")
  def unreadLabel: String = word("list.unread", "unread")

  private def word(key: String, english: String): String = {
    val said = Words(key)
    if (said == key) english else said
  }
}
