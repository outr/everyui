package everyui.chart

import everyui.draw.Path
import everyui.render.Platform

/** The shapes a chart's marks are made of, as every platform draws them: a bar with its data end rounded and its end
  * on the baseline square, and plain rectangles where the platform draws no curves (a Roku, an Apple TV: `drawn`). */
object ChartPaths {
  /** Whether this platform draws paths (curves, diagonals, circles): the web, the desktop and Android do; a Roku and
    * the Apple backend draw rectangles only (`everyui.draw.Block`). */
  def drawn: Boolean = Platform.kind != everyui.PlatformKind.Roku && Platform.kind != everyui.PlatformKind.TvOS &&
    Platform.kind != everyui.PlatformKind.IOS

  /** The corner a bar's data end is rounded by. */
  val corner: Double = 4.0

  /** A bar from (`x`, `y`), `w` by `h`, its data end rounded `r` where `end` says ("top", "bottom", "left", "right";
    * "" for none, a segment inside a stack) and the rest square; a plain rectangle where curves are not drawn. */
  def bar(x: Double, y: Double, w: Double, h: Double, end: String, r: Double = corner): Path = {
    val c = math.max(0.0, math.min(r, math.min(w, h) / 2.0))
    if (!drawn || c < 0.5 || end.isEmpty) Path.rect(x, y, w, h)
    else {
      // each corner's radius: those at the data end rounded
      val tl = if (end == "top" || end == "left") c else 0.0
      val tr = if (end == "top" || end == "right") c else 0.0
      val br = if (end == "bottom" || end == "right") c else 0.0
      val bl = if (end == "bottom" || end == "left") c else 0.0
      var p = Path.empty.moveTo(x + tl, y).lineTo(x + w - tr, y)
      if (tr > 0.0) p = p.arc(x + w - tr, y + tr, tr, math.Pi * 1.5, math.Pi * 2.0)
      p = p.lineTo(x + w, y + h - br)
      if (br > 0.0) p = p.arc(x + w - br, y + h - br, br, 0.0, math.Pi / 2.0)
      p = p.lineTo(x + bl, y + h)
      if (bl > 0.0) p = p.arc(x + bl, y + h - bl, bl, math.Pi / 2.0, math.Pi)
      p = p.lineTo(x, y + tl)
      if (tl > 0.0) p = p.arc(x + tl, y + tl, tl, math.Pi, math.Pi * 1.5)
      p.close
    }
  }

  /** A dot of radius `r` at (`x`, `y`): a circle, or a square where curves are not drawn. */
  def dot(x: Double, y: Double, r: Double): Path = if (drawn) Path.circle(x, y, r) else Path.rect(x - r, y - r, 2 * r, 2 * r)

  /** A ring's slice about (`cx`, `cy`) between radius `inner` and `outer`, from angle `from` to `to` (radians,
    * clockwise from twelve o'clock), `gap` pixels kept clear between it and the next all along its sides (a pie's
    * slice where `inner` is 0); empty where the slice is too thin to show past its gaps, and a whole ring for all of
    * it. */
  def slice(cx: Double, cy: Double, inner: Double, outer: Double, from: Double, to: Double, gap: Double = 0.0): Path = {
    val quarter = math.Pi / 2.0
    val sweep = to - from
    if (sweep >= 2.0 * math.Pi - 1e-6) {
      val ring = Path.empty.arc(cx, cy, outer, 0.0, 2.0 * math.Pi).close
      if (inner <= 0.0) ring else ring.moveTo(cx + inner, cy).arc(cx, cy, inner, 2.0 * math.Pi, 0.0).close
    }
    else {
      val out = gap / 2.0 / outer
      if (sweep <= 2.0 * out) Path.empty
      else {
        val a = from - quarter
        val b = to - quarter
        val rim = Path.empty.arc(cx, cy, outer, a + out, b - out)
        val in = if (inner > 0.0) gap / 2.0 / inner else 0.0
        if (inner > 0.0 && sweep > 2.0 * in) rim.arc(cx, cy, inner, b - in, a + in).close
        else {
          // the point the two sides meet at, moved out along the middle so each side keeps its half of the gap
          val middle = (a + b) / 2.0
          val d = math.min(outer / 2.0, gap / 2.0 / math.max(1e-6, math.sin(sweep / 2.0)))
          rim.lineTo(cx + d * math.cos(middle), cy + d * math.sin(middle)).close
        }
      }
    }
  }
}
