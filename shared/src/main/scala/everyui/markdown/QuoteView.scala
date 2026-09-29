package everyui.markdown

import everyui.component.Container
import everyui.layout.VerticalLayout
import reactify.*

/** A block quote of a `MarkdownView`: its blocks, quieter, beside a bar in the quote colour, on the side its words
  * start from. */
class QuoteView(blocks: List[MdBlock], ctx: MarkdownContext, width: => Double, depth: Int) extends Container {
  private val gap = 14.0
  private val bar = new Container {
    size.width @= 3.0
    radius @= 1.5
    backgroundColor := MarkdownView.quoteBar()
  }
  private val body = new Container {
    size.width := QuoteView.this.size.width() - gap
    layout @= Some(VerticalLayout(ctx.style.spacing))
  }
  size.width := width
  body.children ++= blocks.map(b => MarkdownBlocks.make(b, ctx.copy(quiet = true), body.size.width(), depth))
  body.position.left := (if (rtl) 0.0 else gap)
  bar.position.left := (if (rtl) size.width() - bar.size.width() else 0.0)
  bar.size.height := body.size.height()
  size.height := body.size.height()
  children ++= List(bar, body)

  /** How wide it would be drawn on its own (see `MarkdownView.natural`): its blocks' widest, past the bar. */
  val natural: Val[Option[Double]] = own(Val(MarkdownBlocks.widest(body.children().map(MarkdownBlocks.natural)).map(_ + gap)))

  // whether its words read right to left
  private def rtl: Boolean = ctx.rightToLeft(blocks)

  override def kind: String = "QuoteView"
}
