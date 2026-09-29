package everyui.render

import everyui.passkey.PasskeyResult

/** Passkeys (`everyui.passkey.Passkeys`) through Android's Credential Manager (androidx.credentials, in
  * `EveryUILibraries.android`). Android 14 and later answer from the system's own; before 14 only an app that adds
  * Google Play services' provider (`credentials-play-services-auth`: CONSUMING.md) has passkeys, and without it every
  * request answers PasskeyUnsupported. The site's options name it by its `rp.id`, and the app may use that site's
  * passkeys only once the site says the app is its own (Digital Asset Links: `everyui.sbt.AppDomains`). Autofill is the
  * web's; here a sign-in is asked for with `get`. */
object PasskeyAccess {
  // Credential Manager itself, asked for directly (a name looked up would be the shrinker's to rename), where an app's
  // build left it out; and Play services' provider, which it finds by name itself
  private lazy val available: Boolean =
    (try Credentials.present() catch { case _: LinkageError => false }) &&
      (android.os.Build.VERSION.SDK_INT >= 34 || provider("androidx.credentials.playservices.CredentialProviderPlayServicesImpl"))

  private def provider(name: String): Boolean =
    try {
      Class.forName(name)
      true
    } catch { case _: Throwable => false }

  def supported(): Boolean = available

  def create(options: String, done: PasskeyResult => Unit): Unit =
    if (supported()) Credentials.create(options, done) else done(PasskeyResult.PasskeyUnsupported)

  def get(options: String, done: PasskeyResult => Unit): Unit =
    if (supported()) Credentials.get(options, done) else done(PasskeyResult.PasskeyUnsupported)

  def autofillable(done: Boolean => Unit): Unit = done(false)

  def autofill(options: String, done: PasskeyResult => Unit): Unit = done(PasskeyResult.PasskeyUnsupported)
}
