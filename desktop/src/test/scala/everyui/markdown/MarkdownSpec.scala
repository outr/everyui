package everyui.markdown

import everyui.component.types.TextAlign
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The Markdown parser: CommonMark's blocks and inlines with their edge cases, GFM's tables, strikethrough, task lists
  * and bare links, and a streamed document parsed again only from its last block. */
class MarkdownSpec extends AnyWordSpec with Matchers {
  import MdBlock.*
  import MdInline.*

  private def t(s: String): MdInline = Text(s)
  private def para(content: MdInline*): MdBlock = Paragraph(content.toList)
  private def one(md: String): List[MdInline] = Markdown.parse(md) match {
    case List(Paragraph(content)) => content
    case other => fail("not one paragraph: " + other)
  }

  "Blocks" should {
    "split paragraphs at blank lines, joining a paragraph's lines with soft breaks" in {
      Markdown.parse("one\ntwo\n\n\nthree") shouldBe List(para(t("one"), SoftBreak, t("two")), para(t("three")))
      Markdown.parse("") shouldBe Nil
      Markdown.parse("   \n\n") shouldBe Nil
    }
    "take ATX headings, their closing hashes, and not seven or a missing space" in {
      Markdown.parse("# One\n## Two ##\n###### Six\n####### Seven\n#NoSpace") shouldBe List(
        Heading(1, List(t("One"))), Heading(2, List(t("Two"))), Heading(6, List(t("Six"))),
        para(t("####### Seven"), SoftBreak, t("#NoSpace")))
      Markdown.parse("# Title #not closing") shouldBe List(Heading(1, List(t("Title #not closing"))))
      Markdown.parse("#") shouldBe List(Heading(1, Nil))
    }
    "take setext headings, over a paragraph of any lines" in {
      Markdown.parse("Title\n=====") shouldBe List(Heading(1, List(t("Title"))))
      Markdown.parse("Two\nlines\n---") shouldBe List(Heading(2, List(t("Two"), SoftBreak, t("lines"))))
      // with no paragraph above it, --- is a rule
      Markdown.parse("---") shouldBe List(Rule)
    }
    "take rules of three or more of *, - or _, with spaces between" in {
      Markdown.parse("***\n- - -\n_____\n**") shouldBe List(Rule, Rule, Rule, para(t("**")))
      Markdown.parse("para\n\n* * *") shouldBe List(para(t("para")), Rule)
    }
    "take fenced code with its language, and its indentation taken back to the fence's" in {
      Markdown.parse("```scala extra words\nval x = 1\n\n  y\n```") shouldBe
        List(CodeBlock("scala", "val x = 1\n\n  y"))
      Markdown.parse("  ~~~\n  a\n    b\n c\n  ~~~") shouldBe List(CodeBlock("", "a\n  b\nc"))
      // a longer closing fence closes it, a shorter one is code, and a backtick fence's words hold no backtick
      Markdown.parse("````\n```\n`````") shouldBe List(CodeBlock("", "```"))
      Markdown.parse("``` a`b") shouldBe List(para(t("``` a`b")))
    }
    "keep a fence that has not closed as code so far, not closed" in {
      Markdown.parse("text\n```python\nprint(1)\nprint(") shouldBe
        List(para(t("text")), CodeBlock("python", "print(1)\nprint(", closed = false))
      Markdown.parse("```") shouldBe List(CodeBlock("", "", closed = false))
    }
    "take indented code, which cannot break into a paragraph" in {
      Markdown.parse("    code\n      more\n\n    after blank\n\npara") shouldBe
        List(CodeBlock("", "code\n  more\n\nafter blank"), para(t("para")))
      Markdown.parse("para\n    not code") shouldBe List(para(t("para"), SoftBreak, t("not code")))
    }
    "turn tabs in indentation into spaces" in {
      Markdown.parse("\tcode") shouldBe List(CodeBlock("", "code"))
    }
    "take block quotes, nested, with lazy continuation lines" in {
      Markdown.parse("> # Head\n> quoted\nlazy\n\n> > deeper") shouldBe List(
        Quote(List(Heading(1, List(t("Head"))), para(t("quoted"), SoftBreak, t("lazy")))),
        Quote(List(Quote(List(para(t("deeper")))))))
      Markdown.parse(">no space") shouldBe List(Quote(List(para(t("no space")))))
      Markdown.parse("> a\n---") shouldBe List(Quote(List(para(t("a")))), Rule)
    }
    "take bullet and ordered lists, their start, nesting, and a change of marker starting a new list" in {
      Markdown.parse("- a\n- b\n  - c\n    - d\n- e") shouldBe List(BulletList(false, 1, true, List(
        MdItem(List(para(t("a")))),
        MdItem(List(para(t("b")), BulletList(false, 1, true, List(
          MdItem(List(para(t("c")), BulletList(false, 1, true, List(MdItem(List(para(t("d")))))))))))),
        MdItem(List(para(t("e")))))))
      Markdown.parse("3. three\n4) four") shouldBe List(
        BulletList(true, 3, true, List(MdItem(List(para(t("three")))))),
        BulletList(true, 4, true, List(MdItem(List(para(t("four")))))))
      Markdown.parse("- a\n+ b") shouldBe List(
        BulletList(false, 1, true, List(MdItem(List(para(t("a")))))),
        BulletList(false, 1, true, List(MdItem(List(para(t("b")))))))
    }
    "make a list loose where blank lines stand between its items or their blocks" in {
      Markdown.parse("- a\n\n- b") shouldBe List(BulletList(false, 1, false,
        List(MdItem(List(para(t("a")))), MdItem(List(para(t("b")))))))
      Markdown.parse("- a\n\n  more\n- b") shouldBe List(BulletList(false, 1, false,
        List(MdItem(List(para(t("a")), para(t("more")))), MdItem(List(para(t("b")))))))
      // a blank line after the last item leaves it tight
      Markdown.parse("- a\n- b\n\npara") shouldBe List(BulletList(false, 1, true,
        List(MdItem(List(para(t("a")))), MdItem(List(para(t("b")))))), para(t("para")))
    }
    "let only a list starting at 1 break into a paragraph, and an item run on lazily" in {
      Markdown.parse("The number\n14. is not a list") shouldBe
        List(para(t("The number"), SoftBreak, t("14. is not a list")))
      Markdown.parse("Steps\n1. one") shouldBe
        List(para(t("Steps")), BulletList(true, 1, true, List(MdItem(List(para(t("one")))))))
      Markdown.parse("- a\nlazy") shouldBe
        List(BulletList(false, 1, true, List(MdItem(List(para(t("a"), SoftBreak, t("lazy")))))))
    }
    "take code inside a list item, and an item that starts empty" in {
      Markdown.parse("1. item\n\n   ```\n   code\n   ```") shouldBe List(BulletList(true, 1, false,
        List(MdItem(List(para(t("item")), CodeBlock("", "code"))))))
      Markdown.parse("-\n  foo") shouldBe List(BulletList(false, 1, true, List(MdItem(List(para(t("foo")))))))
      Markdown.parse("-      indented") shouldBe
        List(BulletList(false, 1, true, List(MdItem(List(CodeBlock("", " indented"))))))
    }
    "take task list items, ticked or not" in {
      Markdown.parse("- [ ] todo\n- [x] done\n- [X] Done too\n- [y] not a task") shouldBe List(
        BulletList(false, 1, true, List(MdItem(List(para(t("todo"))), Some(false)),
          MdItem(List(para(t("done"))), Some(true)), MdItem(List(para(t("Done too"))), Some(true)),
          MdItem(List(para(t("[y] not a task")))))))
    }
    "take GFM tables with their alignment, rows padded or cut to the header's cells" in {
      Markdown.parse("| a | b | c |\n|:--|:-:|--:|\n| 1 | 2 |\n| 1 | 2 | 3 | 4 |\n| `x` \\| y | **z** | |") shouldBe
        List(Table(List(TextAlign.Start, TextAlign.Center, TextAlign.End), List(List(t("a")), List(t("b")), List(t("c"))),
          List(List(List(t("1")), List(t("2")), Nil), List(List(t("1")), List(t("2")), List(t("3"))),
            List(List(CodeSpan("x"), t(" | y")), List(Strong(List(t("z")))), Nil))))
      Markdown.parse("a | b\n--|--\nc | d\n\nafter") shouldBe List(
        Table(List(TextAlign.Start, TextAlign.Start), List(List(t("a")), List(t("b"))),
          List(List(List(t("c")), List(t("d"))))), para(t("after")))
      // a header and a delimiter row of different widths are no table
      Markdown.parse("| a | b |\n| - |") shouldBe List(para(t("| a | b |"), SoftBreak, t("| - |")))
    }
    "let a table break into a paragraph" in {
      Markdown.parse("Intro\n| a |\n| - |\n| b |") shouldBe List(para(t("Intro")),
        Table(List(TextAlign.Start), List(List(t("a"))), List(List(List(t("b"))))))
    }
    "keep raw HTML as its text" in {
      Markdown.parse("<div>\n*hi*\n</div>\n\npara") shouldBe List(Html("<div>\n*hi*\n</div>"), para(t("para")))
      Markdown.parse("<!-- a\n\nb -->\nafter") shouldBe List(Html("<!-- a\n\nb -->"), para(t("after")))
      one("a <b>bold</b> c") shouldBe List(t("a <b>bold</b> c"))
    }
    "take link reference definitions, and resolve links to them anywhere in the document" in {
      Markdown.parse("[foo]\n\n[Foo]: /url \"Title\"") shouldBe List(
        para(Link("/url", "Title", List(t("foo")))), Definitions(List(MdReference("foo", "/url", "Title"))))
      Markdown.parse("[text][ref] and [ref][] and [missing]\n\n[ref]: <http://x.y/a b>").head shouldBe para(
        Link("http://x.y/a b", "", List(t("text"))), t(" and "), Link("http://x.y/a b", "", List(t("ref"))),
        t(" and [missing]"))
    }
  }

