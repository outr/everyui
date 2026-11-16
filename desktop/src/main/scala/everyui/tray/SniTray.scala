package everyui.tray

import everyui.Color
import everyui.menu.MenuItem

/** A `TrayIcon` on Linux: a StatusNotifierItem served on its own D-Bus connection under `name`, its menu a DBusMenu
  * (`TrayMenu`), which KDE, COSMIC, XFCE and others draw, GNOME with its AppIndicator extension. Registered with the
  * panel's watcher, and again each time a new one starts (the panel restarted). */
private[tray] class SniTray(bus: DBusConnection, name: String, icon: TrayIcon, appTitle: String) extends TrayHost {
  private val menuServed = new TrayMenu(bus, everyui.render.Host.rtl)
  private var shown: TrayImage = icon.icon()
  private var badge: Option[Color] = None
  private var title: String = icon.title()
  private var tooltip: String = icon.tooltip()
  private var wantsAttention = false
  private var gone = false

  // every size of the picture, the dot drawn on each: the panel picks the one it wants
  private def pixmaps: Vector[DBusStruct] = {
    val p = badge.map(c => shown.badged(c)).getOrElse(shown)
    p.sizes.map(s => DBusStruct(List(s.width, s.height, s.bytes))).toVector
  }
  private def status: String = if (wantsAttention) "NeedsAttention" else "Active"

  private def property(n: String): Option[DBusVariant] = n match {
    case "Category" => Some(DBusVariant("s", "ApplicationStatus"))
    case "Id" => Some(DBusVariant("s", appTitle))
    case "Title" => Some(DBusVariant("s", title))
    case "Status" => Some(DBusVariant("s", status))
    case "WindowId" => Some(DBusVariant("i", 0))
    case "IconThemePath" => Some(DBusVariant("s", ""))
    case "IconName" => Some(DBusVariant("s", ""))
    case "IconPixmap" => Some(DBusVariant("a(iiay)", pixmaps))
    case "AttentionIconName" => Some(DBusVariant("s", ""))
    case "AttentionIconPixmap" => Some(DBusVariant("a(iiay)", pixmaps))
    case "OverlayIconName" => Some(DBusVariant("s", ""))
    case "OverlayIconPixmap" => Some(DBusVariant("a(iiay)", Vector.empty[DBusStruct]))
    case "AttentionMovieName" => Some(DBusVariant("s", ""))
    case "ToolTip" => Some(DBusVariant("(sa(iiay)ss)",
      DBusStruct(List("", Vector.empty[DBusStruct], title, tooltip))))
    case "ItemIsMenu" => Some(DBusVariant("b", false))
    case "Menu" => Some(DBusVariant("o", DBusPath(TrayMenu.Path)))
    case _ => None
  }

  private def answer(m: DBusMessage): Option[(String, List[Any])] = (m.interface, m.member) match {
    case (TrayIcon.Interface, "Activate") =>
      icon.clicked(m.body.head.asInstanceOf[Int], m.body(1).asInstanceOf[Int], panelPoint = true)
      Some(("", Nil))
    case (TrayIcon.Interface, "SecondaryActivate" | "ContextMenu" | "Scroll" | "ProvideXdgActivationToken") =>
      Some(("", Nil))
    case (TrayMenu.Properties, "Get") => property(m.body(1).toString).map(v => ("v", List(v)))
    case (TrayMenu.Properties, "GetAll") =>
      Some(("a{sv}", List(TrayIcon.properties.flatMap(n => property(n).map(n -> _)).toVector)))
    case (TrayMenu.Introspectable, "Introspect") => Some(("s", List(TrayIcon.introspection)))
    case _ => None
  }

  bus.serve(TrayIcon.Path)(answer)
  bus.serve(TrayMenu.Path)(menuServed.answer)

  // the panel's watcher, told of the icon now and whenever a new one takes the name (the panel started again)
  bus.addMatch("type='signal',sender='org.freedesktop.DBus',interface='org.freedesktop.DBus'," +
    "member='NameOwnerChanged',arg0='" + TrayIcon.Watcher + "'")
  bus.onSignal { m =>
    if (m.member == "NameOwnerChanged" && m.body.headOption.contains(TrayIcon.Watcher) &&
      m.body.lift(2).exists(_.toString.nonEmpty) && !gone) register()
  }

  private def signal(member: String): Unit = if (!gone) bus.signal(TrayIcon.Path, TrayIcon.Interface, member)

  /** Told to the panel's watcher, where there is one. */
  def register(): Unit =
    bus.call(TrayIcon.Watcher, TrayIcon.WatcherPath, TrayIcon.Watcher, "RegisterStatusNotifierItem", "s", List(name))()

  def picture(i: TrayImage, b: Option[Color]): Unit = {
    shown = i
    badge = b
    signal("NewIcon")
  }

  def tip(t: String, tt: String): Unit = {
    val titled = t != title
    title = t
    tooltip = tt
    if (titled) signal("NewTitle")
    signal("NewToolTip")
  }

  def attention(on: Boolean): Unit = if (on != wantsAttention) {
    wantsAttention = on
    if (!gone) bus.signal(TrayIcon.Path, TrayIcon.Interface, "NewStatus", "s", List(status))
  }

  def menu(items: List[MenuItem]): Unit = menuServed.set(items)

  // (the panel opens the menu on a right click, and asks for Activate on a left one, whatever the popover)
  def popover(on: Boolean): Unit = ()

  // the panel says where the icon is only as it is clicked
  def locate(found: Option[(Int, Int)] => Unit): Unit = found(None)

  def close(): Unit = if (!gone) {
    gone = true
    bus.close()
  }
}
