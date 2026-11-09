package everyui.chat

import everyui.component.Component
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** What a screen reader is given of a transcript's entries, on the desktop's real nodes: a message's Markdown said as
  * its words (`Transcript.said`, its row's name and what is announced), a terminal's block with no fence and no
  * escapes, bold, code and a link as their words, escapes in the prose taken out too, a plain message as it was and a
  * long one whole; a tool call's body the same; a transcript of plain text saying its words as they are less the
  * escapes; and a turn header's line, tip and name its words, or a plain one's as they are. */
class TranscriptSaidSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val esc = "\u001b"

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
    }
  }

  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))

  private def transcript(entries: Var[Vector[ChatEntry]]): Transcript = {
    val t = fx(new Transcript(entries))
    fx {
      t.size.width @= 700.0
      t.size.height @= 900.0
    }
    t
  }

  // the name its row gives a screen reader
  private def rowSaid(t: Transcript, key: String): String =
    fx(t.list.rowAt(t.list.indexOf(key)).get.asInstanceOf[ChatRow].semantics().label)

  private val build = "Build output:\n\n```ansi\n" + esc + "[2m10:00:01" + esc + "[0m " + esc + "[1;31mERROR" + esc +
    "[0m tests failed\n" + esc + "[32mok" + esc + "[0m " + esc + "]8;;https://ci.example/run/7" + esc + "\\run 7" +
    esc + "]8;;" + esc + "\\\n```\n\nSee above."

  "A transcript" should {
    "say a terminal's block with no fence and no escapes" in {
      val m = ChatEntry.message("m", ada, self = false, build)
      val t = transcript(Var(Vector(m)))
      onScreen(t) {
        settle(t)
        val said = fx(t.said(m))
        said shouldBe "Ada: Build output:\n\n10:00:01 ERROR tests failed\nok run 7\n\nSee above."
        said should not include "```"
        said should not include esc
        said should not include "[0m"
        rowSaid(t, "m") shouldBe said
      }
    }

    "say bold, inline code and a link as their words" in {
      val m = ChatEntry.message("m", ada, self = false,
        "**Done**: run `sbt test`, then read [the notes](https://example.com/notes) and _ship_ it.")
      val t = transcript(Var(Vector(m)))
      onScreen(t) {
        settle(t)
        fx(t.said(m)) shouldBe "Ada: Done: run sbt test, then read the notes and ship it."
        rowSaid(t, "m") shouldBe "Ada: Done: run sbt test, then read the notes and ship it."
        // a link with no words of its own is where it goes; a list keeps its items' marks, a heading its words
        fx(t.said(ChatEntry.message("u", ada, false, "<https://example.com>"))) shouldBe "Ada: https://example.com"
        fx(t.said(ChatEntry.message("l", ada, false, "# Plan\n\n- one\n- two"))) shouldBe "Ada: Plan\n\n- one\n- two"
      }
    }

    "take a terminal's escapes out of the prose as well" in {
      val m = ChatEntry.message("m", ada, self = false, "Status: " + esc + "[1;32mgreen" + esc + "[0m now")
      val t = transcript(Var(Vector(m)))
      try fx(t.said(m)) shouldBe "Ada: Status: green now" finally fx(t.dispose())
    }

    "say a plain message as it was, its line breaks kept" in {
      val words = "Hello there, are you free at 3? The build on main is green again.\nSecond line, as typed."
      val m = ChatEntry.message("m", ada, self = false, words)
      val t = transcript(Var(Vector(m)))
      onScreen(t) {
        settle(t)
        fx(t.said(m)) shouldBe "Ada: " + words
        rowSaid(t, "m") shouldBe "Ada: " + words
      }
    }

    "say a long message whole, as before" in {
      val words = (1 to 400).map(i => "Sentence " + i + " of a long answer.").mkString(" ")
      words.length should be > 8000
      val m = ChatEntry.message("m", ada, self = false, words)
      val t = transcript(Var(Vector(m)))
      try fx(t.said(m)) shouldBe "Ada: " + words finally fx(t.dispose())
    }

    "follow a message's words as they change, as they stream in" in {
      val m = ChatEntry.message("m", ada, self = false, "**Work", streaming = true)
      val t = transcript(Var(Vector(m)))
      onScreen(t) {
        settle(t)
        rowSaid(t, "m") shouldBe "Ada: **Work"
        fx(m.text @= "**Working** on `it`")
        rowSaid(t, "m") shouldBe "Ada: Working on it"
      }
    }

    "say a tool call's body as its words" in {
      val a = ChatEntry.activity("a", "Ran the tests", ActivityStatus.Failed,
        body = "**2 failed**: `" + esc + "[31mSpec" + esc + "[0m`")
      val t = transcript(Var(Vector(a)))
      try fx(t.said(a)) shouldBe "Ran the tests: 2 failed: Spec, " + ChatWords.failed finally fx(t.dispose())
    }

    "say plain text's words as they are, less a terminal's escapes, where they are plainText" in {
      val m = ChatEntry.message("m", ada, self = false, "Use **stars** and `ticks`: " + esc + "[31mred" + esc + "[0m")
      val t = transcript(Var(Vector(m)))
      onScreen(t) {
        fx(t.plainText @= true)
        settle(t)
        fx(t.said(m)) shouldBe "Ada: Use **stars** and `ticks`: red"
        rowSaid(t, "m") shouldBe "Ada: Use **stars** and `ticks`: red"
        fx(t.said(ChatEntry.message("p", ada, false, "a\tb"))) shouldBe "Ada: a\tb"
      }
    }
  }

  "A turn header" should {
    "show and say a message's words, not its Markdown" in {
      val m = ChatEntry.message("q", ada, self = true, "Why does **this** fail?\n\n```ansi\n" + esc + "[31mboom" + esc +
        "[0m\n```")
      val h = TurnHeader.of(m, named = true, attachment = Some("log.txt"))
      h.line shouldBe "Ada: Why does this fail? boom"
      h.said shouldBe "Ada: Why does this fail? boom, log.txt"
      val view = fx(new TurnHeaderView(() => ()))
      fx(view.header @= Some(h))
      try {
        fx(view.said()) shouldBe "Ada: Why does this fail? boom, log.txt"
        fx(view.semantics().label) shouldBe ChatWords.jumpToMessage("Ada: Why does this fail? boom, log.txt")
        fx(view.tooltip()) shouldBe "Ada: Why does this fail? boom, log.txt"
      } finally fx(view.dispose())
    }

    "show a plain one's words as they are" in {
      val m = ChatEntry.message("q", ada, self = true, "Use **stars**\n" + esc + "[1mnow" + esc + "[0m")
      TurnHeader.of(m, plain = true).line shouldBe "Use **stars** now"
      TurnHeader.of(ChatEntry.message("p", ada, self = true, "Plain words, as typed")).said shouldBe
        "Plain words, as typed"
    }
  }
}
