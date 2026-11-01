package everyui.picker

import com.sun.net.httpserver.HttpServer
import everyui.async.Task
import everyui.component.{Component, MaterialIconView, Modal, TextView}
import everyui.draw.VectorIcon
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.render.RenderNode
import everyui.theme.{Palette, Theme}
import everyui.{FxSpec, ui}
import javafx.scene.layout.Pane
import javafx.scene.shape.{FillRule, SVGPath}
import reactify.*

import java.net.InetSocketAddress

/** A segment chip's icon: there only when given, its size and the gap after it, in the colour of the chip's words as
  * they follow the chip chosen, the focus and the palette, from the right in a right-to-left language, read from an SVG
  * file on disk or over HTTP; and a chip without one exactly as it was. */
class SegmentIconSpec extends FxSpec {
  // shaped as an app's own provider marks are: currentColor, even-odd on the root, a title, two paths
  private val mark = """<svg fill="currentColor" fill-rule="evenodd" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">""" +
    """<title>Mark</title><path d="M12 2a10 10 0 110 20 10 10 0 010-20zm0 4a6 6 0 110 12 6 6 0 110-12z"/>""" +
    """<path d="M12 9.5l2.5 2.5-2.5 2.5-2.5-2.5z"></path></svg>"""
  private val leaf = SegmentIcon.Path("M3 21C3 10 10 3 21 3c0 11-7 18-18 18Z")

  private def shapes(icon: Component): List[SVGPath] = {
    val all = icon.node.fx.asInstanceOf[Pane].getChildren
    (0 until all.size).map(i => all.get(i).asInstanceOf[SVGPath]).toList
  }
  private def words(chip: SegmentChip): TextView = chip.children().collectFirst { case t: TextView if !t.isInstanceOf[MaterialIconView] => t }.get

  private def chips[T](segments: Segment*)(test: (List[SegmentChip], Var[String]) => T): T = {
    val chosen = Var("")
    val made = fx {
      val all = segments.toList.map(s => new SegmentChip(s, chosen))
      ui.children ++= all
      all
    }
    try test(made, chosen) finally fx {
      made.foreach(c => ui.children -= c)
      Focus.focused @= None
    }
  }

