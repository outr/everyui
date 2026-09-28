package everyui.tree

import everyui.async.Task
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}

/** Tick boxes on a tree: a parent ticked with its subtree, shown mixed when only some of it is, Space and OK, a box
  * clicked, roots without one, children loaded under a ticked folder, and the model changing under the ticks. */
class CheckSpec extends FxSpec {
  private def press(code: KeyCode, text: String = ""): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, false, false, false, false))
  }

  /** A change list: two roots without boxes, "Changes" holding a folder of two files and a file, "Unversioned" one. */
  private def changes(): (TreeModel[String], Tree[String]) = {
    val model = new TreeModel[String](s => s)
    val tree = new Tree[String](model, s => s.substring(s.lastIndexOf('/') + 1))
    model.batch {
      model.setChildren(None, List("Changes", "Unversioned"))
      model.setChildren(Some("Changes"), List("Changes/src", "Changes/README"))
      model.setChildren(Some("Changes/src"), List("Changes/src/A", "Changes/src/B"))
      model.setChildren(Some("Unversioned"), List("Unversioned/tmp"))
    }
    tree.checkable @= (k => k.contains("/"))
    tree.expanded @= Set("Changes", "Changes/src", "Unversioned")
    (model, tree)
  }

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

  "A tree's ticks" should {
    "tick a folder with what is under it, and show its parents mixed" in {
      val (model, tree) = fx(changes())
      var heard: List[CheckToggle] = Nil
      fx(tree.checkToggled.attach(t => heard = heard :+ t))
      onScreen(tree) {
        fx(tree.lineOf("Changes").flatMap(_.check)) shouldBe None
        fx(tree.lineOf("Changes/src").flatMap(_.check)) shouldBe Some(CheckState.Unchecked)
        fx(tree.list.select("Changes/src"))
        press(KeyCode.SPACE, " ")
        fx(tree.checked()) shouldBe Set("Changes/src", "Changes/src/A", "Changes/src/B")
        heard shouldBe List(CheckToggle(Set("Changes/src"), true))
        fx(tree.checkState("Changes")) shouldBe CheckState.Mixed
        // one file unticked: its folder is mixed
        fx(tree.list.select("Changes/src/B"))
        press(KeyCode.SPACE, " ")
        fx(tree.checked()) shouldBe Set("Changes/src/A")
        fx(tree.lineOf("Changes/src").flatMap(_.check)) shouldBe Some(CheckState.Mixed)
        // ticked again, and the other file too: everything under Changes is ticked, the root has no box to tick
        press(KeyCode.SPACE, " ")
        fx(tree.setChecked(List("Changes/README"), true))
        fx(tree.checked()) shouldBe Set("Changes/src", "Changes/src/A", "Changes/src/B", "Changes/README")
        fx(tree.checkState("Changes")) shouldBe CheckState.Checked
        // a click on a box ticks it
        val unversioned = fx(tree.list.indexOf("Unversioned/tmp"))
        val line = fx(tree.list(unversioned))
        fx(tree.list.clicks(unversioned, everyui.list.ListRow.inset + line.depth * 16.0 + Tree.chevron + 6.0)) shouldBe true
        fx(tree.checked().contains("Unversioned/tmp")) shouldBe true
        // the ticked files taken out of the model go from the ticks, and what is left follows
        fx(model.remove("Changes/src/B"))
        fx(tree.checked()) shouldBe Set("Changes/src", "Changes/src/A", "Changes/README", "Unversioned/tmp")
        fx(model.insert(Some("Changes/src"), 0, "Changes/src/C"))
        fx(tree.checkState("Changes/src")) shouldBe CheckState.Mixed
      }
    }
    "tick the chosen nodes together, and take OK for a tick on a remote" in {
      val (_, tree) = fx(changes())
      onScreen(tree) {
        fx(tree.selection.keys @= Set("Changes/README", "Changes/src/A"))
        fx(tree.current @= Some("Changes/README"))
        press(KeyCode.SPACE, " ")
        fx(tree.checked()) shouldBe Set("Changes/README", "Changes/src/A")
        fx(tree.checkOnEnter @= true)
        fx(tree.list.select("Unversioned/tmp"))
        press(KeyCode.ENTER)
        fx(tree.checked()) shouldBe Set("Changes/README", "Changes/src/A", "Unversioned/tmp")
      }
    }
    "tick children as they load under a ticked folder" in {
      val model = fx(new TreeModel[String](s => s))
      val tree = fx(new Tree[String](model, s => s))
      var answer: Seq[String] => Unit = null
      fx {
        tree.checkable @= (_ => true)
        tree.expandable @= (s => !s.contains("."))
        tree.loader @= Some(_ => Task.async(done => answer = done))
        model.setChildren(None, List("/lib", "/a.txt"))
        tree.setChecked(List("/lib"), true)
      }
      onScreen(tree) {
        fx(tree.checkState("/lib")) shouldBe CheckState.Checked
        fx(tree.expand("/lib"))
        fx(answer(List("/lib/x.jar", "/lib/y.jar")))
        fx(tree.checked()) shouldBe Set("/lib", "/lib/x.jar", "/lib/y.jar")
        fx(tree.lineOf("/lib/x.jar").flatMap(_.check)) shouldBe Some(CheckState.Checked)
      }
    }
  }
}
