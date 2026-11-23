package everyui.render

/** Where a cue goes in the video's box where EveryUI draws the subtitles ([[WebCues]]), from its WebVTT settings, plain
  * Scala (so the desktop's specs read it as the page does). Across: its box `left` and `width`, percent of the video's
  * width, its lines aligned in it by `align` (CSS: left, center, right). Down: `percent` of the video's height (its
  * line anchored there by `shift`, the share of its own height it is moved up: 0, 50 or 100), or where that is -1,
  * `lines` lines in from the `edge` ("top" or "bottom"), past the safe margin. The usual cue, which says nothing, is at
  * the foot, centred, 5% in from each side. */
case class CuePlace(edge: String = CuePlace.Bottom,
                    lines: Int = 0,
                    percent: Double = -1.0,
                    shift: Double = 0.0,
                    left: Double = CuePlace.margin,
                    width: Double = 100.0 - 2.0 * CuePlace.margin,
                    align: String = "center") {
  /** Whether it sits where an ordinary cue does, so it is stacked with the others there, the last said lowest. */
  def ordinary: Boolean = edge == CuePlace.Bottom && lines == 0 && percent < 0.0
}

object CuePlace {
  val Top: String = "top"
  val Bottom: String = "bottom"

  /** The safe margin, percent of the video's width (across) and of its height (down). */
  val margin: Double = 5.0

  /** A cue's place from its settings, as a cue object has them, any it lacks (an older engine's) as WebVTT's own
    * defaults: `line` a number (None for `auto`), `snapToLines` whether it counts lines rather than percent,
    * `lineAlign` (start, center, end), `position` percent (None for `auto`), `positionAlign` (line-left, center,
    * line-right, auto), `size` percent, `align` (start, center, end, left, right; Chromium 38's `middle` as center),
    * and the SubStation keypad place a SubRip cue carries (`CueText.anchor`, 2 where none). */
  def of(line: Option[Double], snapToLines: Boolean, lineAlign: String, position: Option[Double], positionAlign: String,
         size: Double, align: String, keypad: Int = 2): CuePlace = {
    val aligned = align.toLowerCase match {
      case "start" | "left" => "left"
      case "end" | "right" => "right"
      case _ => "center"
    }
    // across, as WebVTT lays a horizontal cue out: its position as its anchor, by the alignment where it is auto
    val wide = math.max(0.0, math.min(100.0, if (size.isNaN) 100.0 else size))
    val at = position.filterNot(_.isNaN).getOrElse(aligned match {
      case "left" => 0.0
      case "right" => 100.0
      case _ => 50.0
    })
    val anchor = positionAlign.toLowerCase match {
      case "line-left" | "start" | "left" => "left"
      case "line-right" | "end" | "right" => "right"
      case "center" | "middle" => "center"
      case _ => aligned
    }
    val from = anchor match {
      case "left" => at
      case "right" => at - wide
      case _ => at - wide / 2.0
    }
    // kept within the safe margin
    val width = math.min(wide, 100.0 - 2.0 * margin)
    val left = math.max(margin, math.min(100.0 - margin - width, from))
    val across = CuePlace(left = left, width = width, align = aligned)
    line.filterNot(_.isNaN) match {
      case None =>
        if (keypad >= 7) across.copy(edge = Top)
        else if (keypad >= 4) across.copy(percent = 50.0, shift = 50.0)
        else across
      case Some(n) if snapToLines =>
        if (n >= 0.0) across.copy(edge = Top, lines = n.toInt)
        else across.copy(lines = (-n).toInt - 1)
      case Some(p) =>
        across.copy(percent = math.max(0.0, math.min(100.0, p)), shift = lineAlign.toLowerCase match {
          case "center" | "middle" => 50.0
          case "end" => 100.0
          case _ => 0.0
        })
    }
  }
}
