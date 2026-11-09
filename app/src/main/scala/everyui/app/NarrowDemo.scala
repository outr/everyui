package everyui.app

import everyui.component.{Column, Container, Fill, Link, Row, TextInput, TextView}
import everyui.layout.FlowLayout
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A form and rows of links in a card as wide as the window, to be narrowed to a phone's width and widened again: the
  * fields follow the card both ways (bound to the card, which has a width of its own, or filling a row with `Fill`), a
  * row of links too wide for the card runs past its edge and is cut there, each link's words whole on their lines, and
  * the same links in a flow go onto as many lines as they need. With `cycle=1` the fields are bound to the column they
  * are in, which is sized to them, instead: they grow with the window and never shrink, the idiom CONSUMING.md warns
  * of. Logs `narrow ok`, then the widths as the window changes:
  *
  *   [ui] narrow card 358 to 326 from 326 jobs 28x19
  */
object NarrowDemo {
  def run(): Unit = {
    Theme.family @= "Open Sans"
    ui.backgroundColor := Theme.background()
    val cycle = Platform.launchArg("cycle") == "1"
    val card = new Container {
      backgroundColor := Theme.raised()
      radius @= 12.0
      position.left @= 16.0
      position.top @= 16.0
      size.width := ui.size.width() - 32.0
    }
    val to = new TextInput("To")
    val cc = new TextInput("Cc")
    val subject = new TextInput("Subject")
    val from = new TextInput("Address")
    val fromRow = Row(8.0, middle = true)(TextView("From"), Fill(from))
    val words = List("Home", "About", "Jobs", "Press", "Contact us", "Privacy policy", "Terms of service")
    def links: List[Link] =
      words.map(w => Link("#" + w.toLowerCase.replace(' ', '-'))(TextView(w, 14.0, Theme.accentText())))
    val row = Row(24.0)(links*)
    val linkTexts = row.children().collect { case l: Link => l.children().head }
    val flow = new Container {
      layout @= Some(FlowLayout(24.0, 8.0))
      children ++= links
    }
    val form = Column(12.0, 16.0)(TextView("New message", 20.0), to, cc, subject, fromRow,
      TextView("A row of links:", 12.0, Theme.muted()), row, TextView("The same links in a flow:", 12.0, Theme.muted()),
      flow)
    if (cycle) List(to, cc, subject).foreach(f => f.size.width := form.size.width() - 32.0)
    else {
      form.size.width := card.size.width()
      List(to, cc, subject).foreach(f => f.size.width := card.size.width() - 32.0)
    }
    fromRow.size.width := card.size.width() - 32.0
    flow.size.width := card.size.width() - 32.0
    card.size.height := form.size.height()
    card.children += form
    ui.children += card
    Platform.log("narrow ok")
    def report(): Unit = Platform.after(0.5, () => {
      val jobs = linkTexts(2)
      Platform.log("narrow card " + card.size.width().round.toString + " to " + to.size.width().round.toString +
        " from " + from.size.width().round.toString + " jobs " + jobs.size.width().round.toString + "x" +
        jobs.size.height().round.toString)
    })
    ui.size.width.attach(_ => report())
    report()
  }
}
