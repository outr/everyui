package everyui.font

/** A bundled face (`assets/fonts/`): the file every backend loads, and the vertical metrics every backend lays a line
  * of text out with.
  *
  * Each platform's own idea of a line's height differs — a browser's `line-height: normal`, a SceneGraph `Label`'s
  * bounding rect, an Android `TextView`, a JavaFX `Text` — so no backend asks its platform. They all take the box
  * from here, and the geometry is identical everywhere. The numbers are the fonts' own (`unitsPerEm`, and the
  * ascender and descender a browser uses); `tools/check.sh` fails if they drift from the files. */
class Face(val family: String, val weight: Int, val file: String, val unitsPerEm: Double, val ascender: Double,
           val descender: Double):
  /** The height of one line at `size` pixels: ascent plus descent, rounded to whole pixels. */
  def lineHeight(size: Double): Double = ((ascender + descender) * size / unitsPerEm + 0.5).toInt.toDouble

  /** From the top of that line box to the baseline, with the rounding's leftover split evenly above and below. */
  def baseline(size: Double): Double = (lineHeight(size) + (ascender - descender) * size / unitsPerEm) / 2.0

object Faces:
  lazy val OpenSansRegular: Face = new Face("Open Sans", 400, "OpenSans-Regular.ttf", 2048.0, 2189.0, 600.0)
  lazy val OpenSansSemiBold: Face = new Face("Open Sans", 600, "OpenSans-SemiBold.ttf", 2048.0, 2189.0, 600.0)
  lazy val MaterialIcons: Face = new Face("Material Icons", 400, "MaterialIcons-Regular.ttf", 512.0, 512.0, 0.0)

  /** The bundled face for a family and CSS weight — Open Sans for a family that is not bundled. */
  def apply(family: String, weight: Int): Face =
    if family == "Material Icons" then MaterialIcons
    else if weight >= 600 then OpenSansSemiBold
    else OpenSansRegular
