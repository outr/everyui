package everyui.chat

import everyui.component.{Component, RichText, TextView}
import everyui.event.Pointer
import everyui.focus.Focus
import everyui.markdown.MarkdownView
import everyui.theme.Tone
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** Passages of a message highlighted where they are (`ChatEntry.Message.highlights`), on the desktop's real nodes:
  * marked in a paragraph, a list item and a table cell's bold words and never in code, set, changed and cleared in
  * place with the bubble where it was, marked once a streaming message's words arrive; the words under an asking
  * message counting them and, pressed or by the keys, bringing each into view in turn; and a screen reader hearing
  * each with its label. */
class MessageHighlightSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val reply: String = "A note first:\n\n```\n// Shall I start with group 1?\n```\n\n" +
    "Groups are ready. Shall I start with group 1? It is smaller.\n\n- `Socket.scala` backs off\n" +
    "- Do you want the old flag kept?\n\n| Item | Status |\n| --- | --- |\n" +
    "| Changelog | **Should it list internal fixes?** |\n\nThen I open the pull requests."
  private val questions = Vector("Shall I start with group 1?", "Do you want the old flag kept?",
    "**Should it list internal fixes?**").map(MessageHighlight(_, label = "Question for you"))

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
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

  // every RichText under `c`, in order
  private def texts(c: Component): Vector[RichText] = c match {
    case r: RichText => Vector(r)
    case _ => c.childComponents.flatMap(texts)
  }
  // the words marked under `c`, each line's or cell's marked runs joined
  private def markedNow(c: Component): Vector[String] =
    texts(c).map(_.runs().filter(_.mark.nonEmpty).map(_.text).mkString).filter(_.nonEmpty)
  private def marked(c: Component): Vector[String] = fx(markedNow(c))
  private def markdown(v: MessageView): MarkdownView = v.childComponents.collectFirst {
    case b: everyui.component.Container if b eq v.bubble => b.childComponents.collectFirst { case w: MessageMarkdown => w.view }
  }.flatten.get
  private def labelNow(v: MessageView): Option[String] = v.asking.filter(_.visible()).map { r =>
    r.childComponents.collect { case w: TextView => w.content() }.lastOption.getOrElse("")
  }
  private def label(v: MessageView): Option[String] = fx(labelNow(v))

  private def standalone(m: ChatEntry.Message, revealed: Component => Unit = _ => (),
                         heard: String => Unit = _ => ()): MessageView =
    fx(new MessageView(m, true, 800.0, Var(0L), MessageMarkdown.render, announce = heard, reveal = revealed))

  "A message's highlights" should {
    "be marked where they are, never in code, and set, changed and cleared in place" in {
      val m = ChatEntry.message("m1", ada, self = false, reply)
      val v = standalone(m)
      onScreen(v) {
        val bubble = fx((v.bubble.size.width(), v.bubble.size.height()))
        val blocks = fx(markdown(v).childComponents)
        marked(v) shouldBe Vector.empty
        fx(m.highlights @= questions)
        eventually(markedNow(v).size == 3) shouldBe true
        marked(v) shouldBe Vector("Shall I start with group 1?", "Do you want the old flag kept?",
          "Should it list internal fixes?")
        // the code block's words are not marked, and nothing was made again
        fx(texts(markdown(v).childComponents(1)).flatMap(_.runs()).exists(_.mark.nonEmpty)) shouldBe false
        fx(markdown(v).childComponents).zip(blocks).forall(p => p._1 eq p._2) shouldBe true
        fx((v.bubble.size.width(), v.bubble.size.height())) shouldBe bubble
        // the bold cell keeps its weight, marked
        fx(texts(v).flatMap(_.runs()).find(_.text == "Should it list internal fixes?").map(_.weight)) shouldBe Some(700)

        // changed: one gone, another tone, one not there passed over
        fx(m.highlights @= Vector(MessageHighlight("Groups are ready.", Tone.Info), questions(1),
          MessageHighlight("Nowhere in it")))
        eventually(markedNow(v) == Vector("Groups are ready.", "Do you want the old flag kept?")) shouldBe true
        val info = fx(everyui.markdown.Passage.wash(Tone.Info))
        fx(texts(v).flatMap(_.runs()).find(_.text == "Groups are ready.").map(_.mark)) shouldBe
          Some(if (info.alpha >= 1.0) info.toHex else info.toHexAlpha)
        fx(markdown(v).childComponents).zip(blocks).forall(p => p._1 eq p._2) shouldBe true

        fx(m.highlights @= Vector.empty)
        eventually(markedNow(v).isEmpty) shouldBe true
        fx((v.bubble.size.width(), v.bubble.size.height())) shouldBe bubble
      }
    }

    "be marked once a streaming message's words arrive" in {
      val m = ChatEntry.message("m1", ada, self = false, "Groups are ready. Shall I", streaming = true)
      val v = standalone(m)
      onScreen(v) {
        fx(m.highlights @= Vector(questions(0)))
        marked(v) shouldBe Vector.empty
        fx(m.text @= m.text() + " start with")
        marked(v) shouldBe Vector.empty
        fx(m.text @= m.text() + " group 1? It is smaller.")
        eventually(markedNow(v) == Vector("Shall I start with group 1?")) shouldBe true
        fx(m.streaming @= false)
        marked(v) shouldBe Vector("Shall I start with group 1?")
      }
    }

    "make nothing for a message with none" in {
      val m = ChatEntry.message("m1", ada, self = false, reply)
      val v = standalone(m)
      onScreen(v) {
        m.highlightsKept shouldBe false
        fx(v.jumper) shouldBe None
        marked(v) shouldBe Vector.empty
      }
    }
  }

  "The words under an asking message" should {
    "count its highlights found, where they are the default's" in {
      val m = ChatEntry.message("m1", ada, self = false, reply)
      fx(m.attention @= Some(Attention.asking))
      val v = standalone(m)
      onScreen(v) {
        label(v) shouldBe Some("Question for you")
        fx(v.jumper) shouldBe None
        fx(m.highlights @= questions)
        eventually(labelNow(v) == Some("3 questions for you")) shouldBe true
        fx(v.jumper).nonEmpty shouldBe true
        fx(m.highlights @= Vector(questions(0), MessageHighlight("Nowhere in it")))
        eventually(labelNow(v) == Some("Question for you")) shouldBe true
        fx(v.jumper).nonEmpty shouldBe true
        fx(m.highlights @= questions.take(2))
        eventually(labelNow(v) == Some("2 questions for you")) shouldBe true
        // an app's own words are its own
        fx(m.attention @= Some(Attention.Asking(Some("Your call"))))
        label(v) shouldBe Some("Your call")
        fx(m.attention @= Some(Attention.asking))
        label(v) shouldBe Some("2 questions for you")
        fx(m.highlights @= Vector.empty)
        eventually(labelNow(v) == Some("Question for you")) shouldBe true
        fx(v.jumper) shouldBe None
      }
    }

    "bring each highlight into view in turn, from the first again after the last, and say it" in {
      val m = ChatEntry.message("m1", ada, self = false, reply)
      fx(m.attention @= Some(Attention.asking))
      fx(m.highlights @= questions)
      var revealed: Vector[Component] = Vector.empty
      var heard: Vector[String] = Vector.empty
      val v = standalone(m, c => revealed = revealed :+ c, s => heard = heard :+ s)
      onScreen(v) {
        eventually(v.jumper.nonEmpty) shouldBe true
        val words = Vector("Shall I start with group 1?", "Do you want the old flag kept?", "Should it list internal fixes?")
        // what each was brought into view by: the line or the cell holding it
        def holds(c: Component, w: String): Boolean = fx(texts(c).exists(_.runs().map(_.text).mkString.contains(w)))
        (1 to 4).foreach(_ => fx(v.jumper.get.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click,
          0.0, 0.0)))
        revealed.size shouldBe 4
        revealed.zip(words :+ words.head).forall(p => holds(p._1, p._2)) shouldBe true
        heard shouldBe (words :+ words.head).map("Question for you: " + _)
      }
    }

    "be reached by the forward arrow before the actions, Enter bringing the next into view" in {
      val m1 = ChatEntry.message("m1", ada, self = false, "Morning.")
      val m2 = ChatEntry.message("m2", ada, self = false, reply)
      var ran: List[String] = Nil
      fx {
        m2.attention @= Some(Attention.asking)
        m2.highlights @= questions
        m2.actions @= List(MessageAction(everyui.font.MaterialIcon.VisibilityOff, "Ignore", () => ran = ran :+ "ignore"))
      }
      val t = fx {
        val made = new Transcript(Var(Vector[ChatEntry](m1, m2)))
        made.size.width @= 800.0
        made.size.height @= 300.0
        made
      }
      def view: MessageView = t.list.rows.find(_.item().exists(_.id == "m2")).collect { case r: ChatRow => r }
        .flatMap(_.message).get
      onScreen(t) {
        eventually(t.list.rows.exists(_.item().exists(_.id == "m2"))) shouldBe true
        eventually(view.jumper.nonEmpty) shouldBe true
        fx(Pointer.usedAt = -1.0e9)
        fx(t.focus())
        fx(t.list.current @= Some("m2"))
        def on(c: => Component): Boolean = Focus.focused().exists(_ eq c)
        press(KeyCode.RIGHT)
        eventually(on(view.jumper.get)) shouldBe true
        // the reply is taller than the transcript: the table's question is brought into view, below where it shows
        val offsets = (1 to 3).map { _ =>
          press(KeyCode.ENTER)
          fx(t.list.offset())
        }
        offsets.last should be > 0.0
        press(KeyCode.RIGHT)
        eventually(on(view.actionButtons.head)) shouldBe true
        press(KeyCode.LEFT)
        eventually(on(view.jumper.get)) shouldBe true
        press(KeyCode.ESCAPE)
        eventually(on(t.list)) shouldBe true
        ran shouldBe Nil
      }
    }
  }

  "A screen reader" should {
    "hear each highlight found with its label, after the message, as they change" in {
      val m = ChatEntry.message("m1", ada, self = false, "Groups are ready. Shall I start with group 1? Or not.")
      fx(m.attention @= Some(Attention.asking))
      val t = fx {
        val made = new Transcript(Var(Vector[ChatEntry](m)))
        made.size.width @= 800.0
        made.size.height @= 400.0
        made
      }
      def saidNow: String = t.list.rows.find(_.item().exists(_.id == "m1")).get.semantics().label
      def said: String = fx(saidNow)
      onScreen(t) {
        eventually(t.list.rows.exists(_.item().exists(_.id == "m1"))) shouldBe true
        said shouldBe "Ada: Groups are ready. Shall I start with group 1? Or not., Question for you"
        fx(m.highlights @= Vector(questions(0), MessageHighlight("Or not.", Tone.Info), MessageHighlight("Nowhere")))
        eventually(saidNow == "Ada: Groups are ready. Shall I start with group 1? Or not., 2 questions for you, " +
          "Question for you: Shall I start with group 1?, Highlighted: Or not.") shouldBe true
        fx(m.highlights @= Vector.empty)
        eventually(saidNow == "Ada: Groups are ready. Shall I start with group 1? Or not., Question for you") shouldBe true
      }
    }
  }
}
