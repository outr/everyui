package everyui

/** Where a radial gradient (`Gradient.radial`) is centred and how far it reaches: its centre `x` and `y` as fractions
  * of the box (0.5, 0.5 its middle; Flutter's `Alignment(ax, ay)` is `((ax + 1) / 2, (ay + 1) / 2)`), and the circle
  * its last stop lies on, `radius` as a fraction of the box's shorter side (Flutter's `RadialGradient.radius`). Past
  * the circle, the last stop's colour. */
case class RadialShape(x: Double = 0.5, y: Double = 0.5, radius: Double = 0.5)
