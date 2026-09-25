package everyui.component

import everyui.text.TextRun

/** One subtitle on the screen as the file places and styles it, where the platform hands the words over rather than
  * drawing them (Android): its words as styled runs (italics, bold, a SubStation colour), and where it goes.
  *
  * Places are shares of the PICTURE, not the screen, as the file means them: `line` down from its top (a sign read at
  * the top of the frame, a SubStation `\an8`), `position` across from its left, each anchored at the cue's start,
  * middle or end; `size` is how wide its box may be, and `textSize` how tall its letters are, as a share of the
  * picture's height. Anything the file leaves to the player is -1: a cue with no `line` is an ordinary one at the
  * foot of the picture. `window` is the colour of the box behind it where the file names one ("" leaves it to the
  * app). */
case class MediaCue(runs: List[TextRun],
                    line: Double = -1.0,
                    lineAnchor: String = MediaCue.End,
                    position: Double = -1.0,
                    positionAnchor: String = MediaCue.Middle,
                    align: String = MediaCue.Middle,
                    size: Double = -1.0,
                    textSize: Double = -1.0,
                    window: String = "") {
  /** The words alone, as they are written. */
  def text: String = runs.map(_.text).mkString

  /** Whether the file placed it anywhere but the foot of the picture. */
  def placed: Boolean = line >= 0.0 && line < 0.75
}

object MediaCue {
  val Start: String = "start"
  val Middle: String = "middle"
  val End: String = "end"
}
