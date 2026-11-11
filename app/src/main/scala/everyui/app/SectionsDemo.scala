package everyui.app

import everyui.Color
import everyui.component.{Backdrop, Checkbox, Column, Component, Container, IconButton, MaterialIconView, Row,
  SectionCard, TextView}
import everyui.component.types.FontWeight
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.Spinner
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A day's groups of tasks as section cards (`SectionCard`): first "Background work · 2 running", a spinner before
  * its title, Clear at its end, only as wide as its header while closed (`work=0` starts it closed) and a
  * "Finished · 3" sub-section in it with a button of its own; under "Today", "Tasks · 3 due" open with three tasks, a
  * tick and a title each and a coloured line under it, "Errands · 2 due" open, and "Done · 4" closed, then "Tomorrow"
  * with a card with no header; each header opens and closes its card. `backdrop=<picture>` draws the picture behind
  * them with the surfaces at `see` (0.8 unless given, `Theme.surfaceOpacity`); `cards=1` draws each header and each
  * task as a card of its own, a gap between them, as an app drew them before, to set beside it; `theme=light`,
  * `rtl=1`. Logs `sections ok` once built, and `sections open <title> <open>` as a card opens or closes. */
object SectionsDemo {
  private case class Task(title: String, detail: String, done: Boolean = false)

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val picture = Platform.launchArg("backdrop")
    if (picture.nonEmpty) {
      val see = Platform.launchArg("see")
      Theme.surfaceOpacity @= (if (see.nonEmpty) see.toDouble else 0.8)
      val back = new Backdrop
      back.size.width := ui.size.width()
      back.size.height := ui.size.height()
      back.source @= picture
      ui.children += back
    }
    val cards = Platform.launchArg("cards") == "1"
    val width = 420.0
    def heading(text: String): TextView = {
      val t = TextView(text, 13.0, Theme.text())
      t.font.weight @= FontWeight.SemiBold
      t
    }
    def row(task: Task): Container = {
      val box = new Checkbox("", task.done, compact = true)
      val name = TextView(task.title, 15.0, if (task.done) Theme.muted() else Theme.text())
      name.font.weight @= FontWeight.SemiBold
      name.maxWidth @= width - 70.0
      val when = TextView(task.detail, 11.0, Theme.successText())
      val r = Row(8.0, middle = true, padding = 10.0)(box, Column(2.0)(name, when))
      r.size.width @= width
      if (cards) {
        r.radius @= 10.0
        r.borderWidth @= 1.0
        r.borderColor @= Theme.success().withAlpha(0.5)
      }
      r
    }
    def group(title: String, tasks: Vector[Task], open: Boolean): Component = {
      val opened = Var(open)
      opened.attach(o => Platform.log("sections open " + title + " " + o.toString))
      if (cards) {
        // each a card of its own: the header a fourth card beside its tasks
        val chevron = new MaterialIconView(MaterialIcon.ExpandMore, 20.0)
        chevron.icon @= (if (open) MaterialIcon.ExpandMore else MaterialIcon.ChevronRight)
        chevron.color := Theme.muted()
        val head = Row(8.0, middle = true, padding = 8.0)(chevron, heading(title))
        head.size.width @= width
        head.radius @= 10.0
        head.borderWidth @= 1.0
        head.borderColor @= Theme.success().withAlpha(0.5)
        head.backgroundColor @= Theme.success().withAlpha(0.08)
        Column(4.0)((head +: (if (open) tasks.map(row) else Vector.empty))*)
      } else {
        val card = new SectionCard(opened)
        card.title @= title
        card.size.width @= width
        card.borderColor @= Theme.success().withAlpha(0.5)
        card.rows @= tasks.map(row)
        card
      }
    }
    val today = Vector(Task("Partner programme application", "Task · Mobile · To do"),
      Task("Planking", "Task · All day"), Task("Security audit", "Task · Platform · To do"))
    val errands = Vector(Task("Pick up the parcel", "Errand · 5:30 PM"),
      Task("Return the library books", "Errand · To do"))
    val done = Vector(Task("Water the plants", "Task · Done", done = true), Task("Stretch", "Task · Done", done = true),
      Task("Reply to the landlord", "Task · Done", done = true), Task("Book the dentist", "Task · Done", done = true))
    val later = Vector(row(Task("Release notes", "Task · Docs · To do")), row(Task("Inbox zero", "Task · All day")))
    // a card with no header: its rows always shown
    val tomorrow: Component = if (cards) Column(4.0)(later*) else {
      val c = new SectionCard()
      c.size.width @= width
      c.borderColor @= Theme.success().withAlpha(0.5)
      c.rows @= later
      c
    }
    // the work running in the background, as a card over a composer: a spinner before its title, Clear at its end
    // taking the finished away, only as wide as its header while closed (compact), and what is finished a
    // sub-section of its own, with a button to open where it went
    def work(): Component = {
      val running = Vector(Task("Indexing the mailbox", "Work · 42%"), Task("Syncing the calendar", "Work · Waiting"))
      val finished = Vector(Task("Exported the report", "Work · Done", done = true),
        Task("Sent three invites", "Work · Done", done = true),
        Task("Backed up the photos", "Work · Done", done = true))
      val opened = Var(Platform.launchArg("work") != "0")
      opened.attach(o => Platform.log("sections open Background work " + o.toString))
      val card = new SectionCard(opened)
      card.title @= "Background work · 2 running"
      card.compact @= true
      card.openWidth @= width
      card.leading @= Some(new Spinner(18.0, () => Theme.accent()))
      val ended = SectionCard.sub()
      ended.title @= "Finished · 3"
      ended.trailing @= Vector(new IconButton(MaterialIcon.FolderOpen, "Open the folder",
        () => Platform.log("sections work folder")))
      ended.rows @= finished.map(row)
      card.rows @= running.map(row) :+ ended
      card.trailing @= Vector(new IconButton(MaterialIcon.ClearAll, "Clear finished", () => {
        Platform.log("sections work cleared")
        card.rows @= card.rows().filter(r => !(r eq ended))
      }))
      card
    }
    val page = Column(10.0, padding = 24.0)(work(),heading("Today"), group("Tasks · 3 due", today, open = true),
      group("Errands · 2 due", errands, open = true), group("Done · 4", done, open = false), heading("Tomorrow"),
      tomorrow)
    page.backgroundColor @= Color.Clear
    ui.children += page
    Platform.log("sections ok")
  }
}
