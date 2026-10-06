package everyui.event

/** Where a finger's tap goes that lands near a small control rather than on it (`Component.tapSlop`): a fingertip
  * covers far more than a mouse's point, so a control smaller than `reach` across (a 24 px glyph, a 32 px icon
  * button) answers a tap anywhere in the `reach` square around its centre, as Apple (44 pt) and Material (48 dp) ask
  * of a touch target, while it is drawn, and laid out beside its neighbours, at its own size. A control already
  * larger than `reach` reaches no further than its edges. Where two such squares overlap, the control whose centre
  * is nearer the tap takes it. A mouse's clicks are never moved. */
object TapSlop {
  /** How far across a control a finger can tap: Material's 48, which holds Apple's 44. */
  val reach: Double = 48.0

  /** Which of `boxes` (left, top, width and height each) a tap at `x`, `y` is for: of those whose reach holds it,
    * the one with its centre nearest. */
  def pick(x: Double, y: Double, boxes: Vector[(Double, Double, Double, Double)]): Option[Int] = {
    var best = -1
    var nearest = Double.MaxValue
    boxes.indices.foreach { i =>
      val (left, top, width, height) = boxes(i)
      val cx = left + width / 2.0
      val cy = top + height / 2.0
      val across = math.max(width, reach) / 2.0
      val down = math.max(height, reach) / 2.0
      if (width > 0.0 && height > 0.0 && math.abs(x - cx) <= across && math.abs(y - cy) <= down) {
        val d = math.hypot(x - cx, y - cy)
        if (d < nearest) {
          nearest = d
          best = i
        }
      }
    }
    if (best < 0) None else Some(best)
  }

  /** Which of `boxes` a finger's tap is to be taken to, if not where the platform sent its click: reached from where
    * the finger went down (`x`, `y`), never from where a browser moved the click to (Chrome on a phone moves it a few
    * pixels towards what listens for clicks, a list under a header, say, which can take it out of a control's reach);
    * of those `open`, as nothing else that answers a click under the finger holds them back; and none where the click
    * goes to the one chosen already (`already`). */
  def retarget(x: Double, y: Double, boxes: Vector[(Double, Double, Double, Double)], open: Vector[Boolean],
               already: Option[Int]): Option[Int] = {
    val allowed = boxes.indices.toVector.filter(i => open(i))
    pick(x, y, allowed.map(i => boxes(i))).map(i => allowed(i)).filterNot(i => already.contains(i))
  }
}
