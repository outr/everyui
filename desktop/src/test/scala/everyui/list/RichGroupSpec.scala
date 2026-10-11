package everyui.list

import everyui.component.{Component, Modal}
import everyui.event.{Pointer, PointerKind}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.menu.{Menu, MenuItem, MenuRow}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** Groups of a rich list's sections (`RichSection.group`): a header a level above the sections' that opens and closes
  * them all, says how many they hold, is remembered apart from the sections (`collapsedGroups`), opens to what the
  * words find, takes the keys and its menu (`groupMenu`) as a section's header does, is mirrored right to left, and
  * leaves sections in no group, and dragging within a section, as they were. */
class RichGroupSpec extends FxSpec {
  private val work = RichGroup("work", "Work", Some(MaterialIcon.Work))
  private val personal = RichGroup("personal", "Personal")

  // a section on its own; Alpha and Beta in Work; Me in Personal; and one on its own named as Work is
  private def sections: Var[Vector[RichSection[String]]] = Var(Vector(
    RichSection("inbox", "Inbox", Vector("i0", "i1")),
    RichSection("a", "Alpha", Vector("a0", "a1"), group = Some(work)),
    RichSection("b", "Beta", Vector("b0", "b1", "b2"), count = Some(7), group = Some(work)),
    RichSection("me", "Me", Vector("m0"), group = Some(personal)),
    RichSection("work", "Work section", Vector("w0"))))

  private def made(groups: Var[Set[String]] = Var(Set.empty[String]), collapsed: Var[Set[String]] = Var(Set.empty[String]),
                   search: Boolean = false): RichList[String] =
    new RichList[String](sections, s => s, s => RichLook(s), collapsed, search, groups)

  private def lines(list: RichList[String]): List[String] = fx((0 until list.list.count).map(list.list(_)).map(l =>
    l.groupHead.map("^" + _.key).orElse(l.item).getOrElse("#" + l.section.key)).toList)

  private def at(list: RichList[String], line: String): Int = lines(list).indexOf(line)

  private def row(list: RichList[String], index: Int): RichRow[String] =
    list.list.rowAt(index).get.asInstanceOf[RichRow[String]]

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

  private def rightClick(list: RichList[String], y: Double): Boolean = fx {
    Pointer.consumed = None
    list.list.event.pointer.heard(list.list.event.pointer.menu, PointerKind.Menu, 40.0, y)
    Pointer.menuAnswered
  }

  private def all(c: Component): List[Component] = c :: (c match {
    case k: everyui.component.Container => k.children().toList.flatMap(all)
    case _ => Nil
  })

  // where a header's count ends, as its label draws it
  private def countEnd(r: RichRow[String]): Double = fx(all(r).collect {
    case t: everyui.component.TextView if shown(t, r) && t.content().nonEmpty && t.content().forall(_.isDigit) =>
      t.absoluteLeft + t.size.width()
  }.head)

  private def shown(c: Component, upTo: Component): Boolean = c.visible() && c.parent().forall(p => (p eq upTo) ||
    shown(p, upTo))

  private def entries: List[String] = fx(all(ui).collect { case m: Menu => m }.flatMap(all)
    .collect { case r: MenuRow => r.item.label })

