package everyui.tray

import everyui.Color
import everyui.component.{Button, Checkbox, Container, TextInput, TextView}
import everyui.desktop.Desktop
import everyui.layout.VerticalLayout
import everyui.menu.MenuItem
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A tray icon shown and its every callback said, for a platform's tray to be checked by hand or on a CI runner, with
  * no app of its own: a disc at several sizes, a menu (a disabled status line, a separator, a submenu, a ticked toggle,
  * Quit) and a small popover. Each callback is a line: `[probe] clicked x,y`, `[probe] menu <label>`,
  * `[probe] toggle on=<b>`, `[probe] notify clicked`. Two seconds in, it shows a notification and badges the icon
  * green. It exits after `--seconds=N` (120 by default), or on Quit.
  *
  * With `--popover` it opens its popover from code (`TrayIcon.openPopover`) three seconds in and shuts it two seconds
  * later, then opens a plain window (a framed Stage holding a few components) and shuts that: `[probe] popover
  * opened`, `[probe] popover closed`, `[probe] window opened`, `[probe] window closed`, or what failed. Traced with
  * GraalVM's agent, it records what JavaFX's windows reach on the platform (glass's window, view and menu bar classes,
  * their JNI), which a probe that opens nothing never does.
  *
  * With `--selftest=1` on Windows it sends its own window what the shell would (the notification clicked, a left
  * click, a right click with the menu's toggle chosen by the keyboard, Explorer starting again with the icon gone, then
  * Quit chosen), so a desktop with no one to click checks the same. With `--notify-first=1` it shows a notification
  * before the icon, as an app with none shown would (`[probe] first notify clicked` where it is clicked).
  *
  * {{{
  *   java --enable-native-access=ALL-UNNAMED -cp <desktop and JavaFX> everyui.tray.TrayProbe [--seconds=N] [--selftest=1]
  *     [--notify-first=1] [--popover]
  * }}} */
object TrayProbe {
  private def say(line: String): Unit = {
    println("[probe] " + line)
    System.out.flush()
  }

  def main(args: Array[String]): Unit = {
    val seconds = args.collectFirst { case a if a.startsWith("--seconds=") => a.drop(10).toDouble }.getOrElse(120.0)
    val selfTest = args.contains("--selftest=1")
    val notifyFirst = args.contains("--notify-first=1")
    val popover = args.contains("--popover") || args.contains("--popover=1")
    Desktop.launch(args :+ "--headless=1", "EveryUI tray probe", "images/everyui-mark.png")(() =>
      run(seconds, selfTest, notifyFirst, popover))
  }

  private def run(seconds: Double, selfTest: Boolean, notifyFirst: Boolean, popover: Boolean): Unit = {
    say("supported=" + Tray.supported + " platform=" + TrayPlatform.current)
    if (notifyFirst) Tray.notify("Tray probe", "Before the icon", clicked = Some(() => say("first notify clicked")))
    Platform.after(seconds, () => quit(None))
    val sizes = List(16, 20, 24, 32, 64).map(px => TrayImage.disc(Color.fromHex("#1e88e5"), px))
    Tray.show(TrayImage.of(sizes.head, sizes.tail*), "Tray probe").foreach { t =>
      val on = Var(false)
      def choose(label: String): () => Unit = () => say("menu " + label)
      t.menu := List(
        MenuItem("Status: probing", enabled = false),
        MenuItem.separator,
        MenuItem.sub("More", List(MenuItem("First", choose("First")), MenuItem("Second", choose("Second")))),
        MenuItem.toggle("Toggle", on, b => {
          on @= b
          say("toggle on=" + b)
        }),
        MenuItem.separator,
        MenuItem("Quit", () => {
          say("menu Quit")
          quit(Some(t))
        }))
      t.popover @= Some(() => new Container {
        size.width @= 200.0
        padding @= 12.0
        backgroundColor := Theme.surface()
        layout @= Some(VerticalLayout(6.0))
        children ++= List(TextView("Tray probe", 14.0), TextView("Toggle is " + (if (on()) "on" else "off"), 12.0))
      })
      t.activated.attach { case (x, y) => say("clicked " + x + "," + y) }
      Platform.after(2.0, () => {
        Tray.notify("Tray probe", "A notification from the probe", clicked = Some(() => say("notify clicked")))
        t.badge @= Some(Color.Green)
        say("notified and badged")
      })
      if (popover) windows(t)
      if (selfTest) t.platform match {
        case w: WinTrayIcon => windowsSelfTest(w)
        case _ => say("selftest: only on Windows")
      }
    }
  }

