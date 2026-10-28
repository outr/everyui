package everyui.app

import everyui.component.{Button, ButtonStyle, Chip, Column, ScrollContainer, TextView}
import everyui.component.types.TextOverflow
import everyui.dialog.{DialogAction, DialogPanel}
import everyui.kit.{TabBar, Toast}
import everyui.list.{RichList, RichLook, RichSection, RichStyle}
import everyui.menu.{Menu, MenuItem}
import everyui.render.Platform
import everyui.table.{DataColumn, DataTable}
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** Words that do not fit, in each kind of component, for checking they wrap by default (Matt's ruling of 2026-10-27)
  * and where a line cannot grow, cut with the whole of it in a tip: a heading, buttons (one asking for a single line),
  * a chip, a tab bar, a list's rows in a tree and in blocks, a small table, and buttons that open a menu, a toast and a
  * dialog of long words. `ellipsis=1` sets the app's default to the ellipsis instead, as an app that wants it would. */
object WrapDemo {
  private val long = "A title long enough that it cannot possibly fit on one line of a phone's narrow screen"

  def run(): Unit = {
    if (Platform.launchArg("ellipsis") == "1") TextOverflow.default @= TextOverflow.Ellipsis
    ui.backgroundColor := Theme.background()
    val width = Val(math.max(200.0, ui.size.width() - 32.0))
    val heading = new TextView {
      content @= long
      font.size @= 20.0
      color := Theme.text()
    }
    val wraps = new Button("Save these settings and go back to the conversation you were in before", () => ())
    val oneLine = new Button("Save these settings and go back to the conversation you were in before", () => (),
      ButtonStyle.Outlined)
    oneLine.labelLines @= 1
    val chip = new Chip("a chip whose word runs on and on past where it should", Theme.accent())
    val tabs = TabBar(List("a" -> "Overview of the whole thing", "b" -> "Cast and crew in full", "c" -> "More like this"),
      Var("a"), width())
    def look(s: String): RichLook = RichLook(s, Some("and a detail under it that says rather more than one line can hold"))
    def list(style: RichStyle): RichList[String] = {
      val l = new RichList[String](Var(Vector(RichSection("s", "A section whose name is long too, longer than the row",
        Vector(long, "Short one", "Another title that wraps onto a second line at least")))), s => s, look)
      l.style @= style
      l.size.width := width()
      l.size.height @= 360.0
      l
    }
    val table = DataTable(Vector(DataColumn("Name"), DataColumn("What it says")),
      Vector(Vector("A name", long), Vector("Another", "short")))
    table.size.width := width()
    lazy val menu: Button = new Button("Open a menu of long items", () => Menu.below(menu, List(
      MenuItem("An item whose label is far too long for any menu on a phone", () => ()),
      MenuItem("Short", () => (), detail = "with a detail under it that also runs on past the edge"),
      MenuItem("Delete everything you made today and yesterday too", () => (), danger = true))))
    val toast = new Button("Show a toast", () =>
      Toast.show("A message long enough to need a second line, and a third, on a phone held upright", seconds = 8.0))
    val dialog = new Button("Open a dialog", () => {
      lazy val panel: DialogPanel = new DialogPanel("A dialog's title that is long enough to wrap onto two lines",
        List(TextView.wrapped(long, 300.0)), List(DialogAction("Keep what I wrote and close", () => panel.close()),
          DialogAction("Throw it all away and start again", () => panel.close(), default = true, danger = true)))
      panel.open()
    })
    val page = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    val column = Column(spacing = 16.0, padding = 16.0)(heading, wraps, oneLine, chip, tabs, list(RichStyle.Tree),
      list(RichStyle.Blocks), table, menu, toast, dialog)
    heading.maxWidth := width()
    page.content.children += column
    ui.children += page
  }
}
