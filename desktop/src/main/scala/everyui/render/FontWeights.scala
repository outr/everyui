package everyui.render

import javafx.scene.text.{Font, FontPosture, FontWeight}

import scala.collection.mutable

/** A weight in an installed family, the system's among them. JavaFX's `Font.font` weighs a family as bold or not, so a
  * semibold 600 asked of it came back regular: `Font.font("System", FontWeight.SEMI_BOLD, size)` is System Regular. A
  * weight of 600 or more is drawn here in the family's lightest face at or above it, found by its name (JavaFX lists a
  * semibold face apart, as "Noto Sans SemiBold" beside "Noto Sans", or within the family, as "System Bold"), and in
  * its bold where it has none that heavy, as CSS chooses for a weight above 500. Lighter weights are drawn regular, as
  * before. A bundled face (Faces) is chosen by weight in shared code and loaded by Host.font, not here.
  *
  * {{{
  *   FontWeights("System", 600, italic = false, 16.0)   // System Bold where the system family has no semibold
  * }}} */
object FontWeights {
  // the words a face's name gives its weight by, each before any it contains ("semibold" before "bold")
  private val named: List[(String, Int)] = List("hairline" -> 100, "thin" -> 100, "extralight" -> 200,
    "ultralight" -> 200, "light" -> 300, "medium" -> 500, "semibold" -> 600, "demibold" -> 600, "extrabold" -> 800,
    "ultrabold" -> 800, "bold" -> 700, "black" -> 900, "heavy" -> 900)

  // a family's faces, looked up once (on the FX thread)
  private val faces = mutable.Map.empty[String, List[(String, Int, Boolean)]]

  /** `family` at CSS `weight`, upright or `italic`, at `size`. */
  def apply(family: String, weight: Int, italic: Boolean, size: Double): Font = {
    val posture = if (italic) FontPosture.ITALIC else FontPosture.REGULAR
    if (weight < 600) Font.font(family, FontWeight.NORMAL, posture, size)
    else of(family).filter { case (_, w, i) => i == italic && w >= weight }.sortBy(_._2).headOption match {
      case Some((name, _, _)) => new Font(name, size)
      case None => Font.font(family, FontWeight.BOLD, posture, size)
    }
  }

  /** Looks the families up afresh: a font file was loaded, which may have added a face to one. */
  def forget(): Unit = faces.clear()

  /** The weight a face's style ("SemiBold Italic") gives it: 400 where it names none. */
  def weightOf(style: String): Int = {
    val letters = style.toLowerCase.filter(_.isLetter)
    named.find { case (word, _) => letters.contains(word) }.map(_._2).getOrElse(400)
  }

  // the faces of `family` and of the families named for one of its weights ("Noto Sans Medium"): name, weight, italic
  private def of(family: String): List[(String, Int, Boolean)] = faces.getOrElseUpdate(family, {
    val prefix = family + " "
    val families = Font.getFamilies.toArray.toList.map(_.toString).filter(f => f == family ||
      (f.startsWith(prefix) && f.drop(prefix.length).split(' ').forall(w => named.exists(_._1 == w.toLowerCase))))
    families.flatMap(f => Font.getFontNames(f).toArray.toList.map(_.toString)).distinct.map { name =>
      val style = name.stripPrefix(family).toLowerCase
      (name, weightOf(style), style.contains("italic") || style.contains("oblique"))
    }
  })
}
