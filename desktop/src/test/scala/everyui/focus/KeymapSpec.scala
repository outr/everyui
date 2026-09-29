package everyui.focus

import everyui.component.{Component, Container}
import everyui.event.{Keys, KeyEvent as UIKeyEvent}
import everyui.keymap.{Chord, Keymap}
import everyui.layout.HorizontalLayout
import everyui.render.{Host, Nodes}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import javafx.scene.layout.Pane

/** Chords, the keymaps they run in, Shift+Tab, and keys nobody takes left to the platform: real JavaFX key events
  * through the desktop backend's filter, on the JavaFX thread. */
class KeymapSpec extends FxSpec {
  /** Keys that got past the focus layer to the window's own handlers: what the platform would have acted on. */
  private var reached: List[String] = Nil

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx(window.addEventHandler(KeyEvent.KEY_PRESSED, (e: KeyEvent) => reached = reached :+ e.getCode.getName))
  }

  /** `code` pressed and released in the window, with the modifiers given. */
  private def press(code: KeyCode, text: String = "", ctrl: Boolean = false, meta: Boolean = false,
                    alt: Boolean = false, shift: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, shift, ctrl, alt, meta))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, shift, ctrl, alt, meta))
  }

  private def card(): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c.size.width @= 100.0
    c.size.height @= 100.0
    c
  }

  /** Three cards in a row on the screen, the first focused, and fresh keymaps; all taken away after. */
  private def onScreen[T](test: Vector[Component] => T): T = {
    val cards = Vector.fill(3)(card())
    fx {
      ui.children.clear()
      val r = new Container
      r.layout @= Some(HorizontalLayout(10.0))
      r.children ++= cards
      ui.children += r
      cards(0).focus()
      reached = Nil
    }
    try test(cards) finally fx {
      Keymap.global.commands @= Nil
      Keymap.shown @= (() => None)
      Keymap.embeds @= Map.empty
      Keymap.doubleTapWindow @= 400.0
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A chord in the global keymap" should {
    "run its command and keep the key from the focused component and the platform" in onScreen { cards =>
      var ran = 0
      var heard: List[UIKeyEvent] = Nil
      fx {
        Keymap.global.add("tab.close", "Close tab", Chord("W", primary = true))(ran += 1)
        cards(0).event.key.down.attach(e => heard = heard :+ e)
      }
      press(KeyCode.W, "w", ctrl = true)
      ran shouldBe 1
      heard shouldBe Nil
      reached shouldBe Nil
    }
    "match only with exactly its modifiers held" in onScreen { _ =>
      var ran = 0
      fx(Keymap.global.add("tab.close", "Close tab", Chord("W", primary = true))(ran += 1))
      press(KeyCode.W, "W", ctrl = true, shift = true)
      press(KeyCode.W, "w")
      press(KeyCode.W, "w", alt = true)
      ran shouldBe 0
    }
    "be written out and read back" in onScreen { _ =>
      var ran: List[String] = Nil
      fx {
        Keymap.global.add("file.new", "New file", Chord.parse("Primary+Shift+N")) { ran = ran :+ "new" }
        Keymap.global.add("tab.next", "Next tab", Chord.parse("Ctrl+Tab")) { ran = ran :+ "next" }
        Keymap.global.add("tab.previous", "Previous tab", Chord.parse("Ctrl+Shift+Tab")) { ran = ran :+ "previous" }
        Keymap.global.add("close", "Close", Chord.parse("Escape")) { ran = ran :+ "close" }
      }
      press(KeyCode.N, "N", ctrl = true, shift = true)
      press(KeyCode.TAB, ctrl = true)
      press(KeyCode.TAB, ctrl = true, shift = true)
      press(KeyCode.ESCAPE)
      ran shouldBe List("new", "next", "previous", "close")
      Chord.parse("Primary+Shift+N") shouldBe Chord("N", primary = true, shift = true)
      Chord.parse("ctrl++") shouldBe Chord("+", ctrl = true)
      fx(Chord.parse("Primary+Shift+N").label) shouldBe "Ctrl+Shift+N"   // not a Mac
    }
    "follow a rebinding, stop when disabled, and be listed" in onScreen { _ =>
      var ran = 0
      fx {
        val c = Keymap.global.add("tab.close", "Close tab", Chord("W", primary = true))(ran += 1)
        Keymap.global.rebind("tab.close", Chord("F4", ctrl = true))
        c.chords() shouldBe List(Chord("F4", ctrl = true))
      }
      press(KeyCode.W, "w", ctrl = true)
      press(KeyCode.F4, ctrl = true)
      ran shouldBe 1
      fx(Keymap.global("tab.close").get.enabled @= false)
      press(KeyCode.F4, ctrl = true)
      ran shouldBe 1
      fx(Keymap.listed.map(_.id)) shouldBe List("tab.close")
      fx(Keymap.global("tab.close").get.reset())
      fx(Keymap.global("tab.close").get.chords()) shouldBe List(Chord("W", primary = true))
    }
  }

  "A screen's keymap" should {
    "be in force only while its screen shows, and before the global one" in onScreen { _ =>
      var ran: List[String] = Nil
      val screen = new Keymap
      var showing = false
      fx {
        Keymap.global.add("save", "Save", Chord("S", primary = true)) { ran = ran :+ "global" }
        screen.add("save", "Save file", Chord("S", primary = true)) { ran = ran :+ "screen" }
        Keymap.shown @= (() => if (showing) Some(screen) else None)
      }
      press(KeyCode.S, "s", ctrl = true)
      showing = true
      press(KeyCode.S, "s", ctrl = true)
      ran shouldBe List("global", "screen")
      // the screen's hides the global one of the same id in a list of shortcuts
      fx(Keymap.listed.map(_.label)) shouldBe List("Save file")
    }
  }

  "The keys" should {
    "carry their modifiers to the focused component" in onScreen { cards =>
      var heard: List[UIKeyEvent] = Nil
      fx(cards(0).event.key.down.attach(e => heard = heard :+ e))
      press(KeyCode.Q, "q", ctrl = true, alt = true)
      heard.map(e => (e.name, e.ctrl, e.meta, e.alt, e.shift)) shouldBe List(("q", true, false, true, false))
    }
    "be left to the platform when nothing takes them" in onScreen { _ =>
      fx(Keymap.global.add("tab.close", "Close tab", Chord("W", primary = true))(()))
      press(KeyCode.Q, "q", ctrl = true)
      reached shouldBe List("Q")
    }
    "move the focus back on Shift+Tab and on with Tab" in onScreen { cards =>
      press(KeyCode.TAB, shift = true)
      fx(Focus.focused()) shouldBe Some(cards(2))
      press(KeyCode.TAB, shift = true)
      fx(Focus.focused()) shouldBe Some(cards(1))
      press(KeyCode.TAB)
      fx(Focus.focused()) shouldBe Some(cards(2))
    }
    "move along a row on the arrows as before, a chord on the same key notwithstanding" in onScreen { cards =>
      var ran = 0
      fx(Keymap.global.add("jump", "Jump", Chord("ArrowRight", ctrl = true))(ran += 1))
      press(KeyCode.RIGHT)
      fx(Focus.focused()) shouldBe Some(cards(1))
      press(KeyCode.RIGHT, ctrl = true)
      fx(Focus.focused()) shouldBe Some(cards(1))
      ran shouldBe 1
    }
  }
  /** `code` pressed and released on an embed named `name` in the window, as the keys of an editor there come. */
  private def pressIn(name: String, code: KeyCode, text: String = "", ctrl: Boolean = false, shift: Boolean = false): Unit = {
    val editor = fx {
      val r = new javafx.scene.layout.Region
      r.getProperties.put(Host.embedKey, name)
      window.getRoot.asInstanceOf[Pane].getChildren.add(r)
      r
    }
    fx {
      javafx.event.Event.fireEvent(editor, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, shift, ctrl, false, false))
      javafx.event.Event.fireEvent(editor, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, shift, ctrl, false, false))
      window.getRoot.asInstanceOf[Pane].getChildren.remove(editor)
    }
  }

  "Keys in an embed" should {
    "be left to it, but for the chords it leaves to the app and the commands that win there" in onScreen { cards =>
      var ran: List[String] = Nil
      fx {
        Keymap.global.add("tab.close", "Close tab", Chord("W", primary = true)) { ran = ran :+ "close" }
        Keymap.global.add("find", "Find", Chord("F", primary = true)) { ran = ran :+ "find" }
        Keymap.global.add("file.new", "New file", Chord.parse("Primary+Shift+N")) { ran = ran :+ "new" }
          .inEmbeds @= true
        Keymap.embed("editor", Chord("W", primary = true))
      }
      pressIn("editor", KeyCode.F, "f", ctrl = true)
      pressIn("editor", KeyCode.RIGHT)
      pressIn("editor", KeyCode.TAB)
      // what the embed kept got past the focus layer untouched, and the focus stayed where it was
      reached shouldBe List("F", "Right", "Tab")
      fx(Focus.focused()) shouldBe Some(cards(0))
      pressIn("editor", KeyCode.W, "w", ctrl = true)
      pressIn("editor", KeyCode.N, "N", ctrl = true, shift = true)
      ran shouldBe List("close", "new")
      reached shouldBe List("F", "Right", "Tab")
      // another embed leaves it nothing but the command that wins everywhere
      pressIn("viewer", KeyCode.W, "w", ctrl = true)
      pressIn("viewer", KeyCode.N, "N", ctrl = true, shift = true)
      ran shouldBe List("close", "new", "new")
      // outside any embed, Find is the app's again
      press(KeyCode.F, "f", ctrl = true)
      ran shouldBe List("close", "new", "new", "find")
    }
    "have Escape, which the embed can give back to be Back" in onScreen { _ =>
      var backs = 0
      val off = fx(Focus.onBack(k => { if (k == everyui.event.Key.Escape) backs += 1; true }))
      try {
        pressIn("editor", KeyCode.ESCAPE)
        backs shouldBe 0
        reached shouldBe List("Esc")
        fx(Focus.handBack("Escape")) shouldBe true
        backs shouldBe 1
      }
      finally fx(off())
    }
  }

  /** A modifier `code` pressed or released on `target`, holding itself as it goes down (Ctrl down with Ctrl's own
    * press) and the others given, as a keyboard reports it. */
  private def modifier(code: KeyCode, pressed: Boolean, target: javafx.event.EventTarget = window,
                       shift: Boolean = false): Unit = fx {
    val kind = if (pressed) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED
    javafx.event.Event.fireEvent(target, new KeyEvent(kind, "", "", code,
      shift || (pressed && code == KeyCode.SHIFT), pressed && code == KeyCode.CONTROL,
      pressed && code == KeyCode.ALT, pressed && code == KeyCode.META))
  }

  /** A modifier `code` tapped on `target`: pressed and released. */
  private def tap(code: KeyCode, target: javafx.event.EventTarget = window): Unit = {
    modifier(code, pressed = true, target)
    modifier(code, pressed = false, target)
  }

  "A double-tap of a modifier" should {
    "run its command on the second release" in onScreen { _ =>
      var ran: List[String] = Nil
      fx {
        Keymap.global.add("palette", "Palette", Chord.doubleTap("Control")) { ran = ran :+ "palette" }
        Keymap.global.add("search", "Search", Chord.parse("Shift Shift")) { ran = ran :+ "search" }
      }
      tap(KeyCode.CONTROL)
      modifier(KeyCode.CONTROL, pressed = true)
      ran shouldBe Nil
      modifier(KeyCode.CONTROL, pressed = false)
      ran shouldBe List("palette")
      tap(KeyCode.SHIFT)
      tap(KeyCode.SHIFT)
      ran shouldBe List("palette", "search")
      // a third tap is the first of the next double-tap
      tap(KeyCode.SHIFT)
      ran shouldBe List("palette", "search")
    }
    "not run when too slow" in onScreen { _ =>
      var ran = 0
      fx {
        Keymap.doubleTapWindow @= 100.0
        Keymap.global.add("palette", "Palette", Chord.doubleTap("Control"))(ran += 1)
      }
      tap(KeyCode.CONTROL)
      Thread.sleep(200L)
      tap(KeyCode.CONTROL)
      ran shouldBe 0
      // the slow second tap starts another, which a quick third completes
      tap(KeyCode.CONTROL)
      ran shouldBe 1
    }
    "not run when another key goes down between, or another modifier is held" in onScreen { _ =>
      var ran = 0
      fx(Keymap.global.add("palette", "Palette", Chord.doubleTap("Control"))(ran += 1))
      // Ctrl+C, then Ctrl
      modifier(KeyCode.CONTROL, pressed = true)
      press(KeyCode.C, "c", ctrl = true)
      modifier(KeyCode.CONTROL, pressed = false)
      tap(KeyCode.CONTROL)
      ran shouldBe 0
      // Ctrl tapped twice with Shift held
      modifier(KeyCode.CONTROL, pressed = true, shift = true)
      modifier(KeyCode.CONTROL, pressed = false, shift = true)
      modifier(KeyCode.CONTROL, pressed = true, shift = true)
      modifier(KeyCode.CONTROL, pressed = false, shift = true)
      ran shouldBe 0
    }
    "take no repeat of a held modifier for a press" in onScreen { _ =>
      var ran = 0
      fx(Keymap.global.add("palette", "Palette", Chord.doubleTap("Control"))(ran += 1))
      modifier(KeyCode.CONTROL, pressed = true)
      modifier(KeyCode.CONTROL, pressed = true)
      modifier(KeyCode.CONTROL, pressed = true)
      modifier(KeyCode.CONTROL, pressed = false)
      ran shouldBe 0
      tap(KeyCode.CONTROL)
      ran shouldBe 1
    }
    "run inside an embed, though the command is not made to win there" in onScreen { cards =>
      var ran = 0
      fx(Keymap.global.add("palette", "Palette", Chord.doubleTap("Primary"))(ran += 1))
      val editor = fx {
        val r = new javafx.scene.layout.Region
        r.getProperties.put(Host.embedKey, "editor")
        window.getRoot.asInstanceOf[Pane].getChildren.add(r)
        r
      }
      try {
        tap(KeyCode.CONTROL, editor)
        tap(KeyCode.CONTROL, editor)
        ran shouldBe 1
        fx(Focus.focused()) shouldBe Some(cards(0))
      }
      finally fx(window.getRoot.asInstanceOf[Pane].getChildren.remove(editor))
    }
    "keep a lone Alt from the platform only while a double-tap of Alt runs a command" in onScreen { _ =>
      var ran = 0
      tap(KeyCode.ALT)
      reached shouldBe List("Alt")
      fx(Keymap.global.add("menu", "Menu", Chord.parse("Alt Alt"))(ran += 1))
      tap(KeyCode.ALT)
      tap(KeyCode.ALT)
      ran shouldBe 1
      reached shouldBe List("Alt")
    }
    "be written out, labelled and listed" in onScreen { _ =>
      Chord.parse("Ctrl Ctrl") shouldBe Chord.doubleTap("Control")
      Chord.parse("control  control") shouldBe Chord("Control", doubleTap = true)
      Chord.parse("Shift Shift") shouldBe Chord.doubleTap("Shift")
      Chord.parse("Alt Alt") shouldBe Chord.doubleTap("Option")
      Chord.parse("Primary Primary") shouldBe Chord.doubleTap("Primary")
      Chord.parse("Ctrl+Shift") shouldBe Chord("Shift", ctrl = true)
      fx {
        Keymap.global.add("palette", "Palette", Chord.parse("Primary Primary"))(())
        Keymap.global.add("search", "Search", Chord.doubleTap("Shift"))(())
        Keymap.global.add("menu", "Menu", Chord.doubleTap("Alt"))(())
      }
      // not a Mac: Primary is Ctrl
      fx(Keymap.listed.map(c => c.id + " " + c.chords().map(_.label).mkString(", "))) shouldBe
        List("palette Ctrl Ctrl", "search Shift Shift", "menu Alt Alt")
      fx(Chord.doubleTap("Primary").tapped) shouldBe "Control"
      // no one key is a double-tap
      fx(Chord.doubleTap("Control").matches(UIKeyEvent(Keys.of("Control"), "Control", ctrl = true))) shouldBe false
    }
  }
}
