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
  def text(): Color = palette().text
  def muted(): Color = palette().muted
  def accent(): Color = palette().accent
  def accentLit(): Color = palette().accentLit
  def onAccent(): Color = palette().onAccent
  def success(): Color = palette().success
  def warning(): Color = palette().warning
  def danger(): Color = palette().danger
  def info(): Color = palette().info
  def backdrop(): Color = palette().backdrop
  /** An app's own colour from the palette's `extras`, or `fallback` where the palette has none. */
  def extra(name: String, fallback: => Color): Color = palette().extras.getOrElse(name, fallback)
}
