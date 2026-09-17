package everyui.component

import everyui.{Color, Gradient}
import everyui.component.feature.FontFeature
import everyui.component.types.{Prop, TextAlign, WhiteSpace}
import everyui.render.{Nodes, RenderNode}
import everyui.theme.Theme
import reactify.Var

class TextView(node: RenderNode = Nodes.text()) extends Component(node) {
  lazy val content: Prop[String] = new Prop[String](node.getText(), _ => fit(), () => measure.trigger())
  /** The widest these words may be drawn on one line: what will not fit is cut short with an ellipsis, as a title
    * beside a chip is, so whatever sits after them keeps its place. 0 for no limit. */
  lazy val maxWidth: Var[Double] = Var(0.0)
  /** The most lines wrapped words may take: what will not fit is cut short at a word with an ellipsis, as a synopsis
    * under an episode's name is. 0 for no limit. */
  lazy val maxLines: Var[Int] = Var(0)
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
  size.width.attach(_ => if (size.width.explicit && whiteSpace() != WhiteSpace.NoWrap) {
    if (maxLines() > 0) fit() else measure.trigger()
  })
  maxWidth.attach(_ => fit())
  maxLines.attach(_ => fit())

  /** Draw the words, or as many of them as `maxWidth` or `maxLines` leaves room for with an ellipsis after. The
    * platform measures each try, so the cut is the one the words really take, and not a guess at how wide a letter
    * is. */
  private def fit(): Unit = {
    val wanted = content()
    val limit = maxWidth()
    node.setText(wanted)
    if (limit > 0.0 && node.getWidth() > limit) {
      var fewest = 0
      var most = wanted.length
      while (fewest < most) {
        val half = (fewest + most + 1) / 2
        node.setText(wanted.substring(0, half) + "…")
        if (node.getWidth() <= limit) fewest = half else most = half - 1
      }
      node.setText(wanted.substring(0, fewest) + "…")
    }
    val lines = maxLines()
    if (lines > 0 && whiteSpace() != WhiteSpace.NoWrap && wanted.nonEmpty) {
      // a line's height, from one letter: n lines are no taller than n of them, give or take half of one
      node.setText(wanted.substring(0, 1))
      val one = node.getHeight()
      val tallest = one * lines + one / 2.0
      node.setText(wanted)
      if (one > 0.0 && node.getHeight() > tallest) {
        var fewest = 0
        var most = wanted.length
        while (fewest < most) {
          val half = (fewest + most + 1) / 2
          node.setText(wanted.substring(0, half) + "…")
          if (node.getHeight() <= tallest) fewest = half else most = half - 1
        }
        // back to the last whole word, where there is one
        val space = wanted.substring(0, fewest).lastIndexOf(' ')
        val cut = if (space > fewest / 2) space else fewest
        node.setText(wanted.substring(0, cut).trim + "…")
      }
    }
    // what is drawn decides the width: a cut that came from a narrower `maxWidth` changes nothing else
    measure.trigger()
  }

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
