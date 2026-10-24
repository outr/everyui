package everyui.markdown

/** Where words name somebody: "@" and one of the names that count (a name may hold spaces: "@Matt Hicks"), the "@" at
  * the start of the words or after anything but a letter or a digit (not an email address), and the name ending the
  * words or followed by anything but a letter or a digit. A name is matched whatever its case, the longest that fits
  * first, so "@Matt Hicks" is not taken for "@Matt". Where the app answers a press on one, it is drawn as a link to the
  * name (`href`), pressed as a link is. */
object Mentions {
  /** `text` cut into its stretches, each with whether it is a mention ("@" and the name, as they were written). */
  def split(text: String, names: Set[String]): List[(String, Boolean)] =
    if (names.isEmpty || text.indexOf('@') < 0) List((text, false))
    else {
      val wanted = names.toList.filter(_.nonEmpty).map(_.toLowerCase).sortBy(n => -n.length)
      val lower = text.toLowerCase
      var out: List[(String, Boolean)] = Nil
      var from = 0
      var i = text.indexOf('@')
      while (i >= 0) {
        val length = if (i > 0 && word(text.charAt(i - 1))) 0 else named(lower, i + 1, wanted)
        if (length > 0) {
          if (i > from) out = (text.substring(from, i), false) :: out
          out = (text.substring(i, i + 1 + length), true) :: out
          from = i + 1 + length
          i = text.indexOf('@', from)
        } else i = text.indexOf('@', i + 1)
      }
      if (from < text.length) out = (text.substring(from), false) :: out
      out.reverse
    }

  /** Whether `text` names anybody in `names`. */
  def any(text: String, names: Set[String]): Boolean = split(text, names).exists(_._2)

  /** The one of `names` that `mention` ("@" and a name, as it was written) is, as the names have it. */
  def nameOf(mention: String, names: Set[String]): Option[String] = {
    val wanted = (if (mention.startsWith("@")) mention.substring(1) else mention).toLowerCase
    names.find(_.toLowerCase == wanted)
  }

  /** What a mention of `name` that can be pressed is a link to (`everyui.component.RichText.action`): the app's to
    * answer. */
  def href(name: String): String = everyui.component.RichText.action + "mention:" + name

  /** The name a mention's link (`href`) is to, where `link` is one. */
  def pressed(link: String): Option[String] = {
    val start = everyui.component.RichText.action + "mention:"
    if (link.startsWith(start)) Some(link.substring(start.length)) else None
  }

  private def word(c: Char): Boolean = c.isLetterOrDigit

  // how long the longest of `names` at `at` in `lower` is, where it ends a word; 0 for none
  private def named(lower: String, at: Int, names: List[String]): Int =
    names.find { n =>
      val end = at + n.length
      end <= lower.length && lower.substring(at, end) == n && (end == lower.length || !word(lower.charAt(end)))
    }.map(_.length).getOrElse(0)
}
