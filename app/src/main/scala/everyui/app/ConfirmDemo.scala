package everyui.app

import everyui.app.screen.ScreenManager

/** A form a phone's keyboard comes up over and goes from (`ConfirmScreen`): a code to type, a secondary button and the
  * primary under it, each press logged ("[ui] confirm: pressed Confirm"), for a tap on the primary as the keyboard
  * closes to be a click on it, the card staying where it was. */
object ConfirmDemo extends ScreenManager {
  lazy val screen: ConfirmScreen = new ConfirmScreen

  def run(): Unit = run(screen)
}
