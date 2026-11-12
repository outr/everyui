package everyui.app

import everyui.component.{Button, ProgressBar, TextView, ThumbFrame, ThumbnailSource, TrickPlayThumb, VideoView}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** Trick-play thumbnails for a scrubber of the app's own (`TrickPlayThumb`), from a BIF and from tile sheets side by
  * side, following one place: `bif=<url>` and `tiles=<template>` (with `{index}`), else `origin=`
  * (http://127.0.0.1:8098) with /trick/film.bif and /trick/tiles/{index}.jpg, a film of 20 minutes with a thumbnail
  * every 10 seconds, the tiles 320 by 180, 5 across and 4 down, 120 in all. `-10 s` and `+10 s` move the place;
  * `at=<seconds>` starts there, and `auto=1` walks it by itself (the start, the middle, past the end, back before the
  * start, then a quick run as a scrubber held down makes), logging each thumbnail as it is shown and the one it should
  * be:
  *
  *   [ui] trickplay bif at 125s: frame 12 (wanted 12) tmp:/everyui-range-4.jpg
  *   [ui] trickplay tiles at 125s: frame 12 (wanted 12) http://…/tiles/0.jpg cell 2,2 */
object TrickPlayDemo {
  private val length: Double = 1200.0
  private val intervalMs: Double = 10000.0

  def run(): Unit = {
    def arg(name: String, otherwise: String): String =
      if (Platform.launchArg(name).nonEmpty) Platform.launchArg(name) else otherwise
    // in a bundled face, measured as a television app's words are (the device's own measures nothing in brs-engine)
    Theme.family @= "Open Sans"
    val origin = arg("origin", "http://127.0.0.1:8098")
    val place: Var[Double] = Var(arg("at", "0").toDoubleOption.getOrElse(0.0))

    val bif = new VideoView
    bif.thumbnails @= ThumbnailSource.BifFile(arg("bif", origin + "/trick/film.bif"))
    val tiles = new VideoView
    tiles.thumbnails @= ThumbnailSource.TileSheets(arg("tiles", origin + "/trick/tiles/{index}.jpg"), intervalMs, 320.0,
      180.0, 5, 4, 120)

    val bifThumb = new TrickPlayThumb(bif, place, 320.0, 180.0)
    val tileThumb = new TrickPlayThumb(tiles, place, 320.0, 180.0)
    bifThumb.backgroundColor @= Theme.controlLit()
    tileThumb.backgroundColor @= Theme.controlLit()
    bifThumb.frame.attach(f => f.foreach(shown => said("bif", place(), shown)))
    tileThumb.frame.attach(f => f.foreach(shown => said("tiles", place(), shown)))

    val title = TextView("Trick-play thumbnails: BIF (left), tile sheets (right)", 22.0, Theme.text())
    val where = TextView("", 20.0, Theme.text())
    where.content := "at " + clock(place()) + " of " + clock(length)
    val bar = ProgressBar(0.0, 680.0, 8.0)
    bar.fraction := math.max(0.0, math.min(1.0, place() / length))
    val back = new Button("-10 s", () => place @= math.max(0.0, place() - 10.0))
    val on = new Button("+10 s", () => place @= math.min(length, place() + 10.0))

    title.position.left @= 40.0
    title.position.top @= 30.0
    bifThumb.position.left @= 40.0
    bifThumb.position.top @= 90.0
    tileThumb.position.left @= 400.0
    tileThumb.position.top @= 90.0
    bar.position.left @= 40.0
    bar.position.top @= 300.0
    where.position.left @= 40.0
    where.position.top @= 320.0
    back.position.left @= 40.0
    back.position.top @= 370.0
    on.position.left @= 180.0
    on.position.top @= 370.0
    ui.backgroundColor := Theme.background()
    ui.children ++= List(title, bifThumb, tileThumb, bar, where, back, on)

    if (Platform.launchArg("auto") == "1") walk(place, List(125.0, 600.0, 605.0, 1195.0, 99999.0, -20.0, 300.0), 0.0)
    // a frozen frame (the desktop's --freeze and --screenshot) once the thumbnails have had time to arrive
    Platform.after(4.0, () => if (Platform.freezeSeconds() >= 0.0) Platform.frozen())
  }

  // the places in turn, three seconds apart, then ten steps of 10 seconds a tenth of a second apart, as a scrubber held
  // down makes them
  private def walk(place: Var[Double], places: List[Double], quick: Double): Unit =
    Platform.after(if (places.nonEmpty || quick == 0.0) 3.0 else 0.1, () => {
      if (places.nonEmpty) {
        place @= places.head
        walk(place, places.tail, 0.0)
      } else if (quick < 10.0) {
        place @= 400.0 + quick * 10.0
        walk(place, Nil, quick + 1.0)
      } else Platform.log("trickplay walked")
    })

  private def said(kind: String, at: Double, shown: ThumbFrame): Unit = {
    val wanted = math.max(0, math.min(119, math.floor(math.max(0.0, at) * 1000.0 / intervalMs).toInt))
    val cell = if (shown.across > 1) " cell " + shown.column.toString + "," + shown.row.toString else ""
    Platform.log("trickplay " + kind + " at " + math.round(at).toString + "s: frame " + shown.frame.toString +
      " (wanted " + wanted.toString + ") " + shown.address.take(60) + cell)
  }

  private def clock(seconds: Double): String = {
    val s = math.max(0, math.round(seconds).toInt)
    val m = s / 60
    val r = s % 60
    (if (m < 10) "0" else "") + m.toString + ":" + (if (r < 10) "0" else "") + r.toString
  }
}
