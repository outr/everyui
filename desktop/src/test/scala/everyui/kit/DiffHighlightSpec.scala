package everyui.kit

import everyui.text.TextRun
import everyui.theme.Theme
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A diff is coloured by its lines: added, taken out, a hunk's header and the files' names, the rest plain. */
class DiffHighlightSpec extends AnyWordSpec with Matchers {
  private val patch =
    "diff --git a/Main.scala b/Main.scala\n" +
      "index 3f2a1c0..9b4e7d2 100644\n" +
      "--- a/Main.scala\n" +
      "+++ b/Main.scala\n" +
      "@@ -1,4 +1,4 @@ object Main {\n" +
      " object Main {\n" +
      "   def main(args: Array[String]): Unit = {\n" +
      "-    println(\"hello\")\n" +
      "+    println(\"hello, world\")\n" +
      "   }\n" +
      " }"

  "A diff" should {
    "colour each line by what it is, and keep every character" in {
      val runs = Highlight.runs(patch, "diff")
      runs.map(_.text).mkString shouldBe patch
      runs shouldBe List(
        TextRun("diff --git a/Main.scala b/Main.scala\n", weight = 700),
        TextRun("index 3f2a1c0..9b4e7d2 100644\n", weight = 700),
        TextRun("--- a/Main.scala\n", weight = 700),
        TextRun("+++ b/Main.scala\n", weight = 700),
        TextRun("@@ -1,4 +1,4 @@ object Main {\n", color = Theme.muted().toHex),
        TextRun(" object Main {\n   def main(args: Array[String]): Unit = {\n"),
        TextRun("-    println(\"hello\")\n", color = Theme.danger().toHex),
        TextRun("+    println(\"hello, world\")\n", color = Theme.success().toHex),
        TextRun("   }\n }"))
    }
    "be read the same as a patch, and take the theme's own colours where it has them" in {
      Highlight.runs("+a\n-b", "PATCH") shouldBe Highlight.runs("+a\n-b", "diff")
      val old = Theme.palette()
      try {
        Theme.palette @= old.copy(extras = old.extras + ("code.added" -> everyui.Color.fromHex("#00ff00")))
        Highlight.runs("+a", "diff") shouldBe List(TextRun("+a", color = everyui.Color.fromHex("#00ff00").toHex))
      } finally Theme.palette @= old
    }
  }
}
