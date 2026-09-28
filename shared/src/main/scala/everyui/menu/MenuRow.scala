package everyui.menu

import everyui.Color
import everyui.component.*
import everyui.component.types.WhiteSpace
import everyui.event.Key
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.Mirror
import everyui.theme.Theme
import reactify.*

/** A row of a [[Menu]], `width` wide: a slot for the tick or the icon, the label (and its detail under it), and at the
  * right the shortcut, the note, or the way into a submenu. It lights under the focus, which a pointer resting on it
  * brings; `chosen` hears it clicked or entered, and `opened` the arrow into its submenu. A separator is a rule. */
class MenuRow(val item: MenuItem, width: => Double, points: Double) extends Container {
  /** A click, a tap or OK on it. */
  val chosen: Channel[Unit] = Channel[Unit]
  /** The arrow pressed into its submenu. */
  val opened: Channel[Unit] = Channel[Unit]

  private val pad: Double = points * 0.85
  private val slot: Double = points * 1.4
  private val ink: Val[Color] = own(Val(
    if (!item.enabled) Theme.muted().withAlpha(0.6) else if (item.danger) Theme.warning() else Theme.text()))

  private val lead: Option[MaterialIconView] =
    if (item.checked.contains(true)) Some(new MaterialIconView(MaterialIcon.Check, points * 1.15))
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
    size.height @= points * 0.9 + 1.0
    children += rule
  }
  else {
    focusable @= item.enabled
    size.height := words.size.height() + points * 0.9
    backgroundColor := (if (focused()) Theme.controlLit() else Color.Clear)
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
      event.key.down.attach(k => if (k.key == MenuRow.inward && item.submenu.nonEmpty) opened @= (()))
    }
  }

  /** Right opens its submenu, where it has one. */
  override def keepsKey(key: Key): Boolean = key == MenuRow.inward && item.submenu.nonEmpty

  override def kind: String = "MenuRow(" + item.label + ")"
}

object MenuRow {
  /** The arrow into a submenu: the way the page reads on (Right, or Left in a right-to-left language). */
  def inward: Key = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
}
