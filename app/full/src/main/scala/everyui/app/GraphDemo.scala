package everyui.app

import everyui.app.screen.ScreenManager
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui

/** A project's modules and what each depends on, on a GraphView: sixteen modules in four groups, each in its group's
  * colour, two planned ones drawn dashed with their planned dependencies, and a dependency that breaks the rule that
  * the domain never depends on the interface, in red (it makes a cycle, which the layout turns round). Hover or choose
  * a module to light what it depends on and what depends on it; the arrows move among them; the bar turns the flow
  * from left to right to top to bottom, adds a planned module (the graph lays itself out again, gliding), and fits it
  * all in. On a phone it runs top to bottom, with the buttons under its name. `theme=light` shows the light palette,
  * `flow=down` starts top to bottom and `flow=across` left to right wherever it is, and `big=<n>` shows a made-up graph
  * of n modules instead. Logs `graph ok <nodes> nodes, <edges> edges, laid out in <ms> ms`, and each module chosen
  * (`GraphScreen`). */
object GraphDemo extends ScreenManager {
  lazy val screen: GraphScreen = new GraphScreen

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    run(screen)
  }
}
