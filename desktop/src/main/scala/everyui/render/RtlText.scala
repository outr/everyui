package everyui.render

import everyui.text.Bidi
import javafx.scene.text.{Font, FontPosture, FontWeight, Text}

/** Words with right-to-left letters in them (Arabic, Hebrew) set here as JavaFX can draw them, around two of its
  * faults (JavaFX 22 to 28 at least):
  *
  *  - a line of them in a face that has not got their letters (the bundled Open Sans), drawn with the platform's
  *    fallback face, comes out in the wrong order: the runs of each face are put in order apart, so the full stops and
  *    spaces (the face's own) land at the wrong end and sentences change places. They are drawn in a face that has
  *    Latin and Arabic and Hebrew letters alike (`faces`, the first the system has) instead, as a browser would fall
  *    back to one.
  *  - a `Text` or a `TextFlow` whose wrap falls inside a right-to-left run throws from its layout
  *    (`PrismTextLayout.computeTrailingSpaceWidth` reads before the start of its text), which JavaFX 21 did not. The
  *    lines are broken here, at spaces where they fit, measured in the face they are drawn in (a word wider than a line
  *    between its letters, as JavaFX breaks one), and handed over with their line breaks, so JavaFX wraps nothing.
  *
  * Words with no right-to-left letter are left to JavaFX, in their own face. */
object RtlText {
  /** The faces tried for right-to-left words, in order: each has Latin, Arabic and Hebrew letters. */
  val faces: List[String] = List("Segoe UI", "DejaVu Sans", "Arial", "Noto Sans Arabic UI", "Noto Sans Arabic")

  private val wordsKey = "everyui.words"
  private val faceKey = "everyui.words.face"
  private val watched = "everyui.words.watched"
  private lazy val probe = new Text()
  private lazy val face: Option[String] = {
    val installed = Font.getFamilies
    faces.find(installed.contains)
  }

  /** `words` shown in `t`: in a face that draws them in order and broken into lines at its wrapping width where they
    * have right-to-left letters, again as that width or its face changes. */
  def lay(t: Text, words: String): Unit = {
    t.getProperties.put(wordsKey, words)
    val rtl = Bidi.anyRightToLeft(words)
    if (rtl && !t.getProperties.containsKey(watched)) {
      t.getProperties.put(watched, true)
      t.wrappingWidthProperty.addListener((_, _, _) => relay(t))
      t.fontProperty.addListener((_, _, _) => relay(t))
    }
    val own = ownFont(t)
    val drawn = if (rtl) covering(own) else own
    if (t.getFont != drawn) t.setFont(drawn)
    val width = t.getWrappingWidth
    t.setText(if (rtl && width > 0.0) lines(words, drawn, width) else words)
  }

  /** `t`'s words laid again, for a new width or face. */
  def relay(t: Text): Unit = lay(t, words(t))

  /** The words `t` was given, without the line breaks put in here. */
  def words(t: Text): String = t.getProperties.get(wordsKey) match {
    case s: String => s
    case _ => t.getText
  }

  /** `font` in a face that draws right-to-left words in order, as heavy and as slanted, where the system has one. */
  def covering(font: Font): Font = face match {
    case Some(family) if font.getFamily != family =>
      val style = font.getStyle.toLowerCase
      val weight = if (style.contains("black") || style.contains("heavy")) FontWeight.BLACK
        else if (style.contains("semi") || style.contains("demi")) FontWeight.SEMI_BOLD
        else if (style.contains("bold")) FontWeight.BOLD
        else if (style.contains("medium")) FontWeight.MEDIUM
        else if (style.contains("light")) FontWeight.LIGHT
        else FontWeight.NORMAL
      val posture = if (style.contains("italic") || style.contains("oblique")) FontPosture.ITALIC else FontPosture.REGULAR
      Font.font(family, weight, posture, font.getSize)
    case _ => font
  }

  // the face the node was given (`setFont`), which the one drawn right-to-left words stands in for
  private def ownFont(t: Text): Font = {
    val now = t.getFont
    t.getProperties.get(faceKey) match {
      case (set: Font, standIn: Font) if standIn == now => set
      case _ =>
        t.getProperties.put(faceKey, (now, covering(now)))
        now
    }
  }

  /** `text` with a line break wherever the next word would not fit `width` in `font`; its own line breaks kept. */
  def lines(text: String, font: Font, width: Double): String =
    broken(text, width, (from, to) => across(font, text.substring(from, to)))

  /** The words of `pieces`, each drawn in its own face, with a line break wherever the next word would not fit `width`:
    * a paragraph of styled runs (`RichFlow`), broken as one. */
  def lines(pieces: List[(String, Font)], width: Double): List[String] = {
    val text = pieces.map(_._1).mkString
    val starts = pieces.scanLeft(0)((at, p) => at + p._1.length)
    val widths = (from: Int, to: Int) => pieces.indices.foldLeft(0.0) { (w, i) =>
      val a = math.max(from, starts(i))
      val b = math.min(to, starts(i + 1))
      if (a < b) w + across(pieces(i)._2, text.substring(a, b)) else w
    }
    val laid = broken(text, width, widths)
    // back into the pieces: a space the break took, or a break put in between letters, in the piece it falls in
    val out = pieces.map(_ => new StringBuilder).toArray
    var piece = 0
    var i = 0
    var j = 0
    while (j < laid.length) {
      while (piece < pieces.size - 1 && i >= starts(piece + 1)) piece += 1
      out(piece).append(laid.charAt(j))
      val put = laid.charAt(j) == '\n' && i < text.length && text.charAt(i) != '\n' && text.charAt(i) != ' '
      if (put) j += 1
      else {
        i += 1
        j += 1
      }
    }
    out.toList.map(_.toString)
  }

  /** How wide `s` is in `font`. */
  def across(font: Font, s: String): Double = {
    probe.setFont(font)
    probe.setText(s)
    probe.getLayoutBounds.getWidth
  }

  // `text` with a line break wherever the next word would not fit `width` as `widths` measures from one index to another
  private def broken(text: String, width: Double, widths: (Int, Int) => Double): String = {
    // a hair inside the width, so JavaFX never finds a line it has to wrap again
    val room = width - 1.0
    val out = new StringBuilder
    var from = 0
    text.split("\n", -1).foreach { paragraph =>
      if (from > 0) out.append('\n')
      wrap(text, from, from + paragraph.length, room, widths, out)
      from += paragraph.length + 1
    }
    out.toString
  }

  // the paragraph of `text` from `start` to `end` into `out`, broken at spaces (a word wider than a line between its
  // letters)
  private def wrap(text: String, start: Int, end: Int, room: Double, widths: (Int, Int) => Double,
                   out: StringBuilder): Unit = {
    var line = start
    var i = start
    while (i < end) {
      var j = i
      while (j < end && text.charAt(j) == ' ') j += 1
      while (j < end && text.charAt(j) != ' ') j += 1
      if (widths(line, j) <= room) i = j
      else if (i > line) {
        // the space before the word becomes the break
        out.append(text.substring(line, i)).append('\n')
        line = i + 1
        i = line
      }
      else {
        // a word wider than the line: as much of it as fits
        var k = line + 1
        while (k < j && widths(line, k + 1) <= room) k += 1
        out.append(text.substring(line, k)).append('\n')
        line = k
        i = k
      }
    }
    out.append(text.substring(line, end))
  }
}
