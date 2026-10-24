package everyui.markdown

import everyui.chat.{ChatEntry, MentionPressed, Participant, Transcript}
import everyui.component.{Component, RichText}
import everyui.focus.Focus
import everyui.text.TextRun
import everyui.{FxSpec, ui}
import reactify.*

/** Mentions: "@" and a name that counts, found in words (names with spaces, the longest first, whatever their case,
  * never in an email address or a longer word), and drawn in the mention colour on its wash by a Markdown view, outside
  * code and links, following the names as they change; and a transcript's messages drawn with its names. */
class MentionsSpec extends FxSpec {
  private val names = Set("Helix", "Matt", "Matt Hicks")

  // the words drawn: not a paragraph's lines measured out of sight
  private def texts(c: Component): List[RichText] = c match {
    case _ if !c.visible() => Nil
    case r: RichText => List(r)
    case _ => c.childComponents.toList.flatMap(texts)
  }

  // the runs drawn as mentions under `c` (on the FX thread)
  private def mentioned(c: Component): List[String] = {
    val colour = MarkdownView.mention().toHex
    texts(c).flatMap(_.runs()).filter(r => r.color == colour && r.background.nonEmpty).map(_.text)
  }

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

  "Mentions" should {
    "find the names that count, with their spaces, the longest first, whatever their case" in {
      Mentions.split("@Matt Hicks, can @helix look?", names) shouldBe
        List(("@Matt Hicks", true), (", can ", false), ("@helix", true), (" look?", false))
      Mentions.split("Thanks @Matt", names) shouldBe List(("Thanks ", false), ("@Matt", true))
      Mentions.split("@Matt Hickson", names) shouldBe List(("@Matt", true), (" Hickson", false))
    }

    "leave an email address, a longer word, a name that does not count and words with none as they are" in {
      Mentions.split("mail matt@helix.dev", names) shouldBe List(("mail matt@helix.dev", false))
      Mentions.split("@Helixir is new", names) shouldBe List(("@Helixir is new", false))
      Mentions.split("@Ada said so", names) shouldBe List(("@Ada said so", false))
      Mentions.split("@Helix", Set.empty) shouldBe List(("@Helix", false))
      Mentions.any("no one here", names) shouldBe false
      Mentions.any("(@Helix)", names) shouldBe true
    }
  }

  "A Markdown view with mentions" should {
    "draw them in the mention colour on its wash, in emphasis too, but not in code or a link" in {
      val view = fx(MarkdownView("Ask @Helix and *@Matt Hicks*, not `@Helix` or [@Matt](/m).\n\n```\n@Helix\n```", 500.0))
      onScreen(view) {
        fx(mentioned(view)) shouldBe Nil
        fx(view.mentions @= names)
        eventually(mentioned(view).nonEmpty) shouldBe true
        fx(mentioned(view)) shouldBe List("@Helix", "@Matt Hicks")
        val runs: List[TextRun] = fx(texts(view).flatMap(_.runs()))
        runs.find(_.text == "@Matt Hicks").exists(_.italic) shouldBe true
        // without them, the words as they were
        fx(view.mentions @= Set.empty)
        eventually(mentioned(view).isEmpty) shouldBe true
        fx(texts(view).head.runs().map(_.text).mkString) should startWith("Ask @Helix and ")
      }
    }

    "follow the names as they change, the paragraph drawn in place" in {
      val view = fx(MarkdownView("Over to @Ada, then @Helix.", 500.0))
      onScreen(view) {
        fx(view.mentions @= Set("Helix"))
        eventually(mentioned(view) == List("@Helix")) shouldBe true
        val para = fx(view.children().head)
        fx(view.mentions @= Set("Helix", "Ada"))
        eventually(mentioned(view) == List("@Ada", "@Helix")) shouldBe true
        fx(view.children().head) should be theSameInstanceAs para
      }
    }

    "find a mention in a table's cells" in {
      val view = fx(MarkdownView("| who | what |\n|---|---|\n| @Helix | tests |", 500.0))
      fx(view.mentions @= names)
      fx(view.content @= "| who | what |\n|---|---|\n| @Helix | tests, for @Matt |")
      onScreen(view) {
        eventually(mentioned(view).nonEmpty) shouldBe true
        fx(mentioned(view)) should contain("@Helix")
      }
    }
  }

