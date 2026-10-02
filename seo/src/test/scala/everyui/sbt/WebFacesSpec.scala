package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** An app's faces as its build declares them, shipped as WOFF2 cut to what it draws, with the page's rules and
  * preloads (WebFaces): the icons its scripts reach, the slices of a face for words, the faces at the start and those
  * fetched later, and a page that declares a face the build does. */
class WebFacesSpec extends AnyWordSpec with Matchers {
  private val fonts = Seq("assets/fonts", "../assets/fonts").map(new File(_)).find(_.isDirectory).get
  private def read(file: File): String = new String(Files.readAllBytes(file.toPath), UTF_8)
  private val page = "<html><head><title>App</title></head><body><script src=\"main.js\"></script></body></html>"

  // a staged bundle: index.html, main.js naming `inJs`, and a WebAssembly build naming `inWasm` as Scala.js does
  private def staged(inJs: String, inWasm: Seq[Int] = Nil, html: String = page): File = {
    val out = Files.createTempDirectory("webfaces").toFile
    Files.write(new File(out, "index.html").toPath, html.getBytes(UTF_8))
    Files.write(new File(out, "main.js").toPath, inJs.getBytes(UTF_8))
    if (inWasm.nonEmpty) {
      val imports = new java.io.ByteArrayOutputStream()
      imports.write(inWasm.size)
      inWasm.foreach { c =>
        val name = new String(Character.toChars(c)).getBytes(UTF_8)
        imports.write(0)                        // the module "", where Scala.js imports its strings from
        imports.write(name.length); imports.write(name)
        imports.write(Array[Byte](3, 0x64, 0x6F, 0))   // a global, (ref extern), not mutable
      }
      val section = imports.toByteArray
      val wasm = Array[Byte](0, 'a', 's', 'm', 1, 0, 0, 0, 2, section.length.toByte) ++ section
      new File(out, "wasm-0123/").mkdirs()
      Files.write(new File(out, "wasm-0123/main.wasm").toPath, wasm)
    }
    out
  }

  "WebFaces.reached" should {
    "find the private use characters main.js names, as themselves and as escapes, and those main.wasm imports" in {
      val out = staged("var a=\"\\ue145\";var b=\"\ue5cd\";var c='\\u{F0001}';var d=\"x\\u0041\"", Seq(0xE8B6))
      WebFaces.reached(out) shouldBe Set(0xE145, 0xE5CD, 0xF0001, 0xE8B6)
    }

    "say the scripts are to be written first where there are none" in {
      val out = Files.createTempDirectory("webfaces").toFile
      an[RuntimeException] should be thrownBy WebFaces.reached(out)
    }
  }

