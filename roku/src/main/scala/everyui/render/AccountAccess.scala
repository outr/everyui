package everyui.render

import everyui.roku.*

/** The account's details (`everyui.auth.AccountDetails`) through a ChannelStore node's getUserData: Roku shows its own
  * dialog naming the fields asked for, and the answer is what the person agreed to share, or None
  * (UniversalRuntime.brs's uiUserData). */
object AccountAccess {
  def supported(): Boolean = true

  def ask(fields: Seq[String], signIn: Boolean, done: Option[Map[String, String]] => Unit): Unit =
    uiUserData(fields.mkString(","), signIn, done)
}