  // the drawn words `text` under `c` clicked, as the desktop's own click on them
  private def click(c: Component, text: String): Unit = fx {
    val node = texts(c).flatMap(r => {
      val flow = r.node.fx.asInstanceOf[everyui.render.RichFlow]
      (0 until flow.getChildren.size).map(flow.getChildren.get).collect { case t: javafx.scene.text.Text => t }
    }).find(_.getText == text).get
    javafx.event.Event.fireEvent(node, new javafx.scene.input.MouseEvent(
      javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1,
      false, false, false, false, true, false, false, true, false, false, null))
  }

  "A pressed mention" should {
    "be a link only where something answers it, and answer a click with the name as given, never followed" in {
      var followed: List[String] = Nil
      var pressed: List[(String, Component)] = Nil
      val view = fx(new MarkdownView("Ask @helix and [docs](https://docs.example).", onLink = h => followed = followed :+ h))
      fx {
        view.size.width @= 500.0
        view.mentions @= names
      }
      onScreen(view) {
        eventually(mentioned(view) == List("@helix")) shouldBe true
        fx(texts(view).flatMap(_.runs()).filter(_.text == "@helix").map(_.href)) shouldBe List("")
        fx(view.onMention @= Some((n: String, at: Component) => pressed = pressed :+ (n -> at)))
        eventually(texts(view).flatMap(_.runs()).exists(_.href == Mentions.href("Helix"))) shouldBe true
        // still drawn as a mention, not as a link
        fx(texts(view).flatMap(_.runs()).find(_.text == "@helix").exists(r => !r.underline &&
          r.color == MarkdownView.mention().toHex)) shouldBe true
        click(view, "@helix")
        pressed.map(_._1) shouldBe List("Helix")
        pressed.head._2.isInstanceOf[RichText] shouldBe true
        followed shouldBe Nil
        // the link beside it still goes where it goes
        click(view, "docs")
        followed shouldBe List("https://docs.example")
        pressed.size shouldBe 1
        // and none answering again: only drawn
        fx(view.onMention @= None)
        eventually(texts(view).flatMap(_.runs()).filter(_.text == "@helix").forall(_.href.isEmpty)) shouldBe true
      }
    }

    "be reached from the keys: its paragraph focused, and Enter pressing it" in {
      var pressed: List[String] = Nil
      val view = fx(new MarkdownView("Over to @Matt Hicks."))
      fx {
        view.size.width @= 500.0
        view.mentions @= names
        view.onMention @= Some((n: String, _: Component) => pressed = pressed :+ n)
      }
      onScreen(view) {
        val para = fx(view.children().head.asInstanceOf[ParagraphView])
        eventually(para.focusable()) shouldBe true
        fx(para.openLinks())
        pressed shouldBe List("Matt Hicks")
      }
    }

    "tell a transcript's app whom, in which entry, and where" in {
      val ada = Participant("u-ada", "Ada Lovelace")
      val entries = Var(Vector[ChatEntry](ChatEntry.message("m0", ada, self = false, "Thanks @Helix")))
      val t = fx(new Transcript(entries))
      var pressed: List[MentionPressed] = Nil
      fx {
        t.size.width @= 600.0
        t.size.height @= 400.0
        t.mentions @= Set("Helix")
        t.onMention @= Some((p: MentionPressed) => pressed = pressed :+ p)
      }
      onScreen(t) {
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        eventually(texts(t).flatMap(_.runs()).exists(_.href == Mentions.href("Helix"))) shouldBe true
        click(t, "@Helix")
        pressed.map(p => (p.name, p.entry.id)) shouldBe List(("Helix", "m0"))
        fx(pressed.head.at.size.width()) should be > 0.0
      }
    }
  }

  "A transcript's mentions" should {
    "draw its messages' mentions, and follow the names as people come and go" in {
      val ada = Participant("u-ada", "Ada Lovelace")
      val entries = Var(Vector[ChatEntry](
        ChatEntry.message("m0", ada, self = false, "@Helix, can you check this for @Matt Hicks?")))
      val t = fx(new Transcript(entries))
      fx {
        t.size.width @= 600.0
        t.size.height @= 400.0
      }
      onScreen(t) {
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        eventually(texts(t).nonEmpty) shouldBe true
        fx(mentioned(t)) shouldBe Nil
        fx(t.mentions @= Set("Helix", "Matt Hicks"))
        eventually(mentioned(t) == List("@Helix", "@Matt Hicks")) shouldBe true
        // somebody leaves
        fx(t.mentions @= Set("Helix"))
        eventually(mentioned(t) == List("@Helix")) shouldBe true
        // and a message arriving is drawn with them
        fx(entries @= entries() :+ ChatEntry.message("m1", ada, self = false, "Thanks @helix"))
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        eventually(mentioned(t) == List("@Helix", "@helix")) shouldBe true
      }
    }
  }
}
