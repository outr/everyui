package everyui.app

import everyui.component.{Checkbox, Component, Container, TextView}
import everyui.kit.Spinner
import everyui.menu.{MenuItem, SplitButton}
import everyui.render.Platform
import everyui.ui
import reactify.*

/** What is made, or changed, in a hidden panel has its size when the panel shows: a split button, a compact checkbox
  * and words changed a turn later, each against its twin made in sight; and a spinner made hidden in a panel not yet on
  * the screen has the size it was given when it shows. It prints one line (tools/check.sh):
  *
  *   [ui] hidden ok
  */
object HiddenDemo {
  def run(): Unit = {
    val (seen, seenButton, seenBox, seenWords) = panel(40.0)
    val (hidden, hiddenButton, hiddenBox, hiddenWords) = panel(200.0)
    hidden.visible @= false
    ui.children ++= List(seen, hidden)
    seenWords.content @= "Changed while hidden"
    Platform.after(0.3, () => hiddenWords.content @= "Changed while hidden")
    // measured again as its panel is put on the screen, hidden, it read 0 by 0 on the web
    val spinner = new Spinner(18.0)
    spinner.visible @= false
    val late = new Container
    late.position.left @= 40.0
    late.position.top @= 360.0
    late.children += spinner
    Platform.after(0.3, () => ui.children += late)
    Platform.after(0.6, () => {
      hidden.visible @= true
      spinner.visible @= true
    })
    Platform.after(1.0, () => {
      val pairs = List("button" -> (seenButton, hiddenButton), "checkbox" -> (seenBox, hiddenBox),
        "words" -> (seenWords, hiddenWords))
      val wrong = pairs.collect {
        case (name, (a, b)) if a.size.width() != b.size.width() || a.size.width() <= 0.0 =>
          name + " " + a.size.width() + " vs " + b.size.width()
      } ++
        (if (spinner.size.width() == 18.0 && spinner.size.height() == 18.0) Nil
         else List("spinner " + spinner.size.width() + " by " + spinner.size.height()))
      Platform.log(if (wrong.isEmpty) "hidden ok" else "hidden wrong: " + wrong.mkString(", "))
    })
  }

  private def panel(top: Double): (Container, Component, Component, TextView) = {
    val button = new SplitButton("Commit", () => (), List(MenuItem("Amend", () => ())))
    val box = new Checkbox("Amend", compact = true)
    box.position.left @= 200.0
    val words = new TextView
    words.content @= "Words"
    words.position.left @= 320.0
    val holder = new Container
    holder.position.left @= 40.0
    holder.position.top @= top
    holder.children ++= List(button, box, words)
    (holder, button.main, box, words)
  }
}
