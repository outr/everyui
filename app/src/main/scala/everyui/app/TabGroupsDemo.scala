package everyui.app

import everyui.chat.Participant
import everyui.component.{Component, Container, TextView}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.{TabGroups, TabLayout, TabLead}
import everyui.keymap.Keymap
import everyui.menu.MenuItem
import everyui.render.Platform
import everyui.store.Stored
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** Tabs in groups (`TabGroups`), as an editor splits its files: four files and a conversation with Helper, laid out in
  * three groups split across and down. Each tab shows its name, which of the views made it is, and a counter that has
  * gone on counting since it was made, so a tab moved to another group shows it kept rather than made again. Files may
  * show in two groups at once; the conversation may not, and moves. The layout is kept (`Stored`) and comes back on
  * the next launch; `fresh=1` starts from the default, `layout=<json>` from that, and `probe=1` runs the menu's actions
  * in turn. `theme=light`, `rtl=1`. Logs `tabgroups ok`, and the layout at each change (`tabgroups layout <json>`). */
object TabGroupsDemo {
  private val helper = Participant("agent-1", "Helper")
  private val files = List("README.md", "Main.scala", "build.sbt", "notes.txt")
  private val chat = "Chat with Helper"
  private val views: Var[Int] = Var(0)

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val groups = new TabGroups[String](k => k, view, k => k,
      lead = k => if (k == chat) TabLead.Person(helper) else TabLead.Icon(MaterialIcon.Description),
      allowsTwice = k => k != chat)
    groups.tabMenu @= (k => List(MenuItem("Copy name", () => Platform.log("tabgroups copied " + k))))
    groups.commands(Keymap.global)
    groups.size.width := ui.size.width()
    groups.size.height := ui.size.height()
    ui.children += groups
    val known = (chat :: files).toSet
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
    TabLayout(vertical = true, parts = List(TabLayout(List("build.sbt"), "build.sbt"), TabLayout(List("notes.txt"),
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
}
