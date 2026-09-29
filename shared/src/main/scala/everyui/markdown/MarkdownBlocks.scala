package everyui.markdown

import everyui.component.{Component, Container}
import everyui.theme.Theme
import reactify.*

/** The component that draws each kind of block (`MarkdownView`), and whether one already drawn can take a block's new
  * content in place. */
private[markdown] object MarkdownBlocks {
  /** `block` drawn `width` wide; `depth` is how deep in lists it is. */
  def make(block: MdBlock, ctx: MarkdownContext, width: => Double, depth: Int = 0): Component = block match {
    case MdBlock.Paragraph(content) => new ParagraphView(content, ctx, width, ctx.style.points)
    case MdBlock.Heading(level, content) =>
      val heading = new ParagraphView(content, ctx, width, ctx.style.heading(level), 700)
      heading.semantics @= everyui.component.types.Semantics.heading(level)
      heading
    case MdBlock.CodeBlock(language, code, _) => new CodeBlockView(language, code, ctx, width)
    case MdBlock.Quote(blocks) => new QuoteView(blocks, ctx, width, depth)
    case list: MdBlock.BulletList => new ListView(list, ctx, width, depth)
    case table: MdBlock.TableBlock => new TableView(table, ctx, width)
    case MdBlock.Rule => new Container {
      size.width := width
      size.height @= 1.0
      backgroundColor := Theme.border()
    }
    case MdBlock.Html(source) =>
      // shown as the text it is, never rendered
      val lines = source.split("\n").toList
      new ParagraphView(lines.flatMap(l => List(MdInline.HardBreak, MdInline.CodeSpan(l))).drop(1), ctx.copy(quiet = true),
        width, ctx.style.points)
    case MdBlock.Definitions(_) => new Container
  }

  /** Whether `view`, drawn for `was`, took `now` in place: a paragraph's or a heading's new words, a code block's new
    * code (in the same language). */
  def update(view: Component, was: MdBlock, now: MdBlock): Boolean = (view, was, now) match {
    case (p: ParagraphView, MdBlock.Paragraph(_), MdBlock.Paragraph(content)) =>
      p.inlines @= content
      true
    case (p: ParagraphView, MdBlock.Heading(a, _), MdBlock.Heading(b, content)) if a == b =>
      p.inlines @= content
      true
    case (c: CodeBlockView, MdBlock.CodeBlock(_, _, _), MdBlock.CodeBlock(language, code, _)) if c.language == language =>
      c.code @= code
      true
    case _ => false
  }
}
