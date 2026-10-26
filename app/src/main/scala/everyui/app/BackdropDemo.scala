package everyui.app

import everyui.component.{Backdrop, Button, TextView}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A backdrop's video (`Backdrop.videoSource`), for checking a backend plays one behind a screen: the picture `src` at
  * once, and the looping `video` in its place once it can play through, cut or (`crossfade=1`) faded. A button turns
  * the video off and on, as an app's setting would; what it shows is said in its corner, and in the log. `idle=<s>` is
  * how long nobody uses it before the video rests. `late=1` puts the backdrop on the screen a second after its video was
  * asked for, as a screen manager starting does, and `move=1` takes it off and puts it back just after. */
object BackdropDemo {
  def run(): Unit = {
    def arg(name: String, otherwise: String): String = {
      val named = Platform.launchArg(name)
      if (named.nonEmpty) named else otherwise
    }
    val video = arg("video", "backgrounds/void-blue-veil-loop.webm")
    val back = new Backdrop
    back.size.width := ui.size.width()
    back.size.height := ui.size.height()
    back.crossfade @= arg("crossfade", "0") == "1"
    back.source @= arg("src", "backgrounds/void-blue-veil.jpg")
    back.idleSeconds @= arg("idle", Backdrop.idleDefault.toString).toDouble
    back.videoSource @= video

    val said = new TextView {
      content := (if (back.videoShown()) "video" else if (back.videoFailed()) "picture (the video failed)"
        else if (back.videoResting()) "picture (the video rests: nobody is using the app)" else "picture")
      font.size @= 14.0
      color := Theme.text()
      position.left @= 24.0
      position.top @= 24.0
    }
    back.videoShown.attach(on => Platform.log("backdrop " + (if (on) "video" else "picture")))
    back.videoFailed.attach(f => if (f) Platform.log("backdrop video failed"))
    back.videoResting.attach(r => if (r) Platform.log("backdrop video resting"))
    val toggle = new Button("Background video", () => back.videoSource @= (if (back.videoSource().isEmpty) video else ""))
    toggle.position.left @= 24.0
    toggle.position.top @= 56.0

    if (arg("late", "0") == "1") {
      ui.children ++= List(said, toggle)
      Platform.after(1.0, () => ui.children @= back +: ui.children())
    }
    else ui.children ++= List(back, said, toggle)
    // taken off the screen and put back a moment later
    if (arg("move", "0") == "1") Platform.after(0.3, () => {
      ui.children -= back
      Platform.after(0.1, () => ui.children @= back +: ui.children())
    })
    // the frame loop a screen manager would run, so a cross-fade steps
    var last = -1.0
    everyui.task.Frames.run(_ => {
      val now = Platform.ticks()
      ui.update(if (last < 0.0) 0.0 else (now - last) / 1000.0)
      last = now
    }, () => ui.needsFrames, () => last = -1.0)
  }
}
