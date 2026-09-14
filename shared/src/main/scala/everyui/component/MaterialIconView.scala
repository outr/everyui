package everyui.component

import everyui.font.MaterialIcon
import everyui.render.Nodes
import reactify.Var

/** youi's `MaterialIconView`: one Material Icons glyph, `size` pixels square; `icon` changes it, `color` tints it. */
class MaterialIconView(initial: MaterialIcon = MaterialIcon.Empty, size: Double = 24.0) extends TextView(Nodes.icon(initial.glyph, size)) {
  val icon: Var[MaterialIcon] = Var(initial)
  icon.attach(i => content @= i.glyph)

  override def kind: String = "MaterialIconView(" + icon().name + ")"
}
