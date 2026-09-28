package everyui.monaco.gallery

import everyui.component.TextView
import everyui.monaco.*
import everyui.theme.Theme

/** A minimal editor, with the theme and read-only switched, and where the cursor is. */
object BasicDemo {
  val code: String =
    """void main() {
      |  final greetings = ['Hello', 'Bonjour', 'Hallo', 'Hola', 'こんにちは'];
      |  for (final greeting in greetings) {
      |    print('$greeting from Monaco!');
      |  }
      |}
      |""".stripMargin

  def apply(demo: Demo): DemoPage = {
    val controller = MonacoController(initialValue = code, language = "dart")
    val editor = new MonacoEditor(controller)
    val theme = new Picker(List("vs-dark", "vs", "hc-black", "hc-light"), MonacoThemes.current.getOrElse("vs-dark"))(t =>
      controller.setTheme(t))
    val readOnly = new Toggle("read-only", false)(on => controller.setReadOnly(on))
    val where = TextView.bound(controller.position().map(p => s"Ln ${p.line}, Col ${p.column}").getOrElse("-"), 12.0, Theme.muted())
    where.font.family @= "Roboto Mono"
    new DemoPage(demo, editor, List(theme, readOnly, where), () => controller.dispose())
  }
}
