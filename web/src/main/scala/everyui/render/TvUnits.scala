package everyui.render

/** The app's units on a TV, where the page is an installed app (`AppHosts`): the screen is 540 units tall, as Android
  * TV's 1080p screen is at its density of 2 (960 by 540) and a Roku's is, so a screen designed for one TV is the same
  * size on all of them. A unit is two CSS pixels at 1080, one and a third at 720. Plain Scala, so the desktop's specs
  * read it as the TV does. */
object TvUnits {
  /** How tall a TV's screen is in the app's units. */
  val tall: Double = 540.0

  /** CSS pixels to a unit, for a page `innerHeight` CSS pixels tall (1 where it is not known). */
  def scale(innerHeight: Double): Double = if (innerHeight > 0.0) innerHeight / tall else 1.0
}
