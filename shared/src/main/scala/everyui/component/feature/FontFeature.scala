package everyui.component.feature

import everyui.component.Component
import everyui.component.types.{FontWeight, Prop}
import everyui.theme.Theme

/** youi's `FontFeature`: family, weight, size (pixels) and slant, and a variable face's axes (`opticalSizing`,
  * `variations`). Setting a weight converted from a Google Fonts weight
  * sets the family too. */
class FontFeature(component: Component) {
  /** The theme's family (`Theme.family`, as it is when the text is made) until this names its own. */
  lazy val family: Prop[String] = component.own(
    new Prop[String](Theme.family(), _ => applyFont(), () => restyled()))

  lazy val weight: Prop[FontWeight] = component.own(new Prop[FontWeight](FontWeight.Normal, w => {
    if (w.family.nonEmpty) family @= w.family
    applyFont()
  }, () => restyled()))

  lazy val size: Prop[Double] = component.own(
    new Prop[Double](16.0, _ => applyFont(), () => restyled()))
  /** The height of a line as a multiple of the size (a paragraph's 1.7, say); 0 is the face's own line box. Only the
    * web spaces lines by it so far. */
  lazy val lineHeight: Prop[Double] = component.own(
    new Prop[Double](0.0, _ => applyFont(), () => restyled()))
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
  }, () => restyled()))
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
  }, () => restyled()))
  // what italic is, read without making it: most text is upright
  private var slanted: Boolean = false

  /** Whether a variable face shapes its letters for the size they are drawn at (its `opsz` axis): `None`, the
    * platform's way (a browser's `font-optical-sizing: auto`); `Some(false)` the face's default axis whatever the size,
    * as Flutter and most native text draw it; `Some(true)` sized. Words measure as they are drawn, the axis included.
    * The app's default for text made from then on is `FontFeature.opticalSizing`. Only the web has the axis to set;
    * elsewhere the platform's own drawing stands.
    * {{{
    * FontFeature.opticalSizing @= Some(false)   // at start: every word drawn and measured as Flutter does
    * }}} */
  lazy val opticalSizing: Prop[Option[Boolean]] = component.own(new Prop[Option[Boolean]](optical, o => {
    optical = o
    applyAxes()
  }, () => restyled()))
  // what opticalSizing is, read without making it
  private var optical: Option[Boolean] = FontFeature.opticalSizing()
  /** A variable face's axes set by their tags (`"wdth" -> 87.5`, `"GRAD" -> 50`), as CSS `font-variation-settings`;
    * weight and slant are `weight` and `italic`'s. Empty, the default, sets none. Words measure as they are drawn.
    * Only the web sets them so far. */
  lazy val variations: Prop[Map[String, Double]] = component.own(new Prop[Map[String, Double]](Map.empty, v => {
    varied = v
    applyAxes()
  }, () => restyled()))
  // what variations is, read without making it: most text sets none
  private var varied: Map[String, Double] = Map.empty

  private def applyAxes(): Unit = component.node.setFontAxes(optical.map(on => if (on) "auto" else "none").getOrElse(""),
    varied.toList.map { case (tag, value) => "'" + tag + "' " + value.toString }.mkString(", "))

  /** How many times the face, size, weight, slant, spacing or axes have changed: what words cut to fit go by
    * (`TextView`), which are cut again once it has moved. */
  private[everyui] var changes: Int = 0
  private def restyled(): Unit = {
    changes += 1
    component.measure.trigger()
  }

  private def applyFont(): Unit = {
    component.node.setFont(family(), weight().value, size())
    if (slanted) component.node.setItalic(true)
    if (lineHeight() > 0.0) component.node.setLineHeight(lineHeight() * size())
    // spacing given in ems where the platform takes them is kept in pixels as the size changes
    if (spacing != 0.0) component.node.setLetterSpacing(spacing)
    // (a shorthand a platform sets for a field puts the axes back to its own: set again after it)
    if (optical.nonEmpty || varied.nonEmpty) applyAxes()
  }

  // The defaults reach the node too, so a text view that never sets a font still lays out by the shared line box.
  applyFont()
}

object FontFeature {
  /** Whether text made from now on sizes a variable face for its size (`FontFeature.opticalSizing` of each): `None`,
    * the default, leaves it to the platform. Set it before the screens are made. */
  val opticalSizing: reactify.Var[Option[Boolean]] = reactify.Var(None)
}
