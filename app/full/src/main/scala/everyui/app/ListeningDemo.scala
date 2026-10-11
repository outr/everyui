package everyui.app

import everyui.component.{Button, Component, TextView, VideoView}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A VideoView's listening aids (MediaSupport.voiceClarity and nightMode): a stereo file playing in a loop, and
  * buttons that set each aid off, to half and to full as it plays, with whether the platform can and whether each is
  * heard under them. `src=` gives the file (by default `listening.mp4` beside the page: a speech-band tone at a
  * syllable's pace over a low rumble, then loud bursts, then a quiet passage; the web only hears a file from the
  * page's own origin, or one that allows it), `clarity=` and `night=` the levels it starts at, `db=` a centre mix
  * ahead of them (for a surround file), and `steps=1` walks clarity 1 and 0, then night 1, both, and neither, four
  * seconds each, for a device with no one at its remote; `muted=1` plays it muted (the levels are still measured and
  * said, before the player's volume). Logs `listening clarity=<level> night=<level> supported=<on> clarityOn=<on>
  * nightOn=<on>` at each change; the platform says how loud the sound went in and came out (`media: listening aids
  * …`). */
object ListeningDemo {
  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val video = new VideoView
    video.cover @= false
    video.loop @= true
    video.muted @= Platform.launchArg("muted") == "1"
    video.voiceClarity @= Platform.launchArg("clarity").toDoubleOption.getOrElse(0.0)
    video.nightMode @= Platform.launchArg("night").toDoubleOption.getOrElse(0.0)
    // the centre mix ahead of them, for a surround file
    video.centerMix @= Platform.launchArg("db").toDoubleOption.getOrElse(0.0)
    def said(): Unit = Platform.log("listening clarity=" + video.voiceClarity() + " night=" + video.nightMode() +
      " supported=" + video.voiceClaritySupported() + " clarityOn=" + video.voiceClarityOn() + " nightOn=" +
      video.nightModeOn())
    video.voiceClarity.attach(_ => said())
    video.nightMode.attach(_ => said())
    video.voiceClaritySupported.attach(_ => said())
    video.voiceClarityOn.attach(_ => said())
    video.nightModeOn.attach(_ => said())
    video.events(e => Platform.log("video " + e + (if (e == "error") " " + video.diagnostics.failure else "")))

    val levels = List(0.0 -> "off", 0.5 -> "half", 1.0 -> "full")
    val buttons = levels.map { case (level, name) =>
      Button.bound("Clarity " + name + (if (video.voiceClarity() == level) " *" else ""), () => video.voiceClarity @= level)
    } ++ levels.map { case (level, name) =>
      Button.bound("Night " + name + (if (video.nightMode() == level) " *" else ""), () => video.nightMode @= level)
    }
    val status = TextView.bound((if (video.voiceClaritySupported()) "Supported here" else "Not supported here") +
      ": voice clarity " + (if (video.voiceClarityOn()) "heard" else "off") + ", night mode " +
      (if (video.nightModeOn()) "heard" else "off"), 14.0, Theme.muted())

    video.position.left @= 16.0
    video.position.top @= 16.0
    video.size.width := ui.size.width() - 32.0
    video.size.height := math.max(120.0, math.min((ui.size.width() - 32.0) * 9.0 / 16.0, ui.size.height() - 190.0))
    buttons.zipWithIndex.foreach { case (b, i) =>
      if (i == 0) {
        b.position.left @= 16.0
        b.position.top := video.position.top() + video.size.height() + 12.0
      }
      else {
        val before = buttons(i - 1)
        def wraps: Boolean =
          before.position.left() + before.size.width() + 8.0 + b.size.width() > ui.size.width() - 16.0
        b.position.left := (if (wraps) 16.0 else before.position.left() + before.size.width() + 8.0)
        b.position.top := (if (wraps) before.position.top() + before.size.height() + 8.0 else before.position.top())
      }
    }
    status.position.left @= 16.0
    status.position.top := buttons.last.position.top() + buttons.last.size.height() + 10.0
    ui.children ++= (video :: buttons) :+ (status: Component)

    if (Platform.launchArg("steps") == "1") {
      val walk = List((1.0, 0.0), (0.0, 0.0), (0.0, 1.0), (1.0, 1.0), (0.0, 0.0))
      def step(i: Int): Unit = Platform.after(4.0, () => {
        val (c, n) = walk(i % walk.size)
        video.voiceClarity @= c
        video.nightMode @= n
        step(i + 1)
      })
      step(0)
    }
    video.source @= (Platform.launchArg("src") match {
      case "" => "listening.mp4"
      case src => src
    })
    said()
  }
}
