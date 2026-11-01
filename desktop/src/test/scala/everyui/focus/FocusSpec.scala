package everyui.focus

import everyui.component.{Component, Container, Modal}
import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.render.Nodes
import everyui.{FxSpec, ui}

/** The focus taken off the screen with what it was on: it goes to what took its place, never to nothing, and Back
  * still answers wherever it was. Run on the JavaFX thread, against the desktop backend's real nodes. */
class FocusSpec extends FxSpec {
  // the focus as the keys move it, which these are about
  override protected def keyed: Boolean = true

  private def card(): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c.size.width @= 100.0
    c.size.height @= 100.0
    c
  }

  private def row(cards: Component*): Container = {
    val r = new Container
    r.layout @= Some(HorizontalLayout(10.0))
    r.children ++= cards
    r
  }

  /** A screen's worth under `ui`, in a screen's container, taken off again after the test. */
  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      Focus.scope @= None
      val screen = new Container
      screen.children ++= parts
      ui.children += screen
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "The focus on a component taken off the screen" should {
    "go to the part that took its place" in {
      val cards = Vector.fill(4)(card())
      onScreen(row(cards*)) {
        fx {
          cards(1).focus()
          cards(1).parent().get.asInstanceOf[Container].children -= cards(1)
        }
        fx(Focus.focused()) shouldBe Some(cards(2))
      }
    }
    "go to the one before it where it was the last" in {
      val cards = Vector.fill(3)(card())
      onScreen(row(cards*)) {
        fx {
          cards(2).focus()
          cards(2).parent().get.asInstanceOf[Container].children -= cards(2)
        }
        fx(Focus.focused()) shouldBe Some(cards(1))
      }
    }
    "follow a row edited in one go, as a rail read again is" in {
      val cards = Vector.fill(4)(card())
      val arrived = card()
      val r = row(cards*)
      onScreen(r) {
        fx {
          cards(1).focus()
          // the focused card gone and another arriving at the end, the rest kept, all at once
          r.children @= Vector(cards(0), cards(2), cards(3), arrived)
          cards(1).dispose()
        }
        fx(Focus.focused()) shouldBe Some(cards(2))
      }
    }
    "go to the row that took the place of a row taken away with it, at its entry" in {
      val first = Vector.fill(3)(card())
      val second = Vector.fill(3)(card())
      val third = Vector.fill(3)(card())
      val rows = new Container
      rows.layout @= Some(VerticalLayout(10.0))
      val a = row(first*)
      val b = row(second*)
      val c = row(third*)
      rows.children ++= List(a, b, c)
      onScreen(rows) {
        fx {
          // where the third row was left, it is entered again
          third(2).focus()
          second(1).focus()
          rows.children -= b
          b.dispose()
        }
        fx(Focus.focused()) shouldBe Some(third(2))
      }
    }
    "go to where the screen starts, by the next key at the latest, when nothing is left in its place" in {
      val cards = Vector.fill(2)(card())
      val elsewhere = card()
      val r = row(cards*)
      onScreen(r, row(elsewhere)) {
        fx {
          Focus.home @= (() => Some(elsewhere))
          cards(0).focus()
          r.children.clear()
        }
        // nothing took its place: the next key starts from where the screen does (a remote's watch goes sooner)
        fx {
          Focus.key("Shift", true)
          Focus.key("Shift", false)
        }
        fx(Focus.focused()) shouldBe Some(elsewhere)
        fx(Focus.home @= (() => None))
      }
    }
    "be given back to what took the place of what a dialog covered" in {
      val cards = Vector.fill(4)(card())
      val r = row(cards*)
      val button = card()
      onScreen(r) {
        fx {
          cards(1).focus()
          val panel = row(button)
          Modal.over(panel, button)
        }
        fx(Focus.focused()) shouldBe Some(button)
        // the row read again under the dialog, the card it covered gone
        fx {
          r.children -= cards(1)
          cards(1).dispose()
        }
        fx(Focus.focused()) shouldBe Some(button)
        fx(Modal.close())
        fx(Focus.focused()) shouldBe Some(cards(2))
      }
    }
  }

  "A dialog waiting under a cover" should {
    "take no focus and no Back until it is uncovered" in {
      val cards = Vector.fill(2)(card())
      val button = card()
      var heard = 0
      onScreen(row(cards*)) {
        val release = fx(Focus.onBack { _ =>
          heard += 1
          true
        })
        fx {
          cards(0).focus()
          Modal.covered @= true
          Modal.over(row(button), button)
          // what asks for it anyway, as a question putting the focus on the answer it suggests does
          button.focus()
        }
        fx(Focus.focused()) should not be Some(button)
        fx(Modal.inView) shouldBe false
        fx {
          Focus.key("Back", true)
          Focus.key("Back", false)
        }
        heard shouldBe 1
        fx(Modal.covered @= false)
        fx(Focus.focused()) shouldBe Some(button)
        fx(release())
      }
    }
  }

  "Back" should {
    "reach the screen whatever holds the focus, a component taken off it included" in {
      val cards = Vector.fill(3)(card())
      val r = row(cards*)
      var heard = 0
      onScreen(r) {
        val release = fx(Focus.onBack { _ =>
          heard += 1
          true
        })
        fx {
          cards(0).focus()
          // gone with nothing yet to move the focus on: the key itself does
          r.children -= cards(0)
          Focus.key("Back", true)
          Focus.key("Back", false)
        }
        heard shouldBe 1
        fx(Focus.focused()) shouldBe Some(cards(1))
        fx(release())
      }
    }
    "not be held by a dialog something else took off the screen" in {
      val cards = Vector.fill(2)(card())
      val button = card()
      var heard = 0
      var closed = false
      onScreen(row(cards*)) {
        val release = fx(Focus.onBack { _ =>
          heard += 1
          true
        })
        fx {
          cards(0).focus()
          Modal.over(row(button), button, () => closed = true)
          // a screen shown in the dialog's place puts nothing but itself in the host
          ui.children @= ui.children().filterNot(c => Focus.within(button, c))
        }
        fx {
          Focus.key("Back", true)
          Focus.key("Back", false)
        }
        heard shouldBe 1
        closed shouldBe true
        fx(Modal.showing) shouldBe false
        // and a focus asked for outside it is no longer refused for it
        fx {
          cards(1).focus()
          Focus.focused()
        } shouldBe Some(cards(1))
        fx(release())
      }
    }
  }
}
