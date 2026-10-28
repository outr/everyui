package everyui.chat

import everyui.component.{Component, Container, Modal, TextView}
import everyui.emoji.{Emoji, EmojiPicker, EmojiWords}
import everyui.event.{Pointer, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** Reactions on a chat's messages (`Transcript.reactionsEnabled`): none shown, nor offered, where a transcript has them
  * off; on, a message's under its bubble as pills (an emoji and how many, the person's own lit, who in its tip), a
  * click on one heard as taking the person's away or adding it (`onReact`), the app's store the truth; a "+" after them
  * and Add reaction beside the message opening a picker, the emoji chosen heard as added (or taken away, where it is
  * theirs already). The picker finds emoji by name, keeps those chosen as used last, and puts them in a skin tone. */
class ReactionsSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
    }
  }

  private def made(): (ChatEntry.Message, Transcript) = fx {
    val m = ChatEntry.message("m1", ada, self = false, "Shipped the release")
    m.reactions @= List(ChatReaction("🎉", 3, mine = true, who = List("Ada", "Grace", "Me")), ChatReaction("👀", 1, mine = false))
    val t = new Transcript(Var(Vector[ChatEntry](m)))
    t.size.width @= 700.0
    t.size.height @= 500.0
    (m, t)
  }

  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))
  // (on the FX thread already)
  private def view(t: Transcript): MessageView = t.list.rowAt(t.list.indexOf("m1")).get.asInstanceOf[ChatRow].message.get
  // the pills under the bubble: their emoji and counts as drawn, and their tips
  private def pills(t: Transcript): Vector[Container] = fx {
    def all(c: Component): Vector[Component] = c +: c.childComponents.toVector.flatMap(all)
    all(view(t)).collect { case c: Container if c.semantics().role == "button" && c.size.height() == MessageView.pillHeight => c }
  }
  private def words(c: Component): Vector[String] = fx {
    def all(c: Component): Vector[Component] = c +: c.childComponents.toVector.flatMap(all)
    all(c).collect { case t: TextView => t.content() }
  }

  "A chat's reactions" should {
    "show nothing, nor offer to add one, where the transcript has them off" in {
      val (_, t) = made()
      onScreen(t) {
        settle(t)
        pills(t) shouldBe Vector.empty
      }
    }
    "show under the bubble, the person's own lit, a click heard as theirs taken away or added" in {
      val (m, t) = made()
      var heard: List[Reacted] = Nil
      fx {
        t.reactionsEnabled @= true
        t.onReact.attach(r => heard = heard :+ r)
      }
      onScreen(t) {
        settle(t)
        val shown = pills(t)
        // the two reactions and the "+"
        shown.size shouldBe 3
        words(shown(0)) shouldBe Vector("🎉", "3")
        fx(shown(0).tooltip()) shouldBe "Ada, Grace, Me"
        words(shown(1)) shouldBe Vector("👀", "1")
        fx(shown(2).tooltip()) shouldBe EmojiWords.addReaction
        // under the bubble
        fx(shown(0).absoluteTop > view(t).bubble.absoluteTop + view(t).bubble.size.height()) shouldBe true
        fx(shown(0).event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        fx(shown(1).event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        heard shouldBe List(Reacted("m1", "🎉", on = false), Reacted("m1", "👀", on = true))
        // the store answers: drawn again from it
        fx(m.reactions @= List(ChatReaction("👀", 2, mine = true)))
        settle(t)
        words(pills(t).head) shouldBe Vector("👀", "2")
      }
    }
    "open a picker from the + and from Add reaction, the emoji chosen heard" in {
      val (_, t) = made()
      var heard: List[Reacted] = Nil
      fx {
        t.reactionsEnabled @= true
        t.onReact.attach(r => heard = heard :+ r)
      }
      onScreen(t) {
        settle(t)
        val plus = pills(t).last
        fx(plus.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        val picker = fx(ui.children().flatMap(c => c.childComponents).collectFirst { case p: EmojiPicker => p })
        picker.nonEmpty shouldBe true
        fx(picker.get.choose(Emoji.all.find(_.emoji == "🚀").get))
        heard.last shouldBe Reacted("m1", "🚀", on = true)
        fx(Modal.showing) shouldBe false
        // and Add reaction among the buttons beside it
        fx {
          Pointer.consumed = None
          val i = t.list.indexOf("m1")
          t.list.event.pointer.heard(t.list.event.pointer.move, PointerKind.Move, t.list.absoluteLeft + 60.0,
            t.list.absoluteTop + t.list.top(i) - t.list.offset() + 20.0)
        }
        settle(t)
        fx(t.actions.get.actionButtons.map(_.tooltip())) should contain(EmojiWords.addReaction)
      }
    }
  }

  "An emoji picker" should {
    "find emoji by name, keep those chosen as used last, and put them in a skin tone" in {
      val picker = fx(new EmojiPicker)
      var chosen: List[String] = Nil
      fx(picker.chosen.attach(e => chosen = chosen :+ e))
      onScreen(picker) {
        fx(picker.query @= "tada")
        fx(picker.choose(Emoji.search("tada").head))
        chosen shouldBe List("🎉")
        fx(EmojiPicker.recent().head) shouldBe "🎉"
        fx(picker.tone @= 3)
        fx(picker.choose(Emoji.all.find(_.emoji == "👍").get))
        chosen.last shouldBe "👍🏽"
        fx(EmojiPicker.recent().head) shouldBe "👍"
        fx(picker.tone @= 0)
      }
    }
  }
}
