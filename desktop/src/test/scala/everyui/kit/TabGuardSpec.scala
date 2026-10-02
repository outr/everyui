package everyui.kit

import everyui.async.Task
import everyui.component.{Component, Container, Modal}
import everyui.focus.Focus
import everyui.keymap.Keymap
import everyui.{FxSpec, ui}

/** Tabs in groups asking before they close, and sharing one view per group: every way of closing a tab asks the app,
  * a tab it will not let go stays, Close Others and Close All stop at the first, a close asked again while waiting does
  * nothing, a second view of a key closes without asking; `tab(k)` and `dirty`; and the keys that share a group's view
  * shown in one made per group, told what it shows, kept, and disposed of with the group, the other keys as before. */
class TabGuardSpec extends FxSpec {
  /** The answers the app owes, by key, given when the spec says. */
  private class Answers {
    var asked: List[String] = Nil
    private var waiting: Map[String, Boolean => Unit] = Map.empty
    def ask(k: String): Task[Boolean] = Task.async[Boolean] { done =>
      asked = asked :+ k
      waiting = waiting + (k -> done)
    }
    def answer(k: String, ok: Boolean): Unit = {
      val done = waiting(k)
      waiting = waiting - k
      done(ok)
    }
    def pending: Set[String] = waiting.keySet
  }

  /** A group's shared view, telling what it is told. */
  private class Shared(n: Int, log: String => Unit) extends GroupContent[String] {
    var showing: Option[String] = None
    onDispose(() => log("view" + n.toString + " disposed"))
    override def show(k: String): Unit = {
      showing = Some(k)
      log("view" + n.toString + " show " + k)
    }
    override def hide(): Unit = {
      showing = None
      log("view" + n.toString + " hide")
    }
  }

  private var told: List[String] = Nil
  private var views = 0
  private var made: List[String] = Nil

