package everyui.menu

import everyui.component.*
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.keymap.{Chord, Command}
import everyui.render.Nodes
import everyui.{FxSpec, ui}

/** Menus: where they open, the arrows and OK through them, submenus in and out, and the focus given back. */
class MenuSpec extends FxSpec {
  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private def focusedLabel: Option[String] = fx(Focus.focused().collect { case r: MenuRow => r.item.label })

  /** A focusable card on the screen, focused, for the menus to give the focus back to; all taken away after. */
  private def onScreen[T](test: Component => T): T = {
    val card = fx {
      ui.children.clear()
      val c = new Component(Nodes.container())
      c.focusable @= true
      c.size.width @= 100.0
      c.size.height @= 40.0
      c.position.left @= 50.0
      c.position.top @= 50.0
      ui.children += c
      c.focus()
      c
    }
    try test(card) finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A menu" should {
    "move through what can be chosen, round the ends, and choose with OK" in onScreen { card =>
      var chose: List[String] = Nil
      var dismissed = 0
      val menu = fx(Menu.at(1900.0, 20.0, List(
        MenuItem("Cut", () => chose = chose :+ "cut", enabled = false),
        MenuItem("Copy", () => chose = chose :+ "copy"),
        MenuItem.separator,
        MenuItem("Paste", () => chose = chose :+ "paste")), () => dismissed += 1))
      // kept on the screen
      fx(menu.position.left() + menu.size.width()) should be <= 1912.0
      focusedLabel shouldBe Some("Copy")
      press("ArrowDown")
      focusedLabel shouldBe Some("Paste")
      press("ArrowDown")
      focusedLabel shouldBe Some("Copy")
      press("ArrowUp")
      focusedLabel shouldBe Some("Paste")
      press("Enter")
      chose shouldBe List("paste")
      fx(Modal.showing) shouldBe false
      dismissed shouldBe 0
      fx(Focus.focused()) shouldBe Some(card)
    }
    "open a submenu beside its entry, come back out of it, and close them all on a choice" in onScreen { card =>
      var chose = ""
      var dismissed = 0
      val menu = fx(Menu.at(100.0, 100.0, List(
        MenuItem("Open"),
        MenuItem.sub("Copy", List(MenuItem("Path", () => chose = "path"), MenuItem("Name", () => chose = "name")))),
        () => dismissed += 1))
      press("ArrowDown")
      press("ArrowRight")
      focusedLabel shouldBe Some("Path")
      val sub = fx(Focus.focused().get.parent().flatMap(_.parent()).flatMap(_.parent()).flatMap(_.parent()).get)
      fx(sub.position.left()) should be > fx(menu.position.left() + menu.size.width() - 20.0)
      press("ArrowLeft")
      focusedLabel shouldBe Some("Copy")
      press("Enter")
      focusedLabel shouldBe Some("Path")
      press("ArrowDown")
      press("Enter")
      chose shouldBe "name"
      fx(Modal.showing) shouldBe false
      dismissed shouldBe 0
      fx(Focus.focused()) shouldBe Some(card)
    }
    "say it was closed with nothing chosen" in onScreen { card =>
      var dismissed = 0
      fx(Menu.show(List(MenuItem("One"), MenuItem("Two")), "Title", () => dismissed += 1))
      press("Escape")
      fx(Modal.showing) shouldBe false
      eventually(dismissed == 1) shouldBe true
      fx(Focus.focused()) shouldBe Some(card)
    }
    "show a command's chord, a tick, and drop from its anchor" in onScreen { card =>
      val command = new Command("file.save", "Save", List(Chord("S", primary = true)), () => ())
      val menu = fx(Menu.below(card, List(MenuItem.command(command), MenuItem.check("Wrap", on = true, _ => ()))))
      def shown(row: MenuRow): List[String] = {
        var found: List[String] = Nil
        var pending: List[Component] = List(row)
        while (pending.nonEmpty) {
          found = found :+ pending.head.kind
          pending = pending.head.childComponents.toList ++ pending.tail
        }
        found
      }
      fx(menu.rows.head.item.shortcut.map(_.label)) shouldBe Some("Ctrl+S")
      fx(shown(menu.rows(1))).exists(_.contains("check")) shouldBe true
      fx(menu.position.top()) shouldBe 94.0
      fx(menu.position.left()) shouldBe 50.0
    }
    "open where the pointer asks for it" in onScreen { card =>
      fx(Menu.context(card)(List(MenuItem("Rename"))))
      fx(card.event.pointer.menu @= PointerEvent(PointerKind.Menu, 300.0, 200.0))
      fx(Modal.showing) shouldBe true
      focusedLabel shouldBe Some("Rename")
      fx(Focus.focused().get.absoluteLeft) should be >= 300.0
    }
  }
}
