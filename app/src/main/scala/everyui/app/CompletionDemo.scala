package everyui.app

import everyui.async.Task
import everyui.chat.Completion
import everyui.component.{Button, ButtonStyle, Column, Component, ScrollContainer, TextInput}
import everyui.dialog.{DialogAction, Dialogs}
import everyui.render.Platform
import everyui.text.{CompletionSource, TextArea}
import everyui.theme.{Palette, Theme}
import everyui.ui

/** Fields that complete what is typed in them (`CompletionSource`): To and Cc lists of addresses (separators ",;",
  * each choice followed by ", "), a city searched by the whole field, and a box of lines whose words starting with '#'
  * complete to tags; each source answers a moment later, as a server would. A button (or `dialog=1` as the screen
  * opens) puts the city field up in a dialog, where Escape closes the list and only the list. `theme=light` for the
  * light palette, `rtl=1` for right to left. Logs `completion ok` once built and `completion chose <field> <insert>`
  * for each choice. */
object CompletionDemo {
  private val people: Vector[(String, String)] = Vector(("Ossi Mäki", "ossi@example.com"),
    ("Oscar Lindqvist", "oscar.l@example.com"), ("Olivia Brandt", "olivia@example.com"),
    ("Ann Berg", "ann@example.com"), ("Anders Holm", "anders@example.com"), ("Bea Novak", "bea@example.com"),
    ("Carl Eklund", "carl@example.com"), ("Dana Reyes", "dana@example.com"), ("Omar Haddad", "omar@example.com"),
    ("Otto Weber", "otto@example.com"), ("Oona Virtanen", "oona@example.com"))
  private val cities: Vector[String] = Vector("Paris", "Parma", "Patras", "Perth", "Porto", "Prague", "Pune",
    "Oslo", "Osaka", "Ottawa", "Oxford", "Helsinki", "Hamburg", "Lisbon", "Lyon")
  private val tags: Vector[String] = Vector("#design", "#deploy", "#docs", "#release", "#review", "#roadmap")

  // `found` a moment after it is asked for, as an answer over the network comes
  private def later[T](found: => T): Task[T] = Task.async[T](done => Platform.after(0.15, () => done(found)))

  private val addresses: CompletionSource = CompletionSource(typed => later {
    val t = typed.toLowerCase
    people.filter { case (n, e) => n.toLowerCase.startsWith(t) || e.startsWith(t) ||
      n.toLowerCase.contains(" " + t) }.map { case (n, e) => Completion(n + " <" + e + ">", e) }
  }, separators = ",;", after = ", ")

  private val places: CompletionSource = CompletionSource(typed => later {
    cities.filter(_.toLowerCase.startsWith(typed.toLowerCase)).map(c => Completion(c, c))
  }, rows = 5)

  // a word starting with '#' completes to the tags it begins; any other word asks for nothing
  private val tagged: CompletionSource = CompletionSource(typed => later {
    if (!typed.startsWith("#")) Vector.empty
    else tags.filter(_.startsWith(typed.toLowerCase)).map(t => Completion(t, t, Some("tag")))
  }, separators = " \n", after = " ", minimum = 2)

  private def field(label: String, source: CompletionSource): TextInput = {
    val input = new TextInput(label)
    input.size.width := math.min(520.0, ui.size.width() - 32.0)
    input.completions @= Some(source)
    input.completed.attach(c => Platform.log("completion chose " + label + " " + c.insert))
    input
  }

  private def dialog(): Unit = {
    val city = field("City in a dialog", places)
    city.size.width := math.min(360.0, ui.size.width() - 80.0)
    Dialogs.show("Where to?", List(city), close => List(DialogAction("Done", close)))
      .map(_ => Platform.log("completion dialog closed " + city.value())).start()
    city.focus()
  }

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") everyui.i18n.Direction.set(true)
    ui.backgroundColor := Theme.background()
    val to = field("To", addresses)
    val cc = field("Cc", addresses)
    val city = field("City", places)
    val notes = new TextArea("Notes: type #d or #r for a tag", 520.0)
    notes.size.width := math.min(520.0, ui.size.width() - 32.0)
    notes.completions @= Some(tagged)
    notes.completed.attach(c => Platform.log("completion chose Notes " + c.insert))
    val open: Component = new Button("City in a dialog", () => dialog(), ButtonStyle.Outlined)
    val body = Column(14.0)(to, cc, city, notes, open)
    body.position.left @= 16.0
    body.position.top @= 16.0
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    scroll.content.children += body
    ui.children += scroll
    if (Platform.launchArg("dialog") == "1") dialog()
    Platform.log("completion ok")
  }
}
