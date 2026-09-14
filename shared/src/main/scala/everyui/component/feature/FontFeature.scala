package everyui.component.feature

import everyui.component.Component
import everyui.component.types.{FontWeight, Prop}
import everyui.theme.Theme

/** youi's `FontFeature`: family, weight and size (pixels). Setting a weight converted from a Google Fonts weight
  * sets the family too. */
class FontFeature(component: Component) {
  /** The theme's family (`Theme.family`, as it is when the text is made) until this names its own. */
  lazy val family: Prop[String] = new Prop[String](Theme.family(), _ => applyFont(), () => component.measure.trigger())

  lazy val weight: Prop[FontWeight] = new Prop[FontWeight](FontWeight.Normal, w => {
    if (w.family.nonEmpty) family @= w.family
    applyFont()
  }, () => component.measure.trigger())

  lazy val size: Prop[Double] = new Prop[Double](16.0, _ => applyFont(), () => component.measure.trigger())

  private def applyFont(): Unit = component.node.setFont(family(), weight().value, size())

  // The defaults reach the node too, so a text view that never sets a font still lays out by the shared line box.
  applyFont()
}
