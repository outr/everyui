package everyui.app

import everyui.component.{Button, ButtonStyle, TextChoice, TextView, VideoView}
import everyui.render.Platform
import everyui.text.{CaptionMode, CaptionPreference, Captions}
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** The device's caption setting (`Captions.device`), as it is and as it changes, over a video that follows it until a
  * subtitle track is chosen here: with the device's captions on, the track in its caption language, else the first;
  * off, none. The first button steps `captions` from the device's choice to none, through the tracks and back; the
  * second writes the device's mode where the platform lets it (On, Off, On replay, When mute on a Roku). `src=` plays
  * another stream. Logs `captions device <mode> <language>` at the start and at each change, `captions ready <n> text
  * tracks, the device's choice <id>` once the video is, and `captions setDevice <mode> <written>` for each write. */
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
      " text tracks, the device's choice " +
      Captions.choose(TextChoice.TextFromDevice, Captions.device, video.tracks).getOrElse("none")))
    // the device's, none, then each text track, and round again
    val step = Button.bound("Subtitles: " + (video.captions() match {
      case TextChoice.TextFromDevice => "the device's"
      case TextChoice.TextOff => "off"
      case TextChoice.TextTrack(id) => video.textTracks.find(_.id == id).map(_.name).getOrElse(id)
    }), () => {
      val tracks = video.textTracks
      val next = video.captions() match {
        case TextChoice.TextFromDevice => TextChoice.TextOff
        case TextChoice.TextOff =>
          if (tracks.isEmpty) TextChoice.TextFromDevice else TextChoice.TextTrack(tracks.head.id)
        case TextChoice.TextTrack(id) =>
          val i = tracks.indexWhere(_.id == id) + 1
          if (i > 0 && i < tracks.size) TextChoice.TextTrack(tracks(i).id) else TextChoice.TextFromDevice
      }
      video.captions @= next
    }, style = ButtonStyle.Filled)
    // the device's own mode, where the app may write it (a Roku's caption menu: On, Off, On replay, When mute)
    val modes = List(CaptionMode.CaptionsOn, CaptionMode.CaptionsOff, CaptionMode.CaptionsOnReplay,
      CaptionMode.CaptionsWhenMuted)
    val at = Var(0)
    val write = Button.bound(if (Captions.writable) "Set the device's mode" else "The device's mode is its own", () => {
      val mode = modes(at())
      at @= (at() + 1) % modes.size
      val written = Captions.setDevice(mode)
      Platform.log("captions setDevice " + said(Some(CaptionPreference(mode))) + " " + written.toString)
    })
    val setting = TextView.bound("Device captions: " + device(), 16.0, Theme.text())

    setting.position.left @= 16.0
    setting.position.top @= 16.0
    video.position.left @= 16.0
    video.position.top @= 48.0
    video.size.width := ui.size.width() - 32.0
    video.size.height := math.max(120.0, math.min((ui.size.width() - 32.0) * 9.0 / 16.0, ui.size.height() - 130.0))
    step.position.left @= 16.0
    step.position.top := video.position.top() + video.size.height() + 12.0
    write.position.left := step.position.left() + step.size.width() + 8.0
    write.position.top := step.position.top()
    ui.children ++= List(setting, video, step, write)
    video.source @= (Platform.launchArg("src") match {
      case "" => "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
      case url => url
    })
    Platform.after(1.0, () => if (Platform.freezeSeconds() >= 0.0) Platform.frozen())
  }
}
