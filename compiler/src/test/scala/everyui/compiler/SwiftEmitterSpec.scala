package everyui.compiler

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

/** The Swift emitter's string literals (SwiftEmitter.literal) and its check of what the emitted Swift calls against
  * the Swift runtime (SwiftEmitter.gaps, SwiftRuntimeNames): a literal swiftc refuses, or a helper only the Roku
  * runtime has, is found on Linux at the emit, not on the Mac. */
class SwiftEmitterSpec extends AnyWordSpec with Matchers {
  "SwiftEmitter.literal" should {
    "escape the characters swiftc refuses or a reader cannot see" in {
      SwiftEmitter.literal("\u007f") shouldBe "\"\\u{7f}\""
      SwiftEmitter.literal("a\u0000b\u001bc") shouldBe "\"a\\u{0}b\\u{1b}c\""
      SwiftEmitter.literal("\u0085\u009f") shouldBe "\"\\u{85}\\u{9f}\""
      SwiftEmitter.literal("\u200b\u2028\u2029\ufeff") shouldBe "\"\\u{200b}\\u{2028}\\u{2029}\\u{feff}\""
    }
    "write quotes, backslashes, newlines and tabs as Swift's escapes" in {
      SwiftEmitter.literal("say \"hi\"\\\n\r\t") shouldBe "\"say \\\"hi\\\"\\\\\\n\\r\\t\""
    }
    "keep printable text, accents, scripts and emoji as they are" in {
      SwiftEmitter.literal("Español العربية 简体 \ud83d\ude00") shouldBe "\"Español العربية 简体 \ud83d\ude00\""
    }
    "write half of a surrogate pair alone as U+FFFD, which is what a Swift String would hold" in {
      SwiftEmitter.literal("\ud83d") shouldBe "\"\\u{fffd}\""
      SwiftEmitter.literal("x\ude00y") shouldBe "\"x\\u{fffd}y\""
    }
    "never leave a raw control or format character in its output" in {
      val all = SwiftEmitter.literal((0 until 0x10000).map(_.toChar).mkString)
      all.exists(c => Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) shouldBe false
    }
  }

  "SwiftEmitter.gaps" should {
    "pass Swift whose every call is to itself, to the runtime or to Swift" in {
      val code =
        """func Shape_area(_ this: V) throws -> V {
          |  x = try _div(this["w"], V.vDbl(2.0))
          |  if (try (x > V.vInt(0))).rtTrue { return try Shape_area(this) }
          |  _ = try this["draw"](this, rtObj(["self": this, "invalid": V.vNull]))
          |  return try rtCharAt(V.vStr("Mid(s, 1) and invalid"), _quot(x, V.vInt(2)))
          |}
          |""".stripMargin
      SwiftEmitter.gaps(code) shouldBe Nil
    }
    "name a function the runtime does not define, with how often it is called" in {
      val code = "func f(_ this: V) throws -> V {\n  _ = try _noSuchHelper(V.vInt(1)) + _noSuchHelper(V.vInt(2))\n}\n"
      SwiftEmitter.gaps(code) shouldBe
        List("unsupported: the Swift runtime has no `_noSuchHelper`, which the emitted Swift calls 2 time(s)")
    }
    "name BrightScript the derivation left in the Swift" in {
      val code =
        "func f(_ this: V) throws -> V {\n  x = try _arrNew(n, invalid)\n  y = try _until(V.vInt(0), xs.count())\n}\n"
      SwiftEmitter.gaps(code) shouldBe List(
        "unsupported: BrightScript left in the Swift: `.count()` (1 time(s))",
        "unsupported: BrightScript left in the Swift: `invalid` (1 time(s))")
    }
  }

  "SwiftRuntimeNames" should {
    "hold every helper the Roku runtime has that the Swift emitter's templates call" in {
      SwiftRuntimeNames.all should contain allOf ("_div", "_rem", "_quot", "_mapOfPairs", "_strIndexFrom",
        "_strTakeWhile", "_arrNew", "refArrayOps", "_lift", "_flatten", "_strFilter", "_minOption", "RtIsA",
        "rtCharAt", "Tuple5", "option2Iterable")
    }
    "define every runtime function a template of the derived Swift emitter writes a call to" in {
      val path = sys.props.get("everyui.swiftEmitter")
      assume(path.isDefined, "run by sbt, which names the derived SwiftEmitter.scala")
      val source = new String(Files.readAllBytes(Paths.get(path.get)), StandardCharsets.UTF_8)
      // the Scala source of the emitter, its comments left out: the string literals of its templates
      val code = source.linesIterator.filterNot(l => l.trim.startsWith("//") || l.trim.startsWith("*")).mkString("\n")
      val literals = """"(?:[^"\\\n]|\\.)*"""".r.findAllIn(code).toList
      // a helper's name standing alone in a template (`_div(`, `rtCharAt(`), not one made of an interpolation
      val helper = """(?<![A-Za-z0-9_.$}])((?:_|rt|Rt)[A-Za-z0-9_]*)\(""".r
      val called = literals.flatMap(l => helper.findAllMatchIn(l).map(_.group(1))).toSet
      // and not one the emitter writes itself (`func rtMain(`)
      val written = literals.flatMap(l => """func ([A-Za-z_][A-Za-z0-9_]*)\(""".r.findAllMatchIn(l).map(_.group(1))).toSet
      called should not be empty
      (called -- written -- SwiftRuntimeNames.all) shouldBe empty
    }
  }
}