  "WebFaces.writeTo" should {
    lazy val out = {
      val o = staged("render(\"\\ue145\", \"\\ue5cd\")")
      WebFaces(fonts, WebFace.bundled).writeTo(o)
      o
    }
    lazy val html = read(new File(out, "index.html"))

    "write each face as WOFF2, a face for words in slices by script and the icons in one file" in {
      val files = new File(out, "fonts").list().toSet
      files should contain("OpenSans-Regular.latin.woff2")
      files should contain("OpenSans-SemiBold.latin.woff2")
      files should contain("RobotoMono-Regular.latin.woff2")
      files should contain("RobotoMono-Regular.cyrillic.woff2")
      files should contain("MaterialIcons-Regular.woff2")
      files.filter(_.endsWith(".ttf")) shouldBe empty
    }

    "cut the icon face to the icons the scripts name" in {
      val woff = Files.readAllBytes(new File(out, "fonts/MaterialIcons-Regular.woff2").toPath)
      woff.length should be < 4000
      val icons = WebFaces.files(fonts, WebFace.MaterialIcons, Set(0xE145, 0xE5CD)).head
      icons.bytes.toSeq shouldBe woff.toSeq
    }

    "write a rule for each file, a slice's with the characters it holds" in {
      html should include("""@font-face { font-family: 'Material Icons'; font-style: normal; font-weight: 400; src: url(fonts/MaterialIcons-Regular.woff2) format('woff2'); }""")
      html should include("""font-family: 'Open Sans'; font-style: normal; font-weight: 600; src: url(fonts/OpenSans-SemiBold.latin.woff2) format('woff2'); unicode-range: U+""")
      val cyrillic = html.linesIterator.find(_.contains("RobotoMono-Regular.cyrillic.woff2")).get
      cyrillic should include("unicode-range: U+")
      cyrillic should include(", U+400-")
      cyrillic should not include ("U+20-7E")
      html.indexOf("<style id=\"everyui-fonts\">") should be < html.indexOf("</head>")
    }

    "preload the faces at the start, a face in slices by its Latin one, and not those fetched later" in {
      html should include("""<link rel="preload" href="fonts/OpenSans-Regular.latin.woff2" as="font" type="font/woff2" crossorigin>""")
      html should include("""<link rel="preload" href="fonts/MaterialIcons-Regular.woff2" as="font" type="font/woff2" crossorigin>""")
      html should not include "rel=\"preload\" href=\"fonts/RobotoMono"
      html should not include "rel=\"preload\" href=\"fonts/OpenSans-Regular.cyrillic"
    }

    "tell the runtime which faces to wait for and which come later" in {
      html should include("""<meta name="everyui-fonts" content="normal 400 Open Sans; normal 600 Open Sans; normal 400 Material Icons" data-later="normal 400 Roboto Mono">""")
    }

    "put the combining marks in every slice, so a letter and its accent are drawn from one file" in {
      def characters(range: String): Set[Int] = range.split(", ").toSet.flatMap { r =>
        val ends = r.stripPrefix("U+").split("-").map(Integer.parseInt(_, 16))
        (ends.head to ends.last).toSet
      }
      val marks = Sfnt.read(new File(fonts, "RobotoMono-Regular.ttf")).characters.keySet.filter(c => c >= 0x300 && c <= 0x36F)
      marks should not be empty
      val slices = WebFaces.files(fonts, WebFace.RobotoMono, Set.empty)
      slices.map(_.slice.get) should contain allOf ("latin", "latin-ext", "cyrillic", "greek")
      slices.foreach(f => (marks -- characters(f.range.get)) shouldBe empty)
      // and each other character in one slice only
      val all = slices.toList.flatMap(f => (characters(f.range.get) -- marks).toList)
      all.size shouldBe all.distinct.size
    }

    "ship no icon face, and declare none, where the scripts name no icon" in {
      val o = staged("render(\"words\")")
      WebFaces(fonts, WebFace.bundled).writeTo(o)
      new File(o, "fonts/MaterialIcons-Regular.woff2").exists shouldBe false
      read(new File(o, "index.html")) should not include "Material Icons"
    }

    "refuse a page that declares a face the build does, rather than fetch it twice" in {
      val o = staged("x", html = page.replace("</head>",
        "<style>@font-face { font-family: 'Open Sans'; src: url(fonts/OpenSans-Regular.ttf); }</style></head>"))
      val e = the[RuntimeException] thrownBy WebFaces(fonts, WebFace.bundled).writeTo(o)
      e.getMessage should include("Open Sans")
    }

    "name a face that is not among the fonts" in {
      val e = the[RuntimeException] thrownBy WebFaces(fonts, Seq(WebFace("Brand", 400, "Brand.ttf"))).writeTo(staged("x"))
      e.getMessage should include("Brand.ttf")
    }
  }

  "WebFaces.unicodeRange" should {
    "write runs of characters as ranges" in {
      WebFaces.unicodeRange(Set(0x20, 0x21, 0x22, 0x41, 0x100, 0x101)) shouldBe "U+20-22, U+41, U+100-101"
    }
  }
}
