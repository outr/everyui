package everyui.component

import everyui.render.Nodes

/** A Material Icons glyph, by codepoint, `size` pixels square — youi's `<i class="material-icons">`. A text
  * view, so `content` changes the glyph and `color` tints it. */
class Icon(glyph: String, size: Double) extends TextView(Nodes.icon(glyph, size)) {
  override def kind: String = "Icon"
}
