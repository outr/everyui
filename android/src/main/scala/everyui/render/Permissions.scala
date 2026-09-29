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

  /** Where `permission` stands: held; refused, once asked for here and not given; or not asked yet. */
  def state(permission: String): everyui.Permission =
    if (held(permission)) everyui.Permission.Granted
    else if (Platform.setting("everyui.asked." + permission).nonEmpty) everyui.Permission.Refused
    else everyui.Permission.NotAsked

  /** `permission`, asked for where it is not held yet (the system's own prompt), and whether it was given. */
  def ask(permission: String)(answer: Boolean => Unit): Unit = askAll(List(permission))(answer)

  /** Each of `permissions`, asked for together where any is not held yet (one prompt: a precise and an approximate
    * location are offered as one choice), and whether all were given. */
  def askAll(permissions: List[String])(answer: Boolean => Unit): Unit =
    if (permissions.forall(held)) answer(true)
    else {
      permissions.foreach(p => Platform.saveSetting("everyui.asked." + p, "1"))
      next += 1
      waiting(next) = answer
      Host.activity.requestPermissions(permissions.toArray, next)
    }

  /** What the system said to request `code`. */
  def answered(code: Int, granted: Boolean): Unit = waiting.remove(code).foreach(answer => answer(granted))
}
