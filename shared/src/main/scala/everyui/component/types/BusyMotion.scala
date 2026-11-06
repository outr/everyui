package everyui.component.types

import everyui.font.MaterialIcon

/** How a busy glyph moves (`MaterialIconView.busyMotion`): turning, for a glyph that is itself a turning symbol (Sync,
  * Refresh, an hourglass), whose fade read as flashing; breathing (fading slowly down and back), for any other; or,
  * `Auto`, by the glyph. */
enum BusyMotion {
  case Auto, Breathe, Spin
}

object BusyMotion {
  // the glyphs that are a turn, or a wait, of themselves
  private val turning: Set[String] = Set("sync", "autorenew", "refresh", "loop", "cached", "rotate_left",
    "rotate_right", "rotate_90_degrees_ccw", "rotate_90_degrees_cw", "hourglass_empty")

  /** What `Auto` is for `icon`: a turn for a turning glyph, else a breath. */
  def of(icon: MaterialIcon): BusyMotion = if (turning.contains(icon.name)) Spin else Breathe
}
