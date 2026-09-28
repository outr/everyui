package everyui.draw

import everyui.component.Component
import everyui.component.types.Prop
import everyui.render.Nodes
import reactify.*

/** Paths drawn in a box: lines, curves, circles and shapes, stroked and filled (`figures`). Give it a size; the figures
  * are in its own pixels. It draws again only when its figures or its size change, so it is cheap enough for a row of
  * a long list: bind the figures to the row's item and they follow it as the row is handed item after item.
  *
  * {{{
  *   val lanes = new Drawing
  *   lanes.size.width @= 60.0
  *   lanes.size.height @= 24.0
  *   lanes.figures := row.item().map(commit => graph(commit, Theme.accent())).getOrElse(Nil)
  * }}}
  *
  * The web draws it as inline SVG, the desktop as JavaFX shapes, Android on a canvas of its own. A Roku and an Apple TV
  * draw no paths: they show what rectangles can (`Block.of`: straight strokes across or down, filled rectangles). */
class Drawing extends Component(Nodes.drawing()) {
  override def kind: String = "Drawing"

  lazy val figures: Prop[List[Figure]] = own(new Prop[List[Figure]](Nil, f => node.setFigures(f)))
}
