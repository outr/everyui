package everyui.app

import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Button, Component, Container, Row, ScrollContainer, TextView}
import everyui.focus.Focus
import everyui.layout.HorizontalLayout
import everyui.menu.{Menu, MenuItem}
import everyui.net.*
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{PlatformKind, ui}

/** The coach demo's one screen: a bar with Ask on it, a rail of twelve titles that glides with the focus, each with a
  * menu held up on it, and a title's Play and Trailer under them. The tour starts once it shows. */
class CoachScreen extends ScrollScreen {
  override def manager: ScreenManager = CoachDemo
  override def title: String = "Coach"
  override def path: URLPath = path"/coach"

  private val narrow = ui.size.width() < 700.0
  private val ask = new Button("Ask", () => Platform.log("coach asked"))
  private val rail = new ScrollContainer(horizontal = true) {
    size.width := ui.size.width() - 32.0
    size.height @= 124.0
    revealGlide @= 0.4
    content.layout @= Some(HorizontalLayout(16.0))
  }
  private val cards = Vector.tabulate(12)(i => card("Title " + (i + 1).toString, if (narrow) 150.0 else 220.0))
  private val play = new Button("Play", () => Platform.log("coach played"))
  // room under the rail for the note, which would otherwise sit over the title's row
  private val gap = new Container {
    size.height @= 56.0
  }

  override def defaultFocus(): Component = cards.head

  override def createUI(): Task[Unit] = Task {
    rail.content.children := cards
    body.children ++= List(Row(12.0)(new Button("Home"), new Button("Search"), ask), TextView("Weekly picks", 20.0),
      rail, gap, Row(12.0)(play, new Button("Trailer")))
    Focus.focused.attach(f => Platform.log("coach focus " + f.map(_.kind).getOrElse("nothing")))
    Platform.after(0.5, () => {
      val tour = new CoachTour(rail, cards, play, ask)
      if (Platform.launchArg("probe") == "1") tour.probe()
    })
  }

  /** A title: its name on a raised box, ringed while focused, with a menu held up on it. */
  private def card(name: String, width: Double): Container = {
    val c = new Container {
      override def kind: String = name
      size.width @= width
      size.height @= 104.0
      radius @= 12.0
      backgroundColor := Theme.raised()
      borderColor := Theme.accent()
      borderWidth := (if (focused()) 3.0 else 0.0)
      focusable @= true
    }
    val label = TextView(name, 16.0)
    label.position.left @= 14.0
    label.position.top @= 14.0
    c.children += label
    // in a browser the card under the mouse takes the focus, as NaboTV's do
    if (Platform.kind == PlatformKind.Web) c.event.pointer.enter.attach(_ => if (!c.focused()) c.focus())
    Menu.context(c)(List(
      MenuItem("Mark watched", () => Platform.log("coach marked " + name)),
      MenuItem("Add to list", () => Platform.log("coach listed " + name))))
    c
  }
}
