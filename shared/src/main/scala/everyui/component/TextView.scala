package everyui.component

import everyui.Color
import everyui.component.feature.FontFeature
import everyui.component.types.{Prop, WhiteSpace}
import everyui.render.{Nodes, RenderNode}
import everyui.theme.Theme

class TextView(node: RenderNode = Nodes.text()) extends Component(node) {
  lazy val content: Prop[String] = new Prop[String](node.getText(), node.setText, () => measure.trigger())
  val font: FontFeature = new FontFeature(this)

  override def kind: String = "TextView(" + content() + ")"
}

/** A line of text in one call: `TextView("Hello")`, `TextView.bound(name() + "!")`, `TextView.wrapped(long, 320.0)`. */
object TextView {
  def apply(value: String, points: Double = 14.0, colour: => Color = Theme.text()): TextView = new TextView {
    content @= value
    font.size @= points
    color := colour
  }

  /** Bound to `value`, so it follows what it reads. */
  def bound(value: => String, points: Double = 14.0, colour: => Color = Theme.text()): TextView = new TextView {
    content := value
    font.size @= points
    color := colour
  }

  /** Bound to `value`, and out of the way — no height — while there is none. */
  def optional(value: => Option[String], points: Double = 14.0, colour: => Color = Theme.text()): TextView = {
    val view = bound(value.getOrElse(""), points, colour)
    view.size.height := (if (value.isEmpty) 0.0 else view.measured.height())
    view
  }

  /** Wrapped to `width`, as tall as it needs. */
  def wrapped(value: String, width: Double, points: Double = 14.0, colour: => Color = Theme.text()): TextView = new TextView {
    content @= value
    font.size @= points
    color := colour
    whiteSpace @= WhiteSpace.Normal
    size.width @= width
    measure.trigger()
  }
}
