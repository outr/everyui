package everyui.tray

import everyui.Color
import everyui.component.Component
import everyui.menu.MenuItem
import reactify.*

/** An icon in the desktop's tray (`Tray.show`), served on its own D-Bus connection as a StatusNotifierItem under
  * `name`: what it shows (`icon`, with a dot of `badge` at its corner), what a pointer resting on it says (`title`
  * over `tooltip`), whether it asks to be looked at (`attention`), and the panel's menu of `menu` (`TrayMenu`) on a
  * right click. A left click opens `popover`, where there is one, beside the icon (`TrayPopover`), and tells
  * `activated` where it was. Each is a Var: set it as things change (a status, a line of live numbers), and the panel
  * is told. Registered with the panel's watcher, and again each time a new one starts (the panel restarted). */
class TrayIcon private[tray] (bus: DBusConnection, val name: String, first: TrayImage, firstTip: String,
                             appTitle: String) {
  val icon: Var[TrayImage] = Var(first)
  val badge: Var[Option[Color]] = Var(None)
  val title: Var[String] = Var(appTitle)
  val tooltip: Var[String] = Var(firstTip)
  val attention: Var[Boolean] = Var(false)
  val menu: Var[List[MenuItem]] = Var(Nil)
  /** What a left click opens beside the icon, made afresh each time it opens; None, nothing opens. */
  val popover: Var[Option[() => Component]] = Var(None)
  /** A left click on the icon, where the panel says it was, in the screen's pixels. */
  val activated: Channel[(Int, Int)] = Channel[(Int, Int)]

  private val menuServed = new TrayMenu(bus, everyui.render.Host.rtl)
  private val shown = new TrayPopover
  private var gone = false

  private def picture: TrayImage = badge().map(c => icon().badged(c)).getOrElse(icon())
  private def pixmaps: Vector[DBusStruct] = {
    val p = picture
    Vector(DBusStruct(List(p.width, p.height, p.bytes)))
  }
  private def status: String = if (attention()) "NeedsAttention" else "Active"

  private def property(n: String): Option[DBusVariant] = n match {
    case "Category" => Some(DBusVariant("s", "ApplicationStatus"))
    case "Id" => Some(DBusVariant("s", appTitle))
    case "Title" => Some(DBusVariant("s", title()))
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
      DBusStruct(List("", Vector.empty[DBusStruct], title(), tooltip()))))
    case "ItemIsMenu" => Some(DBusVariant("b", false))
    case "Menu" => Some(DBusVariant("o", DBusPath(TrayMenu.Path)))
    case _ => None
  }

  private def answer(m: DBusMessage): Option[(String, List[Any])] = (m.interface, m.member) match {
    case (TrayIcon.Interface, "Activate") =>
      val (x, y) = (m.body.head.asInstanceOf[Int], m.body(1).asInstanceOf[Int])
      activated @= (x, y)
      popover().foreach(make => shown.toggle(make, x, y))
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
  icon.attach(_ => signal("NewIcon"))
  badge.attach(_ => signal("NewIcon"))
  title.attach(_ => {
    signal("NewTitle")
    signal("NewToolTip")
  })
  tooltip.attach(_ => signal("NewToolTip"))
  attention.attach(_ => bus.signal(TrayIcon.Path, TrayIcon.Interface, "NewStatus", "s", List(status)))
  menu.attach(items => menuServed.set(items))

  // the panel's watcher, told of the icon now and whenever a new one takes the name (the panel started again)
  bus.addMatch("type='signal',sender='org.freedesktop.DBus',interface='org.freedesktop.DBus'," +
    "member='NameOwnerChanged',arg0='" + TrayIcon.Watcher + "'")
  bus.onSignal { m =>
    if (m.member == "NameOwnerChanged" && m.body.headOption.contains(TrayIcon.Watcher) &&
      m.body.lift(2).exists(_.toString.nonEmpty) && !gone) register()
  }

  private def signal(member: String): Unit = if (!gone) bus.signal(TrayIcon.Path, TrayIcon.Interface, member)

  /** Told to the panel's watcher, where there is one. */
  private[tray] def register(): Unit =
    bus.call(TrayIcon.Watcher, TrayIcon.WatcherPath, TrayIcon.Watcher, "RegisterStatusNotifierItem", "s", List(name))()

  /** The icon taken out of the tray, for good. */
  def hide(): Unit = if (!gone) {
    gone = true
    shown.close()
    bus.close()
  }

  /** The popover shut, where it is open. */
  def closePopover(): Unit = shown.close()

  /** Whether it is in the tray still (not `hide`n). */
  def isShown: Boolean = !gone

  private[tray] def popoverScene: Option[javafx.scene.Scene] = shown.scene
}

object TrayIcon {
  val Path: String = "/StatusNotifierItem"
  val Interface: String = "org.kde.StatusNotifierItem"
  val Watcher: String = "org.kde.StatusNotifierWatcher"
  val WatcherPath: String = "/StatusNotifierWatcher"
  private val properties: List[String] = List("Category", "Id", "Title", "Status", "WindowId", "IconThemePath",
    "IconName", "IconPixmap", "AttentionIconName", "AttentionIconPixmap", "OverlayIconName", "OverlayIconPixmap",
    "AttentionMovieName", "ToolTip", "ItemIsMenu", "Menu")

  private val introspection: String =
    """<!DOCTYPE node PUBLIC "-//freedesktop//DTD D-BUS Object Introspection 1.0//EN"
      | "http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd">
      |<node>
      | <interface name="org.kde.StatusNotifierItem">
      |  <property name="Category" type="s" access="read"/>
      |  <property name="Id" type="s" access="read"/>
      |  <property name="Title" type="s" access="read"/>
      |  <property name="Status" type="s" access="read"/>
      |  <property name="WindowId" type="i" access="read"/>
      |  <property name="IconThemePath" type="s" access="read"/>
      |  <property name="IconName" type="s" access="read"/>
      |  <property name="IconPixmap" type="a(iiay)" access="read"/>
      |  <property name="AttentionIconName" type="s" access="read"/>
      |  <property name="AttentionIconPixmap" type="a(iiay)" access="read"/>
      |  <property name="OverlayIconName" type="s" access="read"/>
      |  <property name="OverlayIconPixmap" type="a(iiay)" access="read"/>
      |  <property name="AttentionMovieName" type="s" access="read"/>
      |  <property name="ToolTip" type="(sa(iiay)ss)" access="read"/>
      |  <property name="ItemIsMenu" type="b" access="read"/>
      |  <property name="Menu" type="o" access="read"/>
      |  <method name="ContextMenu"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/>
      |  </method>
      |  <method name="Activate"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/></method>
      |  <method name="SecondaryActivate"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/>
      |  </method>
      |  <method name="Scroll"><arg name="delta" type="i" direction="in"/>
      |   <arg name="orientation" type="s" direction="in"/>
      |  </method>
      |  <signal name="NewTitle"/>
      |  <signal name="NewIcon"/>
      |  <signal name="NewAttentionIcon"/>
      |  <signal name="NewOverlayIcon"/>
      |  <signal name="NewToolTip"/>
      |  <signal name="NewStatus"><arg name="status" type="s"/></signal>
      | </interface>
      |</node>""".stripMargin
}
