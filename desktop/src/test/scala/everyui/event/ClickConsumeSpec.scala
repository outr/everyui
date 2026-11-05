package everyui.event

import everyui.component.{Component, Container, IconButton, TextView}
import everyui.font.MaterialIcon
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}

/** A click a button takes is not heard by the card it sits in, however long the button's work takes, while the next
  * click, even at the same pixel, is a click of its own. */
class ClickConsumeSpec extends FxSpec {
  // one click of the window's, as JavaFX sends it to the node under it and up through what holds it
  private def click(on: Component): Unit = fx {
    val x = on.absoluteLeft + on.size.width() / 2.0
    val y = on.absoluteTop + on.size.height() / 2.0
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y, MouseButton.PRIMARY,
      1, false, false, false, false, true, false, false, false, false, true, null))
  }

  "A card holding a button" should {
    "not open as the button is clicked, though the button's work outlasts a moment, and open as it is clicked" in {
      var opened = 0
      var archived = 0
      val (card, archive) = fx {
        ui.children.clear()
        val card = new Container
        card.size.width @= 360.0
        card.size.height @= 64.0
        card.focusable @= true
        card.clickFocus @= true
        card.children += TextView("Lunch on Friday?")
        card.event.click.attach(_ => opened += 1)
        val archive = new IconButton(MaterialIcon.Archive, "Archive", () => {
          archived += 1
          // work that takes a while, as a list archived and drawn again does
          Thread.sleep(Pointer.hold.toLong * 2L)
        })
        archive.position.left @= 300.0
        archive.position.top @= 16.0
        card.children += archive
        ui.children += card
        (card, archive)
      }
      click(archive)
      click(archive)
      (archived, opened) shouldBe (2, 0)
      click(card.children().head)
      (archived, opened) shouldBe (2, 1)
      fx(ui.children.clear())
    }
  }
}
