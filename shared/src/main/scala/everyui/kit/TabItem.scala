package everyui.kit

import everyui.component.*
import everyui.Color
import everyui.chat.{Avatar, Participant}
import everyui.component.types.{FontWeight, Semantics}
import everyui.event.Key
import everyui.font.MaterialIcon
import everyui.layout.HorizontalLayout
import everyui.menu.{Menu, MenuItem}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** One tab of a `TabBar`: its label, in the accent and underlined while it is the one shown, lit while focused. A
  * click shows it, and so does the focus landing on it where the bar says (`showOnFocus`: a remote moving along).
  *
  * Around the label are slots an app fills: `icon` before it (a file's type), `marker` after it (an unsaved file's
  * dot, a count); and a closable tab ends in a close button, which asks the bar to close it (`TabBar.closing`) rather
  * than showing it, as a click on it with the middle button does.
  *
  * A `lead` goes first of all (`TabLead`): somebody's avatar, which turns a ring while the tab is `busy` (an agent at
  * work in it) and says `busyDetail` to a pointer resting on it and to a screen reader; or an icon. The avatar keeps
  * the ring's room whether or not it is busy, so the label does not move as it starts and stops. The lead can change
  * after the tab is made (`lead @= TabLead.Person(renamed)`), as the label can: to one of the same kind, the face or
  * icon shown follows it in place, the tab as wide as it was; to another kind, its view is made afresh, and the tab
  * widens or narrows by the difference (an avatar's room is 20 and its ring's outset each side, an icon 18; no lead
  * none, nor the gap after it).
  *
  * `menu` is the tab's context menu (`TabBar.tabMenu`): the secondary button opens it at the pointer, keeping the
  * browser's own away only where there is one; the menu key, Shift+F10 and OK held (a remote's long press) drop it from
  * the tab. A tab is read as a tab (`Semantics("tab")`), by its words. */
class TabItem(val key: String, title: String, current: Var[String], closable: Boolean = false,
              close: String => Unit = _ => (), showOnFocus: Boolean = true, firstLead: TabLead = TabLead.None,
              menu: String => List[MenuItem] = _ => Nil) extends Container {
  val label: Var[String] = Var(title)
  /** Whether what the tab shows is at work: a ring turns around its avatar, where it is led by one. */
  val busy: Var[Boolean] = Var(false)
  /** What it is doing while busy ("Running bash"), said by the avatar. */
  val busyDetail: Var[String] = Var("")
  val icon: Var[Option[Component]] = Var(None)
  val marker: Var[Option[Component]] = Var(None)

  private def selected: Boolean = current() == key
  /** What leads the label (`TabLead`), changeable as the label is: a renamed persona's face, a new portrait. */
  val lead: Var[TabLead] = own(Var(firstLead))
  // who the avatar shows and which icon: the lead's view follows them in place while the lead keeps its kind
  private val who: Var[Participant] = Var(Participant("", ""))
  private val glyph: Var[MaterialIcon] = Var(MaterialIcon.Empty)
  private val leads: Var[Option[Component]] = Var(None)
  private var ledBy: String = "none"

  /** What leads the label now: the avatar in the ring's room, or the icon. */
  def leadView: Option[Component] = leads()

  private def made(kind: String): Option[Component] = kind match {
    case "person" =>
      val face = new Avatar(who(), TabItem.face)
      face.busy := busy()
      face.busyDetail := busyDetail()
      val room = BusyRing.outset(TabItem.face)
      face.position.left @= room
      face.position.top @= room
      Some(new Container {
        size.width @= TabItem.face + room * 2.0
        size.height @= TabItem.face + room * 2.0
        children += face
      })
    case "icon" =>
      val view = new MaterialIconView(glyph(), 18.0) {
        color := (if (selected) Theme.accent() else Theme.muted())
      }
      view.icon := glyph()
      Some(view)
    case _ => None
  }

  // a person for a person, an icon for an icon, is the same view showing the new one; another kind is made afresh
  // and the one it replaces taken apart, once it is off the row
  private def relead(l: TabLead): Unit = {
    val kind = l match {
      case TabLead.Person(p) =>
        who @= p
        "person"
      case TabLead.Icon(i) =>
        glyph @= i
        "icon"
      case _ => "none"
    }
    if (kind != ledBy) {
      ledBy = kind
      val old = leads()
      leads @= made(kind)
      old.foreach(_.dispose())
    }
  }
  relead(lead())
  lead.attach(relead)

  private val text = new TextView {
    content := label()
    font.size @= 16.0
    font.weight := (if (selected) FontWeight.Bold else FontWeight.SemiBold)
    color := (if (selected) Theme.accent() else if (TabItem.this.focusShown()) Theme.text() else Theme.muted())
  }
  /** The close button, on a closable tab. */
  val closeButton: Option[MaterialIconView] = if (!closable) None else Some(new MaterialIconView(MaterialIcon.Close, 16.0) {
    color := Theme.muted()
    tapSlop @= true
    event.click.attach { e =>
      e.consume()
      close(key)
    }
  })
  private val row = new Container {
    layout @= Some(HorizontalLayout(6.0, middle = true))
    children := leads().toVector ++ icon().toVector ++ Vector(text) ++ marker().toVector ++ closeButton.toVector
  }
  private val underline = new Container {
    size.height @= 3.0
    backgroundColor := (if (selected) Theme.accent() else Color.Clear)
  }

  focusable @= true
  size.width := row.size.width() + TabItem.padH * 2.0
  size.height @= TabBar.height
  backgroundColor := (if (focusShown()) Theme.controlLit() else Color.Clear)
  row.position.left @= TabItem.padH
  row.position.top := (TabBar.height - row.size.height()) / 2.0
  underline.size.width := size.width()
  underline.position.top @= TabBar.height - 3.0
  children ++= List(row, underline)
  // on a turn of its own: the focus is still settling, and what the tab shows may be a few dozen components
  if (showOnFocus) focused.attach(on => if (on && !selected) Platform.later(() => current @= key))
  event.click.attach { e =>
    e.consume()
    current @= key
  }
  // the middle button closes a closable tab, as a browser's does
  if (closable) event.pointer.middle.attach { e =>
    e.consume()
    close(key)
  }
  semantics @= Semantics("tab")
  event.pointer.menu.attach { e =>
    val items = menu(key)
    if (items.nonEmpty) {
      e.consume()
      if (Platform.remoteNavigation()) Menu.show(items, label()) else Menu.at(e.x, e.y, items)
    }
  }
  event.hold.attach(_ => openMenu())
  event.key.down.attach(k => if (k.key == Key.ContextMenu || (k.key == Key.Other && k.name == "F10" && k.shift)) openMenu())

  /** Its menu, dropped from it, where it has one. */
  def openMenu(): Unit = {
    val items = menu(key)
    if (items.nonEmpty) {
      if (Platform.remoteNavigation()) Menu.show(items, label()) else Menu.below(this, items)
    }
  }
}

object TabItem {
  val padH: Double = 18.0
  /** How big a lead avatar is. */
  val face: Double = 20.0
}
