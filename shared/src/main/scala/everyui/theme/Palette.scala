package everyui.theme

import everyui.Color

/** The colours a theme is made of: the ground, the surfaces on it, controls and their lit (hovered or focused) state, the
  * edge of a field, text, the accent, and the states, plus `extras`: an app's own named colours (a brand's gold), which a theme can change as well.
  * `dark` and `light` are the two the framework ships. */
case class Palette(background: Color, surface: Color, raised: Color, control: Color, controlLit: Color, border: Color,
                   text: Color, muted: Color, accent: Color, accentLit: Color, onAccent: Color, success: Color, warning: Color,
                   danger: Color, info: Color, backdrop: Color, extras: Map[String, Color] = Map.empty)

object Palette {
  lazy val dark: Palette = Palette(
    background = Color.fromHex("121212"), surface = Color.fromHex("1e1e1e"), raised = Color.fromHex("272727"),
    control = Color.fromHex("2c2c2c"), controlLit = Color.fromHex("3a3a3a"), border = Color.fromHex("424242"), text = Color.fromHex("e0e0e0"),
    muted = Color.fromHex("9e9e9e"), accent = Color.fromHex("90caf9"), accentLit = Color.fromHex("bbdefb"),
    onAccent = Color.fromHex("0d1b2a"), success = Color.fromHex("69f0ae"), warning = Color.fromHex("ffab40"),
    danger = Color.fromHex("ff8a80"), info = Color.fromHex("80d8ff"), backdrop = Color.Black.withAlpha(0.6),
    extras = Map("list.block" -> Color.White.withAlpha(0.05)))

  lazy val light: Palette = Palette(
    background = Color.fromHex("fafafa"), surface = Color.White, raised = Color.fromHex("f0f0f0"),
    control = Color.fromHex("e6e6e6"), controlLit = Color.fromHex("d8d8d8"), border = Color.fromHex("c4c4c4"), text = Color.fromHex("1c1b1f"),
    muted = Color.fromHex("6b6b6b"), accent = Color.fromHex("1565c0"), accentLit = Color.fromHex("1e88e5"),
    onAccent = Color.White, success = Color.fromHex("2e7d32"), warning = Color.fromHex("ef6c00"),
    danger = Color.fromHex("c62828"), info = Color.fromHex("0277bd"), backdrop = Color.Black.withAlpha(0.4),
    extras = Map("list.block" -> Color.Black.withAlpha(0.04)))
}
