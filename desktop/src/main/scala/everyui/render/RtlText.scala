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
  def lines(text: String, font: Font, width: Double): String = {
    probe.setFont(font)
    // a hair inside the width, so JavaFX never finds a line it has to wrap again
    val room = width - 0.5
    text.split("\n", -1).map(paragraph => wrap(paragraph, room)).mkString("\n")
  }

  private def across(s: String): Double = {
    probe.setText(s)
    probe.getLayoutBounds.getWidth
  }

  private def wrap(paragraph: String, room: Double): String = {
    val out = new StringBuilder
    var line = ""
    paragraph.split(" ", -1).foreach { word =>
      val tried = if (line.isEmpty) word else line + " " + word
      if (across(tried) <= room || tried.isEmpty) line = tried
      else {
        if (line.nonEmpty) out.append(line).append('\n')
        line = word
        // a word wider than the line: as much of it as fits, a line at a time
        while (line.length > 1 && across(line) > room) {
          var fits = 1
          while (fits < line.length && across(line.substring(0, fits + 1)) <= room) fits += 1
          out.append(line.substring(0, fits)).append('\n')
          line = line.substring(fits)
        }
      }
    }
    out.append(line).toString
  }
}
