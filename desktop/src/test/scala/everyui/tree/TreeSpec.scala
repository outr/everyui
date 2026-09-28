package everyui.tree

import everyui.Color
import everyui.async.Task
import everyui.focus.Focus
import everyui.list.Decoration
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}

/** The tree: a model changed a node at a time under a tree that keeps what is open, chosen and scrolled to, children
  * loaded as a node opens, the keys, the app's dressing and its menu. */
class TreeSpec extends FxSpec {
  case class Node(path: String, directory: Boolean = false, status: String = "") {
    def name: String = path.substring(path.lastIndexOf('/') + 1)
  }

  private def press(code: KeyCode, text: String = ""): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, false, false, false, false))
  }

  private def files(): TreeModel[Node] = {
    val model = new TreeModel[Node](_.path)
    model.batch {
      model.setChildren(None, List(Node("/a", true), Node("/b", true), Node("/c")))
      model.setChildren(Some("/a"), List(Node("/a/1"), Node("/a/2")))
      model.setChildren(Some("/b"), List(Node("/b/1", true)))
      model.setChildren(Some("/b/1"), List(Node("/b/1/x")))
    }
    model
  }

  private def shown(tree: Tree[Node]): List[String] = fx((0 until tree.list.count).map(i => tree.list(i).key).toList)

  private def onScreen[T](tree: Tree[?])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += tree
      tree.focus()
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A tree" should {
    "follow its model a node at a time, keeping what is open and chosen" in {
      val model = fx(files())
      val tree = fx(new Tree[Node](model, _.name))
      onScreen(tree) {
        shown(tree) shouldBe List("/a", "/b", "/c")
        fx(tree.expand("/a"))
        fx(tree.expand("/b"))
        shown(tree) shouldBe List("/a", "/a/1", "/a/2", "/b", "/b/1", "/c")
        fx(tree.list.select("/a/2"))
        fx(model.insert(Some("/a"), 0, Node("/a/0")))
        shown(tree) shouldBe List("/a", "/a/0", "/a/1", "/a/2", "/b", "/b/1", "/c")
        fx(tree.current()) shouldBe Some("/a/2")
        fx(tree.selection.keys()) shouldBe Set("/a/2")
        // moved with what is under it, still open where it was open
        fx(tree.expand("/b/1"))
        fx(model.move("/b/1", Some("/a"), 1))
        shown(tree) shouldBe List("/a", "/a/0", "/b/1", "/b/1/x", "/a/1", "/a/2", "/b", "/c")
        fx(model.update(Node("/a/2", status = "M")))
        fx(tree.lineOf("/a/2").flatMap(_.value).map(_.status)) shouldBe Some("M")
        fx(tree.current()) shouldBe Some("/a/2")
        fx(model.remove("/a"))
        shown(tree) shouldBe List("/b", "/c")
        fx(tree.expanded()) shouldBe Set("/b")
        fx(tree.current()) shouldBe Some("/b")
      }
    }
    "load a node's children as it opens" in {
      val model = fx(new TreeModel[Node](_.path))
      var asked: List[String] = Nil
      var answer: Seq[Node] => Unit = null
      val tree = fx(new Tree[Node](model, _.name))
      fx {
        tree.expandable @= (n => n.directory)
        tree.loader @= Some(n => Task.async { done =>
          asked = asked :+ n.path
          answer = done
        })
        model.setChildren(None, List(Node("/src", true), Node("/README")))
      }
      onScreen(tree) {
        fx(tree.expand("/src"))
        asked shouldBe List("/src")
        fx(tree.lineOf("/src").map(_.loading)) shouldBe Some(true)
        fx(tree.list.rowAt(0).map(r => r.children().size)) should not be None
        fx(answer(List(Node("/src/Main.scala"), Node("/src/util", true))))
        shown(tree) shouldBe List("/src", "/src/Main.scala", "/src/util", "/README")
        fx(tree.lineOf("/src").map(_.loading)) shouldBe Some(false)
        // closed and opened again, it is not asked again
        fx(tree.collapse("/src"))
        fx(tree.expand("/src"))
        asked shouldBe List("/src")
      }
    }
    "open, close and move with the arrows, and activate with Enter" in {
      val model = fx(files())
      val tree = fx(new Tree[Node](model, _.name))
      var activated: List[String] = Nil
      fx(tree.activated.attach(n => activated = activated :+ n.path))
      onScreen(tree) {
        fx(tree.current()) shouldBe Some("/a")
        press(KeyCode.RIGHT)
        fx(tree.expanded()) shouldBe Set("/a")
        press(KeyCode.RIGHT)
        fx(tree.current()) shouldBe Some("/a/1")
        press(KeyCode.DOWN)
        fx(tree.current()) shouldBe Some("/a/2")
        press(KeyCode.ENTER)
        activated shouldBe List("/a/2")
        press(KeyCode.LEFT)
        fx(tree.current()) shouldBe Some("/a")
        press(KeyCode.LEFT)
        fx(tree.expanded()) shouldBe Set.empty
        shown(tree) shouldBe List("/a", "/b", "/c")
        // Enter on a node that opens opens it too
        press(KeyCode.ENTER)
        fx(tree.expanded()) shouldBe Set("/a")
        // a click on the chevron toggles, without picking the row
        fx(tree.list.clicks(3, everyui.list.ListRow.inset + 4.0)) shouldBe true
        fx(tree.expanded()) shouldBe Set("/a", "/b")
      }
    }
    "dress its rows as the app says, and hear a node's menu" in {
      val model = fx(files())
      val tree = fx(new Tree[Node](model, _.name))
      var menus: List[String] = Nil
      fx {
        tree.decorate @= (n => Decoration(color = Some(Color.fromHex("ff0000")), badge = n.status))
        tree.menu.attach(p => menus = menus :+ p.item.path)
      }
      onScreen(tree) {
        val row = fx(tree.list.rowAt(2).get.asInstanceOf[TreeRow[Node]])
        fx(row.label.label.content()) shouldBe "c"
        fx(row.label.label.color()) shouldBe Color.fromHex("ff0000")
        fx(model.update(Node("/c", status = "U")))
        fx(tree.list.rowAt(2).get.asInstanceOf[TreeRow[Node]].label.children().collect {
          case t: everyui.component.TextView => t.content()
        }) should contain("U")
        fx(tree.list.menuAt(2))
        menus shouldBe List("/c")
      }
    }
    "pick several nodes with the pointer and the keys, and show what the app picks" in {
      val model = fx(files())
      val tree = fx(new Tree[Node](model, _.name))
      def held(code: KeyCode, down: Boolean): Unit = fx(javafx.event.Event.fireEvent(window, new KeyEvent(
        if (down) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED, "", "", code, code == KeyCode.SHIFT && down,
        code == KeyCode.CONTROL && down, false, false)))
      def click(row: Int, shift: Boolean = false, ctrl: Boolean = false): Unit = fx {
        val x = tree.list.absoluteLeft + 60.0
        val y = tree.list.absoluteTop + row * 28.0 + 10.0
        javafx.event.Event.fireEvent(tree.list.node.fx, new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
          MouseButton.PRIMARY, 1, shift, ctrl, false, false, true, false, false, false, false, true, null))
      }
      onScreen(tree) {
        fx(tree.expand("/a"))
        click(1)
        click(3, shift = true)
        fx(tree.selection.keys()) shouldBe Set("/a/1", "/a/2", "/b")
        click(0, ctrl = true)
        fx(tree.selectedItems.map(_.path)) shouldBe Vector("/a", "/a/1", "/a/2", "/b")
        fx(javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, true, false, false, false)))
        held(KeyCode.SHIFT, false)
        fx(tree.selection.keys()) shouldBe Set("/a", "/a/1")
        // what the app picks shows on the rows
        fx(tree.selection.keys @= Set("/c"))
        fx(tree.list.rows.filter(_.selected()).flatMap(_.item()).map(_.key)) shouldBe Vector("/c")
      }
    }
    "take fifty thousand nodes, open, in its stride" in {
      val model = fx(new TreeModel[Node](_.path))
      val tree = fx(new Tree[Node](model, _.name))
      onScreen(tree) {
        val started = System.nanoTime()
        fx(model.batch {
          model.setChildren(None, (0 until 100).map(d => Node("/d" + d, true)))
          (0 until 100).foreach(d => model.setChildren(Some("/d" + d), (0 until 500).map(f => Node("/d" + d + "/f" + f))))
        })
        info(f"50k nodes given: ${(System.nanoTime() - started) / 1e6}%.0f ms")
        val opened = System.nanoTime()
        fx(tree.expanded @= (0 until 100).map(d => "/d" + d).toSet)
        info(f"100 folders of 500 opened at once (50,100 rows): ${(System.nanoTime() - opened) / 1e6}%.0f ms")
        fx(tree.list.count) shouldBe 50100
        fx(tree.reveal("/d70/f250"))
        val inserted = System.nanoTime()
        fx(model.insert(Some("/d10"), 0, Node("/d10/new")))
        info(f"one node inserted into 50k: ${(System.nanoTime() - inserted) / 1e6}%.1f ms")
        fx(tree.current()) shouldBe Some("/d70/f250")
        fx(tree.list.rows.size) should be < 40
      }
    }
  }
}
