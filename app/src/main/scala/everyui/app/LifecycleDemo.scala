package everyui.app

import everyui.component.TextView
import everyui.render.Platform
import everyui.ui

/** Each time the app goes away and comes back (`Platform.onAway`, `Platform.onResume`): logged ("[ui] lifecycle:
  * away 1", "[ui] lifecycle: resumed 1") and written on the screen with how many of each so far. On the web another tab
  * in front, the window minimised or the page left and brought back from the back/forward cache; on an Apple TV
  * another app opened over it or the TV's home screen; on Android another app in front. */
object LifecycleDemo {
  private val words = new TextView
  private var aways = 0
  private var resumes = 0

  def run(): Unit = {
    words.position.left @= 40.0
    words.position.top @= 40.0
    ui.children += words
    show("started")
    Platform.onAway(() => {
      aways += 1
      show("away " + aways.toString)
    })
    Platform.onResume(() => {
      resumes += 1
      show("resumed " + resumes.toString)
    })
  }

  private def show(line: String): Unit = {
    words.content @= line + " (away " + aways.toString + ", resumed " + resumes.toString + ")"
    Platform.log("lifecycle: " + line)
  }
}
