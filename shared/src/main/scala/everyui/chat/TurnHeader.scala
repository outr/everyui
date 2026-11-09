package everyui.chat

import everyui.log.AnsiText
import everyui.markdown.MarkdownPlain
import reactify.*

/** What a transcript's turn header shows for an entry that starts a turn (`Transcript.turnHeader`): the turn's words
  * (`text`, the person's message, followed as it changes), who said them where the conversation has more than one
  * person (`author`, a room's), the name of a file that went with them (`attachment`, drawn after a paperclip), and
  * whether the turn is still running (`busy`, a small spinner at the end, which the app turns on and off as it goes).
  * The header is one line: the words are cut short to fit, and shown whole in its tip. They are Markdown, as a
  * message's are, shown and said as their words (`MarkdownPlain.spoken`: no marks, fences or escapes), unless they
  * are `plain` text (a transcript of `MessageText.plain`), shown and said as they are less a terminal's escapes.
  *
  * {{{
  *   transcript.turnHeader @= Some {
  *     case m: ChatEntry.Message if m.self => Some(TurnHeader(m.text, busy = replying(m.id)))
  *     case _ => None
  *   }
  * }}} */
case class TurnHeader(text: Val[String], author: Option[String] = None, attachment: Option[String] = None,
                      busy: Val[Boolean] = Val(false), plain: Boolean = false) {
  /** The words on its line: the author's name and the words, on one line. */
  def line: String = {
    val words = TurnHeader.oneLine(if (plain) AnsiText.unescaped(text()) else MarkdownPlain.spoken(text()))
    author.filter(_.nonEmpty) match {
      case Some(name) if words.nonEmpty => name + ": " + words
      case Some(name) => name
      case None => words
    }
  }

  /** The words whole, with the file's name: its tip, and what a screen reader hears after "Jump to message". */
  def said: String = {
    val words = line
    attachment.filter(_.nonEmpty) match {
      case Some(file) if words.nonEmpty => words + ", " + file
      case Some(file) => file
      case None => words
    }
  }
}

object TurnHeader {
  /** How tall the header is, how far it sits in from the transcript's top and sides, and the room inside it. */
  val height: Double = 30.0
  val inset: Double = 6.0
  val sideInset: Double = 12.0
  val padX: Double = 10.0
  /** How big its glyphs are (the arrow, the paperclip, the spinner), and the gap between its parts. */
  val glyph: Double = 14.0
  val gap: Double = 6.0
  /** The most of the header's width a file's name takes before it is cut short. */
  val fileShare: Double = 0.4

  /** A header for message `m`: its words, followed as they change, its author's name where `named` (a room of several
    * people), `busy` for the spinner, and `plain` where its words are plain text. */
  def of(m: ChatEntry.Message, named: Boolean = false, attachment: Option[String] = None,
         busy: Val[Boolean] = Val(false), plain: Boolean = false): TurnHeader =
    TurnHeader(m.text, if (named) Some(m.author.name) else None, attachment, busy, plain)

  /** `text` on one line: its line breaks and tabs as spaces, runs of spaces as one, and none at either end. */
  def oneLine(text: String): String = {
    var s = text.replace("\r\n", " ").replace("\n", " ").replace("\r", " ").replace("\t", " ").trim
    while (s.contains("  ")) s = s.replace("  ", " ")
    s
  }
}
