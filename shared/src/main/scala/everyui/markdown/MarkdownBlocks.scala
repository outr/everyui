package everyui.markdown

import everyui.component.{Component, Container}
import everyui.theme.Theme
import reactify.*

/** The component that draws each kind of block (`MarkdownView`), and whether one already drawn can take a block's new
  * content in place. */
private[markdown] object MarkdownBlocks {
  /** `block` drawn `width` wide; `depth` is how deep in lists it is. */
  def make(block: MdBlock, ctx: MarkdownContext, width: => Double, depth: Int = 0): Component = {
    val view = drawn(block, ctx, width, depth)
    view.copyAs @= copyAs(block)
    view
  }

  /** What `block` is when words selected across it are copied (`Component.copyAs`). */
  def copyAs(block: MdBlock): String = block match {
    case MdBlock.Paragraph(_) => "p"
    case MdBlock.Heading(level, _) => "h" + math.max(1, math.min(6, level)).toString
    case MdBlock.CodeBlock(_, _, _) | MdBlock.Html(_) => "pre"
    case MdBlock.Quote(_) => "blockquote"
    case MdBlock.BulletList(ordered, start, _, _) =>
      if (!ordered) "ul" else if (start == 1) "ol" else "ol " + start.toString
    case MdBlock.Rule => "hr"
    case MdBlock.TableBlock(_, _, _) => ""
    case MdBlock.Definitions(_) => "none"
  }

  private def drawn(block: MdBlock, ctx: MarkdownContext, width: => Double, depth: Int): Component = block match {
    case MdBlock.Paragraph(content) => new ParagraphView(content, ctx, width, ctx.style.points)
    case MdBlock.Heading(level, content) =>
      val heading = new ParagraphView(content, ctx, width, ctx.style.heading(level), 700)
      heading.semantics @= everyui.component.types.Semantics.heading(level)
      heading
    case MdBlock.CodeBlock(language, code, _) => new CodeBlockView(language, code, ctx, width)
    case MdBlock.Quote(blocks) => new QuoteView(blocks, ctx, width, depth)
    case list: MdBlock.BulletList => new ListView(list, ctx, width, depth)
    case table: MdBlock.TableBlock =>
      if (ctx.richTables()) everyui.render.TableAccess.markdown(table, ctx, width) else new TableView(table, ctx, width)
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

  /** How wide `view` would be drawn on its own, read where it can change (see `MarkdownView.natural`): a paragraph, a
    * list or a quote by their words, a block that shows nothing as nothing, and anything else (code, a table, a rule)
    * none, as it takes all the width it is given. */
  def natural(view: Component): Option[Double] = view match {
    case p: ParagraphView => p.natural()
    case l: ListView => l.natural()
    case q: QuoteView => q.natural()
    case c: Container if c.children().isEmpty && c.size.height() <= 0.0 => Some(0.0)
    case _ => None
  }

  /** The widest of `widths`, or none where any of them takes all the width there is. */
  def widest(widths: Seq[Option[Double]]): Option[Double] =
    if (widths.exists(_.isEmpty)) None else Some(widths.foldLeft(0.0)((w, o) => math.max(w, o.getOrElse(0.0))))

  /** The words of `blocks`, as plain text, for which way they read. */
  def words(blocks: List[MdBlock]): String = blocks.map {
    case MdBlock.Paragraph(content) => MdInline.plain(content)
    case MdBlock.Heading(_, content) => MdInline.plain(content)
    case MdBlock.Quote(inside) => words(inside)
    case MdBlock.BulletList(_, _, _, items) => items.map(i => words(i.blocks)).mkString(" ")
    case MdBlock.TableBlock(_, header, _) => header.map(MdInline.plain).mkString(" ")
    case _ => ""
  }.mkString(" ")

  /** Whether `view`, drawn for `was`, took `now` in place: a paragraph's or a heading's new words, a code block's new
    * code (in the same language), a table's new rows (under the same header, where the table sorts and filters). */
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
    case (t, MdBlock.TableBlock(align, header, _), next: MdBlock.TableBlock)
      if align == next.align && header == next.header =>
      everyui.render.TableAccess.update(t, next)
    case _ => false
  }
}
