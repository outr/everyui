package everyui.chat

import everyui.component.TextView
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import reactify.*

/** How much of everybody else's bubble is drawn (`MessageView.othersOpacity`): all of it by default, the control colour
  * exactly; less once it is set, the bubbles already shown following it and one's own as it was; and all of it again
  * once it is set back. */
class BubbleOpacitySpec extends FxSpec {
  private val ada = Participant("ada", "Ada")

  private def message(self: Boolean): MessageView = fx {
    val m = new ChatEntry.Message(if (self) "m-self" else "m-ada", ada, self)
    m.text @= "What's the actual goal here?"
    val v = new MessageView(m, leads = true, 600.0, Val(0L), (s, _) => new TextView {
      content := s()
    })
    ui.children += v
    v
  }

  override def afterAll(): Unit = {
    fx {
      MessageView.othersOpacity @= 1.0
      ui.children.clear()
    }
    super.afterAll()
  }

  "MessageView.othersOpacity" should {
    "draw everybody else's bubble in the control colour by default" in {
      val other = message(self = false)
      fx(other.bubble.backgroundColor()) should be(fx(Theme.control()))
    }
    "draw less of everybody else's bubble once set, those shown following it, one's own as it was" in {
      val other = message(self = false)
      val own = message(self = true)
      val before = fx(own.bubble.backgroundColor())
      fx(MessageView.othersOpacity @= 0.8)
      val c = fx(Theme.control())
      val drawn = fx(other.bubble.backgroundColor())
      (drawn.redInt, drawn.greenInt, drawn.blueInt) should be((c.redInt, c.greenInt, c.blueInt))
      drawn.alpha should be(c.alpha * 0.8 +- 0.01)
      fx(own.bubble.backgroundColor()) should be(before)
      fx(MessageView.othersOpacity @= 1.0)
      fx(other.bubble.backgroundColor()) should be(c)
    }
  }
}
