package everyui.tray

import everyui.Color
import everyui.component.Component
import everyui.menu.MenuItem
import reactify.*

/** An icon in the desktop's tray (`Tray.show`): what it shows (`icon`, with a dot of `badge` at its corner), what a
  * pointer resting on it says (`title` over `tooltip`), whether it asks to be looked at (`attention`), and its menu of
  * `menu`. A left click opens `popover`, where there is one, beside the icon (`TrayPopover`; `openPopover` opens it
  * from code), and tells `activated` where it was; a right click opens the menu (on macOS, with no popover, any click
  * does). Each is a Var: set it as things change (a status, a line of live numbers), and the tray is told. Shown by
  * each platform's own (`TrayHost`): a StatusNotifierItem over D-Bus on Linux, served under `name`; an NSStatusItem on
  * macOS; a notification-area icon on Windows. */
class TrayIcon private[tray] (val name: String, first: TrayImage, firstTip: String, appTitle: String,
                             host: TrayIcon => TrayHost) {
  val icon: Var[TrayImage] = Var(first)
  val badge: Var[Option[Color]] = Var(None)
  val title: Var[String] = Var(appTitle)
  val tooltip: Var[String] = Var(firstTip)
  val attention: Var[Boolean] = Var(false)
  val menu: Var[List[MenuItem]] = Var(Nil)
  /** What a left click opens beside the icon, made afresh each time it opens; None, nothing opens. */
  val popover: Var[Option[() => Component]] = Var(None)
  /** A left click on the icon, where the tray says it was, in the screen's pixels. */
  val activated: Channel[(Int, Int)] = Channel[(Int, Int)]

  private val shown = new TrayPopover
  private var gone = false
  // where the last click was, as `clicked` was told it, for `openPopover` where the platform cannot say where the icon is
  private var lastClick: Option[(Int, Int, Boolean)] = None
  private[tray] val platform: TrayHost = host(this)

  platform.picture(icon(), badge())
  platform.tip(title(), tooltip())
  platform.popover(popover().nonEmpty)
  icon.attach(i => if (!gone) platform.picture(i, badge()))
  badge.attach(b => if (!gone) platform.picture(icon(), b))
  title.attach(t => if (!gone) platform.tip(t, tooltip()))
  tooltip.attach(t => if (!gone) platform.tip(title(), t))
  attention.attach(on => if (!gone) platform.attention(on))
  menu.attach(items => if (!gone) platform.menu(items))
  popover.attach(p => if (!gone) platform.popover(p.nonEmpty))

  /** A left click at `x`, `y`, as the platform tells it, on the app's thread: `activated` told, and the popover opened
    * or shut. The point is in JavaFX's screen coordinates, unless it is a Linux panel's (`panelPoint`), which Plasma
    * under Wayland says its own way (`TrayScreen.locate`). */
  private[tray] def clicked(x: Int, y: Int, panelPoint: Boolean = false): Unit = if (!gone) {
    val plasma = panelPoint && TrayScreen.plasmaWayland
    lastClick = Some((x, y, plasma))
    activated @= (x, y)
    popover().foreach(make => shown.toggle(make, x, y, plasma))
  }

  /** The icon taken out of the tray, for good. */
  def hide(): Unit = if (!gone) {
    gone = true
    shown.close()
    platform.close()
  }

  /** The popover opened beside the icon, as a left click opens it, where there is one and it is not open already: for
    * an app to open it from code (a shortcut, a first run, a run traced for a native image's metadata). It is put where
    * the platform says the icon is (macOS, Windows); where it cannot (a Linux panel says so only with a click), where
    * the last click was, or by the main screen's corner before any. `activated` is not told. */
  def openPopover(): Unit = if (!gone && !shown.showing && popover().nonEmpty) platform.locate { at =>
    if (!gone) popover().foreach { make =>
      val (x, y, plasma) = at.map { case (x, y) => (x, y, false) }.orElse(lastClick).getOrElse(corner)
      shown.open(make, x, y, plasma)
    }
  }

  // the corner of the main screen's free area where the tray's icons are as a rule: the bottom right on Windows, the
  // top right elsewhere (macOS's menu bar, GNOME's top bar)
  private def corner: (Int, Int, Boolean) = {
    val area = javafx.stage.Screen.getPrimary.getVisualBounds
    val bottom = TrayPlatform.current == TrayPlatform.Windows
    (area.getMaxX.toInt, (if (bottom) area.getMaxY else area.getMinY).toInt, false)
  }

  /** The popover shut, where it is open. */
  def closePopover(): Unit = shown.close()

  /** Whether the popover is open. */
  def popoverShown: Boolean = shown.showing

  /** Whether it is in the tray still (not `hide`n). */
  def isShown: Boolean = !gone

  private[tray] def popoverScene: Option[javafx.scene.Scene] = shown.scene
}

object TrayIcon {
  val Path: String = "/StatusNotifierItem"
  val Interface: String = "org.kde.StatusNotifierItem"
  val Watcher: String = "org.kde.StatusNotifierWatcher"
  val WatcherPath: String = "/StatusNotifierWatcher"
  private[tray] val properties: List[String] = List("Category", "Id", "Title", "Status", "WindowId", "IconThemePath",
    "IconName", "IconPixmap", "AttentionIconName", "AttentionIconPixmap", "OverlayIconName", "OverlayIconPixmap",
    "AttentionMovieName", "ToolTip", "ItemIsMenu", "Menu")

  private[tray] val introspection: String =
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
