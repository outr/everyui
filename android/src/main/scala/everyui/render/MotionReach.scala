package everyui.render

import everyui.motion.Track

/** Where a motion may take a box over its whole cycle (`Motion`'s `reach`): what is in sight is read from this, not
  * from where the motion holds its view at the moment, so a motion that takes its own view off the screen, to bring it
  * back in elsewhere, is not stopped by it.
  *
  * The box (`left`, `top`, `right`, `bottom`, in the view) is carried to its parent from the view's own place: turned
  * and scaled about the pivot as far as any turn or scale could take it (a circle through its farthest corner, at the
  * largest scale), and moved to every place a position track goes (`x`, `y` and each key's `a`, `b` from it), the
  * place it rests at among them. */
object MotionReach {
  def apply(box: Array[Double], tracks: List[Track], x: Double, y: Double, pivotX: Double, pivotY: Double,
            turned: Boolean, scaleX: Double, scaleY: Double): Unit = {
    val scales = tracks.filter(_.property == Track.Scale).flatMap(_.keys)
      .flatMap(k => List(math.abs(k.a), math.abs(k.b)))
    if (turned || scales.nonEmpty || tracks.exists(_.property == Track.Rotation) || scaleX != 1.0 || scaleY != 1.0) {
      val s = (math.abs(scaleX) :: math.abs(scaleY) :: scales).max
      val r = s * math.hypot(math.max(math.abs(box(0) - pivotX), math.abs(box(2) - pivotX)),
        math.max(math.abs(box(1) - pivotY), math.abs(box(3) - pivotY)))
      box(0) = pivotX - r
      box(1) = pivotY - r
      box(2) = pivotX + r
      box(3) = pivotY + r
    }
    val places = tracks.filter(_.property == Track.Position).flatMap(_.keys)
    val xs = 0.0 :: places.map(_.a)
    val ys = 0.0 :: places.map(_.b)
    box(0) += x + xs.min
    box(1) += y + ys.min
    box(2) += x + xs.max
    box(3) += y + ys.max
  }
}
