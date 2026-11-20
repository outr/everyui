package everyui.menu

import everyui.component.*
import everyui.component.types.{FontWeight, Semantics}
import everyui.event.{FocusMoves, Key, KeyEvent, Pointer, PointerDrag}
import everyui.focus.Focus
import everyui.i18n.{Direction, Words}
import everyui.kit.Mirror
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, Shadow, ui}
import reactify.*

/** A list of choices over the screen: a context menu where the pointer asked for it (`Menu.at`), one dropped from a
  * button (`Menu.below`), or, on a television, a panel in the middle of the screen under a title (`Menu.show`).
  *
  * Up and Down move through what can be chosen, round from the last to the first; OK or a click chooses, which closes
  * every menu open and then does what the entry says. Right (or a click, or the pointer resting on it for
  * `Menu.hoverDelay`) opens a submenu beside its entry, and Left, Back or Escape closes it, back on that entry; Back or
  * a click outside closes a menu, and the focus goes back where it was. The pointer resting on an entry moves the focus
  * to it, and over the menu a submenu opened from, another entry switches submenus and a plain one closes it; a click
  * outside every menu closes them all. An entry that `keepOpen`s (a box ticked, `MenuItem.toggle`) does its work and
  * leaves every menu open, its tick following the app. `dismissed` hears the menus closed with nothing chosen.
  *
  * A finger's menu too tall for its place (`Menu.touch`, leaving less than `Menu.tapRoom` to tap outside it) goes up
  * as a sheet from the foot of the screen instead, over a dimmed screen that closes it tapped, with a grip at its top
  * dragged down to close it (a Close button to a screen reader); the rest is a menu's as ever.
  *
  * {{{
  *   Menu.context(fileRow) {
  *     List(MenuItem("Open", () => open(file)),
  *          MenuItem.command(Keymap.global("file.rename").get),
  *          MenuItem.separator,
  *          MenuItem.sub("Copy", List(MenuItem("Path", () => copy(file.path)), MenuItem("Name", () => copy(file.name)))),
  *          MenuItem.check("Pinned", file.pinned, pin(file, _)),
  *          MenuItem("Delete", () => delete(file), MaterialIcon.Delete, danger = true))
  *   }
  *   Menu.below(moreButton, items)
  * }}} */
class Menu(items: List[MenuItem], title: String = "", private val under: Option[Menu] = None) extends Container {
  private val points: Double = Menu.textSize()
  private val inset: Double = points * 0.3
  /** As wide as its widest entry asks, and never narrower than `Menu.minWidth`. */
  private val across: Var[Double] = own(Var(Menu.minWidth()))
  val rows: List[MenuRow] = items.map(i => new MenuRow(i, across() - inset * 2.0, points, Menu.widest - inset * 2.0))
  // a menu to a screen reader, named by its title where it has one, its rows its entries
  semantics @= Semantics("menu", label = title)
  private val column = new Container {
    layout @= Some(VerticalLayout(0.0))
    children ++= rows
  }
  private val heading: Option[TextView] = if (title.isEmpty) None else Some(new TextView {
    content @= title
    font.size @= points * 0.9
    font.weight @= FontWeight.SemiBold
    color := Theme.muted()
  })
  /** Whether it is up as a sheet from the foot of the screen (`Menu.touch`): a finger's menu too tall for its place. */
  def isSheet: Boolean = sheet()
  private val sheet: Var[Boolean] = own(Var(false))
  // how far down a drag on its grip has drawn the sheet
  private val pulled: Var[Double] = own(Var(0.0))
  private val grip: Double = 28.0
  private val top: Val[Double] = own(Val(heading.map(_.size.height() + points * 1.6).getOrElse(inset) +
    (if (sheet()) grip else 0.0)))
  private val scroll = new ScrollContainer {
    content.children += column
  }
  private var settled = false
  // the submenu this opened, while it is up
  private var opened: Option[Menu] = None

  across := (if (sheet()) math.min(ui.size.width(), Menu.sheetWidest()) else
    math.min(math.max(Menu.minWidth(), rows.map(_.natural()).foldLeft(0.0)(math.max) + inset * 2.0), Menu.widest))
  heading.foreach { h =>
    Mirror.place(h, () => size.width(), () => points * 1.2)
    h.position.top := points + (if (sheet()) grip else 0.0)
  }
  scroll.position.left @= inset
  scroll.position.top := top()
  scroll.size.width := across() - inset * 2.0
  // as a sheet, kept `Menu.tapRoom` short of the head of the screen, to be tapped to close it
  scroll.size.height := math.min(column.size.height(),
    ui.size.height() - (if (sheet()) Menu.tapRoom() else 16.0) - top() - inset)
  size.width := across()
  size.height := top() + scroll.size.height() + inset
  backgroundColor := Theme.raised()
  borderWidth @= 1.0
  borderColor := Theme.border()
  radius @= points * 0.6
  shadow @= Some(Shadow(0.0, 6.0, 24.0, Color.Black.withAlpha(0.45)))
  children ++= heading.toList ++ List(scroll)
  // round from one end to the other, and never out of the menu
  column.exitAction(Key.ArrowDown, () => choosable.headOption.foreach(_.focus()))
  column.exitAction(Key.ArrowUp, () => choosable.lastOption.foreach(_.focus()))
  // back out of a submenu the way the page reads back (Left, or Right in a right-to-left language)
  column.exitAction(Key.ArrowRight, () => if (under.nonEmpty && Direction.rtl()) Modal.remove(this))
  column.exitAction(Key.ArrowLeft, () => if (under.nonEmpty && !Direction.rtl()) Modal.remove(this))

