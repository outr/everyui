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

  private val Clear = "#00000000"

  /** The editor's own ground made clear (its background, gutter, line highlight, minimap and overview ruler), the
    * tokens left to `base`: for an editor over a picture (with a clear embed behind it). What floats over the code
    * stays solid, so no text shows through it: the widgets keep `base`'s, and the sticky scroll and a problem's peek
    * (which would take the clear ground) take `base`'s ground. */
  def transparent(base: String = "vs-dark"): MonacoTheme = {
    val ground = base match {
      case "vs" => "#FFFFFE"
      case "hc-black" => "#000000"
      case "hc-light" => "#FFFFFF"
      case _ => "#1E1E1E"
    }
    MonacoTheme(base, colors = Map(
      "editor.background" -> Clear, "editor.lineHighlightBackground" -> Clear,
      "editorGutter.background" -> Clear, "editorLineNumber.background" -> Clear,
      "minimap.background" -> Clear, "editorOverviewRuler.background" -> Clear,
      "scrollbarSlider.background" -> "#30808080", "scrollbarSlider.hoverBackground" -> "#40808080",
      "editorStickyScroll.background" -> ground, "editorStickyScrollGutter.background" -> ground,
      "editorMarkerNavigation.background" -> ground))
  }

  /** What floats over the code, solid: the widgets (hover, suggest, find, parameter hints, the action list, menus,
    * the quick input), the sticky scroll, a problem's peek and the peek view. Each would otherwise take the editor's
    * clear ground, or a colour of Monaco's own. */
  private def floating(ground: String, raised: String, lit: String): Map[String, String] = Map(
    "editorWidget.background" -> raised,
    "editorHoverWidget.background" -> raised,
    "editorSuggestWidget.background" -> raised,
    "editorActionList.background" -> raised,
    "menu.background" -> raised,
    "quickInput.background" -> raised,
    "editorMarkerNavigation.background" -> raised,
    "peekViewTitle.background" -> raised,
    "peekViewResult.background" -> raised,
    "peekViewEditor.background" -> ground,
    "peekViewEditorGutter.background" -> ground,
    "peekViewEditorStickyScroll.background" -> ground,
    "peekViewEditorStickyScrollGutter.background" -> ground,
    "editorStickyScroll.background" -> ground,
    "editorStickyScrollGutter.background" -> ground,
    "editorStickyScrollHover.background" -> lit)

  /** The editor in the app's colours: `vs-dark` or `vs` by how dark the palette's ground is, its surfaces, text,
    * accent and states in Monaco's places. The tokens keep the base's colours, unless `tokens` maps the palette onto
    * them too (the accent for keywords, success for strings, info for types, warning for numbers, muted italic for
    * comments). `clear` leaves the editor's own ground see-through (its background, gutter, minimap and overview
    * ruler), and keeps what floats over the code solid (`floating`) in the palette's surfaces. */
  def fromPalette(p: Palette, tokens: Boolean = false, clear: Boolean = false): MonacoTheme = {
    def hex(c: Color): String = c.toHex
    def alpha(c: Color, a: Int): String = c.withAlpha(a / 255.0).toHexAlpha
    val dark = luminance(p.background) < 0.5
    val ground = if (clear) Clear else hex(p.surface)
    val raised = hex(p.raised)
    val under = if (clear) Clear else raised
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
        "minimap.background" -> under,
        "editorOverviewRuler.background" -> under,
        "editorWidget.border" -> alpha(p.border, 0x80),
        "list.hoverBackground" -> hex(p.controlLit),
        "editorError.foreground" -> hex(p.danger),
        "editorWarning.foreground" -> hex(p.warning),
        "editorInfo.foreground" -> hex(p.info),
        "diffEditor.insertedTextBackground" -> alpha(p.success, 0x33),
        "diffEditor.removedTextBackground" -> alpha(p.danger, 0x33),
        "diffEditor.insertedLineBackground" -> alpha(p.success, 0x1a),
        "diffEditor.removedLineBackground" -> alpha(p.danger, 0x1a)) ++
        (if (clear) floating(hex(p.surface), raised, hex(p.controlLit)) else Map.empty))
  }

  /** Relative luminance, 0 (black) to 1 (white), as WCAG weighs the channels. */
  def luminance(c: Color): Double = {
    def linear(v: Double): Double = if (v <= 0.03928) v / 12.92 else math.pow((v + 0.055) / 1.055, 2.4)
    0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)
  }
}
