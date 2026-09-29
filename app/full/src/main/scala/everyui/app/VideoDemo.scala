package everyui.app

import everyui.component.{Button, ButtonStyle, Component, MediaTrack, TextView, VideoView}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A VideoView over streams and a file: an HLS stream with one audio track, one with alternate audio and subtitle
  * renditions, and an `.mp4`, played through hls.js where the browser has no HLS of its own. The bar pauses and
  * resumes, seeks back 10 s and on 30 s, steps through the audio and subtitle tracks, and opens the next source.
  * `src=` gives the sources instead (comma separated), `header=Name:value` sends a header with every request an HLS
  * stream makes (`headers`), `lang=<audio>,<text>` chooses its tracks by language as it opens (`languages`), and
  * `muted=1` starts it silent. Logs `video <event>` as each is heard, and twice a second `video probe t=<playhead>
  * d=<duration> playing=<on> waiting=<on> bw=<bits> audio=<n>/<chosen> text=<n>/<chosen> src=<index> tracks=...`. */
object VideoDemo {
  private val defaults = List(
    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
    "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val sources = Platform.launchArg("src") match {
      case "" => defaults
      case list => list.split(',').toList.map(_.trim).filter(_.nonEmpty)
    }
    val at = Var(0)
    val video = new VideoView
    Platform.launchArg("header").split(":", 2) match {
      case Array(name, value) if name.nonEmpty => video.headers @= Map(name.trim -> value.trim)
      case _ =>
    }
    Platform.launchArg("lang").split(",", 2) match {
      case Array(audio, text) => video.languages(audio.trim, text.trim)
      case Array(audio) if audio.nonEmpty => video.languages(audio.trim, "")
      case _ =>
    }
    video.muted @= Platform.launchArg("muted") == "1"
    video.cover @= false
    video.events(e => Platform.log("video " + e + (if (e == "error") " " + video.diagnostics.failure else "")))

    val audio = Var("")
    // the stream's own choice until one is made here
    val text = Var("default")
    val status = Var("")
    def open(i: Int): Unit = {
      at @= i
      audio @= ""
      text @= "default"
      video.audioTrack @= None
      video.textTrack @= None
      video.source @= sources(i)
    }
    // the track after the chosen one, and past the last text track none
    def next(tracks: List[MediaTrack], chosen: Option[String], none: Boolean): Option[MediaTrack] = {
      val i = chosen.map(id => tracks.indexWhere(_.id == id)).getOrElse(-1) + 1
      if (i < tracks.size) Some(tracks(i)) else if (none) None else tracks.headOption
    }

    val play = Button.bound(if (video.playing()) "Pause" else "Play", () => video.playing @= !video.playing(),
      style = ButtonStyle.Filled)
    val back = new Button("-10 s", () => video.seek(math.max(0.0, video.playhead() - 10.0)))
    val on = new Button("+30 s", () => video.seek(video.playhead() + 30.0))
    val hear = Button.bound("Audio: " + (if (audio().isEmpty) "default" else audio()), () => {
      val t = next(video.audioTracks, video.audioTrack(), none = false)
      video.audioTrack @= t.map(_.id)
      audio @= t.map(_.name).getOrElse("")
    })
    val read = Button.bound("Subtitles: " + text(), () => {
      val t = next(video.textTracks, video.textTrack(), none = true)
      video.textTrack @= t.map(_.id)
      text @= t.map(_.name).getOrElse("off")
    })
    val source = Button.bound("Source " + (at() + 1) + " of " + sources.size, () => open((at() + 1) % sources.size))
    val said = TextView.bound(status(), 13.0, Theme.muted())

    video.position.left @= 16.0
    video.position.top @= 16.0
    video.size.width := ui.size.width() - 32.0
    video.size.height := math.max(120.0, math.min((ui.size.width() - 32.0) * 9.0 / 16.0, ui.size.height() - 150.0))
    // the bar under it, wrapping at the edge
    val bar = List(play, back, on, hear, read, source)
    bar.zipWithIndex.foreach { case (b, i) =>
      if (i == 0) {
        b.position.left @= 16.0
        b.position.top := video.position.top() + video.size.height() + 12.0
      }
      else {
        val before = bar(i - 1)
        def wraps: Boolean =
          before.position.left() + before.size.width() + 8.0 + b.size.width() > ui.size.width() - 16.0
        b.position.left := (if (wraps) 16.0 else before.position.left() + before.size.width() + 8.0)
        b.position.top := (if (wraps) before.position.top() + before.size.height() + 8.0 else before.position.top())
      }
    }
    said.position.left @= 16.0
    said.position.top := bar.last.position.top() + bar.last.size.height() + 10.0
    ui.children ++= (video :: bar) :+ (said: Component)

    def probe(): Unit = Platform.after(0.5, () => {
      val chosenAudio = video.audioTrack().getOrElse("-")
      val chosenText = video.textTrack().getOrElse("-")
      status @= f"${video.playhead()}%.1f of ${video.duration()}%.1f s" +
        (if (video.waiting) ", waiting" else "") +
        (if (video.bandwidth > 0) f", ${video.bandwidth / 1e6}%.1f Mb/s" else "") +
        s", ${video.audioTracks.size} audio, ${video.textTracks.size} subtitle tracks"
      Platform.log(f"video probe t=${video.playhead()}%.2f d=${video.duration()}%.2f playing=${video.playing()} " +
        s"waiting=${video.waiting} bw=${video.bandwidth} audio=${video.audioTracks.size}/$chosenAudio " +
        s"text=${video.textTracks.size}/$chosenText src=${at()} " +
        s"tracks=${video.tracks.map(t => t.kind + ":" + t.language + ":" + t.label).mkString("|")}")
      probe()
    })
    probe()
    open(0)
  }
}
