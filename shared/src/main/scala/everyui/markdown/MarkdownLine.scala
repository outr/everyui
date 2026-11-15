package everyui.markdown

import everyui.component.{Link, RichText}
import everyui.text.TextRun

/** Inline Markdown drawn on one line, as a tool call's summary is written ("Read `src/A.scala`"): code spans in the
  * monospaced face on their wash, strong words bold and emphasis slanted, as a message's words are drawn
  * (`MarkdownRuns`). A link is drawn as its words, in the line's own colour, since what shows the line is pressed as a
  * whole; a picture is its alt text; what would start a block (a heading's `#`, a list's `-`, a fence) is the words it
  * is; a line break is a space.
  *
  * Where the line is wider than its room it is cut with an ellipsis (`fit`), the ellipsis in the style of what was cut:
  * a code span cut short keeps its wash, the ellipsis inside it, rather than ending on a dangling mark. */
object MarkdownLine {
  /** The runs of `source` at `points`, on one line. */
  def runs(source: String, points: Double): List[TextRun] =
    if (source.isEmpty) Nil
    else {
      val parts = MarkdownRuns.parts(Inlines.parse(source, Map.empty), points, 0)
      val joined = parts.zipWithIndex.flatMap { case (part, i) =>
        val gap = if (i == 0) Nil else List(TextRun(" "))
        gap ++ part.image.map(p => List(TextRun(p.alt))).getOrElse(part.runs)
      }
      joined.map(r => if (r.href.isEmpty) r else r.copy(href = "", title = "", color = "", underline = false))
        .filter(r => r.text.nonEmpty || r.image.nonEmpty)
    }

  /** The words of `runs` without their marks: what is shown in a tip for the line. */
  def plain(runs: List[TextRun]): String = runs.map(_.text).mkString

  /** The words of `source` without their marks, as a screen reader is given them. */
  def words(source: String): String = plain(runs(source, 0.0))

  /** `runs` cut to their first `letters` letters, an ellipsis after them in the style of the run the cut falls in (the
    * first one left out, where it falls between two): spaces before it go, a letter and its pair are never parted,
    * and a picture is kept whole or left out. As they are where they have no more letters than that. */
  def cut(runs: List[TextRun], letters: Int): List[TextRun] = {
    var left = letters
    var out: List[TextRun] = Nil
    var rest = runs
    var ended = false
    while (!ended && rest.nonEmpty) {
      val r = rest.head
      rest = rest.tail
      val n = r.text.length
      if (left >= n) {
        out = r :: out
        left -= n
      }
      else {
        var k = left
        if (k > 0 && r.image.isEmpty && r.text.charAt(k - 1) >= '\uD800' && r.text.charAt(k - 1) <= '\uDBFF') k -= 1
        // (spaces before the ellipsis go)
        while (k > 0 && r.image.isEmpty && r.text.charAt(k - 1).isWhitespace) k -= 1
        val kept = if (r.image.nonEmpty) "" else r.text.substring(0, k)
        out = r.copy(text = kept + "…", image = "") :: out
        ended = true
      }
    }
    out.reverse
  }

  /** `runs` as they fit `room` on one line: whole where they measure `whole` within it, else cut (`cut`) to the most
    * letters that fit, as `probe` (drawn just as the line is, on one line) measures each try; the probe is given back
    * its own runs after. */
  def fit(probe: RichText, runs: List[TextRun], whole: Double, room: Double): List[TextRun] =
    if (runs.isEmpty || room <= 0.0 || whole <= room + 0.5) runs
    else {
      val node = probe.node
      def wide(n: Int): Double = {
        node.setRuns(TextRun.merged(cut(runs, n)), _ => ())
        node.getWidth()
      }
      // the most letters kept, found in as many measures as halvings, from a first guess at the share that fits
      val total = runs.map(_.text.length).sum
      var fewest = 0
      var most = total - 1
      val guess = math.max(0, math.min(most, (total * room / whole).toInt))
      if (wide(guess) <= room + 0.5) fewest = guess else most = guess - 1
      while (fewest < most) {
        val half = (fewest + most + 1) / 2
        if (wide(half) <= room + 0.5) fewest = half else most = half - 1
      }
      node.setRuns(TextRun.merged(probe.runs().map(Link.titled)), href => probe.follow(href))
      cut(runs, fewest)
    }
}
