package everyui.app

import everyui.{Color, Gradient}
import everyui.component.*
import everyui.component.types.FontWeight
import everyui.render.Platform
import everyui.ui

/** A TV player's overlay over a playing picture, nested as NaboTV's is: a screen's box under the app's root, black and
  * holding the video (`src=`, muted; by default `media/bars.webm`, colour bars, which OldChromeCheck gives it from
  * assets/media), then the artwork, a shade and a dimmer over it, then the controls' layer (the title's words at the
  * top left, a clock at the top right, and at the bottom a rounded bar cut to its box: a gradient seen through, an
  * edge, a scrubber with its time, and the transport, a box holding a row of round buttons), then the layers a player
  * keeps over those (its pills, its flashes, the keys' layer). The bar is labelled "player bar" for a check to find.
  * Logs `playerbar ready` once the video plays. */
object PlayerBarDemo {
  def run(): Unit = {
    val (width, height) = (ui.size.width(), ui.size.height())
    def layer(): Container = new Container {
      size.width @= width
      size.height @= height
    }
    val host = layer()
    val screen = new Container {
      size.width @= width
      size.height @= height
      backgroundColor @= Color.Black
      clipsToBox @= Some(true)
    }
    val video = new VideoView
    video.size.width @= width
    video.size.height @= height
    video.cover @= false
    video.volume @= 0.0
    video.events(e => if (e.toString.toLowerCase.contains("ready")) Platform.log("playerbar ready"))
    video.source @= (Platform.launchArg("src") match {
      case "" => "media/bars.webm"
      case s => s
    })
    // the artwork before the first frame, a shade over it and Sleeping mode's dimmer, all at nothing while it plays
    val art = new ImageView
    art.size.width @= width
    art.size.height @= height
    art.opacity @= 0.0
    val shade = layer()
    shade.backgroundColor @= Color.Black.withAlpha(0.62)
    shade.opacity @= 0.0
    val dim = layer()
    dim.backgroundColor @= Color.Black
    dim.opacity @= 0.0
    val meta = Column(8.0)(
      TextView.line("A Title of the Film", 28.0, Color.White, FontWeight.SemiBold),
      TextView.line("2024  ·  PG-13  ·  1 h 52 m", 14.0, Color.White.withAlpha(0.85)),
      TextView.line("What it is about, a line of the description over the picture.", 14.0, Color.White.withAlpha(0.88)))
    meta.position.left @= 56.0
    meta.position.top @= 48.0
    val clock = TextView.line("9:41 PM", 22.0, Color.White, FontWeight.SemiBold)
    clock.position.left := width - clock.size.width() - 44.0
    clock.position.top @= 30.0
    val pane = new Container {
      size.width @= width - 96.0
      size.height @= 114.0
      position.left @= 48.0
      position.top @= height - 114.0 - 32.0
      radius @= 14.0
      gradient @= Some(Gradient(180.0, Color.fromRGB(0.1, 0.2, 0.45).withAlpha(0.42), Color.fromRGB(0.04, 0.05, 0.09).withAlpha(0.95)))
      borderWidth @= 1.0
      borderColor @= Color.fromRGB(0.3, 0.55, 1.0).withAlpha(0.22)
      semantics @= types.Semantics(label = "player bar")
    }
    val track = new Container {
      size.width @= pane.size.width() - 160.0
      size.height @= 6.0
      position.left @= 24.0
      position.top @= 25.0
      radius @= 4.0
      backgroundColor @= Color.White.withAlpha(0.33)
    }
    track.children += new Container {
      size.width @= track.size.width() * 0.4
      size.height @= 6.0
      radius @= 4.0
      backgroundColor @= Color.fromRGB(0.3, 0.6, 1.0)
    }
    val time = TextView.line("0:12 / 1:30:00", 14.0, Color.White, FontWeight.SemiBold)
    time.position.left @= 24.0 + track.size.width() + 16.0
    time.position.top @= 18.0
    val row = Row(14.0, middle = true)((1 to 9).map { i =>
      new Container {
        size.width @= 44.0
        size.height @= 44.0
        radius @= 22.0
        backgroundColor @= Color.Black.withAlpha(0.6)
        borderWidth @= 1.5
        borderColor @= Color.White.withAlpha(0.1)
        children += TextView(i.toString, 16.0, Color.White)
      }
    }*)
    val transport = new Container {
      size.width := row.size.width()
      size.height := row.size.height()
      children += row
    }
    transport.position.left := (pane.size.width() - transport.size.width()) / 2.0
    transport.position.top @= 54.0
    val grip = new Container {
      size.width @= track.size.width() + 24.0
      size.height @= 44.0
      position.left @= 12.0
      position.top @= 6.0
    }
    pane.children ++= List(track, time, transport, grip)
    val controls = layer()
    controls.children ++= List(meta, clock, pane)
    screen.children ++= List(video, art, shade, dim, controls, layer(), layer(), layer())
    host.children += screen
    ui.children += host
  }
}
