package everyui.kit

import everyui.component.{Component, Container, Modal}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.keymap.Keymap
import everyui.menu.MenuItem
import everyui.split.SplitPane
import everyui.store.FabricCodec
import everyui.{FxSpec, ui}
import fabric.rw.*
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** Tabs in groups: the menu on a tab (by the keys and the app's own entries), Open Right and Down, moving, closing,
  * unsplitting; each tab's content kept while it is open, wherever it moves, and disposed of once it closes; a key in
  * two groups where the app allows it, moved where not; tabs dragged into another group and onto a group's edge; the
  * focused group and `open`; the layout kept and restored, as JSON and through fabric; an empty group closing, the last
  * staying; and right to left. */
class TabGroupsSpec extends FxSpec {
  /** What each tab shows: a box named for its key and which view it is, and which have been disposed of. */
  private class Views {
    var made: List[(String, Component)] = Nil
    var disposed: List[String] = Nil
    def make(k: String): Component = {
      val c = new Container
      made = made :+ (k -> c)
      val n = made.size
      c.onDispose(() => disposed = disposed :+ (k + "#" + n.toString))
      c
    }
    def count(k: String): Int = made.count(_._1 == k)
  }

  private def groupsOf(views: Views, twice: String => Boolean = _ => false): TabGroups[String] = fx {
    val g = new TabGroups[String](k => k, views.make, k => k, allowsTwice = twice)
    g.size.width @= 1200.0
    g.size.height @= 700.0
    g
  }

  private def shown[T](g: TabGroups[String])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += g
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def press(code: KeyCode, shift: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, shift, false, false, false))
  }

  private def labels(items: List[MenuItem]): List[String] = items.filterNot(_.separator).map(_.label)
  private def run(items: List[MenuItem], label: String): Unit = items.find(_.label == label).get.action()
  private def keysOf(g: TabGroups[String]): List[List[String]] = fx(g.groups().map(_.keys().toList))

  private def opened(g: TabGroups[String], keys: String*): Unit = fx(keys.foreach(k => g.open(k)))

  "A tab's menu" should {
    "offer the groups' own entries and then the app's, and open from the menu key and Shift+F10" in {
      val views = new Views
      val g = groupsOf(views)
      shown(g) {
        opened(g, "a", "b")
        fx(g.tabMenu @= (k => List(MenuItem("Reveal " + k))))
        val first = fx(g.groups().head)
        labels(fx(g.menuFor(first, "b"))) shouldBe List("Close", "Close Others", "Close All", "Open Right", "Open Down",
          "Move Right", "Move Left", "Move Down", "Move Up", "Reveal b")
        // a split adds Unsplit, and a lone tab moves only where there is a group that way
        fx(g.openRight(first, "b"))
        labels(fx(g.menuFor(first, "a"))) shouldBe List("Close", "Close Others", "Close All", "Open Right", "Open Down",
          "Move Right", "Move Left", "Move Down", "Move Up", "Unsplit", "Reveal a")
        fx(g.menuFor(first, "a").filter(_.label.startsWith("Move")).map(i => i.label -> i.enabled)) shouldBe List(
          "Move Right" -> true, "Move Left" -> false, "Move Down" -> false, "Move Up" -> false)
        labels(fx(g.menuFor(g.groups()(1), "b"))) should contain("Move Left")
        // the keys open it on the focused tab
        fx(first.focusTab())
        press(KeyCode.CONTEXT_MENU)
        fx(Modal.showing) shouldBe true
        fx(while (Modal.showing) Modal.close())
        fx(first.focusTab())
        press(KeyCode.F10, shift = true)
        fx(Modal.showing) shouldBe true
      }
    }

    "keep the browser's own menu where the app and the groups have none: a bare TabBar's tabs have none" in {
      val bar = fx(new TabBar(Var("a"), 400.0))
      fx(bar.add("a", "A"))
      fx(bar.tabMenu()("a")) shouldBe Nil
      fx(bar.openMenu("a"))
      fx(Modal.showing) shouldBe false
    }
  }

  "Open Right and Open Down" should {
    "move a tab into a new group after or below, keeping its content, and leave a group's only tab alone" in {
      val views = new Views
      val g = groupsOf(views)
      shown(g) {
        opened(g, "a", "b", "c")
        val first = fx(g.groups().head)
        val c = fx(first.contentOf("c")).get
        fx(g.openRight(first, "c"))
        keysOf(g) shouldBe List(List("a", "b"), List("c"))
        fx(g.groups()(1).contentOf("c")) shouldBe Some(c)
        views.count("c") shouldBe 1
        views.disposed shouldBe Nil
        val split = fx(g.childComponents.head.asInstanceOf[SplitPane])
        split.vertical shouldBe false
        // its group is right of the first, and focused
        fx(g.groups()(1).absoluteLeft) should be > fx(first.absoluteLeft)
        fx(g.focusedGroup()) shouldBe fx(g.groups()(1))
        // down from the first
        fx(g.openDown(first, "b"))
        keysOf(g) shouldBe List(List("a"), List("b"), List("c"))
        fx(g.groups()(1).absoluteTop) should be > fx(first.absoluteTop)
        // the only tab of a group, which may not show twice, stays
        fx(g.openRight(first, "a"))
        keysOf(g) shouldBe List(List("a"), List("b"), List("c"))
        fx(g.menuFor(first, "a").find(_.label == "Open Right").get.enabled) shouldBe false
      }
    }

    "open a second view where the key may show twice, each with its own content, disposed of apart" in {
      val views = new Views
      val g = groupsOf(views, twice = _ => true)
      shown(g) {
        opened(g, "a")
        val first = fx(g.groups().head)
        fx(g.openRight(first, "a"))
        keysOf(g) shouldBe List(List("a"), List("a"))
        views.count("a") shouldBe 2
        fx(g.groups()(1).contentOf("a")) should not be fx(first.contentOf("a"))
        fx(g.close(g.groups()(1), "a"))
        views.disposed shouldBe List("a#2")
        keysOf(g) shouldBe List(List("a"))
        // open() in the focused group, where it shows already
        fx(g.open("a"))
        keysOf(g) shouldBe List(List("a"))
      }
    }
  }

  "Tabs" should {
    "keep their content while open and alive off the screen, and dispose of it once closed" in {
      val views = new Views
      val g = groupsOf(views)
      shown(g) {
        opened(g, "a", "b")
        val first = fx(g.groups().head)
        // made as it is first shown, kept when another is shown
        views.made.map(_._1) shouldBe List("a", "b")
        fx(first.select("a"))
        views.made.size shouldBe 2
        fx(first.body.children().exists(_ eq first.contentOf("a").get)) shouldBe true
        fx(first.body.children().exists(_ eq first.contentOf("b").get)) shouldBe false
        fx(g.close(first, "b"))
        views.disposed shouldBe List("b#2")
        // moved to another group and back: the same content, never made again
        opened(g, "c")
        fx(g.openRight(first, "c"))
        val c = fx(g.groups()(1).contentOf("c")).get
        fx(g.move(g.groups()(1), "c", first))
        fx(first.contentOf("c")) shouldBe Some(c)
        views.count("c") shouldBe 1
        // Close Others and Close All
        opened(g, "d")
        fx(g.closeOthers(first, "a"))
        keysOf(g) shouldBe List(List("a"))
        views.disposed.toSet shouldBe Set("b#2", "c#3", "d#4")
        fx(g.closeAll(first))
        keysOf(g) shouldBe List(Nil)
        views.disposed should contain("a#1")
        // and taken apart with the groups
        opened(g, "e")
        fx(g.dispose())
        views.disposed should contain("e#5")
      }
    }

    "move to the group beside, unsplit into it, and an empty group close, the last staying" in {
      val views = new Views
      val g = groupsOf(views)
      shown(g) {
        opened(g, "a", "b", "c")
        val first = fx(g.groups().head)
        fx(g.openRight(first, "c"))
        fx(run(g.menuFor(first, "b"), "Move Right"))
        keysOf(g) shouldBe List(List("a"), List("c", "b"))
        fx(g.groups()(1).selected()) shouldBe Some("b")
        // the last tab of a group moved away: the group closes, the focus on where it went
        fx(run(g.menuFor(first, "a"), "Move Right"))
        keysOf(g) shouldBe List(List("c", "b", "a"))
        fx(g.childComponents.head.isInstanceOf[TabGroup[?]]) shouldBe true
        fx(Focus.focused().exists(f => Focus.within(f, g.groups().head))) shouldBe true
        // unsplit puts the tabs back
        val only = fx(g.groups().head)
        fx(g.openDown(only, "a"))
        fx(run(g.menuFor(g.groups()(1), "a"), "Unsplit"))
        keysOf(g) shouldBe List(List("c", "b", "a"))
        views.disposed shouldBe Nil
        // closing the last tab of the last group leaves it, empty, the focus on what it says
        fx(g.closeAll(g.groups().head))
        keysOf(g) shouldBe List(Nil)
        fx(Focus.focused().exists(f => Focus.within(f, g.groups().head))) shouldBe true
      }
    }

    "split a new group off where there is none that way, and leave a group's only tab where it would only make it again" in {
      val views = new Views
      val g = groupsOf(views, twice = _ == "c")
      shown(g) {
        var told: List[TabLayout] = Nil
        fx(g.layoutChanged.attach(l => told = told :+ l))
        opened(g, "a", "b", "c", "d")
        val first = fx(g.groups().head)
        val c = fx(first.contentOf("c")).get
        // nowhere right: a new group after it, the tab taken along (a key that may show twice moved, not copied)
        told = Nil
        fx(run(g.menuFor(first, "c"), "Move Right"))
        keysOf(g) shouldBe List(List("a", "b", "d"), List("c"))
        fx(g.groups()(1).contentOf("c")) shouldBe Some(c)
        views.count("c") shouldBe 1
        fx(g.groups()(1).absoluteLeft) should be > fx(first.absoluteLeft)
        fx(g.focusedGroup()) shouldBe fx(g.groups()(1))
        fx(Focus.focused().exists(f => Focus.within(f, g.groups()(1)))) shouldBe true
        told.last.parts.map(_.tabs) shouldBe List(List("a", "b", "d"), List("c"))
        // nowhere down from the first: a new group below it
        fx(run(g.menuFor(first, "b"), "Move Down"))
        keysOf(g) shouldBe List(List("a", "d"), List("b"), List("c"))
        fx(g.groups()(1).absoluteTop) should be > fx(first.absoluteTop)
        fx(g.groups()(1).absoluteLeft) shouldBe fx(first.absoluteLeft)
        // nowhere left of the first: a new group before it, in the first's place
        fx(run(g.menuFor(first, "d"), "Move Left"))
        keysOf(g) shouldBe List(List("d"), List("a"), List("b"), List("c"))
        fx(g.groups().head.absoluteLeft) should be < fx(first.absoluteLeft)
        // a group's only tab, with nowhere up: Move Up is shown, not enabled, and does nothing
        val upper = fx(g.groups()(1))
        fx(g.menuFor(upper, "a").find(_.label == "Move Up").map(_.enabled)) shouldBe Some(false)
        fx(g.moveBeside(upper, "a", vertical = true, after = false))
        keysOf(g) shouldBe List(List("d"), List("a"), List("b"), List("c"))
        // but down, where b's group is, it goes, and its group closes
        fx(run(g.menuFor(upper, "a"), "Move Down"))
        keysOf(g) shouldBe List(List("d"), List("b", "a"), List("c"))
        fx(Focus.focused().exists(f => Focus.within(f, g.groups()(1)))) shouldBe true
        views.disposed shouldBe Nil
      }
    }

    "drag into another group, among its tabs, and onto a group's edge to split it there" in {
      val views = new Views
      val g = groupsOf(views)
      shown(g) {
        opened(g, "a", "b", "c")
        val first = fx(g.groups().head)
        fx(g.openRight(first, "c"))
        val second = fx(g.groups()(1))
        val b = fx(first.contentOf("b")).get
        // into the second, before its tab
        val c = fx(second.bar("c").get)
        val (cx, cy) = fx((c.absoluteLeft + 4.0, c.absoluteTop + 10.0))
        fx(g.press(first, "b", 30.0, 15.0))
        fx(g.dragTo(cx, cy))
        fx(g.landingAt(cx, cy).map(l => (l._2, l._3))) shouldBe Some((TabDropZone.Into, 0))
        fx(g.drop(cx, cy))
        keysOf(g) shouldBe List(List("a"), List("b", "c"))
        fx(second.contentOf("b")) shouldBe Some(b)
        // onto the second's bottom edge: a group under it
        val (bx, by) = fx((second.absoluteLeft + second.size.width() / 2.0, second.absoluteTop + second.size.height() - 20.0))
        fx(g.landingAt(bx, by).map(_._2)) shouldBe Some(TabDropZone.Bottom)
        fx(g.press(second, "c", cx, cy))
        fx(g.dragTo(bx, by))
        fx(g.drop(bx, by))
        keysOf(g) shouldBe List(List("a"), List("b"), List("c"))
        fx(g.groups()(2).absoluteTop) should be > fx(g.groups()(1).absoluteTop)
        // onto the first's start edge
        val (sx, sy) = fx((first.absoluteLeft + 10.0, first.absoluteTop + 300.0))
        fx(g.landingAt(sx, sy).map(_._2)) shouldBe Some(TabDropZone.Start)
        fx(g.press(g.groups()(1), "b", 0.0, 0.0))
        fx(g.dragTo(sx, sy))
        fx(g.drop(sx, sy))
        keysOf(g) shouldBe List(List("b"), List("a"), List("c"))
        // a press let go where it was shows the tab rather than moving it
        opened(g, "d")
        val d = fx(g.focusedGroup())
        val shownBefore = fx(d.keys().toList)
        fx(d.select(shownBefore.head))
        fx(g.press(d, "d", 5.0, 5.0))
        fx(g.drop(6.0, 6.0))
        fx(d.selected()) shouldBe Some("d")
        fx(d.keys().toList) shouldBe shownBefore
        views.disposed shouldBe Nil
      }
    }
  }

  "The focused group" should {
    "follow the focus, take what open() opens, and show what is open elsewhere there rather than twice" in {
      val views = new Views
      val g = groupsOf(views)
      shown(g) {
        opened(g, "a", "b")
        val first = fx(g.groups().head)
        fx(g.openRight(first, "b"))
        val second = fx(g.groups()(1))
        fx(g.focusedGroup()) shouldBe second
        fx(first.focusTab())
        fx(g.focusedGroup()) shouldBe first
        fx(g.open("c"))
        keysOf(g) shouldBe List(List("a", "c"), List("b"))
        // b shows in the second already, and may not show twice: shown there
        fx(g.open("b"))
        keysOf(g) shouldBe List(List("a", "c"), List("b"))
        fx(g.focusedGroup()) shouldBe second
        fx(Focus.focused().exists(f => Focus.within(f, second))) shouldBe true
        // the keyboard's next group, and close
        val keys = fx(new Keymap)
        val commands = fx(g.commands(keys))
        commands.map(_.id) shouldBe List("tabs.openRight", "tabs.openDown", "tabs.nextGroup", "tabs.previousGroup",
          "tabs.close")
        fx(keys("tabs.nextGroup").get.run())
        fx(g.focusedGroup()) shouldBe first
        fx(keys("tabs.close").get.run())
        keysOf(g) shouldBe List(List("a"), List("b"))
        fx(keys("tabs.openDown").get.run())
        keysOf(g) shouldBe List(List("a"), List("b"))
        fx(first.select("a"))
        opened(g, "e")
        fx(g.focusedGroup()) shouldBe first
        fx(keys("tabs.openDown").get.run())
        keysOf(g) shouldBe List(List("a"), List("e"), List("b"))
      }
    }
  }

  "The layout" should {
    "be told as it changes, and come back the same, as JSON and through fabric" in {
      val views = new Views
      val g = groupsOf(views, twice = _ => true)
      shown(g) {
        var told: List[TabLayout] = Nil
        fx(g.layoutChanged.attach(l => told = l :: told))
        opened(g, "a", "b", "c")
        val first = fx(g.groups().head)
        fx(g.openRight(first, "c"))
        fx(g.openDown(g.groups()(1), "c"))
        val split = fx(g.childComponents.head.asInstanceOf[SplitPane])
        fx(split.ratio @= 0.3)
        val l = fx(g.tabLayout)
        told.head shouldBe l
        l.isGroup shouldBe false
        l.ratio shouldBe 0.3
        l.groups.map(_.tabs) shouldBe List(List("a", "b", "c"), List("c"), List("c"))
        TabLayout.parse(l.json) shouldBe Some(l)
        TabLayout.parse("{\"tabs\":[\"x \\\"y\\\"\"],\"other\":{\"q\":[1,2]},\"selected\":\"x \\\"y\\\"\"}") shouldBe
          Some(TabLayout(List("x \"y\""), "x \"y\""))
        TabLayout.parse("not json") shouldBe None
        List(0.4822605965463108, 1.0e-4, 0.1, 2.0 / 3.0).foreach { r =>
          TabLayout.parse(TabLayout(ratio = r).json).map(_.ratio) shouldBe Some(r)
        }
        given RW[TabLayout] = RW.gen
        val fabric = FabricCodec[TabLayout]
        fabric.decode(fabric.encode(l)) shouldBe Some(l)
        // restored in another, a tab it no longer knows left out
        val other = groupsOf(new Views, twice = _ => true)
        fx {
          ui.children += other
          other.restore(l.copy(parts = l.parts :+ TabLayout(List("gone"))), k => if (k == "gone") None else Some(k))
        }
        fx(other.tabLayout) shouldBe l
        fx(other.groups().map(_.selected())) shouldBe List(Some("c"), Some("c"), Some("c"))
      }
    }
  }

  "Right to left" should {
    "open right on the trailing side, the left, and take a drag's start edge from the right" in {
      fx(Direction.set(true))
      try {
        val views = new Views
        val g = groupsOf(views)
        shown(g) {
          opened(g, "a", "b", "c")
          val first = fx(g.groups().head)
          fx(g.openRight(first, "b"))
          fx(g.groups()(1).absoluteLeft) should be < fx(first.absoluteLeft)
          labels(fx(g.menuFor(first, "a"))) should contain("Move Right")
          // Move Left, with nowhere before it, splits a group off on the right, as Right is after by the reading direction
          fx(g.moveBeside(first, "c", vertical = false, after = false))
          keysOf(g) shouldBe List(List("c"), List("a"), List("b"))
          fx(g.groups().head.absoluteLeft) should be > fx(first.absoluteLeft)
          fx(g.focusedGroup()) shouldBe fx(g.groups().head)
          val (x, y) = fx((first.absoluteLeft + first.size.width() - 10.0, first.absoluteTop + 300.0))
          fx(g.landingAt(x, y).map(_._2)) shouldBe Some(TabDropZone.Start)
        }
      }
      finally fx(Direction.set(false))
    }
  }
}
