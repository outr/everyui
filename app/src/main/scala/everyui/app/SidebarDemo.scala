package everyui.app

import everyui.component.{Container, IconButton, TextView}
import everyui.component.types.{FontWeight, WhiteSpace}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.Mirror
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.split.{ResizeHandle, SplitPane, SplitSide}
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A workspace laid out by hand, as Voidcraft lays its own out: a tool rail, a sidebar whose edge drags its width
  * (ResizeHandle, 180 to 480, folded by a double-click), and a header over the main area, which is a split of an
  * editor and an inspector kept at a width in pixels (SplitPane's `length`, 160 to 520, folded by a double-click). The
  * sidebar and the inspector keep their widths as the window changes; the editor takes the change. Each width is shown
  * in its part and logged as it changes (`sidebar width=280 folded=false`, `sidebar inspector=300 folded=false`).
  * `width=` and `inspector=` start them at other widths, `folded=1` with the sidebar folded, `theme=light`, `rtl=1`. */
object SidebarDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    def arg(name: String, otherwise: Double): Double =
      if (Platform.launchArg(name).isEmpty) otherwise else Platform.launchArg(name).toDouble
    // what the app keeps: the sidebar's width and whether it is folded, and the inspector's width
    val width = Var(arg("width", 280.0))
    val folded = Var(Platform.launchArg("folded") == "1")
    val inspector = Var(arg("inspector", 300.0))
    def whole(d: Double): String = math.round(d).toString
    Val("width=" + whole(width()) + " folded=" + folded().toString).attach(s => Platform.log("sidebar " + s))

    def label(words: => String, points: Double, bold: Boolean = false): TextView = new TextView {
      content := words
      font.size @= points
      font.weight @= (if (bold) FontWeight.SemiBold else FontWeight.Normal)
      color := (if (bold) Theme.text() else Theme.muted())
    }
    // (the words as wide as the part, so they start from its right in a right-to-left language, and wrap in it)
    def panel(words: => String, detail: => String): Container = new Container {
      layout @= Some(VerticalLayout(6.0))
      padding @= 16.0
      backgroundColor := Theme.surface()
      val parts = List(label(words, 15.0, bold = true), label(detail, 13.0))
      parts.foreach { t =>
        t.size.width := math.max(0.0, size.width() - 32.0)
        t.whiteSpace @= WhiteSpace.Normal
      }
      children ++= parts
    }

    val rail = new Container {
      layout @= Some(VerticalLayout(4.0))
      padding @= 6.0
      backgroundColor := Theme.raised()
      children ++= List(new IconButton(MaterialIcon.Folder, "Files"), new IconButton(MaterialIcon.Search, "Search"),
        new IconButton(MaterialIcon.Forum, "Conversations"))
    }
    val sidebar = panel("Sidebar", "Width " + whole(width()) + " px, 180 to 480: double-click its edge to fold it")
    val header = new Container {
      backgroundColor := Theme.raised()
      children += label("Header", 15.0, bold = true)
    }
    Mirror.place(header.children().head, () => header.size.width(), () => 16.0)
    header.children().head.position.top @= 14.0
    val editor = panel("Editor", "takes what the window gives or takes")
    val inspect = panel("Inspector", "Width " + whole(inspector()) + " px, 160 to 520")
    val split = new SplitPane(editor, inspect, length = Some(SplitSide.Second -> inspector),
      collapsible = Some(SplitSide.Second))
    split.minSecond @= 160.0
    split.maxSecond @= 520.0
    Val("inspector=" + whole(inspector()) + " folded=" + split.collapsed().toString)
      .attach(s => Platform.log("sidebar " + s))
    val edge = new ResizeHandle(width, min = 180.0, max = 480.0, collapsed = Some(folded))

    // laid out by hand, from the reading direction's start: the rail, the sidebar (none while folded), and the rest
    val railWidth = 48.0
    val shown: Val[Double] = Val(if (folded()) 0.0 else width())
    val all = () => ui.size.width()
    rail.size.width @= railWidth
    rail.size.height := ui.size.height()
    sidebar.size.width := shown()
    sidebar.size.height := ui.size.height()
    sidebar.visible := !folded()
    Mirror.place(rail, all, () => 0.0)
    Mirror.place(sidebar, all, () => railWidth)
    edge.size.height := ui.size.height()
    Mirror.place(edge, all, () => railWidth + shown() - edge.size.width() / 2.0)
    val rest: Val[Double] = Val(math.max(0.0, ui.size.width() - railWidth - shown()))
    header.size.width := rest()
    header.size.height @= 48.0
    Mirror.place(header, all, () => railWidth + shown())
    split.size.width := rest()
    split.size.height := math.max(0.0, ui.size.height() - 48.0)
    split.position.top @= 48.0
    Mirror.place(split, all, () => railWidth + shown())
    ui.children ++= List(rail, sidebar, header, split, edge)
    // how it lies, as the window or a width changes
    Val("window=" + whole(ui.size.width()) + " sidebar=" + whole(sidebar.size.width()) + " editor=" +
      whole(editor.size.width()) + " inspector=" + whole(inspect.size.width()))
      .attach(s => Platform.log("sidebar layout " + s))
    rail.children().head.focus()
    Platform.log("sidebar ok width=" + whole(width()) + " inspector=" + whole(inspector()))
  }
}
