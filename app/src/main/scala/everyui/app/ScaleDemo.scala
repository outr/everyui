package everyui.app

import everyui.component.{Button, ButtonStyle, Column, Component, Row, ScrollContainer, TextInput, TextView}
import everyui.i18n.Direction
import everyui.keymap.Keymap
import everyui.kit.{Mirror, ScaleControl}
import everyui.list.VirtualList
import everyui.menu.{Menu, MenuItem}
import everyui.render.Platform
import everyui.theme.{Palette, Theme, UiScale}
import everyui.ui
import reactify.*

/** The size the app is drawn at (`UiScale`), turned up and down over a screen of everything at once: words, a
  * paragraph that wraps to the screen, list rows with a menu on each, a field, buttons and a menu dropped from one.
  * The size is kept on the device (`UiScale.remembered`), so it is the same after a reload; Ctrl or Command with =, -
  * and 0 turn it up, down and back. `scale=<factor>` to start at a size, `probe=1` to turn it up a step a second three
  * times and back, `theme=light` for the light palette, `rtl=1` for right to left. Logs `scale ok` once built,
  * `scale <factor> <width>x<height>` at each size it is laid out at, and each thing chosen. */
object ScaleDemo {
  private val fruit: Vector[String] = Vector("Apples", "Bananas", "Cherries", "Dates", "Elderberries", "Figs", "Grapes",
    "Honeydew", "Kiwis", "Lemons", "Mangoes", "Nectarines")

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    UiScale.remembered().map(found => Platform.log("scale remembered " + found.map(_.toString).getOrElse("none"))).start()
    Platform.launchArg("scale").toDoubleOption.foreach(f => UiScale.factor @= f)
    UiScale.commands(Keymap.global)
    val said = Var("Nothing chosen yet")
    def chose(what: String): Unit = {
      said @= "Chose " + what
      Platform.log("scale chose " + what)
    }
    def width: Double = math.min(560.0, ui.size.width() - 32.0)

    val control = new ScaleControl(label = "Size")
    // the menu is dropped from the button, which it is made before
    val anchor: Var[Option[Component]] = Var(None)
    val more = new Button("More", () => anchor().foreach(a => Menu.below(a, List(
      MenuItem("Larger", () => UiScale.larger()), MenuItem("Smaller", () => UiScale.smaller()),
      MenuItem("Default size", () => UiScale.reset()), MenuItem.separator,
      MenuItem("Rename…", () => chose("Rename")), MenuItem("Delete", () => chose("Delete"), danger = true)))),
      ButtonStyle.Outlined)
    anchor @= Some(more)
    val readout = TextView.bound("Drawn at " + UiScale.percent() + ", laid out in " + ui.size.width().toInt.toString +
      " × " + ui.size.height().toInt.toString, 13.0, Theme.muted())
    val paragraph = TextView.paragraph("Everything on this screen is drawn at the size chosen above: the words, the " +
      "rows, the field and the buttons, each in proportion, so nothing is cut short in a box sized for smaller words. " +
      "The screen is laid out again in the room left, as a window made smaller would be.", 560.0)
    paragraph.size.width := width
    val list = VirtualList.text(Var(fruit), (f: String) => f, (f: String) => f)
    list.size.width := width
    list.size.height @= list.rowHeight * 6.0
    list.activated.attach(f => chose(f))
    list.menu.attach(p => Menu.at(p.x, p.y, List(MenuItem("Open " + p.item, () => chose(p.item)),
      MenuItem("Delete", () => chose("Delete " + p.item), danger = true))))
    val field = new TextInput("Name", width = 320.0)
    field.size.width := math.min(320.0, width)
    val buttons = Row(8.0, middle = true)(
      new Button("Save", () => chose("Save"), ButtonStyle.Filled),
      new Button("Cancel", () => chose("Cancel"), ButtonStyle.Text))

    val note = TextView.paragraph("Turned up and down with − and +, or Ctrl or Command with =, - and 0; the " +
      "percentage goes back to 100%.", 560.0, 13.0, Theme.muted())
    note.size.width := width
    val body = Column(14.0)(
      TextView("Size", 22.0), note,
      Row(12.0, middle = true)(control, more), readout,
      heading("Words"), paragraph,
      heading("Rows"), list,
      heading("A field and buttons"), field, buttons, TextView.bound(said(), 13.0, Theme.muted()))
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    Mirror.place(body, () => ui.size.width(), () => 16.0)
    body.position.top @= 16.0
    scroll.content.children += body
    ui.children += scroll
    // logged once the screen has its new size, both ways
    val waiting = Var(false)
    def soon(): Unit = if (!waiting()) {
      waiting @= true
      Platform.later(() => {
        waiting @= false
        laidOut()
      })
    }
    ui.size.width.attach(_ => soon())
    ui.size.height.attach(_ => soon())
    laidOut()
    Platform.log("scale ok")
    // the size turned up a step a second, three times, and back, laid out again each time
    if (Platform.launchArg("probe") == "1") {
      Platform.after(1.0, () => UiScale.larger())
      Platform.after(2.0, () => UiScale.larger())
      Platform.after(3.0, () => UiScale.larger())
      Platform.after(4.0, () => UiScale.reset())
    }
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
  }

  private def laidOut(): Unit = Platform.log("scale " + UiScale().toString + " " + ui.size.width().toInt.toString + "x" +
    ui.size.height().toInt.toString)

  private def heading(words: String): Component = TextView(words, 18.0)
}
