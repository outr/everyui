package everyui.markdown

import everyui.component.{Component, Container}
import everyui.render.Platform
import everyui.table.{DataCell, DataColumn, DataTable, DataTableStyle}
import everyui.text.TextRun
import reactify.*

/** A Markdown table that sorts, filters, copies and exports (a `DataTable`, where the platform has one:
  * `everyui.render.TableAccess`), made only once it is wanted: at rest it is the table as it is drawn (`TableView`),
  * which a `DataTable` lays out alike, so a chat of answers with tables opens as fast as it did; the pointer coming
  * over it (unless it is selecting words), or the focus, makes the `DataTable` in its place. One whose filter shows at
  * rest (past `filterAfter` rows), or on a touch screen, whose tools always show, is a `DataTable` at once. Either way it is
  * as tall as its rows: the wheel turned down over it scrolls what it is in. */
private[everyui] class MarkdownTable(table: MdBlock.TableBlock, ctx: MarkdownContext, width: => Double) extends Container {
  private var block = table
  private val view: Var[Component] = own(Var(new Container))
  /** The table that sorts and filters, once it is made. */
  var rich: Option[DataTable] = None

  size.width := width
  size.height := view().size.height()
  if (table.rows.size > MarkdownTable.filterAfter || MarkdownTable.touch) engage()
  else {
    show(new TableView(table, ctx, width))
    focusable @= true
    event.pointer.enter.attach(_ => wanted())
    event.pointer.move.attach(_ => wanted())
    focused.attach(on => if (on) engage())
  }

  /** The `DataTable` made in place of the plain table, if it is not yet: the focus on this goes to it. */
  def engage(): Unit = if (rich.isEmpty) {
    val made = new DataTable(MarkdownTable.columns(block, ctx), MarkdownTable.cells(block, ctx),
      DataTableStyle(MarkdownTable.points(ctx), ctx.tableLook(), ctx.quiet, ctx.direction), width)
    // as tall as its rows: a table in a page or a message never scrolls up and down within it, only sideways
    made.maxHeight @= 0.0
    made.onLink = href => ctx.press(href, made)
    made.hovered := event.pointer.over()
    rich = Some(made)
    focusable @= false
    show(made)
    if (focused()) made.focus()
  }

  /** `next`'s rows in place of its own: a `DataTable`'s that did not change keep what draws them; the plain table is
    * drawn again. */
  def take(next: MdBlock.TableBlock): Unit = {
    block = next
    rich match {
      case Some(t) => t.cells @= MarkdownTable.cells(next, ctx)
      case None => show(new TableView(next, ctx, width))
    }
  }

  // the pointer over it, and not selecting words across it: what it shows is to be acted on
  private def wanted(): Unit = if (rich.isEmpty && (!Platform.selectsText() || Platform.selectedText().isEmpty)) engage()

  private def show(next: Component): Unit = {
    val was = view()
    view @= next
    children @= Vector(next)
    was.dispose()
  }

  override def kind: String = "MarkdownTable"
}

private[everyui] object MarkdownTable {
  /** How many rows a table has before its filter shows at rest, and it is a `DataTable` from the start. */
  val filterAfter: Int = 8
  // whether the platform is touched rather than pointed at: the tools always show there
  private def touch: Boolean = Platform.touchSupport() && !Platform.pointerSupport()

  def apply(table: MdBlock.TableBlock, ctx: MarkdownContext, width: => Double): Component =
    new MarkdownTable(table, ctx, width)

  /** Whether `view` is a table that took `table`'s rows in place. */
  def update(view: Component, table: MdBlock.TableBlock): Boolean = view match {
    case t: MarkdownTable =>
      t.take(table)
      true
    case _ => false
  }

  /** The size of a table's words: a little under the paragraphs'. */
  def points(ctx: MarkdownContext): Double = ctx.style.points * 0.94

  def columns(table: MdBlock.TableBlock, ctx: MarkdownContext): Vector[DataColumn] =
    table.align.indices.toVector.map { c =>
      val content = table.header.lift(c).getOrElse(Nil)
      DataColumn(MdInline.plain(content), align = Some(table.align(c)),
        runs = runs(content, ctx, ctx.tableLook().headerWeight))
    }

  def cells(table: MdBlock.TableBlock, ctx: MarkdownContext): Vector[Vector[DataCell]] =
    table.rows.toVector.map(_.toVector.map(content => DataCell(MdInline.plain(content), runs(content, ctx, 400))))

  private def runs(content: List[MdInline], ctx: MarkdownContext, weight: Int): List[TextRun] =
    MarkdownRuns.parts(content, points(ctx), weight, ctx.breaks, ctx.mentions(), ctx.pressesMentions())
      .flatMap(_.runs)
}
