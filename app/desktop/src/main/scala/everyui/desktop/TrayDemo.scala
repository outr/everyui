package everyui.desktop

import everyui.Color
import everyui.component.*
import everyui.layout.VerticalLayout
import everyui.menu.MenuItem
import everyui.render.Platform
import everyui.theme.Theme
import everyui.tray.{Tray, TrayIcon, TrayImage}
import reactify.*

import java.nio.file.{Files, Paths}

/** The tray on the desktop (`--demo=tray`, best with `--headless=1`): an icon whose dot cycles through connected,
  * connecting, away and stopped every three seconds, a menu with a live line of this machine's CPU and memory, a
  * submenu of statuses (the current one ticked), a ticked "Cycle status", notifications with an action; and, on a left
  * click, a card beside the icon with the same numbers as bars and a Stop or Start. Where there is no tray (GNOME
  * without its AppIndicator extension), it says so in a notification and quits. */
object TrayDemo {
  private val statuses: List[(String, Color)] = List(
    "Connected" -> Color.fromHex("#2e7d32"), "Connecting" -> Color.fromHex("#f9a825"),
    "Away" -> Color.fromHex("#9e9e9e"), "Stopped" -> Color.fromHex("#c62828"))

  def run(): Option[TrayIcon] = {
    val status = Var(0)
    val cycling = Var(true)
    val cpu = Var(0)
    val memory = Var(0)
    val logo = Paths.get("assets/images/everyui-mark.png")
    val picture = if (Files.isRegularFile(logo)) TrayImage.png(Files.readAllBytes(logo))
      else TrayImage.disc(Theme.accent(), 64)
    if (!Tray.supported) {
      Tray.notify("EveryUI tray demo", "This desktop has no tray (no StatusNotifierWatcher): notifications only.")
      Platform.after(2.0, () => sys.exit(0))
      None
    }
    else Tray.show(picture, "Connected").map { t =>
      def name: String = statuses(status())._1
      t.badge := Some(statuses(status())._2)
      t.tooltip := name + " · CPU " + cpu() + "% · Mem " + memory() + "%"
      t.attention := status() == 3
      t.menu := List(
        MenuItem("CPU " + cpu() + "% · Mem " + memory() + "%", enabled = false),
        MenuItem.separator,
        MenuItem.sub("Status", statuses.indices.toList.map(i =>
          MenuItem(statuses(i)._1, () => status @= i, checked = Some(status() == i)))),
        MenuItem.toggle("Cycle status", cycling, c => cycling @= c),
        MenuItem.separator,
        MenuItem("Notify", () => Tray.notify("EveryUI tray demo", "Status: " + name + ". Stop the client?",
          List("Stop" -> (() => status @= 3)))),
        MenuItem(if (status() == 3) "Start" else "Stop", () => status @= (if (status() == 3) 0 else 3)),
        MenuItem.separator,
        MenuItem("Quit", () => {
          t.hide()
          sys.exit(0)
        }))
      t.popover @= Some(() => card(status, cpu, memory))
      def tick(): Unit = {
        val (c, m) = TrayDemo.sample()
        cpu @= c
        memory @= m
        if (t.isShown) Platform.after(1.0, () => tick())
      }
      def cycle(): Unit = {
        if (cycling()) status @= (status() + 1) % statuses.size
        if (t.isShown) Platform.after(3.0, () => cycle())
      }
      tick()
      Platform.after(3.0, () => cycle())
      t
    }
  }

  // the card a left click opens: the status, the numbers as bars, and Stop or Start
  private def card(status: Var[Int], cpu: Val[Int], memory: Val[Int]): Component = new Container {
    size.width @= 300.0
    padding @= 16.0
    backgroundColor := Theme.surface()
    layout @= Some(VerticalLayout(10.0))
    private val dot = new Container {
      size.width @= 10.0
      size.height @= 10.0
      radius @= 5.0
      backgroundColor := statuses(status())._2
    }
    private val said = TextView.bound(statuses(status())._1, 15.0)
    private val head = new Container {
      children ++= List(dot, said)
      dot.position.top @= 5.0
      said.position.left @= 18.0
      size.width @= 268.0
      size.height := said.size.height()
    }
    private def bar(label: => String, value: => Int): Container = new Container {
      size.width @= 268.0
      private val words = TextView.bound(label, 12.0)
      private val track = new ProgressBar(268.0)
      track.fraction := value / 100.0
      track.position.top := words.size.height() + 4.0
      size.height := words.size.height() + 10.0
      children ++= List(words, track)
    }
    private val toggle = new Button("", () => status @= (if (status() == 3) 0 else 3))
    toggle.label := (if (status() == 3) "Start" else "Stop")
    children ++= List(TextView("EveryUI tray demo", 13.0, Theme.muted()), head,
      bar("CPU " + cpu() + "%", cpu()), bar("Memory " + memory() + "%", memory()), toggle)
  }

  private var lastBusy = 0L
  private var lastAll = 0L

  /** This machine's CPU in use since the last sample and its memory in use, in percent, from /proc. */
  def sample(): (Int, Int) = try {
    val stat = Files.readAllLines(Paths.get("/proc/stat")).get(0).trim.split("\\s+").drop(1).map(_.toLong)
    val idle = stat(3) + stat(4)
    val all = stat.sum
    val busy = all - idle
    val cpu = if (all > lastAll) math.round(100.0 * (busy - lastBusy) / (all - lastAll)).toInt else 0
    lastBusy = busy
    lastAll = all
    var total = 0L
    var available = 0L
    Files.readAllLines(Paths.get("/proc/meminfo")).forEach { l =>
      if (l.startsWith("MemTotal:")) total = l.split("\\s+")(1).toLong
      if (l.startsWith("MemAvailable:")) available = l.split("\\s+")(1).toLong
    }
    (cpu, if (total > 0) math.round(100.0 * (total - available) / total).toInt else 0)
  } catch { case _: Exception => (0, 0) }
}
