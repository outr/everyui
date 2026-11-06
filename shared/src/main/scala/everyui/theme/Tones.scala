package everyui.theme

import everyui.Color

/** A role's colour as words are drawn in it: the same hue and saturation, its lightness taken as little as reads at
  * 4.5:1 against every ground the palette has (background, surface, raised, control, controlLit), lighter on dark
  * grounds and darker on light ones. A glyph or a fill keeps the role's own tone, which needs only 3:1 and may be
  * deeper (a theme's chosen depth); words in it are given this one (`Theme.accentText` and the rest). */
object Tones {
  /** `c`, or the least lighter or darker tone of it that words read in on every ground of `p`. */
  def readable(c: Color, p: Palette, need: Double = 4.5): Color = {
    val grounds = List(p.background, p.surface, p.raised, p.control, p.controlLit)
    def worst(x: Color): Double = grounds.map(g => everyui.access.Access.contrast(x, g)).min
    if (worst(c) >= need) c
    else {
      val (h, s, l) = hsl(c)
      val dark = luminance(p.background) < 0.5
      var at = l
      var best = c
      var found = false
      while (!found && at >= 0.0 && at <= 1.0) {
        at = if (dark) at + 0.01 else at - 0.01
        val next = fromHsl(h, s, math.max(0.0, math.min(1.0, at))).withAlpha(c.alpha)
        best = next
        if (worst(next) >= need) found = true
      }
      best
    }
  }

  /** Words drawn on a fill of `fill` (a chip's, a face's): white where white reads at 4.5:1 on it, else black, which
    * then reads better. A fill not wholly opaque is taken as it shows over `p`'s background. */
  def wordsOn(fill: Color, p: Palette): Color = {
    val a = fill.alpha
    val g = p.background
    val shown = Color.fromRGB(fill.red * a + g.red * (1.0 - a), fill.green * a + g.green * (1.0 - a),
      fill.blue * a + g.blue * (1.0 - a))
    val white = everyui.access.Access.contrast(Color.White, shown)
    if (white >= 4.5 || white >= everyui.access.Access.contrast(Color.Black, shown)) Color.White else Color.Black
  }

  private def luminance(c: Color): Double = 0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue

  /** Hue (0 to 1), saturation and lightness. */
  def hsl(c: Color): (Double, Double, Double) = {
    val (r, g, b) = (c.red, c.green, c.blue)
    val most = math.max(r, math.max(g, b))
    val least = math.min(r, math.min(g, b))
    val l = (most + least) / 2.0
    if (most == least) (0.0, 0.0, l)
    else {
      val d = most - least
      val s = if (l > 0.5) d / (2.0 - most - least) else d / (most + least)
      val h =
        if (most == r) ((g - b) / d + (if (g < b) 6.0 else 0.0)) / 6.0
        else if (most == g) ((b - r) / d + 2.0) / 6.0
        else ((r - g) / d + 4.0) / 6.0
      (h, s, l)
    }
  }

  /** The colour at hue `h` (0 to 1), saturation `s` and lightness `l`. */
  def fromHsl(h: Double, s: Double, l: Double): Color =
    if (s == 0.0) Color.fromRGB(l, l, l)
    else {
      val q = if (l < 0.5) l * (1.0 + s) else l + s - l * s
      val p = 2.0 * l - q
      def part(t0: Double): Double = {
        val t = if (t0 < 0.0) t0 + 1.0 else if (t0 > 1.0) t0 - 1.0 else t0
        if (t < 1.0 / 6.0) p + (q - p) * 6.0 * t
        else if (t < 0.5) q
        else if (t < 2.0 / 3.0) p + (q - p) * (2.0 / 3.0 - t) * 6.0
        else p
      }
      Color.fromRGB(part(h + 1.0 / 3.0), part(h), part(h - 1.0 / 3.0))
    }
}
