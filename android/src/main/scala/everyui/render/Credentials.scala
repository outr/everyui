package everyui.render

import android.os.CancellationSignal
import androidx.credentials.{CreateCredentialResponse, CreatePublicKeyCredentialRequest, CreatePublicKeyCredentialResponse,
  CredentialManager, CredentialManagerCallback, GetCredentialRequest, GetCredentialResponse, GetPublicKeyCredentialOption,
  PublicKeyCredential}
import androidx.credentials.exceptions.{CreateCredentialCancellationException, CreateCredentialException,
  CreateCredentialProviderConfigurationException, CreateCredentialUnsupportedException, GetCredentialCancellationException,
  GetCredentialException, GetCredentialProviderConfigurationException, GetCredentialUnsupportedException,
  NoCredentialException}
import everyui.passkey.PasskeyResult

/** Credential Manager's calls, apart from `PasskeyAccess` so that nothing of androidx.credentials is reached until it
  * is known to be there. Its answers come on the thread that draws. */
object Credentials {
  /** Whether Credential Manager is in the app: a LinkageError where its build left it out. */
  def present(): Boolean = classOf[CredentialManager].getName.nonEmpty

  private def manager: CredentialManager = CredentialManager.Companion.create(Host.activity)

  // its answers on the thread that draws: Activity.getMainExecutor is Android 9's
  private lazy val main: java.util.concurrent.Executor = {
    val handler = new android.os.Handler(android.os.Looper.getMainLooper)
    (r: Runnable) => { handler.post(r); () }
  }

  /** The options as Credential Manager takes them: the object itself, not under "publicKey". */
  private def bare(options: String): String = {
    val json = new org.json.JSONObject(options)
    if (json.has("publicKey")) json.getJSONObject("publicKey").toString else options
  }

  def create(options: String, done: PasskeyResult => Unit): Unit =
    manager.createCredentialAsync(Host.activity, new CreatePublicKeyCredentialRequest(bare(options)), new CancellationSignal,
      main, new CredentialManagerCallback[CreateCredentialResponse, CreateCredentialException] {
        override def onResult(r: CreateCredentialResponse): Unit = r match {
          case p: CreatePublicKeyCredentialResponse => done(PasskeyResult.Credential(p.getRegistrationResponseJson))
          case other => done(PasskeyResult.PasskeyFailed("Not a passkey: " + other.getType))
        }

        override def onError(e: CreateCredentialException): Unit = done(e match {
          case _: CreateCredentialCancellationException => PasskeyResult.PasskeyCancelled
          case _: CreateCredentialUnsupportedException | _: CreateCredentialProviderConfigurationException =>
            PasskeyResult.PasskeyUnsupported
          case other => PasskeyResult.PasskeyFailed(other.getType + ": " + String.valueOf(other.getErrorMessage))
        })
      })

  def get(options: String, done: PasskeyResult => Unit): Unit =
    manager.getCredentialAsync(Host.activity,
      new GetCredentialRequest(java.util.Collections.singletonList(new GetPublicKeyCredentialOption(bare(options)))),
      new CancellationSignal, main, new CredentialManagerCallback[GetCredentialResponse, GetCredentialException] {
        override def onResult(r: GetCredentialResponse): Unit = r.getCredential match {
          case p: PublicKeyCredential => done(PasskeyResult.Credential(p.getAuthenticationResponseJson))
          case other => done(PasskeyResult.PasskeyFailed("Not a passkey: " + other.getType))
        }

        override def onError(e: GetCredentialException): Unit = done(e match {
          // no passkey for the site is told the person, and answered as the web answers it
          case _: GetCredentialCancellationException | _: NoCredentialException => PasskeyResult.PasskeyCancelled
          case _: GetCredentialUnsupportedException | _: GetCredentialProviderConfigurationException =>
            PasskeyResult.PasskeyUnsupported
          case other => PasskeyResult.PasskeyFailed(other.getType + ": " + String.valueOf(other.getErrorMessage))
        })
      })
}
