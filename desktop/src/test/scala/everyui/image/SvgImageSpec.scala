package everyui.image

import everyui.chat.{Avatar, Participant}
import everyui.component.{ImageView, TextView}
import everyui.draw.VectorArt
import everyui.render.Host
import everyui.{FxSpec, ui}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** SVG drawn as a picture on the desktop: a tile of a rounded square and a shape under a group's translate and scale at
  * two sizes, read pixel by pixel; every shape, transform, fill rule, opacity, colour form and stroke the reader takes;
  * the viewBox placed by preserveAspectRatio, and its own size; what it does not draw logged and the rest drawn; a
  * file that is no picture logged; an SVG by its address, by its first bytes and as a `data:` address through `Image`;
  * and an avatar whose picture is an SVG showing it rather than its initial, drawn sharp at its size. */
class SvgImageSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    offStage()
    super.afterAll()
  }

  // the app's sample, drawn afresh: a 48 by 48 tile, a square rounded 12 in a warm orange, and over it a white diamond
  // (16 across) under a group moved 12 in and scaled half again, so it spans 12 to 36 about the middle
  private val tile = """<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48">
    <rect width="48" height="48" rx="12" fill="#D97757"/>
    <g transform="translate(12 12) scale(1.5)" fill="#fff"><path d="M8 0L16 8 8 16 0 8z"/></g></svg>"""

  private def art(svg: String): VectorArt = SvgImage.art(svg).get
  // the pixel at (x, y) of `svg` drawn `w` by `h`, as 0xAARRGGBB
  private def pixels(svg: String, w: Int, h: Int)(points: (Int, Int)*): List[Int] = fx {
    val reader = Host.svgPicture(art(svg), w, h).getPixelReader
    points.toList.map { case (x, y) => reader.getArgb(x, y) }
  }
  private def alpha(argb: Int): Int = (argb >>> 24) & 0xff
  private def rgb(argb: Int): Int = argb & 0xffffff
  private val clear = 0

  private val dir = new File("target/svgimage")
  private def file(name: String, text: String): String = {
    dir.mkdirs()
    val f = new File(dir, name)
    Files.write(f.toPath, text.getBytes(StandardCharsets.UTF_8))
    "file://" + f.getAbsolutePath
  }
  // what `Image` makes of `url`, once it has come
  private def load(url: String): Image = {
    var got: Option[Image] = None
    fx(Image(url).map(i => got = Some(i)).startUnit())
    eventually(got.nonEmpty) shouldBe true
    got.get
  }

  "The sample tile" should {
    "be its rounded square with the white shape over it, clear past the corners, at 48 and at 96 pixels" in {
      List(48, 96).foreach { side =>
        val s = side / 48
        val List(middle, beside, edge, corner, inCorner) =
          pixels(tile, side, side)((24 * s, 24 * s), (16 * s, 16 * s), (24 * s, 2 * s), (1 * s, 1 * s), (5 * s, 5 * s))
        middle shouldBe 0xffffffff
        beside shouldBe 0xffd97757
        edge shouldBe 0xffd97757
        alpha(corner) shouldBe 0
        inCorner shouldBe 0xffd97757
        // the diamond's points: 12 to 36 across and down, its corners' neighbourhood orange
        pixels(tile, side, side)((24 * s, 15 * s), (32 * s, 24 * s), (15 * s, 24 * s), (24 * s, 32 * s))
          .foreach(_ shouldBe 0xffffffff)
        pixels(tile, side, side)((24 * s, 10 * s), (38 * s, 24 * s)).foreach(_ shouldBe 0xffd97757)
      }
    }
  }

  "The SVG reader" should {
    "draw every shape: a circle, an ellipse, a rect rounded rx by ry, a line, a polyline, a polygon, paths" in {
      val svg = """<svg viewBox="0 0 100 100">
        <circle cx="15" cy="15" r="10" fill="red"/>
        <ellipse cx="50" cy="15" rx="15" ry="6" fill="green"/>
        <rect x="70" y="5" width="25" height="20" rx="10" ry="4" fill="blue"/>
        <line x1="5" y1="40" x2="45" y2="40" stroke="#ff00ff" stroke-width="4"/>
        <polyline points="55,35 95,35 95,45" fill="none" stroke="#0ff" stroke-width="2"/>
        <polygon points="5,55 45,55 25,95" fill="#ffff00"/>
        <path d="M60 75a15 15 0 1 0 30 0a15 15 0 1 0-30 0z" fill="#ff8000"/>
        <path d="M52 52h6v4H52zM60 52q4-4 8 0t8 0c0 4-16 4-16 0z" fill="#808080"/></svg>"""
      val List(circle, outCircle, ellipse, ellipseEnd, outEllipse, rect, rectSide, rectCorner, line, offLine, polyline,
        polylineInside, polygon, arc, arcTop, outArc, h, q) = pixels(svg, 100, 100)((15, 15), (15, 27), (50, 15),
        (62, 15), (50, 23), (82, 15), (71, 15), (70, 5), (25, 40), (25, 45), (75, 35), (85, 42), (25, 65), (75, 75),
        (75, 62), (61, 61), (55, 54), (68, 53))
      List(circle, ellipse, ellipseEnd, rect, rectSide, line, polyline, polygon, arc, arcTop, h, q).map(rgb) shouldBe
        List(0xff0000, 0x008000, 0x008000, 0x0000ff, 0x0000ff, 0xff00ff, 0x00ffff, 0xffff00, 0xff8000, 0xff8000,
          0x808080, 0x808080)
      List(outCircle, outEllipse, rectCorner, offLine, polylineInside, outArc).map(alpha) shouldBe List.fill(6)(0)
    }

    "follow translate, scale, rotate (about a point too), matrix and nested groups, scaling a stroke's width" in {
      val svg = """<svg viewBox="0 0 100 100">
        <rect width="10" height="10" fill="#f00" transform="translate(10 10)"/>
        <g transform="scale(2)"><rect x="20" y="5" width="5" height="5" fill="#0f0"/></g>
        <rect x="-20" y="-2" width="40" height="4" fill="#00f" transform="translate(75 25) rotate(90)"/>
        <rect width="10" height="10" fill="#ff0" transform="matrix(1 0 0 1 10 60)"/>
        <rect x="40" y="78" width="20" height="4" fill="#f0f" transform="rotate(45 50 80)"/>
        <g transform="translate(10 0)"><g transform="translate(0 80)"><rect width="5" height="5" fill="#0ff"/></g></g>
        <g transform="scale(4)"><line x1="20" y1="23" x2="24" y2="23" stroke="#800" stroke-width="1"/></g></svg>"""
      val List(moved, unmoved, scaled, turned, turnedEnd, beside, matrix, diagonal, offDiagonal, nested, thick, past) =
        pixels(svg, 100, 100)((15, 15), (5, 5), (45, 15), (75, 10), (75, 40), (85, 25), (15, 65), (54, 84), (57, 80),
          (12, 82), (88, 91), (88, 95))
      List(moved, scaled, turned, turnedEnd, matrix, diagonal, nested, thick).map(rgb) shouldBe
        List(0xff0000, 0x00ff00, 0x0000ff, 0x0000ff, 0xffff00, 0xff00ff, 0x00ffff, 0x880000)
      List(unmoved, beside, offDiagonal, past).map(alpha) shouldBe List(0, 0, 0, 0)
    }

    "fill by the even-odd rule, a shape inside another a hole, and by the non-zero rule, taken from the group" in {
      val evenOdd = """<svg viewBox="0 0 100 100"><path fill-rule="evenodd" fill="#f00"
        d="M10 10h80v80h-80z M30 30h40v40h-40z"/></svg>"""
      val nonZero = """<svg viewBox="0 0 100 100"><g style="fill-rule: nonzero; fill: #f00">
        <path d="M10 10h80v80h-80z M30 30h40v40h-40z"/></g></svg>"""
      val List(ring, hole) = pixels(evenOdd, 100, 100)((20, 20), (50, 50))
      rgb(ring) shouldBe 0xff0000
      alpha(hole) shouldBe 0
      pixels(nonZero, 100, 100)((20, 20), (50, 50)) shouldBe List(0xffff0000, 0xffff0000)
    }

    "draw fill-opacity, a group's opacity and a shape's own, an rgba colour, and a percentage" in {
      val svg = """<svg viewBox="0 0 50 10">
        <rect width="10" height="10" fill="#f00" fill-opacity="0.5"/>
        <g opacity="0.5"><rect x="10" width="10" height="10" fill="#f00" fill-opacity=".5"/></g>
        <rect x="20" width="10" height="10" fill="rgba(0, 0, 255, 0.25)"/>
        <rect x="30" width="10" height="10" fill="#f00" opacity="50%"/>
        <rect x="40" width="10" height="10" fill="none" stroke="#f00" stroke-width="4" stroke-opacity="0.5"/></svg>"""
      val alphas = pixels(svg, 50, 10)((5, 5), (15, 5), (25, 5), (35, 5), (40, 5)).map(alpha)
      List(128, 64, 64, 128, 128).zip(alphas).foreach { case (want, got) => got shouldBe want +- 2 }
    }

    "read colours as names, #rgb, #rrggbb, rgb() and currentColor, inherited from the groups round them" in {
      val svg = """<svg viewBox="0 0 60 10" color="tomato">
        <rect width="10" height="10" fill="currentColor"/>
        <g color="rgb(0, 0, 255)"><rect x="10" width="10" height="10" fill="currentColor"/></g>
        <rect x="20" width="10" height="10" fill="#0f0"/>
        <rect x="30" width="10" height="10" fill="grey"/>
        <rect x="40" width="10" height="10" fill="rgb(100%, 0%, 50%)"/>
        <g fill="#123456"><rect x="50" width="10" height="10"/></g></svg>"""
      pixels(svg, 60, 10)((5, 5), (15, 5), (25, 5), (35, 5), (45, 5), (55, 5)).map(rgb) shouldBe
        List(0xff6347, 0x0000ff, 0x00ff00, 0x808080, 0xff0080, 0x123456)
    }

    "stroke in a shape's stroke colour and width, inherited, with no fill, and its line caps" in {
      val svg = """<svg viewBox="0 0 100 100">
        <g fill="none" stroke="#f00" stroke-width="8"><rect x="10" y="10" width="80" height="40"/></g>
        <line x1="20" y1="70" x2="80" y2="70" stroke="currentColor" stroke-width="10"/>
        <line x1="20" y1="90" x2="80" y2="90" stroke="#00f" stroke-width="10" stroke-linecap="round"/></svg>"""
      val List(edge, inside, offEdge, butt, buttEnd, round) =
        pixels(svg, 100, 100)((10, 30), (50, 30), (20, 30), (50, 70), (17, 70), (17, 90))
      rgb(edge) shouldBe 0xff0000
      alpha(inside) shouldBe 0
      alpha(offEdge) shouldBe 0
      butt shouldBe 0xff000000
      alpha(buttEnd) shouldBe 0
      rgb(round) shouldBe 0x0000ff
    }

    "place its viewBox by preserveAspectRatio, always as meet, and know its own size" in {
      def wide(aspect: String) =
        s"""<svg viewBox="0 0 20 10" $aspect><rect width="20" height="10" fill="#f00"/></svg>"""
      pixels(wide(""), 40, 40)((20, 5), (20, 20), (20, 35)).map(alpha) shouldBe List(0, 255, 0)
      pixels(wide("""preserveAspectRatio="xMinYMin meet""""), 40, 40)((20, 5), (20, 35)).map(alpha) shouldBe
        List(255, 0)
      pixels(wide("""preserveAspectRatio="xMaxYMax""""), 40, 40)((20, 5), (20, 35)).map(alpha) shouldBe List(0, 255)
      pixels(wide("""preserveAspectRatio="none""""), 40, 40)((20, 5), (20, 35)).map(alpha) shouldBe List(255, 255)
      art(wide("")).natural shouldBe (20.0, 10.0)
      art("""<svg width="40" viewBox="0 0 20 10"/>""").natural shouldBe (40.0, 20.0)
      art("""<svg width="30px" height="12" viewBox="0 0 20 10"/>""").natural shouldBe (30.0, 12.0)
      art("""<svg width="100%" viewBox="0 0 20 10"/>""").natural shouldBe (20.0, 10.0)
      // drawn at a size in its own proportion, covering the box it is shown in
      SvgImage.pixels(art(wide("")), 40.0, 40.0) shouldBe (80, 40)
      SvgImage.pixels(art(wide("")), 1e6, 1e6) shouldBe (4096, 2048)
    }
  }

  "What it does not draw" should {
    "be logged once, a gradient drawn in its first colour (defined before or after it) and the rest drawn" in {
      val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 30 10">
        <defs><linearGradient id="early"><stop offset="0" stop-color="#00f"/><stop offset="1" stop-color="#f00"/>
        </linearGradient></defs>
        <rect width="10" height="10" fill="url(#early)"/>
        <rect x="10" width="10" height="10" fill="#0f0" filter="url(#blur)"/>
        <rect x="20" width="10" height="10" style="fill: url('#late')"/>
        <text x="0" y="8">Words</text>
        <defs><radialGradient id="late"><stop offset="0" style="stop-color: #ff0; stop-opacity: 1"/></radialGradient>
        <filter id="blur"><feGaussianBlur stdDeviation="2"/></filter></defs></svg>"""
      art(svg).skipped shouldBe List("gradients (drawn in their first colour)", "filters", "text")
      pixels(svg, 30, 10)((5, 5), (15, 5), (25, 5)).map(rgb) shouldBe List(0x0000ff, 0x00ff00, 0xffff00)
      val url = file("skipping.svg", svg)
      val image = load(url)
      (image.width, image.height) shouldBe (30.0, 10.0)
      val said = "Image: " + url + " is drawn without its gradients (drawn in their first colour), filters, text"
      SvgImage.logged.count(_ == said) shouldBe 1
      // once, however often it is drawn
      load(url)
      SvgImage.logged.count(_ == said) shouldBe 1
    }
  }

  "A picture" should {
    "be an SVG by its .svg, by its first bytes under another name, and as a data: address, at its own size" in {
      val byName = load(file("tile.svg", tile))
      (byName.width, byName.height) shouldBe (48.0, 48.0)
      val sniffed = load(file("tile.bin", "\n<?xml version=\"1.0\"?>\n" + tile))
      (sniffed.width, sniffed.height) shouldBe (48.0, 48.0)
      val encoded = "data:image/svg+xml;base64," +
        java.util.Base64.getEncoder.encodeToString(tile.getBytes(StandardCharsets.UTF_8))
      val data = load(encoded)
      (data.width, data.height) shouldBe (48.0, 48.0)
      val escaped = load("data:image/svg+xml," + java.net.URLEncoder.encode(tile, "UTF-8").replace("+", "%20"))
      (escaped.width, escaped.height) shouldBe (48.0, 48.0)
      fx(Host.image(sniffed.source).getPixelReader.getArgb(math.round(24 * Host.density).toInt,
        math.round(24 * Host.density).toInt)) shouldBe 0xffffffff
    }

    "that is neither a raster image nor an SVG be logged as one that could not be decoded, 0 by 0" in {
      val url = file("words.png", "Not a picture at all, only words.")
      val image = load(url)
      (image.width, image.height) shouldBe (0.0, 0.0)
      SvgImage.logged should contain("Image: " + url + " could not be decoded (not a raster image or SVG?)")
      // one that cannot be had at all is not one that could not be decoded
      val missing = "file://" + new File(dir, "nowhere.png").getAbsolutePath
      load(missing).width shouldBe 0.0
      SvgImage.logged.exists(_.contains("nowhere.png")) shouldBe false
    }

    "be drawn again sharper where a view shows it larger than its own size" in {
      val url = file("small.svg", """<svg viewBox="0 0 10 10"><circle cx="5" cy="5" r="5" fill="#0a0"/></svg>""")
      val view = fx {
        ui.children.clear()
        val v = new ImageView
        v.sizedByPicture @= false
        v.size.width @= 120.0
        v.size.height @= 120.0
        ui.children += v
        v
      }
      val loaded = load(url)
      fx(view.image @= loaded)
      val fxView = view.node.fx.asInstanceOf[javafx.scene.image.ImageView]
      eventually(fxView.getImage != null && fxView.getImage.getWidth >= 120.0 * Host.density) shouldBe true
      fx(fxView.getImage.getPixelReader.getArgb(60, 60)) shouldBe 0xff00aa00
    }
  }

  "An avatar whose picture is an SVG" should {
    "show the picture, not its initial, drawn at its size" in {
      val url = file("face.svg", tile)
      val face = fx {
        ui.children.clear()
        val a = new Avatar(Participant("p-svg", "Sam", Some(url)), 40.0)
        ui.children += a
        a
      }
      def picture: ImageView = face.childComponents.collectFirst { case p: ImageView => p }.get
      eventually(picture.visible()) shouldBe true
      fx(face.childComponents.collectFirst { case t: TextView => t }.get.visible()) shouldBe false
      val drawn = fx(picture.node.fx.asInstanceOf[javafx.scene.image.ImageView].getImage)
      drawn should not be null
      drawn.getWidth should be >= 40.0 * fx(Host.density)
      fx(drawn.getPixelReader.getArgb((drawn.getWidth / 2).toInt, (drawn.getHeight / 2).toInt)) shouldBe 0xffffffff
    }
  }
}