  // the popover opened from code and shut, then a plain window opened and shut, each said, or what went wrong
  private def windows(t: TrayIcon): Unit = {
    def step(what: String)(f: => Unit): Unit = try f catch {
      case e: Throwable =>
        say(what + " failed: " + e)
        e.printStackTrace()
    }
    Platform.after(3.0, () => step("popover")(t.openPopover()))
    Platform.after(4.0, () => say(t.popoverScene.map(_.getWindow).map(w =>
      "popover opened at " + w.getX.round + "," + w.getY.round + ", " + w.getWidth.round + "x" + w.getHeight.round)
      .getOrElse("popover not open")))
    Platform.after(5.0, () => step("popover")({
      t.closePopover()
      say(if (t.popoverShown) "popover still open" else "popover closed")
    }))
    Platform.after(6.0, () => step("window")({
      val content = new Container {
        size.width @= 360.0
        padding @= 16.0
        backgroundColor := Theme.surface()
        layout @= Some(VerticalLayout(10.0))
        children ++= List(TextView("Tray probe", 16.0), new TextInput("Name"), new Checkbox("Checked"),
          new Button("Done"))
      }
      val root = new javafx.scene.layout.Pane()
      root.getChildren.add(content.node.fx)
      val stage = new javafx.stage.Stage()
      stage.setTitle("Tray probe")
      stage.setScene(new javafx.scene.Scene(root, 360.0, 240.0, javafx.scene.paint.Color.web(Theme.surface().toHex)))
      stage.show()
      say("window " + (if (stage.isShowing) "opened" else "not shown"))
      Platform.after(2.0, () => step("window")({
        stage.hide()
        content.dispose()
        say("window closed")
      }))
    }))
  }

  private def quit(t: Option[TrayIcon]): Unit = {
    t.foreach(_.hide())
    say("bye")
    // the icon's window let go of before the process goes
    Platform.after(2.0, () => sys.exit(0))
  }

  // the shell's callbacks played to the icon's own window, version 4's way: the event and the icon in lParam, the
  // point (physical pixels) in wParam
  private def windowsSelfTest(w: WinTrayIcon): Unit = {
    val hwnd = w.window.hwnd
    def tray(event: Int, x: Int, y: Int): Unit = {
      say("selftest: event 0x" + Integer.toHexString(event) + " at " + x + "," + y)
      Win32.post(hwnd, Win32.WM_TRAY, ((y & 0xffffL) << 16) | (x & 0xffffL), (1L << 16) | event)
    }
    def key(vk: Int): Unit = Win32.post(hwnd, 0x0100, vk.toLong, 0L)   // WM_KEYDOWN, as the menu's loop reads it
    // the notification shown at two seconds, clicked
    Platform.after(3.0, () => tray(Win32.NIN_BALLOONUSERCLICK, 0, 0))
    Platform.after(4.0, () => tray(Win32.NIN_SELECT, 1200, 780))
    Platform.after(8.0, () => tray(Win32.WM_CONTEXTMENU, 1200, 780))
    // the toggle is the third the keys reach: the disabled status, "More", then it (the separator passed over)
    Platform.after(12.0, () => List(0x28, 0x28, 0x28, 0x0d).foreach(key))
    Platform.after(15.0, () => w.window.post(() => {
      say("selftest: icon deleted, as Explorer restarting leaves it: " + Win32.deleteIcon(hwnd))
      val arena = java.lang.foreign.Arena.ofConfined()
      val taskbar = try (Win32.RegisterWindowMessageW.invokeExact(Win32.wide(arena, "TaskbarCreated")): Int)
        finally arena.close()
      say("selftest: TaskbarCreated sent: " + Win32.post(hwnd, taskbar))
    }))
    // then Quit, the last entry, which Up reaches first
    Platform.after(18.0, () => tray(Win32.WM_CONTEXTMENU, 1200, 780))
    Platform.after(21.0, () => List(0x26, 0x0d).foreach(key))
  }
}
