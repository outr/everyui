package everyui.kit

import everyui.component.{Component, Container, Modal}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.keymap.Keymap
import everyui.menu.MenuItem
import everyui.split.SplitPane
import everyui.{FxSpec, ui}

/** One group shown alone (`TabGroups.maximize`), as an editor maximizes a group: the others hidden, not closed, and laid
  * out again exactly as they were, nested ratios, tabs, the tab each shows and every content the same; what would land
  * where the person cannot see it laying them out again first (opening, splitting, moving, unsplitting, a drag onto an
  * edge, a restore, the group's last tab closing); the saved layout unchanged by it; the focus kept in it; its menu
  * entries and its key; and right to left. */
class TabMaximizeSpec extends FxSpec {
  /** What each tab shows, and which have been disposed of. */
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
  }

  // a, b (b shown) | over c, d (c shown), under e | f: three splits, nested, each its own ratio
  private val nested: TabLayout = TabLayout(ratio = 0.35, parts = List(
    TabLayout(List("a", "b"), "b"),
    TabLayout(vertical = true, ratio = 0.7, parts = List(
      TabLayout(List("c", "d"), "c"),
      TabLayout(ratio = 0.25, parts = List(TabLayout(List("e"), "e"), TabLayout(List("f"), "f")))))))

  private def made(views: Views, twice: String => Boolean = _ => false): TabGroups[String] = fx {
    val g = new TabGroups[String](k => k, views.make, k => k, allowsTwice = twice)
    g.size.width @= 1200.0
    g.size.height @= 700.0
    ui.children.clear()
    ui.children += g
    g.restore(nested, k => Some(k))
    // every tab shown once, so each has its content, and the first chosen tabs shown again
    g.groups().foreach { group =>
      val was = group.selected()
      group.keys().foreach(group.select)
      was.foreach(group.select)
    }
    g
  }

  private def done(): Unit = fx {
    while (Modal.showing) Modal.close()
    ui.children.clear()
    Focus.focused @= None
  }

  /** Each group's box: left, top, width and height. */
  private def boxes(g: TabGroups[String]): List[(Double, Double, Double, Double)] = fx(g.groups().map(x =>
    (x.absoluteLeft, x.absoluteTop, x.size.width(), x.size.height())))
  /** Each tab's content, by group. */
  private def contents(g: TabGroups[String]): List[List[Option[Component]]] =
    fx(g.groups().map(x => x.keys().toList.map(x.contentOf)))
  private def keysOf(g: TabGroups[String]): List[List[String]] = fx(g.groups().map(_.keys().toList))
  private def selectedOf(g: TabGroups[String]): List[Option[String]] = fx(g.groups().map(_.selected()))
  /** Whether `c` is drawn under `g`. */
  private def within(c: Component, g: Component): Boolean = {
    var at: Option[Component] = Some(c)
    while (at.exists(x => !(x eq g))) at = at.flatMap(_.parent())
    at.nonEmpty
  }
  private def splits(c: Component): Int = c match {
    case s: SplitPane => 1 + s.childComponents.map(splits).sum
    case x: Container => x.childComponents.map(splits).sum
    case _ => 0
  }
  private def labels(items: List[MenuItem]): List[String] = items.filterNot(_.separator).map(_.label)

  "Maximizing a group" should {
    "show it alone over the whole area, hide the rest, and lay them out again exactly as they were" in {
      val views = new Views
      val g = made(views)
      try {
        var told = 0
        fx(g.layoutChanged.attach(_ => told += 1))
        val layout = fx(g.tabLayout)
        layout shouldBe nested
        val before = boxes(g)
        val content = contents(g)
        val keys = keysOf(g)
        val selected = selectedOf(g)
        val count = views.made.size
        fx(splits(g)) shouldBe 3
        val third = fx(g.groups()(2))
        fx(g.maximize(third))
        fx(g.maximized()) shouldBe Some(third)
        boxes(g)(2) shouldBe ((0.0, 0.0, 1200.0, 700.0))
        fx(g.groups().map(x => within(x, g))) shouldBe List(false, false, true, false)
        // the handles hidden with them, and nothing closed
        fx(splits(g)) shouldBe 0
        keysOf(g) shouldBe keys
        selectedOf(g) shouldBe selected
        contents(g) shouldBe content
        // the focus in it, its tab shown at the whole width
        fx(Focus.focused().exists(f => Focus.within(f, third))) shouldBe true
        fx(g.focusedGroup()) shouldBe third
        fx(third.bar.size.width()) shouldBe 1200.0
        // the layout kept is the whole one, unchanged, and not told
        fx(g.tabLayout) shouldBe layout
        told shouldBe 0
        fx(g.unmaximize())
        fx(g.maximized()) shouldBe None
        boxes(g) shouldBe before
        fx(splits(g)) shouldBe 3
        fx(g.groups().forall(x => within(x, g))) shouldBe true
        fx(g.tabLayout) shouldBe layout
        keysOf(g) shouldBe keys
        selectedOf(g) shouldBe selected
        contents(g) shouldBe content
        contents(g).flatten.zip(content.flatten).forall { case (a, b) => a.get eq b.get } shouldBe true
        views.made.size shouldBe count
        views.disposed shouldBe Nil
        told shouldBe 0
        fx(Focus.focused().exists(f => Focus.within(f, third))) shouldBe true
        // a handle dragged after it still writes the ratio it kept
        val outer = fx(g.childComponents.head.asInstanceOf[SplitPane])
        outer.ratio() shouldBe 0.35
        fx(outer.ratio @= 0.4)
        fx(g.tabLayout.ratio) shouldBe 0.4
        told shouldBe 1
      }
      finally done()
    }

    "do nothing with one group, and tell `maximized` each way" in {
      val views = new Views
      val g = fx(new TabGroups[String](k => k, views.make, k => k))
      fx {
        g.size.width @= 800.0
        g.size.height @= 600.0
        ui.children += g
        g.open("a")
        g.open("b")
      }
      try {
        fx(g.maximize(g.groups().head))
        fx(g.maximized()) shouldBe None
        labels(fx(g.menuFor(g.groups().head, "a"))) should not contain TabGroups.maximizeWord
        fx(g.openRight(g.groups().head, "b"))
        var heard: List[Option[Int]] = Nil
        fx(g.maximized.attach(m => heard = heard :+ m.map(x => g.groups().indexWhere(_ eq x))))
        fx(g.maximize(g.groups()(1)))
        fx(g.maximize(g.groups()(1)))
        fx(g.unmaximize())
        fx(g.unmaximize())
        heard shouldBe List(Some(1), None)
      }
      finally done()
    }
  }

  "Opening a tab while a group is maximized" should {
    "keep it maximized into that group, and lay the groups out again first into a hidden one" in {
      val views = new Views
      val g = made(views)
      try {
        val layout = fx(g.tabLayout)
        val before = boxes(g)
        val first = fx(g.groups().head)
        fx(g.maximize(first))
        // into the maximized group itself: it stays, the tab shown there
        fx(g.open("x"))
        fx(g.maximized()) shouldBe Some(first)
        keysOf(g).head shouldBe List("a", "b", "x")
        fx(first.selected()) shouldBe Some("x")
        // a tab open in a hidden group, which may not show twice, is shown there: every group shows again first
        fx(g.open("e"))
        fx(g.maximized()) shouldBe None
        fx(g.focusedGroup()) shouldBe fx(g.groups()(2))
        boxes(g) shouldBe before
        fx(g.tabLayout) shouldBe layout.copy(parts = layout.parts.head.copy(tabs = List("a", "b", "x"), selected = "x") ::
          layout.parts.tail)
        // into a hidden group by openIn
        fx(g.maximize(first))
        val last = fx(g.groups().last)
        fx(g.openIn(last, "y"))
        fx(g.maximized()) shouldBe None
        keysOf(g).last shouldBe List("f", "y")
        fx(within(last, g)) shouldBe true
        fx(Focus.focused().exists(f => Focus.within(f, last))) shouldBe true
        boxes(g) shouldBe before
        views.disposed shouldBe Nil
      }
      finally done()
    }
  }

  "Changing the groups while one is maximized" should {
    "lay them out again first to split, move to another group, move beside and unsplit, and stay for a reorder" in {
      val views = new Views
      val g = made(views)
      try {
        val before = boxes(g)
        // (read on the FX thread, in what runs there)
        def first: TabGroup[String] = g.groups().head
        // a split
        fx(g.maximize(first))
        fx(g.openDown(first, "a"))
        fx(g.maximized()) shouldBe None
        keysOf(g) shouldBe List(List("b"), List("a"), List("c", "d"), List("e"), List("f"))
        fx(g.unsplit(g.groups()(1)))
        keysOf(g) shouldBe List(List("b", "a"), List("c", "d"), List("e"), List("f"))
        boxes(g) shouldBe before
        // a reorder among its own tabs keeps it maximized
        fx(g.maximize(first))
        fx(g.move(first, "a", first, 0))
        fx(g.maximized()) shouldBe Some(first)
        keysOf(g).head shouldBe List("a", "b")
        // a move to a hidden group
        fx(g.move(first, "a", g.groups()(1)))
        fx(g.maximized()) shouldBe None
        keysOf(g) shouldBe List(List("b"), List("c", "d", "a"), List("e"), List("f"))
        fx(g.groups()(1).selected()) shouldBe Some("a")
        boxes(g) shouldBe before
        // Move Down, from the menu, into the group under it
        val second = fx(g.groups()(1))
        fx(g.maximize(second))
        fx(g.menuFor(second, "a").find(_.label == TabGroups.moveDownWord).get.action())
        fx(g.maximized()) shouldBe None
        keysOf(g) shouldBe List(List("b"), List("c", "d"), List("e", "a"), List("f"))
        // Unsplit
        val third = fx(g.groups()(2))
        fx(g.maximize(third))
        fx(g.menuFor(third, "a").find(_.label == TabGroups.unsplitWord).get.action())
        fx(g.maximized()) shouldBe None
        keysOf(g) shouldBe List(List("b"), List("c", "d"), List("f", "e", "a"))
        fx(g.groups().forall(x => within(x, g))) shouldBe true
        views.disposed shouldBe Nil
      }
      finally done()
    }

    "land a drag only in the maximized group: among its tabs it stays, onto its edge every group shows and it splits" in {
      val views = new Views
      val g = made(views)
      try {
        val first = fx(g.groups().head)
        fx(g.maximize(first))
        // where the hidden groups lay, the maximized group is all there is
        fx(g.landingAt(1100.0, 30.0).map(_._1)) shouldBe Some(first)
        val a = fx(first.bar("a").get)
        val b = fx(first.bar("b").get)
        val (bx, by) = fx((b.absoluteLeft + b.size.width() - 4.0, b.absoluteTop + 10.0))
        fx(g.press(first, "a", a.absoluteLeft + 10.0, a.absoluteTop + 10.0))
        fx(g.dragTo(bx, by))
        fx(g.drop(bx, by))
        fx(g.maximized()) shouldBe Some(first)
        keysOf(g).head shouldBe List("b", "a")
        // onto its right edge: every group shows again, and a group is split off it there
        val (ex, ey) = fx((first.absoluteLeft + first.size.width() - 20.0, 400.0))
        fx(g.landingAt(ex, ey).map(_._2)) shouldBe Some(TabDropZone.End)
        fx(g.press(first, "a", bx, by))
        fx(g.dragTo(ex, ey))
        fx(g.drop(ex, ey))
        fx(g.maximized()) shouldBe None
        keysOf(g) shouldBe List(List("b"), List("a"), List("c", "d"), List("e"), List("f"))
        fx(g.groups()(1).absoluteLeft) should be > fx(first.absoluteLeft)
        fx(g.groups().forall(x => within(x, g))) shouldBe true
        views.disposed shouldBe Nil
      }
      finally done()
    }

    "show every group again as its last tab closes, or as a restore lays out another" in {
      val views = new Views
      val g = made(views)
      try {
        val last = fx(g.groups().last)
        fx(g.maximize(last))
        fx(g.close(last, "f"))
        fx(g.maximized()) shouldBe None
        keysOf(g) shouldBe List(List("a", "b"), List("c", "d"), List("e"))
        fx(g.groups().forall(x => within(x, g))) shouldBe true
        fx(Focus.focused().exists(f => Focus.within(f, g.groups()(2)))) shouldBe true
        views.disposed.map(_.takeWhile(_ != '#')) shouldBe List("f")
        // the hidden groups closing until it is the only one
        val first = fx(g.groups().head)
        fx(g.maximize(first))
        fx(g.closeAll(g.groups()(1)))
        fx(g.maximized()) shouldBe Some(first)
        fx(g.closeAll(g.groups()(1)))
        fx(g.maximized()) shouldBe None
        keysOf(g) shouldBe List(List("a", "b"))
        // a restore
        fx(g.restore(nested, k => Some(k)))
        fx(g.maximize(g.groups()(1)))
        fx(g.restore(nested, k => Some(k)))
        fx(g.maximized()) shouldBe None
        fx(splits(g)) shouldBe 3
        fx(g.tabLayout) shouldBe nested
      }
      finally done()
    }

    "dispose of the hidden groups' contents with it" in {
      val views = new Views
      val g = made(views)
      try {
        fx(g.maximize(g.groups().head))
        fx(g.dispose())
        views.disposed.map(_.takeWhile(_ != '#')).toSet shouldBe Set("a", "b", "c", "d", "e", "f")
      }
      finally done()
    }
  }

  "The focus while a group is maximized" should {
    "move into it, and go to no hidden group by the keys; its menu and its key maximize and show every group" in {
      val views = new Views
      val g = made(views)
      try {
        val first = fx(g.groups().head)
        val third = fx(g.groups()(2))
        fx(first.focusTab())
        // the menu: Hide Other Groups after Unsplit, Show All Groups while one is maximized
        labels(fx(g.menuFor(third, "e"))).takeRight(2) shouldBe List(TabGroups.unsplitWord, TabGroups.maximizeWord)
        TabGroups.maximizeWord shouldBe "Hide Other Groups"
        fx(g.menuFor(third, "e").find(_.label == TabGroups.maximizeWord).get.action())
        fx(g.maximized()) shouldBe Some(third)
        fx(Focus.focused().exists(f => Focus.within(f, third))) shouldBe true
        labels(fx(g.menuFor(third, "e"))).takeRight(2) shouldBe List(TabGroups.unsplitWord, TabGroups.unmaximizeWord)
        TabGroups.unmaximizeWord shouldBe "Show All Groups"
        // Primary+Alt+Right and Left stay in it
        fx(g.nextGroup())
        fx(g.nextGroup(back = true))
        fx(Focus.focused().exists(f => Focus.within(f, third))) shouldBe true
        fx(g.focusedGroup()) shouldBe third
        fx(g.menuFor(third, "e").find(_.label == TabGroups.unmaximizeWord).get.action())
        fx(g.maximized()) shouldBe None
        fx(g.nextGroup())
        fx(g.focusedGroup()) shouldBe fx(g.groups()(3))
        // the key: the focused group, and back
        val keys = fx(new Keymap)
        fx(g.commands(keys))
        val command = fx(keys("tabs.maximize").get)
        command.chords().map(_.label) shouldBe List("Ctrl+Shift+Enter")
        fx(command.run())
        fx(g.maximized()) shouldBe fx(Some(g.groups()(3)))
        fx(command.run())
        fx(g.maximized()) shouldBe None
        views.disposed shouldBe Nil
      }
      finally done()
    }
  }

  "Right to left" should {
    "maximize to the whole area and lay the groups out again mirrored as they were" in {
      fx(Direction.set(true))
      try {
        val views = new Views
        val g = made(views)
        try {
          val before = boxes(g)
          // the first group on the right
          before.head._1 should be > before(1)._1
          val content = contents(g)
          val second = fx(g.groups()(1))
          fx(g.maximize(second))
          boxes(g)(1) shouldBe ((0.0, 0.0, 1200.0, 700.0))
          fx(g.unmaximize())
          boxes(g) shouldBe before
          contents(g) shouldBe content
          fx(g.tabLayout) shouldBe nested
        }
        finally done()
      }
      finally fx(Direction.set(false))
    }
  }
}
