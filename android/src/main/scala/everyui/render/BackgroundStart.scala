package everyui.render

import android.content.{ContentProvider, ContentValues}
import android.database.Cursor
import android.net.Uri
import everyui.activity.ServiceRuntime

/** Made as the app's process starts, before any activity, receiver or job: the runtime's context, and WorkManager
  * ready (`BackgroundWork.ready`), so the job WorkManager's SystemJobService is started for in a process of its own
  * finds it; otherwise it lets the job go. In place of androidx.startup's provider, which WorkManager's own manifest
  * declares. sbt-everyui declares it where the build names a `BackgroundApp` (`everyuiBackground`). It provides
  * nothing. */
class BackgroundStart extends ContentProvider {
  override def onCreate(): Boolean = {
    val context = getContext
    if (context != null) {
      ServiceRuntime.init(context)
      try BackgroundWork.ready(context)
      catch { case t: Throwable => Platform.logError("background: WorkManager not made ready", t) }
    }
    true
  }

  override def query(uri: Uri, projection: Array[String], selection: String, args: Array[String],
                     order: String): Cursor = null

  override def getType(uri: Uri): String = null

  override def insert(uri: Uri, values: ContentValues): Uri = null

  override def delete(uri: Uri, selection: String, args: Array[String]): Int = 0

  override def update(uri: Uri, values: ContentValues, selection: String, args: Array[String]): Int = 0
}
