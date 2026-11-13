package everyui.compiler

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.tasty.inspector.TastyInspector

/** A compound assignment to a var of a top-level object, a trait's companion or a nested object, from a class outside
  * them (`AccountOffer.offered += "x"`): Scala binds the object to a temporary first (`val _1 = AccountOffer`), and the
  * Roku emitter wrote that as `_1_=AccountOffer`, a name nothing defines (brighterscript: "Cannot find name"). The
  * object is read and its var written as a plain `AccountOffer.offered = …` is. (The values, run, are the reactify
  * suite's `emit.modassign` lines, against the JVM's.) */
class ModuleAssignSpec extends AnyWordSpec with Matchers {
  private val source =
    """package app {
      |  object AccountOffer {
      |    var offered: Set[String] = Set.empty
      |    var count: Int = 0
      |    var names: List[String] = Nil
      |    object Inner {
      |      var n: Int = 0
      |    }
      |  }
      |  object Registry {
      |    var seen: List[String] = Nil
      |    seen = seen :+ "boot"
      |  }
      |  trait Shown
      |  object Shown {
      |    var seen: Set[String] = Set.empty
      |  }
      |  class Offerer {
      |    def run(): String = {
      |      AccountOffer.offered += "x"
      |      AccountOffer.offered += "y"
      |      AccountOffer.offered -= "x"
      |      AccountOffer.names ++= List("a", "b")
      |      AccountOffer.count += 2
      |      AccountOffer.count -= 1
      |      AccountOffer.Inner.n += 3
      |      Shown.seen += "z"
      |      Registry.seen :+= "r"
      |      Registry.seen ++= List("s")
      |      AccountOffer.offered.mkString + AccountOffer.names.mkString + AccountOffer.count +
      |        AccountOffer.Inner.n + Shown.seen.mkString + Registry.seen.mkString
      |    }
      |  }
      |}
      |""".stripMargin

  /** The program compiled with -Yretain-trees, and emitted for `target`. */
  private def emit(target: String): String = {
    val dir = Files.createTempDirectory("everyui-modassign")
    val src = dir.resolve("Probe.scala")
    Files.write(src, source.getBytes(StandardCharsets.UTF_8))
    val classes = Files.createDirectories(dir.resolve("classes"))
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
    "never bind an object to a temporary of scalac's" in {
      for (code <- List(roku, swift)) {
        code should not include "_1_="
        code should not include "=AccountOffer\n"
        code should not include "=Shown\n"
        code should not include "=AccountOffer_Inner()\n"
      }
    }
    "read and write a plain object's var through its accessors, a nested object's and a companion's too" in {
      roku should include("__mvset_AccountOffer_offered=_setAdd(AccountOffer_offered(),\"x\")")
      roku should include("__mvset_AccountOffer_offered=_setRemove(AccountOffer_offered(),\"x\")")
      roku should include("__mvset_AccountOffer_names=_concat(AccountOffer_names(),[\"a\",\"b\"])")
      roku should include("AccountOffer_count__set(__mvset_AccountOffer_count)")
      roku should include("__mvset_Inner_n=(Inner_n()+3)")
      roku should include("ShownObj_seen__set(__mvset_ShownObj_seen)")
      swift should include("_=tryAccountOffer_offered__set(__mvset_AccountOffer_offered)")
      swift should include("_=tryShownObj_seen__set(__mvset_ShownObj_seen)")
    }
    "write an object-model object's var through the object" in {
      roku should include("_setField(app_Registry__module(),\"seen\",_mv1)")
      swift should include("_=try_setField(app_Registry__module(),V.vStr(\"seen\"),_mv1)")
    }
  }
}
