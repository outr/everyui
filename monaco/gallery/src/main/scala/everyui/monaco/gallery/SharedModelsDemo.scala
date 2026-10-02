package everyui.monaco.gallery

import everyui.component.{Button, ButtonStyle, Component, Container, TextView}
import everyui.monaco.*
import everyui.theme.Theme

/** Three editors over two shared models (MonacoModels), VS Code's shape: the two on top show the same file, typed in
  * and undone together with a cursor and scroll each; the one below shows another file. The left one switches between
  * the files and has its own place in each when it comes back. */
object SharedModelsDemo {
  private val files = NavigationDemo.files

  def apply(demo: Demo): DemoPage = {
    val models = files.map { case (uri, text) => MonacoModels.open(uri, text, "scala") }
    val main = models(1)
    val greeter = models.head
    val left = MonacoEditor.of(main)
    val right = MonacoEditor.of(main)
    val below = MonacoEditor.of(greeter)
    def name(uri: String): String = uri.substring(uri.lastIndexOf('/') + 1)

    // a pane: what its editor shows (its file, dirty or not) over the editor
    def pane(editor: MonacoEditor, label: String): Container = new Container {
      private val c = editor.controller
      private val title = TextView.bound(label + ": " + name(c.uri()) + (if (c.dirty()) "  ●" else ""), 12.0, Theme.muted())
      title.position.left @= 12.0
      title.position.top @= 6.0
      editor.position.top @= 26.0
      editor.size.width := size.width()
      editor.size.height := math.max(0.0, size.height() - 26.0)
      children ++= List(title, editor)
    }

    val a = pane(left, "A")
    val b = pane(right, "B")
    val c = pane(below, "C")
    val body = new Container {
      private val gap = 1.0
      a.size.width := (size.width() - gap) / 2.0
      a.size.height := (size.height() - gap) * 0.6
      b.position.left := a.size.width() + gap
      b.size.width := a.size.width()
      b.size.height := a.size.height()
      c.position.top := a.size.height() + gap
      c.size.width := size.width()
      c.size.height := size.height() - a.size.height() - gap
      backgroundColor := Theme.border()
      children ++= List(a, b, c)
    }
    val switch = new Button("A: other file", () => {
      val to = if (left.controller.uri() == main.uri) greeter else main
      left.controller.switchToModel(to.uri)
    }, ButtonStyle.Tonal)
    val save = new Button("Save all", () => models.foreach(_.markSaved()), ButtonStyle.Tonal)
    new DemoPage(demo, body, List[Component](switch, save), () => models.foreach(m => MonacoModels.close(m.uri)))
  }
}
