package everyui.focus

import everyui.component.Component
import everyui.event.Key
import everyui.keymap.{Chord, Keymap}
import everyui.render.{Host, Nodes}
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}

/** A mouse's back and forward buttons: keys named BrowserBack and BrowserForward, which a keymap's chord takes, the
  * back button Back where none does; never a press on what is under the pointer, nor an embed's. Real JavaFX mouse
  * events through the desktop backend's filter. */
class NavButtonsSpec extends FxSpec {
  private def card(): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c.size.width @= 200.0
    c.size.height @= 200.0
    c
  }

  /** `button` pressed, released and clicked on `on`, with the modifiers given. */
  private def click(on: Component, button: MouseButton, shift: Boolean = false, ctrl: Boolean = false,
                    alt: Boolean = false): Unit = fx {
    List(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED).foreach { kind =>
      javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, 50.0, 50.0, 50.0, 50.0, button, 1, shift, ctrl, alt,
        false, false, false, false, button == MouseButton.BACK && kind == MouseEvent.MOUSE_PRESSED,
        button == MouseButton.FORWARD && kind == MouseEvent.MOUSE_PRESSED, true, null))
    }
  }

  private def onScreen[T](test: Component => T): T = {
    val c = card()
    fx {
      ui.children.clear()
      ui.children += c
      c.focus()
    }
    try test(c) finally fx {
      Keymap.global.commands @= Nil
      Keymap.embeds @= Map.empty
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A mouse's back and forward buttons" should {
    "run the chords that name them, once a click, and never reach what is under the pointer" in onScreen { c =>
      var ran: List[String] = Nil
      var pressed = 0
      var keys: List[String] = Nil
      fx {
        Keymap.global.add("nav.back", "Navigate Back", Chord("BrowserBack"), Chord.parse("Primary+Alt+ArrowLeft"))({ ran = ran :+ "back" })
        Keymap.global.add("nav.forward", "Navigate Forward", Chord("BrowserForward"))({ ran = ran :+ "forward" })
        c.event.pointer.down.attach(_ => pressed += 1)
        c.event.pointer.up.attach(_ => pressed += 1)
        c.event.key.down.attach(e => keys = keys :+ e.name)
      }
      click(c, MouseButton.BACK)
      click(c, MouseButton.FORWARD)
      click(c, MouseButton.BACK)
      ran shouldBe List("back", "forward", "back")
      pressed shouldBe 0
      keys shouldBe Nil
      // the primary button is still a press
      click(c, MouseButton.PRIMARY)
      pressed shouldBe 2
    }
    "match a chord's modifiers, held with the button" in onScreen { c =>
      var ran: List[String] = Nil
      fx {
        Keymap.global.add("plain", "Plain", Chord("BrowserBack"))({ ran = ran :+ "plain" })
        Keymap.global.add("shifted", "Shifted", Chord("BrowserBack", shift = true))({ ran = ran :+ "shifted" })
      }
      click(c, MouseButton.BACK, shift = true)
      click(c, MouseButton.BACK)
      click(c, MouseButton.BACK, ctrl = true)
      ran shouldBe List("shifted", "plain")
    }
    "be Back where no chord takes the back button, and the forward button a key of its own" in onScreen { c =>
      var backs: List[Key] = Nil
      var keys: List[(Key, String)] = Nil
      val off = fx(Focus.onBack(k => { backs = backs :+ k; true }))
      fx(c.event.key.down.attach(e => keys = keys :+ (e.key -> e.name)))
      try {
        click(c, MouseButton.BACK)
        click(c, MouseButton.FORWARD)
      } finally fx(off())
      keys shouldBe List(Key.Back -> "BrowserBack", Key.Other -> "BrowserForward")
      backs shouldBe List(Key.Back)
    }
    "be the app's over an embed, whatever the embed keeps" in onScreen { c =>
      var ran = 0
      fx {
        c.node.fx.getProperties.put(Host.embedKey, "editor")
        Keymap.embed("editor")
        Keymap.global.add("nav.back", "Navigate Back", Chord("BrowserBack"))(ran += 1)
      }
      click(c, MouseButton.BACK)
      ran shouldBe 1
    }
  }
}
