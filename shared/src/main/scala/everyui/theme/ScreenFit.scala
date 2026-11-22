package everyui.theme

import reactify.*

/** The screen as `ui` measures it, over the size the app is drawn at: the room the device's text size is held to
  * (`room`, from `screenRoom`), then the screen in the app's units (`width`, `height`), each set only where it is more
  * than rounding away (`UiScale.near`), the room first, so a change of window is laid out once at the new size. The
  * size drawn at (`drawn`, worked out by `scale`) is told to the platform (`drawAt`) and the screen measured again as it
  * changes, heard from before the first measure: the room that measure sets can change it (a device's text size held
  * to the window), and a change heard by nobody left the app drawn at the size worked out with no room, the whole
  * text size on a narrow phone, until the window or the text size next changed (Voidcraft, EveryUI 0.1.41). */
private[everyui] final class ScreenFit(room: Var[Double], scale: () => Double, screenRoom: () => Double,
                                       screenWidth: () => Double, screenHeight: () => Double,
                                       drawAt: Double => Unit, width: Var[Double], height: Var[Double]) {
  /** The size the app is drawn at. */
  val drawn: Val[Double] = Val(scale())
  drawn.attach { d =>
    drawAt(d)
    measure()
  }
  drawAt(drawn())
  measure()

  /** The screen measured again: as the window changes size, and as the size drawn at does. */
  def measure(): Unit = {
    settle(room, screenRoom())
    settle(width, screenWidth())
    settle(height, screenHeight())
  }

  private def settle(v: Var[Double], to: Double): Unit = if (!UiScale.near(v(), to)) v @= to
}
