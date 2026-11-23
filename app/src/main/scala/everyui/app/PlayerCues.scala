package everyui.app

import everyui.component.{Container, MediaSidecar, MediaSupport, TextChoice, VideoView}
import everyui.render.Platform
import everyui.ui

/** The subtitles of a player as a TV app reads them (PlayerDemo with `cues=1`): a player screen put up, `src=` opened
  * `start=` seconds in with the sidecars in `side=` (`url|label|language`, comma separated), and once it is ready, the
  * first sidecar read (a step after the second sound track is chosen, where `audio=1` asks: a TV's playlist is then
  * played through hls.js, whose subtitles are read hidden); `step=` seconds (4) later the video moved into a mini
  * player's box (built off the page and put on it, as NaboTV's picture-in-picture is), the sidecar still read; a step
  * later the first of the source's own text tracks read, where it has one; a step later none; and half a step later
  * the screen left. Logs, for a check to follow (webcheck's PlayerCheck):
  *
  *   player cues ready tracks=<kind:id:language:label|...>
  *   player cues audio <id>             (with `audio=1`, where the source has two sound tracks)
  *   player cues side <id> <label>
  *   player cues moved
  *   player cues text <id> <label>      (where the source has a text track of its own)
  *   player cues off
  *   player cues left held=<MediaSupport.held> */
object PlayerCues {
  def run(source: String): Unit = {
    val start = Platform.launchArg("start").toDoubleOption.getOrElse(0.0)
    val step = Platform.launchArg("step").toDoubleOption.getOrElse(4.0)
    val sides = Platform.launchArg("side").split(',').toList.map(_.trim).filter(_.nonEmpty).map { s =>
      val p = s.split('|')
      MediaSidecar(p(0), if (p.length > 1) p(1) else "", if (p.length > 2) p(2) else "")
    }
    def say(what: String): Unit = Platform.log("player cues " + what)
    val screen = new Container
    screen.size.width := ui.size.width()
    screen.size.height := ui.size.height()
    val video = new VideoView
    video.size.width := ui.size.width()
    video.size.height := ui.size.height()
    video.muted @= true
    screen.children += video
    ui.children += screen
    val mini = new Container
    var ready = false
    def later(steps: Double, f: () => Unit): Unit = Platform.after(step * steps, f)
    def read(id: String): Unit = video.captions @= TextChoice.TextTrack(id)
    video.events { e =>
      if (e == "error") say("error " + video.diagnostics.failure)
      if (e == "ready" && !ready) {
        ready = true
        say("ready tracks=" +
          video.tracks.map(t => t.kind + ":" + t.id + ":" + t.language + ":" + t.label).mkString("|"))
        val audio = video.audioTracks
        val first = if (Platform.launchArg("audio") == "1" && audio.size > 1) {
          video.audioTrack @= Some(audio(1).id)
          say("audio " + audio(1).id)
          1.0
        } else 0.0
        later(first, () => video.textTracks.find(_.id.startsWith("side")).foreach { t =>
          read(t.id)
          say("side " + t.id + " " + t.label)
        })
        later(first + 1.0, () => {
          mini.size.width @= 480.0
          mini.size.height @= 270.0
          mini.position.left := ui.size.width() - 500.0
          mini.position.top @= 20.0
          video.size.width @= 480.0
          video.size.height @= 270.0
          mini.children += video
          ui.children += mini
          say("moved")
        })
        later(first + 2.0, () => video.textTracks.find(!_.id.startsWith("side")).foreach { t =>
          read(t.id)
          say("text " + t.id + " " + t.label)
        })
        later(first + 3.0, () => {
          video.captions @= TextChoice.TextOff
          say("off")
        })
        later(first + 3.5, () => {
          ui.children -= screen
          ui.children -= mini
          screen.dispose()
          mini.dispose()
          say("left held=" + MediaSupport.held.toString)
        })
      }
    }
    video.sidecars @= sides
    video.open(source, start)
  }
}
