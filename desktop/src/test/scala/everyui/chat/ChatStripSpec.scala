package everyui.chat

import everyui.component.{Component, Container}
import everyui.{FxSpec, ui}
import reactify.*

/** A strip of the app's just above the composer: the full width, the transcript giving up what it takes, nothing
  * while it is empty or hidden, growing upward as it unfolds, and still on the composer as the view grows shorter (the
  * keyboard coming up). */
class ChatStripSpec extends FxSpec {
  private def box(height: Double): Container = {
    val c = new Container
    c.size.height @= height
    c
  }

  "A chat's strip" should {
    "sit between the transcript and the composer, and take no room while there is none or it is hidden" in {
      val (chat, composer, strip) = fx {
        ui.children.clear()
        val composer = box(56.0)
        val chat = new ChatView(Var(Vector.empty), composer = Some(composer))
        chat.size.width @= 390.0
        chat.size.height @= 800.0
        ui.children += chat
        (chat, composer, box(32.0))
      }
      try {
        val full = fx(chat.transcript.size.height())
        fx(chat.stripSlot @= Some(strip))
        fx(chat.transcript.size.height()) shouldBe full - 32.0
        fx(strip.size.width()) shouldBe 390.0
        fx(strip.position.top()) shouldBe fx(chat.transcript.size.height())
        fx(strip.position.top() + strip.size.height()) should be <= fx(composer.position.top())
        // in the order the keys and a screen reader take: after the transcript, before the composer
        fx(chat.children().indexWhere(_ eq strip)) shouldBe fx(chat.children().indexWhere(_ eq chat.transcript)) + 1
        // unfolding upward
        fx(strip.size.height @= 200.0)
        fx(chat.transcript.size.height()) shouldBe full - 200.0
        fx(strip.position.top() + strip.size.height()) should be <= fx(composer.position.top())
        // the keyboard up: the view shorter, the strip still on the composer
        fx(chat.size.height @= 500.0)
        fx(composer.position.top() - (strip.position.top() + strip.size.height())) shouldBe
          fx(chat.status.size.height())
        fx(strip.visible @= false)
        fx(chat.transcript.size.height()) shouldBe fx(500.0 - chat.status.size.height() - 56.0)
        fx(chat.stripSlot @= None)
        fx(chat.children().exists(_ eq strip)) shouldBe false
      } finally fx(ui.children.clear())
    }
  }
}
