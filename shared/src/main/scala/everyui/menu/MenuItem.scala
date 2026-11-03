package everyui.menu

import everyui.font.MaterialIcon
import everyui.keymap.{Chord, Command}
import reactify.Val

/** One entry of a [[Menu]]: what it says and what choosing it does, with an `icon` before it, a line of `detail` under
  * it, and after it a `shortcut` hint (a chord, as a keymap names it) or a `note`. A `checked` entry shows whether it is
  * on; one with a `submenu` opens it rather than doing anything itself. A disabled one is shown but cannot be chosen;
  * a `danger` one is drawn in the warning colour; a `separator` is a rule between groups. An `active` entry is the
  * current choice of a "pick one of these" menu (a view, a sort, a language): the one focused as the menu opens, ringed
  * in the accent with its words in it, and half lit while the focus is elsewhere, so the focus stays the plainest to see.
  *
  * An entry that `keepOpen`s does its work and leaves the menu open, and any it is in, the focus on it, and Space
  * chooses it as OK does: a box ticked before something else in the menu is picked ("Archive from inbox" above the
  * places to send to). Its tick follows `on` where it has one, so it shows what the app holds, not what was clicked: an
  * action that refuses leaves it as it was (`MenuItem.toggle`). */
case class MenuItem(label: String,
                    action: () => Unit = () => (),
                    icon: MaterialIcon = MaterialIcon.Empty,
                    enabled: Boolean = true,
                    shortcut: Option[Chord] = None,
                    note: String = "",
                    detail: String = "",
                    checked: Option[Boolean] = None,
                    submenu: List[MenuItem] = Nil,
                    danger: Boolean = false,
                    separator: Boolean = false,
                    active: Boolean = false,
                    on: Option[Val[Boolean]] = None,
                    keepOpen: Boolean = false) {
  /** Whether it can be chosen, and so take the focus. */
  def choosable: Boolean = enabled && !separator

  /** Whether it is ticked now: as `on` is, else as `checked` was made. */
  def ticked: Boolean = on.map(_()).orElse(checked).contains(true)
}

object MenuItem {
  /** A rule between groups of entries. */
  val separator: MenuItem = MenuItem("", separator = true)

  /** An entry that runs `command`, by its label, with its first chord as the hint, and only while it is enabled. */
  def command(command: Command, icon: MaterialIcon = MaterialIcon.Empty): MenuItem =
    MenuItem(command.label, () => command.run(), icon, command.enabled(), command.chords().headOption)

  /** An entry that is on or off: choosing it hands `toggle` the other. */
  def check(label: String, on: Boolean, toggle: Boolean => Unit, enabled: Boolean = true): MenuItem =
    MenuItem(label, () => toggle(!on), enabled = enabled, checked = Some(on))

  /** An entry ticked as `on` is, which keeps the menu open: choosing it hands `toggle` the other of `on` now, and the
    * tick changes where it is as `on` does (or does not, where `toggle` refuses). */
  def toggle(label: String, on: Val[Boolean], toggle: Boolean => Unit, enabled: Boolean = true,
             keepOpen: Boolean = true): MenuItem =
    MenuItem(label, () => toggle(!on()), enabled = enabled, checked = Some(on()), on = Some(on), keepOpen = keepOpen)

  /** An entry that opens `items` beside it. */
  def sub(label: String, items: List[MenuItem], icon: MaterialIcon = MaterialIcon.Empty): MenuItem =
    MenuItem(label, icon = icon, submenu = items)
}
