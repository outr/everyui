package everyui.component

import everyui.focus.Focus
import everyui.layout.VerticalLayout
import everyui.render.Nodes
import everyui.theme.Theme
import everyui.{FxSpec, LeakPaths, ui}

import java.lang.ref.WeakReference

/** A dialog closed is let go of, with everything in it: on a Roku every "Quit the quiz?" a game had asked stayed in
  * memory, the sheet bound to the size of the screen and the panel to the theme. As NaboTV's games go: a board drawn
  * afresh for each question (`children.replaceMaking`), Back asking whether to quit over it, Keep playing, the next
  * question, Back again, Quit, and the board put away. A panel put up to `keep` is left whole, to be put up again.
  *
  * A reactify value keeps the value it had before its last change (the focus, the last thing focused before), so the
  * focus is moved on twice before anything is looked for. */
class ModalDisposeSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
    offStage()
    super.afterAll()
  }

  /** A dialog as an app makes one: on the surface, as wide as the screen leaves it, a title, words and buttons. */
  private def dialog(title: String, actions: List[(String, () => Unit)]): (Container, List[Button]) = {
    val buttons = actions.map(a => new Button(a._1, a._2))
    val panel = new Container {
      layout @= Some(VerticalLayout(12.0))
      padding @= 20.0
      size.width := math.min(480.0, ui.size.width() - 32.0)
      backgroundColor := Theme.surface()
      radius @= 16.0
      children ++= List(TextView(title, 18.0), TextView("Your progress will be lost.", 14.0, Theme.muted()),
        Row(8.0)(buttons*))
    }
    (panel, buttons)
  }

  /** A board for question `n`: the question and four answers, the answers bound to the theme. */
  private def board(n: Int): Container = Column(12.0)(TextView("Question " + n.toString, 22.0),
    Column(7.0)((1 to 4).map(k => new Button("Answer " + k.toString))*))

  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  /** `c` and everything under it. */
  private def under(c: Component): Vector[Component] = {
    var all = Vector.empty[Component]
    var pending: List[Component] = List(c)
    while (pending.nonEmpty) {
      all = all :+ pending.head
      pending = pending.head.childComponents.toList ++ pending.tail
    }
    all
  }

  private def weakly(parts: Vector[Component]): Vector[WeakReference[Component]] = parts.map(p => new WeakReference(p))

  /** How many of `parts` are still reachable once the focus has moved on twice: waited for, a collection at a time,
    * until none is or five seconds have passed; what holds those left is told. */
  private def surviving(parts: Vector[WeakReference[Component]], elsewhere: Component, also: Component): Int = {
    fx(also.focus())
    fx(elsewhere.focus())
    def count = parts.count(_.get() != null)
    val until = System.currentTimeMillis() + 5000L
    while (count > 0 && System.currentTimeMillis() < until) {
      fx(())
      System.gc()
      Thread.sleep(50L)
    }
    if (count > 0) LeakPaths.find(parts.flatMap(r => Option(r.get()))).foreach(p => info("held: " + p))
    count
  }

  /** Two focusable cards on the screen, the first focused: what has the focus before a dialog and after it. */
  private def cards(): (Component, Component) = fx {
    ui.children.clear()
    Focus.focused @= None
    val made = (0 to 1).map { i =>
      val c = new Component(Nodes.container())
      c.focusable @= true
      c.size.width @= 100.0
      c.size.height @= 40.0
      c.position.left @= 50.0 + i * 150.0
      c.position.top @= 600.0
      ui.children += c
      c
    }
    made.head.focus()
    (made.head, made(1))
  }

  "A dialog closed" should {
    "be let go of with its sheet and all it holds" in {
      val (first, second) = cards()
      // (held only weakly here, as everything the specs look for)
      val parts = fx {
        val (panel, buttons) = dialog("Quit the quiz?", List("Keep playing" -> (() => Modal.close())))
        Modal.show(panel, buttons.head)
        weakly(under(panel.parent().get))
      }
      eventually(Modal.showing) shouldBe true
      fx(Focus.focused().exists(f => parts.exists(_.get() eq f))) shouldBe true
      press("Enter")
      eventually(!Modal.showing) shouldBe true
      fx(Focus.focused()) shouldBe Some(first)
      withClue(s"of ${parts.size}: ") {
        surviving(parts, first, second) shouldBe 0
      }
    }
    "leave the one it was over whole and up, and be let go of" in {
      val (first, second) = cards()
      val (below, top) = fx {
        val (lower, lowerButtons) = dialog("Settings", List("Done" -> (() => Modal.close())))
        Modal.show(lower, lowerButtons.head)
        val (upper, upperButtons) = dialog("Are you sure?", List("No" -> (() => Modal.close())))
        Modal.over(upper, upperButtons.head)
        (new WeakReference(lower), weakly(under(upper.parent().get)))
      }
      val before = fx(under(below.get()).size)
      fx(Modal.close())
      eventually(Modal.top.exists(_ eq below.get())) shouldBe true
      surviving(top, first, second) shouldBe 0
      // the one under it as it was, and the focus in it
      fx(under(below.get()).size) shouldBe before
      fx(Focus.showing(below.get())) shouldBe true
      val lower = fx(weakly(under(below.get().parent().get)))
      fx(Modal.close())
      eventually(!Modal.showing) shouldBe true
      surviving(lower, first, second) shouldBe 0
    }
    "be left whole when it is kept, to be put up again" in {
      val (first, second) = cards()
      val (panel, buttons) = fx(dialog("Filter", List("Close" -> (() => Modal.close()))))
      val count = fx(under(panel).size)
      fx(Modal.show(panel, buttons.head, keep = true))
      val sheet = fx(weakly(Vector(panel.parent().get)))
      fx(Modal.close())
      eventually(!Modal.showing) shouldBe true
      surviving(sheet, first, second) shouldBe 0
      fx(under(panel).size) shouldBe count
      fx(Modal.show(panel, buttons.head, keep = true))
      eventually(Focus.showing(buttons.head)) shouldBe true
      fx(Focus.focused()) shouldBe Some(buttons.head)
      fx(Modal.close())
    }
  }

  "A game's board with its quit question" should {
    "all be let go of once the game is put away" in {
      val (first, second) = cards()
      val stage = fx {
        val s = new Container
        ui.children += s
        s
      }
      var made = Vector.empty[WeakReference[Component]]
      def ask(n: Int): Unit = fx {
        val b = board(n)
        stage.children.replaceMaking(Vector(b))
        b.childComponents(1).childComponents.head.focus()
        made = made ++ weakly(under(b))
      }
      // Back over a question: whether to quit, over the board
      def quit(sure: Boolean): Unit = {
        fx {
          val (panel, buttons) = dialog("Quit the quiz?", List(
            "Keep playing" -> (() => Modal.close()),
            "Quit" -> (() => {
              Modal.close()
              stage.children.replaceMaking(Vector.empty)
            })))
          Modal.show(panel, buttons.head)
          made = made ++ weakly(under(panel.parent().get))
          if (sure) buttons(1).focus()
        }
        press("Enter")
        eventually(!Modal.showing) shouldBe true
      }
      ask(1)
      quit(sure = false)
      ask(2)
      ask(3)
      quit(sure = true)
      fx(stage.children().isEmpty) shouldBe true
      withClue(s"of ${made.size}: ") {
        surviving(made, first, second) shouldBe 0
      }
    }
  }
}
