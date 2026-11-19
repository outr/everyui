package everyui.render

import android.app.{DownloadManager, NotificationManager, PendingIntent}
import android.content.{ContentValues, Context, Intent}
import android.net.Uri
import android.os.{Build, Environment}
import android.provider.MediaStore

/** A file a page's script made (a Blob, a `data:` address) being written into the device's Downloads, as
  * DownloadManager cannot fetch one: from Android 10 an entry of MediaStore's Downloads, pending until it is whole;
  * before it a file in the public Downloads folder where the app may write there (`public`), else in the app's own
  * Downloads on the shared storage. `finish` makes it seen, with a notification that opens it (or, where the app may not
  * notify, a toast); `abandon` takes away what was written. Written on a thread of its own, never the main one. */
class DownloadFile private (val name: String, mime: String, val out: java.io.OutputStream, entry: Option[Uri],
                            file: Option[java.io.File]) {
  private var written = 0L

  def write(bytes: Array[Byte]): Unit = {
    out.write(bytes)
    written += bytes.length
  }

  def finish(): Unit = {
    out.close()
    val context = Host.activity
    entry.foreach { uri =>
      val done = new ContentValues()
      done.put(MediaStore.MediaColumns.IS_PENDING, Integer.valueOf(0))
      context.getContentResolver.update(uri, done, null, null)
      DownloadFile.told(context, name, mime, uri)
    }
    // before Android 10 the system's own notification, which opens the file
    file.foreach { f =>
      context.getSystemService(classOf[DownloadManager])
        .addCompletedDownload(name, name, true, mime, f.getAbsolutePath, written, true)
    }
    Platform.log("[web] downloaded " + name + " (" + written + " bytes)")
  }

  def abandon(): Unit = {
    try out.close() catch { case _: Throwable => () }
    entry.foreach(uri => Host.activity.getContentResolver.delete(uri, null, null))
    file.foreach(_.delete())
  }
}

object DownloadFile {
  private val channel = "everyui.downloads"

  /** A file named `name` (or as near as the folder lets it be) opened for writing; None where it cannot be made. */
  def open(name: String, mime: String, public: Boolean): Option[DownloadFile] =
    try {
      val context = Host.activity
      if (Build.VERSION.SDK_INT >= 29) {
        val values = new ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime)
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        values.put(MediaStore.MediaColumns.IS_PENDING, Integer.valueOf(1))
        val resolver = context.getContentResolver
        Option(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)).map { uri =>
          val shown = Option(resolver.query(uri, Array(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null))
            .map { c => try (if (c.moveToFirst()) c.getString(0) else name) finally c.close() }.getOrElse(name)
          new DownloadFile(shown, mime, resolver.openOutputStream(uri), Some(uri), None)
        }
      }
      else {
        val folder =
          if (public) Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
          else context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        folder.mkdirs()
        val target = free(folder, name)
        Some(new DownloadFile(target.getName, mime, new java.io.FileOutputStream(target), None, Some(target)))
      }
    }
    catch {
      case t: Throwable =>
        Platform.log("[web] a download could not be written: " + t)
        None
    }

  // `name`, or `name (1)`, `name (2)`… before its extension, the first not in `folder` yet
  private def free(folder: java.io.File, name: String): java.io.File = {
    val dot = name.lastIndexOf('.')
    val (base, ext) = if (dot > 0) (name.substring(0, dot), name.substring(dot)) else (name, "")
    Iterator.from(0).map(n => new java.io.File(folder, if (n == 0) name else s"$base ($n)$ext")).find(!_.exists()).get
  }

  // a notification that opens the file, where the app may notify; a toast otherwise (a television, notices refused)
  private def told(context: Context, name: String, mime: String, uri: Uri): Unit = {
    val manager = context.getSystemService(classOf[NotificationManager])
    val may = !Platform.remoteNavigation() && NoticeChannels.enabled(manager) &&
      (Build.VERSION.SDK_INT < 33 || Permissions.held("android.permission.POST_NOTIFICATIONS"))
    if (may) {
      NoticeChannels.ensure(manager, channel, "Downloads", NotificationManager.IMPORTANCE_LOW)
      val open = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK)
      val notification = NoticeChannels.builder(context, channel, NotificationManager.IMPORTANCE_LOW)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(name)
        .setContentText("Download complete")
        .setAutoCancel(true)
        .setContentIntent(PendingIntent.getActivity(context, uri.hashCode, open,
          PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
        .build()
      try manager.notify("everyui.download:" + uri, 0, notification)
      catch { case t: SecurityException => Platform.log("[web] the download's notification not posted: " + t.getMessage) }
    }
    else Host.activity.runOnUiThread(() =>
      android.widget.Toast.makeText(context, "Downloaded " + name, android.widget.Toast.LENGTH_SHORT).show())
  }
}
