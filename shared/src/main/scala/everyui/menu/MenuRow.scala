package everyui.menu

import everyui.Color
import everyui.component.*
import everyui.component.types.WhiteSpace
import everyui.event.Key
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.Mirror
import everyui.theme.Theme
import everyui.component.types.Semantics
import reactify.*

/** A row of a [[Menu]], `width` wide: a slot for the tick or the icon, the label (and its detail under it), and at the
  * right the shortcut, the note, or the way into a submenu. It lights under the focus, which a pointer resting on it
  * brings, and stays half lit while it is the menu's current choice (`active`), ringed in the accent with its words
  * in it; `chosen` hears it clicked or entered, and `opened` the arrow into its submenu. A separator is a rule. */
class MenuRow(val item: MenuItem, width: => Double, points: Double, widest: => Double = 0.0) extends Container {
  /** A click, a tap or OK on it. */
  val chosen: Channel[Unit] = Channel[Unit]
  /** The arrow pressed into its submenu. */
  val opened: Channel[Unit] = Channel[Unit]

  private val pad: Double = points * 0.85
  private val slot: Double = points * 1.4
  private val ink: Val[Color] = own(Val(
    if (!item.enabled) Theme.muted().withAlpha(0.6) else if (item.danger) Theme.warning()
    else if (item.active) Theme.accent() else Theme.text()))

  // a tick that follows the app (`MenuItem.on`) is there however it starts, shown as it is on
  private val lead: Option[MaterialIconView] =
    if (item.on.nonEmpty) Some(new MaterialIconView(item.icon, points * 1.15) {
      icon := (if (item.ticked) MaterialIcon.Check else item.icon)
    })
    else if (item.checked.contains(true)) Some(new MaterialIconView(MaterialIcon.Check, points * 1.15))
    else if (item.icon.nonEmpty) Some(new MaterialIconView(item.icon, points * 1.15))
    else None
  private val label = new TextView {
    content @= item.label
    font.size @= points
    color := ink()
  }
  private val detail: Option[TextView] = if (item.detail.isEmpty) None else Some(new TextView {
    content @= item.detail
    font.size @= points * 0.85
    color := Theme.muted()
  })
  private val words = Column(2.0)((label :: detail.toList)*)
  private val trail: Option[Component] =
    if (item.submenu.nonEmpty) Some(new MaterialIconView(MaterialIcon.ChevronRight, points * 1.15) {
      icon := Mirror.onward
      color := ink()
    })
    else item.shortcut.map(_.label).orElse(Option(item.note).filter(_.nonEmpty)).map { hint =>
      new TextView {
        content @= hint
        font.size @= points * 0.9
        color := Theme.muted()
      }
    }

  // its words wrapped within the widest a row may be (the screen's, less the menu's edges), less the slot and what
  // trails them: a menu on a phone wraps a long entry rather than running off the screen (words wrap by default)
  private val wordsRoom: Val[Double] = own(Val(if (widest <= 0.0) 0.0 else math.max(points * 4.0,
    widest - pad * 2.0 - slot - trail.map(t => points * 2.0 + t.size.width()).getOrElse(0.0))))
  label.maxWidth := wordsRoom()
  detail.foreach(d => d.maxWidth := wordsRoom())

  /** How wide it would be with the room it asks for: what the menu is made as wide as, for its widest row. */
  val natural: Val[Double] = own(Val(
    if (item.separator) 0.0
    else pad * 2.0 + slot + words.size.width() + trail.map(t => points * 2.0 + t.size.width()).getOrElse(0.0)))

  size.width := width
  if (item.separator) {
    val rule = new Container {
      size.width := MenuRow.this.size.width() - pad
      size.height @= 1.0
      position.left @= pad / 2.0
      position.top @= points * 0.45
      backgroundColor := Theme.border()
    }
    semantics @= Semantics("separator")
    size.height @= points * 0.9 + 1.0
    children += rule
  }
  else {
    focusable @= item.enabled
    // a screen reader hears an entry of a menu, ticked or not where it can be, and out of use where it is
    semantics := Semantics(if (item.on.nonEmpty || item.checked.nonEmpty) "menuitemcheckbox" else "menuitem",
      label = item.label, checked = if (item.on.nonEmpty || item.checked.nonEmpty) Some(item.ticked) else None)
    disabled @= !item.enabled
    size.height := words.size.height() + points * 0.9
    backgroundColor := MenuRow.fill(focused(), item.active)
    // the current choice ringed in the accent, its words in it too: plainer than a mark before it
    if (item.active) {
      borderWidth @= MenuRow.ring
      borderColor := Theme.accent()
    }
    radius @= points * 0.35
    lead.foreach { l =>
      l.color := ink()
      Mirror.place(l, () => size.width(), () => pad)
      l.position.top := (size.height() - l.size.height()) / 2.0
    }
    Mirror.place(words, () => size.width(), () => pad + slot)
    words.position.top := (size.height() - words.size.height()) / 2.0
    trail.foreach { t =>
      Mirror.place(t, () => size.width(), () => size.width() - pad - t.size.width())
      t.position.top := (size.height() - t.size.height()) / 2.0
    }
    children ++= lead.toList ++ List(words) ++ trail.toList
    if (item.enabled) {
      event.pointer.enter.attach(_ => focus())
      event.click.attach { e =>
        e.consume()
        chosen @= (())
      }
      event.key.down.attach { k =>
        if (k.key == MenuRow.inward && item.submenu.nonEmpty) opened @= (())
        else if (k.key == Key.Space) chosen @= (())
      }
    }
  }

  /** Right opens its submenu, where it has one; Space chooses it, as Enter does (one that keeps the menu open is ticked
    * where it is). */
  override def keepsKey(key: Key): Boolean =
    (key == MenuRow.inward && item.submenu.nonEmpty) || (key == Key.Space && item.enabled)

  override def kind: String = "MenuRow(" + item.label + ")"
}

object MenuRow {
  /** The arrow into a submenu: the way the page reads on (Right, or Left in a right-to-left language). */
  def inward: Key = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight

  /** How thick the accent ring round the current choice is (`MenuItem.active`). */
  val ring: Double = 1.5

  /** A row's ground: lit under the focus; half lit for the menu's current choice (`MenuItem.active`) while the focus is
    * elsewhere, so that the focus is always the plainest to see; clear otherwise. */
  def fill(focused: Boolean, active: Boolean): Color =
    if (focused) Theme.controlLit()
    else if (active) {
      val lit = Theme.controlLit()
      lit.withAlpha(lit.alpha * 0.5)
    }
    else Color.Clear
}
