package everyui.theme

import everyui.Color
import reactify.Var

/** The palette every widget draws from. youi themed through CSS selectors, which only a browser has; here a theme
  * is a `Palette` in a `Var`, so `Theme.palette @= Palette.light` re-colours every binding that read
  * `Theme.accent()`, on every backend, without a stylesheet. */
object Theme {
  val palette: Var[Palette] = Var(Palette.dark)
  /** The family text is set in unless it names its own: "" is the framework's (Open Sans), an app's is one it
    * registered (`Faces.register`). Set it before building anything: text takes it as it is made. */
  val family: Var[String] = Var("")
  /** The image behind every screen that names none of its own (`UIScreen.background`): a site-absolute path or a
    * URL. Empty leaves the palette's background colour on its own. */
  val backgroundImage: Var[String] = Var("")
  /** How much of a surface that holds words is drawn, 0 to 1: all of it (1, the default, so every theme is drawn as
    * it always was), or less where the app shows a picture or a video behind its screens and wants it through them,
    * about 0.8 keeping the words on them easy to read. What follows it: everybody else's bubbles in a conversation
    * (`MessageView.othersOpacity` is this same `Var`), and the code wash of Markdown's code blocks and code spans,
    * which lets half as much through as the bubble or card it is in, and no more than its words read over
    * (`MarkdownView.codeOver`). */
  val surfaceOpacity: Var[Double] = Var(1.0)
  /** "dark" or "light": which the palette's background is, by its luminance, followed as the palette changes. What a
    * page declares as its `color-scheme` (the browser's own scrollbars, controls and selection follow it, and a frame
    * of the same scheme is drawn clear over it), and what an app tells the frames it shows. */
  val colorScheme: reactify.Val[String] = reactify.Val {
    val c = palette().background
    if (0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue > 0.5) "light" else "dark"
  }

  /** The palette `light` or `dark` as the person's device asks (`SystemScheme`), switched as they change it, unless
    * `choice` says otherwise: "light" or "dark" for an app's own setting, anything else ("system", "") to follow the
    * device. A bound `choice` (a setting the app keeps) is followed too.
    * {{{
    *   Theme.follow(Palette.light, Palette.dark, settings.appearance())   // "system", "light" or "dark"
    * }}} */
  def follow(light: => Palette, dark: => Palette, choice: => String = "system"): Unit = palette := {
    val asked = choice
    val scheme = if (asked == "light" || asked == "dark") asked else SystemScheme()
    if (scheme == "dark") dark else light
  }

  def background(): Color = palette().background
  def surface(): Color = palette().surface
  def raised(): Color = palette().raised
  def control(): Color = palette().control
  def controlLit(): Color = palette().controlLit
  def border(): Color = palette().border
  /** The edge of a control that its edge says is there (a field, a select, an outlined button): the palette's border
    * taken toward its text, as little as gives 3:1 against the grounds a control sits on (WCAG 1.4.11). A card's edge,
    * which says nothing a reader needs, keeps the lighter `border`. */
  def edge(): Color = edgeOf(palette())

  /** `edge` for palette `p`. */
  def edgeOf(p: Palette): Color = {
    def enough(c: Color): Boolean =
      List(p.background, p.surface, p.raised).forall(g => everyui.access.Access.contrast(c, g) >= 3.0)
    var t = 0.0
    while (t < 1.0 && !enough(p.border.blend(p.text, t))) t += 0.05
    p.border.blend(p.text, math.min(1.0, t))
  }
  def text(): Color = palette().text
  def muted(): Color = palette().muted
  def accent(): Color = palette().accent
  def accentLit(): Color = palette().accentLit
  def onAccent(): Color = palette().onAccent
  def success(): Color = palette().success
  def warning(): Color = palette().warning
  def danger(): Color = palette().danger
  def info(): Color = palette().info

  /** The accent and the states as words are drawn in them: their own tones where those read at 4.5:1 on every ground,
    * else the palette's `accentText` and the rest where it gives them, else the least lighter or darker tone of the
    * same hue that does (`Tones.readable`). A glyph, a fill, a ring keeps `accent()`; its words take `accentText()`. */
  def accentText(): Color = { val p = palette(); wordsTone("accent", p.accent, p.accentText) }
  def accentLitText(): Color = { val p = palette(); wordsTone("accentLit", p.accentLit, p.accentLitText) }
  def successText(): Color = { val p = palette(); wordsTone("success", p.success, p.successText) }
  def warningText(): Color = { val p = palette(); wordsTone("warning", p.warning, p.warningText) }
  def dangerText(): Color = { val p = palette(); wordsTone("danger", p.danger, p.dangerText) }
  def infoText(): Color = { val p = palette(); wordsTone("info", p.info, p.infoText) }
  /** Any colour as words read in it on this palette's grounds (an app's own: a tag's, a brand's). */
  def readable(c: Color): Color = Tones.readable(c, palette())
  /** Words drawn on a fill of `fill`, as `onAccent` is on the accent: white, or black where white does not read. */
  def wordsOn(fill: Color): Color = Tones.wordsOn(fill, palette())

  // the words' tone of each role, worked out once for each palette
  private var tonesOf: Palette = null
  private var tones: Map[String, Color] = Map.empty
  private def wordsTone(name: String, own: Color, set: Option[Color]): Color = set.getOrElse {
    val p = palette()
    if (!(tonesOf eq p)) {
      tonesOf = p
      tones = Map.empty
    }
    tones.getOrElse(name, {
      val t = Tones.readable(own, p)
      tones = tones + (name -> t)
      t
    })
  }
  def backdrop(): Color = palette().backdrop
  /** An app's own colour from the palette's `extras`, or `fallback` where the palette has none. */
  def extra(name: String, fallback: => Color): Color = palette().extras.getOrElse(name, fallback)
}
