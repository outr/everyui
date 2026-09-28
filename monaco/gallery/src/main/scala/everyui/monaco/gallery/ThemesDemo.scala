package everyui.monaco.gallery

import everyui.component.{Column, Container, TextView}
import everyui.monaco.*
import everyui.monaco.Monaco.uiThread
import everyui.theme.{Palette, Theme}

import scala.concurrent.Future

/** Themes defined at runtime and set globally, one of them made from the app's own palette and following it. */
object ThemesDemo {
  val code: String =
    """// everyui-monaco: custom themes
      |//
      |// Monaco's themes are global: setting one changes every editor on the page. Token rules follow
      |// the TextMate scope names VS Code's theme files use. "everyui" is the app's own palette
      |// (MonacoTheme.fromPalette), and follows it: flip the palette to see.
      |
      |case class Greeting(target: String) {
      |  // the number, "Hello" and the interpolation are coloured by the theme
      |  def render: String = s"Hello, $target! #1"
      |}
      |
      |@main def main(): Unit = println(Greeting("Monaco").render)
      |""".stripMargin

  val themes: List[(String, MonacoTheme)] = List(
    "catppuccin-mocha" -> MonacoTheme("vs-dark", rules = List(
      MonacoTokenRule("comment", Some("6c7086"), fontStyle = Some("italic")), MonacoTokenRule("keyword", Some("cba6f7"), fontStyle = Some("bold")),
      MonacoTokenRule("string", Some("a6e3a1")), MonacoTokenRule("number", Some("fab387")), MonacoTokenRule("type", Some("f9e2af")),
      MonacoTokenRule("identifier", Some("cdd6f4"))), colors = Map("editor.background" -> "#1e1e2e", "editor.foreground" -> "#cdd6f4",
      "editorLineNumber.foreground" -> "#585b70", "editorCursor.foreground" -> "#f5e0dc", "editor.selectionBackground" -> "#414355")),
    "solarized-warm" -> MonacoTheme("vs", rules = List(
      MonacoTokenRule("comment", Some("93a1a1"), fontStyle = Some("italic")), MonacoTokenRule("keyword", Some("859900"), fontStyle = Some("bold")),
      MonacoTokenRule("string", Some("2aa198")), MonacoTokenRule("number", Some("dc322f")), MonacoTokenRule("type", Some("cb4b16")),
      MonacoTokenRule("identifier", Some("657b83"))), colors = Map("editor.background" -> "#fdf6e3", "editor.foreground" -> "#586e75",
      "editorLineNumber.foreground" -> "#93a1a1", "editorCursor.foreground" -> "#cb4b16", "editor.selectionBackground" -> "#eee8d5")),
    "tokyo-night" -> MonacoTheme("vs-dark", rules = List(
      MonacoTokenRule("comment", Some("565f89"), fontStyle = Some("italic")), MonacoTokenRule("keyword", Some("bb9af7")),
      MonacoTokenRule("string", Some("9ece6a")), MonacoTokenRule("number", Some("ff9e64")), MonacoTokenRule("type", Some("7aa2f7")),
      MonacoTokenRule("identifier", Some("c0caf5"))), colors = Map("editor.background" -> "#1a1b26", "editor.foreground" -> "#a9b1d6",
      "editorLineNumber.foreground" -> "#3b4261", "editorCursor.foreground" -> "#c0caf5", "editor.selectionBackground" -> "#283457")))

  def apply(demo: Demo): DemoPage = {
    val controller = MonacoController(initialValue = code, language = "scala")
    Future.sequence(themes.map { case (name, theme) => MonacoThemes.defineTheme(name, theme) })
    val names = List("vs-dark", "vs", "hc-black", "hc-light") ++ themes.map(_._1) :+ MonacoTheme.PaletteName
    val picker = new Picker(names, MonacoThemes.current.getOrElse("vs-dark"), 188.0)(name =>
      if (name == MonacoTheme.PaletteName) MonacoThemes.followPalette(tokens = true)
      else {
        MonacoThemes.stopFollowing()
        MonacoThemes.setTheme(name)
      })
    val palette = new Picker(List("dark", "light"), if (Theme.palette() == Palette.light) "light" else "dark")(p =>
      Theme.palette @= (if (p == "light") Palette.light else Palette.dark))
    val panel = Column(10.0, padding = 16.0)(TextView("Theme", 13.0), picker, TextView("App palette", 13.0), palette)
    val editor = new MonacoEditor(controller)
    val body = new Container {
      panel.size.width @= 220.0
      editor.position.left @= 221.0
      editor.size.width := size.width() - 221.0
      editor.size.height := size.height()
      children ++= List(panel, editor)
    }
    new DemoPage(demo, body, Nil, () => controller.dispose())
  }
}
