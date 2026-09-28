package everyui.draw

import everyui.{Color, FxSpec, ui}
import everyui.theme.{Palette, Theme}
import javafx.scene.layout.Pane
import javafx.scene.shape.SVGPath
import reactify.*

/** Paths and what draws them: the steps, arcs made of curves, the desktop's shapes following the figures and the
  * palette, a sparkline, and the rectangles a platform without paths shows. */
class DrawSpec extends FxSpec {
  private def shapes(d: Drawing): List[SVGPath] = {
    val all = d.node.fx.asInstanceOf[Pane].getChildren
    (0 until all.size).map(i => all.get(i).asInstanceOf[SVGPath]).toList
  }

  "A path" should {
    "be written as SVG path data" in {
      Path.empty.moveTo(0, 10).lineTo(20.5, 0).cubicTo(1, 2, 3, 4, 5, 6).close.svg shouldBe
        "M 0 10 L 20.5 0 C 1 2 3 4 5 6 Z"
      Path.rect(1, 2, 3, 4).svg shouldBe "M 1 2 L 4 2 L 4 6 L 1 6 Z"
      Path.number(1.0 / 3.0) shouldBe "0.333"
    }
    "make arcs and circles of curves that end where they should" in {
      val quarter = Path.empty.arc(0, 0, 10, 0, math.Pi / 2)
      quarter.ops.map(_.kind) shouldBe Vector("M", "C")
      quarter.ops.last.values.drop(4).map(v => math.rint(v * 1000) / 1000) shouldBe List(0.0, 10.0)
      // the curve's middle lies on the circle, to within a pixel's hundredth
      val c = quarter.ops.last.values
      val (x, y) = (0.125 * 10 + 0.375 * c(0) + 0.375 * c(2) + 0.125 * c(4), 0.375 * c(1) + 0.375 * c(3) + 0.125 * c(5))
      math.hypot(x, y) shouldBe 10.0 +- 0.01
      Path.circle(5, 5, 5).ops.map(_.kind) shouldBe Vector("M", "C", "C", "C", "C", "Z")
    }
  }

  "A drawing" should {
    "draw its figures, again when they change, in the palette's colours" in {
      val lanes = Var(1)
      val d = fx {
        val d = new Drawing
        d.size.width @= 60.0
        d.size.height @= 24.0
        d.figures := (0 until lanes()).toList.map(i => Figure(Path.line(6 + i * 12, 0, 6 + i * 12, 24), stroke = Some(Theme.accent()), width = 2))
        ui.children += d
        d
      }
      try {
        fx(shapes(d).map(_.getContent)) shouldBe List("M 6 0 L 6 24")
        fx(lanes @= 3)
        fx(shapes(d).size) shouldBe 3
        fx(shapes(d).head.getStrokeWidth) shouldBe 2.0
        val before = fx(Theme.palette())
        fx(Theme.palette @= Palette.light)
        fx(shapes(d).head.getStroke) shouldBe everyui.render.RenderNode.paintOf(Palette.light.accent)
        fx(Theme.palette @= before)
      }
      finally fx(ui.children -= d)
    }
  }

  "A sparkline" should {
    "run the values across its box, the highest at the top" in {
      val figures = Sparkline.figures(Seq(0.0, 10.0, 5.0), 102.0, 22.0, Color.Red, width = 2.0)
      figures.size shouldBe 1
      figures.head.path.ops.map(op => op.values) shouldBe Vector(List(1.0, 21.0), List(51.0, 1.0), List(101.0, 11.0))
      Sparkline.figures(Seq(1.0, 2.0), 50, 10, Color.Red, area = true).map(_.fill.nonEmpty) shouldBe List(true, false)
      Sparkline.figures(Seq(1.0), 50, 10, Color.Red) shouldBe Nil
    }
    "follow its values and its size" in {
      val values = Var(Seq(1.0, 2.0, 3.0))
      val d = fx {
        val d = Sparkline(values())
        d.size.width @= 40.0
        d.size.height @= 10.0
        ui.children += d
        d
      }
      try {
        fx(shapes(d).size) shouldBe 1
        fx(values @= Seq(3.0, 1.0))
        fx(shapes(d).head.getContent) shouldBe "M 0.75 0.75 L 39.25 9.25"
        fx(d.size.width @= 80.0)
        fx(shapes(d).head.getContent) shouldBe "M 0.75 0.75 L 79.25 9.25"
      }
      finally fx(ui.children -= d)
    }
  }

  "The rectangles of a drawing" should {
    "keep straight strokes across and down, and filled rectangles, and leave out the rest" in {
      val blocks = Block.of(List(
        Figure(Path.line(10, 0, 10, 20), stroke = Some(Color.Red), width = 2),
        Figure(Path.line(0, 5, 30, 5), stroke = Some(Color.Blue), width = 1),
        Figure(Path.line(0, 0, 10, 10), stroke = Some(Color.Red)),
        Figure(Path.rect(2, 3, 4, 5), fill = Some(Color.Green)),
        Figure(Path.circle(5, 5, 5), fill = Some(Color.Green))))
      blocks.map(b => (b.x, b.y, b.width, b.height)) shouldBe
        List((9.0, -1.0, 2.0, 22.0), (-0.5, 4.5, 31.0, 1.0), (2.0, 3.0, 4.0, 5.0))
    }
  }
}
