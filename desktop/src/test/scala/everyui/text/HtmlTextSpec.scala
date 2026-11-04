package everyui.text

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** HTML from elsewhere (a calendar event's description) read as words and links, without a DOM. */
class HtmlTextSpec extends AnyWordSpec with Matchers {
  "HTML read as text" should {
    "end a line at each br, and keep a block to lines of its own" in {
      HtmlText.read("Join the call<br>Agenda:<div>One</div><div>Two</div>after").text shouldBe
        "Join the call\nAgenda:\nOne\nTwo\nafter"
    }
    "keep an empty line for a br after a br, and no more than one" in {
      HtmlText.read("a<br><br>b<br><br><br><br>c").text shouldBe "a\n\nb\n\nc"
    }
    "run white space together as a browser draws it, and keep it in pre" in {
      HtmlText.read("  one\n   two\t three  ").text shouldBe "one two three"
      HtmlText.read("<pre>a  b\n  c</pre>").text shouldBe "a  b\n  c"
    }
    "mark a list's items, and put a row's cells on one line" in {
      HtmlText.read("<ul><li>Bread</li><li>Milk</li></ul>" +
        "<table><tr><td>A</td><td>B</td></tr><tr><td>C</td></tr></table>")
        .text shouldBe "• Bread\n• Milk\nA B\nC"
    }
    "leave out script, style, the head and comments" in {
      HtmlText.read("<html><head><title>T</title><style>p{}</style></head><body><!-- x --><script>alert(1)</script>" +
        "<p>Seen</p></body></html>").text shouldBe "Seen"
    }
    "write what character references stand for" in {
      HtmlText.read("Fish &amp; chips &lt;3 &#8212; caf&eacute;&nbsp;&#x1F600;").text shouldBe
        "Fish & chips <3 — café 😀"
    }
    "find each link with its words, its href's references written out" in {
      val read = HtmlText.read("See <a href=\"https://meet.example.com/x?a=1&amp;b=2\">the <b>call</b></a> or " +
        "<A HREF='https://docs.example.com'>notes</A>, <a href=https://bare.example.com>bare</a> <a href=\"\">none</a>")
      read.text shouldBe "See the call or notes, bare none"
      read.links shouldBe List("the call" -> "https://meet.example.com/x?a=1&b=2",
        "notes" -> "https://docs.example.com", "bare" -> "https://bare.example.com")
    }
    "name a link with no words by its href" in {
      HtmlText.read("<a href=\"https://x.example.com\"><img src=\"i.png\"></a>").links shouldBe
        List("https://x.example.com" -> "https://x.example.com")
    }
    "take a > in a quoted attribute as part of it, and a lone < as words" in {
      HtmlText.read("<a title=\"a>b\" href=\"https://q.example.com\">q</a> 1 < 2").text shouldBe "q 1 < 2"
    }
  }
}
