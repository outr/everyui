package everyui.render

import android.content.{ClipData, ClipboardManager, Context}
import android.os.{Build, PersistableBundle}
import android.widget.Toast
import everyui.i18n.{Language, Words}

/** A copy button's text put on the clipboard (`PushButton.copyText`), with the app closed or open: marked
  * sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`, so the clipboard preview of Android 13 and later shows dots for
  * it), said "Copied" by a Toast before Android 13, which says nothing itself, and never written to the log.
  * Android lets an app write the clipboard from the background; only reading it is kept to the app in front (Android
  * 10 on). */
object PushCopy {
  // the key Android 13 names ClipDescription.EXTRA_IS_SENSITIVE, set as text so it is there before 13 too
  private val sensitive = "android.content.extra.IS_SENSITIVE"

  def apply(context: Context, text: String): Unit = {
    val clip = ClipData.newPlainText("everyui", text)
    if (Build.VERSION.SDK_INT >= 24) {
      val extras = new PersistableBundle
      extras.putBoolean(sensitive, true)
      clip.getDescription.setExtras(extras)
    }
    context.getSystemService(classOf[ClipboardManager]).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, word("push.copied"), Toast.LENGTH_SHORT).show()
    Platform.log("push: a button's text copied (" + text.length.toString + " characters)")
  }

  /** `key` in the device's language, where EveryUI has it, else English: what the system shows (a notification's
    * button, a Toast) speaks the device's language, whatever the app's screens were last set to. */
  def word(key: String): String = {
    val shipped = Words.languages
    val code = Language.best(Platform.language(), shipped.keys.toList.map(c => Language(c, c, c)),
      Language("en", "English", "English")).code
    shipped.get(code).flatMap(_.get(key)).orElse(shipped.get("en").flatMap(_.get(key))).getOrElse(key)
  }
}
