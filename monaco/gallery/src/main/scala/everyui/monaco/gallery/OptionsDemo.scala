package everyui.monaco.gallery

import everyui.component.{Column, Container, TextView}
import everyui.monaco.*
import everyui.theme.Theme
import reactify.*

/** Options changed live: every change goes through `updateOptions`, and the editor is never made again. */
object OptionsDemo {
  val code: String =
    """// Resize the font, toggle word wrap, flip the minimap: every change goes through
      |// MonacoController.updateOptions(MonacoEditorOptions(...)) and is applied live.
      |//
      |// A very long comment that shows word wrap: Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua, ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
      |
      |object Counter {
      |  private var count = 0
      |
      |  def increment(by: Int = 1): Int = {
      |    count += by
      |    count
      |  }
      |
      |  def reset(): Unit = count = 0
      |}
      |""".stripMargin

  def apply(demo: Demo): DemoPage = {
    val fontSize = Var(14.0)
    val tabSize = Var(2)
    val wrap = Var(false)
    val minimap = Var(true)
    val numbers = Var(true)
    val smooth = Var(true)
    val cursor = Var(MonacoCursorStyle.Line)
    def options: MonacoEditorOptions = MonacoEditorOptions(
      fontSize = Some(fontSize()), tabSize = Some(tabSize()),
      wordWrap = Some(if (wrap()) MonacoWordWrap.On else MonacoWordWrap.Off),
      minimap = Some(MonacoMinimapOptions(enabled = Some(minimap()))),
      lineNumbers = Some(if (numbers()) MonacoLineNumbersStyle.On else MonacoLineNumbersStyle.Off),
      cursorSmoothCaretAnimation = Some(smooth()), cursorStyle = Some(cursor()),
      bracketPairColorization = Some(true), smoothScrolling = Some(true))
    val controller = MonacoController(initialValue = code, language = "scala", options = options)
    def apply(): Unit = controller.updateOptions(options)

    def label(words: => String): TextView = TextView.bound(words, 13.0, Theme.text())
    val panel = Column(10.0, padding = 16.0)(
      label("Font size: " + fontSize().toInt.toString + "px"),
      new Picker((10 to 22 by 2).map(_.toString).toList, "14", 240.0)(v => { fontSize @= v.toDouble; apply() }),
      label("Tab size: " + tabSize().toString),
      new Picker(List("2", "4", "8"), "2")(v => { tabSize @= v.toInt; apply() }),
      new Toggle("Word wrap", false)(v => { wrap @= v; apply() }),
      new Toggle("Minimap", true)(v => { minimap @= v; apply() }),
      new Toggle("Line numbers", true)(v => { numbers @= v; apply() }),
      new Toggle("Smooth caret", true)(v => { smooth @= v; apply() }),
      label("Cursor style"),
      new Picker(MonacoCursorStyle.values.toList.map(_.wire), "line", 240.0)(v => {
        cursor @= MonacoCursorStyle.values.find(_.wire == v).getOrElse(MonacoCursorStyle.Line)
        apply()
      }))
    val editor = new MonacoEditor(controller)
    val body = new Container {
      panel.size.width @= 280.0
      editor.position.left @= 281.0
      editor.size.width := size.width() - 281.0
      editor.size.height := size.height()
      private val rule = new Container {
        position.left @= 280.0
        size.width @= 1.0
        backgroundColor := Theme.border()
      }
      rule.size.height := size.height()
      children ++= List(panel, rule, editor)
    }
    new DemoPage(demo, body, Nil, () => controller.dispose())
  }
}
