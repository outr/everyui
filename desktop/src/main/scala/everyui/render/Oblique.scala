package everyui.render

import javafx.scene.text.Text
import javafx.scene.transform.Shear

/** Italic where the face has none: JavaFX answers a family with no italic of its own (Open Sans, as bundled) with its
  * upright letters, so they are slanted here instead, sheared about their baseline as a browser slants such a face.
  * The shear leaves the words' layout bounds, and so their measured width and where the next words go, as they were.
  * Nothing else in the backend shears a node, so a `Shear` on text is this one. */
object Oblique {
  // the slant a browser gives a face it has no italic for: about 11 degrees
  private val slant: Double = -0.2

  /** Slant `t`'s letters if it is to be italic and its font is not, and straighten them otherwise. */
  def apply(t: Text, italic: Boolean): Unit = {
    t.getTransforms.removeIf(_.isInstanceOf[Shear])
    val style = t.getFont.getStyle.toLowerCase
    if (italic && !style.contains("italic") && !style.contains("oblique"))
      t.getTransforms.add(new Shear(slant, 0.0, 0.0, t.getLayoutBounds.getMinY + t.getBaselineOffset))
  }
}
