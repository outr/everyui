package everyui.app

import everyui.component.{Button, ButtonStyle, TextView, VideoView}
import everyui.render.Platform
import everyui.text.{CaptionMode, CaptionPreference, Captions}
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** The device's caption setting (`Captions.device`), as it is and as it changes, over a video that follows it until a
  * subtitle track is chosen here: with the device's captions on, the track in its caption language, else the first;
  * off, none. The button steps from the device's choice to none and through the tracks; none turns this video's off
  * and leaves the device's setting as it was. `src=` plays another stream. Logs `captions device <mode> <language>` at
  * the start and at each change, and `captions ready <n> text tracks, the device's choice <id>` once the video is. */
object CaptionsDemo {
  private def said(p: Option[CaptionPreference]): String = p match {
    case Some(pref) =>
      val mode = pref.mode match {
        case CaptionMode.CaptionsOn => "on"
        case CaptionMode.CaptionsOff => "off"
        case CaptionMode.CaptionsOnReplay => "on replay"
        case CaptionMode.CaptionsWhenMuted => "when muted"
        case CaptionMode.CaptionsAutomatic => "automatic"
      }
      mode + " " + pref.language.getOrElse("-")
    case None => "none on this platform"
  }

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val device = Var(said(Captions.device))
    Platform.log("captions device " + device())
    Captions.listen(p => {
      device @= said(p)
      Platform.log("captions device " + device())
    })
    val video = new VideoView
    video.cover @= false
    video.events(e => if (e == "ready") Platform.log("captions ready " + video.textTracks.size.toString +
      " text tracks, the device's choice " + Captions.choose(false, None, Captions.device, video.tracks).getOrElse("none")))
    // -1 the device's choice, 0 none, n the nth text track
    val at = Var(-1)
    val reading: () => String = () => {
      val tracks = video.textTracks
      if (at() < 0) "the device's" else if (at() == 0 || at() > tracks.size) "off" else tracks(at() - 1).name
    }
    val step = Button.bound("Subtitles: " + reading(), () => {
      val tracks = video.textTracks
      at @= (if (at() + 1 > tracks.size) 0 else at() + 1)
      // Some("") rather than None: None is what it starts as, so setting it would be no change, and no choice
      video.textTrack @= (if (at() == 0) Some("") else Some(tracks(at() - 1).id))
    }, style = ButtonStyle.Filled)
    val setting = TextView.bound("Device captions: " + device(), 16.0, Theme.text())

    setting.position.left @= 16.0
    setting.position.top @= 16.0
    video.position.left @= 16.0
    video.position.top @= 48.0
    video.size.width := ui.size.width() - 32.0
    video.size.height := math.max(120.0, math.min((ui.size.width() - 32.0) * 9.0 / 16.0, ui.size.height() - 130.0))
    step.position.left @= 16.0
    step.position.top := video.position.top() + video.size.height() + 12.0
    ui.children ++= List(setting, video, step)
    video.source @= (Platform.launchArg("src") match {
      case "" => "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
      case url => url
    })
    Platform.after(1.0, () => if (Platform.freezeSeconds() >= 0.0) Platform.frozen())
  }
}