  private def onScreen[T](list: RichList[String])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += list
      list.focus()
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      Direction.set(false)
      ui.children.clear()
      // taken apart, so its clock (Ticker) stops: left running, it moved on every twenty seconds for the rest of the run,
      // and woke the frame loop of the specs after
      list.dispose()
      Focus.focused @= None
    }
  }

  "A group of sections" should {
    "head its sections, a level above them, saying how many they hold, leaving those in no group as they were" in {
      val list = fx(made())
      onScreen(list) {
        lines(list) shouldBe List("#inbox", "i0", "i1", "^work", "#a", "a0", "a1", "#b", "b0", "b1", "b2",
          "^personal", "#me", "m0", "#work", "w0")
        // Alpha's two and Beta's seven, as its header says
        fx(list.list(3).count) shouldBe 9
        fx(list.list(11).count) shouldBe 1
        fx(row(list, 3).size.height()) shouldBe RichRow.headerHeight
        // a section's header in a group set in under the group's; one in none where it always was
        val chevronLeft = (r: RichRow[String]) => fx(all(r).collect { case v: everyui.component.MaterialIconView
          if v.visible() && v.icon() == MaterialIcon.ExpandMore => v.absoluteLeft }.head)
        chevronLeft(row(list, 0)) shouldBe (ListRow.inset +- 0.5)
        chevronLeft(row(list, 3)) shouldBe (ListRow.inset +- 0.5)
        chevronLeft(row(list, 4)) shouldBe (ListRow.inset + RichRow.nest +- 0.5)
        chevronLeft(row(list, 14)) shouldBe (ListRow.inset +- 0.5)
        fx(list.list(0).nested) shouldBe false
        fx(list.list(4).nested) shouldBe true
      }
    }
    "fold with a click, Enter, Left and Right, its key kept apart from a section's of the same name" in {
      val groups = Var(Set.empty[String])
      val collapsed = Var(Set.empty[String])
      val list = fx(made(groups, collapsed))
      onScreen(list) {
        click(fx(row(list, 3)))
        groups() shouldBe Set("work")
        collapsed() shouldBe Set.empty
        lines(list) shouldBe List("#inbox", "i0", "i1", "^work", "^personal", "#me", "m0", "#work", "w0")
        fx(list.list.currentItem.map(_.key)) shouldBe Some(RichList.groupLineKey("work"))
        press(KeyCode.ENTER)
        groups() shouldBe Set.empty
        lines(list).size shouldBe 16
        // Left on the group's header closes it, Right opens it
        press(KeyCode.LEFT)
        groups() shouldBe Set("work")
        press(KeyCode.RIGHT)
        groups() shouldBe Set.empty
        // the cursor in a group that closes goes up to its header
        val b1 = at(list, "b1")
        fx(list.list.moveTo(b1))
        fx(list.collapseGroup("work"))
        fx(list.list.currentItem.map(_.key)) shouldBe Some(RichList.groupLineKey("work"))
        fx(list.expandGroup("work"))
        // the section named Work closes alone
        val alone = at(list, "#work")
        fx(list.list.moveTo(alone))
        press(KeyCode.ENTER)
        collapsed() shouldBe Set("work")
        groups() shouldBe Set.empty
        lines(list).takeRight(2) shouldBe List("m0", "#work")
        lines(list) should contain("#a")
      }
    }
    "remember which are closed in the app's Var, and close its sections within it as they were" in {
      val groups = Var(Set("personal"))
      val collapsed = Var(Set("a"))
      val list = fx(made(groups, collapsed))
      onScreen(list) {
        lines(list) shouldBe List("#inbox", "i0", "i1", "^work", "#a", "#b", "b0", "b1", "b2", "^personal",
          "#work", "w0")
        fx(groups @= Set("work"))
        lines(list) shouldBe List("#inbox", "i0", "i1", "^work", "^personal", "#me", "m0", "#work", "w0")
        // Alpha still closed once Work opens again
        fx(groups @= Set.empty)
        lines(list).slice(3, 6) shouldBe List("^work", "#a", "#b")
      }
    }
    "open a closed group to what the words find, with only the sections they find something in" in {
      val groups = Var(Set("work", "personal"))
      val list = fx(made(groups, search = true))
      onScreen(list) {
        fx(list.query @= "b")
        lines(list) shouldBe List("^work", "#b", "b0", "b1", "b2")
        fx(list.list(0).count) shouldBe 3
        fx(list.query @= "1")
        lines(list) shouldBe List("#inbox", "i1", "^work", "#a", "a1", "#b", "b1")
        fx(list.list(2).count) shouldBe 2
        fx(list.query @= "")
        groups() shouldBe Set("work", "personal")
        lines(list) shouldBe List("#inbox", "i0", "i1", "^work", "^personal", "#work", "w0")
      }
    }
    "open its menu from the menu key, Shift+F10, OK held, the secondary button and its ⋮, and none where it has none" in {
      var chose: List[String] = Nil
      val groups = Var(Set.empty[String])
      val list = fx(made(groups))
      fx(list.groupMenu @= ((g: RichGroup) => if (g.key == "personal") Nil else List(
        MenuItem("New in " + g.title, () => chose = chose :+ ("new " + g.key)), MenuItem("Rename", () => ()))))
      fx(list.sectionMenu @= ((s: RichSection[String]) => List(MenuItem("Section " + s.key, () => ()))))
      onScreen(list) {
        fx(list.list.moveTo(3))
        fx(row(list, 3).sectionMore.visible()) shouldBe true
        press(KeyCode.CONTEXT_MENU)
        entries shouldBe List("New in Work", "Rename")
        press(KeyCode.ENTER)
        chose shouldBe List("new work")
        press(KeyCode.F10, shift = true)
        entries shouldBe List("New in Work", "Rename")
        press(KeyCode.ESCAPE)
        key(KeyCode.ENTER, down = true)
        Thread.sleep((VirtualList.holdSeconds * 1000.0).toLong + 300L)
        eventually(Modal.showing) shouldBe true
        key(KeyCode.ENTER, down = false)
        entries shouldBe List("New in Work", "Rename")
        groups() shouldBe Set.empty
        press(KeyCode.ESCAPE)
        rightClick(list, fx(row(list, 3).absoluteTop) + 10.0) shouldBe true
        entries shouldBe List("New in Work", "Rename")
        press(KeyCode.ESCAPE)
        // the ⋮ opens it and folds nothing
        click(fx(row(list, 3).sectionMore))
        entries shouldBe List("New in Work", "Rename")
        groups() shouldBe Set.empty
        press(KeyCode.ESCAPE)
        // a section's header in the group still opens the section's
        fx(list.list.moveTo(4))
        press(KeyCode.CONTEXT_MENU)
        entries shouldBe List("Section a")
        press(KeyCode.ESCAPE)
        // Personal has none: no ⋮, nothing opened, and the platform's menu left
        val p = at(list, "^personal")
        fx(list.list.moveTo(p))
        fx(row(list, p).sectionMore.visible()) shouldBe false
        press(KeyCode.CONTEXT_MENU)
        fx(Modal.showing) shouldBe false
        rightClick(list, fx(row(list, p).absoluteTop) + 10.0) shouldBe false
        // its count in line with those of the headers that have one
        countEnd(row(list, p)) shouldBe (countEnd(row(list, 3)) +- 0.5)
        countEnd(row(list, 4)) shouldBe (countEnd(row(list, 3)) +- 0.5)
      }
    }
    "keep every header's count where it was in a list where no header has a menu" in {
      val list = fx(made())
      onScreen(list) {
        fx(list.headersHaveMenus()) shouldBe false
        Vector(0, 3, 4, 11).foreach(i => countEnd(row(list, i)) shouldBe (fx(list.size.width()) - RichRow.inset +- 0.5))
      }
    }
    "be mirrored right to left, set in from the right" in {
      val list = fx(made())
      fx(list.groupMenu @= ((_: RichGroup) => List(MenuItem("Rename", () => ()))))
      onScreen(list) {
        fx(Direction.set(true))
        fx(list.list.moveTo(3))
        val width = fx(list.size.width())
        val chevronRight = (r: RichRow[String]) => fx(all(r).collect { case v: everyui.component.MaterialIconView
          if v.visible() && v.icon() == MaterialIcon.ExpandMore => v.absoluteLeft + v.size.width() }.head)
        chevronRight(row(list, 3)) shouldBe (width - ListRow.inset +- 0.5)
        chevronRight(row(list, 4)) shouldBe (width - ListRow.inset - RichRow.nest +- 0.5)
        fx(row(list, 3).sectionMore.absoluteLeft) shouldBe (6.0 +- 0.5)
        // Left opens and Right closes, right to left
        press(KeyCode.RIGHT)
        fx(list.collapsedGroups()) shouldBe Set("work")
        press(KeyCode.LEFT)
        fx(list.collapsedGroups()) shouldBe Set.empty
      }
    }
    "drag within a section as before, and never into a closed group" in {
      val groups = Var(Set.empty[String])
      val list = fx(made(groups))
      var moves: List[RichMove] = Nil
      fx(list.moved.attach(m => moves = moves :+ m))
      fx(list.reorders @= true)
      onScreen(list) {
        val item = (s: String, id: String) => GroupedList.itemKey(s, id)
        def drop(k: String, target: String, at: DropPosition): Drop = Drop(list.list, List(k), Some(target), at)
        fx(list.list.dropped @= drop(item("b", "b0"), item("b", "b2"), DropPosition.After))
        fx(list.list.dropped @= drop(item("b", "b2"), item("b", "b0"), DropPosition.Before))
        // after Beta's header, its start; after Alpha's last, Alpha's end
        fx(list.list.dropped @= drop(item("b", "b2"), GroupedList.headerKey("b"), DropPosition.After))
        moves shouldBe List(RichMove(List("b0"), "b", "b", None), RichMove(List("b2"), "b", "b", Some("b0")),
          RichMove(List("b2"), "b", "b", Some("b0")))
        // where the app allows any: onto an open group's header is its first section's start, before it the end of the
        // section above, and before the first section's header its start
        fx(list.accepts @= ((_: RichMove) => true))
        fx(list.list.dropped @= drop(item("inbox", "i0"), RichList.groupLineKey("work"), DropPosition.After))
        fx(list.list.dropped @= drop(item("a", "a0"), RichList.groupLineKey("work"), DropPosition.Before))
        fx(list.list.dropped @= drop(item("inbox", "i0"), GroupedList.headerKey("a"), DropPosition.Before))
        moves.drop(3) shouldBe List(RichMove(List("i0"), "inbox", "a", Some("a0")),
          RichMove(List("a0"), "a", "inbox", None), RichMove(List("i0"), "inbox", "a", Some("a0")))
        // a closed group takes nothing, and a group's header is never dragged
        fx(groups @= Set("personal"))
        fx(list.list.accepts()(drop(item("a", "a0"), RichList.groupLineKey("personal"), DropPosition.After))) shouldBe false
        fx(list.list.accepts()(drop(item("a", "a0"), RichList.groupLineKey("personal"), DropPosition.Into))) shouldBe false
        fx(list.list.accepts()(drop(RichList.groupLineKey("work"), item("a", "a0"), DropPosition.Before))) shouldBe false
        // before the section after a closed group: that section's start, not into the group
        fx(list.list.dropped @= drop(item("a", "a0"), GroupedList.headerKey("work"), DropPosition.Before))
        moves.last shouldBe RichMove(List("a0"), "a", "work", Some("w0"))
      }
    }
  }
}
