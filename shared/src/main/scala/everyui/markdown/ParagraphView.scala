package everyui.markdown

import everyui.Color
import everyui.component.{Container, RichText}
import everyui.event.Key
import everyui.layout.VerticalLayout
import everyui.menu.{Menu, MenuItem}
import everyui.theme.Theme
import reactify.*

/** A paragraph or a heading of a `MarkdownView`: its lines wrapped to `width`, a picture on a line of its own. New
  * words for it (`inlines`, as an answer streams in) are drawn in place while its lines and pictures stay as they
  * were. Where it holds links it takes the focus, and Enter (a remote's OK) follows its link, or offers its links in a
  * menu where it has several; a click or a tap on a link follows it where the platform draws links. */
class ParagraphView(content: List[MdInline], ctx: MarkdownContext, width: => Double, points: Double,
                    weight: Int = 400) extends Container {
  /** What it shows. */
  val inlines: Var[List[MdInline]] = own(Var(content))
  private val links: Val[List[(String, String)]] = own(Val(MarkdownRuns.links(inlines())))
  private var shape = ""

  size.width := width
  layout @= Some(VerticalLayout(0.0))
  focusable := links().nonEmpty
  radius @= 4.0
  backgroundColor := (if (focused()) Theme.controlLit() else Color.Clear)
  inlines.attach(_ => build())
  build()

  override def keepsKey(key: Key): Boolean = key == Key.Enter && links().nonEmpty
  event.key.down.attach(k => if (k.key == Key.Enter) openLinks())

  /** Its link followed, or its links offered to choose from. */
  def openLinks(): Unit = {
    val all = links()
    if (all.size == 1) ctx.onLink(all.head._2)
    else if (all.size > 1) Menu.below(this, all.map(l => MenuItem(if (l._1.trim.isEmpty) l._2 else l._1,
      () => ctx.onLink(l._2), detail = l._2)))
  }

  /** Its lines made again where they are no longer the same lines and pictures; otherwise their words follow
    * `inlines` where they stand. */
  private def build(): Unit = {
    val parts = MarkdownRuns.parts(inlines(), points, weight)
    val now = parts.map(p => p.image.map(i => "i" + i.src).getOrElse("t")).mkString("|")
    if (now != shape) {
      shape = now
      val old = children()
      children @= parts.indices.toVector.map(i => parts(i).image match {
        case Some(image) => new MarkdownImage(image, ctx, size.width())
        case None => line(i)
      })
      old.foreach(_.dispose())
    }
  }

  private def line(i: Int): RichText = new RichText {
    size.width := ParagraphView.this.size.width()
    font.size @= points
    color := (if (ctx.quiet) Theme.muted() else Theme.text())
    follow = ctx.onLink
    runs := {
      val parts = MarkdownRuns.parts(inlines(), points, weight)
      if (i < parts.size) parts(i).runs else Nil
    }
  }

  override def kind: String = "ParagraphView"
}
