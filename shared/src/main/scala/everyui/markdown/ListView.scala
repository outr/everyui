package everyui.markdown

import everyui.component.{Component, Container, MaterialIconView, TextView}
import everyui.component.types.Semantics
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.layout.VerticalLayout
import everyui.theme.Theme
import reactify.*

/** A list of a `MarkdownView`: each item's blocks stood in by the style's indent, after its marker: a bullet (a
  * different one a level down), its number, or a task's box, ticked or not. A tight list's items sit closer. */
class ListView(list: MdBlock.BulletList, ctx: MarkdownContext, width: => Double, depth: Int) extends Container {
  private val style = ctx.style
  private val gap = if (list.tight) style.spacing * 0.3 else style.spacing * 0.7

  size.width := width
  layout @= Some(VerticalLayout(gap))
  semantics @= Semantics.list
  children ++= list.items.indices.map(i => item(list.items(i), i))

  private def item(it: MdItem, i: Int): Container = {
    val row = new Container
    val body = new Container {
      size.width := row.size.width() - style.indent
      layout @= Some(VerticalLayout(if (list.tight) style.spacing * 0.3 else style.spacing))
    }
    body.children ++= it.blocks.map(b => MarkdownBlocks.make(b, ctx, body.size.width(), depth + 1))
    val mark = marker(it, i)
    row.size.width := size.width()
    row.semantics @= Semantics.listItem
    body.position.left := (if (Direction.rtl()) 0.0 else style.indent)
    row.size.height := math.max(body.size.height(), mark.size.height())
    row.children ++= List(mark, body)
    row
  }

  /** The item's marker, placed in the indent: a number up against the words, a bullet or a box further out. */
  private def marker(it: MdItem, i: Int): Component = it.task match {
    case Some(done) =>
      val box = new MaterialIconView(if (done) MaterialIcon.CheckBox else MaterialIcon.CheckBoxOutlineBlank,
        style.points * 1.15)
      box.color := (if (done) Theme.accent() else Theme.muted())
      box.semantics @= Semantics("checkbox", label = if (done) "done" else "not done")
      box.position.top @= style.points * 0.14
      place(box, style.indent - box.size.width() - 5.0)
      box
    case None =>
      val words =
        if (list.ordered) (list.start + i).toString + "."
        else if (depth % 3 == 0) "\u2022" else if (depth % 3 == 1) "\u25e6" else "\u25aa"
      val mark = TextView(words, style.points, if (ctx.quiet) Theme.muted() else Theme.text())
      if (list.ordered) place(mark, style.indent - 6.0 - mark.size.width())
      else place(mark, style.indent * 0.3)
      mark
  }

  /** `c` at `left` in the indent, from the right in a right-to-left language. */
  private def place(c: Component, left: => Double): Unit =
    c.position.left := (if (Direction.rtl()) size.width() - left - c.size.width() else left)

  override def kind: String = "ListView"
}
