package everyui.app

import everyui.chat.Participant
import everyui.async.Task
import everyui.component.{Component, Container, TextView}
import everyui.dialog.{Dialogs, SaveChoice}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.{GroupContent, TabGroups, TabLayout, TabLead}
import everyui.keymap.Keymap
import everyui.menu.MenuItem
import everyui.render.Platform
import everyui.store.Stored
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** Tabs in groups (`TabGroups`), as an editor splits its files: four files, a terminal and a conversation with Helper,
  * laid out in three groups split across and down. The files share one view per group (`GroupContent`), which says
  * which group view it is and which file it shows; the terminal and the conversation have a view each, which says which
  * view it is. Each counts the seconds since it was made, so one kept shows it rather than starting again. Files may
  * show in two groups at once; the conversation and the terminal may not, and move. Main.scala and notes.txt start
  * with unsaved work (a dot), and closing one asks Save, Don't Save or Cancel; a tab's menu marks a file saved or not. The layout is kept (`Stored`) and comes back on
  * the next launch; `fresh=1` starts from the default, `layout=<json>` from that, and `probe=1` runs the menu's actions
  * in turn. `theme=light`, `rtl=1`. Logs `tabgroups ok`, and the layout at each change (`tabgroups layout <json>`). */
object TabGroupsDemo {
  private val helper = Participant("agent-1", "Helper")
  private val files = List("README.md", "Main.scala", "build.sbt", "notes.txt")
  private val chat = "Chat with Helper"
  private val terminal = "Terminal"
  private val views: Var[Int] = Var(0)
  private val groupViews: Var[Int] = Var(0)

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val groups = new TabGroups[String](k => k, view, k => k,
      lead = k => if (k == chat) TabLead.Person(helper)
        else TabLead.Icon(if (k == terminal) MaterialIcon.Terminal else MaterialIcon.Description),
      allowsTwice = k => files.contains(k),
      sharesGroupContent = k => files.contains(k),
      groupContent = _ => groupView())
    groups.tabMenu @= (k => List(MenuItem("Copy name", () => Platform.log("tabgroups copied " + k))) ++
      (if (!files.contains(k)) Nil
        else if (groups.isDirty(k)) List(MenuItem("Mark saved", () => groups.dirty(k, false)))
        else List(MenuItem("Mark changed", () => groups.dirty(k, true)))))
    groups.canClose @= { (k: String) =>
      if (!groups.isDirty(k)) Task.pure(true)
      else Dialogs.saveChanges("Save changes to " + k + "?", "Your changes will be lost if you don't save them.").map { c =>
        Platform.log("tabgroups close " + k + " " + c.toString)
        if (c != SaveChoice.Cancel) groups.dirty(k, false)
        c != SaveChoice.Cancel
      }
    }
    List("Main.scala", "notes.txt").foreach(k => groups.dirty(k, true))
    groups.commands(Keymap.global)
    groups.size.width := ui.size.width()
    groups.size.height := ui.size.height()
    ui.children += groups
    val known = (chat :: terminal :: files).toSet
    def find(id: String): Option[String] = if (known.contains(id)) Some(id) else None
    groups.restore(TabLayout.parse(Platform.launchArg("layout")).getOrElse(start), find)
    groups.layoutChanged.attach { l =>
      Platform.log("tabgroups layout " + l.json)
      Stored.put("demo", "tabgroups", l)(using TabLayout.codec).startUnit()
    }
    if (Platform.launchArg("fresh") != "1" && Platform.launchArg("layout").isEmpty)
      Stored.get[TabLayout]("demo", "tabgroups")(using TabLayout.codec).map(_.foreach { l =>
        groups.restore(l, find)
        Platform.log("tabgroups restored " + l.json)
      }).startUnit()
    groups.focusedGroup().focusTab()
    // probe=1: the menu's actions run in turn, as a remote would choose them, and the layout read back from its JSON
    if (Platform.launchArg("probe") == "1") Platform.after(1.0, () => {
      val first = groups.groups().head
      def choose(g: everyui.kit.TabGroup[String], k: String, word: String): Unit =
        groups.menuFor(g, k).find(_.label == word).foreach(_.action())
      choose(first, chat, TabGroups.openRightWord)
      choose(first, "README.md", TabGroups.openDownWord)
      choose(groups.groups()(1), "README.md", TabGroups.moveRightWord)
      choose(groups.groups().last, "notes.txt", TabGroups.unsplitWord)
      val kept = groups.tabLayout
      groups.restore(TabLayout.parse(kept.json).getOrElse(TabLayout()), find)
      Platform.log("tabgroups probe " + (if (groups.tabLayout == kept) "same" else "DIFFERENT") + " " +
        groups.groups().map(_.keys().mkString("+")).mkString(" | "))
      if (groups.tabLayout != kept) Platform.log("tabgroups probe read back " + groups.tabLayout.json)
    })
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
    Platform.log("tabgroups ok " + groups.tabLayout.json)
  }

  // README, Main.scala and the conversation; build.sbt beside them; notes.txt under build.sbt
  private val start: TabLayout = TabLayout(ratio = 0.6, parts = List(
    TabLayout(List("README.md", "Main.scala", chat), "Main.scala"),
    TabLayout(vertical = true, parts = List(TabLayout(List("build.sbt"), "build.sbt"), TabLayout(List("notes.txt", terminal),
      "notes.txt")))))

  /** What tab `k` shows: its name, which view of it this is, and how long it has been counting. */
  private def view(k: String): Component = {
    views @= views() + 1
    val made = views()
    val count = Var(0)
    val name = TextView.bound(k, 22.0, Theme.text())
    val which = TextView.bound("View #" + made.toString + " · " + count().toString + " s", 14.0, Theme.muted())
    val box = new Container {
      backgroundColor := Theme.surface()
      children ++= List(name, which)
    }
    // from the start of the reading direction
    name.position.left := (if (Direction.rtl()) box.size.width() - 24.0 - name.size.width() else 24.0)
    name.position.top @= 24.0
    which.position.left := (if (Direction.rtl()) box.size.width() - 24.0 - which.size.width() else 24.0)
    which.position.top @= 60.0
    var gone = false
    box.onDispose(() => {
      gone = true
      Platform.log("tabgroups disposed " + k + " #" + made.toString)
    })
    def tick(): Unit = if (!gone) {
      count @= count() + 1
      Platform.after(1.0, () => tick())
    }
    Platform.after(1.0, () => tick())
    Platform.log("tabgroups made " + k + " #" + made.toString)
    box
  }

  /** A group's one view of its files: which group view it is, the file it shows, and how long it has been counting. */
  private def groupView(): GroupContent[String] = {
    groupViews @= groupViews() + 1
    val made = groupViews()
    val showing = Var("")
    val count = Var(0)
    val name = TextView.bound(showing(), 22.0, Theme.text())
    val which = TextView.bound("Group view #" + made.toString + " showing " + showing() + " · " + count().toString + " s",
      14.0, Theme.muted())
    val view = new GroupContent[String] {
      backgroundColor := Theme.surface()
      children ++= List(name, which)
      override def show(k: String): Unit = {
        showing @= k
        Platform.log("tabgroups group view #" + made.toString + " showing " + k)
      }
      override def hide(): Unit = Platform.log("tabgroups group view #" + made.toString + " hidden")
    }
    name.position.left := (if (Direction.rtl()) view.size.width() - 24.0 - name.size.width() else 24.0)
    name.position.top @= 24.0
    which.position.left := (if (Direction.rtl()) view.size.width() - 24.0 - which.size.width() else 24.0)
    which.position.top @= 60.0
    var gone = false
    view.onDispose(() => {
      gone = true
      Platform.log("tabgroups group view #" + made.toString + " disposed")
    })
    def tick(): Unit = if (!gone) {
      count @= count() + 1
      Platform.after(1.0, () => tick())
    }
    Platform.after(1.0, () => tick())
    Platform.log("tabgroups group view #" + made.toString + " made")
    view
  }
}