  "Inlines" should {
    "take emphasis and strong emphasis, with * and _" in {
      one("*a* _b_ **c** __d__ ***e***") shouldBe List(Emphasis(List(t("a"))), t(" "), Emphasis(List(t("b"))), t(" "),
        Strong(List(t("c"))), t(" "), Strong(List(t("d"))), t(" "), Emphasis(List(Strong(List(t("e"))))))
      one("*a **b** c*") shouldBe List(Emphasis(List(t("a "), Strong(List(t("b"))), t(" c"))))
      one("**a*") shouldBe List(t("*"), Emphasis(List(t("a"))))
      one("*a**") shouldBe List(Emphasis(List(t("a"))), t("*"))
      one("*foo**bar**baz*") shouldBe List(Emphasis(List(t("foo"), Strong(List(t("bar"))), t("baz"))))
      one("*foo**bar*") shouldBe List(Emphasis(List(t("foo**bar"))))
    }
    "follow the flanking rules: no emphasis around spaces, nor _ inside words" in {
      one("a * b * c") shouldBe List(t("a * b * c"))
      one("snake_case_name") shouldBe List(t("snake_case_name"))
      one("2*3*4") shouldBe List(t("2"), Emphasis(List(t("3"))), t("4"))
      one("_foo_bar") shouldBe List(t("_foo_bar"))
      one("*(*foo*)*") shouldBe List(Emphasis(List(t("("), Emphasis(List(t("foo"))), t(")"))))
    }
    "take strikethrough with one or two tildes, not three" in {
      one("~~gone~~ ~also~ ~~~no~~~") shouldBe List(Strike(List(t("gone"))), t(" "), Strike(List(t("also"))),
        t(" ~~~no~~~"))
    }
    "take code spans: as many backticks to close, a space either side taken off, and no emphasis inside" in {
      one("`a *b*` ``c ` d`` ` e ` `  `") shouldBe List(CodeSpan("a *b*"), t(" "), CodeSpan("c ` d"), t(" "),
        CodeSpan("e"), t(" "), CodeSpan("  "))
      one("`open") shouldBe List(t("`open"))
      one("`a\nb`") shouldBe List(CodeSpan("a b"))
    }
    "undo backslash escapes and character references" in {
      one("\\*not\\* \\_x\\_ \\a") shouldBe List(t("*not* _x_ \\a"))
      one("&amp; &copy; &#35; &#x22; &nosuch; & alone") shouldBe List(t("& \u00a9 # \" &nosuch; & alone"))
    }
    "take hard breaks from two spaces or a backslash, and soft ones otherwise" in {
      one("a  \nb\\\nc\nd") shouldBe List(t("a"), HardBreak, t("b"), HardBreak, t("c"), SoftBreak, t("d"))
      one("trailing  ") shouldBe List(t("trailing"))
    }
    "take inline links with titles and brackets in their hrefs, and images with their alt text" in {
      one("[a *b*](/u \"T\") ![alt *x*](img.png) [p](/a(b)c) [e](<a b>)") shouldBe List(
        Link("/u", "T", List(t("a "), Emphasis(List(t("b"))))), t(" "), Image("img.png", "", "alt x"), t(" "),
        Link("/a(b)c", "", List(t("p"))), t(" "), Link("a b", "", List(t("e"))))
      one("[not a link] (x)") shouldBe List(t("[not a link] (x)"))
      one("[a](b") shouldBe List(t("[a](b"))
      one("[![img](i.png)](/to)") shouldBe List(Link("/to", "", List(Image("i.png", "", "img"))))
    }
    "never put a link inside a link" in {
      one("[a [b](/in) c](/out)") shouldBe List(t("[a "), Link("/in", "", List(t("b"))), t(" c](/out)"))
    }
    "take autolinks, in angle brackets and bare" in {
      one("<https://a.b/c> <me@x.org> <nope>") shouldBe List(Link("https://a.b/c", "", List(t("https://a.b/c"))),
        t(" "), Link("mailto:me@x.org", "", List(t("me@x.org"))), t(" <nope>"))
      one("see https://example.com/a_b. or www.x.org, (https://y.io/p)") shouldBe List(t("see "),
        Link("https://example.com/a_b", "", List(t("https://example.com/a_b"))), t(". or "),
        Link("http://www.x.org", "", List(t("www.x.org"))), t(", ("), Link("https://y.io/p", "", List(t("https://y.io/p"))),
        t(")"))
      one("nohttps://x.y") shouldBe List(t("nohttps://x.y"))
    }
    "resolve references, full, collapsed and shortcut, by a label in any case" in {
      val refs = "\n\n[ref]: /r 'Tt'"
      Markdown.parse("[text][REF] [ref][] [ref] [miss][]" + refs).head shouldBe para(
        Link("/r", "Tt", List(t("text"))), t(" "), Link("/r", "Tt", List(t("ref"))), t(" "),
        Link("/r", "Tt", List(t("ref"))), t(" [miss][]"))
    }
  }

