package everyui.zoom

import everyui.easing.Easing
import everyui.task.DurationTask

import scala.concurrent.duration.*

/** A canvas's view moved to `to` over `seconds`: the scale in even steps of its ratio and the middle of the view in a
  * straight line across the world, so the eye follows one place in to another. */
private[zoom] class ZoomMove(canvas: ZoomCanvas, to: ZoomView, seconds: Double) extends DurationTask {
  private var from: ZoomView = to

  override def time: FiniteDuration = (seconds * 1000.0).toLong.millis

  override def act(delta: Double, elapsed: Double, progress: Double, reset: Boolean): Unit = {
    if (reset) from = canvas.view()
    val t = Easing.cubicInOut.calculate(progress)
    val w = canvas.size.width()
    val h = canvas.size.height()
    val scale = from.scale * math.pow(to.scale / from.scale, t)
    val cx = from.x + w / 2.0 / from.scale + (to.x + w / 2.0 / to.scale - from.x - w / 2.0 / from.scale) * t
    val cy = from.y + h / 2.0 / from.scale + (to.y + h / 2.0 / to.scale - from.y - h / 2.0 / from.scale) * t
    canvas.view @= (if (progress >= 1.0) to else ZoomView(cx - w / 2.0 / scale, cy - h / 2.0 / scale, scale))
  }
}
