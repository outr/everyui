package everyui.draw

import everyui.{Color, FxSpec}

/** SVG path data read into the four steps every backend draws, a simple SVG file read into monochrome art, and the art
  * fitted to the square it shows in. */
class VectorArtSpec extends FxSpec {
  private def svgOf(d: String): String = PathData.parse(d).svg
  private def near(a: Double, b: Double): Boolean = math.abs(a - b) < 0.01
  // the point a cubic's ops pass through halfway
  private def middle(from: (Double, Double), c: List[Double]): (Double, Double) =
    (0.125 * from._1 + 0.375 * c(0) + 0.375 * c(2) + 0.125 * c(4), 0.125 * from._2 + 0.375 * c(1) + 0.375 * c(3) + 0.125 * c(5))

  "Path data" should {
    "read lines, across and down, absolute and relative, closed back to where the figure began" in {
      svgOf("M2 2h20v20H2z") shouldBe "M 2 2 L 22 2 L 22 22 L 2 22 Z"
      svgOf("m1 1 2 0 0 2z m1 1 1 0") shouldBe "M 1 1 L 3 1 L 3 3 Z M 2 2 L 3 2"
      svgOf("M 1,1 L 2,2 V 5 h -1") shouldBe "M 1 1 L 2 2 L 2 5 L 1 5"
    }
    "read numbers run together, and in exponent form" in {
      svgOf("M.5.5-1-1") shouldBe "M 0.5 0.5 L -1 -1"
      svgOf("M1e1 2E-1l1.5.25") shouldBe "M 10 0.2 L 11.5 0.45"
    }
    "raise quadratics to cubics, and mirror the last control point for S and T" in {
      svgOf("M0 0Q10 10 20 0") shouldBe "M 0 0 C 6.667 6.667 13.333 6.667 20 0"
      svgOf("M0 0Q5 5 10 0T20 0") shouldBe "M 0 0 C 3.333 3.333 6.667 3.333 10 0 C 13.333 -3.333 16.667 -3.333 20 0"
      svgOf("M0 0C0 5 5 5 5 0S10-5 10 0") shouldBe "M 0 0 C 0 5 5 5 5 0 C 5 -5 10 -5 10 0"
      // S after no curve takes the pen for its first control point
      svgOf("M1 1S5 5 6 6") shouldBe "M 1 1 C 1 1 5 5 6 6"
    }
    "cut arcs into cubics that lie on the ellipse, with their flags run into what follows" in {
      // a circle of radius 10 about (12, 12) in two arcs, the second's flags and number written together
      val circle = PathData.parse("M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0")
      circle.ops.map(_.kind) shouldBe Vector("M", "C", "C", "C", "C")
      var at = (2.0, 12.0)
      circle.ops.tail.foreach { op =>
        val (x, y) = middle(at, op.values)
        math.hypot(x - 12, y - 12) shouldBe 10.0 +- 0.01
        at = (op.values(4), op.values(5))
      }
      near(at._1, 2) && near(at._2, 12) shouldBe true
      // as simple-icons write them: "a1.64 1.64 0 003.038.002"
      val packed = PathData.parse("M10.469 12.428a1.64 1.64 0 003.038.002L16.598 5")
      packed.ops.map(_.kind).last shouldBe "L"
      val end = packed.ops.init.last.values
      near(end(4), 13.507) && near(end(5), 12.43) shouldBe true
      // radii too small to reach are made large enough: a half circle of radius 5
      val half = PathData.parse("M0 0A1 1 0 0 1 10 0")
      val (mx, my) = middle((0.0, 0.0), half.ops(1).values)
      math.hypot(mx - 5, my) shouldBe 5.0 +- 0.01
      // a radius of 0 is a line
      svgOf("M0 0A0 5 0 0 1 10 0") shouldBe "M 0 0 L 10 0"
    }
    "end at what it cannot read, keeping what came before" in {
      svgOf("M0 0L10 10 x 20 20") shouldBe "M 0 0 L 10 10"
      svgOf("M0 0Z5 5") shouldBe "M 0 0 Z"
      svgOf("") shouldBe ""
    }
  }

