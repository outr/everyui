package everyui.kit

import everyui.async.Task
import everyui.component.{Container, Modal}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.menu.{MenuItem, MenuRow}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}

/** Close Tabs to the Left and to the Right on a tab's menu: after Close All, the tabs that side of the one whose menu
  * it is closed first to last, each asked of `canClose` and stopping at the first that stays; not enabled with no tab
  * that side; reached by the keys as the rest of the menu is; and the sides as seen right to left, Left closing the
  * tabs after it in the reading order. */
class TabSideCloseSpec extends FxSpec {
  private val left = "Close Tabs to the Left"
  private val right = "Close Tabs to the Right"

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

  private def groupsOf(): TabGroups[String] = fx {
    val g = new TabGroups[String](k => k, _ => new Container, k => k)
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

  /** `g` on the screen with the tabs a to e in its one group, every close asked of the app and let go at once. */
  private def five[T](test: (TabGroups[String], TabGroup[String], () => List[String]) => T): T = {
    val g = groupsOf()
    var asked: List[String] = Nil
    shown(g) {
      fx {
        g.canClose @= (k => {
          asked = asked :+ k
          Task.pure(true)
        })
        List("a", "b", "c", "d", "e").foreach(k => g.open(k))
      }
      test(g, fx(g.groups().head), () => asked)
    }
  }

  private def keysOf(g: TabGroups[String]): List[List[String]] = fx(g.groups().map(_.keys().toList))
  private def item(g: TabGroups[String], group: TabGroup[String], k: String, label: String): MenuItem =
    fx(g.menuFor(group, k).find(_.label == label).get)
  private def run(g: TabGroups[String], group: TabGroup[String], k: String, label: String): Unit = {
    val i = item(g, group, k, label)
    fx(i.action())
  }
  private def sides(g: TabGroups[String], group: TabGroup[String], k: String): (Boolean, Boolean) =
    (item(g, group, k, left).enabled, item(g, group, k, right).enabled)

  "Closing the tabs to a side" should {
    "come after Close All on the menu, before the moves" in five { (g, group, _) =>
      fx(g.menuFor(group, "c").map(i => if (i.separator) "-" else i.label)).take(7) shouldBe
        List("Close", "Close Others", "Close All", left, right, "-", "Open Right")
    }

    "close the tabs left of a middle tab, first to last, then those right of it, the tab itself staying" in five {
      (g, group, asked) =>
        fx(group.select("c"))
        run(g, group, "c", left)
        asked() shouldBe List("a", "b")
        keysOf(g) shouldBe List(List("c", "d", "e"))
        fx(group.keyOf(group.shown())) shouldBe Some("c")
        run(g, group, "d", right)
        asked() shouldBe List("a", "b", "e")
        keysOf(g) shouldBe List(List("c", "d"))
        // through the API, by the reading order
        fx(g.open("x"))
        fx(g.open("y"))
        fx(g.closeAfter(group, "x"))
        keysOf(g) shouldBe List(List("c", "d", "x"))
        fx(g.closeBefore(group, "x"))
        keysOf(g) shouldBe List(List("x"))
        asked() shouldBe List("a", "b", "e", "y", "c", "d")
    }

    "not be enabled with no tab that side: Left at the first tab, Right at the last, neither with one tab" in five {
      (g, group, asked) =>
        sides(g, group, "a") shouldBe (false, true)
        sides(g, group, "c") shouldBe (true, true)
        sides(g, group, "e") shouldBe (true, false)
        // and doing nothing at an end, asked through the API
        fx(g.closeBefore(group, "a"))
        fx(g.closeAfter(group, "e"))
        fx(g.closeAfter(group, "nowhere"))
        asked() shouldBe Nil
        keysOf(g) shouldBe List(List("a", "b", "c", "d", "e"))
        fx(g.closeOthers(group, "c"))
        sides(g, group, "c") shouldBe (false, false)
    }

    "ask the app one tab at a time and stop at the first it keeps, those before it closed" in {
      val g = groupsOf()
      val answers = new Answers
      shown(g) {
        fx {
          g.canClose @= answers.ask
          List("a", "b", "c", "d", "e").foreach(k => g.open(k))
        }
        val group = fx(g.groups().head)
        run(g, group, "c", left)
        answers.pending shouldBe Set("a")
        fx(answers.answer("a", ok = false))
        answers.pending shouldBe Set.empty
        answers.asked shouldBe List("a")
        keysOf(g) shouldBe List(List("a", "b", "c", "d", "e"))
        run(g, group, "b", right)
        answers.pending shouldBe Set("c")
        fx(answers.answer("c", ok = true))
        answers.pending shouldBe Set("d")
        fx(answers.answer("d", ok = false))
        answers.pending shouldBe Set.empty
        answers.asked shouldBe List("a", "c", "d")
        keysOf(g) shouldBe List(List("a", "b", "d", "e"))
      }
    }

    "be reached by the keys as the rest of the menu is" in five { (g, group, asked) =>
      fx(group.select("c"))
      fx(group.focusTab())
      fx(javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.CONTEXT_MENU, false,
        false, false, false)))
      fx(Modal.showing) shouldBe true
      def focused: Option[String] = fx(Focus.focused().collect { case r: MenuRow => r.item.label })
      focused shouldBe Some("Close")
      (1 to 4).foreach { _ =>
        fx {
          Focus.key("ArrowDown", true)
          Focus.key("ArrowDown", false)
        }
      }
      focused shouldBe Some(right)
      fx {
        Focus.key("Enter", true)
        Focus.key("Enter", false)
      }
      fx(Modal.showing) shouldBe false
      asked() shouldBe List("d", "e")
      keysOf(g) shouldBe List(List("a", "b", "c"))
    }
  }

  "Right to left" should {
    "take the sides as seen, Left closing the tabs after it in the reading order, which lie to its left" in {
      fx(Direction.set(true))
      try five { (g, group, asked) =>
        // the tabs after the middle one lie to its left
        val (c, d) = fx((group.bar("c").get.absoluteLeft, group.bar("d").get.absoluteLeft))
        d should be < c
        fx(g.menuFor(group, "c").map(_.label)).slice(3, 5) shouldBe List(left, right)
        // the first tab in the reading order, on the far right, has none to its right
        sides(g, group, "a") shouldBe (true, false)
        sides(g, group, "e") shouldBe (false, true)
        run(g, group, "c", left)
        asked() shouldBe List("d", "e")
        keysOf(g) shouldBe List(List("a", "b", "c"))
        run(g, group, "c", right)
        asked() shouldBe List("d", "e", "a", "b")
        keysOf(g) shouldBe List(List("c"))
      }
      finally fx(Direction.set(false))
    }
  }
}
