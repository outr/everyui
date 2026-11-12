package everyui.render

/** The account's details (`everyui.auth.AccountDetails`): this platform has no account of the person's for an app to
  * ask, so every request answers None, on a later turn as a platform's dialog would. */
object AccountAccess {
  def supported(): Boolean = false

  def ask(fields: Seq[String], signIn: Boolean, done: Option[Map[String, String]] => Unit): Unit =
    Platform.later(() => done(None))
}
