package everyui.chat

import everyui.kit.ImageViewer

/** Somebody's avatar or name pressed in a conversation: a message's (`messageId`), or the status bar's line of what
  * they are working on (none). What it does is the app's (`Transcript.onAuthor`): their profile, a mention. */
case class AuthorPressed(participant: Participant, messageId: Option[String])

object AuthorPressed {
  /** What a press does unless the app says: their picture, whole, over the screen (`ImageViewer`, the picture at its
    * own address rather than the avatar's small circle), which Escape, Back or a click outside it closes. Without a
    * picture, nothing. */
  def showPicture(pressed: AuthorPressed): Unit =
    pressed.participant.avatar.foreach(url => new ImageViewer(List(url), 0).open())

  /** Whether a press on `who` does anything: the app's `handler`, else their picture where they have one. */
  def answers(who: Participant, handler: Option[AuthorPressed => Unit]): Boolean =
    handler.nonEmpty || who.avatar.nonEmpty
}
