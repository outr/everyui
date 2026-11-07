package everyui.tray

import java.io.File
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import scala.jdk.CollectionConverters.*

/** The tray demo (`--demo=tray`) checked from outside, as a panel sees it: on a bus of its own, the command given (the
  * app as a native image, or on the JVM under GraalVM's tracing agent) is started with that bus as its session's, and
  * this plays the panel's watcher and the panel. Its icon is to register and say its title, its menu to be laid out,
  * "Cycle status" and "Stop" to run when chosen ("Stop" then reading "Start"), and a left click on it to open its
  * popover, a window more on the display (`DISPLAY`, an Xvfb's). Each step is said PASS or FAIL; the exit code is 1
  * where any failed. What the app says goes to `log`.
  *
  * {{{
  *   TrayNativeCheck <log> <command> [args...]
  * }}} */
object TrayNativeCheck {
  private var failed = false
  private def step(name: String)(ok: => Boolean, detail: => String = ""): Unit = {
    val passed = try ok catch { case t: Throwable => println("  " + t); false }
    if (!passed) failed = true
    val said = (if (passed) "PASS" else "FAIL") + (if (detail.isEmpty) "" else "  " + detail)
    println(String.format("%-44s %s", name, said))
  }

  def main(args: Array[String]): Unit = {
    val log = new File(args(0))
    val bus = new PrivateBus
    val registered = new LinkedBlockingQueue[String]()
    val watcher = bus.connect()
    watcher.serve(TrayIcon.WatcherPath) { m =>
      if (m.member == "RegisterStatusNotifierItem") {
        registered.add(m.body.head.toString)
        Some(("", Nil))
      }
      else None
    }
    watcher.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "RequestName", "su",
      List(TrayIcon.Watcher, 4))
    val panel = bus.connect()
    val builder = new ProcessBuilder(args.drop(1)*).redirectErrorStream(true).redirectOutput(log)
    builder.environment().put("DBUS_SESSION_BUS_ADDRESS", "unix:path=" + bus.path)
    val app = builder.start()
    try {
      val item = Option(registered.poll(90, TimeUnit.SECONDS))
      step("tray: icon registered with the watcher")(item.nonEmpty, item.getOrElse(""))
      item.foreach { name =>
        def ask(path: String, interface: String, member: String, sig: String = "", body: List[Any] = Nil) =
          panel.ask(name, path, interface, member, sig, body).getOrElse(throw new IllegalStateException(member))
        val props = ask(TrayIcon.Path, TrayMenu.Properties, "GetAll", "s", List(TrayIcon.Interface))
          .body.head.asInstanceOf[Vector[(String, DBusVariant)]].toMap
        step("tray: its title and status read")(props.get("Title").exists(_.value.toString.nonEmpty),
          List("Title", "Status").map(p => props.get(p).map(_.value.toString).getOrElse("")).mkString(", "))
        def entries(): Vector[DBusStruct] = {
          val root = ask(TrayMenu.Path, TrayMenu.Interface, "GetLayout", "iias", List(0, -1, Vector.empty))
            .body(1).asInstanceOf[DBusStruct]
          root.fields(2).asInstanceOf[Vector[DBusVariant]].map(_.value.asInstanceOf[DBusStruct])
        }
        def label(s: DBusStruct): String = s.fields(1).asInstanceOf[Vector[(String, DBusVariant)]].toMap
          .get("label").map(_.value.toString).getOrElse("")
        def choose(text: String): Unit = entries().find(e => label(e) == text).foreach(e =>
          ask(TrayMenu.Path, TrayMenu.Interface, "Event", "isvu",
            List(e.fields.head, "clicked", DBusVariant("i", 0), 0)))
        def until(f: => Boolean): Boolean = {
          val end = System.currentTimeMillis() + 10000L
          var now = f
          while (!now && System.currentTimeMillis() < end) {
            Thread.sleep(100L)
            now = f
          }
          now
        }
        val first = entries().map(label)
        step("tray: its menu laid out")(first.contains("Cycle status") && first.contains("Quit"),
          first.filter(_.nonEmpty).mkString(" | "))
        choose("Cycle status")
        choose("Stop")
        step("tray: Stop chosen, the menu then saying Start")(until(entries().map(label).contains("Start")))
        val before = windows()
        ask(TrayIcon.Path, TrayIcon.Interface, "Activate", "ii", List(200, 200))
        step("tray: a left click opens its popover")(until(windows() > before), before + " windows, then " + windows())
        // and what it shows, where a picture of it is asked for (`TRAY_SHOT`)
        sys.env.get("TRAY_SHOT").foreach { shot =>
          Thread.sleep(1000L)
          new ProcessBuilder("magick", "import", "-window", "root", shot).start().waitFor()
        }
      }
      step("tray: the app still running")(app.isAlive)
    } finally {
      app.destroy()
      if (!app.waitFor(5, TimeUnit.SECONDS)) app.destroyForcibly()
      watcher.close()
      panel.close()
      bus.stop()
    }
    sys.exit(if (failed) 1 else 0)
  }

  // the windows shown on the display, as `xwininfo` lists them
  private def windows(): Int = {
    val p = new ProcessBuilder("xwininfo", "-root", "-tree").redirectErrorStream(true).start()
    val lines = scala.io.Source.fromInputStream(p.getInputStream).getLines().toList
    p.waitFor()
    lines.count(l => l.trim.startsWith("0x") && !l.contains("has no name") && l.matches(".*\\d+x\\d+\\+.*"))
  }
}
