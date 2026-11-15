package everyui.tray

import everyui.menu.MenuItem
import java.lang.foreign.{FunctionDescriptor, MemoryLayout, MemorySegment, StructLayout, ValueLayout}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import reactify.Var
import scala.jdk.CollectionConverters.*

/** The macOS tray's parts that need no Mac: the point a click is told at, the pixels and size an NSImage is given, the
  * menu's numbering, and the native image's metadata against the calls made. */
class MacTraySpec extends AnyWordSpec with Matchers {
  "The macOS tray" should {
    "tell a click at the item's middle, at the foot of the menu bar, from the primary screen's top" in {
      // an item 24 points wide at x 1000 in a menu bar 24 tall on a 1080 high primary screen: its window's frame
      // starts at y 1056, from the bottom
      MacTray.javafxPoint(1000.0, 1056.0, 24.0, 1080.0) shouldBe (1012, 24)
      // on a screen above the primary one the menu bar is above 0
      MacTray.javafxPoint(-300.0, 2136.0, 30.0, 1080.0) shouldBe (-285, -1056)
    }
    "give a bitmap R G B A, premultiplied" in {
      val i = TrayImage(4, 1, Array(0xff102030, 0x80ff0000, 0x00ffffff, 0x40ffffff))
      MacImage.rgba(i).toList.map(_ & 0xff) shouldBe List(16, 32, 48, 255, 128, 0, 0, 128, 0, 0, 0, 0, 64, 64, 64, 64)
    }
    "size the picture in points from its smallest size, a big one brought down to the menu bar" in {
      def px(w: Int, h: Int) = TrayImage(w, h, new Array[Int](w * h))
      MacImage.points(TrayImage.of(px(36, 36), px(18, 18))) shouldBe (18.0, 18.0)
      MacImage.points(px(32, 16)) shouldBe (32.0, 16.0)
      MacImage.points(px(64, 64)) shouldBe (18.0, 18.0)
      MacImage.points(px(44, 22)) shouldBe (44.0, 22.0)
    }
    "ink a template white or black by its alpha, every size, for a badge" in {
      val t = TrayImage.of(TrayImage(1, 1, Array(0x80000000)), TrayImage(2, 2, Array.fill(4)(0xff000000))).asTemplate
      val white = MacImage.inked(t, white = true)
      white.sizes.map(_.width) shouldBe List(1, 2)
      white.sizes.map(_.template) shouldBe List(false, false)
      white.argb.toList shouldBe List(0x80ffffff)
      white.sizes(1).argb.distinct.toList shouldBe List(0xffffffff)
      MacImage.inked(TrayImage(1, 1, Array(0x80ffffff)), white = false).argb.toList shouldBe List(0x80000000)
      val badged = white.badged(everyui.Color.Green)
      badged.sizes.map(_.template) shouldBe List(false, false)
    }
    "number the menu as the Linux one is, choosing only what runs, ticked as `on` is" in {
      val on = Var(false)
      var ran = List.empty[String]
      val items = List(
        MenuItem("CPU 4%", enabled = false),
        MenuItem.separator,
        MenuItem.sub("Status", List(MenuItem("Away", () => ran ::= "away"), MenuItem("Busy", () => ran ::= "busy"))),
        MenuItem.toggle("Cycle", on, v => on @= v),
        MenuItem("Stop", () => ran ::= "stop"))
      val (flat, _) = TrayMenu.flatten(items)
      flat.map(_.label) shouldBe Vector("CPU 4%", "", "Status", "Away", "Busy", "Cycle", "Stop")
      List(1L, 2L, 3L, 0L, 8L).flatMap(t => MacMenu.chosen(flat, t)) shouldBe Nil
      MacMenu.chosen(flat, 5L).foreach(_.action())
      MacMenu.chosen(flat, 7L).foreach(_.action())
      ran shouldBe List("stop", "busy")
      MacMenu.state(flat(5)) shouldBe Some(MacMenu.Off)
      on @= true
      MacMenu.state(flat(5)) shouldBe Some(MacMenu.On)
      MacMenu.state(flat(6)) shouldBe None
      MacMenu.chosen(flat, 6L).foreach(_.action())
      on() shouldBe false
    }
    "list in the native image's metadata every call it makes and every call back, and their targets" in {
      val json = scala.io.Source.fromResource(
        "META-INF/native-image/com.outr/everyui-desktop-tray-macos/reachability-metadata.json").mkString
      val flat = json.replaceAll("\\s+", " ")
      val entry = """\{ ?"returnType": "([^"]*)", "parameterTypes": \[([^\]]*)\] ?\}""".r
      def listed(section: String, until: String): Set[String] = {
        val from = flat.indexOf("\"" + section + "\"")
        val part = flat.substring(from, if (until.isEmpty) flat.length else flat.indexOf(until, from))
        entry.findAllMatchIn(part).map(m => m.group(1).replace(" ", "") + "(" + m.group(2).replace("\"", "").replace(" ", "") + ")").toSet
      }
      listed("downcalls", "\"upcalls\"") shouldBe ObjC.downcalls.map(MacTraySpec.render).toSet
      listed("upcalls", "") shouldBe ObjC.upcalls.map(MacTraySpec.render).toSet
      val m = classOf[MemorySegment]
      Class.forName("everyui.tray.ObjC$").getMethod("work", m)
      Class.forName("everyui.tray.MacTray$").getMethod("answer", m, m, m)
      Class.forName("everyui.tray.MacNotifier$").getMethod("answer", m, m, m, m, m)
      Class.forName("everyui.tray.MacNotifier$").getMethod("allowed", m, java.lang.Boolean.TYPE, m)
    }
  }
}

object MacTraySpec {
  private def layout(l: MemoryLayout): String = l match {
    case s: StructLayout => "struct(" + s.memberLayouts.asScala.map(layout).mkString(",") + ")"
    case _: java.lang.foreign.AddressLayout => "void*"
    case v: ValueLayout if v.carrier == classOf[Long] => "jlong"
    case v: ValueLayout if v.carrier == classOf[Boolean] => "jboolean"
    case v: ValueLayout if v.carrier == classOf[Double] => "jdouble"
    case other => other.toString
  }

  /** A descriptor as the metadata writes it, spaces left out: `void*(void*,jlong)`. */
  def render(d: FunctionDescriptor): String =
    d.returnLayout.map(layout).orElse("void") + "(" + d.argumentLayouts.asScala.map(layout).mkString(",") + ")"
}
