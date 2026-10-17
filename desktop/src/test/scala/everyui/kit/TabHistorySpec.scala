package everyui.kit

import everyui.component.{Component, Container, Modal}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** Closing the tab shown shows the one shown before it, as a browser does, however each came to be shown (a click,
  * `select`, `open`, a drag in): a chain closed back in turn; a tab closed while another shows changing nothing shown;
  * Close Others and Close All; a layout restored, with no such order yet, showing the neighbour; a tab dragged to
  * another group leaving the order of the one it left; a reorder keeping it; and a bar on its own. */
class TabHistorySpec extends FxSpec {
  private def groupsOf(): TabGroups[String] = fx {
    val g = new TabGroups[String](k => k, _ => new Container, k => k)
    g.size.width @= 1200.0
    g.size.height @= 700.0
    g
  }

  private def shown[T](g: TabGroups[String])(test: TabGroup[String] => T): T = {
    fx {
      ui.children.clear()
      ui.children += g
    }
    try test(fx(g.groups().head)) finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def opened(g: TabGroups[String], keys: String*): Unit = fx(keys.foreach(k => g.open(k)))
  private def showing(t: TabGroup[String]): Option[String] = fx(t.selected())
  private def click(c: Component): Unit = c.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0)

  "Closing the tab shown" should {
    "show the one shown before it, not its neighbour" in {
      val g = groupsOf()
      shown(g) { first =>
        opened(g, "a", "b", "c")
        fx(first.select("b"))
        opened(g, "d")
        fx(first.keys().toList) shouldBe List("a", "b", "c", "d")
        fx(g.close(first, "d"))
        showing(first) shouldBe Some("b")
      }
    }

    "go back along a chain in turn" in {
      val g = groupsOf()
      shown(g) { first =>
        // the tabs lie b, d, a, c, and are shown a, b, c, d
        opened(g, "b", "d", "a", "c")
        List("a", "b", "c", "d").foreach(k => fx(first.select(k)))
        fx(first.bar.recent) shouldBe List("d", "c", "b", "a")
        fx(g.close(first, "d"))
        showing(first) shouldBe Some("c")
        fx(g.close(first, "c"))
        showing(first) shouldBe Some("b")
        fx(g.close(first, "b"))
        showing(first) shouldBe Some("a")
      }
    }

    "follow select, open, a click and a tab opened again" in {
      val g = groupsOf()
      shown(g) { first =>
        opened(g, "a", "b", "c", "d")
        fx(first.select("a"))
        opened(g, "b")
        fx(click(first.bar("c").get))
        fx(g.openIn(first, "a"))
        fx(first.bar.recent) shouldBe List("a", "c", "b", "d")
        fx(g.close(first, "a"))
        showing(first) shouldBe Some("c")
        fx(g.close(first, "c"))
        showing(first) shouldBe Some("b")
      }
    }
  }

  "Closing a tab not shown" should {
    "leave the one shown, and drop out of the order" in {
      val g = groupsOf()
      shown(g) { first =>
        opened(g, "a", "b", "c")
        fx(first.select("a"))
        fx(g.close(first, "c"))
        showing(first) shouldBe Some("a")
        fx(first.bar.recent) shouldBe List("a", "b")
        fx(g.close(first, "a"))
        showing(first) shouldBe Some("b")
      }
    }
  }

  "Close Others and Close All" should {
    "end on the tab asked from, and with none" in {
      val g = groupsOf()
      shown(g) { first =>
        opened(g, "a", "b", "c", "d")
        fx(first.select("b"))
        fx(g.closeOthers(first, "c"))
        fx(first.keys().toList) shouldBe List("c")
        showing(first) shouldBe Some("c")
        fx(first.bar.recent) shouldBe List("c")
        opened(g, "e", "f")
        fx(g.closeOthers(first, "f"))
        showing(first) shouldBe Some("f")
        fx(g.closeAll(first))
        fx(g.groups().head.keys().toList) shouldBe Nil
        fx(g.groups().head.bar.recent) shouldBe Nil
      }
    }
  }

  "A restored layout" should {
    "show the neighbour, having no order yet, and keep the order out of the layout" in {
      val g = groupsOf()
      shown(g) { _ =>
        fx(g.restore(TabLayout(List("a", "b", "c", "d"), "b"), k => Some(k)))
        val first = fx(g.groups().head)
        fx(g.close(first, "b"))
        showing(first) shouldBe Some("c")
        fx(first.select("a"))
        fx(first.select("d"))
        fx(g.tabLayout) shouldBe TabLayout(List("a", "c", "d"), "d")
        fx(g.close(first, "d"))
        showing(first) shouldBe Some("a")
      }
    }
  }

  "A tab dragged to another group" should {
    "leave the order of the group it left and go to the front of the one it joins" in {
      val g = groupsOf()
      shown(g) { first =>
        opened(g, "a", "b", "d", "c")
        fx(g.openRight(first, "c"))
        val second = fx(g.groups()(1))
        showing(first) shouldBe Some("d")
        fx(first.select("a"))
        val c = fx(second.bar("c").get)
        val (cx, cy) = fx((c.absoluteLeft + 4.0, c.absoluteTop + 10.0))
        fx(g.press(first, "a", 30.0, 15.0))
        fx(g.dragTo(cx, cy))
        fx(g.drop(cx, cy))
        fx(g.groups().map(_.keys().toList)) shouldBe List(List("b", "d"), List("a", "c"))
        // the neighbour of a was b, but d was shown before it
        showing(first) shouldBe Some("d")
        fx(first.bar.recent) shouldBe List("d", "b")
        fx(second.bar.recent) shouldBe List("a", "c")
        fx(g.close(second, "a"))
        showing(second) shouldBe Some("c")
      }
    }

    "keep the order where it is only moved among its own group's tabs" in {
      val g = groupsOf()
      shown(g) { first =>
        opened(g, "a", "b", "c", "d")
        fx(first.select("b"))
        fx(first.select("a"))
        fx(first.reorder("c", 0))
        fx(first.reorder("a", 3))
        fx(first.keys().toList) shouldBe List("c", "b", "d", "a")
        showing(first) shouldBe Some("a")
        fx(first.bar.recent) shouldBe List("a", "b", "d", "c")
        fx(g.close(first, "a"))
        showing(first) shouldBe Some("b")
      }
    }
  }

  "A tab bar on its own" should {
    "show the tab shown before the one closed" in {
      val current = Var("a")
      val bar = fx(new TabBar(current, 600.0, showOnFocus = false))
      fx {
        ui.children.clear()
        ui.children += bar
        List("a", "b", "c", "d").foreach(k => bar.add(k, k.toUpperCase, closable = true))
      }
      try {
        fx(click(bar("c").get))
        fx(click(bar("b").get))
        fx(current @= "d")
        fx(bar.remove("d"))
        current() shouldBe "b"
        fx(bar.remove("a"))
        current() shouldBe "b"
        fx(bar.remove("b"))
        current() shouldBe "c"
      }
      finally fx(ui.children.clear())
    }
  }
}
