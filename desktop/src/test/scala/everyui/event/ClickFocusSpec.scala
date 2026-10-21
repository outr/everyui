package everyui.event

import everyui.component.*
import everyui.focus.Focus
import everyui.{FxSpec, ui}

/** A click focuses the innermost focusable component it lands on: the list a field sits in does not take the focus from
  * the field, whether the click focused the field or the press did as it went down, and still takes it when clicked
  * anywhere else. */
class ClickFocusSpec extends FxSpec {
  // a field or a list: what a click focuses where a pointer is used
  private class Box extends Container {
    focusable @= true
    clickFocus @= true
  }

  /** One click climbing from `inner` out through `outer`, as every backend delivers it. */
  private def climb(inner: Component, outer: Component, x: Double): Unit = fx {
    val event = PointerEvent(PointerKind.Click, x, 5.0)
    inner.event.click @= event
    outer.event.click @= event
  }

  private def onScreen[T](parts: Component*)(test: => T): T = {
    FocusMoves.watch()
    fx {
      ui.children.clear()
      Focus.focused @= None
      ui.children ++= parts
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A click on a field in a list" should {
    "focus the field, not the list" in {
      val (list, field) = fx {
        val list = new Box
        val field = new Box
        list.children += field
        (list, field)
      }
      onScreen(list) {
        climb(field, list, 11.0)
        Focus.focused() shouldBe Some(field)
      }
    }
    "leave the focus the press gave the field" in {
      val (list, field) = fx {
        val list = new Box
        val field = new Box
        list.children += field
        (list, field)
      }
      onScreen(list) {
        // a native field focuses itself as the press goes down, and has no click of its own here
        fx(Focus.focused @= Some(field))
        fx(list.event.click @= PointerEvent(PointerKind.Click, 12.0, 5.0))
        Focus.focused() shouldBe Some(field)
      }
    }
  }

  "A click on the list itself" should {
    "focus the list" in {
      val (list, other) = fx((new Box, new Box))
      onScreen(list, other) {
        fx(Focus.focused @= Some(other))
        fx(list.event.click @= PointerEvent(PointerKind.Click, 13.0, 5.0))
        Focus.focused() shouldBe Some(list)
      }
    }
    "take the focus from a field in it that was focused before the press" in {
      val (list, field) = fx {
        val list = new Box
        val field = new Box
        list.children += field
        (list, field)
      }
      onScreen(list) {
        fx(Focus.focused @= Some(field))
        Thread.sleep((FocusMoves.press + 100.0).toLong)
        fx(list.event.click @= PointerEvent(PointerKind.Click, 14.0, 5.0))
        Focus.focused() shouldBe Some(list)
      }
    }
  }
}
