package everyui.markdown

import everyui.component.Container
import everyui.layout.VerticalLayout
import reactify.*

/** A block quote of a `MarkdownView`: its blocks, quieter, beside a bar in the quote colour. */
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
  body.position.left @= gap
  body.children ++= blocks.map(b => MarkdownBlocks.make(b, ctx.copy(quiet = true), body.size.width(), depth))
  bar.size.height := body.size.height()
  size.height := body.size.height()
  children ++= List(bar, body)

  override def kind: String = "QuoteView"
}
