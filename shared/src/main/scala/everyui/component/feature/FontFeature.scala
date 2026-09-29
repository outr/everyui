package everyui.component.feature

import everyui.component.Component
import everyui.component.types.{FontWeight, Prop}
import everyui.theme.Theme

/** youi's `FontFeature`: family, weight, size (pixels) and slant. Setting a weight converted from a Google Fonts weight
  * sets the family too. */
class FontFeature(component: Component) {
  /** The theme's family (`Theme.family`, as it is when the text is made) until this names its own. */
  lazy val family: Prop[String] = component.own(
    new Prop[String](Theme.family(), _ => applyFont(), () => component.measure.trigger()))

  lazy val weight: Prop[FontWeight] = component.own(new Prop[FontWeight](FontWeight.Normal, w => {
    if (w.family.nonEmpty) family @= w.family
    applyFont()
  }, () => component.measure.trigger()))

  lazy val size: Prop[Double] = component.own(
    new Prop[Double](16.0, _ => applyFont(), () => component.measure.trigger()))
  /** The height of a line as a multiple of the size (a paragraph's 1.7, say); 0 is the face's own line box. Only the
    * web spaces lines by it so far. */
  lazy val lineHeight: Prop[Double] = component.own(
    new Prop[Double](0.0, _ => applyFont(), () => component.measure.trigger()))
  /** Extra space after each letter, in pixels (negative draws them closer); 0 is the face's own. A browser, Android and
    * an Apple TV space the letters and measure the spaced width; JavaFX has no letter spacing, so the desktop draws the
    * letters as the face sets them but takes the spaced width, keeping the layout; a Roku's Label spaces none.
    * Material's type scale spaces its styles (bodyLarge 0.5, labelLarge 0.1, titleMedium 0.15, bodySmall 0.4):
    * {{{
    * label.font.letterSpacing @= 0.1
    * }}} */
  lazy val letterSpacing: Prop[Double] = component.own(new Prop[Double](0.0, px => {
    spacing = px
    component.node.setLetterSpacing(px)
  }, () => component.measure.trigger()))
  // what letterSpacing is, read without making it: most text never spaces its letters
  private var spacing: Double = 0.0
  /** Slanted: the family's own italic where one is registered (`Faces.register` with `italic = true`), else its upright
    * letters slanted by the platform (a browser, JavaFX, Android and an Apple TV each slant a face that has none). A
    * Roku draws the registered italic and otherwise the upright letters, as its fonts cannot be slanted. The measured
    * width is the slanted words':
    * {{{
    * empty.font.italic @= true
    * }}} */
  lazy val italic: Prop[Boolean] = component.own(new Prop[Boolean](false, on => {
    slanted = on
    component.node.setItalic(on)
  }, () => component.measure.trigger()))
  // what italic is, read without making it: most text is upright
  private var slanted: Boolean = false

  private def applyFont(): Unit = {
    component.node.setFont(family(), weight().value, size())
    if (slanted) component.node.setItalic(true)
    if (lineHeight() > 0.0) component.node.setLineHeight(lineHeight() * size())
    // spacing given in ems where the platform takes them is kept in pixels as the size changes
    if (spacing != 0.0) component.node.setLetterSpacing(spacing)
  }

  // The defaults reach the node too, so a text view that never sets a font still lays out by the shared line box.
  applyFont()
}
