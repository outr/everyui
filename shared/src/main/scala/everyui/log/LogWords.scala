package everyui.log

import everyui.i18n.Words

/** What the log components say, in the language in use: an app gives its translations with `Words.add` under these
  * keys ("log.jumpToLatest", …), and English is said where none is given. */
object LogWords {
  private def word(key: String, english: String): String = {
    val said = Words(key)
    if (said == key) english else said
  }

  def log: String = word("log.log", "Log")
  def jumpToLatest: String = word("log.jumpToLatest", "Jump to latest")
  /** "12 new" below the latest. */
  def newCount(n: Int): String = word("log.newCount", "{n} new").replace("{n}", n.toString)
  /** "3 of 41". */
  def matchOf(at: Int, total: Int): String =
    word("log.matchOf", "{at} of {total}").replace("{at}", at.toString).replace("{total}", total.toString)
  def noMatches: String = word("log.noMatches", "No matches")
  def badPattern: String = word("log.badPattern", "Bad pattern")
  /** "1,204 hidden" by the filter. */
  def hidden(n: Int): String = word("log.hidden", "{n} hidden").replace("{n}", n.toString)
  /** "… 12 lines missed", where a reconnect could not fill a gap. */
  def missed(n: Long): String =
    if (n == 1L) word("log.missedOne", "… 1 line missed")
    else word("log.missed", "… {n} lines missed").replace("{n}", n.toString)
  def restarted: String = word("log.restarted", "… the log started again")
  def search: String = word("log.search", "Search")
  def filter: String = word("log.filter", "Filter")
  def previous: String = word("log.previous", "Previous match")
  def next: String = word("log.next", "Next match")
  def matchCase: String = word("log.matchCase", "Match case")
  def regex: String = word("log.regex", "Regular expression")
  def wrap: String = word("log.wrap", "Wrap lines")
  def follow: String = word("log.follow", "Follow the latest")
  def clear: String = word("log.clear", "Clear")
  def copyLine: String = word("log.copyLine", "Copy line")
  /** "Copy 3 lines". */
  def copyLines(n: Int): String = word("log.copyLines", "Copy {n} lines").replace("{n}", n.toString)
  def selectAll: String = word("log.selectAll", "Select all")
}
