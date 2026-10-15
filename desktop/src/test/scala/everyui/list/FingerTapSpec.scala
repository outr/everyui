package everyui.list

import everyui.component.{Component, Modal}
import everyui.event.Pointer
import everyui.focus.Focus
import everyui.tooltip.Tooltip
import everyui.{FxSpec, ui}
import javafx.scene.input.{ContextMenuEvent, MouseButton, MouseEvent}
import reactify.*

/** A finger and a mouse on the same list, on a desktop with a touch screen (a touch laptop), told apart by each press,
  * not the device: a finger's tap activates the row, as on a phone, while a mouse's click chooses it and its double
  * click activates it, as on a desktop; a finger held on a row shows its tip where that opens no menu, the secondary
  * button there does not. An app's own `clickActivates` and `holdTips` say for every click and hold. The finger comes to
  * JavaFX as the mouse, made from the touch (synthesized), as a touch screen's does. */
class FingerTapSpec extends FxSpec {
  private def onFx(f: => Unit): Unit = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double, finger: Boolean,
                    button: MouseButton = MouseButton.PRIMARY, clicks: Int = 1): Unit = onFx {
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, button, clicks, false, false, false,
      false, button == MouseButton.PRIMARY, false, button == MouseButton.SECONDARY, finger, false,
      kind == MouseEvent.MOUSE_CLICKED, null))
  }

  // a press, its release and its click, as one turn of the FX thread
  private def click(on: Component, x: Double, y: Double, finger: Boolean, clicks: Int = 1): Unit = onFx {
    mouse(on, MouseEvent.MOUSE_PRESSED, x, y, finger, clicks = clicks)
    mouse(on, MouseEvent.MOUSE_RELEASED, x, y, finger, clicks = clicks)
    mouse(on, MouseEvent.MOUSE_CLICKED, x, y, finger, clicks = clicks)
  }

  // held on the list at `y` until it asks for a menu, by a finger, or by the mouse's secondary button
  private def hold(on: Component, x: Double, y: Double, finger: Boolean): Unit = fx {
    Pointer.consumed = None
    val button = if (finger) MouseButton.PRIMARY else MouseButton.SECONDARY
    mouse(on, MouseEvent.MOUSE_PRESSED, x, y, finger, button)
    javafx.event.Event.fireEvent(on.node.fx, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, x, y, x, y,
      false, null))
    mouse(on, MouseEvent.MOUSE_RELEASED, x, y, finger, button)
  }

  private def made(): VirtualList[Int] = fx {
    val list = VirtualList.text[Int](Var((0 until 40).toVector), i => i.toString, i => "File" + i.toString,
      i => Decoration(badge = "folder", tooltip = Some("src/File" + i.toString + ".scala")))
    list.size.width @= 320.0
    list.size.height @= 280.0
    list
  }

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      Tooltip.hide()
      ui.children.clear()
      Focus.focused @= None
      ui.children += c
    }
    try test finally fx {
      Modal.close()
      Tooltip.hide()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  // the middle of row `i`
  private def at(list: VirtualList[Int], i: Int): Double = fx(list.absoluteTop + list.top(i) - list.offset() + 10.0)

  private def activations(list: VirtualList[Int]): () => List[Int] = {
    var seen: List[Int] = Nil
    fx(list.activated.attach(i => seen = seen :+ i))
    () => fx(seen)
  }

  "A list on a touch laptop" should {
    "activate the row a finger taps, and only choose the one a mouse clicks" in {
      val list = made()
      val activated = activations(list)
      onScreen(list) {
        click(list, 40.0, at(list, 3), finger = true)
        activated() shouldBe List(3)
        fx(list.current()) shouldBe Some("3")
        click(list, 40.0, at(list, 5), finger = false)
        activated() shouldBe List(3)
        fx(list.current()) shouldBe Some("5")
        fx(list.selection.contains("5")) shouldBe true
        // and a finger after the mouse, on the same list, is still a tap
        click(list, 40.0, at(list, 7), finger = true)
        activated() shouldBe List(3, 7)
      }
    }
    "activate the row a mouse clicks twice" in {
      val list = made()
      val activated = activations(list)
      onScreen(list) {
        click(list, 40.0, at(list, 4), finger = false)
        click(list, 40.0, at(list, 4), finger = false, clicks = 2)
        activated() shouldBe List(4)
      }
    }
    "do as the app says where it says, for a finger and a mouse alike" in {
      val list = made()
      val activated = activations(list)
      onScreen(list) {
        fx(list.clickActivates @= Some(true))
        click(list, 40.0, at(list, 2), finger = false)
        activated() shouldBe List(2)
        fx(list.clickActivates @= Some(false))
        click(list, 40.0, at(list, 6), finger = true)
        activated() shouldBe List(2)
        fx(list.current()) shouldBe Some("6")
      }
    }
    "show a row's tip for a finger held on it, and not for the secondary button" in {
      val list = made()
      onScreen(list) {
        hold(list, 40.0, at(list, 4), finger = false)
        fx(Tooltip.shownFor) shouldBe None
        hold(list, 40.0, at(list, 5), finger = true)
        fx(Tooltip.shownFor.exists(_ eq list.rowAt(5).get)) shouldBe true
        fx(Tooltip.hide())
        // the app's say: none for a finger, or one for the mouse
        fx(list.holdTips @= Some(false))
        hold(list, 40.0, at(list, 6), finger = true)
        fx(Tooltip.shownFor) shouldBe None
        fx(list.holdTips @= Some(true))
        hold(list, 40.0, at(list, 7), finger = false)
        fx(Tooltip.shownFor.exists(_ eq list.rowAt(7).get)) shouldBe true
      }
    }
  }
}
