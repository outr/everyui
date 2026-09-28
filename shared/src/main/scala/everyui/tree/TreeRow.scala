package everyui.tree

import everyui.component.MaterialIconView
import everyui.font.MaterialIcon
import everyui.list.{Decoration, ListRow, RowLabel}
import everyui.theme.Theme
import reactify.*

/** A row of a `Tree`: indented by its depth, a chevron where the node opens (an hourglass while its children load), and
  * the node's label dressed as the tree's `decorate` says. */
class TreeRow[T](tree: Tree[T]) extends ListRow[TreeLine[T]] {
  private val depth: Val[Int] = own(Val(item().map(_.depth).getOrElse(0)))
  private val start: Val[Double] = own(Val(ListRow.inset + depth() * tree.indent()))
  private val chevron = new MaterialIconView(MaterialIcon.ChevronRight, Tree.chevron) {
    icon := item().fold(MaterialIcon.ChevronRight)(l =>
      if (l.loading) MaterialIcon.HourglassEmpty else if (l.expanded) MaterialIcon.ExpandMore else MaterialIcon.ChevronRight)
    color := Theme.muted()
    visible := item().exists(_.expandable)
    position.left := start()
    position.top := (TreeRow.this.size.height() - Tree.chevron) / 2.0
  }
  val label: RowLabel = new RowLabel(
    if (editing()) "" else item().flatMap(_.value).map(tree.labelOf).getOrElse(""),
    item().flatMap(_.value).map(v => tree.decorate()(v)).getOrElse(Decoration.plain),
    TreeRow.this.size.width() - start() - Tree.chevron - 2.0, TreeRow.this.size.height())
  label.position.left := start() + Tree.chevron + 2.0
  children ++= List(chevron, label)

  override def editStart: Double = label.position.left() + label.textStart
  override def kind: String = "TreeRow"
}
