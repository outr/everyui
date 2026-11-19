package everyui.app

import everyui.component.{Button, MaterialIconView, ScrollContainer, TextView}
import everyui.font.MaterialIcon
import everyui.render.Platform
import everyui.ui

/** An icon picker's "show all" (`MaterialIcon.all`), for a web bundle's icon face shipped in two: the icons the app
  * names show at once, from the face cut to them, and "Show all icons" lays out every icon of the face, the browser
  * fetching the rest of them (`MaterialIcons-Regular.rest.woff2`) only as it first draws one. `all=1` shows them at
  * once. */
object IconsDemo {
  private val cell = 40.0
  private val across = 22

  def run(): Unit = {
    val words = new TextView
    words.content @= "Icons named by the app, and every icon on demand"
    words.position.left @= 24.0
    words.position.top @= 20.0
    val named = List(MaterialIcon.Home, MaterialIcon.Search, MaterialIcon.Settings, MaterialIcon.Folder)
    named.zipWithIndex.foreach { case (icon, i) =>
      ui.children += at(new MaterialIconView(icon), 24.0 + i * cell, 56.0)
    }
    val grid = new ScrollContainer
    grid.position.left @= 24.0
    grid.position.top @= 150.0
    grid.size.width @= across * cell
    grid.size.height @= 480.0
    var shown = false
    val showAll = () => if (!shown) {
      shown = true
      val icons = MaterialIcon.all
      grid.content.size.width @= across * cell
      grid.content.size.height @= math.ceil(icons.size.toDouble / across) * cell
      grid.content.children ++= icons.zipWithIndex.map { case (icon, i) =>
        at(new MaterialIconView(icon), (i % across) * cell + 8.0, (i / across) * cell + 8.0)
      }
      Platform.log("icons shown: " + icons.size)
    }
    val show = new Button("Show all icons", showAll)
    show.position.left @= 24.0
    show.position.top @= 96.0
    ui.children ++= List(words, show, grid)
    if (Platform.launchArg("all") == "1") showAll()
  }

  private def at(view: MaterialIconView, left: Double, top: Double): MaterialIconView = {
    view.position.left @= left
    view.position.top @= top
    view
  }
}
