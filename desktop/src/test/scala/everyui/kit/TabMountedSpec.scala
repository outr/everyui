package everyui.kit

import everyui.component.{Component, Container}
import everyui.focus.Focus
import everyui.{FxSpec, ui}

/** Tabs that keep their place (`TabGroups.keepsMounted`): their content stays in the group, hidden, in the order it
  * was first shown, while another shows, and is shown again as the very one; a tab that does not is taken off as
  * before; one moved to another group is shown there, and one closed is disposed of. */
class TabMountedSpec extends FxSpec {
  "A tab that keeps its place" should {
    "stay in the group, hidden and where it was, while another shows, and come back as it was" in {
      var made: Map[String, Component] = Map.empty
      var disposed: List[String] = Nil
      val g = fx {
        val groups = new TabGroups[String](k => k, k => {
          val c = new Container
          c.onDispose(() => disposed = disposed :+ k)
          made = made + (k -> c)
          c
        }, k => k, keepsMounted = _.startsWith("web:"))
        groups.size.width @= 1000.0
        groups.size.height @= 600.0
        groups
      }
      fx {
        ui.children.clear()
        ui.children += g
      }
      try {
        val group = fx(g.groups().head)
        def body: Vector[Component] = fx(group.body.children().drop(1))
        def shows: List[String] = fx(made.toList.filter { case (_, c) => c.parent().exists(_ eq group.body) && c.visible() }
          .map(_._1))
        fx(g.open("web:one"))
        fx(g.open("notes"))
        fx(g.open("web:two"))
        val one = fx(made("web:one"))
        val two = fx(made("web:two"))
        val fxParent = fx(one.node.fx.getParent)
        // the frames in place, the one shown and the other hidden; the notes taken off
        body.map(_ eq one) shouldBe Vector(true, false)
        body(1) should be theSameInstanceAs two
        shows shouldBe List("web:two")
        fx(one.visible()) shouldBe false
        // the notes shown: after the frames, which stay where they are, both hidden
        fx(group.select("notes"))
        body.take(2) shouldBe Vector(one, two)
        body.size shouldBe 3
        shows shouldBe List("notes")
        // back to the first frame: the very one, never moved
        fx(group.select("web:one"))
        body shouldBe Vector(one, two)
        shows shouldBe List("web:one")
        fx(one.node.fx.getParent) should be theSameInstanceAs fxParent
        made.size shouldBe 3
        disposed shouldBe Nil
        // moved to a group of its own: shown there, and gone from here
        fx(g.openRight(group, "web:two"))
        eventually(g.groups().size == 2) shouldBe true
        val right = fx(g.groups()(1))
        fx(right.select("web:two"))
        fx(two.parent().exists(_ eq right.body)) shouldBe true
        fx(two.visible()) shouldBe true
        body shouldBe Vector(one)
        // closed: disposed of, and out of the group
        fx(g.close(group, "web:one"))
        eventually(disposed.contains("web:one")) shouldBe true
        body.contains(one) shouldBe false
      } finally fx {
        ui.children.clear()
        Focus.focused @= None
      }
    }

    "leave every other tab to be taken off as before" in {
      var made: Map[String, Component] = Map.empty
      val g = fx {
        val groups = new TabGroups[String](k => k, k => {
          val c = new Container
          made = made + (k -> c)
          c
        }, k => k)
        groups.size.width @= 1000.0
        groups.size.height @= 600.0
        groups
      }
      fx {
        ui.children.clear()
        ui.children += g
      }
      try {
        val group = fx(g.groups().head)
        fx(g.open("a"))
        fx(g.open("b"))
        fx(group.body.children().drop(1)) shouldBe Vector(made("b"))
        fx(group.select("a"))
        fx(group.body.children().drop(1)) shouldBe Vector(made("a"))
        fx(made("b").visible()) shouldBe true
      } finally fx {
        ui.children.clear()
        Focus.focused @= None
      }
    }
  }
}
