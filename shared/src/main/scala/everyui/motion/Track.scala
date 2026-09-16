package everyui.motion

/** One property of a node moving through `keys` over `seconds`, again and again for as long as it is set, after waiting
  * `delay` seconds the first time. The platform runs it (a Roku's Animation node, an Android animator, the browser's
  * `animate`, a JavaFX timeline), so nothing is computed per frame: what a particle needs on a device with no time to
  * spare.
  *
  * `property` is one of the names below. Each key is at a fraction of the cycle (0 to 1, rising; two at nearly the
  * same fraction make a jump), with its values straight between keys:
  *   - `position`: `a`, `b` — how far right and down from where the node is placed, in layout units
  *   - `rotation`: `a` — radians, clockwise, about the node's centre
  *   - `opacity`:  `a` — 0 to 1
  *   - `scale`:    `a`, `b` — across and down, about the node's centre */
case class Track(property: String, seconds: Double, keys: List[Keyframe], delay: Double = 0.0)

object Track {
  val Position: String = "position"
  val Rotation: String = "rotation"
  val Opacity: String = "opacity"
  val Scale: String = "scale"
}
