package everyui.app

import everyui.component.{Button, ButtonStyle, Component, MediaTrack, NowPlaying, TextView, VideoView}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A VideoView over local test media, on every platform: by default `media/test.mkv` (H.264, two sound tracks and
  * two subtitles, SRT and SubStation), `media/test.mp4` and `media/test.m4a` (a sound alone) in the app's bundle, or
  * the addresses in `src=` (comma separated). The bar plays and pauses, seeks back 10 s and on 10 s, steps through the
  * sound and subtitle tracks, the rate (1, 1.5, 2, 0.5) and the volume (1, 0.5, 0.25, 0), and opens the next source,
  * which follows by itself at each end. `script=1` drives it all itself, for a device with nothing to press on: pause
  * and play, a seek, the second sound track, each subtitle, 1.5 times, half volume, and a seek to near the end, for
  * each source in turn. `now=1` tells the system what plays (`nowPlaying`). Logs `player <event>` as each is heard, a
  * `player step <what>` for each scripted step, and each second `player probe src=<index> t=<playhead>
  * d=<duration> playing=<on> waiting=<on> rate=<r> volume=<v> audio=<n>/<chosen> text=<n>/<chosen> size=<w>x<h>
  * tracks=<kind:language:label:format|...>`, then `player diagnostics <audio> | <video> | <audio tracks>` once each
  * source is ready. */
object PlayerDemo {
  private val defaults = List("media/test.mkv", "media/test.mp4", "media/test.m4a")
  private val rates = List(1.0, 1.5, 2.0, 0.5)
  private val volumes = List(1.0, 0.5, 0.25, 0.0)
  // seconds to a tenth, as the Roku writes no f"" interpolation
  private def tenths(seconds: Double): String = (math.round(seconds * 10.0) / 10.0).toString

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val sources = Platform.launchArg("src") match {
      case "" => defaults
      case list => list.split(',').toList.map(_.trim).filter(_.nonEmpty)
    }
    val scripted = Platform.launchArg("script") == "1"
    val at = Var(0)
    val heard = Var(List.empty[String])
    val video = new VideoView
    video.cover @= false

    def open(i: Int): Unit = {
      at @= i
      video.audioTrack @= None
      video.textTrack @= None
      video.rate @= 1.0
      video.volume @= 1.0
      val name = sources(i).split('/').lastOption.getOrElse(sources(i))
      if (Platform.launchArg("now") == "1") video.nowPlaying @= NowPlaying(name, "EveryUI", "Test media")
      video.source @= sources(i)
      Platform.log("player open " + i.toString + " " + sources(i))
    }
    def trackName(tracks: List[MediaTrack], id: Option[String]): String =
      id.flatMap(i => tracks.find(_.id == i)).map(_.name).getOrElse(if (id.contains("")) "off" else "default")
    // the track after the chosen one, and past the last text track none
    def next(tracks: List[MediaTrack], chosen: Option[String], none: Boolean): Option[String] = {
      val i = chosen.map(id => tracks.indexWhere(_.id == id)).getOrElse(-1) + 1
      if (i < tracks.size) Some(tracks(i).id) else if (none) Some("") else tracks.headOption.map(_.id)
    }
    def step[T](list: List[T], now: T): T = list((list.indexOf(now) + 1) % list.size)

    val play = Button.bound(if (video.playing()) "Pause" else "Play", () => video.playing @= !video.playing(),
      style = ButtonStyle.Filled)
    val back = new Button("-10 s", () => video.seek(math.max(0.0, video.playhead() - 10.0)))
    val on = new Button("+10 s", () => video.seek(video.playhead() + 10.0))
    val hear = Button.bound("Sound: " + trackName(video.audioTracks, video.audioTrack()), () =>
      video.audioTrack @= next(video.audioTracks, video.audioTrack(), none = false))
    val read = Button.bound("Subtitles: " + trackName(video.textTracks, video.textTrack()), () =>
      video.textTrack @= next(video.textTracks, video.textTrack(), none = true))
    val fast = Button.bound("Rate " + video.rate().toString, () => video.rate @= step(rates, video.rate()))
    val loud = Button.bound("Volume " + video.volume().toString, () => video.volume @= step(volumes, video.volume()))
    val source = Button.bound("Source " + (at() + 1).toString + " of " + sources.size.toString,
      () => open((at() + 1) % sources.size))
    val status = Var("")
    val said = TextView.bound(status(), 18.0, Theme.text())
    val events = TextView.bound("Heard: " + heard().mkString(" "), 14.0, Theme.muted())

