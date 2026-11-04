package everyui.rich

import everyui.rich.BlockKind.*
import everyui.rich.RichInline.{Image, Text}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A rich document: written as Markdown and read back the same, edited a step at a time, and written as HTML. */
class RichDocSpec extends AnyWordSpec with Matchers {
  private def t(s: String, m: Mark*): Text = Text(s, m.toSet)
  private def b(kind: BlockKind, pieces: RichInline*): RichBlock = RichBlock(kind, pieces.toVector)
  private def doc(blocks: RichBlock*): RichDoc = RichDoc(blocks.toVector)
  private def p(block: Int, offset: Int): RichPos = RichPos(block, offset)
  private def r(a: RichPos, f: RichPos): RichRange = RichRange(a, f)
  private def back(d: RichDoc): RichDoc = RichDoc.fromMarkdown(d.markdown)

  private val everything = doc(
    b(Heading(1), t("Plan")),
    b(Paragraph, t("Some "), t("bold", Mark.Bold), t(", "), t("italic", Mark.Italic), t(", "),
      t("under", Mark.Underline), t(", "), t("gone", Mark.Strike), t(", "), t("code", Mark.Code), t(" and "),
      t("a link", Mark.Link("https://example.com/a b")), t(".")),
    b(Paragraph, t("both", Mark.Bold, Mark.Italic), t(" "),
      t("linked bold", Mark.Bold, Mark.Link("https://x.example.com")), t(" line\nbreak")),
    b(Heading(2), t("Lists")),
    b(Bullet(0), t("one")),
    b(Bullet(1), t("nested")),
    b(Bullet(2), t("deeper")),
    b(Bullet(0), t("two")),
    b(Numbered(0), t("first")),
    b(Numbered(0), t("second")),
    b(Numbered(1), t("inner")),
    b(Heading(3), t("More")),
    b(Quote, t("quoted")),
    b(Quote, t("still quoted")),
    b(Code("scala"), t("val x = 1\n// **not bold**")),
    b(Rule),
    b(Picture(RichImage("https://img.example.com/a.png", "a picture"))),
    b(Picture(RichImage("blob:x/1", "sized", Some(320.0), Some(200.0)))),
    b(Paragraph, t("inline "), Image(RichImage("https://img.example.com/i.png", "icon")), t(" picture")))

  "A rich document as Markdown" should {
    "read back the same, every block and mark" in {
      back(everything) shouldBe everything
    }
    "write what people write" in {
      everything.markdown.split("\n").take(3).toList shouldBe List("# Plan", "",
        "Some **bold**, *italic*, <u>under</u>, ~~gone~~, `code` and [a link](<https://example.com/a b>).")
      doc(b(Bullet(0), t("a")), b(Bullet(1), t("b")), b(Numbered(0), t("c")), b(Numbered(0), t("d"))).markdown shouldBe
        "- a\n  - b\n1. c\n2. d"
    }
    "escape words that would read as Markdown" in {
      val tricky = doc(b(Paragraph, t("# not a heading, *not em*, [no link](x), 1. no list, <b>, a_b, ~~, \\")),
        b(Paragraph, t("- no bullet")), b(Paragraph, t("> no quote")), b(Paragraph, t("2) no list")))
      back(tricky) shouldBe tricky
    }
    "keep the spaces at a styled stretch's edges outside its delimiters, the words the same" in {
      val spaced = doc(b(Paragraph, t("say "), t(" bold ", Mark.Bold), t("now")))
      spaced.markdown shouldBe "say  **bold** now"
      back(spaced).plain shouldBe spaced.plain
    }
    "map a place in the document to its place in the Markdown" in {
      val d = doc(b(Paragraph, t("ab"), t("cd", Mark.Bold)), b(Bullet(0), t("x")))
      d.markdown shouldBe "ab**cd**\n\n- x"
      d.markdownOffset(p(0, 1)) shouldBe 1
      d.markdownOffset(p(0, 2)) shouldBe 4
      d.markdownOffset(p(0, 4)) shouldBe 8
      d.markdownOffset(p(1, 0)) shouldBe 12
    }
    "read what it has no place for as words" in {
      val read = RichDoc.fromMarkdown("#### Deep\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n- item\n\n  more\n")
      read.blocks.map(_.kind) shouldBe Vector(Heading(3), Paragraph, Paragraph, Bullet(0), Bullet(0))
      read.blocks(1).text shouldBe "a | b"
    }
  }

