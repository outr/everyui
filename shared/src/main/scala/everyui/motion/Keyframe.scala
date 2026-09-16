package everyui.motion

/** Where a [[Track]] is at fraction `at` of its cycle: `a`, and `b` for a property with two values. */
case class Keyframe(at: Double, a: Double, b: Double = 0.0)
