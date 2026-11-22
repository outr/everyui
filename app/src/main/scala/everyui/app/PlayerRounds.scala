package everyui.app

import everyui.component.{Container, MediaSidecar, MediaSupport, MediaTrack, TextChoice, VideoView}
import everyui.render.Platform
import everyui.ui

/** A player screen entered and left `rounds` times, as a viewer opening a title, changing what it is heard and read in,
  * seeking and going back does (PlayerDemo with `rounds=<n>`): each round a screen with a VideoView is put up, the
  * sidecars in `side=` given (`url|label|language`, comma separated), `src=` opened `start=` seconds in (a resume),
  * and once it is ready, a step (`step=` seconds, 2) apart: the second sound track chosen where there is one, each
  * text track shown in turn, then none, a seek 10 s on, a step for it to land, the video moved into a mini player's
  * box (built off the page and put on it, as NaboTV's picture-in-picture is), a step for it to play on there, and the
  * screen and the box left and taken apart.
  * Logs, for a check to follow (webcheck's PlayerCheck):
  *
  *   player round <n> ready at <playhead> tracks=<kind:id:language:label|...>
  *   player round <n> audio <id> heard <diagnostics' audio tracks>
  *   player round <n> text <id> <label>          and   player round <n> text off
  *   player round <n> sought <playhead>
  *   player round <n> moved at <playhead>
  *   player round <n> after move <playhead> heard <diagnostics' audio tracks>
  *   player round <n> left held=<MediaSupport.held>
  *   player rounds done held=<MediaSupport.held> */
object PlayerRounds {
  private def tenths(seconds: Double): String = (math.round(seconds * 10.0) / 10.0).toString

  def run(source: String, rounds: Int): Unit = {
    val start = Platform.launchArg("start").toDoubleOption.getOrElse(0.0)
    val step = Platform.launchArg("step").toDoubleOption.getOrElse(2.0)
    val sides = Platform.launchArg("side").split(',').toList.map(_.trim).filter(_.nonEmpty).map { s =>
      val p = s.split('|')
      MediaSidecar(p(0), if (p.length > 1) p(1) else "", if (p.length > 2) p(2) else "")
    }
    def say(n: Int, what: String): Unit = Platform.log("player round " + n.toString + " " + what)

    def round(n: Int): Unit = {
      val screen = new Container
      screen.size.width := ui.size.width()
      screen.size.height := ui.size.height()
      val video = new VideoView
      video.size.width := ui.size.width()
      video.size.height := ui.size.height()
      video.muted @= true
      screen.children += video
      ui.children += screen
      var ready = false
      var left = false
      var mini: Option[Container] = None
      def later(steps: Double, f: () => Unit): Unit = Platform.after(step * steps, () => if (!left) f())
      def leave(): Unit = if (!left) {
        left = true
        ui.children -= screen
        screen.dispose()
        mini.foreach { box =>
          ui.children -= box
          box.dispose()
        }
        say(n, "left held=" + MediaSupport.held.toString)
        if (n >= rounds) Platform.log("player rounds done held=" + MediaSupport.held.toString)
        else Platform.after(0.5, () => round(n + 1))
      }
      // the picture moved into a small box in the corner, the box made off the page and then put on it
      def move(): Unit = {
        val box = new Container
        box.size.width @= 480.0
        box.size.height @= 270.0
        box.position.left := ui.size.width() - 500.0
        box.position.top @= 20.0
        say(n, "moved at " + tenths(video.playhead()))
        video.size.width @= 480.0
        video.size.height @= 270.0
        box.children += video
        ui.children += box
        mini = Some(box)
      }
      video.events { e =>
        if (e == "error") say(n, "error " + video.diagnostics.failure)
        if (e == "ready" && !ready) {
          ready = true
          say(n, "ready at " + tenths(video.playhead()) + " tracks=" +
            video.tracks.map(t => t.kind + ":" + t.id + ":" + t.language + ":" + t.label).mkString("|"))
          val audio = video.audioTracks
          val texts = video.textTracks
          if (audio.size > 1) {
            video.audioTrack @= Some(audio(1).id)
            later(1.0, () => say(n, "audio " + audio(1).id + " heard " + video.diagnostics.audioTracks))
          }
          texts.zipWithIndex.foreach { case (t, i) =>
            later(2.0 + i, () => {
              video.captions @= TextChoice.TextTrack(t.id)
              say(n, "text " + t.id + " " + t.label)
            })
          }
          later(2.0 + texts.size, () => {
            video.captions @= TextChoice.TextOff
            say(n, "text off")
          })
          later(3.0 + texts.size, () => video.seek(start + 10.0))
          later(4.0 + texts.size, () => say(n, "sought " + tenths(video.playhead())))
          later(5.0 + texts.size, () => move())
          later(7.0 + texts.size, () => say(n, "after move " + tenths(video.playhead()) + " heard " +
            video.diagnostics.audioTracks))
          later(7.5 + texts.size, () => leave())
        }
      }
      video.sidecars @= sides
      video.open(source, start)
      say(n, "opened " + source + " at " + tenths(start))
    }
    round(1)
  }
}
