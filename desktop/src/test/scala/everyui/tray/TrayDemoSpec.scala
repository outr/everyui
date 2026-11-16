package everyui.tray

import everyui.FxSpec
import everyui.desktop.TrayDemo

import java.nio.file.{Files, Paths}
import java.util.concurrent.ConcurrentLinkedQueue

/** The tray demo on a bus of the spec's own, with a panel's watcher played by a connection of the spec's: its menu
  * as the panel reads it, its popover opened by a left click beside the point clicked (pictured for a look at it), and
  * shut with Escape; and opened from code (`TrayIcon.openPopover`), where a panel has not said where the icon is.
  * Nothing reaches the desktop's own session. */
class TrayDemoSpec extends FxSpec {
  private lazy val bus = new PrivateBus
  private val registered = new ConcurrentLinkedQueue[String]()

  override def afterAll(): Unit = {
    panel.close()
    watcher.close()
    Tray.busPath = None
    bus.stop()
    super.afterAll()
  }

  // a panel's watcher, on the spec's bus, noting each icon registered; and a panel clicking
  private lazy val watcher: DBusConnection = {
    Tray.busPath = Some(bus.path)
    val w = bus.connect()
    w.serve(TrayIcon.WatcherPath) { m =>
      registered.add(m.body.head.toString)
      Some(("", Nil))
    }
    w.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "RequestName", "su",
      List(TrayIcon.Watcher, 4))
    w
  }
  private lazy val panel: DBusConnection = bus.connect()

  // where the popover's window is, and its size (on the FX thread)
  private def placed(t: TrayIcon): (Double, Double, Double, Double) = t.popoverScene.map(s =>
    (s.getWindow.getX, s.getWindow.getY, s.getWindow.getWidth, s.getWindow.getHeight)).get

  // the demo's icon, registered with the watcher, given to `f`, then taken out
  private def withDemo(f: TrayIcon => Unit): Unit = {
    watcher
    val t = fx(TrayDemo.run()).get
    try {
      eventually(registered.contains(t.name)) shouldBe true
      f(t)
    } finally fx(t.hide())
  }

  "The tray demo" should {
    "serve its menu, and open its popover beside a left click" in {
      withDemo { t =>
        val layout = panel.ask(t.name, TrayMenu.Path, TrayMenu.Interface, "GetLayout", "iias",
          List(0, 1, Vector.empty)).get
        val labels = layout.body(1).asInstanceOf[DBusStruct].fields(2).asInstanceOf[Vector[DBusVariant]]
          .map(_.value.asInstanceOf[DBusStruct].fields(1).asInstanceOf[Vector[(String, DBusVariant)]].toMap)
          .map(_.get("label").map(_.value.toString).getOrElse("—"))
        labels.head should startWith ("CPU ")
        labels.drop(2).take(2) shouldBe Vector("Status", "Cycle status")
        panel.ask(t.name, TrayIcon.Path, TrayIcon.Interface, "Activate", "ii", List(1500, 1000))
        eventually(t.popoverScene.nonEmpty) shouldBe true
        // drawn: its card laid out, then pictured
        eventually(t.popoverScene.exists(_.getWindow.getHeight > 100.0)) shouldBe true
        val (x, y, w, h) = fx(placed(t))
        // above and to the left of a click in the screen's lower right
        (x + w <= 1500.5, y + h <= 1000.5, w) shouldBe (true, true, 300.0)
        val png = fx {
          val shot = t.popoverScene.get.snapshot(null)
          val (sw, sh) = (shot.getWidth.toInt, shot.getHeight.toInt)
          val px = new Array[Int](sw * sh)
          shot.getPixelReader.getPixels(0, 0, sw, sh, javafx.scene.image.PixelFormat.getIntArgbInstance, px, 0, sw)
          everyui.render.SnapshotAccess.encoded(sw, sh, px)
        }
        Files.write(Paths.get("/data/storage/everyui-scratch/tray/popover.png"), png)
        fx(javafx.event.Event.fireEvent(t.popoverScene.get.getRoot, new javafx.scene.input.KeyEvent(
          javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ESCAPE, false, false, false,
          false)))
        eventually(t.popoverScene.isEmpty) shouldBe true
      }
    }
    // Each popover opened from code is read in the same turn of the JavaFX thread that opens it: on the spec's Xvfb,
    // with no window manager, a window shown soon after another shut can lose its focus a moment later, and so shut.
    // (On Linux the icon's place is known at once, so `openPopover` opens it there and then.)
    "open its popover from code at the main screen's top right, where no click has said where the icon is" in {
      withDemo { t =>
        val area = fx(javafx.stage.Screen.getPrimary.getVisualBounds)
        val ((x, y, w, _), same) = fx {
          t.openPopover()
          val first = t.popoverScene
          val at = placed(t)
          // opened again, it stays as it is, as a click would not
          t.openPopover()
          (at, first.nonEmpty && t.popoverScene == first)
        }
        // under the corner, against the screen's right edge (to a pixel, as the window manager rounds it)
        (math.abs(x + w - area.getMaxX) <= 1.0, y, same) shouldBe (true, area.getMinY + 8.0, true)
        fx(t.closePopover())
        fx(t.popoverShown) shouldBe false
      }
    }
    "open its popover from code beside the last click" in {
      withDemo { t =>
        // the click made with no popover to open
        val make = fx(t.popover())
        fx(t.popover @= None)
        val clicks = new ConcurrentLinkedQueue[(Int, Int)]()
        fx(t.activated.attach(p => clicks.add(p)))
        val area = fx(javafx.stage.Screen.getPrimary.getVisualBounds)
        val (px, py) = ((area.getMaxX - 40.0).toInt, (area.getMaxY - 40.0).toInt)
        panel.ask(t.name, TrayIcon.Path, TrayIcon.Interface, "Activate", "ii", List(px, py))
        eventually(!clicks.isEmpty) shouldBe true
        // with no popover, nothing opens
        fx(t.openPopover())
        fx(t.popoverShown) shouldBe false
        fx(t.popover @= make)
        val (x, y, w, h) = fx {
          t.openPopover()
          placed(t)
        }
        // above the click and to its left, in the screen's lower right
        (math.abs(x + w - px) <= 1.0, y + h <= py) shouldBe (true, true)
        fx(t.closePopover())
      }
    }
  }
}
