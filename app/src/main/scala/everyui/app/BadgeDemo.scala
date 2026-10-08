package everyui.app

import everyui.component.types.Elide
import everyui.component.{Column, Component, TextView}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.list.{Decoration, RowLabel, TextRow, VirtualList}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** Rows with long badges, as a commit window lists changed files: each a file's name with its folder as the badge and
  * its counts as the badge's tail, never cut. The same files in rows 280 and 400 wide, where the folders are cut from
  * their start ("…/backend/app  +1 −1") to fit between the names and the counts, as wide as the screen leaves (up to
  * 900), where most fit whole, and 400 wide cut in the middle and at the end. `demo=badge` shows them in lists, on every
  * backend; `demo=treebadge` in trees, where a tree runs (app/full). `rtl=1` for right to left. Logs `badge ok` once
  * built, and each row's name, badge and tail as drawn:
  *
  *   [ui] badge 400 [AppConnection.scala] […/voidcraft/backend/app] [+39 −7] …
  */
object BadgeDemo {
  val changes: Vector[BadgeChange] = Vector(
    BadgeChange("backend/src/main/scala/ai/voidcraft/backend/app/AppConnection.scala", 39, 7),
    BadgeChange("backend/src/main/scala/ai/voidcraft/backend/session/SessionStore.scala", 112, 64),
    BadgeChange("ui/src/main/scala/ai/voidcraft/ui/commit/CommitWindow.scala", 8, 2),
    BadgeChange("build.sbt", 1, 1),
    BadgeChange("docs/A rather long file name that will not fit in a narrow window.md", 3, 0),
    BadgeChange("ui/M.scala", 2, 2))

  def decoration(c: BadgeChange, side: Elide): Decoration = Decoration(icon = Some(MaterialIcon.Description),
    badge = c.folder, badgeElide = side, badgeTail = "+" + c.added.toString + " −" + c.removed.toString,
    tooltip = Some(c.path))

  def run(): Unit = show { (wide, side) =>
    val list = VirtualList.text(Var(changes), (c: BadgeChange) => c.path, (c: BadgeChange) => c.name,
      (c: BadgeChange) => decoration(c, side))
    list.size.width := wide()
    list.size.height @= list.rowHeight * changes.size
    (list, i => list.rowAt(i).map(_.asInstanceOf[TextRow[BadgeChange]].label))
  }

  /** The four, each made by `make` at a width and an elision, with its rows' labels by index. */
  def show(make: (() => Double, Elide) => (Component, Int => Option[RowLabel])): Unit = {
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    // a bundled face, measured the same on every backend (a Roku's own face is measured only on the device)
    Theme.family @= "Open Sans"
    ui.backgroundColor := Theme.background()
    val made = List(make(() => 280.0, Elide.Start), make(() => 400.0, Elide.Start), make(() => math.min(900.0, ui.size.width() - 32.0), Elide.Start),
      make(() => 400.0, Elide.Middle), make(() => 400.0, Elide.End))
    val titles = List("280, from the start", "400, from the start", "As wide as the screen", "400, in the middle", "400, at the end")
    val column = Column(spacing = 8.0)(titles.zip(made).flatMap((t, m) => List(TextView(t, 12.0, Theme.muted()), m._1))*)
    column.position.left @= 16.0
    column.position.top @= 8.0
    ui.children += column
    Platform.log("badge ok")
    Platform.after(2.0, () => made.foreach { m =>
      Platform.log("badge " + m._1.size.width().round.toString + " " + changes.indices.flatMap(i => m._2(i)).map { l =>
        "[" + l.label.node.getText() + "] [" + (if (l.badge.visible()) l.badge.node.getText() else "") + "] [" +
          (if (l.tail.visible()) l.tail.node.getText() else "") + "]"
      }.mkString(" "))
    })
  }
}
