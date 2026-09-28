package everyui.monaco

import everyui.Color
import everyui.theme.Palette
import fabric.*

/** A theme for the editor: token `rules` and UI `colors` (`"editor.background"` -> `"#1e1e2e"`) over one of Monaco's own
  * (`vs`, `vs-dark`, `hc-black`, `hc-light`), which it inherits the rest from. Themes are global to a page (or to an
  * editor's web view): `MonacoThemes`. */
case class MonacoTheme(base: String, inherit: Boolean = true, rules: List[MonacoTokenRule] = Nil,
                       colors: Map[String, String] = Map.empty) {
  def toJson: Json = obj("base" -> str(base), "inherit" -> bool(inherit), "rules" -> arr(rules.map(_.toJson)*),
    "colors" -> obj(colors.toSeq.map { case (k, v) => k -> str(v) }*))
}

object MonacoTheme {
  /** The name `MonacoThemes.followPalette` defines its theme under. */
  val PaletteName: String = "everyui"

  /** Every surface that could cover what is behind the editor made clear, the tokens left to `base`: for an editor
    * over a picture (with a clear embed behind it). */
  def transparent(base: String = "vs-dark"): MonacoTheme = MonacoTheme(base, colors = Map(
    "editor.background" -> "#00000000", "editor.lineHighlightBackground" -> "#00000000",
    "editorGutter.background" -> "#00000000", "editorLineNumber.background" -> "#00000000",
    "minimap.background" -> "#00000000", "editorOverviewRuler.background" -> "#00000000",
    "editorWidget.background" -> "#00000000", "editorHoverWidget.background" -> "#00000000",
    "editorSuggestWidget.background" -> "#00000000", "scrollbarSlider.background" -> "#30808080",
    "scrollbarSlider.hoverBackground" -> "#40808080"))

  /** The editor in the app's colours: `vs-dark` or `vs` by how dark the palette's ground is, its surfaces, text,
    * accent and states in Monaco's places. The tokens keep the base's colours, unless `tokens` maps the palette onto
    * them too (the accent for keywords, success for strings, info for types, warning for numbers, muted italic for
    * comments). `clear` leaves the editor's own ground see-through. */
  def fromPalette(p: Palette, tokens: Boolean = false, clear: Boolean = false): MonacoTheme = {
    def hex(c: Color): String = c.toHex
    def alpha(c: Color, a: Int): String = c.withAlpha(a / 255.0).toHexAlpha
    val dark = luminance(p.background) < 0.5
    val ground = if (clear) "#00000000" else hex(p.surface)
    val raised = if (clear) "#00000000" else hex(p.raised)
    MonacoTheme(if (dark) "vs-dark" else "vs",
      rules = if (!tokens) Nil else List(
        MonacoTokenRule("keyword", Some(p.accent.toHex.drop(1))),
        MonacoTokenRule("string", Some(p.success.toHex.drop(1))),
        MonacoTokenRule("type", Some(p.info.toHex.drop(1))),
        MonacoTokenRule("number", Some(p.warning.toHex.drop(1))),
        MonacoTokenRule("comment", Some(p.muted.toHex.drop(1)), fontStyle = Some("italic"))),
      colors = Map(
        "editor.background" -> ground,
        "editorGutter.background" -> ground,
        "editor.foreground" -> hex(p.text),
        "editorLineNumber.activeForeground" -> hex(p.text),
        "editorLineNumber.foreground" -> alpha(p.muted, 0x80),
        "editorWhitespace.foreground" -> alpha(p.muted, 0x40),
        "editorIndentGuide.background1" -> alpha(p.muted, 0x20),
        "editorIndentGuide.activeBackground1" -> alpha(p.accent, 0x60),
        "editor.lineHighlightBackground" -> alpha(p.text, 0x0f),
        "editorCursor.foreground" -> hex(p.accent),
        "editorLink.activeForeground" -> hex(p.accent),
        "editor.selectionBackground" -> alpha(p.accent, 0x55),
        "editor.inactiveSelectionBackground" -> alpha(p.accent, 0x33),
        "scrollbarSlider.background" -> alpha(p.muted, 0x40),
        "scrollbarSlider.hoverBackground" -> alpha(p.muted, 0x60),
        "scrollbarSlider.activeBackground" -> alpha(p.accent, 0x80),
        "editorWidget.background" -> raised,
        "editorHoverWidget.background" -> raised,
        "editorSuggestWidget.background" -> raised,
        "editorSuggestWidget.selectedBackground" -> alpha(p.accent, 0x40),
        "minimap.background" -> raised,
        "editorOverviewRuler.background" -> raised,
        "editorWidget.border" -> alpha(p.border, 0x80),
        "list.hoverBackground" -> hex(p.controlLit),
        "editorError.foreground" -> hex(p.danger),
        "editorWarning.foreground" -> hex(p.warning),
        "editorInfo.foreground" -> hex(p.info),
        "diffEditor.insertedTextBackground" -> alpha(p.success, 0x33),
        "diffEditor.removedTextBackground" -> alpha(p.danger, 0x33),
        "diffEditor.insertedLineBackground" -> alpha(p.success, 0x1a),
        "diffEditor.removedLineBackground" -> alpha(p.danger, 0x1a)))
  }

  /** Relative luminance, 0 (black) to 1 (white), as WCAG weighs the channels. */
  def luminance(c: Color): Double = {
    def linear(v: Double): Double = if (v <= 0.03928) v / 12.92 else math.pow((v + 0.055) / 1.055, 2.4)
    0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)
  }
}
