package everyui.render

import everyui.passkey.PasskeyResult

/** Passkeys (`everyui.passkey.Passkeys`): this platform has no passkey manager for an app to ask, so every request
  * answers at once that it is unsupported. */
object PasskeyAccess {
  def supported(): Boolean = false

  def create(options: String, done: PasskeyResult => Unit): Unit = done(PasskeyResult.PasskeyUnsupported)

  def get(options: String, done: PasskeyResult => Unit): Unit = done(PasskeyResult.PasskeyUnsupported)

  def autofillable(done: Boolean => Unit): Unit = done(false)

  def autofill(options: String, done: PasskeyResult => Unit): Unit = done(PasskeyResult.PasskeyUnsupported)
}
