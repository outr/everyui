package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.zip.{ZipEntry, ZipOutputStream}

/** The files a module carries for the web, taken off a classpath of jars and classes directories (ModuleFiles). */
class ModuleFilesSpec extends AnyWordSpec with Matchers {
  private def jar(file: File, entries: (String, String)*): File = {
    val out = new ZipOutputStream(Files.newOutputStream(file.toPath))
    try entries.foreach { case (name, text) =>
      out.putNextEntry(new ZipEntry(name))
      out.write(text.getBytes(UTF_8))
      out.closeEntry()
    }
    finally out.close()
    file
  }

  private def read(file: File): String = new String(Files.readAllBytes(file.toPath), UTF_8)

  "A classpath" should {
    val root = Files.createTempDirectory("module-files").toFile
    val web = jar(new File(root, "everyui-web_sjs1_3-0.1.0.jar"), "everyui/fonts/Mono.ttf" -> "font",
      "everyui/render/RenderNode.sjsir" -> "ir")
    val terminal = jar(new File(root, "everyui-terminal_sjs1_3-0.1.0.jar"), "everyui-terminal/6.0.0/xterm.js" -> "xterm",
      "everyui-terminal/6.0.0/bridge/terminal_bridge.js" -> "bridge", "everyui/terminal/Terminal.sjsir" -> "ir")
    val other = jar(new File(root, "fabric-core_sjs1_3-1.0.jar"), "everyui-fake/x.js" -> "not ours")
    val classes = new File(root, "classes")
    new File(classes, "everyui-monaco/0.57.0/vs").mkdirs()
    Files.write(new File(classes, "everyui-monaco/0.57.0/vs/loader.js").toPath, "loader".getBytes(UTF_8))
    Files.write(new File(classes, "Main.sjsir").toPath, "ir".getBytes(UTF_8))
    val classpath = Seq(web, terminal, other, classes, new File(root, "missing"))

    "give every web guest's files, from jars and classes directories alike" in {
      val out = new File(root, "guests")
      val copied = ModuleFiles.guests(classpath, out)
      copied.map(f => out.toPath.relativize(f.toPath).toString.replace(File.separatorChar, '/')).sorted shouldBe
        Seq("everyui-monaco/0.57.0/vs/loader.js", "everyui-terminal/6.0.0/bridge/terminal_bridge.js",
          "everyui-terminal/6.0.0/xterm.js")
      read(new File(out, "everyui-terminal/6.0.0/xterm.js")) shouldBe "xterm"
      new File(out, "everyui").exists() shouldBe false
    }

    "give what is under one prefix" in {
      val out = new File(root, "fonts")
      ModuleFiles.copy(classpath, "everyui/fonts/", out).size shouldBe 1
      read(new File(out, "everyui/fonts/Mono.ttf")) shouldBe "font"
    }
  }

  "The app's fonts" should {
    val root = Files.createTempDirectory("app-fonts").toFile
    def font(name: String, text: String): File = {
      val f = new File(root, "app/" + text + "/" + name)
      f.getParentFile.mkdirs()
      Files.write(f.toPath, text.getBytes(UTF_8))
      f
    }
    def everyui(): File = {
      val fonts = new File(root, "fonts-" + System.nanoTime)
      fonts.mkdirs()
      Files.write(new File(fonts, "OpenSans-Regular.ttf").toPath, "everyui".getBytes(UTF_8))
      fonts
    }

    "go in beside EveryUI's" in {
      val fonts = everyui()
      ModuleFiles.appFonts(Seq(font("Brand.ttf", "brand"), font("Brand-Bold.otf", "bold")), fonts).map(_.getName) shouldBe
        Seq("Brand.ttf", "Brand-Bold.otf")
      read(new File(fonts, "Brand.ttf")) shouldBe "brand"
      read(new File(fonts, "OpenSans-Regular.ttf")) shouldBe "everyui"
      ModuleFiles.appFonts(Nil, fonts) shouldBe Nil
    }
    "refuse a name EveryUI's has, a name given twice, and a file that is not there, leaving EveryUI's as they were" in {
      val fonts = everyui()
      val e = the[IllegalArgumentException] thrownBy ModuleFiles.appFonts(
        Seq(font("OpenSans-Regular.ttf", "mine"), font("Brand.ttf", "a"), font("Brand.ttf", "b"), new File(root, "none.ttf")), fonts)
      e.getMessage should include("OpenSans-Regular.ttf is one of EveryUI's fonts")
      e.getMessage should include("Brand.ttf is the name of more than one of them")
      e.getMessage should include("none.ttf is not a file")
      read(new File(fonts, "OpenSans-Regular.ttf")) shouldBe "everyui"
      new File(fonts, "Brand.ttf").exists() shouldBe false
    }
  }
}
