package everyui.app

import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, Component, Container, TextView}
import everyui.event.Key
import everyui.focus.Focus
import everyui.layout.{FlowLayout, VerticalLayout}
import everyui.net.URLPath
import everyui.render.Platform
import everyui.theme.Theme

/** Browse as NaboTV has it: a filter rail down the left, a Filters button only a phone shows (hidden here), and a grid
  * of cards, side by side in a container that lays nothing out. Kept between visits, and read again on each: the grid
  * is replaced with words for loading, and a second later with new cards, the focus last on a card that is gone. */
class BrowseDemoScreen(val manager: ScreenManager, open: () => Unit) extends UIScreen {
  override def title: String = "Browse"
  override def path: URLPath = URLPath("/")

  private var visits = 0
  private val grid = new Container {
    layout @= Some(FlowLayout(12.0, 16.0))
    position.left @= 260.0
    position.top @= 120.0
  }
  private val filters = new Button("Filters") {
    position.left @= 1000.0
    position.top @= 40.0
    visible @= false
  }
  private val rail = new Container {
    layout @= Some(VerticalLayout(12.0))
    position.left @= 40.0
    position.top @= 120.0
    children ++= List(new Button("Home"), new Button("Clear filters"))
  }
  private val body = new Container {
    size.width := container.size.width()
    size.height := container.size.height()
  }

  /** As NaboTV's: the grid's first card, else the screen itself. */
  override def defaultFocus(): Component = Focus.candidates(grid).headOption.getOrElse(container)

  override def createUI(): Task[Unit] = Task {
    container.backgroundColor := Theme.background()
    rail.exitFocus(Key.ArrowRight, grid)
    grid.exitFocus(Key.ArrowLeft, rail)
    grid.size.width := container.size.width() - 260.0 - 40.0
    val heading = TextView("Browse", 28.0)
    heading.position.left @= 40.0
    heading.position.top @= 40.0
    body.children ++= List(heading, grid, filters, rail)
    container.children += body
    Focus.focused.attach(f => Platform.log("browse: focus on " + f.map(c => Focus.named(c) +
      (if (Focus.navigable(c)) "" else " (not navigable)")).getOrElse("nothing")))
    fill()
  }

  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    if (visits > 0) {
      // read again: the words for loading in place of the cards, then the new cards
      grid.children.replaceMaking(Vector(TextView("Loading…", 20.0)))
      Platform.after(1.0, () => fill())
    }
  }

  private def fill(): Unit = {
    visits += 1
    val cards = (1 to 12).toVector.map(i => new Button("Visit " + visits + " card " + i, () => open()))
    grid.children.replaceMaking(cards)
    // NaboTV's check: the first card, where the focus is on nothing a remote can use
    val lost = Focus.focused().forall(f => !Focus.showing(f) || !f.focusable())
    Platform.log("browse: visit " + visits + " filled, the app's check finds the focus " + (if (lost) "lost" else "fine"))
    if (lost) cards.headOption.foreach(c => Platform.later(() => c.focus()))
  }
}
