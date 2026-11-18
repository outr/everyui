package everyui.chart

/** Values as a chart writes them, each a `Double => String` a chart takes as its `format`: on its value axis, in its
  * tips and its table. An app with its own (a currency in the reader's own style) passes that instead.
  *
  * {{{
  *   ChartFormat.number(1284.5)        // "1,284.5"
  *   ChartFormat.compact(12900.0)      // "12.9K"
  *   ChartFormat.money("$")(4.2)       // "$4.20"
  *   ChartFormat.percent(0.46)         // "46%"
  * }}} */
object ChartFormat {
  /** Thousands separated, with up to `places` decimals where the value has them: 1,284 and 0.25. */
  def number(v: Double, places: Int = 2): String = fixed(v, places, trim = true)

  /** In full under 10,000 (1,284), and short past it: 12.9K, 4.2M, 1.3B, as an axis that reaches that far is written. */
  def compact(v: Double): String = {
    val a = math.abs(v)
    if (a < 10000.0) number(v, 1)
    else if (a < 1e6) shortened(v, 1e3, "K")
    else if (a < 1e9) shortened(v, 1e6, "M")
    else shortened(v, 1e9, "B")
  }

  /** Money in `symbol` before the amount, to `places` decimals ("$1,284.50"); past 10,000 compact ("$12.9K"), as an
    * axis wants. */
  def money(symbol: String, places: Int = 2): Double => String = v => {
    val sign = if (v < 0.0) "-" else ""
    val a = math.abs(v)
    sign + symbol + (if (a >= 10000.0) compact(a) else fixed(a, places, trim = false))
  }

  /** A share (0.46) as a percentage ("46%"), with up to `places` decimals. */
  def percent(v: Double, places: Int = 0): String = fixed(v * 100.0, places, trim = true) + "%"

  /** Hours of a day's time, "1.5 h". */
  def hours(v: Double): String = number(v, 1) + " h"

  private def shortened(v: Double, by: Double, suffix: String): String = fixed(v / by, 1, trim = true) + suffix

  /** `v` to `places` decimals, thousands separated; `trim` drops trailing zeros (and the point with them). */
  def fixed(v: Double, places: Int, trim: Boolean): String = {
    val scale = math.pow(10.0, places)
    val whole = math.round(math.abs(v) * scale)
    val negative = v < 0.0 && whole != 0L
    val units = whole / math.round(scale)
    var decimals = (whole - units * math.round(scale)).toString
    while (decimals.length < places) decimals = "0" + decimals
    if (trim) while (decimals.nonEmpty && decimals.endsWith("0")) decimals = decimals.substring(0, decimals.length - 1)
    (if (negative) "-" else "") + grouped(units) + (if (places > 0 && decimals.nonEmpty) "." + decimals else "")
  }

  private def grouped(value: Long): String = {
    val digits = value.toString
    var out = ""
    var head = digits.length
    while (head > 3) {
      out = "," + digits.substring(head - 3, head) + out
      head -= 3
    }
    digits.substring(0, head) + out
  }
}
