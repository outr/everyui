package everyui.component

/** How far a container's children reach along one axis, 0 with none — kept up as each child moves or changes size
  * rather than found again over every child each time. A column of a thousand lines laid out moves each line, and
  * walking all the lines for each one took a browser seconds.
  *
  * It keeps the furthest reach and how many children are at it: a child going further is the new furthest, and only
  * the last of the children at the furthest drawing back needs them all counted again (`moved` says so). */
private[component] class Extent {
  private var furthest: Double = 0.0
  private var at: Int = 0

  def value: Double = furthest

  def reset(): Unit = {
    furthest = 0.0
    at = 0
  }

  /** A child reaching `reach` counted in. */
  def add(reach: Double): Unit =
    if (reach > furthest) {
      furthest = reach
      at = 1
    } else if (reach == furthest) at += 1

  /** A child that reached `was` reaches `now`: false when the furthest is no longer known, and every child is to be
    * counted again. */
  def moved(was: Double, now: Double): Boolean = {
    if (now > furthest) {
      furthest = now
      at = 1
    } else if (now == furthest && was != furthest) at += 1
    else if (was == furthest && now < furthest) at -= 1
    at > 0 || furthest == 0.0
  }
}
