package everyui.compiler

import fabric.io.JsonParser
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import scala.tasty.inspector.TastyInspector

/** fabric's codecs as the Roku and Swift emitters write them, held to fabric on the JVM. The emitters cannot read the
  * code RW.gen's macro makes (it is not in the TASTy), so they decide each codec themselves; until they did, an enum,
  * a sealed trait, RW.poly and a library's case class were passed through as they were, and the JSON was wrong.
  *
  * A program of every shape (src/test/resources/fabric-codecs: enums, sealed traits of case classes and case objects,
  * RW.poly with its aliases, RW.enumeration, RW.string, RW.from, `@typeField`, Option, List, Set and Map of them, Maps
  * of other keys than Strings, defaults, a library's types compiled apart) is compiled here, run on the JVM, and
  * emitted; the Roku channel runs in brs-engine (`brs-cli`, named by EVERYUI_BRS_CLI or found on the PATH), and every
  * JSON it writes must be the JVM's, as JSON (FormatJson orders an object's keys, fabric does not), and every value
  * it reads back equal. The Swift is emitted with no gap; it runs on a Mac alone. */
class FabricCodecSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  private val dir = Files.createTempDirectory("everyui-fabric-codecs")

  override def afterAll(): Unit =
    Files.walk(dir).iterator().asScala.toList.reverse.foreach(Files.delete)

  // Scala's libraries and fabric's where they are (the forked test's own class path may be a jar naming them)
  private val classpath =
    (List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror], classOf[fabric.Json], classOf[fabric.io.JsonParser.type],
      classOf[org.typelevel.literally.Literally[?]]).map(c =>
      Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString) :+ System.getProperty("java.class.path"))
      .mkString(java.io.File.pathSeparator)

  /** The sources `files` of `fabric-codecs/<name>` (resources, which the build may keep in a jar), compiled with
    * -Yretain-trees into a directory of their own. */
  private def compile(name: String, files0: List[String], extra: List[Path]): Path = {
    val src = Files.createDirectories(dir.resolve("src").resolve(name))
    val files = files0.map { f =>
      val in = getClass.getResourceAsStream(s"/fabric-codecs/$name/$f")
      try Files.write(src.resolve(f), in.readAllBytes()).toString finally in.close()
    }
    val out = Files.createDirectories(dir.resolve(name))
    val cp = (extra.map(_.toString) :+ classpath).mkString(java.io.File.pathSeparator)
    dotty.tools.dotc.Main.process(Array("-d", out.toString, "-Yretain-trees", "-classpath", cp) ++ files)
      .hasErrors shouldBe false
    out
  }

  private lazy val library = compile("library", List("Library.scala"), Nil)
  private lazy val program = compile("program", List("Types.scala", "Golden.scala"), List(library))
  private lazy val gaps = compile("gaps", List("Gaps.scala"), Nil)

  /** `classes` emitted for `target` into its own directory, with the library on the classpath only, as a jar is: the
    * emitter, for its gaps. */
  private def emit(classes: Path, target: String): (Emitter, Path) = {
    val out = dir.resolve(s"${classes.getFileName}-$target")
    val emitter: Emitter =
      if (target == "swift") new SwiftEmitter(out, None, "codecs") else new RokuEmitter(out, None, "codecs")
    val tasty = Files.walk(classes).iterator().asScala.map(_.toString).filter(_.endsWith(".tasty")).toList.sorted
    val cp = (library.toString :: classpath.split(java.io.File.pathSeparator).toList)
    TastyInspector.inspectAllTastyFiles(tasty, Nil, cp)(emitter) shouldBe true
    emitter.writeOutput()
    (emitter, out)
  }

  /** The `RW` lines of the program's run on the JVM. */
  private lazy val jvm: List[String] = {
    val loader = new URLClassLoader(Array(library.toUri.toURL, program.toUri.toURL), getClass.getClassLoader)
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
      loader.loadClass("probe.Golden").getMethod("run").invoke(null)
    }
    rwLines(bytes.toString(StandardCharsets.UTF_8))
  }

  private def rwLines(output: String): List[String] =
    output.linesIterator.map(_.replace("\r", "")).filter(_.startsWith("RW ")).toList

  /** brs-engine's command line runner, where it is installed. */
  private def brsCli: Option[String] =
    sys.env.get("EVERYUI_BRS_CLI").filter(p => Files.isExecutable(Paths.get(p))).orElse {
      sys.env.getOrElse("PATH", "").split(java.io.File.pathSeparator).map(Paths.get(_, "brs-cli"))
        .find(Files.isExecutable(_)).map(_.toString)
    }

  /** The channel in `out`, zipped and run in brs-engine: its `RW` lines. */
  private def runRoku(cli: String, out: Path): List[String] = {
    val zip = dir.resolve("channel.zip")
    val zos = new ZipOutputStream(Files.newOutputStream(zip))
    try Files.walk(out).iterator().asScala.filter(Files.isRegularFile(_)).foreach { f =>
      zos.putNextEntry(new ZipEntry(out.relativize(f).toString))
      zos.write(Files.readAllBytes(f))
      zos.closeEntry()
    } finally zos.close()
    val log = dir.resolve("brs.log")
    val p = new ProcessBuilder(cli, zip.toString).redirectErrorStream(true).redirectOutput(log.toFile).start()
    // a channel runs until it is closed: it is stopped once it has printed its last line, or an error
    def text = new String(Files.readAllBytes(log), StandardCharsets.UTF_8)
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180)
    while (p.isAlive && System.nanoTime() < deadline && !text.linesIterator.exists(l => l.startsWith("RW done") || l.contains("[error]")))
      p.waitFor(200, TimeUnit.MILLISECONDS)
    p.destroyForcibly().waitFor()
    rwLines(text)
  }

  /** Two runs' lines the same: a JSON line's JSON equal as JSON, any other line equal. */
  private def sameLines(got: List[String], expected: List[String]): Unit = {
    got.size shouldBe expected.size
    got.zip(expected).foreach { (g, e) =>
      (g.split(" ", 4), e.split(" ", 4)) match {
        case (Array("RW", "json", gl, gj), Array("RW", "json", el, ej)) =>
          withClue(s"$el: ") {
            gl shouldBe el
            JsonParser(gj) shouldBe JsonParser(ej)
          }
        case _ => g shouldBe e
      }
    }
  }

  "The Roku and Swift emitters" should {
    "run the program on the JVM first, every value read back equal" in {
      jvm.last shouldBe "RW done"
      jvm.filter(_.startsWith("RW eq")).foreach(_ should endWith(" true"))
    }
    "emit every codec, Roku and Swift, with no gap" in {
      emit(program, "roku")._1.unsupported shouldBe Nil
      emit(program, "swift")._1.unsupported shouldBe Nil
    }
    "write fabric's JSON on a Roku, and read it back to equal values" in {
      val cli = brsCli.getOrElse(cancel("brs-engine's brs-cli is not installed (EVERYUI_BRS_CLI or the PATH)"))
      sameLines(runRoku(cli, emit(program, "roku")._2), jvm)
    }
    "name each codec they cannot follow as a gap, and write it as one that throws" in {
      val (emitter, out) = emit(gaps, "roku")
      val found = emitter.unsupported.mkString("\n")
      found should include("Box, a generic case class")
      found should include("Node, which holds itself")
      found should include("scala.Char, which has no codec")
      found should include("Reply's @unknownCase")
      val app = new String(Files.readAllBytes(out.resolve("components/App.brs")), StandardCharsets.UTF_8)
      app should include("_rwNone(")
      app should not include "_rw(\"valueRW\")"
      emit(gaps, "swift")._1.unsupported.mkString("\n") should include("Node, which holds itself")
    }
  }
}
