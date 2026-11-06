package everyui.focus

import everyui.event.{KeyEvent, Keys}
import everyui.kit.TabBar
import everyui.menu.{Menu, MenuItem, MenuRow}
import everyui.component.{Component, Modal}
import everyui.render.Nodes
import everyui.list.{RichList, RichLook, RichMove, RichSection}
import everyui.tree.{Tree, TreeModel}
import everyui.{FxSpec, ui}
import reactify.*

/** What the keys do that a pointer did alone: a tab bar's ends, a tab moved along it and closed, a menu's ends, its
  * letters and Space, and a tree's folders opened together. */
class KeyParitySpec extends FxSpec {
  private def press(c: Component, name: String, shift: Boolean = false, alt: Boolean = false): Unit =
    fx(c.event.key.down @= KeyEvent(Keys.of(name), name, alt = alt, shift = shift))

  private def clear(): Unit = fx {
    while (Modal.showing) Modal.close()
    ui.children.clear()
    Focus.focused @= None
  }

  "A tab bar" should {
    "go to its ends with Home and End, move a tab along with Alt+Shift and an arrow, and close one with Delete" in {
      val current = Var("a")
      val closed = Var(List.empty[String])
      val bar = fx {
        val b = new TabBar(current, 600.0, showOnFocus = false)
        List("a", "b", "c", "d").foreach(k => b.add(k, k.toUpperCase, closable = true))
        b.closing.attach(k => closed @= closed() :+ k)
        ui.children += b
        b
      }
      try {
        def keys: List[String] = fx(bar.tabs().map(_.key))
        def on: Option[String] = fx(Focus.focused().collect { case t: everyui.kit.TabItem => t.key })
        fx(bar("b").get.focus())
        press(bar("b").get, "End")
        on shouldBe Some("d")
        press(bar("d").get, "Home")
        on shouldBe Some("a")
        fx(bar("a").get.keepsEvent(KeyEvent(Keys.of("ArrowRight"), "ArrowRight", alt = true, shift = true))) shouldBe
          true
        press(bar("a").get, "ArrowRight", shift = true, alt = true)
        keys shouldBe List("b", "a", "c", "d")
        on shouldBe Some("a")
        press(bar("a").get, "ArrowRight", shift = true, alt = true)
        press(bar("a").get, "ArrowLeft", shift = true, alt = true)
        keys shouldBe List("b", "a", "c", "d")
        press(bar("b").get, "ArrowLeft", shift = true, alt = true)
        keys shouldBe List("b", "a", "c", "d")
        press(bar("c").get, "Delete")
        closed() shouldBe List("c")
      } finally clear()
    }
    "give a closable tab with no menu of the app's a Close entry" in {
      val current = Var("a")
      val closed = Var(List.empty[String])
      val bar = fx {
        val b = new TabBar(current, 600.0, showOnFocus = false)
        b.add("a", "A", closable = true)
        b.add("b", "B")
        b.closing.attach(k => closed @= closed() :+ k)
        ui.children += b
        b
      }
      try {
        fx(bar.openMenu("b"))
        fx(Modal.showing) shouldBe false
        fx(bar.openMenu("a"))
        val row = fx(Focus.focused().collect { case r: MenuRow => r })
        row.map(_.item.label) shouldBe Some(everyui.i18n.Words.close)
        press(row.get, " ")
        eventually(closed() == List("a")) shouldBe true
      } finally clear()
    }
  }

  "A menu" should {
    "go to its ends with Home and End, to an entry by its letters, and choose with Space" in {
      var chosen = ""
      val anchor = fx {
        val c = new Component(Nodes.container())
        c.focusable @= true
        c.size.width @= 80.0
        c.size.height @= 30.0
        ui.children += c
        c.focus()
        c
      }
      try {
        fx(Menu.below(anchor, List("Cut", "Copy", "Paste", "Close", "Rename").map(l => MenuItem(l, () => chosen = l))))
        def on: Option[String] = fx(Focus.focused().collect { case r: MenuRow => r.item.label })
        on shouldBe Some("Cut")
        press(fx(Focus.focused().get), "End")
        on shouldBe Some("Rename")
        press(fx(Focus.focused().get), "Home")
        on shouldBe Some("Cut")
        press(fx(Focus.focused().get), "c")
        on shouldBe Some("Copy")
        press(fx(Focus.focused().get), "c")
        on shouldBe Some("Close")
        press(fx(Focus.focused().get), "p")
        on shouldBe Some("Paste")
        press(fx(Focus.focused().get), " ")
        eventually(chosen == "Paste") shouldBe true
        fx(Modal.showing) shouldBe false
      } finally clear()
    }
  }

  "A tree" should {
    "open every folder beside the one under the cursor with *" in {
      val model = fx {
        val m = new TreeModel[String](identity)
        m.batch {
          m.setChildren(None, List("/a", "/b", "/c"))
          m.setChildren(Some("/a"), List("/a/1"))
          m.setChildren(Some("/b"), List("/b/1"))
          m.setChildren(Some("/b/1"), List("/b/1/x"))
        }
        m
      }
      val tree = fx(new Tree[String](model, identity))
      fx {
        ui.children += tree
        tree.list.select("/a")
        tree.focus()
      }
      try {
        press(tree.list, "*", shift = true)
        fx(tree.expanded()) shouldBe Set("/a", "/b")
      } finally clear()
    }
  }

  "A list that reorders" should {
    "move the item under the cursor past the one above or below with Alt and an arrow, as a drag would" in {
      val sections = Var(Vector(RichSection("s", "Queue", Vector("a", "b", "c"))))
      val list = fx(new RichList[String](sections, s => s, s => RichLook(s)))
      var moves = List.empty[RichMove]
      fx {
        list.moved.attach(m => moves = moves :+ m)
        ui.children += list
        list.focus()
        list.reveal("b")
      }
      try {
        // a list that does not reorder moves nothing
        press(list.list, "ArrowUp", alt = true)
        moves shouldBe Nil
        fx {
          list.reorders @= true
          list.reveal("b")
        }
        press(list.list, "ArrowUp", alt = true)
        press(list.list, "ArrowDown", alt = true)
        moves shouldBe List(RichMove(List("b"), "s", "s", Some("a")), RichMove(List("b"), "s", "s", None))
        // nor past the head of its section, where `accepts` lets nothing go
        fx(list.reveal("a"))
        press(list.list, "ArrowUp", alt = true)
        moves.size shouldBe 2
      } finally clear()
    }
  }
}
