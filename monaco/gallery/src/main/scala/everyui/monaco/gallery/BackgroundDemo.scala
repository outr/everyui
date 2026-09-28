package everyui.monaco.gallery

import everyui.{Color, Gradient}
import everyui.component.Container
import everyui.monaco.*
import everyui.monaco.Monaco.ui
import reactify.*

/** The editor over the app's own ground: a clear theme on a clear embed shows the gradient behind it. */
object BackgroundDemo {
  val code: String =
    """// Toggle "Transparent" to show the gradient the app draws behind the editor. It:
      |//   1. makes the editor with MonacoEditor(transparent = true), so its box has no ground of its own;
      |//   2. defines MonacoTheme.transparent() and sets it, clearing every surface of the editor.
      |//
      |// The tokens keep their colours: the clear theme inherits them from its base ("vs-dark").
      |
      |val ground = Gradient(135.0, Color.fromHex("0D1117"), Color.fromHex("1A237E"), Color.fromHex("311B92"),
      |  Color.fromHex("B71C1C"))
      |""".stripMargin

  def apply(demo: Demo): DemoPage = {
    val controller = MonacoController(initialValue = code, language = "scala")
    val defined = MonacoThemes.defineTheme("demo-transparent", MonacoTheme.transparent())
    val clear = Var(false)
    val toggle = new Toggle("Transparent", false)(on => defined.foreach(_ => controller.setTheme(if (on) "demo-transparent" else "vs-dark")))
    val editor = new MonacoEditor(controller, transparent = true)
    val body = new Container {
      gradient @= Some(Gradient(135.0, Color.fromHex("0D1117"), Color.fromHex("1A237E"), Color.fromHex("311B92"), Color.fromHex("B71C1C")))
      editor.size.width := size.width()
      editor.size.height := size.height()
      children += editor
    }
    new DemoPage(demo, body, List(toggle), () => {
      controller.setTheme("vs-dark")
      controller.dispose()
    })
  }
}
