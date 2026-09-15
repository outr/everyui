package everyui.component

import everyui.font.MaterialIcon
import everyui.render.Nodes
import reactify.Var

/** youi's `MaterialIconView`: one Material Icons glyph, `points` pixels square; `icon` changes it, `color` tints it. */
class MaterialIconView(initial: MaterialIcon = MaterialIcon.Empty, points: Double = 24.0) extends TextView(Nodes.icon(initial.glyph, points)) {
  val icon: Var[MaterialIcon] = Var(initial)
  // the text view's font starts as the default family at 16px: the glyph needs its own face, at its own size
  font.family @= "Material Icons"
  font.size @= points
  icon.attach(i => content @= i.glyph)

  override def kind: String = "MaterialIconView(" + icon().name + ")"
}
