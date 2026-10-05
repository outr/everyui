package everyui.app

import everyui.coach.Spotlight
import everyui.component.{Component, Container, Modal, ScrollContainer, TextView}
import everyui.focus.Focus
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** The coach demo's tour (CoachDemo): a stop at a time, each with the light on what it is about, the focus held there,
  * and a note under it, moving on as the viewer does what the note says. Back on the title's page comes home to the
  * last stop; anywhere else it skips the tour. */
class CoachTour(rail: ScrollContainer, cards: Vector[Component], play: Component, ask: Component) {
  private val names = List("browse", "hold", "open", "play", "ask")
  private val words: Var[String] = Var("")
  private val light: Spotlight = Spotlight.on(List(rail), radius = 14.0, ring = 3.0)
  private val note: Container = {
    val width = math.min(360.0, ui.size.width() - 32.0)
    val said = TextView.paragraph(words(), width - 32.0, 16.0)
    said.position.left @= 16.0
    said.position.top @= 14.0
    new Container {
      size.width @= width
      size.height := said.size.height() + 28.0
      backgroundColor := Theme.raised()
      radius @= 12.0
      borderWidth @= 1.5
      borderColor := Theme.accent()
      children += said
      // under the hole, or over it where there is no room under
      position.left := math.max(16.0, math.min(ui.size.width() - width - 16.0, light.holeLeft()))
      position.top := {
        val under = light.holeBottom() + 18.0
        if (under + size.height() < ui.size.height() - 16.0) under
        else math.max(16.0, light.holeTop() - size.height() - 18.0)
      }
    }
  }
  // put up after the light, so it is over the dim and the pointer reaches it
  ui.children += note

  private var stop = -1
  private var seen: List[String] = Nil
  private var opened = 0
  private var played = 0
  // what a stop measures its moving on against: the card focused as it began, and how many dialogs had been put up
  private var focusedAt: Option[Component] = None
  private var menusAt = 0
  // the title opened, which Back from its page comes home to
  private var titled: Option[Component] = None
  private var release: () => Unit = () => ()

  cards.foreach(c => c.event.click.attach { _ =>
    opened += 1
    titled = Some(c)
    Platform.log("coach opened " + c.kind)
    // its row held, Play and Trailer both, with no arrow to move between them: nothing but Back
    if (stop == 2) go(3, play.parent().getOrElse(play), presses = false, holds = false,
      "Play starts it. Press Back to come home.", Some(play), arrows = false)
  })
  play.event.click.attach(_ => played += 1)
  // Back on the title's page comes home; anywhere else it ends the tour
  private val leaveBack = Focus.onBack(_ => if (stop == 3) {
    go(4, ask, presses = true, holds = true, "Ask is up here: any arrow finds it.", titled)
    true
  } else if (stop >= 0) {
    end()
    true
  } else false)

  go(0, rail, presses = true, holds = true, "Move along the rail with the arrows.", None)
  // and as the rail starts to glide, so the light is on the card from its first frame
  rail.offset.attach(_ => if (stop == 0) check())
  tick()

  /** The stop `to`: the focus on `focus` if given, then held in `region` as `presses`, `holds` and `arrows` say (the
    * last stop's let go), the light on it and the note saying `says`. */
  private def go(to: Int, region: Component, presses: Boolean, holds: Boolean, says: String,
                 focus: Option[Component], arrows: Boolean = true): Unit = {
    release()
    focus.foreach(_.focus())
    release = Focus.confine(region, presses, holds, arrows)
    stop = to
    seen = seen :+ names(to)
    focusedAt = Focus.focused()
    menusAt = Modal.opened
    light.targets @= List(region)
    words @= says
    Platform.log("coach stop " + names(to))
  }

  /** Each moment, a look at whether the viewer has done what the note asks. */
  private def tick(): Unit = if (stop >= 0) {
    check()
    if (stop >= 0) Platform.after(0.15, () => tick())
  }

  // whether the viewer has done what the note asks, and if so the next stop
  private def check(): Unit = {
    // the note out of the way of a dialog (the menu), as NaboTV's is
    note.visible @= !Modal.showing
    val now = Focus.focused()
    // along the rail far enough for it to glide: the next stop's light follows the card as it does
    if (stop == 0 && now.nonEmpty && rail.offset() > 0.0)
      go(1, now.get, presses = false, holds = true, "Hold OK on this title for its menu.", None)
    else if (stop == 1 && Modal.opened > menusAt && !Modal.showing)
      go(2, focusedAt.getOrElse(rail), presses = true, holds = false, "Now press OK to open it.", None)
    else if (stop == 4 && ask.focused()) end()
  }

  /** The tour over: the focus free, Back the platform's again, the light faded out and the note gone. */
  private def end(): Unit = if (stop >= 0) {
    release()
    release = () => ()
    leaveBack()
    stop = -1
    light.release()
    ui.children -= note
    note.dispose()
    Platform.log("coach done")
  }

  /** The stops walked through the focus layer: the arrows along the rail until it glides (and on, held back); a
    * short OK held back; OK held for the menu and its first row chosen; OK to open; OK, an arrow and the menu key
    * held back on Play; Back; and an arrow landing on Ask. */
  def probe(): Unit = {
    val menus = Modal.opened
    val steps: List[() => Unit] = List.tabulate(9)(_ => () => press("ArrowRight")) ++ List(
      () => press("Enter"),
      () => Focus.key("Enter", true),
      () => Focus.key("Enter", false),
      () => press("Enter"),
      () => press("Enter"),
      () => List("Enter", "ArrowRight", "ArrowUp", "ContextMenu").foreach(press),
      () => press("Back"),
      () => press("ArrowDown"),
      () => Platform.log("coach ok stops=" + seen.mkString(",") + " opened=" + opened.toString + " played=" +
        played.toString + " menus=" + (Modal.opened - menus).toString + " on=" +
        Focus.focused().map(f => if (f eq ask) "Ask" else f.kind).getOrElse("nothing")))
    def at(i: Int): Unit = if (i < steps.length) Platform.after(if (i < 9) 0.5 else 0.8, () => {
      steps(i)()
      at(i + 1)
    })
    at(0)
  }

  private def press(name: String): Unit = {
    Focus.key(name, true)
    Focus.key(name, false)
  }
}
