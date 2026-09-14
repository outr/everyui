package everyui.font

/** A bundled face (`assets/fonts/`): the file every backend loads, and the vertical metrics every backend lays a line
  * of text out with.
  *
  * Each platform's own idea of a line's height differs — a browser's `line-height: normal`, a SceneGraph `Label`'s
  * bounding rect, an Android `TextView`, a JavaFX `Text` — so no backend asks its platform. They all take the box
  * from here, and the geometry is identical everywhere. The numbers are the fonts' own (`unitsPerEm`, and the
  * ascender and descender a browser uses); `tools/check.sh` fails if they drift from the files. */
class Face(val family: String, val weight: Int, val file: String, val unitsPerEm: Double, val ascender: Double,
           val descender: Double) {
  /** The height of one line at `size` pixels: ascent plus descent, rounded to whole pixels. */
  def lineHeight(size: Double): Double = ((ascender + descender) * size / unitsPerEm + 0.5).toInt.toDouble

  /** From the top of that line box to the baseline, with the rounding's leftover split evenly above and below. */
  def baseline(size: Double): Double = (lineHeight(size) + (ascender - descender) * size / unitsPerEm) / 2.0
}
