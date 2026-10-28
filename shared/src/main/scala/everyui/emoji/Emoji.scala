package everyui.emoji

/** The emoji people write by name: `:tada:` is 🎉, `:+1:` 👍, `:white_check_mark:` ✅, by the names GitHub and Slack
  * know them by (GitHub's table: `EmojiData`), with Slack's `:skin-tone-2:` to `:skin-tone-6:` after one that takes a
  * skin tone. What a composer turns a name into as its closing colon is typed (`everyui.chat.Composer.emojiShortcodes`),
  * what a picker shows and searches (`EmojiPicker`), and what an app turns words it is given into: an agent's
  * `:white_check_mark:` (`convert`, which leaves code alone).
  *
  * {{{
  *   Emoji.convert("Shipped :tada: — see `:not_this:`")   // "Shipped 🎉 — see `:not_this:`"
  *   Emoji.shortcode("👍")                                // Some("+1")
  *   Emoji.search("party").map(_.emoji)                   // 🎉, 🥳, 🎊 …
  * }}} */
object Emoji {
  /** Every emoji, in its categories' order. */
  lazy val all: Vector[EmojiEntry] = EmojiData.categories.indices.toVector.flatMap { c =>
    EmojiData.lines(c).flatMap { line =>
      val parts = line.split("\t", -1)
      if (parts.length < 4) Vector.empty
      else Vector(EmojiEntry(parts(0), parts(1).split(",").toList.filter(_.nonEmpty),
        parts(2).split(" ").toList.filter(_.nonEmpty), c, parts(3) == "1"))
    }
  }

  /** The categories' names, in order ("Smileys & Emotion", "People & Body", … "Flags"). */
  def categories: Vector[String] = EmojiData.categories

  // each shortcode's emoji, and each emoji's first shortcode (pairs made into maps in one go: a Roku's map copies itself
  // for each key added one at a time)
  private lazy val byCode: Map[String, EmojiEntry] = all.flatMap(e => e.shortcodes.map(_ -> e)).toMap
  private lazy val byEmoji: Map[String, EmojiEntry] = all.reverse.map(e => bare(e.emoji) -> e).toMap

  /** The emoji `code` names (without its colons, in any case): `"tada"` is 🎉. */
  def lookup(code: String): Option[String] = byCode.get(code.toLowerCase).map(_.emoji)

  /** The entry for `emoji`, whatever its skin tone. */
  def entry(emoji: String): Option[EmojiEntry] = byEmoji.get(bare(emoji))

  /** The first name `emoji` is known by, without its colons: `"👍"` is `"+1"`. Its skin tone, where it has one, is
    * not in the name. */
  def shortcode(emoji: String): Option[String] = entry(emoji).flatMap(_.shortcodes.headOption)

  /** The emoji whose names or words start with what `query` holds, each of its words, best first: a name that is the
    * query, then names that start with it, then words that do; at most `limit`. Empty for no query. */
  def search(query: String, limit: Int = 60): Vector[EmojiEntry] = {
    val words = query.trim.toLowerCase.split("[\\s:]+").toVector.filter(_.nonEmpty)
    if (words.isEmpty) Vector.empty
    else {
      def names(e: EmojiEntry): List[String] = e.shortcodes.flatMap(n => n :: n.split("[_-]").toList)
      def all(e: EmojiEntry, w: String): Boolean = names(e).exists(_.startsWith(w)) || e.words.exists(_.startsWith(w))
      val found = Emoji.all.filter(e => words.forall(w => all(e, w)))
      val first = words.head
      def rank(e: EmojiEntry): Int =
        if (e.shortcodes.contains(first)) 0
        else if (e.shortcodes.exists(_.startsWith(first))) 1
        else if (names(e).exists(_.startsWith(first))) 2
        else 3
      found.sortBy(rank).take(limit)
    }
  }

  /** The skin tones, lightest first, as their modifiers (U+1F3FB to U+1F3FF). */
  val tones: Vector[String] = Vector("🏻", "🏼", "🏽", "🏾", "🏿")