  "An SVG file" should {
    "be read as its viewBox and every path, filled by the rule it hands down, whatever its colour" in {
      // shaped as an app's own provider marks are: currentColor, even-odd on the root, a title, two paths
      val art = VectorArt.parse("""<svg fill="currentColor" fill-rule="evenodd" height="1em" style="flex:none;line-height:1"
        viewBox="0 0 24 24" width="1em" xmlns="http://www.w3.org/2000/svg"><title>Mark</title>
        <path clip-rule="evenodd" d="M12 2a10 10 0 110 20 10 10 0 010-20zm0 4a6 6 0 110 12 6 6 0 110-12z"></path>
        <path d="M12 9.5l2.5 2.5-2.5 2.5-2.5-2.5z"/></svg>""").get
      (art.left, art.top, art.width, art.height) shouldBe (0.0, 0.0, 24.0, 24.0)
      art.shapes.size shouldBe 2
      art.shapes.map(_.evenOdd) shouldBe List(true, true)
      art.shapes.map(_.fill.nonEmpty) shouldBe List(true, true)
      art.shapes.map(_.stroke) shouldBe List(None, None)
      art.shapes(1).path.svg shouldBe "M 12 9.5 L 14.5 12 L 12 14.5 L 9.5 12 Z"
    }
    "stroke what has a stroke, leave out what is filled with nothing, and never draw what is defined for later" in {
      val art = VectorArt.parse("""<?xml version="1.0"?><!-- an outline icon --><svg viewBox="0 0 16 16">
        <defs><path d="M0 0h1v1z"/><clipPath id="c"><rect width="4" height="4"/></clipPath></defs>
        <g fill="none" stroke="currentColor" stroke-width="2"><circle cx="8" cy="8" r="6"/></g>
        <rect x="1" y="1" width="2" height="2" style="fill: none"/><path d="M1 1h2" fill="none"/>
        <polygon points="0,0 4,0 4,4"/></svg>""").get
      (art.width, art.height) shouldBe (16.0, 16.0)
      art.shapes.size shouldBe 2
      art.shapes.head.fill shouldBe None
      art.shapes.head.stroke.nonEmpty shouldBe true
      art.shapes.head.width shouldBe 2.0
      art.shapes(1).path.svg shouldBe "M 0 0 L 4 0 L 4 4 Z"
      art.shapes(1).evenOdd shouldBe false
    }
    "take its width and height where it has no viewBox, and be nothing where it holds no SVG" in {
      val art = VectorArt.parse("""<svg width="32px" height="16"><rect width="32" height="16"/></svg>""").get
      (art.width, art.height) shouldBe (32.0, 16.0)
      VectorArt.parse("<html><body>Not found</body></html>") shouldBe None
      VectorArt.parse("") shouldBe None
    }
  }

  "Art" should {
    "be fitted into its square in proportion, centred, in the colour given" in {
      val art = VectorArt(0.0, 0.0, 24.0, 12.0, List(Figure(Path.rect(0, 0, 24, 12), fill = Some(Color.Black)),
        Figure(Path.line(0, 0, 24, 0), stroke = Some(Color.Black), width = 2.0)))
      val red = Color.fromHex("#ff0000")
      val drawn = art.figures(12.0, red)
      drawn.head.path.svg shouldBe "M 0 3 L 12 3 L 12 9 L 0 9 Z"
      drawn.head.fill shouldBe Some(red)
      drawn(1).stroke shouldBe Some(red)
      drawn(1).width shouldBe 1.0
      // a viewBox from elsewhere than the corner
      VectorArt.path("M10 10h4v4h-4z", 4.0, 4.0, 10.0, 10.0).figures(8.0, red).head.path.svg shouldBe
        "M 0 0 L 8 0 L 8 8 L 0 8 Z"
    }
  }
}
