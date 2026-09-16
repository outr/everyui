package everyui.component

import everyui.{Color, Gradient}
import everyui.component.feature.FontFeature
import everyui.component.types.{Prop, TextAlign, WhiteSpace}
import everyui.render.{Nodes, RenderNode}
import everyui.theme.Theme

class TextView(node: RenderNode = Nodes.text()) extends Component(node) {
  lazy val content: Prop[String] = new Prop[String](node.getText(), node.setText, () => measure.trigger())
  val font: FontFeature = new FontFeature(this)
  /** Where the text sits in its box, by the reading direction: it matters once the box is wider than the words
    * (wrapped, or given a width). */
  lazy val align: Prop[TextAlign] = new Prop[TextAlign](TextAlign.Start, a => node.setTextAlign(TextView.side(a)))
  /** The letters painted with a gradient instead of `color` (the web; elsewhere its first stop). */
  lazy val fill: Prop[Option[Gradient]] = new Prop[Option[Gradient]](None, g => g match {
    case Some(value) => node.setTextGradient(value.angle, value.encoded)
    case None => node.setTextGradient(0.0, "")
  })

  // wrapped to a width that changes, the words take a different number of lines
  size.width.attach(_ => if (size.width.explicit && whiteSpace() != WhiteSpace.NoWrap) measure.trigger())

  override def kind: String = "TextView(" + content() + ")"
}

/** A line of text in one call: `TextView("Hello")`, `TextView.bound(name() + "!")`, `TextView.wrapped(long, 320.0)`. */
object TextView {
  /** The seam's name for an alignment: "start", "center" or "end", resolved against the platform's direction. */
  def side(align: TextAlign): String =
    if (align == TextAlign.Center) "center" else if (align == TextAlign.End) "end" else "start"

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

  /** Words that follow what they read, wrapped to `width` and aligned in it: a paragraph, a note under a form. */
  def paragraph(words: => String, width: Double, points: Double = 14.0, colour: => Color = Theme.text(),
                side: TextAlign = TextAlign.Start): TextView = new TextView {
    whiteSpace @= WhiteSpace.Normal
    size.width @= width
    font.size @= points
    color := colour
    align @= side
    content := words
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
