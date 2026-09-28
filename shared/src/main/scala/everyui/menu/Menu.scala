package everyui.menu

import everyui.component.*
import everyui.component.types.FontWeight
import everyui.event.Key
import everyui.focus.Focus
import everyui.i18n.Direction
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
  * outside every menu closes them all. `dismissed` hears the menus closed with nothing chosen.
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
  val rows: List[MenuRow] = items.map(i => new MenuRow(i, across() - inset * 2.0, points))
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
  private val top: Val[Double] = own(Val(heading.map(_.size.height() + points * 1.6).getOrElse(inset)))
  private val scroll = new ScrollContainer {
    content.children += column
  }
  private var settled = false
  // the submenu this opened, while it is up
  private var opened: Option[Menu] = None

  across := math.max(Menu.minWidth(), rows.map(_.natural()).foldLeft(0.0)(math.max) + inset * 2.0)
  heading.foreach { h =>
    Mirror.place(h, () => size.width(), () => points * 1.2)
    h.position.top @= points
  }
  scroll.position.left @= inset
  scroll.position.top := top()
  scroll.size.width := across() - inset * 2.0
  scroll.size.height := math.min(column.size.height(), ui.size.height() - 16.0 - top() - inset)
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
    else row.chosen.attach(_ => choose(row.item))
    row.event.pointer.enter.attach(_ => pointedAt(row))
    row.event.pointer.leave.attach(_ => if (pointed.exists(_ eq row)) pointed = None)
  }

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

  /** Put it up, the focus on its first entry that can be chosen: over what is up with `over` (a submenu), `dim`
    * and `centre`d for a menu in the middle of the screen. */
  private[menu] def up(over: Boolean = false, dim: Boolean = false, centre: Boolean = false,
                       dismissed: () => Unit = () => ()): Unit = {
    val first: Component = choosable.headOption.getOrElse(this)
    val after = () => if (under.isEmpty && !settled) dismissed()
    if (over) Modal.over(this, first, after, dim = dim, centre = centre)
    else Modal.show(this, first, after, dim = dim, centre = centre)
  }

  override def kind: String = "Menu"
}

object Menu {
  /** How big a menu's words are: a television's app sets it larger once, before it opens any. */
  val textSize: Var[Double] = Var(14.0)
  /** The least width of a menu. */
  val minWidth: Var[Double] = Var(200.0)
  /** How long the pointer rests on an entry with a submenu before it opens, in seconds. */
  val hoverDelay: Var[Double] = Var(0.3)

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

  /** A menu dropped from `anchor`: under it at its start, or over it where below has no room. */
  def below(anchor: Component, items: List[MenuItem], dismissed: () => Unit = () => ()): Menu = {
    val menu = new Menu(items)
    val left = anchor.absoluteLeft
    val top = anchor.absoluteTop
    // lined up with the anchor's start: its right edge in a right-to-left language
    menu.position.left := math.max(8.0, math.min(if (Direction.rtl()) left + anchor.size.width() - menu.size.width() else left,
      ui.size.width() - menu.size.width() - 8.0))
    menu.position.top := {
      val under = top + anchor.size.height() + 4.0
      if (under + menu.size.height() <= ui.size.height() - 8.0) under
      else math.max(8.0, top - menu.size.height() - 4.0)
    }
    menu.up(dismissed = dismissed)
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
