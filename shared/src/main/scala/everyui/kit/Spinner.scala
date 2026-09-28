package everyui.kit

import everyui.component.*
import everyui.Color
import everyui.font.MaterialIcon
import everyui.motion.{Keyframe, Track}
import everyui.theme.Theme
import reactify.*

/** An indeterminate spinner: a Material glyph turning once a second. The platform runs the rotation (a Roku Animation
  * node, an Android animator, JavaFX's timeline, the browser's `animate`), so nothing is computed per frame. */
class Spinner(points: Double = 18.0, tint: () => Color = () => Theme.text())
  extends MaterialIconView(MaterialIcon.Autorenew, points) {
  color := tint()
  size.width @= points
  size.height @= points
  motion @= List(Track(Track.Rotation, 1.0, List(Keyframe(0.0, 0.0), Keyframe(1.0, 2.0 * math.Pi))))
}

object Spinner {
  /** A spinner `points` across in the middle of a row `width` across, in the theme's accent: a page still loading. */
  def centred(width: => Double, points: Double = 36.0): Container = {
    val spinner = new Spinner(points, () => Theme.accent())
    spinner.position.left := math.max(0.0, (width - points) / 2.0)
    new Container {
      size.width := width
      size.height @= points
      children += spinner
    }
  }
}