  rows.foreach { row =>
    if (row.item.submenu.nonEmpty) {
      row.chosen.attach(_ => open(row))
      row.opened.attach(_ => open(row))
    }
    // one that keeps the menu open does its work where it is, the focus staying on it
    else if (row.item.keepOpen) row.chosen.attach(_ => row.item.action())
    else row.chosen.attach(_ => choose(row.item))
    row.event.key.down.attach(k => keyed(row, k))
    row.event.pointer.enter.attach(_ => pointedAt(row))
    row.event.pointer.leave.attach(_ => if (pointed.exists(_ eq row)) pointed = None)
  }

  // Home and End to the first and the last entry; a letter, or letters typed within a moment of each other, to the
  // next entry they begin
  private def keyed(row: MenuRow, k: KeyEvent): Unit = if (k.key == Key.Other && !k.ctrl && !k.meta && !k.alt) {
    val all = choosable
    if (k.name == "Home") all.headOption.foreach(_.focus())
    else if (k.name == "End") all.lastOption.foreach(_.focus())
    else if (k.name.length == 1 && k.name.trim.nonEmpty && all.nonEmpty) {
      val now = Platform.ticks()
      val letter = k.name.toLowerCase
      // a new letter after the same one over and over (going round the entries it begins) starts a new word
      val cycling = typed.length > 1 && typed.forall(_ == typed.charAt(0)) && letter.charAt(0) != typed.charAt(0)
      typed = (if (now - typedAt > 800.0 || cycling) "" else typed) + letter
      typedAt = now
      // the same letter again goes on to the next entry it begins
      val word = if (typed.forall(_ == typed.charAt(0))) typed.substring(0, 1) else typed
      val at = all.indexWhere(_ eq row)
      val from = if (word.length == 1) at + 1 else math.max(0, at)
      (0 until all.length).map(i => all((from + i) % all.length))
        .find(_.item.label.toLowerCase.startsWith(word)).foreach(_.focus())
    }
  }
  private var typed = ""
  private var typedAt = 0.0

  // the row the pointer is on, and the one whose submenu is open
  private var pointed: Option[MenuRow] = None
  private var openedFrom: Option[MenuRow] = None

  /** The pointer arrived on `row`: it takes the focus; a submenu open from another row closes; and a submenu of its own
    * opens once the pointer has rested there `Menu.hoverDelay`, as a desktop menu does. */
  private def pointedAt(row: MenuRow): Unit = if (!pointed.exists(_ eq row)) {
    pointed = Some(row)
    if (!openedFrom.exists(_ eq row)) closeOpened()
    if (row.item.choosable) row.focus()
    if (row.item.choosable && row.item.submenu.nonEmpty && Platform.pointerSupport())
      Platform.after(Menu.hoverDelay(), () =>
        if (pointed.exists(_ eq row) && Focus.showing(row) && !opened.exists(m => Focus.showing(m))) open(row))
  }

  /** The submenu open from here, and any it opened, closed. */
  private def closeOpened(): Unit = {
    opened.foreach { m =>
      m.closeOpened()
      Modal.remove(m)
    }
    opened = None
    openedFrom = None
  }

  /** Whether `x`, `y` is inside this menu. */
  private[menu] def holds(x: Double, y: Double): Boolean =
    x >= absoluteLeft && x < absoluteLeft + size.width() && y >= absoluteTop && y < absoluteTop + size.height()

  /** The pointer at `x`, `y` over the sheet of a submenu opened from here (which is over this menu): passed on to this
    * menu where it is inside it, or to the menu this was opened from; a `click` chooses the row there. Whether a menu
    * had it. */
  private[menu] def pointer(x: Double, y: Double, click: Boolean): Boolean =
    if (holds(x, y)) {
      rows.find(r => r.item.choosable && y >= r.absoluteTop && y < r.absoluteTop + r.size.height()).foreach { row =>
        if (click) {
          closeOpened()
          pointed = Some(row)
          row.chosen @= (())
        }
        else pointedAt(row)
      }
      true
    }
    else under.exists(_.pointer(x, y, click))

