package everyui.render

import android.content.Context
import android.security.keystore.{KeyGenParameterSpec, KeyProperties}
import android.util.Base64
import java.security.KeyStore
import javax.crypto.{Cipher, KeyGenerator, SecretKey}
import javax.crypto.spec.GCMParameterSpec

/** Values that must not be readable off the device (a session's token), kept encrypted under a key in Android's
  * keystore (Platform.secret). The key never leaves the keystore, so a copy of the app's files — a backup, a
  * device-to-device restore, a rooted look — holds only ciphertext nothing else can open; restored onto another device
  * it reads as nothing, and whoever it was signs in again, which is the right outcome. */
object Secrets {
  private val Alias = "everyui-secrets"
  private lazy val store: android.content.SharedPreferences =
    Host.activity.getSharedPreferences("everyui-secrets", Context.MODE_PRIVATE)

  private def key(): SecretKey = {
    val keys = KeyStore.getInstance("AndroidKeyStore")
    keys.load(null)
    Option(keys.getKey(Alias, null)).map(_.asInstanceOf[SecretKey]).getOrElse {
      val made = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
      made.init(new KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build())
      made.generateKey()
    }
  }

  /** The value kept under `name`, or "" where there is none or it cannot be opened here. */
  def get(name: String): String = {
    val locked = store.getString(name, "")
    if (locked.isEmpty) ""
    else try {
      val bytes = Base64.decode(locked, Base64.NO_WRAP)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 0, 12))
      new String(cipher.doFinal(bytes, 12, bytes.length - 12), "UTF-8")
    }
    catch {
      case t: Throwable =>
        Platform.logError("secrets: " + name + " could not be opened", t)
        ""
    }
  }

  /** Keep `value` under `name`, locked; "" forgets it. */
  def put(name: String, value: String): Unit = {
    val edit = store.edit()
    if (value.isEmpty) edit.remove(name)
    else try {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, key())
      val locked = cipher.getIV ++ cipher.doFinal(value.getBytes("UTF-8"))
      edit.putString(name, Base64.encodeToString(locked, Base64.NO_WRAP))
    }
    catch { case t: Throwable => Platform.logError("secrets: " + name + " could not be kept", t) }
    edit.apply()
  }
}
