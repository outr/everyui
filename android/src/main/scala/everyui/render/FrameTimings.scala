package everyui.render

import android.view.{FrameMetrics, Window}

/** Each frame Android draws, as Android measured it (FrameMetrics, Android 7 and up), for [[Platform.frameTimings]].
  *
  * The split is the one a report of jank needs: the part on the thread that draws (whatever held it up before the
  * frame could start, input, animation, which is where the app's own update runs, layout, and recording the drawing),
  * and the rest, the render thread's (syncing, issuing it to the GPU and swapping it onto the screen). Only frames
  * that were drawn are measured, and the first a window draws is left out, as it carries the window's making.
  *
  * The listener is the window's, so it is attached again to each activity the app is shown in
  * ([[everyui.activity.EveryUIActivity]]), and it runs on a thread of its own, never the one it is measuring. */
object FrameTimings {
  @volatile private var sink: Option[(Double, Double) => Unit] = None
  private var attachedTo: Option[android.app.Activity] = None

  private lazy val handler: android.os.Handler = {
    val thread = new android.os.HandlerThread("everyui-frames")
    thread.start()
    new android.os.Handler(thread.getLooper)
  }

  /** Hear every frame drawn from now on: `f(ms, buildMs)`. False before Android 7, which does not measure them. */
  def watch(f: (Double, Double) => Unit): Boolean =
    if (android.os.Build.VERSION.SDK_INT < 24) false
    else {
      sink = Some(f)
      Option(Host.activity).foreach(attach)
      true
    }

  /** Listen to this activity's window, where something is listening at all. */
  def attach(activity: android.app.Activity): Unit =
    if (sink.nonEmpty && android.os.Build.VERSION.SDK_INT >= 24 && !attachedTo.exists(_ eq activity)) {
      try {
        activity.getWindow.addOnFrameMetricsAvailableListener(listener, handler)
        attachedTo = Some(activity)
      } catch { case t: Throwable => android.util.Log.w("everyui", "frame timings unavailable: " + t.getMessage) }
    }

  private lazy val listener: Window.OnFrameMetricsAvailableListener = new Window.OnFrameMetricsAvailableListener {
    override def onFrameMetricsAvailable(window: Window, metrics: FrameMetrics, dropped: Int): Unit = {
      def ms(id: Int): Double = metrics.getMetric(id).toDouble / 1.0e6
      val first = android.os.Build.VERSION.SDK_INT >= 26 && metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L
      if (!first) {
        val building = ms(FrameMetrics.UNKNOWN_DELAY_DURATION) + ms(FrameMetrics.INPUT_HANDLING_DURATION) +
          ms(FrameMetrics.ANIMATION_DURATION) + ms(FrameMetrics.LAYOUT_MEASURE_DURATION) + ms(FrameMetrics.DRAW_DURATION)
        sink.foreach(f => try f(ms(FrameMetrics.TOTAL_DURATION), building) catch { case _: Throwable => () })
      }
    }
  }
}
