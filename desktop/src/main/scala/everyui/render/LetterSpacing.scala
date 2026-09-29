package everyui.render

import everyui.text.TextRun

/** The width letter spacing adds to words (`FontFeature.letterSpacing`), as a browser spaces them: `px` after each
  * letter (each grapheme, so an accented letter or an emoji is one), on the longest of their lines.
  *
  * JavaFX has no letter spacing: neither `Text` nor its CSS spaces letters, and a `TextFlow` of a `Text` per letter
  * would lose the face's kerning and cost a node a letter. So the desktop draws the letters as the face sets them and
  * takes the spaced width for the words' box, which keeps what is laid out around them where it is on the backends
  * that space them. */
object LetterSpacing {
  /** What `px` after each letter of `text` adds to its width. */
  def width(px: Double, text: String): Double =
    if (px == 0.0 || text.isEmpty) 0.0 else px * text.split("\n", -1).map(letters).max

  /** The same for styled runs, each spaced by its own spacing or the paragraph's `px`. */
  def width(px: Double, runs: List[TextRun]): Double =
    if (px == 0.0 && runs.forall(_.letterSpacing.isEmpty)) 0.0
    else {
      // runs on one line add up; a line break starts the sum again, and the longest line is the width
      var widest = 0.0
      var line = 0.0
      runs.foreach { run =>
        val spacing = run.letterSpacing.getOrElse(px)
        val lines = run.text.split("\n", -1)
        lines.indices.foreach { i =>
          if (i > 0) {
            widest = math.max(widest, line)
            line = 0.0
          }
          line += spacing * letters(lines(i))
        }
      }
      math.max(widest, line)
    }

  /** How many letters (graphemes) `text` has. */
  def letters(text: String): Int = {
    val it = java.text.BreakIterator.getCharacterInstance
    it.setText(text)
    var count = 0
    while (it.next() != java.text.BreakIterator.DONE) count += 1
    count
  }
}