  /** The entries that can be chosen, in order. */
  def choosable: List[MenuRow] = rows.filter(_.item.choosable)

  /** Everything chosen, every menu closes (this one and those it opened from), and then the entry does its work. */
  private def choose(item: MenuItem): Unit = {
    root.settled = true
    Menu.closeFrom(this)
    item.action()
  }

  private def root: Menu = under.map(_.root).getOrElse(this)

  /** `row`'s submenu, beside it the way the page reads (on its right, or its left in a right-to-left language), or on
    * the other side where that has no room. */
  private def open(row: MenuRow): Unit = {
    val sub = new Menu(row.item.submenu, "", Some(this))
    sub.position.left := {
      val right = absoluteLeft + size.width() - inset
      val left = absoluteLeft - sub.size.width() + inset
      val fitsRight = right + sub.size.width() <= ui.size.width() - 8.0
      if (Direction.rtl()) (if (left >= 8.0 || !fitsRight) math.max(8.0, left) else right)
      else if (fitsRight) right else math.max(8.0, left)
    }
    sub.position.top := Menu.fit(row.absoluteTop - inset, sub.size.height())
    opened = Some(sub)
    openedFrom = Some(row)
    sub.up(over = true)
    // the pointer over the menus under it reaches them through its sheet: another entry there switches submenus, a
    // plain one closes it, and a click there chooses; a click outside every menu closes them all
    sub.parent().foreach { sheet =>
      sheet.event.pointer.move.attach(e => if (!sub.holds(e.x, e.y)) pointer(e.x, e.y, click = false))
      sheet.event.click.attach(e => if (!pointer(e.x, e.y, click = true)) Menu.closeFrom(this))
    }
  }

  /** Put it up, the focus on its current choice (an `active` entry), else its first entry that can be chosen: over what
    * is up with `over` (a submenu), `dim` and `centre`d for a menu in the middle of the screen. A menu opened while another is on top takes its place; one
    * opened from a dialog goes over it, and the dialog is there again once it closes. */
  private[menu] def up(over: Boolean = false, dim: Boolean = false, centre: Boolean = false,
                       dismissed: () => Unit = () => (), room: () => Double = () => ui.size.height() - 16.0): Unit = {
    val first: Component = choosable.find(_.item.active).orElse(choosable.headOption).getOrElse(this)
    val after = () => if (under.isEmpty && !settled) dismissed()
    if (over || !Modal.top.forall(_.kind == "Menu")) Modal.over(this, first, after, dim = dim, centre = centre)
    else Modal.show(this, first, after, dim = dim, centre = centre)
    // a finger's menu too tall for its place, now or once its entries are measured, goes up as a sheet
    if (Menu.touch) {
      val tall = () => if (!sheet() && top() + column.size.height() + inset > room() - Menu.tapRoom()) asSheet()
      follow(column.size.height, (_: Double) => tall())
      tall()
    }
  }

  /** Up as a sheet from the foot of the screen, over a dimmed screen, as wide as the screen (up to
    * `Menu.sheetWidest`) and at least `Menu.tapRoom` short of its head: a grip at its top, dragged down to close it
    * (or activated, by a screen reader), and the dimmed screen above it tapped to close it. */
  private def asSheet(): Unit = {
    sheet @= true
    position.left := (ui.size.width() - size.width()) / 2.0
    position.top := ui.size.height() - size.height() + pulled()
    // the dialog's sheet under it, which closes the menu tapped, dimmed
    parent().foreach(_.backgroundColor := Theme.backdrop())
    val bar = new Container
    bar.size.width @= 36.0
    bar.size.height @= 4.0
    bar.radius @= 2.0
    bar.backgroundColor := Theme.muted()
    val handle = new Container
    handle.size.width := size.width()
    handle.size.height @= grip
    handle.semantics @= Semantics.button(Words.close)
    handle.children += bar
    bar.position.left := (handle.size.width() - 36.0) / 2.0
    bar.position.top @= 10.0
    handle.event.click.attach(_ => Modal.remove(this))
    // dragged down: closed once further than a quarter of it (or 64), else back up
    val from: Var[Option[Double]] = handle.own(Var(None))
    new PointerDrag(handle, (_, y) => {
      val now = y + handle.absoluteTop
      if (from().isEmpty) from @= Some(now)
      pulled @= math.max(0.0, now - from().getOrElse(now))
    }, () => {
      from @= None
      if (pulled() >= math.max(64.0, size.height() / 4.0)) Modal.remove(this)
      else pulled @= 0.0
    })
    children += handle
  }

  override def kind: String = "Menu"
}

