package everyui.list

import everyui.component.MaterialIconView
import everyui.component.types.FontWeight
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A row of a `GroupedList`: a group's header (a chevron, its title, how many it holds), or a result under it (the
  * matched line, its matches lit). */
class GroupRow[T](results: GroupedList[T]) extends ListRow[GroupLine[T]] {
  private val header: Val[Boolean] = own(Val(item().exists(_.item.isEmpty)))
  private val chevron = new MaterialIconView(MaterialIcon.ExpandMore, 18.0) {
    icon := (if (item().exists(_.expanded)) MaterialIcon.ExpandMore else MaterialIcon.ChevronRight)
    color := Theme.muted()
    visible := header()
    position.left @= ListRow.inset
    position.top := (GroupRow.this.size.height() - 18.0) / 2.0
  }
  val title: RowLabel = new RowLabel(item().map(_.group.title).getOrElse(""),
    Decoration(icon = item().flatMap(_.group.icon), weight = FontWeight.SemiBold,
      badge = item().map(_.group.items.size.toString).getOrElse("")),
    GroupRow.this.size.width() - ListRow.inset - 22.0, GroupRow.this.size.height())
  val line: MatchText = new MatchText(GroupRow.this.size.width() - GroupRow.indent - 8.0, GroupRow.this.size.height())
  title.position.left @= ListRow.inset + 22.0
  title.visible := header()
  line.position.left @= GroupRow.indent
  line.visible := !header()
  line.text := item().flatMap(_.item).map(results.textOf).getOrElse("")
  line.spans := item().flatMap(_.item).map(results.spansOf).getOrElse(Nil)
  line.lead := item().flatMap(_.item).map(results.leadOf).getOrElse("")
  children ++= List(chevron, title, line)

  override def kind: String = "GroupRow"
}

object GroupRow {
  /** How far a result is indented under its header. */
  val indent: Double = 34.0
}
