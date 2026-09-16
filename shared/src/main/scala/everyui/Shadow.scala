package everyui

/** A soft shadow under a box: offset `x` and `y`, blurred over `blur` pixels, in `color`. Only the web and the desktop
  * draw one. */
case class Shadow(x: Double, y: Double, blur: Double, color: Color)