object Menu {
  /** How big a menu's words are: a television's app sets it larger once, before it opens any. */
  val textSize: Var[Double] = Var(14.0)
  /** The least width of a menu. */
  val minWidth: Var[Double] = Var(200.0)
  /** The widest a menu is drawn: the screen's width less a margin each side, its entries wrapping within it. */
  def widest: Double = math.max(Menu.minWidth(), ui.size.width() - 16.0)
  /** How long the pointer rests on an entry with a submenu before it opens, in seconds. */
  val hoverDelay: Var[Double] = Var(0.3)
  /** How much of the head of the screen a menu up as a sheet leaves uncovered, at the least, to be tapped to close it:
    * a finger's width. */
  val tapRoom: Var[Double] = Var(48.0)
  /** The widest a menu up as a sheet is drawn (on a tablet), in the middle of the foot of the screen. */
  val sheetWidest: Var[Double] = Var(640.0)

  /** Whether a menu put up now is a finger's, which goes up as a sheet from the foot of the screen where it is too tall
    * for its place, leaving less than `tapRoom` to tap outside it: what was used just now was a finger (a tap on a
    * touch laptop as on a phone), or, with no pointer used just now, the screen is touched and has no fine pointer (a
    * phone's menu opened by the app). Never where a remote moves the focus; a mouse's and the keys' menus drop as
    * ever. */
  def touch: Boolean = !Platform.remoteNavigation() &&
    (if (Pointer.recent) Pointer.finger else Platform.touchSupport() && !Platform.pointerSupport())

  /** A menu in the middle of the screen under `title`, over a dimmed screen: what a remote's menu is. */
  def show(items: List[MenuItem], title: String = "", dismissed: () => Unit = () => ()): Menu = {
    val menu = new Menu(items, title)
    menu.up(dim = true, centre = true, dismissed = dismissed)
    menu
  }

  /** A context menu with its corner at `x`, `y` (where the pointer asked for it), kept on the screen. */
  def at(x: Double, y: Double, items: List[MenuItem], dismissed: () => Unit = () => ()): Menu = {
    val menu = new Menu(items)
    // reaching the way the page reads from the pointer: leftward in a right-to-left language
    menu.position.left := math.max(8.0, math.min(if (Direction.rtl()) x - menu.size.width() else x,
      ui.size.width() - menu.size.width() - 8.0))
    menu.position.top := fit(y, menu.size.height())
    menu.up(dismissed = dismissed)
    menu
  }

  /** A menu dropped from `anchor`: under it at its start, or over it where below has no room. Closed, it gives the focus
    * back to what had it before the press that opened it, where that press moved the focus to `anchor` (a field's focus,
    * a button pressed for its menu): else to what had it as it opened, `anchor` most often. */
  def below(anchor: Component, items: List[MenuItem], dismissed: () => Unit = () => ()): Menu = {
    // asked before the menu takes the focus, which moves it again
    // (or, where a click takes no focus, let it go from there: Focus.clicked)
    val back = if (Pointer.recent && (Focus.focused().exists(_ eq anchor) ||
      (Focus.focused().isEmpty && !Platform.remoteNavigation()))) FocusMoves.before
      else None
    val menu = new Menu(items)
    val left = anchor.absoluteLeft
    val top = anchor.absoluteTop
    val after = top + anchor.size.height() + 4.0
    // lined up with the anchor's start: its right edge in a right-to-left language
    menu.position.left := math.max(8.0, math.min(if (Direction.rtl()) left + anchor.size.width() - menu.size.width() else left,
      ui.size.width() - menu.size.width() - 8.0))
    menu.position.top := {
      if (after + menu.size.height() <= ui.size.height() - 8.0) after
      else math.max(8.0, top - menu.size.height() - 4.0)
    }
    // too tall for the room under it or over it, a finger's goes up as a sheet
    menu.up(dismissed = dismissed, room = () => math.max(ui.size.height() - 8.0 - after, top - 12.0))
    back.foreach(b => Modal.returnTo(menu, Some(b)))
    menu
  }

  /** `target`'s context menu, made afresh each time it is asked for: at the pointer for the secondary button, and
    * dropped from `target` for the keyboard's menu key or OK held on it (a remote's long press). */
  def context(target: Component)(items: => List[MenuItem]): Unit = {
    target.event.pointer.menu.attach { e =>
      e.consume()
      at(e.x, e.y, items)
    }
    target.event.hold.attach(_ => below(target, items))
    target.event.key.down.attach(k => if (k.key == Key.ContextMenu) below(target, items))
  }

  /** Where a menu `height` tall starting at `y` goes to stay on the screen. */
  private[menu] def fit(y: Double, height: Double): Double =
    math.max(8.0, math.min(y, ui.size.height() - height - 8.0))

  /** `menu` closed, and each it was opened from. */
  private def closeFrom(menu: Menu): Unit = {
    Modal.remove(menu)
    menu.under.foreach(closeFrom)
  }
}
