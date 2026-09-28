package everyui.component

import everyui.focus.Focus
import everyui.menu.{MenuItem, SplitButton}
import everyui.{FxSpec, ui}
import reactify.*

/** What is made, or changed, in a hidden panel has its size, there and once the panel shows: out of sight, not out of
  * the layout. (The web measures a hidden node by laying it out undrawn, RenderNode.reveal; app/HiddenDemo checks it in
  * a browser.) */
class HiddenSpec extends FxSpec {
  private def panel(): (Container, Button, Checkbox, TextView) = {
    val button = new SplitButton("Commit", () => (), List(MenuItem("Amend")))
    val box = new Checkbox("Amend", compact = true)
    box.position.left @= 200.0
    val words = new TextView
    words.content @= "Words"
    words.position.left @= 320.0
    val holder = new Container
    holder.children ++= List(button, box, words)
    (holder, button.main, box, words)
  }

  "A panel made hidden" should {
    "size what it holds as a panel in sight does" in {
      val (seen, seenButton, seenBox, seenWords) = fx(panel())
      val (hidden, hiddenButton, hiddenBox, hiddenWords) = fx(panel())
      fx {
        hidden.visible @= false
        ui.children.clear()
        Focus.focused @= None
        ui.children ++= List(seen, hidden)
      }
      try {
        fx {
          seenWords.content @= "Changed while hidden"
          hiddenWords.content @= "Changed while hidden"
        }
        fx(hiddenWords.size.width()) shouldBe fx(seenWords.size.width())
        fx(hidden.visible @= true)
        fx(seenButton.size.width()) should be > 40.0
        fx(hiddenButton.size.width()) shouldBe fx(seenButton.size.width())
        fx(hiddenBox.size.width()) shouldBe fx(seenBox.size.width())
        fx(hiddenWords.size.width()) shouldBe fx(seenWords.size.width())
        fx(hiddenBox.size.height()) shouldBe 24.0
      }
      finally fx {
        ui.children.clear()
        Focus.focused @= None
      }
    }
  }
}
