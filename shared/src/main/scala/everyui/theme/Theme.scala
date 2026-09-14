package everyui.theme

import everyui.Color
import reactify.Var

/** The palette every widget draws from. youi themed through CSS selectors, which only a browser has; here a theme
  * is a `Palette` in a `Var`, so `Theme.palette @= Palette.light` re-colours every binding that read
  * `Theme.accent()`, on every backend, without a stylesheet. */
object Theme {
  val palette: Var[Palette] = Var(Palette.dark)

  def background(): Color = palette().background
  def surface(): Color = palette().surface
  def raised(): Color = palette().raised
  def control(): Color = palette().control
  def controlLit(): Color = palette().controlLit
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
}
