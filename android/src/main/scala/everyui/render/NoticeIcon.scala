package everyui.render

import android.content.Context

/** The small icon of the app's notifications (push, `Platform.showNotice`, ongoing notices): the drawable
  * `everyui_notice` where the app's resources have one, the app's own icon otherwise. Android draws a small icon as a
  * silhouette, so a full-colour launcher icon shows as a blank shape: an app supplies `everyui_notice`, white on
  * transparent, at each density (CONSUMING.md). */
object NoticeIcon {
  @volatile private var found: Option[Int] = None

  def of(context: Context): Int = found.getOrElse {
    val own = context.getResources.getIdentifier("everyui_notice", "drawable", context.getPackageName)
    val icon = if (own != 0) own else context.getApplicationInfo.icon
    found = Some(icon)
    icon
  }
}
