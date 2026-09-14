package everyui.theme

import everyui.Color
import reactify.Var

/** The palette every widget defaults to; an app sets what it wants once. Dark by default. */
object Theme {
  val background: Var[Color] = Var(Color.fromHex("121212"))
  val surface: Var[Color] = Var(Color.fromHex("1e1e1e"))
  val raised: Var[Color] = Var(Color.fromHex("272727"))
  val control: Var[Color] = Var(Color.fromHex("2c2c2c"))
  val controlLit: Var[Color] = Var(Color.fromHex("3a3a3a"))
  val text: Var[Color] = Var(Color.fromHex("e0e0e0"))
  val muted: Var[Color] = Var(Color.fromHex("9e9e9e"))
  val accent: Var[Color] = Var(Color.fromHex("90caf9"))
  val accentLit: Var[Color] = Var(Color.fromHex("bbdefb"))
  val onAccent: Var[Color] = Var(Color.fromHex("0d1b2a"))
  val success: Var[Color] = Var(Color.fromHex("69f0ae"))
  val warning: Var[Color] = Var(Color.fromHex("ffab40"))
  val danger: Var[Color] = Var(Color.fromHex("ff8a80"))
  val info: Var[Color] = Var(Color.fromHex("80d8ff"))
  /** The sheet a dialog dims the page with. */
  val backdrop: Var[Color] = Var(Color.fromHex("000000").withAlpha(0.6))
}
