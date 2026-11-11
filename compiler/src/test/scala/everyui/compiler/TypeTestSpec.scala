package everyui.compiler

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.tasty.inspector.TastyInspector

/** `x.isInstanceOf[T]`, `x.asInstanceOf[T]` and `case x: T` as the Roku and Swift emitters write them, for a program
  * compiled here: classes and a trait of a "library" package, an app's class, trait and case class extending them, and
  * anonymous subclasses. `isInstanceOf` was read as a field of x nothing had, so on both it was always false; each is a
  * test against the `_isa` every object carries now (RtIsA), and those tags name every ancestor. (The tests' answers,
  * run, are the reactify suite's `emit.isa` lines, against the JVM's.) */
class TypeTestSpec extends AnyWordSpec with Matchers {
  private val source =
    """package lib {
      |  class Widget(val name: String)
      |  trait Holder
      |  class Pane(name: String) extends Widget(name) with Holder
      |}
      |package app {
      |  import lib.*
      |  trait Badge
      |  class Nav extends Pane("nav")
      |  case class Episode(n: Int) extends Holder
      |  object Probe {
      |    def run(x: Any): String = {
      |      val anon: Any = new Pane("anon") with Badge {}
      |      val plain: Widget = new Widget("w") {}
      |      val tests = List(x.isInstanceOf[Nav], x.isInstanceOf[Holder], anon.isInstanceOf[Badge],
      |        x.isInstanceOf[String], x.isInstanceOf[Int], x.isInstanceOf[Episode | Badge])
      |      val pane = x.asInstanceOf[Pane]
      |      val widget = pane.asInstanceOf[Widget]
      |      val kind = x match {
      |        case _: Nav => "nav"
      |        case _: String => "string"
      |        case _ => "other"
      |      }
      |      tests.mkString + pane.name + widget.name + plain.name + kind
      |    }
      |  }
      |}
      |""".stripMargin

  /** The program compiled with -Yretain-trees, and emitted by `emitter` into `out`. */
  private def emit(target: String): String = {
    val dir = Files.createTempDirectory("everyui-typetest")
    val src = dir.resolve("Probe.scala")
    Files.write(src, source.getBytes(StandardCharsets.UTF_8))
    val classes = Files.createDirectories(dir.resolve("classes"))
    // Scala's own libraries where they are, and whatever else the tests run with
    val cp = (List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c =>
      Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString) :+ System.getProperty("java.class.path"))
      .mkString(java.io.File.pathSeparator)
    val reporter = dotty.tools.dotc.Main.process(Array("-d", classes.toString, "-Yretain-trees", "-classpath", cp,
      src.toString))
    reporter.hasErrors shouldBe false
    val tasty = Files.walk(classes).iterator().asScala.map(_.toString).filter(_.endsWith(".tasty")).toList.sorted
    val out = dir.resolve("out")
    val emitter: Emitter =
      if (target == "swift") new SwiftEmitter(out, None, "probe") else new RokuEmitter(out, None, "probe")
    TastyInspector.inspectAllTastyFiles(tasty, Nil, cp.split(java.io.File.pathSeparator).toList)(emitter) shouldBe true
    emitter.writeOutput()
    val written = Files.walk(out).iterator().asScala.filter(p => p.toString.endsWith(".brs") ||
      p.toString.endsWith(".swift")).toList.sorted
    // its code, spaces taken out (as the Roku's emit compacts its own) and comments left out
    written.map((p: Path) => new String(Files.readAllBytes(p), StandardCharsets.UTF_8)).mkString("\n").linesIterator
      .map(_.replace(" ", "")).filterNot(l => l.startsWith("'") || l.startsWith("//")).mkString("\n")
  }

  private lazy val roku = emit("roku")
  private lazy val swift = emit("swift")

  "The Roku and Swift emitters" should {
    "test isInstanceOf of a program's class or trait against its _isa, never as a field" in {
      roku should include("RtIsA(x,\"|Nav|\")")
      roku should include("RtIsA(x,\"|Holder|\")")
      roku should include("RtIsA(anon,\"|Badge|\")")
      roku should not include ".isInstanceOf"
      swift should include("RtIsA(x,V.vStr(\"|Nav|\"))")
      swift should include("RtIsA(anon,V.vStr(\"|Badge|\"))")
      swift should not include "isInstanceOf"
    }
    "tag each class with its ancestors, a library's among them, and an anonymous one with its parent and trait" in {
      for (code <- List(roku, swift)) {
        code should include("|Nav|Pane|Holder|Widget|")
        code should include("|Episode|")
        code.linesIterator.exists(l => l.contains("|Pane|") && l.contains("|Badge|") && l.contains("_isa")) shouldBe
          true
      }
    }
    "test Scala's own types by what the value is, and a union by either side" in {
      roku should include("RtTypeIs(x,\"s\")")
      roku should include("RtTypeIs(x,\"i\")")
      roku should include("(RtIsA(x,\"|Episode|\")orRtIsA(x,\"|Badge|\"))")
      swift should include("RtTypeIs(x,V.vStr(\"s\"))")
      swift should include("(RtIsA(x,V.vStr(\"|Episode|\"))||RtIsA(x,V.vStr(\"|Badge|\")))")
    }
    "check a downcast to a program's class, and take an upcast as it is" in {
      roku should include("RtCast(x,\"|Pane|\")")
      swift should include("RtCast(x,V.vStr(\"|Pane|\"))")
      roku should not include "RtCast(pane"
      swift should not include "RtCast(pane"
    }
    "match `case _: String` by the value, where it once matched everything" in {
      roku.linesIterator.exists(l => l.contains("RtTypeIs(") && l.contains("\"s\")") && l.contains("if")) shouldBe true
    }
  }
}
