package everyui.kit

/** Where a `Toast` shows: at the foot of the screen, or its head with `top`, `margin` in from that edge. */
case class ToastPlacement(top: Boolean = false, margin: Double = 32.0)

object ToastPlacement {
  /** At the foot of the screen, 32 in from it: what a toast has always done. */
  val Bottom: ToastPlacement = ToastPlacement()
  /** At the head of the screen, 32 in from it (and under `Toast.topInset`). */
  val Top: ToastPlacement = ToastPlacement(top = true)
}
