package everyui.passkey

import everyui.async.Task
import everyui.render.PasskeyAccess

/** Passkeys (WebAuthn): a credential made for the site, and signed in with, by the platform's own passkey manager.
  * The server makes the options (with its WebAuthn library) and verifies what comes back; the app hands one to the
  * other. Options are the JSON a server library writes for the browser, binary fields as base64url
  * (`PublicKeyCredentialCreationOptionsJSON`, `PublicKeyCredentialRequestOptionsJSON`), bare or under "publicKey".
  *
  * The web asks through `navigator.credentials`; Android through Credential Manager (androidx.credentials, which the
  * app adds: CONSUMING.md). The desktop, a Roku and an Apple TV have none (`supported` false).
  *
  * {{{
  *   Passkeys.get(optionsFromServer).map {
  *     case PasskeyResult.Credential(json) => verifyOnServer(json)
  *     case PasskeyResult.PasskeyCancelled => ()
  *     case other => showError(other.toString)
  *   }.start()
  * }}} */
object Passkeys {
  /** Whether this platform makes and signs in with passkeys. */
  def supported: Boolean = PasskeyAccess.supported()

  /** A new passkey for the account, from the server's registration (creation) options. Best started from a click. */
  def create(options: String): Task[PasskeyResult] = Task.async[PasskeyResult](done => PasskeyAccess.create(options, done))

  /** A sign-in with one of the site's passkeys, from the server's authentication (request) options: the platform asks
    * the person to choose one. Best started from a click. */
  def get(options: String): Task[PasskeyResult] = Task.async[PasskeyResult](done => PasskeyAccess.get(options, done))

  /** Whether the site's passkeys can be offered in a field's autofill here (`autofill`). */
  def autofillable: Task[Boolean] = Task.async[Boolean](done => PasskeyAccess.autofillable(done))

  /** A sign-in offered quietly in the autofill of the page's username field (a `TextInput` with `passkey` set): it
    * answers once the person chooses a passkey there, and stays open until then. Started as the sign-in shows, not from
    * a click. A `create` or `get` started meanwhile ends it (PasskeyCancelled), as does a second `autofill`: a page has
    * one request at a time, and each ends the one still waiting. */
  def autofill(options: String): Task[PasskeyResult] =
    Task.async[PasskeyResult](done => PasskeyAccess.autofill(options, done))
}
