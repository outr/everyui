package everyui.kit

import everyui.component.{Component, Container, Modal}
import everyui.component.types.Semantics
import everyui.event.{Pointer, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.menu.{MenuItem, MenuRow}
import everyui.{FxSpec, ui}
import reactify.*

/** The "+" after a tab bar's last tab (`TabBar.addMenu`, `TabGroups.addMenu`): none without a menu, the bar as it was;
  * just after the last tab, and kept at the bar's end while the tabs overflow before it; its menu dropped under it by a
  * click, Enter, Space and Down, made for the group whose "+" it is, its new tabs opening there; reached from the last
  * tab by the arrow onward and Tab, and back; its tooltip and name; right to left; in every group, new ones too, and
  * gone with its group. */
class TabAddSpec extends FxSpec {
  // a key press, with no pointer used just now (a window under Xvfb hears the pointer as nodes come and go under it,
  // and Menu.below gives the focus back to what had it before a press made by a pointer)
  private def press(key: String): Unit = fx {
    Pointer.usedAt = -1.0e9
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private def focusedLabel: Option[String] = fx(Focus.focused().collect { case r: MenuRow => r.item.label })

  private def clean(): Unit = fx {
    while (Modal.showing) Modal.close()
    ui.children.clear()
    Focus.focused @= None
  }

  /** A bar `width` wide with `n` tabs, on the screen. */
  private def barOf(width: Double, n: Int): TabBar = fx {
    ui.children.clear()
    val bar = new TabBar(Var("t0"), width, showOnFocus = false)
    (0 until n).foreach(i => bar.add("t" + i.toString, "Tab number " + i.toString, closable = true))
    bar.position.left @= 100.0
    bar.position.top @= 50.0
    ui.children += bar
    bar
  }

  private def scrollOf(bar: TabBar): Component = bar.childComponents.find(_.kind == "ScrollContainer").get

  "A tab bar's +" should {
    "not be there without a menu, the bar as it was, and sit just after the last tab with one" in {
      val bar = barOf(1000.0, 3)
      try {
        fx(bar.addButton) shouldBe None
        fx(bar.childComponents.size) shouldBe 3
        fx(scrollOf(bar).size.width()) shouldBe 1000.0
        fx(bar.openAddMenu())
        fx(Modal.showing) shouldBe false
        var built = 0
        fx(bar.addMenu @= Some(() => {
          built += 1
          List(MenuItem("New file"), MenuItem("New terminal"))
        }))
        val plus = fx(bar.addButton.get)
        val last = fx(bar.tabs().last)
        eventually(plus.absoluteLeft == last.absoluteLeft + last.size.width() + TabBar.divider) shouldBe true
        fx(plus.absoluteTop) shouldBe fx(bar.absoluteTop)
        fx(plus.size.height()) shouldBe TabBar.height
        fx(plus.size.width()) shouldBe TabAdd.width
        // the rule runs under it too
        fx(bar.childComponents.head.size.width()) shouldBe fx(last.absoluteLeft + last.size.width() - bar.absoluteLeft +
          TabBar.divider + TabAdd.width)
        // a tab more and it moves along
        fx(bar.add("t9", "Another"))
        eventually(plus.absoluteLeft == bar.tabs().last.absoluteLeft + bar.tabs().last.size.width() + TabBar.divider)
          .shouldBe(true)
        built shouldBe 0
        // and taken off again with the menu, the bar as it was
        fx(bar.addMenu @= None)
        fx(bar.addButton) shouldBe None
        fx(bar.childComponents.size) shouldBe 3
        fx(scrollOf(bar).size.width()) shouldBe 1000.0
      }
      finally clean()
    }

    "stay at the bar's end while the tabs overflow, the tabs scrolling before it" in {
      val bar = barOf(400.0, 8)
      try {
        fx(bar.addMenu @= Some(() => List(MenuItem("New"))))
        val plus = fx(bar.addButton.get)
        val scroll = fx(scrollOf(bar))
        eventually(plus.absoluteLeft == 100.0 + 400.0 - TabAdd.width) shouldBe true
        fx(scroll.size.width()) shouldBe 400.0 - TabAdd.width - TabBar.divider
        fx(bar.tabs().last.absoluteLeft) should be > 400.0
        // the last tab shown is scrolled into view before the +, which stays put
        fx(bar.tabs().last.focus())
        eventually {
          val t = bar.tabs().last
          t.absoluteLeft + t.size.width() <= plus.absoluteLeft + 0.5
        } shouldBe true
        fx(plus.absoluteLeft) shouldBe 100.0 + 400.0 - TabAdd.width
      }
      finally clean()
    }

    "be set apart from the last tab by a short line, gone while the last tab is shown, and never a stop" in {
      val bar = barOf(1000.0, 3)
      try {
        def line: Option[Component] =
          bar.childComponents.find(c => c.size.width() == 1.0 && c.size.height() < TabBar.height)
        fx(line.exists(_.visible())) shouldBe false
        fx(bar.addMenu @= Some(() => List(MenuItem("New"))))
        val plus = fx(bar.addButton.get)
        val last = fx(bar.tabs().last)
        eventually(plus.absoluteLeft == last.absoluteLeft + last.size.width() + TabBar.divider) shouldBe true
        val divider = fx(line.get)
        fx(divider.visible()) shouldBe true
        fx(divider.absoluteLeft) should (be > fx(last.absoluteLeft + last.size.width()) and be < fx(plus.absoluteLeft))
        fx(divider.size.height()) shouldBe (TabBar.height * 0.6) +- 0.01
        fx(divider.absoluteTop - bar.absoluteTop) shouldBe (TabBar.height * 0.2) +- 0.01
        fx(divider.focusable()) shouldBe false
        // beside a last tab under the pointer, or shown, its edge is not doubled
        fx(last.pointerOver @= true)
        fx(divider.visible()) shouldBe false
        fx(last.pointerOver @= false)
        fx(divider.visible()) shouldBe true
        fx(last.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        fx(divider.visible()) shouldBe false
        fx(bar.tabs().head.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        fx(divider.visible()) shouldBe true
      }
      finally clean()
    }

    "be called by its label, its tooltip and its name to a screen reader" in {
      val bar = barOf(1000.0, 2)
      try {
        fx(bar.addMenu @= Some(() => List(MenuItem("New"))))
        val plus = fx(bar.addButton.get)
        fx(plus.tooltip()) shouldBe "New…"
        fx(plus.semantics()) shouldBe Semantics.button("New…")
        fx(bar.addLabel @= "New tab")
        fx(plus.tooltip()) shouldBe "New tab"
        fx(plus.semantics()) shouldBe Semantics.button("New tab")
        fx(plus.focusable()) shouldBe true
        fx(plus.tapSlop()) shouldBe true
      }
      finally clean()
    }

    "drop its menu under it from a click, Enter, Space and Down, made afresh, and give the focus back as it closes" in {
      val bar = barOf(1000.0, 3)
      try {
        var did: List[String] = Nil
        var built = 0
        fx(bar.addMenu @= Some(() => {
          built += 1
          List(MenuItem("New file " + built.toString, () => did = did :+ "file"), MenuItem("New terminal"))
        }))
        val plus = fx(bar.addButton.get)
        fx(plus.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        focusedLabel shouldBe Some("New file 1")
        val menu = fx(Modal.top.get)
        fx(menu.position.top()) shouldBe fx(plus.absoluteTop + plus.size.height() + 4.0)
        fx(menu.position.left()) shouldBe fx(plus.absoluteLeft)
        press("Escape")
        fx(Modal.showing) shouldBe false
        // the keys: from the last tab onward to it, and back; Tab too
        fx(bar.tabs().last.focus())
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(plus)
        press("ArrowLeft")
        fx(Focus.focused()) shouldBe Some(bar.tabs().last)
        press("Tab")
        fx(Focus.focused()) shouldBe Some(plus)
        // Enter, Space and Down each open it, made afresh, and Escape gives the focus back to it
        List("Enter", " ", "ArrowDown").zipWithIndex.foreach { case (k, i) =>
          press(k)
          focusedLabel shouldBe Some("New file " + (i + 2).toString)
          press("Escape")
          fx(Modal.showing) shouldBe false
          fx(Focus.focused()) shouldBe Some(plus)
        }
        // chosen, it does its work
        press("Enter")
        press("Enter")
        did shouldBe List("file")
        fx(Modal.showing) shouldBe false
      }
      finally clean()
    }

    "follow the last tab right to left, reached by Left and left by Right" in {
      fx(Direction.set(true))
      try {
        val bar = barOf(1000.0, 3)
        fx(bar.addMenu @= Some(() => List(MenuItem("New"))))
        val plus = fx(bar.addButton.get)
        val last = fx(bar.tabs().last)
        eventually(plus.absoluteLeft + TabAdd.width + TabBar.divider == last.absoluteLeft) shouldBe true
        fx(bar.tabs().head.absoluteLeft + bar.tabs().head.size.width()) shouldBe 1100.0
        fx(last.focus())
        press("ArrowLeft")
        fx(Focus.focused()) shouldBe Some(plus)
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(last)
        // the menu dropped from its right edge, the way the page reads
        fx(bar.openAddMenu())
        val menu = fx(Modal.top.get)
        fx(menu.position.left() + menu.size.width()) shouldBe fx(plus.absoluteLeft + TabAdd.width) +- 0.5
        fx(while (Modal.showing) Modal.close())
        // overflowing, at the bar's left end
        val wide = barOf(400.0, 8)
        fx(wide.addMenu @= Some(() => List(MenuItem("New"))))
        eventually(wide.addButton.get.absoluteLeft == 100.0) shouldBe true
        fx(scrollOf(wide).absoluteLeft) shouldBe 100.0 + TabAdd.width + TabBar.divider
      }
      finally {
        clean()
        fx(Direction.set(false))
      }
    }

    "be disposed of with its bar" in {
      val bar = barOf(1000.0, 2)
      fx(bar.addMenu @= Some(() => List(MenuItem("New"))))
      val plus = fx(bar.addButton.get)
      fx(bar.addMenu @= None)
      fx(bar.dispose())
      fx(plus.takenApart) shouldBe true
      clean()
    }
  }

  "Each group's +" should {
    def groupsOf(): TabGroups[String] = fx {
      ui.children.clear()
      val g = new TabGroups[String](k => k, _ => new Container, k => k)
      g.size.width @= 1200.0
      g.size.height @= 700.0
      ui.children += g
      g
    }

    def pin(): Container = new Container {
      size.width @= 30.0
      size.height @= 30.0
      focusable @= true
    }

    "take the app's own controls after it, the divider before them all, on a bar and in every group" in {
      val bar = barOf(400.0, 8)
      try {
        val mine = fx(pin())
        // without a "+": the divider and then the app's own, at the bar's end, the tabs scrolling before them
        fx(bar.trailing @= List(mine))
        val scroll = fx(scrollOf(bar))
        eventually(mine.absoluteLeft == 100.0 + 400.0 - 30.0) shouldBe true
        fx(scroll.size.width()) shouldBe 400.0 - 30.0 - TabBar.divider
        fx(mine.absoluteTop - bar.absoluteTop) shouldBe (TabBar.height - 30.0) / 2.0
        // with one: the "+", then the app's own
        fx(bar.addMenu @= Some(() => List(MenuItem("New"))))
        val plus = fx(bar.addButton.get)
        eventually(plus.absoluteLeft == 100.0 + 400.0 - 30.0 - TabAdd.width) shouldBe true
        fx(mine.absoluteLeft) shouldBe 100.0 + 400.0 - 30.0
        fx(scroll.size.width()) shouldBe 400.0 - 30.0 - TabAdd.width - TabBar.divider
        // taken off again
        fx(bar.trailing @= Nil)
        fx(bar.childComponents.exists(_ eq mine)) shouldBe false
        eventually(plus.absoluteLeft == 100.0 + 400.0 - TabAdd.width) shouldBe true
      }
      finally clean()
      val g = groupsOf()
      try {
        fx(List("a", "b").foreach(k => g.open(k)))
        fx(g.trailing @= ((_: TabGroup[String]) => List(pin())))
        val first = fx(g.groups().head)
        fx(g.openRight(first, "b"))
        val second = fx(g.groups()(1))
        val (one, two) = fx((first.bar.trailing().head, second.bar.trailing().head))
        fx(one eq two) shouldBe false
        fx(first.bar.childComponents.exists(_ eq one) && second.bar.childComponents.exists(_ eq two)) shouldBe true
      }
      finally clean()
    }

    "be in every group, new ones too, its menu made for that group and opening its new tabs there" in {
      val g = groupsOf()
      try {
        fx(List("a", "b", "c").foreach(k => g.open(k)))
        fx(g.groups().head.bar.addButton) shouldBe None
        var asked: List[TabGroup[String]] = Nil
        fx(g.addMenu @= Some((group: TabGroup[String]) => {
          asked = asked :+ group
          List(MenuItem("New file", () => g.openIn(group, "new-" + g.groups().indexWhere(_ eq group).toString)))
        }))
        val first = fx(g.groups().head)
        fx(first.bar.addButton.nonEmpty) shouldBe true
        // a group split off has one too, and the other group's is its own
        fx(g.openRight(first, "c"))
        val second = fx(g.groups()(1))
        val plus = fx(second.bar.addButton.get)
        fx(plus eq first.bar.addButton.get) shouldBe false
        val end = fx(second.bar.tabs().last)
        eventually(plus.absoluteLeft == end.absoluteLeft + end.size.width() + TabBar.divider) shouldBe true
        fx(first.focusTab())
        fx(plus.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        asked.last shouldBe second
        press("Enter")
        fx(g.groups().map(_.keys().toList)) shouldBe List(List("a", "b"), List("c", "new-1"))
        fx(second.selected()) shouldBe Some("new-1")
        fx(Focus.focused()) shouldBe fx(second.bar("new-1"))
        // the label goes to every group's
        fx(g.addLabel @= "New tab")
        fx(g.groups().map(_.bar.addButton.get.tooltip())) shouldBe List("New tab", "New tab")
        // and by the keys: from the last tab of the first group onward, Down opening it there
        fx(first.bar.tabs().last.focus())
        press("ArrowRight")
        fx(Focus.focused()) shouldBe fx(first.bar.addButton)
        press("ArrowDown")
        asked.last shouldBe first
        press("Enter")
        fx(first.keys().toList) shouldBe List("a", "b", "new-0")
        // a group that closes takes its + with it
        fx(g.closeAll(second))
        fx(g.groups().size) shouldBe 1
        fx(plus.takenApart) shouldBe true
        // and with no menu, no group has one
        fx(g.addMenu @= None)
        fx(g.groups().head.bar.addButton) shouldBe None
        fx(g.openDown(first, "a"))
        fx(g.groups().map(_.bar.addButton)) shouldBe List(None, None)
      }
      finally clean()
    }

    "be disposed of with the groups" in {
      val g = groupsOf()
      fx(g.open("a"))
      fx(g.addMenu @= Some((_: TabGroup[String]) => List(MenuItem("New"))))
      val plus = fx(g.groups().head.bar.addButton.get)
      fx {
        ui.children.clear()
        g.dispose()
      }
      fx(plus.takenApart) shouldBe true
      clean()
    }
  }
}