  "A streamed document" should {
    "cost per append what its last block does, not what the whole document does" in {
      val section = "## Section\n\nSome *words* and `code` and a [link](/x), then more words to make a line.\n\n" +
        "- an item\n- another item\n\n```scala\nval x = List(1, 2, 3).map(_ * 2)\n```\n\n"
      val doc = section * (10240 / section.length + 1)
      def run(): Double = {
        val stream = new MarkdownStream
        var at = 0
        var appends = 0
        val start = System.nanoTime()
        while (at < doc.length) {
          at = math.min(doc.length, at + 4)
          stream.update(doc.substring(0, at))
          appends += 1
        }
        stream.blocks.toList shouldBe Markdown.parse(doc)
        (System.nanoTime() - start) / 1000.0 / appends
      }
      run()
      val each = run()
      val whole = {
        val start = System.nanoTime()
        (1 to 20).foreach(_ => Markdown.parse(doc))
        (System.nanoTime() - start) / 1000.0 / 20
      }
      info(f"${doc.length} characters, 4 at a time: $each%.1f \u00b5s an append; the whole parsed at once $whole%.1f \u00b5s")
      each should be < whole
    }
    "parse as the whole text does, keeping every block but the last as it grows" in {
      val doc = "# Title\n\nA paragraph with *emphasis* and `code`.\n\n- one\n- two\n\n```scala\nval x = 1\n```\n\n" +
        "| a | b |\n|---|---|\n| 1 | 2 |\n\n> quoted\n> more\n\n1. first\n\n2. second\n\nEnd *here*.\n"
      val stream = new MarkdownStream
      var kept: Vector[MdBlock] = Vector.empty
      var at = 0
      while (at < doc.length) {
        at = math.min(doc.length, at + 3)
        val before = stream.blocks
        val from = stream.update(doc.substring(0, at))
        from should be >= math.max(0, before.size - 1)
        (0 until from).foreach(i => stream.blocks(i) should be theSameInstanceAs before(i))
        stream.blocks.toList shouldBe Markdown.parse(doc.substring(0, at))
      }
      stream.blocks.size shouldBe 8
    }
    "show an unterminated fence as code while it streams, then close it" in {
      val stream = new MarkdownStream
      stream.update("Intro\n\n```js\nlet a")
      stream.blocks.last shouldBe CodeBlock("js", "let a", closed = false)
      stream.update("Intro\n\n```js\nlet a = 1\n```\nafter")
      stream.blocks.toList shouldBe List(para(t("Intro")), CodeBlock("js", "let a = 1"), para(t("after")))
    }
    "follow an edit in the middle, and a definition that changes links before it" in {
      val stream = new MarkdownStream
      stream.update("# A\n\npara one\n\npara two\n\n[x]")
      stream.update("# A\n\npara ONE\n\npara two\n\n[x]") shouldBe 0
      stream.blocks.toList shouldBe Markdown.parse("# A\n\npara ONE\n\npara two\n\n[x]")
      stream.update("# A\n\npara ONE\n\npara two\n\n[x]\n\n[x]: /there") shouldBe 0
      stream.blocks(3) shouldBe para(Link("/there", "", List(t("x"))))
    }
  }
}
