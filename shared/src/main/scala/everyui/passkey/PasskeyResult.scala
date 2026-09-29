package everyui.passkey

/** How a passkey request (`Passkeys.create`, `Passkeys.get`, `Passkeys.autofill`) ended. Its cases' names are their
  * own (not "Cancelled"), since a Roku channel's type names are global. */
sealed trait PasskeyResult

object PasskeyResult {
  /** The credential, as JSON in the form of the web's `PublicKeyCredential.toJSON()` (binary fields as base64url):
    * a registration's `response` has its `clientDataJSON` and `attestationObject`, an assertion's its
    * `clientDataJSON`, `authenticatorData`, `signature` and `userHandle`. What the server's WebAuthn library verifies. */
  case class Credential(json: String) extends PasskeyResult

  /** The person closed the request, or let it time out, or the app started another in its place. The platforms do not
    * tell these apart from "no passkey for this site here", on purpose, so a site cannot learn which accounts a person
    * has. */
  case object PasskeyCancelled extends PasskeyResult

  /** No passkeys on this platform (the desktop, a Roku, an Apple TV, an Android without Credential Manager), or not
    * in the way asked (autofill where it has none). */
  case object PasskeyUnsupported extends PasskeyResult

  /** Anything else, as the platform put it: options it would not take, a passkey already made for this account on
    * this authenticator ("InvalidStateError"), a site the app is not associated with. */
  case class PasskeyFailed(message: String) extends PasskeyResult
}
