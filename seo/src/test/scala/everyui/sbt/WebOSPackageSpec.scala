package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, File}
import java.nio.charset.StandardCharsets.{US_ASCII, UTF_8}
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/** An app's web bundle as an LG webOS package (WebOSPackage, WebOSApp): its appinfo.json, and the `.ipk` opened, its
  * members, their paths and ownership as LG's `ares-package` writes them. */
class WebOSPackageSpec extends AnyWordSpec with Matchers {
  private def write(root: File, path: String, bytes: Array[Byte]): File = {
    val f = new File(root, path)
    f.getParentFile.mkdirs()
    Files.write(f.toPath, bytes)
    f
  }
  private def png(root: File, name: String, width: Int, height: Int): File = {
    val f = new File(root, name)
    f.getParentFile.mkdirs()
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", f)
    f
  }
  private def size(bytes: Array[Byte]): (Int, Int) = {
    val image = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes))
    (image.getWidth, image.getHeight)
  }

  // an ar archive's members, in order: name, owner, group, mode, bytes
  private def ar(bytes: Array[Byte]): List[(String, String, String, String, Array[Byte])] = {
    new String(bytes, 0, 8, US_ASCII) shouldBe "!<arch>\n"
    var at = 8
    var found = List.empty[(String, String, String, String, Array[Byte])]
    while (at < bytes.length) {
      val h = new String(bytes, at, 60, US_ASCII)
      h.substring(58, 60) shouldBe "`\n"
      val length = h.substring(48, 58).trim.toInt
      found :+= ((h.substring(0, 16).trim, h.substring(28, 34).trim, h.substring(34, 40).trim, h.substring(40, 48).trim,
        bytes.slice(at + 60, at + 60 + length)))
      at += 60 + length + length % 2
    }
    found
  }

  // a gzipped tar's entries, in order: path, type, mode, owner, group, bytes
  private def tar(gz: Array[Byte]): List[(String, Char, Int, Int, Int, Array[Byte])] = {
    val bytes = new GZIPInputStream(new ByteArrayInputStream(gz)).readAllBytes()
    def field(at: Int, length: Int): String = new String(bytes, at, length, US_ASCII).takeWhile(_ != '\u0000')
    def octal(at: Int, length: Int): Int = Integer.parseInt(field(at, length).trim, 8)
    var at = 0
    var found = List.empty[(String, Char, Int, Int, Int, Array[Byte])]
    while (at + 512 <= bytes.length && bytes(at) != 0) {
      field(at + 257, 6) shouldBe "ustar"
      val sum = (0 until 512).map(i => if (i >= 148 && i < 156) ' '.toInt else bytes(at + i) & 0xff).sum
      octal(at + 148, 8) shouldBe sum
      val prefix = field(at + 345, 155)
      val path = (if (prefix.isEmpty) "" else prefix + "/") + field(at, 100)
      val length = octal(at + 124, 12)
      found :+= ((path, bytes(at + 156).toChar, octal(at + 100, 8), octal(at + 108, 8), octal(at + 116, 8),
        bytes.slice(at + 512, at + 512 + length)))
      at += 512 + (length + 511) / 512 * 512
    }
    found
  }

  private def app(root: File): WebOSApp = WebOSApp(id = "com.example.tv", version = "1.2.3", vendor = "Example",
    title = "Example \"TV\"", icon = png(root, "art/icon.png", 512, 512), splash = Some(png(root, "art/splash.png", 960, 540)))

  // a page as a bundle for a site writes it: a base at the root, a face preloaded
  private val page =
    """<head>
      |  <base href="/">
      |  <link rel="preload" href="fonts/OpenSans-Regular.woff2" as="font" type="font/woff2" crossorigin>
      |  <link rel="stylesheet" href="/app.css">
      |</head>
      |<script src="main.js"></script>
      |""".stripMargin

  // a web bundle: its page, its script (and a CDN's precompressed copy of it), its faces and pictures
  private def bundle(root: File): File = {
    val b = new File(root, "bundle")
    write(b, "index.html", page.getBytes(UTF_8))
    write(b, "main.js", Array.fill(1001)('x'.toByte))
    write(b, "main.js.br", Array[Byte](1, 2, 3))
    write(b, "fonts/OpenSans-Regular.woff2", Array.fill(700)(7.toByte))
    write(b, "images/logo.png", Array[Byte](9))
    write(b, "a/" + "d" * 90 + "/deep.txt", "deep".getBytes(UTF_8))
    b
  }

  "WebOSApp" should {
    "write the appinfo.json LG's schema asks for, Back the app's" in {
      val root = Files.createTempDirectory("webos").toFile
      val info = app(root).copy(iconColor = Some("#101820"), requiredMemory = Some(300),
        extra = Seq("transparent" -> "false", "id" -> "\"ignored\"")).appinfo
      List("\"id\": \"com.example.tv\"", "\"version\": \"1.2.3\"", "\"vendor\": \"Example\"", "\"type\": \"web\"",
        "\"main\": \"index.html\"", "\"title\": \"Example \\\"TV\\\"\"", "\"icon\": \"icon.png\"",
        "\"largeIcon\": \"largeIcon.png\"", "\"resolution\": \"1920x1080\"", "\"disableBackHistoryAPI\": true",
        "\"requiredACG\": []", "\"splashBackground\": \"splash.png\"", "\"iconColor\": \"#101820\"",
        "\"requiredMemory\": 300", "\"transparent\": false").foreach(f => info should include(f))
      info should not include "ignored"
    }
    "say what LG would refuse" in {
      val root = Files.createTempDirectory("webos").toFile
      val ok = app(root)
      ok.problems shouldBe Nil
      ok.copy(id = "Com.Example").problems.head should startWith("id")
      ok.copy(id = "com.lge.app").problems.head should startWith("id")
      ok.copy(version = "1.02.0").problems.head should startWith("version")
      ok.copy(version = "1.2").problems.head should startWith("version")
      ok.copy(resolution = "3840x2160").problems.head should startWith("resolution")
      ok.copy(icon = new File(root, "none.png")).problems.head should startWith("icon")
    }
  }

  "PackagedPage" should {
    "make a page run from its files: its base and its root addresses relative, its font preloads gone" in {
      PackagedPage.local(page) shouldBe
        """<head>
          |  <base href="./">
          |  <link rel="stylesheet" href="app.css">
          |</head>
          |<script src="main.js"></script>
          |""".stripMargin
    }
    "leave a page with neither as it is, and an address to another host" in {
      val plain = """<link rel="preload" href="x.js" as="script"><img src="//cdn.example.com/a.png"><a href="https://x.io/">"""
      PackagedPage.local(plain) shouldBe plain
    }
  }

  "WebOSPackage" should {
    "write an ipk laid out as ares-package lays one out" in {
      val root = Files.createTempDirectory("webos").toFile
      val a = app(root)
      val staged = WebOSPackage.stage(bundle(root), a, new File(root, "staged"))
      val ipk = WebOSPackage.ipk(staged, a, new File(root, "out"), time = 1700000000L)
      ipk.getName shouldBe "com.example.tv_1.2.3_all.ipk"
      val members = ar(Files.readAllBytes(ipk.toPath))
      members.map(_._1) shouldBe List("debian-binary", "control.tar.gz", "data.tar.gz")
      members.foreach { m => (m._2, m._3, m._4) shouldBe ("0", "0", "100644") }
      new String(members.head._5, US_ASCII) shouldBe "2.0\n"

      val control = tar(members(1)._5)
      control.map(_._1) shouldBe List("control")
      val fields = new String(control.head._6, UTF_8).linesIterator.map(_.split(": ", 2)).map(p => p(0) -> p(1)).toMap
      fields("Package") shouldBe "com.example.tv"
      fields("Version") shouldBe "1.2.3"
      fields("Architecture") shouldBe "all"
      fields("webOS-Package-Format-Version") shouldBe "2"

      val data = tar(members(2)._5)
      val app0 = "usr/palm/applications/com.example.tv/"
      val files = data.filter(_._2 == '0').map(e => e._1 -> e._6).toMap
      files.keySet shouldBe Set(app0 + "index.html", app0 + "main.js", app0 + "fonts/OpenSans-Regular.woff2",
        app0 + "images/logo.png", app0 + "a/" + "d" * 90 + "/deep.txt", app0 + "appinfo.json", app0 + "icon.png",
        app0 + "largeIcon.png", app0 + "splash.png", "usr/palm/packages/com.example.tv/packageinfo.json")
      data.filter(_._2 == '5').map(_._1) should contain allOf ("usr/", "usr/palm/", "usr/palm/applications/",
        "usr/palm/packages/", app0, "usr/palm/packages/com.example.tv/", app0 + "fonts/", app0 + "a/")
      // every directory listed before what is in it
      data.zipWithIndex.foreach { case (e, i) =>
        val parent = e._1.stripSuffix("/").reverse.dropWhile(_ != '/').reverse
        if (parent.nonEmpty) data.indexWhere(_._1 == parent) should be < i
      }
      data.foreach { e =>
        (e._4, e._5) shouldBe (0, 0)
        e._3 shouldBe (if (e._2 == '5') Integer.parseInt("755", 8) else Integer.parseInt("644", 8))
      }
      files(app0 + "main.js").length shouldBe 1001
      new String(files(app0 + "index.html"), UTF_8) shouldBe PackagedPage.local(page)
      new String(files(app0 + "appinfo.json"), UTF_8) shouldBe a.appinfo
      new String(files("usr/palm/packages/com.example.tv/packageinfo.json"), UTF_8) shouldBe
        "{\n  \"id\": \"com.example.tv\",\n  \"version\": \"1.2.3\",\n  \"app\": \"com.example.tv\"\n}\n"
      size(files(app0 + "icon.png")) shouldBe (80, 80)
      size(files(app0 + "largeIcon.png")) shouldBe (130, 130)
      size(files(app0 + "splash.png")) shouldBe (1920, 1080)
      fields("Installed-Size").toLong shouldBe files.values.map(_.length.toLong).sum
    }
    "refuse a bundle without its page, or with an appinfo.json of its own" in {
      val root = Files.createTempDirectory("webos").toFile
      val b = bundle(root)
      write(b, "appinfo.json", "{}".getBytes(UTF_8))
      an[RuntimeException] should be thrownBy WebOSPackage.stage(b, app(root), new File(root, "staged"))
      an[RuntimeException] should be thrownBy WebOSPackage.stage(new File(root, "art"), app(root), new File(root, "s2"))
    }
  }
}
