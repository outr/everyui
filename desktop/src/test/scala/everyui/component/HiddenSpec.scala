package everyui.component

import everyui.component.types.SizeProperty
import everyui.focus.Focus
import everyui.kit.Spinner
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

  "A component made hidden off the screen" should {
    "have the size it was given as it is shown, though it is measured again while hidden" in {
      val (spinner, holder) = fx {
        val s = new Spinner(18.0)
        s.visible @= false
        val h = new Container
        h.children += s
        (s, h)
      }
      fx {
        ui.children.clear()
        Focus.focused @= None
        ui.children += holder
      }
      try {
        fx(spinner.measure.trigger())
        fx(spinner.visible @= true)
        fx((spinner.size.width(), spinner.size.height())) shouldBe (18.0, 18.0)
        fx((spinner.node.getWidth(), spinner.node.getHeight())) shouldBe (18.0, 18.0)
      }
      finally fx {
        ui.children.clear()
        Focus.focused @= None
      }
    }
  }

  "A size given the value it measured" should {
    "keep it, and stop following the measure" in {
      val measured = Var(18.0)
      var written: List[Double] = Nil
      val size = new SizeProperty(measured, d => written = written :+ d)
      fx(size @= 18.0)
      written shouldBe List(18.0)
      size.explicit shouldBe true
      // measured again hidden, a web node read 0 by 0
      fx(measured @= 0.0)
      size() shouldBe 18.0
      written shouldBe List(18.0)
    }
  }
}
