package everyui.task

/** Every frame running (`Frames.hold`) until this is released. Releasing it again does nothing. */
class FrameHold private[task] () {
  private var held = true

  def release(): Unit = if (held) {
    held = false
    Frames.release()
  }
}
