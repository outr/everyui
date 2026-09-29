package everyui.app

import everyui.component.{Button, Column, InputView, ScrollContainer, TextInput}
import everyui.render.Platform
import everyui.text.TextArea
import everyui.theme.Theme
import everyui.ui

/** Read-only fields: a share link and a key to copy, a note in a box of lines, and an editable field under them, with a
  * button that makes the link editable and read-only again. What reaches each field's value is logged, so a check can
  * type and paste into them and see that nothing reaches a read-only one:
  *
  *   [ui] readonly link=https://example.com/s/4f2a9c
  *   [ui] readonly ok
  */
object ReadOnlyDemo {
  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val width = math.min(360.0, ui.size.width() - 32.0)
    val link = field("Share link", "link", "https://example.com/s/4f2a9c", width)
    link.readOnly @= true
    val note = new TextArea(width = width, grow = true)
    note.value @= "A note that can be read and copied,\nbut not changed."
    note.readOnly @= true
    note.value.attach(v => Platform.log("readonly note=" + v.replace('\n', '|')))
    // the platform's own field, bare
    val key = new InputView
    key.text @= "KEY-1234-5678"
    key.readOnly @= true
    key.font.size @= 16.0
    key.color := Theme.text()
    key.size.width @= width
    key.size.height @= 24.0
    key.text.attach(v => Platform.log("readonly key=" + v))
    val name = field("Name (editable)", "name", "", width)
    val toggle = new Button("Make the link editable", () => link.readOnly @= !link.readOnly())
    toggle.label := (if (link.readOnly()) "Make the link editable" else "Make the link read-only")
    val body = Column(14.0)(link, note, key, name, toggle)
    body.position.left @= 16.0
    body.position.top @= 16.0
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    scroll.content.children += body
    ui.children += scroll
    Platform.after(0.5, () => {
      Platform.log("readonly ok")
      if (Platform.freezeSeconds() >= 0.0) Platform.frozen()
    })
  }

  private def field(label: String, name: String, value: String, width: Double): TextInput = {
    val input = new TextInput(label, width = width)
    input.value @= value
    input.value.attach(v => Platform.log("readonly " + name + "=" + v))
    input
  }
}
