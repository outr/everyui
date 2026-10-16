package everyui.chat

import everyui.i18n.{Icu, Words}

/** What the chat components say, in the language in use (Words' eleven languages): an app gives its own wording
  * or another language with `Words.add` under these keys ("chat.jumpToLatest", …). */
object ChatWords {
  private def word(key: String): String = Words(key)
  /** A message with its counts and names put in, plural where the language says. */
  private def said(key: String, args: Map[String, String]): String = Icu.format(Words(key), args)

  def conversation: String = word("chat.conversation")
  def jumpToLatest: String = word("chat.jumpToLatest")
  /** "3 new" below the latest. */
  def newCount(n: Int): String = said("chat.newCount", Map("n" -> n.toString))
  /** "2 working", over the composer. */
  def workingCount(n: Int): String = said("chat.workingCount", Map("n" -> n.toString))
  def now: String = word("chat.now")
  /** "5m" ago. */
  def minutesAgo(n: Long): String = said("chat.minutesAgo", Map("n" -> n.toString))
  /** "3h" ago. */
  def hoursAgo(n: Long): String = said("chat.hoursAgo", Map("n" -> n.toString))
  /** "2d" ago. */
  def daysAgo(n: Long): String = said("chat.daysAgo", Map("n" -> n.toString))
  /** How long something took: "12s", "1m 9s", "2h 5m" (`ChatTime.span`). */
  def spanSeconds(s: Long): String = said("chat.spanSeconds", Map("s" -> s.toString))
  def spanMinutes(m: Long, s: Long): String = said("chat.spanMinutes", Map("m" -> m.toString, "s" -> s.toString))
  def spanHours(h: Long, m: Long): String = said("chat.spanHours", Map("h" -> h.toString, "m" -> m.toString))
  def today: String = word("chat.today")
  def yesterday: String = word("chat.yesterday")
  def thinking: String = word("chat.thinking")
  /** "Thought for 12s". */
  def thoughtFor(took: String): String = word("chat.thoughtFor").replace("{t}", took)
  def input: String = word("chat.input")
  def output: String = word("chat.output")
  def running: String = word("chat.running")
  /** Said of somebody at work (`Avatar.busy`). */
  def busy: String = word("chat.busy")
  def succeeded: String = word("chat.succeeded")
  def failed: String = word("chat.failed")
  def connecting: String = word("chat.connecting")
  def offline: String = word("chat.offline")
  def cancel: String = Words.cancel
  def retry: String = Words.retry
  def loadingOlder: String = word("chat.loadingOlder")
  def show: String = word("chat.show")
  def hide: String = word("chat.hide")
  /** Words cut short opened whole, and closed again. */
  def showMore: String = word("chat.showMore")
  def showLess: String = word("chat.showLess")
  def copy: String = word("chat.copy")
  def copyMessage: String = word("chat.copyMessage")
  /** A message's text copied as it was written, its Markdown and all. */
  def copyMarkdown: String = word("chat.copyMarkdown")
  /** Said once a message is copied. */
  def copied: String = word("chat.copied")
  /** What a message's row of actions is, to a screen reader. */
  def messageActions: String = word("chat.messageActions")
  def selectAll: String = word("chat.selectAll")
  /** "About Ada", the menu's way to a message's author. */
  def about(name: String): String = word("chat.about").replace("{name}", name)

  // a composer's queue (Composer.queueWhileBusy)
  /** "2 messages queued", over the queue and to a screen reader as it changes. */
  def queued(n: Int): String = said("chat.queued", Map("n" -> n.toString))
  def queuePaused: String = word("chat.queuePaused")
  def resume: String = word("chat.resume")
  /** A paused queue's next message sent, the rest still paused. */
  def sendNext: String = word("chat.sendNext")
  def clearQueue: String = word("chat.clearQueue")
  /** The ⋮ holding the queue's actions that do not fit beside it. */
  def queueMore: String = word("chat.queueMore")
  /** The Send beside Stop while a reply is coming. */
  def addToQueue: String = word("chat.addToQueue")
  def edit: String = word("chat.edit")
  def remove: String = word("chat.remove")
  /** "2 files", going with a queued message. */
  def fileCount(n: Int): String = said("chat.files", Map("n" -> n.toString))
  /** "Queued message 2 of 3: Hello", a queued message to a screen reader. */
  def queuedItem(i: Int, n: Int, text: String): String =
    said("chat.queuedItem", Map("i" -> i.toString, "n" -> n.toString, "text" -> text))
  /** What the keys do on a queued message. */
  def queuedHelp: String = word("chat.queuedHelp")
  /** "Moved to 1 of 3", once a queued message is moved. */
  def moved(i: Int, n: Int): String = said("chat.moved", Map("i" -> i.toString, "n" -> n.toString))
  /** A queued message's handle. */
  def reorder: String = word("chat.reorder")
}
