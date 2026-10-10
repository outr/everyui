package everyui.app

import everyui.chat.{Avatar, Participant}
import everyui.component.{Button, TextView}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.keymap.{Chord, Keymap}
import everyui.kit.{TabBar, TabLead}
import everyui.menu.MenuItem
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** Tabs led by an avatar or an icon (`TabBar.add(…, lead)`), and the ring that turns around somebody at work: an
  * Overview tab with no lead, Release plan led by Helper's avatar, busy and "Running bash", Ada's led by hers and idle,
  * and Main.scala led by a file's icon; and Helper's face large under them, busy and "Thinking". A button starts and
  * stops them both. `add=1` puts a "+" after the last tab (`TabBar.addMenu`) whose New tab adds one at the end;
  * `busy=0` starts them idle, `theme=light` for the light palette, `rtl=1` to lay it out right to left. N renames Helper
  * to Robot Analyst, then gives Helper a portrait, then neither (`TabItem.lead`); K leads Ada's tab by an icon, then
  * nothing, then her face; `relead=1` does both each second, four times. Logs `tabs ok` once built, `tabs busy <on>` at
  * each change, `tabs added <key>` and `tabs lead <key> <what>`. */
object TabsDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val helper = Participant("agent-1", "Helper")
    val ada = Participant("p-ada", "Ada Lovelace")
    val busy = Var(Platform.launchArg("busy") != "0")

    val shown = Var("plan")
    val bar = new TabBar(shown, ui.size.width(), showOnFocus = false)
    bar.add("overview", "Overview")
    val plan = bar.add("plan", "Release plan", closable = true, lead = TabLead.Person(helper))
    plan.busy := busy()
    plan.busyDetail @= "Running bash"
    val adaTab = bar.add("ada", "Ada Lovelace", closable = true, lead = TabLead.Person(ada))
    bar.add("main", "Main.scala", closable = true, lead = TabLead.Icon(MaterialIcon.Description))
    bar.closing.attach(k => bar.remove(k))
    // N renames Helper, as a persona renamed, then gives them a portrait, then neither; K leads Ada's tab by an
    // icon, then nothing, then her again
    def rename(): Unit = {
      val was = plan.lead() match {
        case TabLead.Person(p) => p
        case _ => helper
      }
      val next = if (was.avatar.nonEmpty) helper else if (was.name == "Helper") helper.copy(name = "Robot Analyst")
        else helper.copy(avatar = Some("/images/icon.png"))
      plan.lead @= TabLead.Person(next)
      Platform.log("tabs lead plan " + next.name + (if (next.avatar.nonEmpty) " portrait" else ""))
    }
    def relead(): Unit = {
      val said = adaTab.lead() match {
        case TabLead.Person(_) => "icon"
        case TabLead.Icon(_) => "none"
        case _ => "person"
      }
      adaTab.lead @= (if (said == "icon") TabLead.Icon(MaterialIcon.Terminal) else if (said == "none") TabLead.None
        else TabLead.Person(ada))
      Platform.log("tabs lead ada " + said)
    }
    Keymap.global.add("tabs.rename", "Rename Helper", Chord("n"))(rename())
    Keymap.global.add("tabs.lead", "Change Ada's lead", Chord("k"))(relead())
    // relead=1: both, each second, where no key can be pressed (a channel in brs-engine)
    if (Platform.launchArg("relead") == "1") (1 to 4).foreach(i => Platform.after(i.toDouble, () => {
      rename()
      relead()
    }))
    // add=1: a "+" after the last tab, its menu opening a new tab at the end and showing it
    if (Platform.launchArg("add") == "1") {
      val added = Var(0)
      bar.addMenu @= Some(() => List(MenuItem("New tab", () => {
        added @= added() + 1
        val k = "new-" + added().toString
        bar.add(k, "New tab " + added().toString, closable = true)
        shown @= k
        bar(k).foreach(_.focus())
        Platform.log("tabs added " + k)
      }, MaterialIcon.Add)))
    }

    val face = new Avatar(helper, 64.0)
    face.busy := busy()
    face.busyDetail @= "Thinking"
    val said = TextView.bound(if (busy()) "Helper is at work" else "Helper is idle", 16.0, Theme.text())
    val toggle = new Button("Start or stop", () => busy @= !busy())
    busy.attach(on => Platform.log("tabs busy " + on.toString))

    face.position.left := (if (Direction.rtl()) ui.size.width() - 96.0 else 32.0)
    face.position.top @= TabBar.height + 40.0
    said.position.left := (if (Direction.rtl()) ui.size.width() - 132.0 - said.size.width() else 132.0)
    said.position.top @= TabBar.height + 62.0
    toggle.position.left := (if (Direction.rtl()) ui.size.width() - 32.0 - toggle.size.width() else 32.0)
    toggle.position.top @= TabBar.height + 140.0
    ui.children ++= List(bar, face, said, toggle)
    toggle.focus()
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
    Platform.log("tabs ok " + bar.tabs().map(_.key).mkString(","))
  }
}
