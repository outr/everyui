package everyui.component

import everyui.component.feature.FontFeature
import everyui.component.types.{Prop, TextDirection, WhiteSpace}
import everyui.render.Nodes
import everyui.text.TextRun
import everyui.theme.Theme

/** A paragraph of styled runs, wrapped to its width: bold, italic, code, links (see [[TextRun]]). A link to a path in
  * this app is followed in place, any other opens in a new tab. The paragraph's own font and colour are the runs'
  * defaults. */
class RichText(node: everyui.render.RenderNode = Nodes.richText()) extends Component(node) {
  override def kind: String = "RichText(" + runs().map(_.text).mkString + ")"

  val font: FontFeature = new FontFeature(this)
  /** What a click on a link does with its `href`: by default a path in this app is shown in place, and anything else
    * opened (`Link.follow`). */
  var follow: String => Unit = href => Link.follow(href, newTab = !RichText.inApp(href))
  lazy val runs: Prop[List[TextRun]] = own(new Prop[List[TextRun]](Nil,
    r => node.setRuns(r, href => follow(href)), () => measure.trigger()))

  /** A shadow behind the letters, as [[TextView.textShadow]] has it: a subtitle's edge over a picture. */
  lazy val textShadow: Prop[Option[everyui.Shadow]] = own(new Prop[Option[everyui.Shadow]](None, s => s match {
    case Some(sh) => node.setTextShadow(sh.x, sh.y, sh.blur, Component.css(sh.color))
    case None => node.setTextShadow(0.0, 0.0, 0.0, "")
  }))
  /** Where each line sits in the box, as [[TextView.align]] has it: it matters once the lines differ in width. */
  lazy val align: Prop[everyui.component.types.TextAlign] = own(new Prop[everyui.component.types.TextAlign](
    everyui.component.types.TextAlign.Start, a => node.setTextAlign(TextView.side(a))))

  /** Which way the words run: the app's reading direction by default (`TextDirection.Inherit`), or the words' own
    * (`Auto`, the first letter with a direction deciding) for words the app did not write, a message or a name, so an
    * English one reads left to right in an Arabic app. The start of `align` is the words' start. */
  lazy val direction: Prop[TextDirection] =
    own(new Prop[TextDirection](TextDirection.Inherit, d => node.setTextDirection(d.value)))
  /** Whether a word too long for a line (a URL, a path, a hash) is broken where it must be, rather than running past
    * the box's edge. It matters for wrapped words alone. */
  lazy val wrapAnywhere: Prop[Boolean] =
    own(new Prop[Boolean](false, on => node.setWrapAnywhere(on), () => measure.trigger()))

  whiteSpace @= WhiteSpace.Normal
  color := Theme.text()
  // measured again once a face its words wait for has loaded: code drawn in a fallback face first was narrower, and
  // the paragraph took fewer lines than it does in its own
  private val faceLoaded: () => Unit = () => if (!takenApart) measure.trigger()
  override private[everyui] def beforeRead(): Unit = node.textMeasurable(faceLoaded)
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
