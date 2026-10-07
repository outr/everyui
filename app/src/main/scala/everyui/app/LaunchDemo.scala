package everyui.app

import everyui.component.TextView
import everyui.render.Platform
import everyui.ui

/** What the app was launched with, and each launch that reaches it once it runs (`Platform.onLaunch`): logged
  * ("[ui] launch: start open=…", "[ui] launch: later open=…") and written on the screen, with `launchArg("open")` as it
  * reads after each. On a Roku, ECP's `/launch/dev?open=…` to the running channel or to one suspended (Instant Resume)
  * is a later launch, as is `/input?open=…`; on Android, `am start --es open …` to the running activity. */
object LaunchDemo {
  private val words = new TextView

  def run(): Unit = {
    words.position.left @= 40.0
    words.position.top @= 40.0
    ui.children += words
    show("start open=" + Platform.launchArg("open") + " contentId=" + Platform.launchArg("contentId"))
    Platform.onLaunch(params => show("later " + params.toList.sortBy(_._1).map(p => p._1 + "=" + p._2).mkString(" ") +
      " | launchArg open=" + Platform.launchArg("open")))
  }

  private def show(line: String): Unit = {
    words.content @= line
    Platform.log("launch: " + line)
  }
}