  private def groupsOf(twice: String => Boolean = _ => false, shares: String => Boolean = _ => false): TabGroups[String] =
    fx {
      told = Nil
      views = 0
      made = Nil
      val g = new TabGroups[String](k => k, k => {
        made = made :+ k
        new Container
      }, k => k, allowsTwice = twice, sharesGroupContent = shares, groupContent = _ => {
        views += 1
        new Shared(views, s => told = told :+ s)
      })
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

  private def keysOf(g: TabGroups[String]): List[List[String]] = fx(g.groups().map(_.keys().toList))
  private def run(g: TabGroups[String], group: TabGroup[String], k: String, label: String): Unit =
    fx(g.menuFor(group, k).find(_.label == label).get.action())

  "Closing a tab" should {
    "ask the app by the ×, the menu and the keys, and keep the tab it will not let go" in {
      val g = groupsOf()
      val answers = new Answers
      shown(g) {
        fx {
          g.canClose @= answers.ask
          List("a", "b", "c").foreach(k => g.open(k))
        }
        val first = fx(g.groups().head)
        // the ×
        fx(first.bar.closing @= "a")
        answers.asked shouldBe List("a")
        keysOf(g) shouldBe List(List("a", "b", "c"))
        fx(answers.answer("a", ok = false))
        keysOf(g) shouldBe List(List("a", "b", "c"))
        // the menu's Close
        run(g, first, "b", "Close")
        answers.asked shouldBe List("a", "b")
        fx(answers.answer("b", ok = true))
        keysOf(g) shouldBe List(List("a", "c"))
        // the keys
        val keys = fx(new Keymap)
        fx(g.commands(keys))
        fx(first.select("c"))
        fx(keys("tabs.close").get.run())
        answers.asked shouldBe List("a", "b", "c")
        fx(answers.answer("c", ok = true))
        keysOf(g) shouldBe List(List("a"))
      }
    }

    "close the others and all one at a time, stopping at the first the app keeps" in {
      val g = groupsOf()
      val answers = new Answers
      shown(g) {
        fx {
          g.canClose @= answers.ask
          List("a", "b", "c", "d", "e").foreach(k => g.open(k))
        }
        val first = fx(g.groups().head)
        run(g, first, "a", "Close Others")
        // asked one at a time
        answers.pending shouldBe Set("b")
        fx(answers.answer("b", ok = true))
        answers.pending shouldBe Set("c")
        fx(answers.answer("c", ok = false))
        answers.pending shouldBe Set.empty
        answers.asked shouldBe List("b", "c")
        keysOf(g) shouldBe List(List("a", "c", "d", "e"))
        // Close All the same, from the first
        run(g, first, "a", "Close All")
        fx(answers.answer("a", ok = true))
        fx(answers.answer("c", ok = true))
        fx(answers.answer("d", ok = false))
        keysOf(g) shouldBe List(List("d", "e"))
        answers.pending shouldBe Set.empty
      }
    }

    "do nothing when asked again while the answer is awaited" in {
      val g = groupsOf()
      val answers = new Answers
      shown(g) {
        fx {
          g.canClose @= answers.ask
          List("a", "b").foreach(k => g.open(k))
        }
        val first = fx(g.groups().head)
        fx(g.close(first, "b"))
        fx(g.close(first, "b"))
        run(g, first, "a", "Close Others")
        answers.asked shouldBe List("b")
        fx(answers.answer("b", ok = true))
        keysOf(g) shouldBe List(List("a"))
        // and a group closing its last tab still closes
        fx(g.open("c"))
        fx(g.openRight(first, "c"))
        fx(g.close(g.groups()(1), "c"))
        fx(answers.answer("c", ok = true))
        keysOf(g) shouldBe List(List("a"))
      }
    }

    "ask only for a key's last tab, a second view closing at once, and close every group's tabs in turn" in {
      val g = groupsOf(twice = _ => true)
      val answers = new Answers
      shown(g) {
        fx {
          g.canClose @= answers.ask
          List("a", "b").foreach(k => g.open(k))
        }
        val first = fx(g.groups().head)
        fx(g.openRight(first, "b"))
        fx(g.close(g.groups()(1), "b"))
        answers.asked shouldBe Nil
        keysOf(g) shouldBe List(List("a", "b"))
        fx(g.openRight(first, "a"))
        // all of them: the second view of a asks nothing, b its last does
        var all: Option[Boolean] = None
        fx(g.closeAllTabs().map(r => all = Some(r)).start())
        answers.pending shouldBe Set("b")
        fx(answers.answer("b", ok = false))
        all shouldBe Some(false)
        keysOf(g) shouldBe List(List("b"), List("a"))
        fx(g.closeAllTabs().map(r => all = Some(r)).start())
        fx(answers.answer("b", ok = true))
        fx(answers.answer("a", ok = true))
        all shouldBe Some(true)
        keysOf(g) shouldBe List(Nil)
      }
    }

    "find a key's tabs in every group, and mark them as holding unsaved work, those opened after too" in {
      val g = groupsOf(twice = _ => true)
      shown(g) {
        fx(List("a", "b").foreach(k => g.open(k)))
        val first = fx(g.groups().head)
        fx(g.tab("a").size) shouldBe 1
        fx(g.dirty("a", on = true))
        fx(g.isDirty("a")) shouldBe true
        fx(g.tab("a").head.marker().nonEmpty) shouldBe true
        fx(g.tab("b").head.marker()) shouldBe None
        fx(g.openRight(first, "a"))
        fx(g.tab("a").size) shouldBe 2
        fx(g.tab("a").forall(_.marker().nonEmpty)) shouldBe true
        fx(g.dirty("a", on = false))
        fx(g.tab("a").forall(_.marker().isEmpty)) shouldBe true
        fx(g.tab("zz")) shouldBe Nil
      }
    }
  }

  "A group's shared view" should {
    "be made once per group as it first shows a key that shares it, told what it shows, and kept" in {
      val g = groupsOf(shares = k => k.startsWith("f"))
      shown(g) {
        fx(List("f1", "t1", "f2").foreach(k => g.open(k)))
        val first = fx(g.groups().head)
        views shouldBe 1
        made shouldBe List("t1")
        told shouldBe List("view1 show f1", "view1 hide", "view1 show f2")
        val view = fx(first.groupView).get
        fx(first.body.children().exists(_ eq view)) shouldBe true
        fx(first.contentOf("f1")) shouldBe None
        // between the files the same view, told again; to the terminal its own content
        fx(first.select("f1"))
        fx(first.groupView) shouldBe Some(view)
        told.last shouldBe "view1 show f1"
        fx(first.select("t1"))
        told.last shouldBe "view1 hide"
        fx(first.body.children().exists(_ eq view)) shouldBe false
        fx(first.body.children().exists(_ eq first.contentOf("t1").get)) shouldBe true
        made shouldBe List("t1")
        views shouldBe 1
      }
    }

    "show a moved key in the other group's own view, and two at once where it may show twice, disposed of with the group" in {
      val g = groupsOf(twice = k => k.startsWith("f"), shares = k => k.startsWith("f"))
      shown(g) {
        fx(List("f1", "f2").foreach(k => g.open(k)))
        val first = fx(g.groups().head)
        fx(g.openRight(first, "f2"))
        views shouldBe 2
        keysOf(g) shouldBe List(List("f1", "f2"), List("f2"))
        fx(g.groups()(1).groupView.map(_.asInstanceOf[Shared].showing)) shouldBe Some(Some("f2"))
        fx(first.groupView.map(_.asInstanceOf[Shared].showing)) shouldBe Some(Some("f2"))
        // moved: the other group's view told, nothing made
        fx(g.move(first, "f1", g.groups()(1)))
        fx(g.groups()(1).groupView.map(_.asInstanceOf[Shared].showing)) shouldBe Some(Some("f1"))
        views shouldBe 2
        made shouldBe Nil
        // the group gone with its view
        fx(g.unsplit(g.groups()(1)))
        told should contain("view2 disposed")
        told should not contain "view1 disposed"
        keysOf(g) shouldBe List(List("f2", "f1"))
        fx(g.closeAll(g.groups().head))
        // the last group stays, and its view with it, told it shows nothing
        told.last shouldBe "view1 hide"
        fx(g.dispose())
        told.last shouldBe "view1 disposed"
      }
    }
  }
}
