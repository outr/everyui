package everyui.kit

import everyui.component.*
import everyui.Color
import everyui.component.types.FontWeight
import everyui.easing.Easing
import everyui.event.Key
import everyui.image.Image
import everyui.task.*
import everyui.ui
import reactify.*

import scala.concurrent.duration.*

/** Pictures one at a time over the whole screen: whole, on black, Left and Right to the one before and after, sliding
  * across as a page does in a fifth of a second, and which of how many at the foot. The ones either side are fetched
  * while one is looked at, so the next is there to slide in. `sources` are the pictures' addresses, at the size to show
  * them; `closed` hears it go (Back, Escape, or a click outside the picture). */
class ImageViewer(sources: List[String], start: Int, closed: () => Unit = () => ()) {
  private val index: Var[Int] = Var(start)

  def open(): Unit = {
    val wide = ui.size.width() - 16.0
    val tall = ui.size.height() - 16.0
    val view = new ImageView
    // the picture being left, sliding out as the next slides in
    val leaving = new ImageView
    def fit(image: Image, into: ImageView, offset: Double): Double = {
      val scale = math.min(1.0, math.min(wide / math.max(1.0, image.width), tall / math.max(1.0, image.height)))
      into.image @= image
      into.size.width @= image.width * scale
      into.size.height @= image.height * scale
      into.position.top @= (tall - image.height * scale) / 2.0
      val home = (wide - image.width * scale) / 2.0
      into.position.left @= home + offset
      home
    }
    def slide(c: Component, to: Double): Unit = c.position.left.to(to).in(200.millis).easing(Easing.cubicOut).start()
    // `from` is the side it comes in from: 1 from the right, -1 from the left, 0 where it is simply shown
    val show: (Int, Int) => Unit = (i, from) => Image(sources(i)).map { image =>
      if (index() == i) {
        val home = fit(image, view, from * wide)
        if (from != 0) slide(view, home)
      }
    }.startUnit()
    def near(i: Int): Unit = List(i - 1, i + 1).foreach { n =>
      if (n >= 0 && n < sources.size) Image(sources(n)).startUnit()
    }
    val counter = TextView.line((index() + 1).toString + " / " + sources.size.toString, 13.0, Color.White,
      FontWeight.SemiBold, 0.0, 1.43)
    val pill = new Container {
      size.width := counter.size.width() + 24.0
      size.height := counter.size.height() + 12.0
      radius @= 20.0
      backgroundColor @= Color.Black.withAlpha(0.54)
      children += counter
      visible @= sources.size > 1
    }
    counter.position.left @= 12.0
    counter.position.top @= 6.0
    pill.position.left := (wide - pill.size.width()) / 2.0
    pill.position.top := tall - 12.0 - pill.size.height()
    val panel = new Container {
      size.width @= wide
      size.height @= tall
      backgroundColor @= Color.Black
      focusable @= true
      override def keepsKey(key: Key): Boolean = key == Key.ArrowLeft || key == Key.ArrowRight
      children ++= List(leaving, view, pill)
    }
    panel.event.key.down.attach { k =>
      val step = if (k.key == Key.ArrowRight) 1 else if (k.key == Key.ArrowLeft) -1 else 0
      val next = index() + step
      if (step != 0 && next >= 0 && next < sources.size) {
        // what is showing goes out the other side, and the new one comes in behind it as soon as it is loaded
        leaving.image @= view.image()
        leaving.size.width @= view.size.width()
        leaving.size.height @= view.size.height()
        leaving.position.top @= view.position.top()
        leaving.position.left @= view.position.left()
        slide(leaving, view.position.left() - step * wide)
        view.image @= Image.empty
        index @= next
        show(next, step)
        near(next)
      }
    }
    // a click on the black about the picture closes it, as one outside it does
    panel.event.click.attach { e =>
      val x = e.x - panel.absoluteLeft - view.position.left()
      val y = e.y - panel.absoluteTop - view.position.top()
      if (x < 0.0 || y < 0.0 || x > view.size.width() || y > view.size.height()) Modal.remove(panel)
    }
    show(start, 0)
    near(start)
    Modal.show(panel, panel, closed)
  }

  /** Which picture is showing. */
  def showing: Val[Int] = index
}
