package everyui.menu

import everyui.component.*
import everyui.event.{Pointer, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Words
import everyui.render.{Nodes, Platform}
import everyui.{FxSpec, ui}

/** A menu a finger opens that is too tall for its place goes up as a sheet from the foot of the screen: a grip to drag
  * it down by, the dimmed screen above it to tap, at least `Menu.tapRoom` of it left; a mouse's and the keys' drop as
  * ever. Back closes the menu on top, sheet or not, before the dialog under it. */
class MenuSheetSpec extends FxSpec {
  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  /** A button near the head of the screen to drop menus from, focused; all taken away after. */
  private def onScreen[T](test: Component => T): T = {
    val button = fx {
      ui.children.clear()
      val b = new Component(Nodes.container())
      b.focusable @= true
      b.size.width @= 80.0
      b.size.height @= 30.0
      b.position.left @= 200.0
      b.position.top @= 20.0
      ui.children += b
      b.focus()
      b
    }
    try test(button) finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
      Pointer.finger = false
      Pointer.usedAt = -1.0e9
    }
  }

  /** The press that opens the menu: a finger's, or a mouse's. */
  private def pressed(finger: Boolean): Unit = {
    Pointer.usedAt = Platform.ticks()
    Pointer.finger = finger
  }

  private def entries(n: Int): List[MenuItem] =
    (1 to n).toList.map(i => MenuItem("Entry " + i.toString, () => (), detail = "What entry " + i.toString + " is"))

  private def grip(menu: Menu): Container = fx(menu.children().collect { case c: Container => c }
    .find(_.semantics().role == "button").get)

  private def drag(c: Component, by: Double): Unit = {
    val (x, y) = fx((c.absoluteLeft + 40.0, c.absoluteTop + 10.0))
    fx(c.event.pointer.down @= PointerEvent(PointerKind.Down, x, y, finger = true))
    fx(c.event.pointer.move @= PointerEvent(PointerKind.Move, x, y + by / 2.0, finger = true))
    fx(c.event.pointer.move @= PointerEvent(PointerKind.Move, x, y + by, finger = true))
    fx(c.event.pointer.up @= PointerEvent(PointerKind.Up, x, y + by, finger = true))
  }

  "A finger's menu" should {
    "go up as a sheet where it is too tall, leaving room above it to tap" in onScreen { button =>
      val menu = fx {
        pressed(finger = true)
        Menu.below(button, entries(60))
      }
      fx(menu.isSheet) shouldBe true
      val (top, height, width, screen, across) = fx((menu.position.top(), menu.size.height(), menu.size.width(),
        ui.size.height(), ui.size.width()))
      top + height shouldBe (screen +- 0.5)
      top should be >= Menu.tapRoom()
      width shouldBe (math.min(across, Menu.sheetWidest()) +- 0.5)
      // over a dimmed screen
      fx(menu.parent().map(_.backgroundColor()).exists(_.alpha > 0.0)) shouldBe true
      // the focus in it, on its first entry, as a dropdown's is
      fx(Focus.focused().collect { case r: MenuRow => r.item.label }) shouldBe Some("Entry 1")
    }
    "drop as ever where it fits, and for a mouse or the keys however tall" in onScreen { button =>
      val short = fx {
        pressed(finger = true)
        Menu.below(button, entries(3))
      }
      fx(short.isSheet) shouldBe false
      fx(short.position.top()) shouldBe (54.0 +- 0.5)
      fx(Modal.close())
      val mouse = fx {
        pressed(finger = false)
        Menu.below(button, entries(60))
      }
      fx(mouse.isSheet) shouldBe false
      fx(Modal.close())
      // the keys, on a desktop with no touch screen
      val keys = fx {
        Pointer.usedAt = -1.0e9
        Menu.below(button, entries(60))
      }
      fx(keys.isSheet) shouldBe false
    }
    "close dragged down by its grip, and go back up after a short drag" in onScreen { button =>
      var dismissed = 0
      val menu = fx {
        pressed(finger = true)
        Menu.below(button, entries(60), dismissed = () => dismissed += 1)
      }
      val handle = grip(menu)
      val at = fx(menu.position.top())
      drag(handle, 30.0)
      fx(Modal.showing) shouldBe true
      fx(menu.position.top()) shouldBe (at +- 0.5)
      drag(handle, 400.0)
      fx(Modal.showing) shouldBe false
      dismissed shouldBe 1
      fx(Focus.focused()) shouldBe Some(button)
    }
    "close on a tap on the dimmed screen above it, or its grip activated" in onScreen { button =>
      var dismissed = 0
      val menu = fx {
        pressed(finger = true)
        Menu.below(button, entries(60), dismissed = () => dismissed += 1)
      }
      fx(menu.parent().get.event.click @= PointerEvent(PointerKind.Click, 10.0, 10.0))
      fx(Modal.showing) shouldBe false
      dismissed shouldBe 1
      val again = fx {
        pressed(finger = true)
        Menu.below(button, entries(60), dismissed = () => dismissed += 1)
      }
      val handle = grip(again)
      fx(handle.semantics().label) shouldBe Words.close
      fx(handle.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
      fx(Modal.showing) shouldBe false
      dismissed shouldBe 2
    }
  }

  "Back" should {
    "close the menu on top, a sheet or a dropdown, before the dialog it is over" in onScreen { button =>
      val dialog = fx {
        val panel = new Container(Nodes.container())
        panel.size.width @= 300.0
        panel.size.height @= 200.0
        val inside = new Component(Nodes.container())
        inside.focusable @= true
        panel.children += inside
        Modal.show(panel, inside)
        panel
      }
      fx {
        pressed(finger = true)
        Menu.below(button, entries(60))
      }
      fx(Modal.top.map(_.kind)) shouldBe Some("Menu")
      press("Back")
      fx(Modal.top) shouldBe Some(dialog)
      fx {
        pressed(finger = false)
        Menu.below(button, entries(5))
      }
      press("Back")
      fx(Modal.top) shouldBe Some(dialog)
      press("Back")
      fx(Modal.showing) shouldBe false
    }
    "close the dialog on top with the focus left on what it covers, before what has the focus or the screen's hooks" in
      onScreen { _ =>
        var kept = 0
        var hooked = 0
        // a player under the dialog that has taken the focus back once it is up, keeping Back, and a screen's hook on Back
        val player = fx {
          val c = new Component(Nodes.container()) {
            override def keepsKey(key: everyui.event.Key): Boolean = {
              if (key == everyui.event.Key.Back) kept += 1
              key == everyui.event.Key.Back
            }
          }
          c.focusable @= true
          ui.children += c
          c
        }
        val unhook = fx(Focus.onKey(k => if (k == everyui.event.Key.Back) { hooked += 1; true } else false))
        try {
          fx {
            pressed(finger = false)
            Menu.below(player, entries(5))
          }
          fx(Focus.focused @= Some(player))
          press("Back")
          fx(Modal.showing) shouldBe false
          kept shouldBe 0
          hooked shouldBe 0
          // with no dialog up, they hear it as before
          fx(Focus.focused @= Some(player))
          press("Back")
          kept should be > 0
        }
        finally fx(unhook())
      }
  }
}
