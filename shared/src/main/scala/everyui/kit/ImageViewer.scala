package everyui.kit

import everyui.component.*
import everyui.Color
import everyui.component.types.FontWeight
import everyui.easing.Easing
import everyui.event.{Key, PointerEvent}
import everyui.image.Image
import everyui.list.Pointers
import everyui.render.{Gestures, Platform}
import everyui.task.*
import everyui.ui
import reactify.*

import scala.concurrent.duration.*

/** Pictures one at a time over the whole screen: whole, on black, Left and Right to the one before and after, sliding
  * across as a page does in a fifth of a second, and which of how many at the foot. The ones either side are fetched
  * while one is looked at, so the next is there to slide in. `sources` are the pictures' addresses, at the size to show
  * them; `closed` hears it go (Back, Escape, or a click outside the picture).
  *
  * A picture zooms (two fingers pinching, a trackpad's pinch, the wheel with Ctrl or Command, a double click or tap, +
  * and -) up to `ImageViewer.deepest` times its fitted size, about where it is zoomed; zoomed, a drag (a finger, or the
  * primary button) and the wheel move it about, never further than its edges. Another picture comes in whole. `saves`
  * puts a button up to download the one showing (`download`, else saved by its address). */
class ImageViewer(sources: List[String], start: Int, closed: () => Unit = () => (),
                  download: Option[Int => Unit] = None, saves: Boolean = true) {
  private val index: Var[Int] = Var(start)
  private val zoomed: Var[Double] = Var(1.0)
  // the picture's size fitted to the screen, and how far it is moved from the middle, zoomed
  private var fitW = 0.0
  private var fitH = 0.0
  private var panX = 0.0
  private var panY = 0.0
  // a press: where it went down, the pointer last, whether it moved too far to be a click, and a pinch under way
  private var pressX = 0.0
  private var pressY = 0.0
  private var last: Option[(Double, Double)] = None
  private var moved = false
  private var pinching = false
  private var pointerAt: Option[(Double, Double)] = None
  private var clickedAt = -1.0e9

  def open(): Unit = {
    // the whole of the screen, to its edges: the black is the viewer's, and nothing of the app shows beside it
    def wide: Double = ui.size.width()
    def tall: Double = ui.size.height()
    val view = new ImageView
    // the picture being left, sliding out as the next slides in
    val leaving = new ImageView
    view.sizedByPicture @= false
    leaving.sizedByPicture @= false
    def place(): Unit = {
      val w = fitW * zoomed()
      val h = fitH * zoomed()
      val reachX = math.max(0.0, (w - wide) / 2.0)
      val reachY = math.max(0.0, (h - tall) / 2.0)
      panX = math.max(-reachX, math.min(reachX, panX))
      panY = math.max(-reachY, math.min(reachY, panY))
      view.size.width @= w
      view.size.height @= h
      view.position.left @= (wide - w) / 2.0 + panX
      view.position.top @= (tall - h) / 2.0 + panY
    }
    def fit(image: Image, offset: Double): Double = {
      val scale = math.min(1.0, math.min(wide / math.max(1.0, image.width), tall / math.max(1.0, image.height)))
      fitW = image.width * scale
      fitH = image.height * scale
      zoomed @= 1.0
      panX = 0.0
      panY = 0.0
      view.image @= image
      place()
      val home = view.position.left()
      view.position.left @= home + offset
      home
    }
    // zoomed to `to` times its fitted size, the point at `x`, `y` (the panel's) staying where it is
    def zoomAt(to: Double, x: Double, y: Double): Unit = if (fitW > 0.0) {
      val z = math.max(1.0, math.min(ImageViewer.deepest, to))
      val r = z / zoomed()
      panX = x - (x - (wide / 2.0 + panX)) * r - wide / 2.0
      panY = y - (y - (tall / 2.0 + panY)) * r - tall / 2.0
      zoomed @= z
      place()
    }
    def slide(c: Component, to: Double): Unit = c.position.left.to(to).in(200.millis).easing(Easing.cubicOut).start()
    // `from` is the side it comes in from: 1 from the right, -1 from the left, 0 where it is simply shown
    val show: (Int, Int) => Unit = (i, from) => Image(sources(i)).map { image =>
      if (index() == i) {
        val home = fit(image, from * wide)
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
    pill.position.left := (ui.size.width() - pill.size.width()) / 2.0
    pill.position.top := ui.size.height() - 12.0 - pill.size.height()
    val downloading = Var(false)
    val save = new IconButton(everyui.font.MaterialIcon.Download, "Download image", () => {
      download match {
        case Some(action) => action(index())
        case None if !downloading() =>
          downloading @= true
          everyui.file.Files.saveUrl(sources(index()), "image.png").map { result =>
            downloading @= false
            result match {
              case everyui.file.SaveResult.SaveFailed(why) => Toast.show(why)
              case _ => ()
            }
          }.startUnit()
        case _ => ()
      }
    }, Some(Color.White))
    save.position.left := ui.size.width() - 44.0
    save.position.top @= 12.0
    save.backgroundColor @= Color.Black.withAlpha(0.65)
    val panel = new Container {
      size.width := ui.size.width()
      size.height := ui.size.height()
      backgroundColor @= Color.Black
      focusable @= true
      touchDrags @= true
      override def keepsKey(key: Key): Boolean = key == Key.ArrowLeft || key == Key.ArrowRight
      children ++= List(leaving, view, pill) ++ (if (saves) List(save) else Nil)
    }
    def local(e: PointerEvent): (Double, Double) = (e.x - panel.absoluteLeft, e.y - panel.absoluteTop)
    panel.event.key.down.attach { k =>
      val step = if (k.key == Key.ArrowRight) 1 else if (k.key == Key.ArrowLeft) -1 else 0
      val next = index() + step
      if (k.name == "+" || k.name == "=") zoomAt(zoomed() * 1.5, wide / 2.0, tall / 2.0)
      else if (k.name == "-") zoomAt(zoomed() / 1.5, wide / 2.0, tall / 2.0)
      else if (step != 0 && next >= 0 && next < sources.size) {
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
    // a drag moves a zoomed picture about (taken up again from where the finger is after a pinch), and is no click
    def drag(e: PointerEvent): Unit = if (!pinching) {
      if (math.abs(e.x - pressX) + math.abs(e.y - pressY) > 6.0) moved = true
      last.foreach { case (lx, ly) =>
        panX += e.x - lx
        panY += e.y - ly
        place()
      }
      last = Some((e.x, e.y))
    }
    panel.event.pointer.down.attach { e =>
      if (e.button == PointerEvent.PrimaryButton) {
        pressX = e.x
        pressY = e.y
        moved = false
        if (zoomed() > 1.0) {
          last = Some((e.x, e.y))
          Pointers.track(m => drag(m), _ => last = None)
        }
      }
    }
    panel.event.pointer.move.attach(e => pointerAt = Some(local(e)))
    Gestures.take(panel.node, (f, x, y) => {
      pinching = true
      moved = true
      zoomAt(zoomed() * f, x - panel.absoluteLeft, y - panel.absoluteTop)
    }, () => {
      pinching = false
      last = None
    })
    panel.event.pointer.wheel.attach { e =>
      val (x, y) = pointerAt.getOrElse((wide / 2.0, tall / 2.0))
      if (e.primary || e.ctrl) zoomAt(zoomed() * math.pow(2.0, -math.max(-1000.0, math.min(1000.0, e.y)) / 250.0), x, y)
      else {
        panX -= e.x
        panY -= e.y
        place()
      }
    }
    // a click on the black about the picture closes it, as one outside it does; two on the picture zoom it, or back
    panel.event.click.attach { e =>
      val (px, py) = local(e)
      val x = px - view.position.left()
      val y = py - view.position.top()
      val outside = x < 0.0 || y < 0.0 || x > view.size.width() || y > view.size.height()
      if (moved) ()
      else if (outside) Modal.remove(panel)
      else if (Platform.ticks() - clickedAt < ImageViewer.doubleClick) {
        clickedAt = -1.0e9
        if (zoomed() > 1.0) zoomAt(1.0, px, py) else zoomAt(ImageViewer.doubled, px, py)
      }
      else clickedAt = Platform.ticks()
      moved = false
    }
    // the screen turned or the window sized again: the picture fitted to it afresh
    def refit(): Unit = if (fitW > 0.0) {
      val _ = fit(view.image(), 0.0)
    }
    panel.follow(ui.size.width, (_: Double) => refit())
    panel.follow(ui.size.height, (_: Double) => refit())
    show(start, 0)
    near(start)
    Modal.over(panel, panel, closed, dim = false)
    // its sheet black too, which a phone's bars are painted as (Modal: the window's edges)
    panel.parent().foreach(_.backgroundColor @= Color.Black)
  }

  /** Which picture is showing. */
  def showing: Val[Int] = index

  /** How many times its fitted size the picture showing is drawn: 1 whole. */
  def zoom: Val[Double] = zoomed
}

object ImageViewer {
  /** How far a picture zooms: this many times its fitted size. */
  val deepest: Double = 5.0
  /** What a double click or tap zooms to. */
  val doubled: Double = 2.5
  /** How close two clicks come to be a double one, in milliseconds. */
  val doubleClick: Double = 350.0
}
