package everyui.image

import everyui.draw.{ArtPen, Path, PathData, SvgColor, VectorArt}
import everyui.log.LoggedAddress
import everyui.render.Host
import everyui.{Color, FxSpec, ui}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.util.Random

/** SVG reading on hostile input: names of colons alone and namespaces cut short, truncated tags, unterminated quotes
  * and comments, XML that is no SVG, an empty file, binary garbage, deep nesting, long transforms and fallbacks, and
  * huge numbers and NaN, each read as None or as a sane partial picture and never an exception; first bytes sniffed as
  * an SVG only where its root is `<svg>`; image addresses logged without their query or a data: address's contents;
  * and a seeded fuzz of a good SVG mutated thousands of times, read, painted and sized without an exception. */
class HostileSvgSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    offStage()
    super.afterAll()
  }

  // a good SVG with something of everything the reader takes: a gradient, a group's transform and style, an arc, a
  // namespaced attribute and a comment
  private val good = """<?xml version="1.0" encoding="UTF-8"?>
    <!-- a tile -->
    <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="48" height="48"
         viewBox="0 0 48 48">
    <defs><linearGradient id="g"><stop offset="0" stop-color="#00f"/></linearGradient>
    <linearGradient id="h" xlink:href="#g"/></defs>
    <rect width="48" height="48" rx="12" fill="#D97757"/>
    <g transform="translate(12 12) scale(1.5) rotate(10 8 8)" style="fill: #fff; stroke: rgba(0, 0, 0, 0.5)">
    <path d="M8 0L16 8 8 16 0 8z a4 4 0 1 0 8 0"/><circle cx="8" cy="8" r="3" fill="url(#h)"/></g>
    <polyline points="0,0 10,10 20,0" stroke="tomato" fill="none" stroke-width="2"/></svg>"""

  // every number of `art` one a picture can be drawn with
  private def sane(art: VectorArt): Boolean = {
    def finite(v: Double): Boolean = !v.isNaN && !v.isInfinite
    art.shapes.forall(f => finite(f.width) && f.path.ops.forall(_.values.forall(finite))) &&
      List(art.left, art.top, art.width, art.height, art.naturalWidth, art.naturalHeight).forall(finite)
  }

  // a pen that draws nothing, but takes every call a platform's would
  private object nowhere extends ArtPen {
    var calls = 0
    def fill(path: Path, color: Color, evenOdd: Boolean): Unit = calls += 1
    def stroke(path: Path, color: Color, width: Double, cap: String, join: String): Unit = calls += 1
  }

  // `svg` read every way an app's picture is: never an exception, and any art sane, painted and sized
  private def read(svg: String): Option[VectorArt] = {
    val art = VectorArt.parse(svg)
    art.foreach { a =>
      sane(a) shouldBe true
      a.paint(nowhere, 64.0, 64.0)
      a.figures(24.0, Color.Black)
    }
    SvgImage.art(svg).foreach { a =>
      val (w, h) = SvgImage.pixels(a, 1e6, 1e6)
      (w <= SvgImage.largest.toInt && h <= SvgImage.largest.toInt) shouldBe true
    }
    SvgImage.sniffed(svg)
    art
  }

  private val dir = new File("target/hostilesvg")
  private def file(name: String, text: String): String = {
    dir.mkdirs()
    val f = new File(dir, name)
    Files.write(f.toPath, text.getBytes(StandardCharsets.UTF_8))
    "file://" + f.getAbsolutePath
  }
  private def load(url: String): Image = {
    var got: Option[Image] = None
    fx(Image(url).map(i => got = Some(i)).startUnit())
    eventually(got.nonEmpty) shouldBe true
    got.get
  }
  private val square = """<rect width="10" height="10" fill="#0f0"/>"""

  "A tag whose name is no XML name" should {
    "be passed by, the rest read: colons alone, a prefix with nothing after it and nothing before a colon" in {
      for (bad <- List("<:>", "<::>", "<a:>", "<:b>", "</:>", "<:/>", "<a:b:c>", "<1a>", "< svg>")) {
        read(bad) shouldBe None
        val art = read("""<svg viewBox="0 0 10 10">""" + bad + square + bad + "</svg>").get
        art.shapes.size shouldBe 1
      }
      read("<:svg>") shouldBe None
    }
    "keep a namespaced SVG, its root and its shapes under a prefix" in {
      val art = read("""<svg:svg xmlns:svg="http://www.w3.org/2000/svg" viewBox="0 0 20 10"><svg:g svg:fill="#f00">
        <svg:rect width="10" height="10"/></svg:g></svg:svg>""").get
      (art.width, art.height) shouldBe (20.0, 10.0)
      art.shapes.map(_.fill) shouldBe List(Some(Color.fromHex("ff0000")))
    }
  }

  "An attribute whose name is no XML name" should {
    "be passed by, the others read" in {
      val art = read("""<svg ::="1" a:="2" :b="3" viewBox="0 0 10 10" :="4"><rect ::=x width="10" a:="y" :b='z'
        height="10" fill="#00f"/></svg>""").get
      (art.width, art.height) shouldBe (10.0, 10.0)
      art.shapes.map(_.fill) shouldBe List(Some(Color.fromHex("0000ff")))
      // of words with no value before a name, the name is read
      read("""<svg hidden viewBox="0 0 30 10"></svg>""").get.width shouldBe 30.0
    }
  }

  "Text cut short" should {
    "read as far as it goes" in {
      read("<") shouldBe None
      read("<svg").get.shapes shouldBe Nil
      read("<svg width=").get.shapes shouldBe Nil
      read("""<svg width="10""").get.shapes shouldBe Nil
      read("""<svg viewBox="0 0 10 10"><path d="M0 0 L""").get.shapes.map(_.path.svg) shouldBe List("M 0 0")
      read("""<svg viewBox="0 0 10 10"><path d="M0 0 L5 5 A""").get.shapes.map(_.path.svg) shouldBe List("M 0 0 L 5 5")
      read("""<svg viewBox="0 0 10 10" fill='#f00><rect width="10" height="10"/></svg>""")
      read("""<svg viewBox="0 0 10 10"><!-- never closed """ + square).get.shapes shouldBe Nil
      read("""<svg viewBox="0 0 10 10"><![CDATA[ never closed """ + square).get.shapes shouldBe Nil
      read("""<svg viewBox="0 0 10 10"><!DOCTYPE never closed""").get.shapes shouldBe Nil
      read("""<svg viewBox="0 0 10 10"><rect transform="scale(2" width="10" height="10"/>""").get.shapes.size shouldBe 1
      read("""<svg viewBox="0 0 10 10"><rect style="fill:" width="10" height="10"/>""").get.shapes.size shouldBe 1
    }
  }

  "What is no SVG" should {
    "be None: other XML, an HTML page, an empty file and binary garbage" in {
      read("""<?xml version="1.0"?><rss version="2.0"><channel><title>t</title></channel></rss>""") shouldBe None
      read("""<?xml version="1.0" encoding="utf-8"?><vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="24dp" android:height="24dp"><path android:pathData="M0 0h24v24H0z"/></vector>""") shouldBe None
      read("<!DOCTYPE html><html><body>Not found</body></html>") shouldBe None
      read("") shouldBe None
      VectorArt.parse(null) shouldBe None
      SvgImage.art(null) shouldBe None
      SvgImage.sniffed(null) shouldBe false
      val random = new Random(7L)
      for (_ <- 1 to 200) {
        val bytes = new Array[Byte](random.nextInt(2048))
        random.nextBytes(bytes)
        read(DataAddress.text(bytes))
        read(DataAddress.text(Array[Byte](0x89.toByte, 'P', 'N', 'G', 13, 10, 26, 10) ++ bytes))
      }
    }
  }

  "A file built to be slow" should {
    "be read in time linear in its length: deep nesting, a long transform and a long fallback" in {
      val depth = 100000
      val nested = read("""<svg viewBox="0 0 10 10" fill="#f00">""" + "<g>" * depth +
        """<rect width="10" height="10"/>""" +
        "</g>" * depth + "</svg>").get
      nested.shapes.map(_.fill) shouldBe List(Some(Color.fromHex("ff0000")))
      val unseen = read("""<svg viewBox="0 0 10 10">""" + "<defs>" * depth + square + "</svg>").get
      unseen.shapes shouldBe Nil
      val moved = read("""<svg viewBox="0 0 10 10"><rect transform="""" + "translate(0.5 0) " * depth +
        """" width="1" height="1"/></svg>""").get
      moved.shapes.head.path.ops.head.values shouldBe List(50000.0, 0.0)
      val fallbacks = read("""<svg viewBox="0 0 10 10"><rect fill="""" + "url(#none) " * depth +
        """" width="10" height="10"/></svg>""").get
      fallbacks.shapes shouldBe Nil
      read("<svg>" + "<:>" * depth + "</svg>").get.shapes shouldBe Nil
      read("""<svg viewBox="0 0 10 10"><path d="M0 0""" + " L1 1" * depth + """"/></svg>""").get.shapes.size shouldBe 1
    }
  }

  "Numbers past what a Double holds, and NaN" should {
    "be no number, and a shape a transform takes past them left out" in {
      val nan = read("""<svg viewBox="0 0 NaN 10" width="NaN" height="1e999"><rect width="10" height="10"/>
        </svg>""").get
      (nan.width, nan.height, nan.naturalWidth, nan.naturalHeight) shouldBe (24.0, 24.0, 0.0, 0.0)
      val huge = read("""<svg viewBox="0 0 1e999 10" width="1e999"><rect width="1e999" height="10"/>
        <rect width="10" height="10" opacity="NaN" fill-opacity="1e999" stroke-width="NaN" stroke="#000"/>
        <rect width="1e300" height="1e300" transform="scale(1e300)"/><rect width="10" height="10" fill="#0f0"/>
        </svg>""").get
      huge.skipped shouldBe List("shapes too large to draw")
      huge.shapes.size shouldBe 2
      huge.shapes.head.fill shouldBe Some(Color.Black)
      // a path ends at a number it cannot hold, or an arc that comes to NaN, keeping what came before
      PathData.parse("M0 0 L1 1 L1e999 0 L2 2").svg shouldBe "M 0 0 L 1 1"
      PathData.parse("M0 0 L1 1 A1e200 1e200 0 0 1 1e200 0 L2 2").svg shouldBe "M 0 0 L 1 1"
      read("""<svg viewBox="0 0 10 10"><path d="m1e308 0 1e308 0 1e308 0"/></svg>""").get.shapes shouldBe Nil
      // its pixels capped at 4096 on its longer side however large it says it is
      val big = SvgImage.art("""<svg width="1e300" height="1" viewBox="0 0 1e300 1"></svg>""").get
      SvgImage.pixels(big, 1e9, 1e9)._1 should be <= 4096
    }
  }

  "A colour that is no colour" should {
    "be None, never read past the names" in {
      SvgColor("9acd32") shouldBe None
      SvgColor("fffafa") shouldBe None
      SvgColor("rgb(1e999, 0, 0)") shouldBe None
      SvgColor("rgba(0, 0, 0, NaN)") shouldBe None
      SvgColor("#12345") shouldBe None
      SvgColor(null) shouldBe None
      SvgColor("snow") shouldBe Some(Color.fromHex("fffafa"))
      SvgColor("yellowgreen") shouldBe Some(Color.fromHex("9acd32"))
      read("""<svg viewBox="0 0 10 10"><rect width="10" height="10" fill="9acd32"/></svg>""").get.shapes
        .map(_.fill) shouldBe List(Some(Color.Black))
    }
  }

  "A data: address that cannot be read" should {
    "be None or its bytes, never an exception" in {
      for (bad <- List("data:", "data:,", "data:,%", "data:,%4", "data:,%zz", "data:image/svg+xml;base64,@@@=",
        "data:;base64,", "data:image/svg+xml;base64", "data:,<:>")) {
        DataAddress(bad).foreach { case (_, bytes) => read(DataAddress.text(bytes)) }
      }
      DataAddress("data:,%4").map(_._2.length) shouldBe Some(2)
    }
  }

  "A base64 data: address" should {
    "read as a MIME decoder reads it, line breaks and all, by hand (java.util.Base64 is Android 8's)" in {
      val random = new scala.util.Random(7)
      for (length <- 0 to 70) {
        val bytes = Array.fill(length)(random.nextInt(256).toByte)
        val written = java.util.Base64.getMimeEncoder(16, "\r\n".getBytes).encodeToString(bytes)
        DataAddress("data:application/octet-stream;base64," + written).map(_._2.toList) shouldBe Some(bytes.toList)
      }
    }
  }

  "First bytes" should {
    "be an SVG's only where the first element is <svg>, after a preamble of any length" in {
      val preamble = "﻿ \n<?xml version=\"1.0\"?>\n<!-- a licence " + "x" * 2000 + " -->\n" +
        "<?xml-stylesheet href=\"a.css\"?><!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" " +
        "\"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\" [ <!ENTITY e \"<rss>\"> ]>\n"
      SvgImage.sniffed(preamble + "<svg viewBox=\"0 0 1 1\"/>") shouldBe true
      SvgImage.sniffed(preamble + "<svg:svg xmlns:svg=\"http://www.w3.org/2000/svg\">") shouldBe true
      SvgImage.sniffed("<svg>") shouldBe true
      SvgImage.sniffed("<svg\n") shouldBe true
      SvgImage.sniffed(good) shouldBe true
      SvgImage.sniffed(preamble + "<rss>") shouldBe false
      SvgImage.sniffed("<?xml version=\"1.0\"?>") shouldBe false
      SvgImage.sniffed("<?xml version=\"1.0\"?><vector xmlns:android=\"x\"><svg/></vector>") shouldBe false
      SvgImage.sniffed("<html><body><svg></svg></body></html>") shouldBe false
      SvgImage.sniffed("<!-- <svg> -->") shouldBe false
      SvgImage.sniffed("<!-- never closed <svg>") shouldBe false
      SvgImage.sniffed("<svgfoo>") shouldBe false
      SvgImage.sniffed("<svg") shouldBe false
      SvgImage.sniffed("<a:svg:svg>") shouldBe false
      SvgImage.sniffed("<:>") shouldBe false
      SvgImage.sniffed("") shouldBe false
      SvgImage.sniffed("\u0089PNG\r\n\u001a\n") shouldBe false
    }
    "leave XML that is no SVG to be logged as undecoded on the desktop, and a malformed .svg too" in {
      val feed = file("feed.bin", """<?xml version="1.0"?><rss version="2.0"><channel/></rss>""")
      load(feed).width shouldBe 0.0
      SvgImage.logged should contain("Image: " + feed + " could not be decoded (not a raster image or SVG?)")
      val colons = file("colons.svg", "<:><::><a:><:b>")
      load(colons).width shouldBe 0.0
      SvgImage.logged should contain("Image: " + colons + " could not be decoded (not a raster image or SVG?)")
    }
  }

  "An image's address in a log line" should {
    "be its scheme, host and path alone, ?… for its query, and a data: address its type and length" in {
      val signed = "https://user:pass@cdn.example.com/api/stored-file/7?X-Amz-Signature=abc123&Expires=99#top"
      LoggedAddress(signed) shouldBe "https://cdn.example.com/api/stored-file/7?…"
      LoggedAddress("https://cdn.example.com/a.svg") shouldBe "https://cdn.example.com/a.svg"
      LoggedAddress("https://cdn.example.com/a.svg#frag") shouldBe "https://cdn.example.com/a.svg?…"
      LoggedAddress("https://u@cdn.example.com?x=1") shouldBe "https://cdn.example.com?…"
      LoggedAddress("/images/a.png?v=2") shouldBe "/images/a.png?…"
      LoggedAddress("data:image/svg+xml;base64,PHN2Zz4=") shouldBe "data:image/svg+xml (34 characters)"
      LoggedAddress("data:secret-words-without-a-comma") shouldBe "data: (33 characters)"
      LoggedAddress("data:a b,c") shouldBe "data: (10 characters)"
      LoggedAddress(null) shouldBe ""
      SvgImage.undecoded(signed)
      SvgImage.undrawn(signed + "&more=1", "a Roku")
      SvgImage.drawn(signed, VectorArt(0, 0, 1, 1, Nil, skipped = List("text")))
      SvgImage.logged should contain("Image: https://cdn.example.com/api/stored-file/7?… could not be decoded " +
        "(not a raster image or SVG?)")
      SvgImage.logged should contain("Image: https://cdn.example.com/api/stored-file/7?… is an SVG, which a Roku " +
        "cannot draw (an Avatar keeps its initial)")
      SvgImage.logged should contain("Image: https://cdn.example.com/api/stored-file/7?… is drawn without its text")
      SvgImage.undecoded("data:image/svg+xml;base64,PHN2Zz48Oj4=")
      SvgImage.logged should contain("Image: data:image/svg+xml (38 characters) could not be decoded " +
        "(not a raster image or SVG?)")
      SvgImage.logged.exists(l => l.contains("abc123") || l.contains("user:pass") || l.contains("PHN2Zz")) shouldBe
        false
    }
  }

  "A good SVG mutated at random" should {
    "never make the reader throw, whatever it is made into (a fixed seed, 5000 mutations)" in {
      val random = new Random(20261110L)
      val marks = "<>:=\"'/!-?[]&#;() \n\t,.eE+-0123456789svgpathMLAZ"
      var mutated = good
      var drawn = 0
      for (i <- 1 to 5000) {
        // from the good file, or on from the last mutation, now and then
        val from = if (random.nextInt(4) == 0) mutated else good
        val at = if (from.isEmpty) 0 else random.nextInt(from.length)
        val c = if (random.nextInt(5) == 0) random.nextInt(0x3000).toChar
          else marks.charAt(random.nextInt(marks.length))
        mutated = random.nextInt(7) match {
          case 0 => from.substring(0, at) + c + from.substring(math.min(from.length, at + 1))
          case 1 => from.substring(0, at) + c + from.substring(at)
          case 2 => from.substring(0, at) + from.substring(math.min(from.length, at + 1 + random.nextInt(8)))
          case 3 => from.substring(0, at)
          case 4 =>
            val end = math.min(from.length, at + random.nextInt(40))
            from.substring(0, end) + from.substring(at, end) + from.substring(end)
          case 5 =>
            val bytes = from.getBytes(StandardCharsets.UTF_8)
            if (bytes.nonEmpty) bytes(random.nextInt(bytes.length)) = random.nextInt(256).toByte
            DataAddress.text(bytes)
          case _ => from.substring(0, at) + List("<:>", "<::>", "::=", "a:=", ":b=", "1e999", "NaN", "url(", "<!--",
            "\"", "'", "<![CDATA[", "<svg", "</g>", "<g>", "-", ".", "e")(random.nextInt(18)) + from.substring(at)
        }
        read(mutated).foreach { art =>
          // now and then drawn on the desktop's own canvas too
          if (i % 100 == 0 && art.width > 0.0 && art.height > 0.0) {
            fx(Host.svgPicture(art, 32, 32))
            drawn += 1
          }
        }
      }
      drawn should be > 0
    }
  }
}
