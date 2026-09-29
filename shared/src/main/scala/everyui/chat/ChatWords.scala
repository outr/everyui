package everyui.chat

import everyui.i18n.Words

/** What the chat components say, in the language in use: an app gives its translations with `Words.add` under these
  * keys ("chat.jumpToLatest", …), and English is said where none is given. */
object ChatWords {
  private def word(key: String, english: String): String = {
    val said = Words(key)
    if (said == key) english else said
  }

  def conversation: String = word("chat.conversation", "Conversation")
  def jumpToLatest: String = word("chat.jumpToLatest", "Jump to latest")
  /** "3 new" below the latest. */
  def newCount(n: Int): String = word("chat.newCount", "{n} new").replace("{n}", n.toString)
  /** "2 working", over the composer. */
  def workingCount(n: Int): String = word("chat.workingCount", "{n} working").replace("{n}", n.toString)
  def now: String = word("chat.now", "now")
  /** "5m" ago. */
  def minutesAgo(n: Long): String = word("chat.minutesAgo", "{n}m").replace("{n}", n.toString)
  /** "3h" ago. */
  def hoursAgo(n: Long): String = word("chat.hoursAgo", "{n}h").replace("{n}", n.toString)
  /** "2d" ago. */
  def daysAgo(n: Long): String = word("chat.daysAgo", "{n}d").replace("{n}", n.toString)
  def today: String = word("chat.today", "Today")
  def yesterday: String = word("chat.yesterday", "Yesterday")
  def thinking: String = word("chat.thinking", "Thinking")
  /** "Thought for 12s". */
  def thoughtFor(took: String): String = word("chat.thoughtFor", "Thought for {t}").replace("{t}", took)
  def input: String = word("chat.input", "Input")
  def output: String = word("chat.output", "Output")
  def running: String = word("chat.running", "running")
  def succeeded: String = word("chat.succeeded", "done")
  def failed: String = word("chat.failed", "failed")
  def connecting: String = word("chat.connecting", "Connecting…")
  def offline: String = word("chat.offline", "Offline")
  def cancel: String = Words.cancel
  def retry: String = Words.retry
  def loadingOlder: String = word("chat.loadingOlder", "Loading earlier messages")
  def show: String = word("chat.show", "Show")
  def hide: String = word("chat.hide", "Hide")
}
