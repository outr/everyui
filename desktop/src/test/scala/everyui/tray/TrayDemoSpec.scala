package everyui.tray

import everyui.FxSpec
import everyui.desktop.TrayDemo

import java.nio.file.{Files, Paths}
import java.util.concurrent.ConcurrentLinkedQueue

/** The tray demo on a bus of the spec's own, with a panel's watcher played by a connection of the spec's: its menu
  * as the panel reads it, its popover opened by a left click beside the point clicked (pictured for a look at it), and
  * shut with Escape. Nothing reaches the desktop's own session. */
class TrayDemoSpec extends FxSpec {
  private lazy val bus = new PrivateBus
  private val registered = new ConcurrentLinkedQueue[String]()

  override def afterAll(): Unit = {
    Tray.busPath = None
    bus.stop()
    super.afterAll()
  }

  "The tray demo" should {
    "serve its menu, and open its popover beside a left click" in {
      Tray.busPath = Some(bus.path)
      val watcher = bus.connect()
      watcher.serve(TrayIcon.WatcherPath) { m =>
        registered.add(m.body.head.toString)
        Some(("", Nil))
      }
      watcher.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "RequestName", "su",
        List(TrayIcon.Watcher, 4))
      val panel = bus.connect()
      val t = fx(TrayDemo.run()).get
      try {
        eventually(registered.contains(t.name)) shouldBe true
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
        val (x, y, w, h) = fx(t.popoverScene.map(s => (s.getWindow.getX, s.getWindow.getY, s.getWindow.getWidth,
          s.getWindow.getHeight)).get)
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
      } finally {
        fx(t.hide())
        panel.close()
        watcher.close()
      }
    }
  }
}
