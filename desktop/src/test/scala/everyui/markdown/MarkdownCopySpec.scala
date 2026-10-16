package everyui.markdown

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Markdown made the two flavours a clipboard takes: clean HTML with no look of its own (`MarkdownHtml`) and plain prose
  * (`MarkdownPlain`), for an agent's answer pasted into an email, with what each kind of block and mark becomes and
  * everything escaped. */
class MarkdownCopySpec extends AnyWordSpec with Matchers {
  private val email: String =
    """Hi Dana,
      |
      |**Order:** #4821
      |**Status:** shipped
      |
      |Here is what changed:
      |
      |- Your refund of *$12.50* went out today
      |- The tracking number is `1Z999AA1`
      |  - it updates overnight
      |
      |1. Open [your account](https://shop.example/account)
      |2. Choose **Orders**
      |
      |---
      |
      || Item | Qty |
      ||:-----|----:|
      || Mug  | 2   |
      || Tea <green> & "loose" | 1 |
      |
      |```scala
      |val total = items.map(_.price).sum
      |if (total < 10 && ok) "small"
      |```
      |
      |> Thanks for your patience.
      |
      |Questions? Mail <help@shop.example> or see https://shop.example/help.""".stripMargin

  "MarkdownHtml" should {
    "write an email's blocks as clean semantic HTML" in {
      MarkdownHtml(email) shouldBe
        """<p>Hi Dana,</p>
          |<p><strong>Order:</strong> #4821
          |<strong>Status:</strong> shipped</p>
          |<p>Here is what changed:</p>
          |<ul>
          |<li>Your refund of <em>$12.50</em> went out today</li>
          |<li>The tracking number is <code>1Z999AA1</code>
          |<ul>
          |<li>it updates overnight</li>
          |</ul></li>
          |</ul>
          |<ol>
          |<li>Open <a href="https://shop.example/account">your account</a></li>
          |<li>Choose <strong>Orders</strong></li>
          |</ol>
          |<hr>
          |<table>
          |<thead>
          |<tr><th>Item</th><th>Qty</th></tr>
          |</thead>
          |<tbody>
          |<tr><td>Mug</td><td>2</td></tr>
          |<tr><td>Tea &lt;green&gt; &amp; &quot;loose&quot;</td><td>1</td></tr>
          |</tbody>
          |</table>
          |<pre><code>val total = items.map(_.price).sum
          |if (total &lt; 10 &amp;&amp; ok) &quot;small&quot;</code></pre>
          |<blockquote>
          |<p>Thanks for your patience.</p>
          |</blockquote>
          |<p>Questions? Mail <a href="mailto:help@shop.example">help@shop.example</a> or see <a href="https://shop.example/help">https://shop.example/help</a>.</p>""".stripMargin
    }

    "carry no look of its own" in {
      val html = MarkdownHtml(email)
      List("style=", "class=", "color", "font", "<span", "<div") foreach { word =>
        withClue(word + ": ")(html.contains(word) shouldBe false)
      }
    }

    "break lines where a chat's message does" in {
      MarkdownHtml("one\ntwo", breaks = true) shouldBe "<p>one<br>\ntwo</p>"
      MarkdownHtml("one\ntwo") shouldBe "<p>one\ntwo</p>"
      MarkdownHtml("one  \ntwo") shouldBe "<p>one<br>\ntwo</p>"
    }

    "write headings, strikethrough, a numbered list's start, loose items, tasks and pictures" in {
      MarkdownHtml("# Title\n\n### Part ~~old~~") shouldBe "<h1>Title</h1>\n<h3>Part <del>old</del></h3>"
      MarkdownHtml("3. three\n4. four") shouldBe "<ol start=\"3\">\n<li>three</li>\n<li>four</li>\n</ol>"
      MarkdownHtml("- one\n\n- two") shouldBe "<ul>\n<li><p>one</p></li>\n<li><p>two</p></li>\n</ul>"
      MarkdownHtml("- [x] done\n- [ ] not yet") shouldBe "<ul>\n<li>☑ done</li>\n<li>☐ not yet</li>\n</ul>"
      MarkdownHtml("![a \"cat\"](cat.png)") shouldBe "<p><img src=\"cat.png\" alt=\"a &quot;cat&quot;\"></p>"
    }

    "escape what would be markup, raw HTML too" in {
      MarkdownHtml("a < b & c > \"d\"") shouldBe "<p>a &lt; b &amp; c &gt; &quot;d&quot;</p>"
      MarkdownHtml("<div onclick=\"x()\">hi</div>") shouldBe
        "<pre><code>&lt;div onclick=&quot;x()&quot;&gt;hi&lt;/div&gt;</code></pre>"
      MarkdownHtml("[x](https://a.io/?q=1&r=\"2\")") shouldBe
        "<p><a href=\"https://a.io/?q=1&amp;r=&quot;2&quot;\">x</a></p>"
    }

    "write nothing for nothing" in {
      MarkdownHtml("") shouldBe ""
      MarkdownHtml("[a]: https://a.io") shouldBe ""
    }
  }

  "MarkdownPlain" should {
    "write an email's blocks as readable prose" in {
      MarkdownPlain(email) shouldBe
        """Hi Dana,
          |
          |Order: #4821 Status: shipped
          |
          |Here is what changed:
          |
          |- Your refund of $12.50 went out today
          |- The tracking number is 1Z999AA1
          |  - it updates overnight
          |
          |1. Open your account (https://shop.example/account)
          |2. Choose Orders
          |
          |Item	Qty
          |Mug	2
          |Tea <green> & "loose"	1
          |
          |val total = items.map(_.price).sum
          |if (total < 10 && ok) "small"
          |
          |Thanks for your patience.
          |
          |Questions? Mail help@shop.example or see https://shop.example/help.""".stripMargin
    }

    "keep a chat message's lines" in {
      MarkdownPlain("**Order:** #4821\n**Status:** shipped", breaks = true) shouldBe "Order: #4821\nStatus: shipped"
    }

    "indent what an item holds under its words, and number from the list's start" in {
      MarkdownPlain("9. nine\n10. ten\n    - deep\n      more") shouldBe "9. nine\n10. ten\n    - deep more"
      MarkdownPlain("- one\n\n  second paragraph\n- two") shouldBe "- one\n\n  second paragraph\n\n- two"
      MarkdownPlain("- [x] done\n- [ ] not yet") shouldBe "- ☑ done\n- ☐ not yet"
    }

    "write headings, quotes and rules as their words and the blank line they stand for" in {
      MarkdownPlain("# Title\n\nOne\n\n***\n\n> quoted\n> on\n\nTwo") shouldBe "Title\n\nOne\n\nquoted on\n\nTwo"
    }

    "write a link as its words and where it goes, or where it goes alone" in {
      MarkdownPlain("[docs](https://x.io) and <https://y.io> and [https://z.io](https://z.io)") shouldBe
        "docs (https://x.io) and https://y.io and https://z.io"
      MarkdownPlain("![a cat](cat.png)") shouldBe "a cat"
    }
  }
}
