package everyui.app

import everyui.app.screen.ScreenManager
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui

/** Words seen together in a pile of notes, on a GraphView laid out by forces: a few hundred words in eight topics, each
  * word in its topic's colour and pushing the others away as hard as it is common, each pair linked as strongly as
  * the notes they share. The graph unfolds from the middle and stops when it settles; a word can be dragged (the rest
  * follow, and settle again once it is let go), Add brings in a word beside those it is seen with, Remove takes the
  * chosen one out, Shake warms it all again, and Fit fits it in. `theme=light` shows the light palette, `n=<words>`
  * makes more or fewer, and `measure=1` also settles layouts of 100, 1,000 and 3,000 words at once and logs how long
  * each took. Logs `forcegraph ok <words> words, <pairs> pairs` and, once it settles, `forcegraph settled in <ticks>
  * ticks, <ms> ms` (`ForceGraphScreen`). */
object ForceGraphDemo extends ScreenManager {
  lazy val screen: ForceGraphScreen = new ForceGraphScreen

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    run(screen)
  }
}
