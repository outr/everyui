package everyui.graph

import everyui.component.{Container, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.draw.{Drawing, Figure, Path}
import everyui.theme.Theme
import reactify.*

/** A [[GraphView]]'s own box for a node: its `label`, and its `detail` under it where there is one, in the theme's
  * colours; an accent colour along its leading side and round its edge, or a dashed outline for one that is planned
  * (`look`); a wider edge in the accent colour while it is `chosen`; and as opaque as `shows` (the graph fades it while
  * another's neighbours are lit). The graph sizes it. */
class GraphNode(label: Val[String], detail: Val[Option[String]], look: Val[NodeLook], chosen: Val[Boolean],
                shows: Val[Double]) extends Container {
  override def kind: String = "GraphNode"

  private val edge: Val[everyui.Color] = Val(look().accent.getOrElse(Theme.border()))
  private val bar = new Container {
    size.width @= 4.0
    radius @= 2.0
  }
  private val outline = new Drawing
  private val name: TextView = TextView.bound(label(), 13.0, Theme.text())
  private val more: TextView = TextView.bound(detail().getOrElse(""), 11.0, Theme.muted())

  radius @= 8.0
  backgroundColor := (if (look().dashed) Theme.surface().withAlpha(0.55) else Theme.raised())
  borderWidth := (if (look().dashed) 0.0 else if (chosen()) 2.0 else 1.0)
  borderColor := (if (chosen()) Theme.accent() else edge())
  opacity := shows()
  bar.backgroundColor := look().accent.getOrElse(everyui.Color.Clear)
  bar.visible := look().accent.nonEmpty
  bar.position.left @= 5.0
  bar.position.top @= 6.0
  bar.size.height := math.max(0.0, size.height() - 12.0)
  outline.size.width := size.width()
  outline.size.height := size.height()
  outline.visible := look().dashed
  outline.figures := (if (!look().dashed || size.width() <= 2.0) Nil
    else List(Figure(Path.roundRect(1.0, 1.0, size.width() - 2.0, size.height() - 2.0, 8.0).dashed(6.0, 4.0),
      stroke = Some(if (chosen()) Theme.accent() else edge()), width = if (chosen()) 2.0 else 1.5)))
  name.font.weight @= FontWeight.SemiBold
  name.maxWidth := math.max(0.0, size.width() - 28.0)
  more.maxWidth := math.max(0.0, size.width() - 28.0)
  more.visible := detail().nonEmpty
  name.position.left @= 16.0
  more.position.left @= 16.0
  // the two lines together in the middle of the box
  name.position.top := (size.height() - name.size.height() -
    (if (detail().nonEmpty) more.size.height() + 2.0 else 0.0)) / 2.0
  more.position.top := name.position.top() + name.size.height() + 2.0
  semantics := Semantics("button", label = label() + detail().map(d => ", " + d).getOrElse(""))
  children ++= List(outline, bar, name, more)
}
