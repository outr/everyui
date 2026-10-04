package everyui.app

import everyui.component.{Button, Column, Row, ScrollContainer, TextInput, TextView}
import everyui.focus.{Focus, TextEntry}
import everyui.kit.SearchField
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** Typing without the platform's keyboard: a field, a search box and a keyboard of the app's own over a line of words,
  * the field focused. What a Roku's phone app types (ECP's `keypress/Lit_<c>`, `Backspace` and `Enter`) goes straight
  * into the one the focus is on or in, and the keyboard's keys still type their letter with OK. Each value, and each
  * Enter typed, is logged:
  *
  *   [ui] typing name=Hi é
  *   [ui] typing name submitted=Hi é
  *   [ui] typing search=ab
  *   [ui] typing words=xy
  *
  * With `probe=1` the demo types through the shared focus layer itself, by the names the platforms report, and
  * prints one line to compare across platforms:
  *
  *   [ui] typing ok name=Hi search=a words=qx done=qx
  */
object TypingDemo {
  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val width = math.min(360.0, ui.size.width() - 32.0)
    val name = new TextInput("Name", width = width)
    name.value.attach(v => Platform.log("typing name=" + v))
    name.submitted.attach(v => Platform.log("typing name submitted=" + v))
    val search = new SearchField(width = width)
    search.value.attach(v => Platform.log("typing search=" + v))
    search.submitted.attach(v => Platform.log("typing search submitted=" + v))
    // the app's own keyboard, editing the words over it
    val words: Var[String] = Var("")
    val done: Var[String] = Var("")
    words.attach(v => Platform.log("typing words=" + v))
    val shown = TextView.bound(if (words().isEmpty) "Words" else words(), 16.0)
    shown.color := (if (words().isEmpty) Theme.muted() else Theme.text())
    val keys = Row(8.0)(
      new Button("q", () => words @= words() + "q"),
      new Button("w", () => words @= words() + "w"),
      new Button("⌫", () => if (words().nonEmpty) words @= words().substring(0, words().length - 1)))
    keys.typeInto(TextEntry(words, () => {
      done @= words()
      Platform.log("typing words done=" + words())
    }))
    val body = Column(14.0)(name, search, shown, keys)
    body.position.left @= 16.0
    body.position.top @= 16.0
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    scroll.content.children += body
    ui.children += scroll
    name.focus()
    if (Platform.launchArg("probe") == "1") Platform.after(0.2, () => {
      List("H", "i", " ", "é", "Backspace", "\n", "Backspace").foreach(press)
      val typed = name.value()
      search.focus()
      press("a")
      keys.childComponents.headOption.foreach(_.focus())
      // OK still presses the key with the focus; what is typed goes to the words
      List("Enter", "x", "\n").foreach(press)
      Platform.log("typing ok name=" + typed + " search=" + search.value() + " words=" + words() + " done=" + done())
    })
    Platform.after(0.5, () => if (Platform.freezeSeconds() >= 0.0) Platform.frozen())
  }

  private def press(name: String): Unit = {
    Focus.key(name, true)
    Focus.key(name, false)
  }
}
