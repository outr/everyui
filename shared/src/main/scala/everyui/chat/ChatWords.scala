package everyui.chat

import everyui.i18n.{Icu, Words}

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
  /** Said of somebody at work (`Avatar.busy`). */
  def busy: String = word("chat.busy", "busy")
  def succeeded: String = word("chat.succeeded", "done")
  def failed: String = word("chat.failed", "failed")
  def connecting: String = word("chat.connecting", "Connecting…")
  def offline: String = word("chat.offline", "Offline")
  def cancel: String = Words.cancel
  def retry: String = Words.retry
  def loadingOlder: String = word("chat.loadingOlder", "Loading earlier messages")
  def show: String = word("chat.show", "Show")
  def hide: String = word("chat.hide", "Hide")
  def copy: String = word("chat.copy", "Copy")
  def copyMessage: String = word("chat.copyMessage", "Copy message")
  /** Said once a message is copied. */
  def copied: String = word("chat.copied", "Copied")
  /** What a message's row of actions is, to a screen reader. */
  def messageActions: String = word("chat.messageActions", "Message actions")
  def selectAll: String = word("chat.selectAll", "Select all")
  /** "About Ada", the menu's way to a message's author. */
  def about(name: String): String = word("chat.about", "About {name}").replace("{name}", name)

  // a composer's queue (Composer.queueWhileBusy): these come in Words' eleven languages
  private def said(key: String, english: String, args: Map[String, String]): String =
    Icu.format(word(key, english), args)
  /** "2 messages queued", over the queue and to a screen reader as it changes. */
  def queued(n: Int): String =
    said("chat.queued", "{n, plural, =0{No messages queued} =1{1 message queued} other{# messages queued}}",
      Map("n" -> n.toString))
  def queuePaused: String = word("chat.queuePaused", "Paused")
  def resume: String = word("chat.resume", "Resume")
  /** The Send beside Stop while a reply is coming. */
  def addToQueue: String = word("chat.addToQueue", "Add to queue")
  def edit: String = word("chat.edit", "Edit")
  def remove: String = word("chat.remove", "Remove")
  /** "2 files", going with a queued message. */
  def fileCount(n: Int): String = said("chat.files", "{n, plural, =1{1 file} other{# files}}", Map("n" -> n.toString))
  /** "Queued message 2 of 3: Hello", a queued message to a screen reader. */
  def queuedItem(i: Int, n: Int, text: String): String =
    said("chat.queuedItem", "Queued message {i} of {n}: {text}", Map("i" -> i.toString, "n" -> n.toString, "text" -> text))
  /** What the keys do on a queued message. */
  def queuedHelp: String = word("chat.queuedHelp", "Enter edits it, Delete removes it, Alt+Up or Alt+Down moves it")
  /** "Moved to 1 of 3", once a queued message is moved. */
  def moved(i: Int, n: Int): String = said("chat.moved", "Moved to {i} of {n}", Map("i" -> i.toString, "n" -> n.toString))
  /** A queued message's handle. */
  def reorder: String = word("chat.reorder", "Drag to reorder")
}
