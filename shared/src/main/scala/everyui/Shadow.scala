package everyui

/** A soft shadow under a box: offset `x` and `y`, blurred over `blur` pixels, in `color`, grown on every side by
  * `spread` pixels before it is blurred (negative shrinks it, as CSS's spread: a shadow tucked under a translucent
  * card). Several are layered by `Component.shadows`. The web draws all of it; the desktop draws the first layer
  * without its spread, Android the first as its elevation, and a Roku and an Apple TV none. */
case class Shadow(x: Double, y: Double, blur: Double, color: Color, spread: Double = 0.0) {
  /** As the seam takes it: `"x y blur spread #rrggbbaa"`. */
  def encoded: String = x.toString + " " + y.toString + " " + blur.toString + " " + spread.toString + " " + color.toHexAlpha
}

object Shadow {
  /** Layers as the seam takes them: each `encoded`, joined by `;`. */
  def encode(layers: List[Shadow]): String = layers.map(_.encoded).mkString(";")

  /** The seam's layers back as `(x, y, blur, spread, hex)`, for a backend to draw from. */
  def decode(encoded: String): List[(Double, Double, Double, Double, String)] =
    if (encoded.isEmpty) Nil
    else encoded.split(";").toList.map { layer =>
      val parts = layer.split(" ")
      (parts(0).toDouble, parts(1).toDouble, parts(2).toDouble, parts(3).toDouble, parts(4))
    }
}
