package everyui.tray

import everyui.{Color, FxSpec}
import everyui.menu.MenuItem
import reactify.*

import java.util.concurrent.{CompletableFuture, ConcurrentLinkedQueue, TimeUnit}

/** The tray on a bus of the spec's own, with a panel's watcher, a panel and a notification server played by
  * connections of the spec's: none without a watcher; an icon registered, read, badged and told to look at; its menu
  * laid out, chosen from, updated in place and laid out again; registered again as a new watcher starts; and a
  * notification's action run. Nothing reaches the desktop's own session. */
class TraySpec extends FxSpec {
  private lazy val bus = new PrivateBus
  private val registered = new ConcurrentLinkedQueue[String]()
  private val signals = new ConcurrentLinkedQueue[DBusMessage]()

  override def beforeAll(): Unit = {
    super.beforeAll()
    Tray.busPath = Some(bus.path)
  }

  override def afterAll(): Unit = {
    Tray.busPath = None
    bus.stop()
    super.afterAll()
  }

  private def own(c: DBusConnection, name: String): Unit =
    c.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "RequestName", "su", List(name, 4))

  // a panel's watcher: it takes the name, and keeps each item registered with it
  private def watcher(): DBusConnection = {
    val w = bus.connect()
    w.serve(TrayIcon.WatcherPath) { m =>
      if (m.member == "RegisterStatusNotifierItem") {
        registered.add(m.body.head.toString)
        Some(("", Nil))
      }
      else None
    }
    own(w, TrayIcon.Watcher)
    w
  }

  // a panel: it hears the items' and menus' signals
  private lazy val panel: DBusConnection = {
    val p = bus.connect()
    p.onSignal(m => signals.add(m))
    p.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "AddMatch", "s",
      List("type='signal',interface='org.kde.StatusNotifierItem'"))
    p.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "AddMatch", "s",
      List("type='signal',interface='com.canonical.dbusmenu'"))
    p
  }

  private def ask(name: String, path: String, interface: String, member: String, sig: String = "",
                  args: List[Any] = Nil): DBusMessage =
    panel.ask(name, path, interface, member, sig, args).getOrElse(fail("no answer to " + member))

  private def prop(name: String, p: String): Any =
    ask(name, TrayIcon.Path, TrayMenu.Properties, "Get", "ss", List(TrayIcon.Interface, p)).body.head
      .asInstanceOf[DBusVariant].value

  private def heard(member: String): Boolean =
    eventually(signals.toArray.exists(_.asInstanceOf[DBusMessage].member == member))

  "The tray" should {
    "not be supported with no panel's watcher on the bus" in {
      Tray.supported shouldBe false
      Tray.show(TrayImage.disc(Color.Red, 16), "nothing") shouldBe None
    }
    "register an icon with the watcher, and say what it shows" in {
      val w = watcher()
      try {
        Tray.supported shouldBe true
        val t = Tray.show(TrayImage.disc(Color.Red, 16), "All well").get
        try {
          eventually(registered.contains(t.name)) shouldBe true
          prop(t.name, "Status") shouldBe "Active"
          prop(t.name, "Menu") shouldBe DBusPath(TrayMenu.Path)
          val pixmaps = prop(t.name, "IconPixmap").asInstanceOf[Vector[DBusStruct]]
          pixmaps.head.fields.take(2) shouldBe List(16, 16)
          prop(t.name, "ToolTip").asInstanceOf[DBusStruct].fields.drop(2) shouldBe List(everyui.render.Host.app,
            "All well")
          signals.clear()
          fx(t.badge @= Some(Color.Green))
          heard("NewIcon") shouldBe true
          val badged = prop(t.name, "IconPixmap").asInstanceOf[Vector[DBusStruct]]
          badged.head.fields(2) should not be pixmaps.head.fields(2)
          fx(t.attention @= true)
          heard("NewStatus") shouldBe true
          prop(t.name, "Status") shouldBe "NeedsAttention"
        } finally t.hide()
      } finally w.close()
    }
    "serve its menu, run what is chosen, and tell the panel of each change" in {
      val w = watcher()
      try {
        val t = Tray.show(TrayImage.disc(Color.Red, 16), "menu").get
        try {
          val stopped = new CompletableFuture[Boolean]()
          val paused = Var(false)
          def entries(cpu: Int): List[MenuItem] = List(
            MenuItem("CPU " + cpu + "%", enabled = false),
            MenuItem.separator,
            MenuItem.toggle("Paused", paused, p => paused @= p),
            MenuItem.sub("More", List(MenuItem("Stop", () => stopped.complete(true)))))
          fx(t.menu @= entries(43))
          val layout = ask(t.name, TrayMenu.Path, TrayMenu.Interface, "GetLayout", "iias", List(0, -1, Vector.empty))
          val root = layout.body(1).asInstanceOf[DBusStruct]
          val kids = root.fields(2).asInstanceOf[Vector[DBusVariant]].map(_.value.asInstanceOf[DBusStruct])
          def label(s: DBusStruct): Any = s.fields(1).asInstanceOf[Vector[(String, DBusVariant)]].toMap
            .get("label").map(_.value).getOrElse("")
          kids.map(label) shouldBe Vector("CPU 43%", "", "Paused", "More")
          val sub = kids(3).fields(2).asInstanceOf[Vector[DBusVariant]].map(_.value.asInstanceOf[DBusStruct])
          sub.map(label) shouldBe Vector("Stop")
          // chosen from the submenu: its action runs
          ask(t.name, TrayMenu.Path, TrayMenu.Interface, "Event", "isvu",
            List(sub.head.fields.head, "clicked", DBusVariant("i", 0), 0))
          stopped.get(5, TimeUnit.SECONDS) shouldBe true
          // the live line changed: told as properties, where an open menu shows it
          signals.clear()
          fx(t.menu @= entries(51))
          heard("ItemsPropertiesUpdated") shouldBe true
          // a tick changed elsewhere: told as well
          signals.clear()
          fx(paused @= true)
          heard("ItemsPropertiesUpdated") shouldBe true
          // an entry more: a new layout
          signals.clear()
          fx(t.menu @= entries(51) :+ MenuItem("Quit"))
          heard("LayoutUpdated") shouldBe true
        } finally t.hide()
      } finally w.close()
    }
    "register again with a watcher that starts after it (the panel restarted)" in {
      val w = watcher()
      val t = Tray.show(TrayImage.disc(Color.Red, 16), "again").get
      try {
        eventually(registered.contains(t.name)) shouldBe true
        w.close()
        registered.clear()
        val again = watcher()
        try eventually(registered.contains(t.name)) shouldBe true
        finally again.close()
      } finally t.hide()
    }
    "show a notification, and run the action chosen on it" in {
      val server = bus.connect()
      try {
        val shown = new CompletableFuture[DBusMessage]()
        server.serve("/org/freedesktop/Notifications") { m =>
          if (m.member == "Notify") {
            shown.complete(m)
            Some(("u", List(7)))
          }
          else None
        }
        own(server, Tray.Notifications)
        val ran = new CompletableFuture[String]()
        fx(Tray.notify("Client stopped", "Start it again?", List("Start" -> (() => ran.complete("start")))))
        val m = shown.get(5, TimeUnit.SECONDS)
        (m.body(3), m.body(4), m.body(5)) shouldBe ("Client stopped", "Start it again?", Vector("a0", "Start"))
        // the action comes once the reply with its id has been heard
        eventually(Tray.knows(7)) shouldBe true
        server.signal("/org/freedesktop/Notifications", Tray.Notifications, "ActionInvoked", "us", List(7, "a0"))
        ran.get(5, TimeUnit.SECONDS) shouldBe "start"
      } finally server.close()
    }
  }
}
