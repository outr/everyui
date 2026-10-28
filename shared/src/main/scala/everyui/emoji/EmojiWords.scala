package everyui.emoji

import everyui.i18n.Words

/** What an emoji picker and a message's reactions say, in the language in use (Words' languages): an app gives its own
  * wording under these keys ("emoji.search", …). */
object EmojiWords {
  private def word(key: String): String = Words(key)

  def search: String = word("emoji.search")
  def recent: String = word("emoji.recent")
  def skinTone: String = word("emoji.skinTone")
  /** The composer's button that opens the picker, to a screen reader and as its tip. */
  def emoji: String = word("emoji.emoji")
  def addReaction: String = word("emoji.addReaction")
  def none: String = word("emoji.none")
  /** A category's name, by its index in `Emoji.categories`. */
  def category(i: Int): String = word("emoji.category" + i.toString)
}
