package everyui.chat

import everyui.component.{Button, Component, TextView}
import everyui.event.Pointer
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** A message that asks something of the person (`ChatEntry.Message.attention`) and the app's own actions under one
  * (`ChatEntry.Message.actions`), on the desktop's real nodes: the bubble edged and washed as it comes to ask, its
  * words under it, changed and taken away live, the bubble never moving or changing size, its row's name saying so, and
  * said as it comes to ask while shown; the actions made only where set, run by a click and by the keys, the forward
  * arrow going onto them and on to the buttons beside the message, and a message without either as it always was. */
class MessageAttentionSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")

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

  private def transcript(entries: Vector[ChatEntry]): Transcript = fx {
    val t = new Transcript(Var(entries))
    t.size.width @= 800.0
    t.size.height @= 500.0
    t
  }

  private def row(t: Transcript, id: String): Option[ChatRow] =
    t.list.rows.find(_.item().exists(_.id == id)).collect { case r: ChatRow => r }
  private def view(t: Transcript, id: String): MessageView = row(t, id).flatMap(_.message).get
  private def shown(t: Transcript, id: String): Boolean = row(t, id).flatMap(_.message).nonEmpty

  // the bubble of `id`: where it is in its view, and its size
  private def bubble(t: Transcript, id: String): (Double, Double, Double, Double) = fx {
    val b = view(t, id).bubble
    (b.position.left(), b.position.top(), b.size.width(), b.size.height())
  }
  private def said(t: Transcript, id: String): String = fx(row(t, id).get.semantics().label)
  private def label(t: Transcript, id: String): Option[String] = fx(view(t, id).asking.filter(_.visible()).map { r =>
    // (the words, after the glyph: a MaterialIconView is a TextView too)
    r.childComponents.collect { case w: TextView => w.content() }.lastOption.getOrElse("")
  })

  private def talk(): (ChatEntry.Message, ChatEntry.Message, ChatEntry.Message) = (
    ChatEntry.message("m1", ada, self = false, "Morning."),
    ChatEntry.message("m2", ada, self = false, "Shall I cut the release now, or wait for the fix?"),
    ChatEntry.message("m3", me, self = true, "Let me look."))

  "A message asking for the person" should {
    "be edged and washed, its words under it, changed and cleared live, the bubble never moving" in {
      val (m1, m2, m3) = talk()
      val t = transcript(Vector(m1, m2, m3))
      onScreen(t) {
        eventually(shown(t, "m1") && shown(t, "m2") && shown(t, "m3")) shouldBe true
        val at = bubble(t, "m2")
        val height = fx(view(t, "m2").size.height())
        val plain = fx(view(t, "m2").bubble.backgroundColor())
        fx(view(t, "m2").bubble.borderWidth()) shouldBe 0.0
        fx(view(t, "m2").asking) shouldBe None
        said(t, "m2") shouldBe "Ada: Shall I cut the release now, or wait for the fix?"

        fx(m2.attention @= Some(Attention.asking))
        bubble(t, "m2") shouldBe at
        fx(view(t, "m2").bubble.borderWidth()) shouldBe 1.5
        fx(view(t, "m2").bubble.borderColor()) shouldBe fx(Theme.warning())
        fx(view(t, "m2").bubble.backgroundColor()) should not be plain
        label(t, "m2") shouldBe Some("Question for you")
        // under the bubble, at the bubble's start, the row taller by the words alone
        val words = fx(view(t, "m2").asking.get)
        fx(words.position.top()) shouldBe (at._2 + at._4 + 4.0 +- 0.5)
        fx(words.position.left()) shouldBe (at._1 +- 0.5)
        fx(view(t, "m2").size.height()) shouldBe (height + fx(words.size.height()) + 4.0 +- 0.5)
        said(t, "m2") shouldBe "Ada: Shall I cut the release now, or wait for the fix?, Question for you"

        fx(m2.attention @= Some(Attention.Asking(Some("Your call"))))
        label(t, "m2") shouldBe Some("Your call")
        said(t, "m2") should endWith(", Your call")
        bubble(t, "m2") shouldBe at

        // asking with no words: the edge alone, the row as it was, and still said
        fx(m2.attention @= Some(Attention.Asking(None)))
        label(t, "m2") shouldBe None
        fx(view(t, "m2").size.height()) shouldBe (height +- 0.5)
        said(t, "m2") should endWith(", Question for you")

        fx(m2.attention @= Some(Attention.SetAside))
        bubble(t, "m2") shouldBe at
        fx(view(t, "m2").bubble.borderWidth()) shouldBe 1.0
        fx(view(t, "m2").bubble.borderColor()) shouldBe fx(Theme.warning().withAlpha(0.4))
        fx(view(t, "m2").bubble.backgroundColor()) shouldBe plain
        said(t, "m2") should endWith(", Set aside")

        fx(m2.attention @= None)
        bubble(t, "m2") shouldBe at
        fx(view(t, "m2").bubble.borderWidth()) shouldBe 0.0
        fx(view(t, "m2").bubble.backgroundColor()) shouldBe plain
        fx(view(t, "m2").size.height()) shouldBe (height +- 0.5)
        said(t, "m2") shouldBe "Ada: Shall I cut the release now, or wait for the fix?"
        // one's own asks the same way, its words at its end edge
        fx(m3.attention @= Some(Attention.asking))
        val own = bubble(t, "m3")
        val ownWords = fx(view(t, "m3").asking.get)
        fx(ownWords.position.left() + ownWords.size.width()) shouldBe (own._1 + own._3 +- 0.5)
      }
    }

    "be said as it comes to ask while it shows, and not as it is drawn asking" in {
      val m = ChatEntry.message("m1", ada, self = false, "Which branch?")
      var heard: List[String] = Nil
      def make(): MessageView = fx(new MessageView(m, true, 600.0, Var(0L), MessageText.plain(_, _),
        announce = (s: String) => heard = heard :+ s))
      val v = make()
      onScreen(v) {
        fx(m.attention @= Some(Attention.asking))
        heard shouldBe List("Ada, Question for you")
        // made asking, nothing is said
        val again = make()
        fx(again.dispose())
        heard.length shouldBe 1
        fx(m.attention @= Some(Attention.SetAside))
        heard.length shouldBe 1
        // and taken apart, it says nothing more
        fx(v.dispose())
        fx(m.attention @= Some(Attention.asking))
        heard.length shouldBe 1
      }
    }

    "make nothing more for a message that asks nothing" in {
      val (m1, m2, m3) = talk()
      val before = fx {
        val v = new MessageView(ChatEntry.message("x", ada, self = false, "Morning."), true, 800.0, Var(0L),
          MessageText.plain(_, _))
        val n = v.childComponents.size
        v.dispose()
        n
      }
      val t = transcript(Vector(m1, m2, m3))
      onScreen(t) {
        eventually(shown(t, "m1")) shouldBe true
        fx(view(t, "m1").childComponents.size) shouldBe before
        fx(view(t, "m1").asking) shouldBe None
        fx(view(t, "m1").actionButtons) shouldBe Vector.empty
        fx(view(t, "m1").bubble.borderWidth()) shouldBe 0.0
      }
    }
  }

  "A message's own actions" should {
    "show under it only where the app sets them, and run" in {
      val (m1, m2, m3) = talk()
      var ran: List[String] = Nil
      fx(m2.actions @= List(MessageAction(MaterialIcon.VisibilityOff, "Ignore", () => ran = ran :+ "ignore")))
      val t = transcript(Vector(m1, m2, m3))
      onScreen(t) {
        eventually(shown(t, "m1") && shown(t, "m2")) shouldBe true
        fx(view(t, "m1").actionButtons) shouldBe Vector.empty
        val buttons = fx(view(t, "m2").actionButtons)
        buttons.map { case b: Button => fx(b.label()) } shouldBe Vector("Ignore")
        // under the bubble, the first one's glyph at its start (its padding before it)
        val at = bubble(t, "m2")
        fx(buttons.head.parent().get.position.top()) should be >= at._2 + at._4
        fx(buttons.head.parent().get.position.left()) shouldBe (at._1 - 11.5 * 1.1 +- 0.5)
        val height = fx(view(t, "m2").size.height())
        val line = fx(buttons.head.parent().get.size.height())
        fx(buttons.head.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0.0, 0.0))
        ran shouldBe List("ignore")
        // changed live: two, then none, the row back to the height of one without
        fx(m2.actions @= List(MessageAction(MaterialIcon.VisibilityOff, "Ignore", () => ran = ran :+ "ignore"),
          MessageAction(MaterialIcon.Reply, "Answer", () => ran = ran :+ "answer")))
        fx(view(t, "m2").actionButtons.map { case b: Button => b.label() }) shouldBe Vector("Ignore", "Answer")
        fx(view(t, "m2").size.height()) shouldBe (height +- 0.5)
        fx(m2.actions @= Nil)
        fx(view(t, "m2").actionButtons) shouldBe Vector.empty
        fx(view(t, "m2").size.height()) shouldBe (height - line - 4.0 +- 0.5)
        fx(buttons.head.takenApart) shouldBe true
      }
    }

    "be reached by the forward arrow, before the buttons beside the message, and left back to it" in {
      val (m1, m2, m3) = talk()
      var ran: List[String] = Nil
      fx(m2.actions @= List(MessageAction(MaterialIcon.VisibilityOff, "Ignore", () => {
        ran = ran :+ "ignore"
        m2.attention @= Some(Attention.SetAside)
        m2.actions @= Nil
      }), MessageAction(MaterialIcon.Reply, "Answer", () => ran = ran :+ "answer")))
      val t = transcript(Vector(m1, m2, m3))
      onScreen(t) {
        eventually(shown(t, "m2")) shouldBe true
        fx(Pointer.usedAt = -1.0e9)
        fx(t.focus())
        fx(t.list.current @= Some("m2"))
        def on(c: => Component): Boolean = Focus.focused().exists(_ eq c)
        def ignore = view(t, "m2").actionButtons(0)
        def answer = view(t, "m2").actionButtons(1)
        press(KeyCode.RIGHT)
        eventually(on(ignore)) shouldBe true
        press(KeyCode.RIGHT)
        eventually(on(answer)) shouldBe true
        // on past the last: the buttons beside it, for this message
        press(KeyCode.RIGHT)
        eventually(t.actions.exists(b => b.actionButtons.headOption.exists(x => on(x)))) shouldBe true
        fx(t.actions.get.message().map(_.id)) shouldBe Some("m2")
        press(KeyCode.ESCAPE)
        eventually(on(t.list)) shouldBe true
        // back, Up and Escape from them go back to the message
        press(KeyCode.RIGHT)
        eventually(on(ignore)) shouldBe true
        press(KeyCode.LEFT)
        eventually(on(t.list)) shouldBe true
        press(KeyCode.RIGHT)
        eventually(on(ignore)) shouldBe true
        press(KeyCode.UP)
        eventually(on(t.list)) shouldBe true
        fx(t.list.current()) shouldBe Some("m2")
        // Enter runs one; one that takes itself away leaves the focus on the message
        press(KeyCode.RIGHT)
        eventually(on(ignore)) shouldBe true
        press(KeyCode.ENTER)
        eventually(ran == List("ignore")) shouldBe true
        eventually(on(t.list)) shouldBe true
        fx(view(t, "m2").actionButtons) shouldBe Vector.empty
        fx(t.list.current()) shouldBe Some("m2")
      }
    }
  }
}
