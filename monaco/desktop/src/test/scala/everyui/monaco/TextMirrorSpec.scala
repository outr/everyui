package everyui.monaco

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TextMirrorSpec extends AnyWordSpec with Matchers {
  "A TextMirror" should {
    "apply an insertion, a deletion and a replacement" in {
      val m = new TextMirror("hello world", 1)
      m(MonacoContentChange(2, List(MonacoTextChange(5, 0, ",")))) shouldBe "hello, world"
      m(MonacoContentChange(3, List(MonacoTextChange(0, 7, "")))) shouldBe "world"
      m(MonacoContentChange(4, List(MonacoTextChange(0, 5, "there")))) shouldBe "there"
      m.versionId shouldBe 4
    }
    "apply several edits that all refer to the text before them" in {
      val m = new TextMirror("a\nb\nc", 1)
      // a multi-cursor edit: an x before each line, given from the end back as Monaco gives them
      m(MonacoContentChange(2, List(MonacoTextChange(4, 0, "x"), MonacoTextChange(2, 0, "x"), MonacoTextChange(0, 0, "x")))) shouldBe "xa\nxb\nxc"
      // and in the other order: the same
      val n = new TextMirror("a\nb\nc", 1)
      n(MonacoContentChange(2, List(MonacoTextChange(0, 0, "x"), MonacoTextChange(2, 0, "x"), MonacoTextChange(4, 0, "x")))) shouldBe "xa\nxb\nxc"
    }
    "take the whole text when it is sent" in {
      val m = new TextMirror("old", 1)
      m(MonacoContentChange(5, List(MonacoTextChange(0, 3, "new")), flush = true, value = Some("new\r\n"))) shouldBe "new\r\n"
    }
    "keep astral characters, counted in UTF-16 as Monaco counts them" in {
      val m = new TextMirror("a😀b", 1)
      m(MonacoContentChange(2, List(MonacoTextChange(3, 1, "c")))) shouldBe "a😀c"
    }
  }
}
