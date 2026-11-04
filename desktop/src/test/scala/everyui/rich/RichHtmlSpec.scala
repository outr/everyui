package everyui.rich

import everyui.rich.BlockKind.*
import everyui.rich.RichInline.{Image, Text}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** HTML read as a rich document, as a paste brings it from a page, a mail or a document editor. */
class RichHtmlSpec extends AnyWordSpec with Matchers {
  private def t(s: String, m: Mark*): Text = Text(s, m.toSet)

  "HTML read as a rich document" should {
    "keep its paragraphs, headings, lists nested as deep as they go, quotes, code and rules" in {
      val d = RichDoc.fromHtml("<h2>Plan</h2><p>one <b>two</b></p><ul><li>a<ul><li>b</li></ul></li></ul>" +
        "<ol><li>c</li></ol><blockquote><p>q</p></blockquote><pre><code>x = 1</code></pre><hr>")
      d.blocks.map(_.kind) shouldBe
        Vector(Heading(2), Paragraph, Bullet(0), Bullet(1), Numbered(0), Quote, Code(), Rule)
      d.blocks(1).inlines shouldBe Vector(t("one "), t("two", Mark.Bold))
      d.blocks(6).text shouldBe "x = 1"
    }
    "put a space before a styled stretch outside it, and keep no empty list items a list's tags alone make" in {
      val d = RichDoc.fromHtml("<p>pasted <b>bold</b> and <a href=\"https://x.example.com\">a link</a></p>" +
        "<ul><li>p1</li></ul>")
      d.blocks(0).inlines shouldBe Vector(t("pasted "), t("bold", Mark.Bold), t(" and "),
        t("a link", Mark.Link("https://x.example.com")))
      d.blocks.map(_.kind) shouldBe Vector(Paragraph, Bullet(0))
    }
    "read a document editor's inline styles, and its normal-weight bold round everything" in {
      val d = RichDoc.fromHtml("<b style=\"font-weight:normal\"><span style=\"font-weight:700\">B</span> <span " +
        "style=\"font-style:italic\">I</span> <span style=\"text-decoration:underline\">U</span> plain</b>")
      d.blocks(0).inlines shouldBe Vector(t("B", Mark.Bold), t(" "), t("I", Mark.Italic), t(" "),
        t("U", Mark.Underline),
        t(" plain"))
    }
    "take a picture among the words or as a block, and a line break as a line break" in {
      val d = RichDoc.fromHtml("<p>a<br>b <img src=\"blob:x/1\" alt=\"i\" width=\"20\"></p>" +
        "<p><img src=\"https://p.example.com/a.png\"></p>")
      d.blocks(0).inlines shouldBe Vector(t("a\nb "), Image(RichImage("blob:x/1", "i", Some(20.0), None)))
      d.blocks(1).inlines shouldBe Vector(Image(RichImage("https://p.example.com/a.png")))
    }
    "leave out script, style and comments" in {
      RichDoc.fromHtml("<style>p{}</style><script>x()</script><!-- c --><p>seen</p>").plain shouldBe "seen"
    }
  }
}
