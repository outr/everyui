package everyui.tree

import everyui.component.MaterialIconView
import everyui.font.MaterialIcon
import everyui.list.{Decoration, ListRow, RowLabel}
import everyui.theme.Theme
import reactify.*

/** A row of a `Tree`: indented by its depth, a chevron where the node opens (an hourglass while its children load), a
  * tick box where it has one (ticked, empty or mixed), and
  * the node's label dressed as the tree's `decorate` says. */
class TreeRow[T](tree: Tree[T]) extends ListRow[TreeLine[T]] {
  private val depth: Val[Int] = own(Val(item().map(_.depth).getOrElse(0)))
  private val start: Val[Double] = own(Val(ListRow.inset + depth() * tree.indent()))
  private val chevron = new MaterialIconView(MaterialIcon.ChevronRight, Tree.chevron) {
    icon := item().fold(MaterialIcon.ChevronRight)(l =>
      if (l.loading) MaterialIcon.HourglassEmpty else if (l.expanded) MaterialIcon.ExpandMore
      else if (everyui.i18n.Direction.rtl()) MaterialIcon.ChevronLeft else MaterialIcon.ChevronRight)
    color := Theme.muted()
    visible := item().exists(_.expandable)
    position.left := ListRow.along(start(), Tree.chevron, TreeRow.this.size.width())
    position.top := (TreeRow.this.size.height() - Tree.chevron) / 2.0
  }
  // the tick box, where the node has one, and where the label starts after it
  private val boxed: Val[Double] = own(Val(if (item().exists(_.check.nonEmpty)) Tree.box + 4.0 else 0.0))
  val box: MaterialIconView = new MaterialIconView(MaterialIcon.CheckBoxOutlineBlank, Tree.box) {
    icon := item().flatMap(_.check).fold(MaterialIcon.CheckBoxOutlineBlank)(c =>
      if (c == CheckState.Checked) MaterialIcon.CheckBox
      else if (c == CheckState.Mixed) MaterialIcon.IndeterminateCheckBox else MaterialIcon.CheckBoxOutlineBlank)
    color := (if (item().flatMap(_.check).exists(_ != CheckState.Unchecked)) Theme.accent() else Theme.muted())
    visible := item().exists(_.check.nonEmpty)
    position.left := ListRow.along(start() + Tree.chevron + 2.0, Tree.box, TreeRow.this.size.width())
    position.top := (TreeRow.this.size.height() - Tree.box) / 2.0
  }
  val label: RowLabel = new RowLabel(
    if (editing()) "" else item().flatMap(_.value).map(tree.labelOf).getOrElse(""),
    item().flatMap(_.value).map(v => tree.decorate()(v)).getOrElse(Decoration.plain),
    TreeRow.this.size.width() - start() - Tree.chevron - 2.0 - boxed(), TreeRow.this.size.height())
  label.position.left := ListRow.along(start() + Tree.chevron + 2.0 + boxed(), label.size.width(), size.width())
  children ++= List(chevron, box, label)
  // the app's words for it, else its own where they are cut short
  tip := item().flatMap(_.value).flatMap(v => tree.decorate()(v).tooltip).orElse(label.cut())

  override def editStart: Double = start() + Tree.chevron + 2.0 + boxed() + label.textStart
  override def kind: String = "TreeRow"
}