  "Editing a rich document" should {
    val d = doc(b(Paragraph, t("hello "), t("world", Mark.Bold)), b(Bullet(0), t("item")))
    "type in the marks the caret is in, and not a link past its end" in {
      val (one, at) = d.typed(RichRange.at(p(0, 8)), "!!", d.marksIn(RichRange.at(p(0, 8))))
      one.blocks(0).inlines shouldBe Vector(t("hello "), t("wo!!rld", Mark.Bold))
      at shouldBe p(0, 10)
      val linked = doc(b(Paragraph, t("go", Mark.Link("https://x.example.com"))))
      linked.marksIn(RichRange.at(p(0, 2))) shouldBe Set.empty
      linked.marksIn(RichRange.at(p(0, 1))) shouldBe Set(Mark.Link("https://x.example.com"))
    }
    "delete across blocks, the first keeping its kind" in {
      val (cut, at) = d.delete(r(p(0, 3), p(1, 2)))
      cut shouldBe doc(b(Paragraph, t("helem")))
      at shouldBe p(0, 3)
    }
    "split a block with Enter, a list item into another, an empty one out of the list" in {
      val (two, at) = d.split(RichRange.at(p(1, 2)))
      two.blocks.drop(1) shouldBe Vector(b(Bullet(0), t("it")), b(Bullet(0), t("em")))
      at shouldBe p(2, 0)
      val (ended, _) = doc(b(Bullet(0))).split(RichRange.at(p(0, 0)))
      ended shouldBe doc(b(Paragraph))
      val (afterHeading, _) = doc(b(Heading(1), t("Title"))).split(RichRange.at(p(0, 5)))
      afterHeading.blocks(1).kind shouldBe Paragraph
    }
    "backspace: a character, a list item outdented, then a paragraph, then joined above" in {
      val nested = doc(b(Paragraph, t("top")), b(Bullet(1), t("x")))
      val (one, _) = nested.backspace(p(1, 0))
      one.blocks(1).kind shouldBe Bullet(0)
      val (two, _) = one.backspace(p(1, 0))
      two.blocks(1).kind shouldBe Paragraph
      val (three, at) = two.backspace(p(1, 0))
      three shouldBe doc(b(Paragraph, t("topx")))
      at shouldBe p(0, 3)
      nested.backspace(p(1, 1))._1.blocks(1).length shouldBe 0
    }
    "toggle a mark on, off where all of it has it, and code apart from the rest" in {
      val on = d.toggleMark(r(p(0, 0), p(0, 5)), Mark.Italic)
      on.blocks(0).inlines.head shouldBe t("hello", Mark.Italic)
      on.toggleMark(r(p(0, 0), p(0, 5)), Mark.Italic) shouldBe d
      val bold = d.toggleMark(r(p(0, 6), p(0, 11)), Mark.Bold)
      bold.blocks(0).inlines shouldBe Vector(t("hello world"))
      d.toggleMark(r(p(0, 6), p(0, 11)), Mark.Code).blocks(0).inlines.last shouldBe t("world", Mark.Code)
    }
    "link and unlink, set a block's kind, and nest a list" in {
      val linked = d.setLink(r(p(0, 0), p(0, 5)), Some("https://x.example.com"))
      Mark.link(linked.marksIn(r(p(0, 0), p(0, 5)))) shouldBe Some("https://x.example.com")
      linked.setLink(r(p(0, 0), p(0, 5)), None) shouldBe d
      d.setKind(r(p(0, 0), p(1, 0)), Numbered()).blocks.map(_.kind) shouldBe Vector(Numbered(0), Numbered(0))
      d.indent(RichRange.at(p(1, 0)), 1).blocks(1).kind shouldBe Bullet(1)
      d.indent(RichRange.at(p(1, 0)), -1).blocks(1).kind shouldBe Bullet(0)
      d.setKind(RichRange.at(p(0, 0)), Code()).blocks(0).inlines shouldBe Vector(t("hello world"))
    }
    "put in a picture among the words or as a block of its own" in {
      val image = RichImage("blob:x/2", "drop")
      val (inline, at) = d.insertImage(RichRange.at(p(0, 6)), image)
      inline.blocks(0).inlines(1) shouldBe Image(image)
      at shouldBe p(0, 7)
      val (block, below) = d.insertImage(RichRange.at(p(0, 6)), image, block = true)
      block.blocks.map(_.kind) shouldBe Vector(Paragraph, Picture(image), Paragraph, Bullet(0))
      below shouldBe p(2, 0)
    }
    "put in a document of several blocks" in {
      val pasted = doc(b(Paragraph, t("A")), b(Heading(2), t("B")), b(Paragraph, t("C")))
      val (in, at) = d.insert(RichRange.at(p(0, 2)), pasted)
      in.blocks.map(_.text) shouldBe Vector("heA", "B", "Cllo world", "item")
      at shouldBe p(2, 1)
    }
  }

  "A rich document as HTML" should {
    "write its blocks and marks, escaped, nesting lists inside their items" in {
      doc(b(Paragraph, t("a<b", Mark.Bold), t(" "), t("x", Mark.Link("javascript:alert(1)"))),
        b(Bullet(0), t("one")), b(Bullet(1), t("two")), b(Bullet(0), t("three"))).html shouldBe
        "<p><strong>a&lt;b</strong> x</p><ul><li>one<ul><li>two</li></ul></li><li>three</li></ul>"
      doc(b(Quote, t("q")), b(Quote, t("r")), b(Code("js"), t("<x>")), b(Rule)).html shouldBe
        "<blockquote><p>q</p><p>r</p></blockquote><pre><code class=\"language-js\">&lt;x&gt;</code></pre><hr>"
    }
  }
}
