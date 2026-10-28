package everyui.menu

import everyui.component.*
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
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

  "A menu dropped from a button" should {
    "give the focus back to what had it before the press on the button" in onScreen { field =>
      val button = fx {
        val b = new Component(Nodes.container())
        b.focusable @= true
        b.size.width @= 80.0
        b.size.height @= 30.0
        b.position.left @= 200.0
        b.position.top @= 50.0
        ui.children += b
        b
      }
      everyui.event.FocusMoves.watch()
      // a click on the button: the pointer used, and the focus moved to the button by it, as a click does
      fx {
        everyui.event.Pointer.usedAt = everyui.render.Platform.ticks()
        button.focus()
        Menu.below(button, List(MenuItem("High", () => ())))
      }
      focusedLabel shouldBe Some("High")
      press("Enter")
      fx(Focus.focused()) shouldBe Some(field)
      // dismissed the same way
      fx {
        everyui.event.Pointer.usedAt = everyui.render.Platform.ticks()
        button.focus()
        Menu.below(button, List(MenuItem("Low", () => ())))
      }
      press("Escape")
      fx(Focus.focused()) shouldBe Some(field)
    }
    "give it back to the button where the keys opened it" in onScreen { _ =>
      val button = fx {
        val b = new Component(Nodes.container())
        b.focusable @= true
        b.size.width @= 80.0
        b.size.height @= 30.0
        b.position.left @= 200.0
        b.position.top @= 50.0
        ui.children += b
        b.focus()
        b
      }
      Thread.sleep(1100L)
      everyui.event.Pointer.usedAt = -1.0e9
      fx(Menu.below(button, List(MenuItem("High", () => ()))))
      press("Enter")
      fx(Focus.focused()) shouldBe Some(button)
    }
  }

  "A menu dropped from a dialog" should {
    "go over the dialog, which is there again with the focus once it is chosen from or dismissed" in onScreen { _ =>
      var chose = 0
      val (dialog, button) = fx {
        val d = new Container
        d.size.width @= 300.0
        d.size.height @= 200.0
        val b = new Component(Nodes.container())
        b.focusable @= true
        b.size.width @= 80.0
        b.size.height @= 30.0
        d.children += b
        Modal.show(d, b)
        (d, b)
      }
      fx(Focus.focused()) shouldBe Some(button)
      val menu = fx(Menu.below(button, List(MenuItem("High", () => chose += 1))))
      fx(Modal.top) shouldBe Some(menu)
      press("Escape")
      fx(Modal.top) shouldBe Some(dialog)
      fx(Focus.focused()) shouldBe Some(button)
      fx(Menu.below(button, List(MenuItem("High", () => chose += 1))))
      press("Enter")
      chose shouldBe 1
      fx(Modal.top) shouldBe Some(dialog)
      fx(Focus.focused()) shouldBe Some(button)
    }
    "take the place of a menu on top rather than go over it" in onScreen { _ =>
      fx(Menu.at(100.0, 100.0, List(MenuItem("One", () => ()))))
      val second = fx(Menu.at(200.0, 200.0, List(MenuItem("Two", () => ()))))
      fx(Modal.top) shouldBe Some(second)
      fx(Modal.close())
      fx(Modal.showing) shouldBe false
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
    "open a submenu when the pointer rests on its entry" in onScreen { _ =>
      fx(Menu.hoverDelay @= 0.05)
      val menu = fx(Menu.at(100.0, 100.0, List(MenuItem("Open"), MenuItem.sub("Copy", List(MenuItem("Path"))))))
      val copy = fx(menu.rows(1))
      fx(copy.event.pointer.enter @= PointerEvent(PointerKind.Enter, 150.0, 130.0))
      focusedLabel shouldBe Some("Copy")
      eventually(Focus.focused().collect { case r: MenuRow => r.item.label }.contains("Path")) shouldBe true
      fx(Menu.hoverDelay @= 0.3)
    }
    "switch submenus, or close one, as the pointer moves over the menu under it, and close all on a click outside" in onScreen { _ =>
      fx(Menu.hoverDelay @= 0.05)
      var chose = ""
      var dismissed = 0
      val menu = fx(Menu.at(100.0, 100.0, List(MenuItem("Open", () => chose = "open"),
        MenuItem.sub("Copy", List(MenuItem("Path"))), MenuItem.sub("Share", List(MenuItem("Mail")))), () => dismissed += 1))
      def label: Option[String] = Focus.focused().collect { case r: MenuRow => r.item.label }
      def sheet: Component = ui.children().last
      def at(i: Int): (Double, Double) = (menu.absoluteLeft + 20.0, menu.rows(i).absoluteTop + menu.rows(i).size.height() / 2.0)
      def move(i: Int): Unit = fx {
        val (x, y) = at(i)
        sheet.event.pointer.move @= PointerEvent(PointerKind.Move, x, y)
      }
      fx(menu.rows(1).event.pointer.enter @= PointerEvent(PointerKind.Enter, 0.0, 0.0))
      eventually(label.contains("Path")) shouldBe true
      move(2)
      eventually(label.contains("Mail")) shouldBe true
      fx(ui.children().size) shouldBe 3   // the card, the menu's sheet and one submenu's
      move(0)
      eventually(label.contains("Open")) shouldBe true
      fx(ui.children().size) shouldBe 2
      // a click through a submenu's sheet onto an entry chooses it
      fx(menu.rows(1).event.pointer.enter @= PointerEvent(PointerKind.Enter, 0.0, 0.0))
      eventually(label.contains("Path")) shouldBe true
      fx {
        val (x, y) = at(0)
        sheet.event.click @= PointerEvent(PointerKind.Click, x, y)
      }
      chose shouldBe "open"
      fx(Modal.showing) shouldBe false
      // and one outside every menu closes them all
      val again = fx(Menu.at(100.0, 100.0, List(MenuItem.sub("Copy", List(MenuItem("Path")))), () => dismissed += 1))
      fx(again.rows(0).event.pointer.enter @= PointerEvent(PointerKind.Enter, 0.0, 0.0))
      eventually(label.contains("Path")) shouldBe true
      fx(sheet.event.click @= PointerEvent(PointerKind.Click, 1500.0, 900.0))
      fx(Modal.showing) shouldBe false
      eventually(dismissed == 1) shouldBe true
      fx(Menu.hoverDelay @= 0.3)
    }
    "open a submenu to the left, and in and out of it the other way, in a right-to-left language" in onScreen { _ =>
      fx(Direction.rtl @= true)
      try {
        val menu = fx(Menu.at(900.0, 100.0, List(MenuItem("Open"), MenuItem.sub("Copy", List(MenuItem("Path"))))))
        fx(menu.position.left() + menu.size.width()) shouldBe 900.0 +- 0.5
        press("ArrowDown")
        press("ArrowRight")
        focusedLabel shouldBe Some("Copy")
        press("ArrowLeft")
        focusedLabel shouldBe Some("Path")
        val sub = fx(Focus.focused().get.parent().flatMap(_.parent()).flatMap(_.parent()).flatMap(_.parent()).get)
        fx(sub.position.left() + sub.size.width()) should be < fx(menu.position.left() + 20.0)
        press("ArrowRight")
        focusedLabel shouldBe Some("Copy")
      }
      finally fx(Direction.rtl @= false)
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
    "open on its current choice, and keep it half lit while the focus is elsewhere" in onScreen { card =>
      val menu = fx(Menu.below(card, List(MenuItem("List"), MenuItem("Grid", active = true), MenuItem("Columns"))))
      def ground(i: Int): String = fx {
        val c = menu.rows(i).backgroundColor()
        if (c == MenuRow.fill(focused = true, active = false)) "lit"
        else if (c == MenuRow.fill(focused = false, active = true)) "half"
        else if (c == MenuRow.fill(focused = false, active = false)) "clear"
        else "other"
      }
      focusedLabel shouldBe Some("Grid")
      (0 to 2).map(ground) shouldBe Vector("clear", "lit", "clear")
      press("ArrowDown")
      focusedLabel shouldBe Some("Columns")
      (0 to 2).map(ground) shouldBe Vector("clear", "half", "lit")
      fx(menu.rows.head.event.pointer.enter @= PointerEvent(PointerKind.Enter, 0.0, 0.0))
      focusedLabel shouldBe Some("List")
      (0 to 2).map(ground) shouldBe Vector("lit", "half", "clear")
      // half lit is still lit, and plainly less than the focus
      fx(MenuRow.fill(focused = false, active = true).alpha) shouldBe fx(everyui.theme.Theme.controlLit().alpha * 0.5) +- (1.0 / 255.0)
    }
    "ring its current choice in the accent, its words in it, and no other" in onScreen { card =>
      val menu = fx(Menu.below(card, List(MenuItem("List"), MenuItem("Grid", active = true), MenuItem("Columns"))))
      def ring(i: Int): Double = fx(menu.rows(i).borderWidth())
      ring(1) shouldBe MenuRow.ring
      fx(menu.rows(1).borderColor()) shouldBe everyui.theme.Theme.accent()
      ring(0) shouldBe 0.0
      ring(2) shouldBe 0.0
      def words(i: Int): Option[everyui.Color] = fx {
        def all(c: Component): List[Component] = c :: (c match {
          case k: Container => k.children().toList.flatMap(all)
          case _ => Nil
        })
        all(menu.rows(i)).collectFirst { case t: TextView if t.content() == menu.rows(i).item.label => t.color() }
      }
      words(1) shouldBe Some(everyui.theme.Theme.accent())
      words(0) shouldBe Some(everyui.theme.Theme.text())
      // the focus moved off it, it keeps its ring
      press("ArrowDown")
      ring(1) shouldBe MenuRow.ring
    }
    "open on its first entry with no current choice" in onScreen { _ =>
      fx(Menu.show(List(MenuItem("One"), MenuItem("Two"))))
      focusedLabel shouldBe Some("One")
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
