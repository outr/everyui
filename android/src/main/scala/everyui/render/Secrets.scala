package everyui.render

import android.content.Context
import android.security.keystore.{KeyGenParameterSpec, KeyPermanentlyInvalidatedException, KeyProperties}
import android.util.Base64
import java.security.KeyStore
import javax.crypto.{AEADBadTagException, Cipher, KeyGenerator, SecretKey}
import javax.crypto.spec.GCMParameterSpec

/** Values that must not be readable off the device (a session's token), kept encrypted under a key in Android's
  * keystore (Platform.secret). The key never leaves the keystore, so a copy of the app's files — a backup, a
  * device-to-device restore, a rooted look — holds only ciphertext nothing else can open; restored onto another device
  * it reads as nothing, and whoever it was signs in again, which is the right outcome.
  *
  * The keystore is asked through the system's keystore service, which can take seconds to answer once the device wakes,
  * so it is asked as little as the process can: the key is loaded once, and each value opened once ([[SecretCache]]).
  * A first read on the main thread can still wait on it; `Platform.secretThen` opens one off it. */
object Secrets {
  /** Whether a secret that has to ask the keystore on the main thread is said in the log, once a name: in a debug build
    * or where the launch asks (`--es hops 1`), as the main thread's other hazards are ([[Hop]]), or where the app sets it. */
  @volatile var audit: Boolean = false

  private val Alias = "everyui-secrets"
  private lazy val store: android.content.SharedPreferences =
    everyui.activity.ServiceRuntime.context.getSharedPreferences("everyui-secrets", Context.MODE_PRIVATE)

  private val cache = new SecretCache[SecretKey](new SecretCache.Keys[SecretKey] {
    private def keystore(): KeyStore = {
      val keys = KeyStore.getInstance("AndroidKeyStore")
      keys.load(null)
      keys
    }
    def load(): SecretKey = Option(keystore().getKey(Alias, null)).map(_.asInstanceOf[SecretKey]).getOrElse {
      val made = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
      made.init(new KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build())
      made.generateKey()
    }
    def discard(): Unit = keystore().deleteEntry(Alias)
    def open(key: SecretKey, locked: String): String = {
      val bytes = Base64.decode(locked, Base64.NO_WRAP)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, bytes, 0, 12))
      new String(cipher.doFinal(bytes, 12, bytes.length - 12), "UTF-8")
    }
    def seal(key: SecretKey, value: String): String = {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, key)
      Base64.encodeToString(cipher.getIV ++ cipher.doFinal(value.getBytes("UTF-8")), Base64.NO_WRAP)
    }
    // text that is not this key's, or damaged, is the text's fault; anything else may be the key's
    def keyFault(failure: Throwable): Boolean =
      !failure.isInstanceOf[AEADBadTagException] && !failure.isInstanceOf[IllegalArgumentException]
    def unusable(failure: Throwable): Boolean = failure.isInstanceOf[KeyPermanentlyInvalidatedException]
  })

  /** The value kept under `name` where it has been opened in this process already, so that reading it asks nothing of
    * the keystore; "" where none is kept. */
  def cached(name: String): Option[String] = {
    val locked = store.getString(name, "")
    if (locked.isEmpty) Some("") else cache.cached(name, locked)
  }

  /** The value kept under `name`, or "" where there is none or it cannot be opened here. */
  def get(name: String): String = {
    val locked = store.getString(name, "")
    if (locked.isEmpty) ""
    else cache.cached(name, locked).getOrElse {
      onMain(name)
      try cache.read(name, locked)
      catch {
        case t: Throwable =>
          Platform.logError("secrets: " + name + " could not be opened", t)
          ""
      }
    }
  }

  /** Keep `value` under `name`, locked; "" forgets it. */
  def put(name: String, value: String): Unit = {
    val edit = store.edit()
    if (value.isEmpty) {
      cache.forget(name)
      edit.remove(name)
    }
    else try {
      onMain(name)
      edit.putString(name, cache.write(name, value))
    }
    catch { case t: Throwable => Platform.logError("secrets: " + name + " could not be kept", t) }
    edit.apply()
  }

  // said once a name where the keystore is asked on the main thread
  private def onMain(name: String): Unit =
    if (audit && Platform.onDrawingThread() && cache.firstTime(name))
      Platform.log("secrets: " + name + " asks the keystore on the main thread, which can hold it for seconds after the " +
        "device wakes (Platform.secretThen opens it off the main thread)")
}
