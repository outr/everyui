package everyui.render

import android.content.pm.PackageManager
import scala.collection.mutable

/** Android's runtime permissions: asked for once somebody has done the thing that needs one, and the answer handed
  * back to whoever asked. The activity passes the system's answers in ([[answered]]). */
object Permissions {
  private val waiting = mutable.Map.empty[Int, Boolean => Unit]
  private var next = 7100

  /** Whether the app holds `permission` already. Anything before Android 6 was granted at install. */
  def held(permission: String): Boolean =
    android.os.Build.VERSION.SDK_INT < 23 ||
      Host.activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

  /** `permission`, asked for where it is not held yet (the system's own prompt), and whether it was given. */
  def ask(permission: String)(answer: Boolean => Unit): Unit =
    if (held(permission)) answer(true)
    else {
      next += 1
      waiting(next) = answer
      Host.activity.requestPermissions(Array(permission), next)
    }

  /** What the system said to request `code`. */
  def answered(code: Int, granted: Boolean): Unit = waiting.remove(code).foreach(answer => answer(granted))
}
