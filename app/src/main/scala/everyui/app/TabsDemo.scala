package everyui.app

import everyui.chat.{Avatar, Participant}
import everyui.component.{Button, TextView}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.{TabBar, TabLead}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** Tabs led by an avatar or an icon (`TabBar.add(…, lead)`), and the ring that turns around somebody at work: an
  * Overview tab with no lead, Release plan led by Helper's avatar, busy and "Running bash", Ada's led by hers and idle,
  * and Main.scala led by a file's icon; and Helper's face large under them, busy and "Thinking". A button starts and
  * stops them both. `busy=0` starts them idle, `theme=light` for the light palette, `rtl=1` to lay it out right to
  * left. Logs `tabs ok` once built and `tabs busy <on>` at each change. */
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
    bar.add("ada", "Ada Lovelace", closable = true, lead = TabLead.Person(ada))
    bar.add("main", "Main.scala", closable = true, lead = TabLead.Icon(MaterialIcon.Description))
    bar.closing.attach(k => bar.remove(k))

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
