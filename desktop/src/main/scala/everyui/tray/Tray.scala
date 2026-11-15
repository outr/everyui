package everyui.tray

import java.util.concurrent.atomic.AtomicInteger

/** The desktop's tray (the status icons at a panel's edge, the menu bar's on macOS, the notification area on
  * Windows), for an app that goes on in the background: a status, a menu of live lines, a popover of its own
  * components. On Linux the icon is a StatusNotifierItem and its menu a DBusMenu, spoken over the session bus
  * (`DBusConnection`), which KDE, COSMIC, XFCE and others draw; GNOME only with its AppIndicator extension. On macOS it
  * is an NSStatusItem with an NSMenu (`MacTray`), and on Windows a `Shell_NotifyIconW` icon with a popup menu
  * (`WinTray`), both reached through the FFM API, on the JVM and in a native image alike (run with
  * `--enable-native-access=ALL-UNNAMED`). AWT's tray is not used: under Wayland it is broken, and a native image
  * leaves it out.
  *
  * {{{
  *   if (Tray.supported) Tray.show(TrayImage.png(logo), "Voidcraft client").foreach { t =>
  *     t.badge := Some(if (connected()) Color.Green else Color.Grey)
  *     t.menu := List(MenuItem("CPU " + cpu() + "% · Mem " + mem() + "%", enabled = false), MenuItem.separator,
  *       MenuItem("Stop", () => client.stop()))
  *     t.popover @= Some(() => new MachineCard(machine))
  *   }
  *   else Tray.notify("Voidcraft client", "Running in the background")
  * }}}
  *
  * Its calls come and its menu's choices run on the JavaFX thread, the app's. Launched with `--headless=1`, the app
  * has no window: the tray is all of it. */
object Tray {
  private val made = new AtomicInteger(0)
  /** Where the bus is, in place of the session's (a spec's own); None for the session's. */
  @volatile private[tray] var busPath: Option[String] = None
  // calls to the app, on its thread
  private def deliver(f: () => Unit): Unit = javafx.application.Platform.runLater(() => f())

  private def connect(): Option[DBusConnection] =
    busPath.map(p => DBusConnection.open(p, deliver)).getOrElse(DBusConnection.session(deliver))

  // one connection for what is asked of the bus and for notifications, made as it is first wanted
  private var shared: Option[DBusConnection] = None
  private def bus: Option[DBusConnection] = {
    if (!shared.exists(_.isOpen)) {
      shared = connect()
      shared.foreach(listen)
    }
    shared
  }

  /** Whether a tray is there to show an icon in: on Linux, a StatusNotifierWatcher on the session bus (KDE's panel,
    * COSMIC's, XFCE's, GNOME's AppIndicator extension), false on GNOME without it and with no session bus; the menu
    * bar on macOS (`MacTray`); the notification area on Windows (`WinTray`). Where there is none, an app says what it
    * would have shown with `notify`. */
  def supported: Boolean = platform match {
    case TrayPlatform.Mac => MacTray.supported
    case TrayPlatform.Windows => WinTray.supported
    case TrayPlatform.Linux => bus.exists(b => owned(b, TrayIcon.Watcher))
  }

  // the platform's own tray; a spec's bus (`busPath`) is Linux's wherever it runs
  private def platform: TrayPlatform = if (busPath.nonEmpty) TrayPlatform.Linux else TrayPlatform.current

  private def owned(b: DBusConnection, name: String): Boolean =
    b.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "NameHasOwner", "s", List(name))
      .flatMap(_.body.headOption).contains(true)

  /** `icon` in the tray, `tooltip` said under the app's name as a pointer rests on it; None where it cannot be shown
    * (`supported`). The app goes on with its windows closed while it is there. */
  def show(icon: TrayImage, tooltip: String): Option[TrayIcon] = if (!supported) None else {
    val app = everyui.render.Host.app
    val shown = platform match {
      case TrayPlatform.Mac => Some(new TrayIcon(app, icon, tooltip, app, MacTray.host))
      case TrayPlatform.Windows => Some(new TrayIcon(app, icon, tooltip, app, WinTray.host))
      case TrayPlatform.Linux => connect().map { c =>
        val name = "org.kde.StatusNotifierItem-" + ProcessHandle.current().pid() + "-" + made.incrementAndGet()
        c.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "RequestName", "su", List(name, 4))
        val t = new TrayIcon(name, icon, tooltip, app, i => new SniTray(c, name, i, app))
        t.platform match {
          case s: SniTray => s.register()
          case _ => ()
        }
        t
      }
    }
    if (shown.nonEmpty) javafx.application.Platform.setImplicitExit(false)
    shown
  }

  // the notifications shown, by their id, with what each action key does
  private var actions: Map[Int, Map[String, () => Unit]] = Map.empty

  private def listen(b: DBusConnection): Unit = {
    b.addMatch("type='signal',interface='" + Tray.Notifications + "'")
    b.onSignal { m =>
      if (m.interface == Tray.Notifications && m.member == "ActionInvoked") {
        val id = m.body.head.asInstanceOf[Int]
        actions.get(id).flatMap(_.get(m.body(1).toString)).foreach(_())
      }
      else if (m.interface == Tray.Notifications && m.member == "NotificationClosed")
        actions = actions - m.body.head.asInstanceOf[Int]
    }
  }

  /** A notification from the app: `title` over `body`, with a button for each of `actions` (its words, and what
    * choosing it does), and `clicked` run where the notification itself is clicked. On Linux, through the desktop's
    * notification server (any Linux desktop's, GNOME's included); on Windows, a balloon from the notification area,
    * which Windows 10 and 11 show as a toast, with no buttons (`actions` are left out); on macOS, through the
    * notification centre where the app runs from a signed .app bundle. Nothing where there is no way to show one. */
  def notify(title: String, body: String, actions: List[(String, () => Unit)] = Nil,
             clicked: Option[() => Unit] = None): Unit = platform match {
    case TrayPlatform.Mac => MacTray.notify(title, body, actions, clicked)
    case TrayPlatform.Windows => WinTray.notify(title, body, clicked)
    case TrayPlatform.Linux => notifyOverBus(title, body, actions, clicked)
  }

  private def notifyOverBus(title: String, body: String, actions: List[(String, () => Unit)],
                            clicked: Option[() => Unit]): Unit = bus.foreach { b =>
    val keyed = actions.zipWithIndex.map { case ((label, run), i) => ("a" + i, label, run) }
    val pairs = (clicked.map(_ => List("default", "")).getOrElse(Nil) ++ keyed.flatMap(k => List(k._1, k._2))).toVector
    val runs = keyed.map(k => k._1 -> k._3).toMap ++ clicked.map(c => "default" -> c).toMap
    b.call(Tray.Notifications, "/org/freedesktop/Notifications", Tray.Notifications, "Notify", "susssasa{sv}i",
      List(everyui.render.Host.app, 0, "", title, body, pairs, Vector.empty[(String, DBusVariant)], -1)) { reply =>
      reply.body.headOption.foreach(id => if (runs.nonEmpty) Tray.this.actions =
        Tray.this.actions + (id.asInstanceOf[Int] -> runs))
    }
  }

  // whether notification `id` is one with actions waiting to be chosen
  private[tray] def knows(id: Int): Boolean = actions.contains(id)

  val Notifications: String = "org.freedesktop.Notifications"
}
