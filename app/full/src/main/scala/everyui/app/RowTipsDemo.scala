package everyui.app

import everyui.component.TextView
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.list.Decoration
import everyui.menu.{Menu, MenuItem}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.tree.{Tree, TreeModel}
import everyui.ui
import reactify.*

/** A project's files in a tree, each file's row with its whole path as its tip (`Decoration.tooltip`) and its folders
  * without one: shown as the pointer rests on a file, as the cursor rests on one, or as a finger is held on one. Some
  * paths are far wider than a tip, to wrap. `n=<files>` for how many (600 by default, so the rows scroll and are
  * handed one file after another), `menu=1` for a context menu on files (a finger held then opens it and no tip),
  * `theme=light`, `rtl=1`. Logs `rowtips ok <files>` once built. */
object RowTipsDemo {
  private val folders: Vector[String] = Vector("backend/src/main/scala/ai/voidcraft/backend/app",
    "backend/src/main/scala/ai/voidcraft/backend/session", "ui/src/main/scala/ai/voidcraft/ui/commit",
    "ui/src/main/scala/ai/voidcraft/ui/sidebar/conversations/blocks/rendering/internal/support", "docs")

  def run(): Unit = {
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val n = Platform.launchArg("n").toIntOption.getOrElse(600)
    val model = new TreeModel[String](p => p)
    val tree = new Tree[String](model, p => p.substring(p.lastIndexOf('/') + 1))
    tree.decorate @= { p =>
      if (folders.contains(p)) Decoration(icon = Some(MaterialIcon.Folder))
      else Decoration(icon = Some(MaterialIcon.Description), badge = p.substring(0, p.lastIndexOf('/')),
        tooltip = Some(p))
    }
    model.setChildren(None, folders)
    folders.indices.foreach { f =>
      val folder = folders(f)
      model.setChildren(Some(folder), (0 until (n + folders.size - 1 - f) / folders.size).toVector.map { i =>
        folder + "/" + (if (i % 7 == 3) "A file whose name runs on well past the width of any tip " else "File") +
          i.toString + ".scala"
      })
    }
    tree.expanded @= folders.toSet
    if (Platform.launchArg("menu") == "1")
      tree.menu.attach(p => if (!folders.contains(p.item)) Menu.at(p.x, p.y, List(MenuItem("Open " + p.item, () => ()))))
    val title = TextView("Files: rest the pointer, the cursor or a finger on one for its path", 13.0, Theme.muted())
    title.position.left @= 16.0
    title.position.top @= 10.0
    tree.position.left @= 16.0
    tree.position.top @= 36.0
    tree.size.width := math.min(420.0, ui.size.width() - 32.0)
    tree.size.height := ui.size.height() - 52.0
    ui.children ++= List(title, tree)
    tree.list.focus()
    Platform.log("rowtips ok " + n.toString)
  }
}
