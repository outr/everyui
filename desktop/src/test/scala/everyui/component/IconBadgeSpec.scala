package everyui.component

import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}

/** An icon button's badge: a count in a pill or a dot over its top corner (the left, right to left), drawn past its
  * box, said after its name to a screen reader, and pressing the button where it is clicked. */
class IconBadgeSpec extends FxSpec {
  private def onScreen[T](b: IconButton)(test: => T): T = {
    fx {
      ui.children.clear()
      b.position.left @= 100.0
      b.position.top @= 100.0
      ui.children += b
    }
    try test finally fx {
      Direction.set(false)
      ui.children.clear()
    }
  }

  private def pill(b: IconButton): Container =
    fx(b.children().collectFirst { case c: Container => c }.get)

  "An icon button's badge" should {
    "hang a count over its corner, said after its name, and a dot for none" in {
      var pressed = 0
      val b = fx(new IconButton(MaterialIcon.Queue, "Queue", () => pressed += 1))
      onScreen(b) {
        fx(b.badge @= Some("3"))
        val p = pill(b)
        fx((p.visible(), p.size.width(), p.size.height(), p.position.top())) shouldBe (true, 16.0, 16.0, -4.0)
        fx(p.position.left()) shouldBe 22.0
        fx(b.clipsToBox()) shouldBe Some(false)
        fx(b.semantics().label) shouldBe "Queue, 3"
        fx(b.badge @= Some(""))
        fx((p.size.width(), p.size.height())) shouldBe (8.0, 8.0)
        fx(b.semantics().label) shouldBe "Queue"
        fx(b.badge @= None)
        fx(p.visible()) shouldBe false
      }
    }
    "press the button where the badge is clicked" in {
      var pressed = 0
      val b = fx(new IconButton(MaterialIcon.Notifications, "Alerts", () => pressed += 1))
      onScreen(b) {
        fx(b.badge @= Some("9+"))
        val p = pill(b)
        fx(javafx.event.Event.fireEvent(p.node.fx, new MouseEvent(MouseEvent.MOUSE_CLICKED, 4.0, 4.0, 0.0, 0.0,
          MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, true, null)))
        pressed shouldBe 1
      }
    }
    "hang over the top left corner right to left" in {
      val b = fx(new IconButton(MaterialIcon.Queue, "Queue"))
      onScreen(b) {
        fx {
          Direction.set(true)
          b.badge @= Some("3")
        }
        val p = pill(b)
        fx(p.position.left()) shouldBe -6.0
      }
    }
  }
}