  "A segment chip" should {
    "have no icon, and be laid out exactly as before, where none is given" in chips(Segment("a", "Alpha")) { (all, _) =>
      val chip = all.head
      fx(chip.icon) shouldBe None
      fx(chip.children().size) shouldBe 1
      fx(words(chip).position.left()) shouldBe SegmentChip.padH
      fx(chip.size.width()) shouldBe fx(words(chip).size.width() + 2.0 * SegmentChip.padH)
      fx(chip.size.height()) shouldBe SegmentChip.height
    }
    "put a given icon at the start, 14 pixels square and centred, the words a gap after it, the chip wider by both" in
      chips(Segment("a", "Alpha"), Segment("b", "Alpha", Some(SegmentIcon(MaterialIcon.Computer))),
        Segment("c", "Alpha", Some(leaf))) { (all, _) =>
        val List(plain, glyph, path) = all
        fx(glyph.icon.get.isInstanceOf[MaterialIconView]) shouldBe true
        fx(path.icon.get.isInstanceOf[VectorIcon]) shouldBe true
        List(glyph, path).foreach { chip =>
          val icon = chip.icon.get
          fx((icon.position.left(), icon.position.top(), icon.size.width(), icon.size.height())) shouldBe
            (12.0, 6.0, 14.0, 14.0)
          fx(words(chip).position.left()) shouldBe 12.0 + 14.0 + 6.0
          fx(chip.size.width() - plain.size.width()) shouldBe 20.0
          fx(chip.size.height()) shouldBe SegmentChip.height
          fx(words(chip).position.top()) shouldBe fx(words(plain).position.top())
        }
        // the path drawn in the square, by the non-zero rule
        val drawn = fx(shapes(path.icon.get))
        drawn.size shouldBe 1
        fx(drawn.head.getFillRule) shouldBe FillRule.NON_ZERO
        fx(drawn.head.getContent).startsWith("M 1.75 12.25 C") shouldBe true
      }
    "draw its icon in the colour of its words, as the chip is chosen, focused and the palette changes" in
      chips(Segment("a", "Alpha", Some(leaf)), Segment("b", "Beta", Some(SegmentIcon(MaterialIcon.Computer)))) {
        (all, chosen) =>
          val List(path, glyph) = all
          def fill: javafx.scene.paint.Paint = fx(shapes(path.icon.get).head.getFill)
          def agree(c: SegmentChip): Boolean = fx(c.icon.get.color() == words(c).color() && c.ink() == words(c).color())
          fx(path.ink()) shouldBe fx(Theme.muted())
          fill shouldBe fx(RenderNode.paintOf(Theme.muted()))
          agree(path) && agree(glyph) shouldBe true
          fx(chosen @= "a")
          fx(path.ink()) shouldBe fx(Theme.onAccent())
          fill shouldBe fx(RenderNode.paintOf(Theme.onAccent()))
          fx(glyph.icon.get.color()) shouldBe fx(Theme.muted())
          // focused where a pointer is used, it is not lit (Matt's ruling of 2026-10-21); the keys moving the focus, it is
          fx(glyph.focus())
          fx(glyph.icon.get.color()) shouldBe fx(Theme.muted())
          fx(Focus.visible @= true)
          fx(glyph.icon.get.color()) shouldBe fx(Theme.text())
          agree(glyph) shouldBe true
          val before = fx(Theme.palette())
          try {
            fx(Theme.palette @= Palette.light)
            fill shouldBe RenderNode.paintOf(Palette.light.onAccent)
            fx(glyph.icon.get.color()) shouldBe Palette.light.text
            fx(chosen @= "b")
            fill shouldBe RenderNode.paintOf(Palette.light.muted)
            fx(glyph.icon.get.color()) shouldBe Palette.light.onAccent
            agree(path) && agree(glyph) shouldBe true
          } finally fx {
            Theme.palette @= before
            Focus.visible @= false
          }
      }
    "put the icon at the right and the words before it in a right-to-left language" in
      chips(Segment("a", "Alpha", Some(leaf)), Segment("b", "Alpha")) { (all, _) =>
        val List(chip, plain) = all
        fx(Direction.set(true))
        try {
          val width = fx(chip.size.width())
          fx(chip.icon.get.position.left()) shouldBe width - 12.0 - 14.0
          fx(words(chip).position.left()) shouldBe 12.0
          fx(words(plain).position.left()) shouldBe 12.0
        } finally fx(Direction.set(false))
        fx(chip.icon.get.position.left()) shouldBe 12.0
        fx(words(chip).position.left()) shouldBe 32.0
      }
    "read its icon from an SVG file, filled by the rule the file gives, and keep the file for the next chip" in {
      val file = java.nio.file.Files.createTempFile("segment-mark", ".svg")
      java.nio.file.Files.writeString(file, mark)
      try chips(Segment("a", "Alpha", Some(SegmentIcon.Url(file.toUri.toString)))) { (all, _) =>
        val icon = all.head.icon.get
        eventually(shapes(icon).size == 2) shouldBe true
        fx(shapes(icon).map(_.getFillRule)) shouldBe List(FillRule.EVEN_ODD, FillRule.EVEN_ODD)
        fx(shapes(icon).map(_.getFill)) shouldBe fx(List.fill(2)(RenderNode.paintOf(Theme.muted())))
        // the diamond in the middle, a 24 box fitted to 14
        fx(shapes(icon)(1).getContent) shouldBe "M 7 5.542 L 8.458 7 L 7 8.458 L 5.542 7 Z"
        java.nio.file.Files.delete(file)
        val again = fx(VectorIcon.load(file.toUri.toString, 14.0))
        fx(shapes(again).size) shouldBe 2
      } finally java.nio.file.Files.deleteIfExists(file)
    }
    "read its icon over HTTP, and show its words alone, its room kept, where the file cannot be had" in {
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext("/providers/mark.svg", exchange => {
        val body = mark.getBytes("UTF-8")
        exchange.getResponseHeaders.add("Content-Type", "image/svg+xml")
        exchange.sendResponseHeaders(200, body.length.toLong)
        exchange.getResponseBody.write(body)
        exchange.close()
      })
      server.start()
      val base = "http://127.0.0.1:" + server.getAddress.getPort
      try chips(Segment("a", "Alpha", Some(SegmentIcon.Url(base + "/providers/mark.svg"))),
        Segment("b", "Beta", Some(SegmentIcon.Url(base + "/providers/missing.svg"))), Segment("c", "Beta")) { (all, _) =>
        val List(found, missing, plain) = all
        eventually(shapes(found.icon.get).size == 2) shouldBe true
        fx(shapes(missing.icon.get)) shouldBe Nil
        fx(missing.size.width() - plain.size.width()) shouldBe 20.0
      } finally server.stop(0)
    }
    "show its icon in a picker's chips" in {
      val picker = fx(new FilterPicker[String](_ => Task.pure(Vector("x")), identity, identity, debounce = 0.0,
        segments = List(Segment.all("All"), Segment("local", "Local", Some(SegmentIcon(MaterialIcon.Computer))))))
      try {
        fx(picker.open())
        val made = fx(picker.chips.children().toList.collect { case c: SegmentChip => c })
        made.map(_.icon.nonEmpty) shouldBe List(false, true)
        fx(made(1).icon.get.asInstanceOf[MaterialIconView].icon()) shouldBe MaterialIcon.Computer
      } finally fx {
        while (Modal.showing) Modal.close()
        ui.children.clear()
        Focus.focused @= None
      }
    }
  }
}
