package everyui.list

import everyui.async.Task
import everyui.component.{Component, TextView}
import everyui.focus.Focus
import everyui.render.Nodes
import everyui.tree.{Tree, TreeEdit, TreeModel}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** Names typed in place: a rename and a new row, in a list and in a tree, taken, refused with a message, cancelled, and
  * committed as the field is left, with the focus back where it belongs. */
class EditSpec extends FxSpec {
  private def press(code: KeyCode, text: String = ""): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, false, false, false, false))
  }

  private def field(list: VirtualList[?]): Option[RowEditor] = list.children().collectFirst { case e: RowEditor => e }

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children ++= parts
      parts.head.focus()
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A list" should {
    "rename a row in place, take the name, and give the focus back" in {
      val items = Var(Vector("alpha", "beta", "gamma"))
      val list = fx(VirtualList.text[String](items, s => s, s => s))
      var heard: List[ListEdit[String]] = Nil
      fx {
        list.renames @= true
        list.commit @= { e =>
          heard = heard :+ e
          items @= items().updated(e.index, e.text)
          Task.pure(None)
        }
      }
      onScreen(list) {
        press(KeyCode.DOWN)
        press(KeyCode.F2)
        val f = fx(field(list).get)
        fx(f.input.text()) shouldBe "beta"
        fx(Focus.focused().exists(_ eq f)) shouldBe true
        fx(list.rowAt(1).get.asInstanceOf[TextRow[String]].label.label.content()) shouldBe ""
        fx(f.input.text @= "delta")
        press(KeyCode.ENTER)
        heard.map(e => (e.key, e.text, e.created)) shouldBe List(("beta", "delta", false))
        fx(field(list)) shouldBe None
        fx(Focus.focused().exists(_ eq list)) shouldBe true
        fx(list.current()) shouldBe Some("delta")
      }
    }
    "keep the field open with the app's message for a name it refuses, until Escape" in {
      val items = Var(Vector("alpha", "beta"))
      val list = fx(VirtualList.text[String](items, s => s, s => s))
      var answer: Option[String] => Unit = null
      fx(list.commit @= (e => Task.async(done => answer = done)))
      onScreen(list) {
        fx(list.edit("alpha"))
        val f = fx(field(list).get)
        fx(f.input.text @= "beta")
        press(KeyCode.ENTER)
        fx(field(list)) shouldBe Some(f)
        fx(answer(Some("A file named beta is already here")))
        fx(f.error()) shouldBe "A file named beta is already here"
        fx(Focus.focused().exists(_ eq f)) shouldBe true
        fx(f.input.text @= "betamax")
        fx(f.error()) shouldBe ""
        press(KeyCode.ESCAPE)
        fx(field(list)) shouldBe None
        fx(items()) shouldBe Vector("alpha", "beta")
        fx(Focus.focused().exists(_ eq list)) shouldBe true
        fx(list.current()) shouldBe Some("alpha")
      }
    }
    "commit a name as the field is left" in {
      val items = Var(Vector("alpha", "beta"))
      val list = fx(VirtualList.text[String](items, s => s, s => s))
      val other = fx {
        val c = new Component(Nodes.container())
        c.focusable @= true
        c.size.width @= 10.0
        c.size.height @= 10.0
        c.position.top @= 500.0
        c
      }
      var heard: List[String] = Nil
      fx(list.commit @= { e => heard = heard :+ e.text; Task.pure(None) })
      onScreen(list, other) {
        fx(list.edit("beta"))
        fx(field(list).get.input.text @= "bravo")
        fx(other.focus())
        heard shouldBe List("bravo")
        fx(field(list)) shouldBe None
        fx(Focus.focused().exists(_ eq other)) shouldBe true
      }
    }
    "name a new row, which the app adds, and put the cursor on it" in {
      val items = Var(Vector("alpha", "beta"))
      val list = fx(VirtualList.text[String](items, s => s, s => s))
      var heard: List[ListEdit[String]] = Nil
      fx(list.commit @= { e =>
        heard = heard :+ e
        items @= items().patch(e.index, Vector(e.text), 0)
        Task.pure(None)
      })
      onScreen(list) {
        fx(list.insertEditing(1, "\u0000new"))
        fx(list.count) shouldBe 3
        fx(list.keyAt(1)) shouldBe "\u0000new"
        fx(field(list).get.input.text()) shouldBe ""
        fx(field(list).get.input.text @= "aardvark")
        press(KeyCode.ENTER)
        heard.map(e => (e.index, e.text, e.created)) shouldBe List((1, "aardvark", true))
        fx(items()) shouldBe Vector("alpha", "aardvark", "beta")
        fx(list.count) shouldBe 3
        fx(list.current()) shouldBe Some("aardvark")
        // and a new row cancelled goes
        fx(list.insertEditing(0, "\u0000new"))
        press(KeyCode.ESCAPE)
        fx(list.count) shouldBe 3
      }
    }
  }

  "A tree" should {
    "name a new node under a folder whose children it has to load first, and rename one" in {
      val model = fx(new TreeModel[String](s => s))
      val tree = fx(new Tree[String](model, s => s.substring(s.lastIndexOf('/') + 1)))
      var answer: Seq[String] => Unit = null
      var heard: List[TreeEdit[String]] = Nil
      fx {
        tree.expandable @= (s => !s.contains("."))
        tree.loader @= Some(_ => Task.async(done => answer = done))
        tree.commit @= { e =>
          heard = heard :+ e
          e.node match {
            case None => model.insert(e.parent, e.position, e.parent.getOrElse("") + "/" + e.text)
            case Some(old) =>
              model.remove(old)
              model.insert(e.parent, e.position, e.parent.getOrElse("") + "/" + e.text)
          }
          Task.pure(None)
        }
        model.setChildren(None, List("/src", "/README.md"))
      }
      onScreen(tree) {
        fx(tree.insertEditing(Some("/src"), 1))
        fx(field(tree.list)) shouldBe None
        fx(answer(List("/src/A.scala", "/src/C.scala")))
        val f = fx(field(tree.list).get)
        fx((0 until tree.list.count).map(i => tree.list(i).key).toList) shouldBe
          List("/src", "/src/A.scala", Tree.newKey, "/src/C.scala", "/README.md")
        fx(tree.list(2).depth) shouldBe 1
        fx(f.input.text @= "B.scala")
        press(KeyCode.ENTER)
        heard.map(e => (e.node, e.parent, e.position, e.text)) shouldBe List((None, Some("/src"), 1, "B.scala"))
        fx((0 until tree.list.count).map(i => tree.list(i).key).toList) shouldBe
          List("/src", "/src/A.scala", "/src/B.scala", "/src/C.scala", "/README.md")
        fx(tree.current()) shouldBe Some("/src/B.scala")
        fx(Focus.focused().exists(_ eq tree.list)) shouldBe true
        fx(tree.edit("/src/C.scala"))
        fx(field(tree.list).get.input.text()) shouldBe "C.scala"
        fx(field(tree.list).get.input.text @= "D.scala")
        press(KeyCode.ENTER)
        heard.last shouldBe TreeEdit(Some("/src/C.scala"), Some("/src"), 2, "D.scala")
        fx(model.childKeys(Some("/src"))) shouldBe Vector("/src/A.scala", "/src/B.scala", "/src/D.scala")
      }
    }
  }
}
