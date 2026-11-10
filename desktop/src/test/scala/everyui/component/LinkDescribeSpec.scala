package everyui.component

import everyui.focus.Focus
import everyui.markdown.{MarkdownView, ParagraphView}
import everyui.render.RichFlow
import everyui.text.TextRun
import everyui.tooltip.Tooltip
import everyui.{FxSpec, ui}

/** What a link says on a hover (`Link.describe`): its scheme's links given the words as their run's title, drawn as
  * the platform's tooltip and a screen reader's help over their letters, another scheme's and a None nothing, a safe
  * link as it was unless its scheme is described, the describer asked with the run's own href; a Markdown link's
  * title, and a `Link`'s tooltip and description; and a paragraph's links' words shown as the keys focus it. */
class LinkDescribeSpec extends FxSpec {
  // the focus as the keys move it, which shows a paragraph's tip
  override protected def keyed: Boolean = true

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      Tooltip.delay @= 0.05
      Tooltip.onFocus @= true
      ui.children.clear()
      Focus.focused @= None
      ui.children += c
    }
    try test finally fx {
      Tooltip.hide()
      Tooltip.delay @= 0.6
      ui.children.clear()
      Focus.focused @= None
      c.dispose()
    }
  }

  // the letters drawn under `c` (not a paragraph's lines measured out of sight), with the tooltip over each and what a
  // screen reader hears after them: read on the FX thread
  private def drawn(c: Component): List[(String, String, String)] = texts(c).flatMap { r =>
    val flow = r.node.fx.asInstanceOf[RichFlow]
    (0 until flow.getChildren.size).map(flow.getChildren.get).collect { case t: javafx.scene.text.Text =>
      val tip = t.getProperties.get("javafx.scene.control.Tooltip") match {
        case tt: javafx.scene.control.Tooltip => tt.getText
        case _ => ""
      }
      (t.getText, tip, Option(t.getAccessibleHelp).getOrElse(""))
    }
  }

  private def texts(c: Component): List[RichText] = c match {
    case _ if !c.visible() => Nil
    case r: RichText => List(r)
    case _ => c.childComponents.toList.flatMap(texts)
  }

  private def words(c: Component, text: String): (String, String) =
    drawn(c).find(_._1 == text).map(d => (d._2, d._3)).getOrElse(fail("no run " + text))

  private def described[T](test: (() => List[String]) => T): T = {
    var asked: List[String] = Nil
    val stop = Link.describe("file:") { href =>
      asked = asked :+ href
      if (href.endsWith(".secret")) None else Some("Open " + href.stripPrefix("file:") + " in a tab")
    }
    try test(() => asked) finally stop()
  }

  "A link's description" should {
    "be its scheme's links' tooltip and description, asked with the run's href, and nothing for another scheme" in {
      var asked: List[String] = Nil
      val stop = Link.describe("File") { href =>
        asked = asked :+ href
        Some("Open " + href.stripPrefix("FILE:"))
      }
      try {
        val text = fx(RichText(List(TextRun("See "), TextRun("Main", href = "FILE:src/Main.scala"), TextRun(" and "),
          TextRun("a note", href = "note:1")), 600.0))
        onScreen(text) {
          fx(words(text, "Main")) shouldBe (("Open src/Main.scala", "Open src/Main.scala"))
          fx(words(text, "a note")) shouldBe (("", ""))
          fx(words(text, "See ")) shouldBe (("", ""))
          asked.distinct shouldBe List("FILE:src/Main.scala")
          // the runs the app gave are as they were: the words are drawn, not written into them
          fx(text.runs().map(_.title)).forall(_.isEmpty) shouldBe true
        }
      } finally stop()
      Link.description("file:a") shouldBe None
    }
    "be nothing where the describer says None, and a safe link as it was unless its scheme is described" in {
      described { _ =>
        val text = fx(RichText(List(TextRun("hidden", href = "file:a.secret"), TextRun(" "),
          TextRun("site", href = "https://everyui.com")), 600.0))
        onScreen(text) {
          fx(words(text, "hidden")) shouldBe (("", ""))
          fx(words(text, "site")) shouldBe (("", ""))
        }
        val https = Link.describe("https")(href => Some("Go to " + href.stripPrefix("https://")))
        try {
          val again = fx(RichText(List(TextRun("site", href = "https://everyui.com")), 600.0))
          onScreen(again)(fx(words(again, "site")) shouldBe (("Go to everyui.com", "Go to everyui.com")))
        } finally https()
      }
    }
    "come after a run's own title, the last describer given answering until it is taken away" in {
      val first = Link.describe("note")(_ => Some("first"))
      val second = Link.describe("note")(_ => Some("second"))
      first()
      Link.titled(TextRun("n", href = "note:1")).title shouldBe "second"
      Link.titled(TextRun("n", href = "note:1", title = "Its own")).title shouldBe "Its own"
      Link.titled(TextRun("n")).title shouldBe ""
      Link.description("note:2") shouldBe Some("second")
      second()
      Link.description("note:2") shouldBe None
    }
    "be a Link's tooltip and description, an app's own tooltip left as it is" in {
      described { asked =>
        val link = fx(new Link("file:README.md"))
        fx((link.tooltip(), link.accessibleDescription())) shouldBe (("Open README.md in a tab",
          "Open README.md in a tab"))
        fx(link.href @= "file:b.secret")
        fx((link.tooltip(), link.accessibleDescription())) shouldBe (("", ""))
        fx(link.href @= "file:c.md")
        fx(link.tooltip()) shouldBe "Open c.md in a tab"
        fx(link.tooltip @= "The app's own")
        fx(link.href @= "file:d.md")
        fx((link.tooltip(), link.accessibleDescription())) shouldBe (("The app's own", "Open d.md in a tab"))
        asked() shouldBe List("file:README.md", "file:b.secret", "file:c.md", "file:d.md")
        val plain = fx(new Link("https://everyui.com"))
        fx((plain.tooltip(), plain.accessibleDescription())) shouldBe (("", ""))
        fx {
          link.dispose()
          plain.dispose()
        }
      }
    }
  }

  "A Markdown link" should {
    "be described by its scheme, or say its own title, as the pointer rests on it" in {
      described { asked =>
        val view = fx(new MarkdownView("Open [Main](file:src/Main.scala), [the site](https://everyui.com \"The site\")" +
          " or [docs](https://docs.example)."))
        fx(view.size.width @= 700.0)
        onScreen(view) {
          eventually(drawn(view).exists(_._1 == "Main")) shouldBe true
          fx(words(view, "Main")) shouldBe (("Open src/Main.scala in a tab", "Open src/Main.scala in a tab"))
          fx(words(view, "the site")) shouldBe (("The site", "The site"))
          fx(words(view, "docs")) shouldBe (("", ""))
          asked().distinct shouldBe List("file:src/Main.scala")
          val para = fx(view.children().head.asInstanceOf[ParagraphView])
          fx(para.titles) shouldBe Map("file:src/Main.scala" -> "Open src/Main.scala in a tab",
            "https://everyui.com" -> "The site")
          fx(para.tip) shouldBe Some("Main: Open src/Main.scala in a tab; the site: The site")
        }
      }
    }
    "show its paragraph's link's words as the keys bring the focus to it, gone as the focus moves on" in {
      described { _ =>
        val view = fx(new MarkdownView("See [Main](file:src/Main.scala).\n\nAnd [docs](https://docs.example)."))
        fx(view.size.width @= 600.0)
        onScreen(view) {
          val paras = fx(view.children().collect { case p: ParagraphView => p })
          eventually(paras.forall(_.focusable())) shouldBe true
          fx(paras.map(_.tip)) shouldBe Vector(Some("Open src/Main.scala in a tab"), None)
          fx(paras.head.focus())
          eventually(Tooltip.shownFor.exists(_ eq paras.head)) shouldBe true
          fx(paras(1).focus())
          fx(Tooltip.shownFor) shouldBe None
          // a paragraph whose links say nothing shows no tip
          Thread.sleep(200L)
          fx(Tooltip.shownFor) shouldBe None
        }
      }
    }
  }
}
