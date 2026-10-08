package everyui.app

import everyui.component.{Button, Component, TextView, VideoView}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A VideoView's centre mix (MediaSupport.centerMix): a surround file playing in a loop, and buttons that set its
  * centre to -6, 0, +3, +6 and +12 dB as it plays, with whether the platform can (`centerMixSupported`) and whether
  * it is heard (`centerMixing`) under them. `src=` gives the file (by default `centre51.mp4` beside the page: the web
  * only hears a file from the page's own origin, or one that allows it), `db=` the level it starts at, and `steps=1`
  * walks 0, +3, +6 and back to 0 by itself, four seconds each, for a device with no one at its remote, and `then=`
  * plays another file twelve seconds in. Logs
  * `centermix db=<level> supported=<on> mixing=<on>` at each change; the platform says how loud the centre went in
  * and each side came out (`media: centre mix …`). */
object CenterMixDemo {
  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val video = new VideoView
    video.cover @= false
    video.loop @= true
    video.centerMix @= Platform.launchArg("db").toDoubleOption.getOrElse(0.0)
    def said(): Unit = Platform.log("centermix db=" + video.centerMix() + " supported=" + video.centerMixSupported() +
      " mixing=" + video.centerMixing())
    video.centerMix.attach(_ => said())
    video.centerMixSupported.attach(_ => said())
    video.centerMixing.attach(_ => said())
    video.events(e => Platform.log("video " + e + (if (e == "error") " " + video.diagnostics.failure else "")))

    val levels = List(-6.0, 0.0, 3.0, 6.0, 12.0)
    val buttons = levels.map { db =>
      Button.bound((if (db > 0.0) "+" else "") + db.toInt + " dB" + (if (video.centerMix() == db) " *" else ""),
        () => video.centerMix @= db)
    }
    val status = TextView.bound((if (video.centerMixSupported()) "Supported here" else "Not supported here") +
      (if (video.centerMixing()) ", folding the centre" else ", not folding: " +
        (if (video.centerMixSupported()) "stereo, or an output that plays every channel" else "nothing to fold with")),
      14.0, Theme.muted())

    video.position.left @= 16.0
    video.position.top @= 16.0
    video.size.width := ui.size.width() - 32.0
    video.size.height := math.max(120.0, math.min((ui.size.width() - 32.0) * 9.0 / 16.0, ui.size.height() - 150.0))
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
      val walk = List(0.0, 3.0, 6.0, 0.0)
      def step(i: Int): Unit = Platform.after(4.0, () => {
        video.centerMix @= walk(i % walk.size)
        step(i + 1)
      })
      step(0)
    }
    Platform.launchArg("then") match {
      case "" =>
      case later => Platform.after(12.0, () => video.source @= later)
    }
    video.source @= (Platform.launchArg("src") match {
      case "" => "centre51.mp4"
      case src => src
    })
    said()
  }
}
