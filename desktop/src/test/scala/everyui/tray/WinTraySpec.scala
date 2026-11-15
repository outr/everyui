package everyui.tray

import everyui.menu.MenuItem
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import reactify.*

import java.lang.foreign.{FunctionDescriptor, MemoryLayout, MemorySegment, ValueLayout}
import scala.jdk.CollectionConverters.*

/** What the Windows tray works out before it calls Win32, checked on any platform: NOTIFYICONDATAW laid out as x64
  * has it, a picture as a DIB's bytes and its mask, the shell's physical point put in JavaFX's logical pixels, the
  * tooltip cut to fit, the menu's flags, and the native image's metadata against the calls made. */
class WinTraySpec extends AnyWordSpec with Matchers {
  "NOTIFYICONDATAW" should {
    "be 976 bytes, each field where x64 puts it" in {
      Win32.NotifyIconData.byteSize shouldBe 976L
      List("cbSize", "hWnd", "uID", "uFlags", "uCallbackMessage", "hIcon", "szTip", "dwState", "dwStateMask", "szInfo",
        "uVersion", "szInfoTitle", "dwInfoFlags", "guidItem", "hBalloonIcon").map(Win32.at) shouldBe
        List(0L, 8L, 16L, 20L, 24L, 32L, 40L, 296L, 300L, 304L, 816L, 820L, 948L, 952L, 968L)
    }
  }

  "The native image's metadata" should {
    "list every call made and every call back, and the methods they are found by" in {
      val json = scala.io.Source.fromResource(
        "META-INF/native-image/com.outr/everyui-desktop-tray-windows/reachability-metadata.json").mkString
      val flat = json.replaceAll("\\s+", " ")
      val entry = """\{ ?"returnType": "([^"]*)", "parameterTypes": \[([^\]]*)\] ?\}""".r
      def listed(section: String, until: String): Set[String] = {
        val from = flat.indexOf("\"" + section + "\"")
        val part = flat.substring(from, if (until.isEmpty) flat.length else flat.indexOf(until, from))
        entry.findAllMatchIn(part).map(m =>
          m.group(1).replace(" ", "") + "(" + m.group(2).replace("\"", "").replace(" ", "") + ")").toSet
      }
      listed("downcalls", "\"upcalls\"") shouldBe Win32.downcalls.values.map(WinTraySpec.render).toSet
      listed("upcalls", "") shouldBe Win32.upcalls.map(WinTraySpec.render).toSet
      val m = classOf[MemorySegment]
      val long = java.lang.Long.TYPE
      Class.forName("everyui.tray.WinWindow$").getMethod("proc", m, Integer.TYPE, long, long)
      Class.forName("everyui.tray.WinTrayIcon$").getMethod("monitor", m, m, m, long)
    }
  }

  "A picture" should {
    "be B, G, R, A for a DIB, with a mask set where it is clear, its rows a whole 16 bits" in {
      val image = TrayImage(3, 2, Array(0xff102030, 0x00000000, 0x80405060, 0x00ffffff, 0x01000000, 0xffffffff))
      Win32.bgra(image).take(12).toList.map(_ & 0xff) shouldBe
        List(0x30, 0x20, 0x10, 0xff, 0, 0, 0, 0, 0x60, 0x50, 0x40, 0x80)
      // the second pixel clear in row one, the first in row two; two bytes a row
      Win32.mask(image).toList.map(_ & 0xff) shouldBe List(0x40, 0x00, 0x80, 0x00)
      val wide = TrayImage(20, 1, Array.fill(20)(0))
      Win32.mask(wide).toList.map(_ & 0xff) shouldBe List(0xff, 0xff, 0xf0, 0x00)
    }
  }

  "The shell's point" should {
    // a 4K primary at 150%, a 1080p one at 100% to its right, as JavaFX lays them out (side by side, in logical pixels)
    val screens = List(TrayScreen(0, 0, 2560, 1440, 1.5), TrayScreen(2560, 0, 1920, 1080, 1.0))
    val monitors = List((0, 0, 3840, 2160), (3840, 0, 5760, 1080))
    "be put on the monitor that holds it, at its own scale" in {
      Win32.logical(3000, 2100, monitors, screens) shouldBe (2000, 1400)
      Win32.logical(5000, 1000, monitors, screens) shouldBe (3720, 1000)
      // the monitors listed in another order, paired by where they are
      Win32.logical(5000, 1000, monitors.reverse, screens) shouldBe (3720, 1000)
    }
    "be divided by a screen's scale where the monitors are not known" in {
      Win32.logical(3000, 2100, Nil, screens) shouldBe (2000, 1400)
      Win32.logical(300, 300, Nil, List(TrayScreen(0, 0, 1280, 800, 1.25))) shouldBe (240, 240)
      Win32.logical(10, 20, Nil, Nil) shouldBe (10, 20)
    }
  }

  "The tooltip" should {
    "say the title over the tooltip, cut to 127 characters, never between a surrogate pair's halves" in {
      Win32.tipText("App", "Connected") shouldBe "App\nConnected"
      Win32.tipText("App", "") shouldBe "App"
      Win32.fit("x" * 200, 128).length shouldBe 127
      Win32.fit("x" * 126 + "😀", 128) shouldBe "x" * 126
      Win32.fit("short", 128) shouldBe "short"
    }
  }

  "The menu" should {
    "number its entries in order, with Windows' flags, and run only what can be chosen" in {
      val on = Var(true)
      var ran = List.empty[String]
      val m = WinMenu.of(List(
        MenuItem("Status & more", enabled = false),
        MenuItem.separator,
        MenuItem.sub("More", List(MenuItem("First", () => ran = "First" :: ran))),
        MenuItem.toggle("Toggle", on, b => on @= b),
        MenuItem("Quit", () => ran = "Quit" :: ran)))
      m.entries.map(e => (e.id, e.label, e.flags)) shouldBe List(
        (1, "Status && more", Win32.MF_GRAYED),
        (2, "", Win32.MF_SEPARATOR),
        (3, "More", Win32.MF_POPUP),
        (5, "Toggle", Win32.MF_CHECKED),
        (6, "Quit", 0))
      m.entries(2).submenu.map(e => (e.id, e.label)) shouldBe List((4, "First"))
      List(1, 2, 3, 4, 6, 9).foreach(id => m.chosen(id).foreach(_()))
      ran shouldBe List("Quit", "First")
      m.chosen(5).foreach(_())
      on() shouldBe false
      WinMenu.of(List(MenuItem.toggle("Toggle", on, b => on @= b))).entries.head.flags shouldBe 0
    }
  }
}

object WinTraySpec {
  private def layout(l: MemoryLayout): String = l match {
    case _: java.lang.foreign.AddressLayout => "void*"
    case v: ValueLayout if v.carrier == classOf[Long] => "jlong"
    case v: ValueLayout if v.carrier == classOf[Int] => "jint"
    case v: ValueLayout if v.carrier == classOf[Char] => "jchar"
    case other => other.toString
  }

  /** A descriptor as the metadata writes it, spaces left out: `jint(void*,jlong)`. */
  def render(d: FunctionDescriptor): String =
    d.returnLayout.map(layout).orElse("void") + "(" + d.argumentLayouts.asScala.map(layout).mkString(",") + ")"
}
