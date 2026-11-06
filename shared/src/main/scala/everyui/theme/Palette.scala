package everyui.theme

import everyui.Color

/** The colours a theme is made of: the ground, the surfaces on it, controls and their lit (hovered or focused) state, the
  * edge of a field, text, the accent, and the states, plus `extras`: an app's own named colours (a brand's gold), which a theme can change as well.
  * `dark` and `light` are the two the framework ships. */
case class Palette(background: Color, surface: Color, raised: Color, control: Color, controlLit: Color, border: Color,
                   text: Color, muted: Color, accent: Color, accentLit: Color, onAccent: Color, success: Color, warning: Color,
                   danger: Color, info: Color, backdrop: Color, extras: Map[String, Color] = Map.empty)

// Each one's words (its text, muted, accent, success, warning, danger and info) are 4.5:1 or more against every ground
// it has (background, surface, raised, control, controlLit), and onAccent against accent and accentLit: WCAG 2.2 AA
// (PaletteContrastSpec). The border is lighter, for a card's edge; a control's is `Theme.edge`.
object Palette {
  /** Where `p` falls short of WCAG 2.2 AA, each as "muted on controlLit 4.25": its words (text, muted, accent, success,
    * warning, danger, info) under 4.5:1 against any ground (background, surface, raised, control, controlLit),
    * onAccent under 4.5:1 on accent or accentLit, and a control's edge (`Theme.edge` for `p`) under 3:1 on what a
    * control sits on. None, for a palette that meets it: for an app's own palettes in its tests. */
  def audit(p: Palette): List[String] = {
    def ratio(a: Color, b: Color): Double = everyui.access.Access.contrast(a, b)
    def short(name: String, ratioOf: Double, need: Double): List[String] =
      if (ratioOf >= need) Nil else List(name + " " + (math.round(ratioOf * 100.0) / 100.0).toString)
    val grounds = List("background" -> p.background, "surface" -> p.surface, "raised" -> p.raised,
      "control" -> p.control, "controlLit" -> p.controlLit)
    val words = List("text" -> p.text, "muted" -> p.muted, "accent" -> p.accent, "success" -> p.success,
      "warning" -> p.warning, "danger" -> p.danger, "info" -> p.info)
    val edge = Theme.edgeOf(p)
    words.flatMap(w => grounds.flatMap(g => short(w._1 + " on " + g._1, ratio(w._2, g._2), 4.5))) ++
      short("onAccent on accent", ratio(p.onAccent, p.accent), 4.5) ++
      short("onAccent on accentLit", ratio(p.onAccent, p.accentLit), 4.5) ++
      grounds.take(3).flatMap(g => short("edge on " + g._1, ratio(edge, g._2), 3.0))
  }

  lazy val dark: Palette = Palette(
    background = Color.fromHex("121212"), surface = Color.fromHex("1e1e1e"), raised = Color.fromHex("272727"),
    control = Color.fromHex("2c2c2c"), controlLit = Color.fromHex("3a3a3a"), border = Color.fromHex("424242"), text = Color.fromHex("e0e0e0"),
    muted = Color.fromHex("a3a3a3"), accent = Color.fromHex("90caf9"), accentLit = Color.fromHex("bbdefb"),
    onAccent = Color.fromHex("0d1b2a"), success = Color.fromHex("69f0ae"), warning = Color.fromHex("ffab40"),
    danger = Color.fromHex("ff8a80"), info = Color.fromHex("80d8ff"), backdrop = Color.Black.withAlpha(0.6),
    extras = Map("list.block" -> Color.White.withAlpha(0.05)))

  lazy val light: Palette = Palette(
    background = Color.fromHex("fafafa"), surface = Color.White, raised = Color.fromHex("f0f0f0"),
    control = Color.fromHex("e6e6e6"), controlLit = Color.fromHex("d8d8d8"), border = Color.fromHex("c4c4c4"), text = Color.fromHex("1c1b1f"),
    muted = Color.fromHex("5e5e5e"), accent = Color.fromHex("135db1"), accentLit = Color.fromHex("1b79cc"),
    onAccent = Color.White, success = Color.fromHex("286c2b"), warning = Color.fromHex("9b4600"),
    danger = Color.fromHex("b42424"), info = Color.fromHex("02629b"), backdrop = Color.Black.withAlpha(0.4),
    extras = Map("list.block" -> Color.Black.withAlpha(0.04)))
}
