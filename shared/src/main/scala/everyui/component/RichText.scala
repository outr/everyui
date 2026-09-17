package everyui.component

import everyui.component.feature.FontFeature
import everyui.component.types.{Prop, WhiteSpace}
import everyui.render.Nodes
import everyui.text.TextRun
import everyui.theme.Theme

/** A paragraph of styled runs, wrapped to its width: bold, italic, code, links (see [[TextRun]]). A link to a path in
  * this app is followed in place, any other opens in a new tab. The paragraph's own font and colour are the runs'
  * defaults. */
class RichText(node: everyui.render.RenderNode = Nodes.text()) extends Component(node) {
  override def kind: String = "RichText(" + runs().map(_.text).mkString + ")"

  val font: FontFeature = new FontFeature(this)
  lazy val runs: Prop[List[TextRun]] = new Prop[List[TextRun]](Nil,
    r => node.setRuns(r, href => Link.follow(href, newTab = !RichText.inApp(href))), () => measure.trigger())

  whiteSpace @= WhiteSpace.Normal
  color := Theme.text()
  // wrapped to a width that changes, the words take a different number of lines
  size.width.attach(_ => if (size.width.explicit) measure.trigger())
}

object RichText {
  /** Whether `href` is a path on this site rather than somewhere else. */
  def inApp(href: String): Boolean = href.startsWith("/") && !href.startsWith("//")

  /** `runs` wrapped to `width` at `points`, a line `lineHeight` times the size tall (0 for the face's own). */
  def apply(runs: List[TextRun], width: Double, points: Double = 16.0, lineHeight: Double = 0.0): RichText = {
    val text = new RichText
    text.font.size @= points
    text.font.lineHeight @= lineHeight
    text.size.width @= width
    text.runs @= runs
    text
  }
}