  /** `emoji` in skin tone `tone` (1, the lightest, to 5; 0 for none), where it takes one: the tone after its first
    * character, in place of the variation selector that may follow it. */
  def withTone(emoji: String, tone: Int): String = {
    val plain = untoned(emoji)
    if (tone < 1 || tone > 5 || !entry(plain).exists(_.skinTones)) plain
    else {
      val first = plain.offsetByCodePoints(0, 1)
      val rest = plain.substring(first)
      plain.substring(0, first) + tones(tone - 1) + (if (rest.startsWith("️")) rest.substring(1) else rest)
    }
  }

  /** `text` with every `:name:` that names an emoji turned into it (`:skin-tone-2:` after one giving its tone), but in
    * code: a span between backticks, or a block between lines of three backticks or tildes. A name must stand apart
    * from the words around it (`12:30:45` and `http://a:b:c` stay as they are). */
  def convert(text: String): String = if (text.indexOf(':') < 0) text else {
    val out = new StringBuilder
    var i = 0
    var fenced = false
    var lineStart = true
    while (i < text.length) {
      val c = text.charAt(i)
      if (lineStart && (text.startsWith("```", i) || text.startsWith("~~~", i))) {
        // a fence's line, as it is
        val end = text.indexOf('\n', i)
        val stop = if (end < 0) text.length else end + 1
        out.append(text.substring(i, stop))
        fenced = !fenced
        i = stop
        lineStart = true
      }
      else if (fenced) {
        out.append(c)
        lineStart = c == '\n'
        i += 1
      }
      else if (c == '`') {
        // a code span, as it is: to the same run of backticks
        var run = 0
        while (i + run < text.length && text.charAt(i + run) == '`') run += 1
        val ticks = "`" * run
        val close = text.indexOf(ticks, i + run)
        val stop = if (close < 0) i + run else close + run
        out.append(text.substring(i, stop))
        lineStart = false
        i = stop
      }
      else if (c == ':' && (i == 0 || !wordy(text.charAt(i - 1)))) {
        named(text, i) match {
          case Some((emoji, end)) =>
            // a tone after it, as Slack writes one
            val toned = tonedAt(text, end)
            out.append(toned.map(t => withTone(emoji, t._1)).getOrElse(emoji))
            i = toned.map(_._2).getOrElse(end)
          case None =>
            out.append(c)
            i += 1
        }
        lineStart = false
      }
      else {
        out.append(c)
        lineStart = c == '\n'
        i += 1
      }
    }
    out.toString
  }

  /** The emoji the `:name:` starting at `at` names and where it ends, where it is one standing apart. */
  private[everyui] def named(text: String, at: Int): Option[(String, Int)] = {
    var j = at + 1
    while (j < text.length && nameChar(text.charAt(j))) j += 1
    if (j >= text.length || j == at + 1 || text.charAt(j) != ':') None
    else if (j + 1 < text.length && wordy(text.charAt(j + 1))) None
    else lookup(text.substring(at + 1, j)).map(e => (e, j + 1))
  }

  // Slack's `:skin-tone-2:` (lightest) to `:skin-tone-6:` at `at`: the tone (1 to 5) and where it ends
  private def tonedAt(text: String, at: Int): Option[(Int, Int)] = {
    val mark = ":skin-tone-"
    if (!text.startsWith(mark, at) || at + mark.length + 2 > text.length) None
    else {
      val digit = text.charAt(at + mark.length)
      if (digit >= '2' && digit <= '6' && text.charAt(at + mark.length + 1) == ':') Some((digit - '1', at + mark.length + 2))
      else None
    }
  }

  /** Whether `c` can be in a name: a letter, a digit, `_`, `+` or `-`. */
  private[everyui] def nameChar(c: Char): Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
    (c >= '0' && c <= '9') || c == '_' || c == '+' || c == '-'
  // a letter or a digit: what a name may not touch, so a time or an address is not taken for one
  private def wordy(c: Char): Boolean = c.isLetterOrDigit

  // `emoji` without a skin tone
  private def untoned(emoji: String): String = tones.foldLeft(emoji)((e, t) => e.replace(t, ""))
  // `emoji` without a skin tone or a variation selector: what it is found by
  private def bare(emoji: String): String = untoned(emoji).replace("️", "")
}
