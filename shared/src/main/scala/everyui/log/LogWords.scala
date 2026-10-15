package everyui.log

import everyui.i18n.{Icu, Words}

/** What the log components say, in the language in use (Words' eleven languages): an app gives its own wording
  * or another language with `Words.add` under these keys ("log.jumpToLatest", …). */
object LogWords {
  private def word(key: String): String = Words(key)
  /** A message with its counts put in, plural where the language says. */
  private def said(key: String, args: Map[String, String]): String = Icu.format(Words(key), args)

  def log: String = word("log.log")
  def jumpToLatest: String = word("log.jumpToLatest")
  /** "12 new" below the latest. */
  def newCount(n: Int): String = said("log.newCount", Map("n" -> n.toString))
  /** "3 of 41". */
  def matchOf(at: Int, total: Int): String = said("log.matchOf", Map("at" -> at.toString, "total" -> total.toString))
  def noMatches: String = word("log.noMatches")
  def badPattern: String = word("log.badPattern")
  /** "1,204 hidden" by the filter. */
  def hidden(n: Int): String = said("log.hidden", Map("n" -> n.toString))
  /** "… 12 lines missed", where a reconnect could not fill a gap. */
  def missed(n: Long): String = if (n == 1L) word("log.missedOne") else said("log.missed", Map("n" -> n.toString))
  def restarted: String = word("log.restarted")
  def search: String = word("log.search")
  def filter: String = word("log.filter")
  def previous: String = word("log.previous")
  def next: String = word("log.next")
  def matchCase: String = word("log.matchCase")
  def regex: String = word("log.regex")
  def wrap: String = word("log.wrap")
  def follow: String = word("log.follow")
  def clear: String = word("log.clear")
  def copyLine: String = word("log.copyLine")
  /** "Copy 3 lines". */
  def copyLines(n: Int): String = said("log.copyLines", Map("n" -> n.toString))
  def selectAll: String = word("log.selectAll")
}
