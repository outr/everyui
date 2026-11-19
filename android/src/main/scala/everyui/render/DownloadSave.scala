package everyui.render

import android.app.DownloadManager
import android.net.Uri
import android.os.{Build, Environment}
import android.webkit.CookieManager

/** A page's download saved into the device's Downloads, as a browser saves it (everyui.component.WebDownload.save).
  *
  * An address of the web (`http:`, `https:`) is fetched by DownloadManager, with the page's cookies for it, its
  * User-Agent and the page as its referer, with the system's notification while it runs and when it is done. From
  * Android 10 it writes into the public Downloads with no permission; before it that takes WRITE_EXTERNAL_STORAGE
  * (`android:maxSdkVersion="28"` in the app's manifest), asked for once, as the first download is saved, and where the
  * app does not declare it or it was refused the file goes into the app's own Downloads on the shared storage instead
  * (`Android/data/<package>/files/Download`), still listed by the Downloads app. */
object DownloadSave {
  private val storage = "android.permission.WRITE_EXTERNAL_STORAGE"

  /** Whether a download may go into the public Downloads folder, asked for first where it needs asking. */
  def where(public: Boolean => Unit): Unit =
    if (Build.VERSION.SDK_INT >= 29 || Permissions.held(storage)) public(true)
    // asked once: refused, the app's own Downloads from then on, rather than the prompt at every download
    else if (declared(storage) && Permissions.state(storage) == everyui.Permission.NotAsked) Permissions.ask(storage)(public)
    else public(false)

  private def declared(permission: String): Boolean =
    try {
      val context = Host.activity
      val info = context.getPackageManager.getPackageInfo(context.getPackageName,
        android.content.pm.PackageManager.GET_PERMISSIONS)
      Option(info.requestedPermissions).exists(_.contains(permission))
    }
    catch { case _: Throwable => false }

  /** `url` fetched into the Downloads as `name`. */
  def fetch(url: String, name: String, mime: String, agent: String, referer: String): Unit = where { public =>
    try {
      val context = Host.activity
      val request = new DownloadManager.Request(Uri.parse(url))
      Option(CookieManager.getInstance().getCookie(url)).filter(_.nonEmpty).foreach(request.addRequestHeader("Cookie", _))
      if (agent.nonEmpty) request.addRequestHeader("User-Agent", agent)
      if (referer.startsWith("http:") || referer.startsWith("https:")) request.addRequestHeader("Referer", referer)
      if (mime.nonEmpty) request.setMimeType(mime)
      request.setTitle(name)
      request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
      if (public) request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
      else request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
      context.getSystemService(classOf[DownloadManager]).enqueue(request)
      Platform.log("[web] downloading " + name)
    }
    catch { case t: Throwable => Platform.log("[web] a download could not be started (" + name + "): " + t) }
  }
}
