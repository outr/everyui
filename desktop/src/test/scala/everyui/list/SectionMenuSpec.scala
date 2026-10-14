package everyui.list

import everyui.component.{Component, MaterialIconView, Modal, TextView}
import everyui.event.{Pointer, PointerKind}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.menu.{Menu, MenuItem, MenuRow}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** A rich list's section headers offer the app's menu for the section (`sectionMenu`): the secondary button at the
  * pointer, the menu key, Shift+F10 and OK held dropped from the header, and the ⋮ at its end, which shows with the
  * pointer over it or the cursor on it, and folds nothing. A header without one is as it was: no ⋮, the platform's
  * own menu, its count where it was. */
class SectionMenuSpec extends FxSpec {
  private def three: Var[Vector[RichSection[String]]] = Var(Vector(
    RichSection("work", "Work", Vector("w0", "w1")),
    RichSection("home", "Home", Vector("h0", "h1"), icon = Some(MaterialIcon.Home)),
    RichSection("old", "Old", Vector("o0"))))

  private def made(chose: String => Unit): RichList[String] = {
    val list = new RichList[String](three, s => s, s => RichLook(s))
    list.sectionMenu @= ((s: RichSection[String]) => if (s.key == "old") Nil else List(
      MenuItem("New in " + s.title, () => chose("new " + s.key)), MenuItem("Rename", () => chose("rename " + s.key))))
    list
  }

