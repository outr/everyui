package everyui.app

import everyui.component.{Backdrop, ImageView}
import everyui.easing.Easing
import everyui.image.Image
import everyui.render.Platform
import everyui.task.*
import everyui.ui
import reactify.*

import scala.concurrent.duration.*

/** Blur, as the screensaver uses it, for checking a backend draws it: on the left a photo whole over a blurred, dimmed
  * copy of itself (spread 40, the Flutter photo scene's); at the top right a picture blurred on its own (12); below it
  * a picture that starts blurred (20) and comes clear over 1.2 seconds, again every 4, as a quote's does. `src=<url>`
  * picks the picture. */
object BlurDemo {
  def run(): Unit = {
    val named = Platform.launchArg("src")
    val src = if (named.nonEmpty) named else "https://picsum.photos/id/1015/1600/1000"
    val w = ui.size.width()
    val h = ui.size.height()
    val half = w / 2.0

    val behind = new Backdrop
    behind.size.width @= half
    behind.size.height @= h
    behind.opacity @= 0.6
    behind.blur @= 40.0
    behind.source @= src
    val photo = new ImageView
    Image(src).map { image =>
      val room = math.min((half - 48.0) / math.max(1.0, image.width), (h - 48.0) / math.max(1.0, image.height))
      photo.image @= image
      photo.size.width @= image.width * room
      photo.size.height @= image.height * room
      photo.position.left @= (half - image.width * room) / 2.0
      photo.position.top @= (h - image.height * room) / 2.0
    }.startUnit()

    val alone = new ImageView
    Image(src).map { image => alone.image @= image }.startUnit()
    alone.position.left @= half
    alone.size.width @= half
    alone.size.height @= h / 2.0
    alone.blur @= 12.0

    val reveal = new Backdrop
    reveal.position.left @= half
    reveal.position.top @= h / 2.0
    reveal.size.width @= half
    reveal.size.height @= h / 2.0
    reveal.source @= src
    def again(): Unit = {
      reveal.blur @= 20.0
      Platform.after(1.5, () => reveal.blur.to(0.0).in(1200.millis).easing(Easing.cubicInOut).start())
      Platform.after(4.0, () => again())
    }
    again()

    ui.children ++= List(behind, photo, alone, reveal)
  }
}
