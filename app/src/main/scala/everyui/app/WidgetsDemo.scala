package everyui.app

import everyui.Color
import everyui.component.*
import everyui.font.MaterialIcon
import everyui.i18n.{Arb, Icu}
import everyui.layout.FlowLayout
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** The widget layer, all of it on one page: rows, columns, padding, a table, bars, a progress bar, a checkbox, a
  * chip, choices, buttons (every style), text fields, a dialog, and a question's choices on a card 360 wide, their
  * words wrapped within it (`Button.labelLines`; `rtl=1` right to left). Logs `widgets ok` with the page's size once
  * built, so a platform that cannot build it says so. */
object WidgetsDemo {
  private def choice(words: String, lines: Int): Button = {
    val b = new Button(words)
    b.labelLines @= lines
    b
  }

  def run(): Unit = {
    if (Platform.launchArg("rtl") == "1") everyui.i18n.Direction.set(true)
    ui.backgroundColor := Theme.background()
    val checked = new Checkbox("Optimise", initial = false, Some("Rebuilds the search indexes; slow, so off by default."))
    val progress = ProgressBar(0.6, 240.0)
    val dialog = new Dialog("A dialog", 400.0)
    dialog.body.children ++= List(TextView.wrapped("Some words that are long enough to wrap onto a second line inside the dialog's body.", 400.0), new Checkbox("A choice", initial = true))
    val cancel = new Button("Cancel", () => dialog.close())
    dialog.buttons.children ++= List(cancel, new Button("Do it", () => dialog.close(), style = ButtonStyle.Filled))
    val table = new Table[(String, Int)](List(
      TableColumn("Name", 200.0, r => TextView(r._1)),
      TableColumn("Count", 120.0, r => TextView(r._2.toString)),
      TableColumn("Status", 130.0, r => new Chip(if (r._2 > 1) "many" else "one", Color.fromHex("2e7d32")))))
    table.rows @= List(("alpha", 1), ("beta", 2), ("gamma", 3))
    // a question's choices on a card 360 wide (a chat's, a form's): each read whole before it is picked, its words wrapped
    // within the card (`labelLines`), and one left on a line, cut short at the card's edge
    val choices = Column(10.0, padding = 16.0)(
      new TextView {
        content @= "How should the cleanup go? Each choice is drawn whole, its words wrapped within the card."
        whiteSpace @= everyui.component.types.WhiteSpace.Normal
        font.size @= 14.0
        color := Theme.text()
      },
      Column(8.0)(
        choice("Proceed with the Voidcraft cleanup (Firebase key is already moved to `.env.firebase`)", 0),
        choice("Keep the old key where it is for now, and remind me again tomorrow morning", 0),
        choice("Proceed with the Voidcraft cleanup (Firebase key is already moved to `.env.firebase`)", 1),
        choice("Skip", 0)))
    choices.size.width := 360.0
    choices.backgroundColor := Theme.surface()
    choices.radius @= 12.0
    val page = Column(16.0, padding = 16.0)(
      Row(8.0, middle = true)(TextView("Widgets", 22.0), new Chip("live", Color.fromHex("1565c0")), new IconButton(MaterialIcon.Home, "Home")),
      Row(8.0)(new Choice("Name", true, () => ()), new Choice("Last run", false, () => ()), new Choice("Failures", false, () => ())),
      Row(12.0, middle = true)(new Button("Open dialog", () => dialog.show(cancel), style = ButtonStyle.Filled), new Button("Quiet"), progress),
      Row(12.0, middle = true)(new TextInput("Name", width = 240.0), new TextInput("Password", secure = true, width = 240.0), new Button("Link", style = ButtonStyle.Text), new Button("Mail", style = ButtonStyle.Outlined, glyph = Some(MaterialIcon.MailOutline))),
      checked,
      new Bars(List(Bar(0.2, Color.fromHex("2e7d32"), "a"), Bar(1.0, Color.fromHex("c62828"), "b"), Bar(0.5, Color.fromHex("1565c0"), "c"))),
      table,
      new Container {
        layout @= Some(FlowLayout(8.0, 8.0))
        size.width @= 500.0
        children ++= (1 to 7).map(i => Column(4.0, padding = 8.0)(TextView("tile " + i.toString, 11.0, Theme.muted()), TextView((i * 137).toString, 12.0)))
      },
      choices)
    ui.children += page
    Platform.log("widgets ok " + page.size.width().toInt.toString + "x" + page.size.height().toInt.toString + " rows=" + table.size.height().toInt.toString)
    // translations: an ARB file's messages (escapes, metadata skipped) and ICU plurals, nested placeholders and selects
    val messages = Arb.parse("{\"@@locale\": \"en\", \"runs\": \"{n, plural, =0{no runs} =1{1 run} other{{n} runs in {state}}}\", \"@runs\": {\"placeholders\": {\"n\": {}}}, \"quote\": \"say \\\"hi\\\" \\u00e9\"}")
    val formatted = List("0", "1", "12").map(n => Icu.format(messages.getOrElse("runs", ""), Map("n" -> n, "state" -> "Utah"))) ++
      List(messages.getOrElse("quote", ""), Icu.format("{kind, select, tv{a TV} other{a device}}", Map("kind" -> "tv")))
    val expected = List("no runs", "1 run", "12 runs in Utah", "say \"hi\" \u00e9", "a TV")
    Platform.log((if (formatted == expected && messages.size == 2) "i18n ok " else "i18n FAIL ") + formatted.mkString(" | "))

    if (Platform.launchArg("probe") == "1") {
      dialog.show(cancel)
      Platform.log("dialog " + dialog.size.width().toInt.toString + "x" + dialog.size.height().toInt.toString)
      dialog.close()
    }
  }
}