  private def key(code: KeyCode, down: Boolean, shift: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(if (down) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED, "", "",
      code, shift, false, false, false))
  }

  private def press(code: KeyCode, shift: Boolean = false): Unit = {
    key(code, down = true, shift)
    key(code, down = false, shift)
  }

  private def click(on: Component): Unit = fx {
    val x = on.absoluteLeft + on.size.width() / 2.0
    val y = on.absoluteTop + on.size.height() / 2.0
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y, MouseButton.PRIMARY,
      1, false, false, false, false, true, false, false, false, false, true, null))
  }

  // the secondary button over the list at `y`, as the platform hears it, and whether the app answered it
  private def rightClick(list: RichList[String], y: Double): Boolean = fx {
    Pointer.consumed = None
    list.list.event.pointer.heard(list.list.event.pointer.menu, PointerKind.Menu, 40.0, y)
    Pointer.menuAnswered
  }

  private def row(list: RichList[String], index: Int): RichRow[String] =
    list.list.rowAt(index).get.asInstanceOf[RichRow[String]]

  private def lines(list: RichList[String]): List[String] = fx((0 until list.list.count).map(list.list(_))
    .map(l => l.item.getOrElse("#" + l.section.key)).toList)

  private def menus: List[Menu] = all(ui).collect { case m: Menu => m }

  private def entries: List[String] = fx(menus.flatMap(m => all(m)).collect { case r: MenuRow => r.item.label })

  private def all(c: Component): List[Component] = c :: (c match {
    case k: everyui.component.Container => k.children().toList.flatMap(all)
    case _ => Nil
  })

  private def onScreen[T](list: RichList[String], keep: Boolean = false)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += list
      list.focus()
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      Direction.set(false)
      ui.children.clear()
      // taken apart, done with, unless it is to be shown again (its clock, a Ticker, rests already, off the screen)
      if (!keep) list.dispose()
      Focus.focused @= None
    }
  }

  "A section header's menu" should {
    "open at the pointer for the secondary button, and leave the platform's where the section has none" in {
      var chose: List[String] = Nil
      val list = fx(made(c => chose = chose :+ c))
      onScreen(list) {
        rightClick(list, 10.0) shouldBe true
        entries shouldBe List("New in Work", "Rename")
        fx(Focus.focused().collect { case r: MenuRow => r.item.label }) shouldBe Some("New in Work")
        press(KeyCode.ENTER)
        chose shouldBe List("new work")
        fx(list.collapsed()) shouldBe Set.empty
        // Old's header, the sixth line, has none
        lines(list)(6) shouldBe "#old"
        rightClick(list, fx(row(list, 6).absoluteTop) + 10.0) shouldBe false
        fx(Modal.showing) shouldBe false
      }
    }
    "open dropped from the header for the menu key, Shift+F10 and OK held, folding nothing" in {
      var chose: List[String] = Nil
      val list = fx(made(c => chose = chose :+ c))
      onScreen(list) {
        fx(list.list.moveTo(3))
        lines(list)(3) shouldBe "#home"
        press(KeyCode.CONTEXT_MENU)
        entries shouldBe List("New in Home", "Rename")
        // under the header, at its start (kept off the window's edge)
        val (top, left) = fx(menus.map(m => (m.absoluteTop, m.absoluteLeft)).head)
        top shouldBe (fx(row(list, 3).absoluteTop + RichRow.headerHeight) + 4.0 +- 0.5)
        left shouldBe (math.max(8.0, fx(row(list, 3).absoluteLeft)) +- 0.5)
        press(KeyCode.DOWN)
        press(KeyCode.ENTER)
        chose shouldBe List("rename home")
        fx(Focus.focused().exists(_ eq list.list)) shouldBe true
        press(KeyCode.F10, shift = true)
        entries shouldBe List("New in Home", "Rename")
        press(KeyCode.ESCAPE)
        fx(Modal.showing) shouldBe false
        // OK held: the menu, and the header neither opened nor closed as it is let go
        key(KeyCode.ENTER, down = true)
        Thread.sleep((VirtualList.holdSeconds * 1000.0).toLong + 300L)
        eventually(Modal.showing) shouldBe true
        key(KeyCode.ENTER, down = false)
        entries shouldBe List("New in Home", "Rename")
        fx(list.collapsed()) shouldBe Set.empty
        press(KeyCode.ESCAPE)
        // a header with none: the key opens nothing
        fx(list.list.moveTo(6))
        press(KeyCode.CONTEXT_MENU)
        fx(Modal.showing) shouldBe false
        chose shouldBe List("rename home")
      }
    }
    "show a ⋮ with the cursor on the header, which opens it without folding, and none where there is no menu" in {
      var chose: List[String] = Nil
      val list = fx(made(c => chose = chose :+ c))
      onScreen(list) {
        fx(list.list.moveTo(0))
        fx(row(list, 0).sectionMore.visible()) shouldBe true
        fx(row(list, 3).sectionMore.visible()) shouldBe false
        fx(row(list, 6).sectionMore.visible()) shouldBe false
        fx(list.list.moveTo(6))
        fx(row(list, 6).sectionMore.visible()) shouldBe false
        // at the header's end, clear of its count
        val more = fx(row(list, 0).sectionMore)
        fx(list.list.moveTo(0))
        fx(more.absoluteLeft + more.size.width()) shouldBe (fx(list.size.width()) - 6.0 +- 0.5)
        fx(countOf(row(list, 0)).map(c => c.absoluteLeft + c.size.width()).get) should be < fx(more.absoluteLeft)
        click(more)
        entries shouldBe List("New in Work", "Rename")
        fx(list.collapsed()) shouldBe Set.empty
        fx(menus.map(_.absoluteTop).head) shouldBe (fx(more.absoluteTop + more.size.height()) + 4.0 +- 0.5)
        press(KeyCode.ESCAPE)
        // a click on the header itself still folds it
        click(fx(row(list, 3)))
        fx(list.collapsed()) shouldBe Set("home")
        press(KeyCode.ENTER)
        fx(list.collapsed()) shouldBe Set.empty
        chose shouldBe Nil
      }
    }
    "leave a header without one as it was, and draw a section's icon before its title" in {
      val plain = fx(new RichList[String](three, s => s, s => RichLook(s)))
      val width = onScreen(plain, keep = true) {
        fx(plain.list.moveTo(0))
        fx(row(plain, 0).sectionMore.visible()) shouldBe false
        fx(row(plain, 0).size.height()) shouldBe RichRow.headerHeight
        rightClick(plain, 10.0) shouldBe false
        fx(Modal.showing) shouldBe false
        // Enter still folds it
        press(KeyCode.ENTER)
        fx(plain.collapsed()) shouldBe Set("work")
        fx(countOf(row(plain, 0)).map(c => c.absoluteLeft + c.size.width()).get)
      }
      fx(plain.collapsed @= Set.empty)
      // the count where it always was: the header's end, short of the inset
      width shouldBe (fx(plain.size.width()) - RichRow.inset +- 0.5)
      onScreen(plain) {
        fx(icons(row(plain, 3))) shouldBe List(MaterialIcon.Home)
        fx(icons(row(plain, 0))) shouldBe Nil
      }
    }
    "mirror right to left, the ⋮ at the left" in {
      val list = fx(made(_ => ()))
      onScreen(list) {
        fx(Direction.set(true))
        fx(list.list.moveTo(0))
        val more = fx(row(list, 0).sectionMore)
        fx(more.visible()) shouldBe true
        fx(more.absoluteLeft) shouldBe (6.0 +- 0.5)
        fx(countOf(row(list, 0)).map(_.absoluteLeft).get) should be > fx(more.absoluteLeft + more.size.width())
      }
    }
  }

  // the header's count, as its label draws it
  private def countOf(r: RichRow[String]): Option[TextView] = all(r).collect {
    case t: TextView if t.visible() && t.content().forall(_.isDigit) && t.content().nonEmpty && everyone(t, r) => t
  }.headOption

  // shown, and all it sits in up to the row
  private def everyone(c: Component, upTo: Component): Boolean = c.visible() && c.parent().forall(p => (p eq upTo) ||
    everyone(p, upTo))

  private def icons(r: RichRow[String]): List[MaterialIcon] = all(r).collect {
    case v: MaterialIconView if everyone(v, r) && v.icon() != MaterialIcon.Empty && v.icon() != MaterialIcon.ExpandMore &&
      v.icon() != MaterialIcon.ChevronRight && v.icon() != MaterialIcon.MoreVert => v.icon()
  }
}