    video.position.left @= 24.0
    video.position.top @= 24.0
    video.size.width := ui.size.width() - 48.0
    video.size.height := math.max(120.0, math.min((ui.size.width() - 48.0) * 9.0 / 16.0, ui.size.height() - 220.0))
    // the bar under it, wrapping at the edge
    val bar = List(play, back, on, hear, read, fast, loud, source)
    bar.zipWithIndex.foreach { case (b, i) =>
      if (i == 0) {
        b.position.left @= 24.0
        b.position.top := video.position.top() + video.size.height() + 16.0
      }
      else {
        val before = bar(i - 1)
        def wraps: Boolean =
          before.position.left() + before.size.width() + 10.0 + b.size.width() > ui.size.width() - 24.0
        b.position.left := (if (wraps) 24.0 else before.position.left() + before.size.width() + 10.0)
        b.position.top := (if (wraps) before.position.top() + before.size.height() + 10.0 else before.position.top())
      }
    }
    said.position.left @= 24.0
    said.maxWidth := ui.size.width() - 48.0
    said.position.top := bar.last.position.top() + bar.last.size.height() + 12.0
    events.position.left @= 24.0
    events.maxWidth := ui.size.width() - 48.0
    events.position.top := said.position.top() + said.size.height() + 6.0
    ui.children ++= (video :: bar) ++ List[Component](said, events)

    // the script's steps, each a second or two after the one before, and the next source at each end
    val steps: List[(Double, String, () => Unit)] = List(
      (2.0, "pause", () => video.playing @= false),
      (1.5, "play", () => video.playing @= true),
      (2.0, "seek 8", () => video.seek(8.0)),
      (2.0, "second sound track", () => video.audioTrack @= video.audioTracks.drop(1).headOption.map(_.id)),
      (2.0, "first subtitle", () => video.textTrack @= video.textTracks.headOption.map(_.id)),
      (3.0, "second subtitle", () => video.textTrack @= video.textTracks.drop(1).headOption.map(_.id)),
      (3.0, "rate 1.5", () => video.rate @= 1.5),
      (2.0, "volume 0.5", () => video.volume @= 0.5),
      (2.0, "seek to the end", () => video.seek(math.max(0.0, video.duration() - 3.0))))
    def walk(left: List[(Double, String, () => Unit)], source: Int): Unit = left match {
      case (wait, what, act) :: rest => Platform.after(wait, () => if (at() == source) {
        Platform.log("player step " + what)
        act()
        walk(rest, source)
      })
      case Nil =>
    }
    video.events { e =>
      heard @= (heard() :+ e).drop(math.max(0, heard().size - 7))
      Platform.log("player " + e + (if (e == "error") " " + video.diagnostics.failure else ""))
      if (e == "ready") {
        val d = video.diagnostics
        Platform.log("player diagnostics " + d.audio + " | " + d.video + " | " + d.audioTracks)
        if (scripted) walk(steps, at())
      }
      if (e == "ended") {
        if (at() + 1 < sources.size) Platform.after(1.0, () => open(at() + 1))
        else Platform.log("player script done")
      }
    }

    def probe(): Unit = Platform.after(1.0, () => {
      val size = video.videoSize.map(s => s._1.toInt.toString + "x" + s._2.toInt.toString).getOrElse("-")
      status @= tenths(video.playhead()) + " of " + tenths(video.duration()) + " s" +
        (if (video.waiting) ", waiting" else "") + ", " + trackName(video.audioTracks, video.audioTrack()) + ", subtitles " +
        trackName(video.textTracks, video.textTrack()) + ", rate " + video.rate().toString + ", volume " +
        video.volume().toString
      Platform.log(s"player probe src=${at()} t=${tenths(video.playhead())} d=${tenths(video.duration())} " +
        s"playing=${video.playing()} waiting=${video.waiting} rate=${video.rate()} volume=${video.volume()} " +
        s"audio=${video.audioTracks.size}/${video.audioTrack().getOrElse("-")} " +
        s"text=${video.textTracks.size}/${video.textTrack().getOrElse("-")} size=$size " +
        s"tracks=${video.tracks.map(t => t.kind + ":" + t.language + ":" + t.label + ":" + t.format).mkString("|")}")
      probe()
    })
    probe()
    open(0)
  }
}
