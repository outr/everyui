package everyui.push

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A Markdown push (`PushFormat.Markdown`): its words made plain for a platform that shows no styles (`PushText.plain`,
  * over every case of PushTextCases), its styled runs (`PushText.runs`), struck words left out of both, and its
  * format read from "format" and written back, a title and lines always plain words. */
class PushTextSpec extends AnyWordSpec with Matchers {
  "PushText.plain" should {
    PushTextCases.all.foreach { (markdown, plain) =>
      s"make ${markdown.take(40).replace("\n", "\\n")} plain" in {
        PushText.plain(markdown) shouldBe plain
      }
    }
    "keep every word of a long body, quickly" in {
      val paragraph = "The **agent** ran `step` and wrote [a note](https://x.io/n) on *it*. "
      val markdown = (1 to 400).map(i => paragraph + i.toString).mkString("\n\n- item\n\n")
      val started = System.nanoTime()
      val plain = PushText.plain(markdown)
      (System.nanoTime() - started) / 1000000L should be < 2000L
      plain.length should be > 400 * 50
      plain should not include "**"
      plain should not include "`"
      plain should not include "https://"
      plain should endWith("on it. 400")
    }
  }

  "PushText.runs" should {
    "style what the marks said, their words plain's" in {
      val markdown = "# Done\n\n**Bold**, *slant*, ***both***, ~~gone~~ and `code`, [a link](https://x.io)\n\n```\nblock\n```"
      val runs = PushText.runs(markdown)
      runs.map(_.text).mkString shouldBe PushText.plain(markdown)
      def run(text: String) = runs.find(_.text == text).getOrElse(fail("no run " + text + " in " + runs.toString))
      run("Done").weight shouldBe 700
      run("Bold").weight shouldBe 700
      run("slant").italic shouldBe true
      run("slant").weight shouldBe 0
      run("both").weight shouldBe 700
      run("both").italic shouldBe true
      // (struck words left out, a notification showing no strikethrough)
      runs.exists(_.text.contains("gone")) shouldBe false
      runs.exists(_.strike) shouldBe false
      run("code").family shouldBe "monospace"
      run("block").family shouldBe "monospace"
      // a link is its words in the text's own style, with nowhere to go
      runs.filter(_.text.contains("a link")).foreach { r =>
        r.href shouldBe ""
        r.color shouldBe ""
        r.underline shouldBe false
      }
    }
    "give a bullet and a line break their own plain runs" in {
      val runs = PushText.runs("- **a**\n- b")
      runs.map(_.text) shouldBe List("• ", "a", "\n• b")
      runs.head.weight shouldBe 0
    }
    "be none for no words" in {
      PushText.runs("") shouldBe Nil
    }
  }

  "A push's format" should {
    "be read from \"format\", in any case, and plain where it says nothing else" in {
      PushMessage.of(Map("title" -> "t", "body" -> "b", "format" -> "markdown")).format shouldBe PushFormat.Markdown
      PushMessage.of(Map("body" -> "b", "format" -> " Markdown ")).format shouldBe PushFormat.Markdown
      PushMessage.of(Map("body" -> "b")).format shouldBe PushFormat.Plain
      PushMessage.of(Map("body" -> "b", "format" -> "html")).format shouldBe PushFormat.Plain
      PushFormat.named("plain") shouldBe PushFormat.Plain
    }
    "be the push's own key, not its data, and written back only where it is Markdown" in {
      val sent = Map("title" -> "**Ann**", "body" -> "*hi*", "format" -> "markdown", "uid" -> "7")
      val m = PushMessage.of(sent)
      m.data shouldBe Map("uid" -> "7")
      m.fields shouldBe sent
      PushMessage.of(m.fields) shouldBe m
      PushMessage("t", "b", None).fields.contains("format") shouldBe false
      PushFormat.Markdown.word shouldBe "markdown"
    }
    "show a Markdown push's title, body and lines as plain words, and a plain one's as they are" in {
      val m = PushMessage("**Agent** `deploy`", "Shipped *v2*", None, lines = Vector("**one**", "`two`"),
        format = PushFormat.Markdown)
      m.shownTitle shouldBe "Agent deploy"
      m.shownBody shouldBe "Shipped v2"
      m.shownLines shouldBe Vector("one", "two")
      val plain = m.copy(format = PushFormat.Plain)
      plain.shownTitle shouldBe "**Agent** `deploy`"
      plain.shownBody shouldBe "Shipped *v2*"
      plain.shownLines shouldBe Vector("**one**", "`two`")
    }
    "make a title plain words, never styled, whatever it starts with" in {
      PushMessage("# **Build** _done_", "", None, format = PushFormat.Markdown).shownTitle shouldBe "Build done"
    }
  }
}
