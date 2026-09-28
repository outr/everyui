package everyui.kit

import everyui.component.*
import everyui.Color
import reactify.*

/** A ring lit clockwise from the top as far as `fraction` (0 to 1) goes, over a ring of `track`: a countdown's dial, a
  * determinate progress circle.
  *
  * Nothing draws an arc, so the lit part is two half rings turned about the centre, each seen only through one half of
  * the box: the left half ring turns into the right-hand window over the first half of the way, and the right half
  * ring into the left-hand window over the second. (A Roku draws no rounded edge, so it shows no ring at all.) */
class Ring(diameter: Double, stroke: Double, fraction: Val[Double], fill: () => Color, track: () => Color)
  extends Container {
  private val half = diameter / 2.0

  private def circle(colour: () => Color): Container = new Container {
    size.width @= diameter
    size.height @= diameter
    radius @= half
    borderWidth @= stroke
    borderColor := colour()
  }

  /** The lit ring cut to its left or its right half, in a box the ring's own size so that it turns about the centre. */
  private def halfRing(left: Boolean): Container = {
    val ring = circle(fill)
    ring.position.left @= (if (left) 0.0 else -half)
    val cut = new Container {
      size.width @= half
      size.height @= diameter
      position.left @= (if (left) 0.0 else half)
      children += ring
    }
    cut.node.setClip(half, diameter)
    new Container {
      size.width @= diameter
      size.height @= diameter
      children += cut
    }
  }

  /** One half of the box, seeing only what turns into it. */
  private def window(left: Boolean, turning: Container): Container = {
    turning.position.left @= (if (left) 0.0 else -half)
    val seen = new Container {
      size.width @= half
      size.height @= diameter
      position.left @= (if (left) 0.0 else half)
      children += turning
    }
    seen.node.setClip(half, diameter)
    seen
  }

  private val first = halfRing(left = true)
  private val second = halfRing(left = false)
  first.rotation := math.max(0.0, math.min(0.5, fraction()))
  second.rotation := math.max(0.0, math.min(0.5, fraction() - 0.5))

  size.width @= diameter
  size.height @= diameter
  children ++= List(circle(track), window(left = false, first), window(left = true, second))
}
