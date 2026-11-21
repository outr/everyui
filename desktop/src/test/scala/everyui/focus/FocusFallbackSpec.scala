package everyui.focus

import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, Component, Container}
import everyui.event.Key
import everyui.keymap.Keymap
import everyui.layout.{FlowLayout, HorizontalLayout, VerticalLayout}
import everyui.net.URLPath
import everyui.render.{Host, Nodes}
import everyui.task.Frames
import everyui.{FxSpec, ui}

/** Where the focus goes when what it was on leaves the screen, on a remote: to what took its place, by the tree and the
  * layouts, never to something hidden nor to a part nothing leads to; with nothing to take it yet (results still
  * loading), it is held on the screen, which no app takes for a place to navigate from, and goes to where the screen
  * starts as soon as there is somewhere, by the next arrow at the latest. Browse as NaboTV has it: a kept screen, its
  * grid of cards replaced on each visit, beside a filter rail, with a button only a phone shows. */
class FocusFallbackSpec extends FxSpec {
  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private def card(): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c.size.width @= 100.0
    c.size.height @= 150.0
    c
  }

  private def cards(n: Int): Vector[Component] = Vector.fill(n)(card())

  /** A browse screen: a hero, the grid in its scroll, a Filters button a television hides, and the filter rail, side
    * by side in a container that lays nothing out, as NaboTV's body is. */
  private class Browse(val manager: ScreenManager) extends UIScreen {
    override def title: String = "Browse"
    override def path: URLPath = URLPath("/fallback/browse")
    val grid: Container = new Container {
      layout @= Some(FlowLayout(12.0, 16.0))
      size.width @= 460.0
    }
    val scroll: Container = new Container {
      position.left @= 120.0
      position.top @= 200.0
      children += grid
    }
    val filters: Button = new Button("Filters") {
      visible @= false
    }
    val homeRow: Component = card()
    val handle: Component = card()
    val rail: Container = new Container {
      layout @= Some(VerticalLayout(0.0))
      children ++= List(homeRow, handle)
    }
    val body = new Container

    /** As NaboTV's: the grid's first card, else the screen itself. */
    override def defaultFocus(): Component = Focus.candidates(grid).headOption.getOrElse(container)

    def root: Container = container

    override def createUI(): Task[Unit] = Task {
      grid.children ++= cards(8)
      body.children ++= List(new Container, scroll, filters, rail)
      rail.exitFocus(Key.ArrowRight, scroll)
      scroll.exitFocus(Key.ArrowLeft, homeRow)
      container.children += body
    }

    /** NaboTV's check after it fills the grid: the first card, where the focus is on nothing a remote can use. */
    def nabosCheck(): Boolean = {
      val lost = Focus.focused().forall(f => !Focus.showing(f) || !f.focusable())
      if (lost) Focus.candidates(grid).headOption.foreach(c => everyui.render.Platform.later(() => c.focus()))
      lost
    }
  }

  private class Other(val manager: ScreenManager) extends UIScreen {
    override def title: String = "Other"
    override def path: URLPath = URLPath("/fallback/other")
    val button: Component = card()
    override def defaultFocus(): Component = button
    override def createUI(): Task[Unit] = Task {
      container.children += button
    }
  }

  private object Manager extends ScreenManager

  /** A browse screen shown, left for another and come back to (Back), its fourth card focused again, as a remote runs
    * it. */
  private def revisited[T](test: Browse => T): T = {
    val was = fx(Host.args)
    fx(Host.args = was + ("remote" -> "true"))
    try {
      val browse = fx(new Browse(Manager))
      val other = fx(new Other(Manager))
      fx {
        ui.children.clear()
        Focus.focused @= None
        everyui.render.Platform.pushPath("/fallback/browse")
        Manager.run(browse, other)
      }
      eventually(Manager.active().exists(_ eq browse) && Focus.focused().exists(_ eq browse.grid.children()(0))) shouldBe true
      fx(browse.grid.children()(3).focus())
      fx(Manager.show(other).startUnit())
      eventually(Focus.focused().exists(_ eq other.button)) shouldBe true
      fx(Manager.back())
      eventually(Manager.active().exists(_ eq browse) && Focus.focused().exists(_ eq browse.grid.children()(3))) shouldBe true
      test(browse)
    }
    finally fx {
      Frames.drive(_ => (), () => false, () => (), _ => ())
      ui.children.clear()
      Focus.focused @= None
      Focus.home @= (() => None)
      Focus.page @= (() => None)
      Keymap.shown @= (() => None)
      ScreenManager.running = None
      Host.args = was
    }
  }

  private def on(c: Component): Boolean = fx(now(c))

  // on the FX thread already, as in `eventually`
  private def now(c: Component): Boolean = Focus.focused().exists(_ eq c)

  "A kept screen whose focused card is replaced" should {
    "hold the focus on the screen while its results load, then put it on the first new card, the arrows moving" in {
      revisited { browse =>
        // a reload: the cards go, the words for loading in their place
        fx(browse.grid.children.replaceMaking(Vector(new Container)))
        eventually(Focus.focused().exists(_ eq browse.root)) shouldBe true
        // never the button a television hides, nor the rail beside the grid
        on(browse.filters) shouldBe false
        fx(Focus.navigable(browse.root)) shouldBe false
        // NaboTV's own check takes it for lost
        fx(Focus.focused().forall(f => !Focus.showing(f) || !f.focusable())) shouldBe true
        // the results: the focus goes to where the screen starts as they arrive, with no help from the app
        val fresh = fx(cards(8))
        fx(browse.grid.children.replaceMaking(fresh))
        eventually(now(fresh(0))) shouldBe true
        press("ArrowRight")
        on(fresh(1)) shouldBe true
        press("ArrowDown")
        on(fresh(5)) shouldBe true
        press("ArrowLeft")
        on(fresh(4)) shouldBe true
        press("ArrowUp")
        on(fresh(0)) shouldBe true
        press("ArrowLeft")
        on(browse.homeRow) shouldBe true
      }
    }

    "let the app's own check find it lost, and put it on the first card" in {
      revisited { browse =>
        fx(browse.grid.children.replaceMaking(Vector(new Container)))
        eventually(Focus.focused().exists(_ eq browse.root)) shouldBe true
        // the app fills the grid and checks, as NaboTV's render does, in the same turn
        val fresh = fx(cards(8))
        fx {
          browse.grid.children.replaceMaking(fresh)
          browse.nabosCheck()
        } shouldBe true
        eventually(now(fresh(0))) shouldBe true
        press("ArrowDown")
        on(fresh(4)) shouldBe true
      }
    }

    "go to the card that took its place, where the new cards come in one go" in {
      revisited { browse =>
        val fresh = fx(cards(8))
        fx {
          browse.grid.children.replaceMaking(fresh)
          // in the same turn, the old card still focused: the app's check finds it gone, and asks for the first
          browse.nabosCheck()
        } shouldBe true
        eventually(now(fresh(0)) || now(fresh(3))) shouldBe true
        val at = fx(fresh.indexWhere(c => Focus.focused().exists(_ eq c)))
        press("ArrowRight")
        on(fresh(at + 1)) shouldBe true
      }
    }

    "go to where it was, by the tree, where the app leaves it to EveryUI" in {
      revisited { browse =>
        val fresh = fx(cards(8))
        fx(browse.grid.children.replaceMaking(fresh))
        eventually(now(fresh(3))) shouldBe true
        press("ArrowDown")
        on(fresh(7)) shouldBe true
      }
    }

    "start the arrows again from the screen while nothing can take the focus" in {
      revisited { browse =>
        fx(browse.grid.children.replaceMaking(Vector(new Container)))
        eventually(Focus.focused().exists(_ eq browse.root)) shouldBe true
        // no card yet: the first that can be reached in the tree, where the screen starts nowhere yet
        press("ArrowDown")
        on(browse.homeRow) shouldBe true
        press("ArrowDown")
        on(browse.handle) shouldBe true
      }
    }
  }

  "A focused part disposed in the middle of a row" should {
    "go to its neighbour in the row" in {
      val was = fx(Host.args)
      fx(Host.args = was + ("remote" -> "true"))
      try {
        val parts = fx(cards(5))
        val (row, other) = fx {
          val row = new Container
          row.layout @= Some(HorizontalLayout(10.0))
          row.children ++= parts
          val other = card()
          ui.children.clear()
          Focus.focused @= None
          val screen = new Container
          screen.children ++= List(other, row)
          ui.children += screen
          Focus.page @= (() => Some(screen))
          (row, other)
        }
        fx {
          parts(2).focus()
          parts(2).dispose()
        }
        eventually(now(parts(3))) shouldBe true
        press("ArrowLeft")
        on(parts(1)) shouldBe true
        // the last of a row gone: the one before it
        fx {
          parts(4).focus()
          row.children -= parts(4)
        }
        eventually(now(parts(3))) shouldBe true
        on(other) shouldBe false
      }
      finally fx {
        ui.children.clear()
        Focus.focused @= None
        Focus.page @= (() => None)
        Host.args = was
      }
    }
  }

  "A part hidden" should {
    "never take the focus, by an arrow nor as a successor" in {
      val was = fx(Host.args)
      fx(Host.args = was + ("remote" -> "true"))
      try {
        val parts = fx(cards(4))
        val row = fx {
          val row = new Container
          row.layout @= Some(HorizontalLayout(10.0))
          row.children ++= parts
          parts(1).visible @= false
          ui.children.clear()
          Focus.focused @= None
          val screen = new Container
          screen.children += row
          ui.children += screen
          Focus.page @= (() => Some(screen))
          row
        }
        fx(parts(0).focus())
        press("ArrowRight")
        on(parts(2)) shouldBe true
        fx(Focus.navigable(parts(1))) shouldBe false
        fx(Focus.candidates(row).exists(_ eq parts(1))) shouldBe false
        // the one after it gone, the hidden one is passed over for the one before it
        fx(row.children -= parts(2))
        eventually(now(parts(3))) shouldBe true
        fx(row.children -= parts(3))
        eventually(now(parts(0))) shouldBe true
      }
      finally fx {
        ui.children.clear()
        Focus.focused @= None
        Focus.page @= (() => None)
        Host.args = was
      }
    }
  }
}
