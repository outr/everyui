package everyui.app

import everyui.component.{Button, ButtonStyle, Checkbox, Container, TextInput, TextView}
import everyui.component.types.{FontWeight, TextAlign}
import everyui.dialog.Dialogs
import everyui.font.MaterialIcon
import everyui.kit.Toast
import everyui.menu.{Menu, MenuItem}
import everyui.paging.PagedSource
import everyui.render.Platform
import everyui.table.{GridColumn, MenuCell, VirtualTable}
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A table over a made-up server of 1,902,345 entities, a page of 100 at a time, each answer 150 ms away
  * (`EntityServer`): scrolled anywhere, the rows there come as grey bars and fill in; a heading sorts on the server;
  * the search narrows the rows; tick boxes and the heading's box choose rows, every row among them; a status is
  * changed from its cell's menu, confirmed and saved; the view changes the columns; the columns fill the width or keep
  * their own; and the footer counts the results and the choice, or says why a page failed with a way to try again.
  * `theme=light` shows the light palette, `latency=` sets the server's seconds, `flaky=1` fails one request in seven,
  * and `q=` puts words in the search. Logs `pagedtable <rows> rows` as each query's count comes. */
object PagedTableDemo {
  private def grouped(n: Int): String = n.toString.reverse.grouped(3).mkString(",").reverse

  private def statusMenu(source: PagedSource[DemoEntity])(e: DemoEntity): List[MenuItem] =
    EntityServer.statuses.map(s => MenuItem(s, () => if (s != e.status) {
      Dialogs.confirm("Change the status", e.name + " is " + e.status + ". Make it " + s + "?", "Change").map { yes =>
        if (yes) EntityServer.save(e, s).map { saved =>
          source.replace(saved)
          Toast.show(saved.name + " is now " + s)
        }.start()
      }.start()
    }, checked = Some(s == e.status)))

  private def columns(source: PagedSource[DemoEntity], view: String): Vector[GridColumn[DemoEntity]] = {
    val name = GridColumn.served[DemoEntity]("name", "Name", 260.0)(_.name).copy(minWidth = 120.0)
    val status = GridColumn[DemoEntity]("status", "Status", 130.0,
      () => new MenuCell[DemoEntity](_.status, statusMenu(source)), minWidth = 110.0, stretches = false)
    val records = GridColumn.served[DemoEntity]("records", "Records", 100.0, align = TextAlign.End)(e => grouped(e.records))
    val county = GridColumn.served[DemoEntity]("county", "County", 150.0)(_.county)
    val updated = GridColumn.served[DemoEntity]("updated", "Updated", 120.0)(e => EntityServer.date(e.updated))
    val id = GridColumn.served[DemoEntity]("id", "Id", 100.0, sortable = false)(_.id)
    view match {
      case "compact" => Vector(name, status, records)
      case "everything" => Vector(id, name, status, records, county, updated)
      case _ => Vector(name, status, records, county, updated)
    }
  }

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    Platform.launchArg("latency").toDoubleOption.foreach(s => EntityServer.latency = s)
    EntityServer.flaky = Platform.launchArg("flaky") == "1"
    ui.backgroundColor := Theme.background()

    val search = new TextInput("Search", width = 320.0)
    search.value @= Platform.launchArg("q")
    val source = new PagedSource[DemoEntity](_.id, pageSize = 100)(request =>
      EntityServer.fetch(search.value(), request))
    val table = VirtualTable.paged[DemoEntity](source, _.name, columns(source, "standard"))
    table.checks @= true
    table.fills @= true
    table.rowColor @= (e => if (e.managed) Some(Theme.success().withAlpha(0.12)) else None)
    table.borderWidth @= 1.0
    table.borderColor := Theme.border()
    search.value.attach(_ => source.invalidate())
    source.total.attach(_.foreach(n => Platform.log("pagedtable " + n + " rows")))

    val view = Var("standard")
    view.attach(v => table.columns @= columns(source, v))
    lazy val viewButton: Button = Button.bound("View: " + view().capitalize, () => Menu.below(viewButton,
      List("standard", "compact", "everything").map(v => MenuItem.check(v.capitalize, view() == v, _ => view @= v))),
      style = ButtonStyle.Outlined, glyph = Some(MaterialIcon.ViewColumn))
    val fit = new Checkbox("Fit the width", initial = true, compact = true)
    table.fills := fit.checked()
    val title = new TextView {
      content @= "Entities"
      font.size @= 20.0
      font.weight @= FontWeight.SemiBold
      color := Theme.text()
    }

