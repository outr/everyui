package everyui.app

import everyui.chat.Participant
import everyui.component.{Button, ButtonStyle}
import everyui.font.MaterialIcon
import everyui.list.{RichList, RichLook, RichSection}
import everyui.menu.{Menu, MenuItem, MenuPicture}
import everyui.render.Platform
import everyui.theme.Tone
import everyui.tooltip.{RichTip, RichTipLine}
import everyui.Color
import reactify.*

/** The richtips demo's list and menu: Voidcraft's conversation rows, each with a tip of its own (`RichLook.tip`), and
  * its bell, a menu whose entries have tips (`MenuItem.tip`), one plain words (`MenuItem.tooltip`) and one none. Logs
  * `richtips row <title>` and `richtips entry <label>` as their tips are made, and `richtips chose <label>`. */
object RichTipsLists {
  // (title, persona, colour, last message, who asks, an agent working, workspace)
  private val talks: Vector[(String, String, String, String, Option[String], Boolean, String)] = Vector(
    ("Release notes", "Ada", "#7c4dff", "The changelog for 0.1.50 is drafted; three entries still need a version.",
      Some("Ada asks: publish today?"), true, "Sigil"),
    ("Player crash", "Bo", "#00897b", "Seen on the SHIELD after a resume from standby, twice in a row.", None, false,
      "NaboTV"),
    ("Import wizard", "Cy", "#ef6c00", "The CSV preview cuts the last column when the header is quoted.",
      Some("Cy asks: which encoding?"), false, "DataWrangler"),
    ("Morning check", "Di", "#1e88e5", "All green. Nothing to do.", None, false, "Sigil"),
    ("Old thread", "Ed", "#8d6e63", "Archived.", None, false, "NaboTV"))

  /** The conversations, a tip for every one but the last. */
  def conversations(): RichList[Int] = {
    val sections = Var(Vector(RichSection("recent", "Recent", talks.indices.toVector)))
    new RichList[Int](sections, _.toString, i => {
      val (title, persona, colour, last, asks, working, workspace) = talks(i)
      RichLook(title, Some(last), who = Some(Participant(persona.toLowerCase, persona)), busy = working,
        unread = asks.nonEmpty, count = if (asks.nonEmpty) 1 else 0,
        tip = Option.when(i < talks.size - 1)(_ => {
          Platform.log("richtips row " + title)
          RichTip(title, Some(MenuPicture.Letter(persona.take(1), Color.fromHex(colour))), List(
            Some(RichTipLine(MaterialIcon.Chat, persona + ": " + last)),
            asks.map(a => RichTipLine(MaterialIcon.Help, a, Some(Tone.Warning))),
            Option.when(working)(RichTipLine(MaterialIcon.Autorenew, "An agent is working", Some(Tone.Success)))).flatten,
            footer = Some("In " + workspace))
        }))
    })
  }

  /** The Alerts button, which opens the bell's menu. */
  def bell(): Button = {
    lazy val button: Button = new Button("Alerts", () => openBell(button), ButtonStyle.Tonal)
    button
  }

  /** The bell's menu, under `button`. */
  def openBell(button: Button): Menu = Menu.below(button, List(
    MenuItem("Approve a command", () => chose("Approve a command"), MaterialIcon.Terminal,
      detail = "sbt -J-Xmx8g -J-Xss8m \"desktop/testFull\" …", tooltip = "Sigil wants to run sbt desktop/testFull",
      tip = Some(_ => {
        Platform.log("richtips entry Approve a command")
        RichTip("Approve a command", lines = List(
          RichTipLine(MaterialIcon.Terminal, "sbt -J-Xmx8g -J-Xss8m \"desktop/testFull\" \"seo/testFull\""),
          RichTipLine(MaterialIcon.Workspaces, "In Sigil, asked by Ada"),
          RichTipLine(MaterialIcon.Schedule, "Waiting 4 minutes", Some(Tone.Warning))))
      })),
    MenuItem("A question", () => chose("A question"), MaterialIcon.Help, detail = "Which encoding does the import …",
      tip = Some(_ => {
        Platform.log("richtips entry A question")
        RichTip("Cy asks", lines = List(RichTipLine(MaterialIcon.Chat,
          "Which encoding does the import use when a file has no byte order mark: UTF-8, or the system's own?")),
          footer = Some("In DataWrangler"))
      })),
    MenuItem("Build finished", () => chose("Build finished"), MaterialIcon.CheckCircle,
      tooltip = "NaboTV's build finished in 3 minutes 12 seconds"),
    MenuItem.separator,
    MenuItem("Mark all read", () => chose("Mark all read"), MaterialIcon.DoneAll)))

  private def chose(label: String): Unit = Platform.log("richtips chose " + label)
}
