package everyui.app

import everyui.chart.{LineChart, Series}
import everyui.component.types.FontWeight
import everyui.component.{Button, ButtonStyle, Container, TextView}
import everyui.font.MaterialIcon
import everyui.layout.{FlowLayout, VerticalLayout}
import everyui.menu.{MenuPicture, MenuPictureView}
import everyui.render.Platform
import everyui.theme.{Palette, Theme, Tone}
import everyui.tooltip.{RichTip, RichTipLine, RichTipMeter, TipContext, Tooltip}
import everyui.{Color, ui}
import reactify.*

/** Workspace tiles shaped like Voidcraft's, each with a rich tip (`Component.tipView`, a `RichTip`): its logo and name
  * over icon lines and CPU and memory meters, and for Sigil a chart of its CPU over the last minutes and an "Open"
  * button that closes the tip (`TipContext.close`), the tip interactive so the pointer can move into it. Each tile's
  * `tooltip` is the plain words a screen reader hears. The tips come as the pointer rests on a tile, as the keys bring
  * the focus to one, or as a finger is held on one. `open=<n>` opens tile n's tip at once (1 is Sigil),
  * `theme=light` shows the light palette. Logs `richtips ok`, `richtips opened <name>` as a tip opens and
  * `richtips open <name>` as its button is pressed. */
object RichTipsDemo {
  // (name, letter colour, unread, state, state's tone, cores, memory in GB, what runs)
  private val workspaces = Vector(
    ("Sigil", "#7c4dff", 5, "working", Tone.Success, 21.8, 14.9, "mostly sbt in 'Sigil'"),
    ("NaboTV", "#00897b", 0, "idle", Tone.Info, 0.4, 3.1, "a Gradle daemon, waiting"),
    ("DataWrangler", "#ef6c00", 12, "waiting on you", Tone.Warning, 2.6, 6.2, "a server and a browser"))
  private val cores = 32.0
  private val memory = 64.0

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val title = TextView("Workspaces: rest the pointer, the focus or a finger on one", 13.0, Theme.muted())
    title.position.left @= 24.0
    title.position.top @= 16.0
    val tiles = new Container {
      layout @= Some(FlowLayout(12.0, 12.0))
      position.left @= 24.0
      position.top @= 48.0
      size.width := math.max(240.0, ui.size.width() - 48.0)
    }
    tiles.children ++= workspaces.zipWithIndex.map { case (w, i) => tile(w, i == 0) }
    ui.children ++= List(title, tiles)
    Platform.log("richtips ok")
    Platform.launchArg("open").toIntOption.flatMap(n => tiles.children().lift(n - 1)).foreach { t =>
      Platform.after(0.5, () => Tooltip.open(t))
    }
  }

  private def tile(w: (String, String, Int, String, Tone, Double, Double, String), interactive: Boolean): Container = {
    val (name, colour, unread, state, tone, used, held, running) = w
    val picture = MenuPicture.Letter(name.take(1), Color.fromHex(colour))
    val box = new Container {
      size.width @= 220.0
      size.height @= 56.0
      radius @= 8.0
      backgroundColor := Theme.surface()
      borderWidth @= 1.0
      borderColor := Theme.border()
      focusable @= true
    }
    val logo = new MenuPictureView(picture, 32.0, MaterialIcon.Workspaces, Theme.muted())
    logo.position.left @= 12.0
    logo.position.top @= 12.0
    val label = TextView(name, 14.0, Theme.text())
    label.font.weight @= FontWeight.SemiBold
    label.position.left @= 54.0
    label.position.top @= 10.0
    val status = TextView(if (unread > 0) unread.toString + " unread, " + state else state, 12.0, tone.text())
    status.position.left @= 54.0
    status.position.top @= 30.0
    box.children ++= List(logo, label, status)
    box.tooltip @= name + " · " + (if (unread > 0) unread.toString + " unread · " else "") + state + " · " +
      used.toString + " cores · " + held.toString + " GB · " + running
    box.tipInteractive @= interactive
    box.tipView @= Some(tip => tipOf(w, picture, interactive, tip))
    box.event.click.attach(_ => box.focus())
    box
  }

  private def tipOf(w: (String, String, Int, String, Tone, Double, Double, String), picture: MenuPicture,
                    interactive: Boolean, tip: TipContext): RichTip = {
    val (name, _, unread, state, tone, used, held, running) = w
    Platform.log("richtips opened " + name)
    val lines = List(
      Option.when(unread > 0)(RichTipLine(MaterialIcon.Mail, unread.toString + " unread")),
      Some(RichTipLine(MaterialIcon.Autorenew, state, Some(tone))),
      Some(RichTipLine(MaterialIcon.Terminal, running))).flatten
    val meters = List(
      RichTipMeter("CPU", used / cores, if (used / cores > 0.6) Tone.Warning else Tone.Accent, used.toString + " cores"),
      RichTipMeter("Memory", held / memory, Tone.Info, held.toString + " GB"))
    val extra = Option.when(interactive) {
      val column = new Container {
        layout @= Some(VerticalLayout(8.0))
      }
      val chart = LineChart((1 to 12).toVector.map(m => (12 - m).toString + "m"),
        Vector(Series("CPU", Vector(9.0, 12.5, 18.0, 16.2, 21.0, 24.4, 22.8, 19.6, 23.1, 25.0, 22.4, 21.8))), area = true)
      chart.tableToggle @= false
      chart.size.width @= 280.0
      chart.size.height @= 96.0
      val open = new Button("Open " + name, () => {
        Platform.log("richtips open " + name)
        tip.close()
      }, ButtonStyle.Tonal)
      column.children ++= List(chart, open)
      column
    }
    RichTip(name, Some(picture), lines, meters, Some("Updated a moment ago"), extra)
  }
}
