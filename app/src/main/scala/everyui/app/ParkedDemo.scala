package everyui.app

import everyui.component.VideoView
import everyui.render.Platform
import everyui.ui

/** A video made ready ahead of time and not played, as a screensaver's next clip is (`src=<url>`): held, it loads only
  * its first seconds, where it used to fill the player's whole buffer while it waited. `play=<seconds>` lets it go
  * after that long, when it loads as the buffering says. Logs `parked ok`. */
object ParkedDemo {
  def run(): Unit = {
    val video = new VideoView
    video.playing @= false
    video.muted @= true
    video.size.width @= 320.0
    video.size.height @= 180.0
    video.source @= Platform.launchArg("src")
    ui.children += video
    Platform.launchArg("play").toDoubleOption.foreach(s => Platform.after(s, () => video.playing @= true))
    Platform.log("parked ok")
  }
}
