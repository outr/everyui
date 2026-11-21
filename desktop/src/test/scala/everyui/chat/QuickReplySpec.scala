package everyui.chat

import everyui.component.{Button, Component, TextView}
import everyui.event.{Pointer, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** A message's answers (`ChatEntry.Message.replies`) on the desktop's real nodes: chips under the bubble and over the
  * actions, grouped under their captions, pressed and dimmed until the replies change, one action to a press, the keys
  * along and out of them, what a screen reader hears, nothing made without them, and changed in place. */
class QuickReplySpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val first = "Shall I start with group 1?"
  private val second = "Should I email the team first?"

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }
  private def click(c: Component): Unit = fx(c.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))

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
    t.size.height @= 600.0
    t
  }

  private def row(t: Transcript, id: String): Option[ChatRow] =
    t.list.rows.find(_.item().exists(_.id == id)).collect { case r: ChatRow => r }
  private def view(t: Transcript, id: String): MessageView = row(t, id).flatMap(_.message).get
  private def shown(t: Transcript, id: String): Boolean = row(t, id).flatMap(_.message).nonEmpty
  private def chips(t: Transcript, id: String): Vector[QuickReplyChip] = fx(view(t, id).replyChips)
  private def labels(t: Transcript, id: String): Vector[String] = chips(t, id).map(_.reply.label)
  // the captions over the groups, in order
  private def captions(t: Transcript, id: String): Vector[String] = fx(view(t, id).quickReplies.toVector.flatMap { r =>
    r.childComponents.flatMap(_.childComponents.collect { case w: TextView => w.content() })
  })
  private def pressedOf(t: Transcript, id: String): Vector[(Boolean, Boolean)] =
    fx(view(t, id).replyChips.map(c => (c.pressed(), c.dimmed())))

  private def yesNo(log: Var[List[String]]): List[QuickReply] =
    List(QuickReply("Yes", () => log @= log() :+ "yes"), QuickReply("No", () => log @= log() :+ "no"))
  private def two(log: Var[List[String]]): List[QuickReply] = List(
    QuickReply("Yes", () => log @= log() :+ "1 yes", Some(first)),
    QuickReply("Yes", () => log @= log() :+ "2 yes", Some(second)),
    QuickReply("No", () => log @= log() :+ "1 no", Some(first)),
    QuickReply("Later", () => log @= log() :+ "2 later", Some(second)),
    QuickReply("Start with group 2", () => log @= log() :+ "1 group 2", Some(first)))

  "A message's quick replies" should {
    "be drawn as chips under the bubble and over the actions, and go once they are cleared" in {
      val log: Var[List[String]] = Var(Nil)
      val m = ChatEntry.message("m1", ada, self = false, "Shall I publish it?", replies = yesNo(log))
      fx(m.actions @= List(MessageAction(MaterialIcon.VisibilityOff, "Ignore", () => ())))
      val t = transcript(Vector(m))
      onScreen(t) {
        eventually(shown(t, "m1")) shouldBe true
        labels(t, "m1") shouldBe Vector("Yes", "No")
        val v = fx(view(t, "m1"))
        val block = fx(v.quickReplies.get)
        val (bubbleLeft, bubbleBottom) = fx((v.bubble.position.left(), v.bubble.position.top() + v.bubble.size.height()))
        // under the bubble at its start, the actions under them
        fx(block.position.top()) shouldBe (bubbleBottom + MessageView.repliesGap +- 0.5)
        fx(block.position.left()) shouldBe (bubbleLeft +- 0.5)
        val actions = fx(v.actionButtons.head.parent().get)
        fx(actions.position.top()) should be >= fx(block.position.top() + block.size.height())
        // pills in the accent, at least a mouse's height, the words a message's size, side by side on one line
        val Vector(yes, no) = chips(t, "m1")
        fx(yes.size.height()) should be >= QuickReplyChip.height
        fx(yes.radius()) shouldBe (fx(yes.size.height()) / 2.0 +- 0.01)
        fx(yes.backgroundColor()) shouldBe fx(Theme.accent().withAlpha(0.08))
        fx(yes.borderWidth()) shouldBe 1.0
        fx(yes.childComponents.collect { case w: TextView => w.font.size() }) shouldBe Vector(QuickReplyChip.points)
        fx(no.parent().get.position.top() + no.position.top()) shouldBe fx(yes.parent().get.position.top() +
          yes.position.top())
        fx(no.position.left()) shouldBe (fx(yes.position.left() + yes.size.width()) + QuickReplies.gap +- 0.5)

        // cleared: the row goes, and the view is as tall as with the actions alone
        val height = fx(v.size.height())
        val blockRoom = fx(block.size.height()) + MessageView.repliesGap + 2.0
        fx(m.replies @= Nil)
        fx(v.quickReplies) shouldBe None
        fx(v.replyChips) shouldBe Vector.empty
        fx(block.takenApart) shouldBe true
        fx(v.size.height()) shouldBe (height - blockRoom +- 0.5)
      }
    }

    "be a thumb's height where a finger was used, wrapped on a phone, never wider than it" in {
      val log: Var[List[String]] = Var(Nil)
      val many = (1 to 8).toList.map(i => QuickReply("Answer number " + i, () => ()))
      val m = ChatEntry.message("m1", ada, self = false, "Which?", replies = many)
      val t = fx {
        Pointer.finger = true
        val made = new Transcript(Var(Vector[ChatEntry](m)))
        made.size.width @= 390.0
        made.size.height @= 700.0
        made
      }
      try onScreen(t) {
        eventually(shown(t, "m1")) shouldBe true
        val all = chips(t, "m1")
        all.foreach(c => fx(c.size.height()) should be >= QuickReplyChip.tallHeight)
        // on several lines, none past the widest a bubble is
        fx(all.map(_.position.top()).distinct.size) should be > 1
        val widest = Transcript.widest(fx(row(t, "m1").get.inner))
        all.foreach(c => fx(c.position.left() + c.size.width()) should be <= widest + 0.5)
      } finally fx(Pointer.finger = false)
    }

    "show each group under its caption, in the order the groups first appear" in {
      val log: Var[List[String]] = Var(Nil)
      val m = ChatEntry.message("m1", ada, self = false, "Two questions.", replies = two(log))
      val t = transcript(Vector(m))
      onScreen(t) {
        eventually(shown(t, "m1")) shouldBe true
        labels(t, "m1") shouldBe Vector("Yes", "No", "Start with group 2", "Yes", "Later")
        captions(t, "m1") shouldBe Vector(first, second)
        // the second group under the first
        val all = chips(t, "m1")
        fx(all(3).parent().get.parent().get.position.top()) should be >
          fx(all(0).parent().get.parent().get.position.top() + all(0).parent().get.parent().get.size.height())
        // a caption too long for its line cut short, on one line
        val long = "A question so long that it cannot fit on one line of the widest bubble a desktop ever shows, " +
          "however small its words are drawn, and longer still, so that it must be cut short with an ellipsis"
        fx(m.replies @= List(QuickReply("Yes", () => (), Some(long))))
        val caption = fx(view(t, "m1").quickReplies.get.childComponents.head.childComponents.collectFirst {
          case w: TextView => w
        }.get)
        fx(caption.content()) shouldBe long
        eventually(caption.elided())
        fx(caption.size.width()) should be <= Transcript.widest(fx(row(t, "m1").get.inner)) + 0.5
        fx(caption.size.height()) should be < 24.0
        fx(caption.elided()) shouldBe true
      }
    }

    "show a press until the replies change, the rest of its group dimmed and still pressable, once per press" in {
      val log: Var[List[String]] = Var(Nil)
      val m = ChatEntry.message("m1", ada, self = false, "Two questions.", replies = two(log))
      val t = transcript(Vector(m))
      onScreen(t) {
        eventually(shown(t, "m1")) shouldBe true
        val v = fx(view(t, "m1"))
        val Vector(yes1, no1, group2, yes2, later2) = chips(t, "m1")
        // a double tap is one press (both in one turn, however busy the machine)
        fx {
          yes1.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0)
          yes1.event.click @= PointerEvent(PointerKind.Click, 1.0, 1.0)
        }
        log() shouldBe List("1 yes")
        pressedOf(t, "m1") shouldBe Vector((true, true), (false, true), (false, true), (false, false), (false, false))
        fx(yes1.backgroundColor()) shouldBe fx(Theme.accent())
        fx(no1.opacity()) shouldBe QuickReplyChip.faint
        fx(yes1.semantics().checked) shouldBe Some(true)
        // a sibling dimmed is still pressed, and takes the press; the other group is its own
        click(no1)
        click(later2)
        log() shouldBe List("1 yes", "1 no", "2 later")
        pressedOf(t, "m1") shouldBe Vector((false, true), (true, true), (false, true), (false, true), (true, true))
        // pressed again later, it runs again
        Thread.sleep((QuickReplies.debounce + 100.0).toLong)
        click(no1)
        log() shouldBe List("1 yes", "1 no", "2 later", "1 no")
        // kept on the message: a view made again (scrolled away and back) shows it pressed
        val again = fx(new MessageView(m, true, 800.0, Var(0L), MessageText.plain(_, _)))
        fx(again.replyChips.map(c => (c.pressed(), c.dimmed()))) shouldBe pressedOf(t, "m1")
        fx(again.dispose())
        // the replies changed: nothing pressed
        fx(m.replies @= two(log))
        (fx(view(t, "m1")) eq v) shouldBe true
        pressedOf(t, "m1") shouldBe Vector.fill(5)((false, false))
        fx(view(t, "m1").replyChips.head.semantics().checked) shouldBe Some(false)
      }
    }

    "be reached and pressed by the keys, Left and Right along them, Up and Down to the message and the actions" in {
      val log: Var[List[String]] = Var(Nil)
      val m = ChatEntry.message("m1", ada, self = false, "Two questions.", replies = two(log))
      fx(m.actions @= List(MessageAction(MaterialIcon.VisibilityOff, "Ignore", () => log @= log() :+ "ignore")))
      val other = ChatEntry.message("m0", ada, self = false, "Morning.")
      val t = transcript(Vector(other, m))
      onScreen(t) {
        eventually(shown(t, "m1")) shouldBe true
        fx(Pointer.usedAt = -1.0e9)
        fx(t.focus())
        fx(t.list.current @= Some("m1"))
        def on(c: => Component): Boolean = Focus.focused().exists(_ eq c)
        def chip(i: Int): Component = view(t, "m1").replyChips(i)
        def ignore: Component = view(t, "m1").actionButtons(0)
        // the forward arrow on the message: the first chip, then along them, group after group
        press(KeyCode.RIGHT)
        eventually(on(chip(0))) shouldBe true
        (1 to 4).foreach { i =>
          press(KeyCode.RIGHT)
          eventually(on(chip(i))) shouldBe true
        }
        // past the last, the actions; back from them, the last chip
        press(KeyCode.RIGHT)
        eventually(on(ignore)) shouldBe true
        press(KeyCode.LEFT)
        eventually(on(chip(4))) shouldBe true
        // Up: the first of the group over it, then the message; Down: the next group, then the actions
        press(KeyCode.UP)
        eventually(on(chip(0))) shouldBe true
        press(KeyCode.DOWN)
        eventually(on(chip(3))) shouldBe true
        press(KeyCode.DOWN)
        eventually(on(ignore)) shouldBe true
        press(KeyCode.UP)
        eventually(on(chip(3))) shouldBe true
        press(KeyCode.UP)
        eventually(on(chip(0))) shouldBe true
        press(KeyCode.UP)
        eventually(on(t.list)) shouldBe true
        fx(t.list.current()) shouldBe Some("m1")
        // Enter and Space press; Escape goes back to the message; Left from the first too
        press(KeyCode.RIGHT)
        eventually(on(chip(0))) shouldBe true
        press(KeyCode.ENTER)
        eventually(log() == List("1 yes")) shouldBe true
        press(KeyCode.RIGHT)
        eventually(on(chip(1))) shouldBe true
        press(KeyCode.SPACE)
        eventually(log() == List("1 yes", "1 no")) shouldBe true
        fx(view(t, "m1").replyChips(1).asInstanceOf[QuickReplyChip].pressed()) shouldBe true
        press(KeyCode.ESCAPE)
        eventually(on(t.list)) shouldBe true
        press(KeyCode.RIGHT)
        eventually(on(chip(0))) shouldBe true
        press(KeyCode.LEFT)
        eventually(on(t.list)) shouldBe true
        // replies cleared under the focus: back to the message, never nothing
        press(KeyCode.RIGHT)
        eventually(on(chip(0))) shouldBe true
        fx(m.replies @= Nil)
        eventually(on(t.list)) shouldBe true
      }
    }

    "be named for a screen reader, with their group's question" in {
      val log: Var[List[String]] = Var(Nil)
      val m = ChatEntry.message("m1", ada, self = false, "Shall I?", replies = yesNo(log) ++ two(log).take(1))
      val t = transcript(Vector(m))
      onScreen(t) {
        eventually(shown(t, "m1")) shouldBe true
        fx(view(t, "m1").replyChips.map(_.semantics().label)) shouldBe
          Vector("Reply: Yes", "Reply: No", "Reply to " + first + ": Yes")
        fx(view(t, "m1").replyChips.map(_.semantics().role).distinct) shouldBe Vector("button")
        QuickReply.said(QuickReply("Oui", () => ())) shouldBe "Reply: Oui"
      }
    }

    "make nothing, and listen for nothing, for a message without them" in {
      val before = fx {
        val v = new MessageView(ChatEntry.message("x", ada, self = false, "Morning."), true, 800.0, Var(0L),
          MessageText.plain(_, _))
        val n = v.childComponents.size
        v.dispose()
        n
      }
      val m = ChatEntry.message("m1", ada, self = false, "Morning.")
      val t = transcript(Vector(m))
      onScreen(t) {
        eventually(shown(t, "m1")) shouldBe true
        fx(view(t, "m1").childComponents.size) shouldBe before
        fx(view(t, "m1").quickReplies) shouldBe None
        fx(view(t, "m1").replyChips) shouldBe Vector.empty
        m.repliesKept shouldBe false
        m.pressedReplies shouldBe None
        // an empty list given: nothing made either
        val n = ChatEntry.message("m2", ada, self = false, "Hi.", replies = Nil)
        n.repliesKept shouldBe false
      }
    }

    "change in place: the message's view kept, its chips alone made again" in {
      val log: Var[List[String]] = Var(Nil)
      val m = ChatEntry.message("m1", ada, self = false, "Shall I?")
      val t = transcript(Vector(m))
      onScreen(t) {
        eventually(shown(t, "m1")) shouldBe true
        val v = fx(view(t, "m1"))
        val bubble = fx(v.bubble)
        val height = fx(v.size.height())
        // set later: made, the view kept
        fx(m.replies @= yesNo(log))
        (fx(view(t, "m1")) eq v) shouldBe true
        labels(t, "m1") shouldBe Vector("Yes", "No")
        fx(v.size.height()) should be > height
        val block = fx(v.quickReplies.get)
        fx(m.replies @= two(log))
        (fx(view(t, "m1")) eq v) shouldBe true
        (fx(v.bubble) eq bubble) shouldBe true
        (fx(v.quickReplies.get) eq block) shouldBe false
        fx(block.takenApart) shouldBe true
        labels(t, "m1") shouldBe Vector("Yes", "No", "Start with group 2", "Yes", "Later")
        // the row as tall as the view
        eventually(math.abs(row(t, "m1").get.size.height() - v.size.height()) < 0.5)
        fx(row(t, "m1").get.size.height()) shouldBe (fx(v.size.height()) +- 0.5)
        fx(m.replies @= Nil)
        eventually(math.abs(v.size.height() - height) < 0.5)
        fx(v.size.height()) shouldBe (height +- 0.5)
      }
    }
  }
}
