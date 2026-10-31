package everyui.kit

import everyui.async.Task
import everyui.component.{Button, Component, Container}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.menu.MenuItem
import everyui.{FxSpec, ui}
import reactify.*

/** Groups kept as empty panes (`TabGroups.keepEmptyGroups`): a group's last tab closed, or moved away, leaves it, its
  * words and a button closing it, the focus on the button; a tab moved into it fills it again; the button closes it,
  * asking `canClose` of any tabs first, and is not there for the last group; the layout keeps the empty group and comes
  * back with it; and without the option, a group left empty closes as ever. */
class TabEmptyGroupSpec extends FxSpec {
  private def groupsOf(keep: Boolean): TabGroups[String] = fx {
    val g = new TabGroups[String](k => k, _ => new Container, k => k)
    g.keepEmptyGroups @= keep
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
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def keysOf(g: TabGroups[String]): List[List[String]] = fx(g.groups().map(_.keys().toList))
  /** The group's button closing it, where it shows. */
  private def closer(g: TabGroup[String]): Option[Button] = g.childComponents.collectFirst {
    case b: Button if b.visible() => b
  }
  private def focusedIn(c: Component): Boolean = Focus.focused().exists(f => Focus.within(f, c))

  /** "a" and "b" in the first group, "c" in a second after it. */
  private def two(g: TabGroups[String]): Unit = fx {
    List("a", "b", "c").foreach(k => g.open(k))
    g.openRight(g.groups().head, "c")
  }

  "An empty group" should {
    "stay as a pane once its last tab closes or moves away, the focus on its button, and fill again" in {
      val g = groupsOf(keep = true)
      shown(g) {
        two(g)
        val second = fx(g.groups()(1))
        fx(closer(second)) shouldBe None
        fx(g.close(second, "c"))
        keysOf(g) shouldBe List(List("a", "b"), Nil)
        fx(g.groups()(1)) should be theSameInstanceAs second
        fx(closer(second).map(_.label())) shouldBe Some(TabGroups.closeGroupWord)
        fx(Focus.focused() == closer(second)) shouldBe true
        // a tab moved in fills it, the button gone; moved away again, it is empty again
        fx(g.move(g.groups().head, "a", second))
        keysOf(g) shouldBe List(List("b"), List("a"))
        fx(closer(second)) shouldBe None
        fx(g.move(second, "a", g.groups().head))
        keysOf(g) shouldBe List(List("b", "a"), Nil)
        fx(closer(second).nonEmpty) shouldBe true
        // the first group's tabs all closed: both kept, the last with no button
        fx(g.closeAll(g.groups().head))
        keysOf(g) shouldBe List(Nil, Nil)
        fx(closer(second).get.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        keysOf(g) shouldBe List(Nil)
        fx(closer(g.groups().head)) shouldBe None
        fx(focusedIn(g.groups().head)) shouldBe true
      }
    }

    "close by its button, or closeGroup, asking canClose of any tabs it still has first" in {
      val g = groupsOf(keep = true)
      shown(g) {
        two(g)
        val second = fx(g.groups()(1))
        fx(g.canClose @= (_ => Task.pure(false)))
        fx(g.closeGroup(second))
        keysOf(g) shouldBe List(List("a", "b"), List("c"))
        fx(g.canClose @= (_ => Task.pure(true)))
        fx(g.closeGroup(second))
        keysOf(g) shouldBe List(List("a", "b"))
        // the last group stays, closed or not
        fx(g.closeGroup(g.groups().head))
        keysOf(g) shouldBe List(Nil)
      }
    }

    "be kept in the layout and come back with it, and left out of one restored without the option" in {
      val g = groupsOf(keep = true)
      shown(g) {
        two(g)
        fx(g.close(g.groups()(1), "c"))
        val l = fx(g.tabLayout)
        l.groups.map(_.tabs) shouldBe List(List("a", "b"), Nil)
        TabLayout.parse(l.json) shouldBe Some(l)
        val kept = groupsOf(keep = true)
        fx {
          ui.children += kept
          kept.restore(l, k => Some(k))
        }
        fx(kept.tabLayout) shouldBe l
        // a group none of whose tabs is found is kept too, empty
        fx(kept.restore(l.copy(parts = List(l.parts.head, TabLayout(List("gone")))),
          k => if (k == "gone") None else Some(k)))
        keysOf(kept) shouldBe List(List("a", "b"), Nil)
        val plain = groupsOf(keep = false)
        fx {
          ui.children += plain
          plain.restore(l, k => Some(k))
        }
        keysOf(plain) shouldBe List(List("a", "b"))
      }
    }

    "be left by a group's only tab moved where no group lies, into a new group split off that way" in {
      val g = groupsOf(keep = true)
      shown(g) {
        fx(g.open("a"))
        val first = fx(g.groups().head)
        def entry(in: TabGroup[String], label: String): MenuItem = g.menuFor(in, "a").find(_.label == label).get
        List("Move Right", "Move Left", "Move Down", "Move Up", "Open Right", "Open Down")
          .map(l => fx(entry(first, l).enabled)) shouldBe List.fill(6)(true)
        fx(entry(first, "Move Right").action())
        keysOf(g) shouldBe List(Nil, List("a"))
        fx(g.groups().head) should be theSameInstanceAs first
        fx(g.tabLayout.vertical) shouldBe false
        fx(closer(first).nonEmpty) shouldBe true
        fx(focusedIn(g.groups()(1))) shouldBe true
        // and down from there, where nothing lies below: the new group under it
        val second = fx(g.groups()(1))
        fx(g.moveBeside(second, "a", vertical = true, after = true))
        keysOf(g) shouldBe List(Nil, Nil, List("a"))
        val l = fx(g.tabLayout)
        l.vertical shouldBe false
        l.parts.head.tabs shouldBe Nil
        l.parts(1).vertical shouldBe true
        l.parts(1).groups.map(_.tabs) shouldBe List(Nil, List("a"))
      }
      // before it, the other way
      val h = groupsOf(keep = true)
      shown(h) {
        fx(h.open("a"))
        fx(h.moveBeside(h.groups().head, "a", vertical = false, after = false))
        keysOf(h) shouldBe List(List("a"), Nil)
      }
      // without the option, it stays where it is
      val plain = groupsOf(keep = false)
      shown(plain) {
        fx(plain.open("a"))
        val only = fx(plain.groups().head)
        fx(plain.menuFor(only, "a").filter(i => i.label.startsWith("Move") || i.label.startsWith("Open"))
          .map(_.enabled)) shouldBe List.fill(6)(false)
        fx(plain.moveBeside(only, "a", vertical = false, after = true))
        keysOf(plain) shouldBe List(List("a"))
      }
    }

    "close as ever without the option" in {
      val g = groupsOf(keep = false)
      shown(g) {
        two(g)
        fx(g.close(g.groups()(1), "c"))
        keysOf(g) shouldBe List(List("a", "b"))
        fx(g.closeAll(g.groups().head))
        keysOf(g) shouldBe List(Nil)
        fx(closer(g.groups().head)) shouldBe None
      }
    }
  }
}
