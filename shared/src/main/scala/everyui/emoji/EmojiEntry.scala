package everyui.emoji

/** An emoji as `Emoji` knows it: the emoji itself, the names it is written by (`:tada:`, without the colons, the first
  * its own), the words it is found by in a search, its category (an index into `Emoji.categories`), and whether it
  * takes a skin tone (`Emoji.withTone`). */
case class EmojiEntry(emoji: String, shortcodes: List[String], words: List[String], category: Int, skinTones: Boolean)
