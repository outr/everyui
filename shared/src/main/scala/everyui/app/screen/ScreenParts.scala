package everyui.app.screen

import everyui.component.Component

/** The components made while a screen is being made (`Component.making`), for the screen to take apart with it: a
  * field it holds but never shows (an episode box on a film's page) is out of its container's reach. Kept apart from
  * the screen, so what `Component.making` last held (a reactify value keeps its previous one) holds only this, emptied
  * as the screen is taken apart, and never the screen with everything it built. */
final class ScreenParts {
  private var made: List[Component] = Nil

  def add(c: Component): Unit = made = c :: made

  /** `add` as a function, holding only this: what `Component.making` is given. */
  def adding: Component => Unit = (c: Component) => add(c)

  /** Every component made, now forgotten here. */
  def take(): List[Component] = {
    val all = made
    made = Nil
    all
  }
}
