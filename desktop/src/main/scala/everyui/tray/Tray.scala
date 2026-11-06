package everyui.tray

import java.util.concurrent.atomic.AtomicInteger

/** The desktop's tray (the status icons at a panel's edge), for an app that goes on in the background: a status, a
  * menu of live lines, a popover of its own components. On Linux the icon is a StatusNotifierItem and its menu a
  * DBusMenu, spoken over the session bus (`DBusConnection`), which KDE, COSMIC, XFCE and others draw; GNOME only with
  * its AppIndicator extension. AWT's tray is not used: under Wayland it is broken, and a native image leaves it out.
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

  /** Whether a tray is there to show an icon in: a StatusNotifierWatcher on the session bus (KDE's panel, COSMIC's,
    * XFCE's, GNOME's AppIndicator extension). False on GNOME without it, off Linux for now, and with no session bus:
    * an app then says what it would have shown with `notify`. */
  def supported: Boolean = bus.exists(b => owned(b, TrayIcon.Watcher))

  private def owned(b: DBusConnection, name: String): Boolean =
    b.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "NameHasOwner", "s", List(name))
      .flatMap(_.body.headOption).contains(true)

  /** `icon` in the tray, `tooltip` said under the app's name as a pointer rests on it; None where it cannot be shown
    * (`supported`). The app goes on with its windows closed while it is there. */
  def show(icon: TrayImage, tooltip: String): Option[TrayIcon] = if (!supported) None else connect().map { c =>
    val name = "org.kde.StatusNotifierItem-" + ProcessHandle.current().pid() + "-" + made.incrementAndGet()
    c.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "RequestName", "su", List(name, 4))
    javafx.application.Platform.setImplicitExit(false)
    val t = new TrayIcon(c, name, icon, tooltip, everyui.render.Host.app)
    t.register()
    t
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

  /** A notification from the app, through the desktop's notification server (any Linux desktop's, GNOME's included):
    * `title` over `body`, with a button for each of `actions` (its words, and what choosing it does), and `clicked`
    * run where the notification itself is clicked. Nothing where there is no server. */
  def notify(title: String, body: String, actions: List[(String, () => Unit)] = Nil,
             clicked: Option[() => Unit] = None): Unit = bus.foreach { b =>
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
