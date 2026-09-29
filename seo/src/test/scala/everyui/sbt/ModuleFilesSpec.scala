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
}