    // the footer: the count and the choice, or what failed and a way to try again
    val summary = TextView.bound({
      val chosen = table.chosenCount
      val found = if (source.searching()) "Searching…"
        else source.total().map(n => if (n == 0) "No results" else grouped(n) + (if (n == 1) " result" else " results"))
          .getOrElse("")
      found + (if (chosen == 0 || source.searching()) ""
        else if (table.selection.all()) " · " + grouped(chosen) + " selected (every row" +
          (if (table.selection.keys().isEmpty) ")" else " but " + table.selection.keys().size + ")")
        else " · " + grouped(chosen) + " selected")
    }, 12.5, Theme.muted())
    val failed = TextView.bound(source.error().getOrElse(""), 12.5, Theme.danger())
    val retry = new Button("Try again", () => source.retry(), style = ButtonStyle.Text)
    val footer = new Container {
      size.height @= 32.0
      backgroundColor := Theme.surface()
      summary.position.left @= 12.0
      summary.position.top := (size.height() - summary.size.height()) / 2.0
      retry.visible := source.error().nonEmpty
      failed.visible := source.error().nonEmpty && size.width() > 520.0
      retry.position.left := size.width() - retry.size.width() - 8.0
      retry.position.top := (size.height() - retry.size.height()) / 2.0
      failed.position.left := retry.position.left() - failed.size.width() - 8.0
      failed.position.top := (size.height() - failed.size.height()) / 2.0
      children ++= List(summary, failed, retry)
    }
    table.footer @= Some(footer)

    // wide, one line of controls over the table; narrow, the search under the title and the rest under it
    val narrow: Val[Boolean] = Val(ui.size.width() < 760.0)
    title.position.left @= 16.0
    title.position.top := (if (narrow()) 16.0 else 16.0 + (search.size.height() - title.size.height()) / 2.0)
    search.size.width := (if (narrow()) ui.size.width() - 32.0 else 320.0)
    search.position.left := (if (narrow()) 16.0 else 140.0)
    search.position.top := (if (narrow()) 52.0 else 16.0)
    viewButton.position.left := (if (narrow()) 16.0 else search.position.left() + search.size.width() + 16.0)
    viewButton.position.top := (if (narrow()) 120.0 else 16.0 + (search.size.height() - viewButton.size.height()) / 2.0)
    fit.position.left := viewButton.position.left() + viewButton.size.width() + 12.0
    fit.position.top := viewButton.position.top() + (viewButton.size.height() - fit.size.height()) / 2.0
    table.position.left @= 16.0
    table.position.top := (if (narrow()) 164.0 else 88.0)
    table.size.width := ui.size.width() - 32.0
    table.size.height := math.max(160.0, ui.size.height() - table.position.top() - 16.0)
    ui.children ++= List(title, search, viewButton, fit, table)
    table.focus()
    if (Platform.launchArg("probe") == "1") probe(source, table)
    // a frame saved where the desktop is asked for one (--freeze= --screenshot=)
    if (Platform.freezeSeconds() >= 0.0) Platform.after(math.max(0.0, Platform.freezeSeconds() - 0.1), () => Platform.frozen())
  }

  // the demo driving itself (`probe=1`): far down, every row chosen but one, a status's menu open; what it saw logged
  private def probe(source: PagedSource[DemoEntity], table: VirtualTable[DemoEntity]): Unit = {
    Platform.after(0.8, () => {
      table.list.scrollTo(950000)
      table.toggleAll()
      Platform.log("pagedtable probe far: placeholder=" + table.list.itemAt(950000).isEmpty + " chosen=" + table.chosenCount)
    })
    Platform.after(1.6, () => {
      table.selection.toggle(table.list.keyAt(950002))
      table.current @= Some(table.list.keyAt(950004))
      Platform.log("pagedtable probe came: " + table.list.itemAt(950000).map(_.name).getOrElse("-") + " chosen=" +
        table.chosenCount + " kept=" + source.kept)
      table.edit()
    })
  }
}
