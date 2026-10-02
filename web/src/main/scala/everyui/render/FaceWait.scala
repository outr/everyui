package everyui.render

/** Which faces words wait for before they are measured (TextFit), worked out from what the page's faces say of
  * themselves: plain Scala, so a spec runs it on the JVM. Words wait for the faces of their family and slant in the
  * weight the browser draws them in (`matched`), that are loading, or not yet asked for and meant for any letter or
  * for one of theirs (a slice of a face by script, a build's), and never for one asked for once already (`tried`):
  * the browser answers a load of words in a weight with the face it takes for them, so another (a heavier one the
  * page declared for later) would stay unloaded, be asked for again, be answered at once, and keep the page from ever
  * drawing again. */
object FaceWait {
  /** The faces of `faces` (one family and slant: their weight, status and `unicode-range` as `document.fonts` says
    * them) that words of `text` in `wanted` (a CSS weight) wait for. */
  def waiting[F](faces: Seq[F], wanted: String, text: String, tried: Seq[F])
                (weightOf: F => String, status: F => String, range: F => String): Seq[F] = {
    val drawn = matched(weight(wanted), faces.map(f => weight(weightOf(f))).distinct)
    faces.filter { f =>
      drawn.contains(weight(weightOf(f))) && !tried.contains(f) && (status(f) == "loading" ||
        (status(f) == "unloaded" && (range(f).equalsIgnoreCase("U+0-10FFFF") || holds(range(f), text))))
    }
  }

  /** The weight of `weights` a browser draws `wanted` in (CSS Fonts' matching): `wanted` itself; for 400 to 500 the
    * heavier up to 500, then the lighter, nearest first, then the heavier past 500; below 400 the lighter, then the
    * heavier; above 500 the heavier, then the lighter. */
  def matched(wanted: Int, weights: Seq[Int]): Option[Int] = {
    val heavier = weights.filter(_ > wanted).sorted
    val lighter = weights.filter(_ < wanted).sorted.reverse
    val order =
      if (wanted >= 400 && wanted <= 500) heavier.filter(_ <= 500) ++ lighter ++ heavier.filter(_ > 500)
      else if (wanted < 400) lighter ++ heavier
      else heavier ++ lighter
    if (weights.contains(wanted)) Some(wanted) else order.headOption
  }

  /** A CSS weight as a number: `bold` 700, `normal` (or what is not a number) 400. */
  def weight(css: String): Int = css.trim match {
    case "bold" => 700
    case w => w.toIntOption.getOrElse(400)
  }

  /** Whether a `unicode-range` (`U+0-FF, U+131, U+4??`) holds one of `text`'s characters. */
  def holds(range: String, text: String): Boolean = {
    def hex(s: String, fill: Char): Int = java.lang.Integer.parseInt(s.replace('?', fill), 16)
    val runs = range.split(",").toList.map(_.trim.toUpperCase.stripPrefix("U+")).filter(_.nonEmpty).map { p =>
      val dash = p.indexOf('-')
      if (dash > 0) (hex(p.substring(0, dash), '0'), hex(p.substring(dash + 1), 'F')) else (hex(p, '0'), hex(p, 'F'))
    }
    var i = 0
    var found = false
    while (!found && i < text.length) {
      val c = text.codePointAt(i)
      found = runs.exists(r => c >= r._1 && c <= r._2)
      i += Character.charCount(c)
    }
    found
  }
}
