package everyui.render

import javafx.animation.AnimationTimer

/** The app's frames on JavaFX pulses, one asked for at a time (`Platform.nextFrame`): the timer runs only while a
  * frame is wanted, since a running AnimationTimer has JavaFX pulse every frame whether anything changed or not. Left
  * stopped, JavaFX pulses only when the scene changes. On the JavaFX thread. */
object FrameTimer extends AnimationTimer {
  private var wanted: Vector[() => Unit] = Vector.empty
  private var started = false

  /** `f` once, on the next pulse. */
  def next(f: () => Unit): Unit = {
    wanted = wanted :+ f
    if (!started) {
      started = true
      start()
    }
  }

  override def handle(now: Long): Unit = {
    val due = wanted
    wanted = Vector.empty
    due.foreach(f => f())
    // what `due` asked for in turn keeps the timer running into the next pulse
    if (wanted.isEmpty) {
      started = false
      stop()
    }
  }
}
