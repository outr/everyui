package everyui.sbt

import java.io.File

/** What an Android app needs for what runs with it closed (`everyui.background`), written from its build
  * (`everyuiBackground`) rather than by hand: the meta-data naming its `BackgroundApp`, EveryUI's receiver (the device
  * started, the app updated, an ongoing notice's actions) and job service (scheduled work), the permission to hear the
  * device start, and the shrinker's rules for what the manifest names and the receiver loads by name.
  *
  * The plugin's tasks put these together (EveryUIPlugin); what is here is the text of each. */
object AndroidBackground {
  /** The receiver and the job service the manifest names: EveryUI's own, one of each for any app. */
  val receiverClass: String = "everyui.render.BackgroundReceiver"
  val jobsClass: String = "everyui.render.BackgroundJobs"

  /** What the app's `BackgroundApp` must be, as the build checks it. */
  val appTrait: String = "everyui.background.BackgroundApp"

  /** The permissions the entries need: to hear the device start, and to keep scheduled work through a restart. */
  val permissions: Seq[String] = Seq("android.permission.RECEIVE_BOOT_COMPLETED")

  private def xml(s: String): String = s.flatMap {
    case '&' => "&amp;"
    case '<' => "&lt;"
    case '>' => "&gt;"
    case '"' => "&quot;"
    case '\'' => "&apos;"
    case c => c.toString
  }

  /** What is wrong with `app` as a name, said; None where it can be written. */
  def problem(app: String): Option[String] =
    if (app.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) None
    else Some(s"everyuiBackground \"$app\" is not an object's full name (\"com.example.Background\")")

  /** The application's entries: the meta-data naming `app`, the receiver and the job service. */
  def entries(app: String): String =
    s"""    <!-- what runs with the app closed, written by sbt-everyui from everyuiBackground -->
       |    <meta-data android:name="everyui.background" android:value="${xml(app)}" />
       |    <receiver android:name="$receiverClass" android:exported="false">
       |      <intent-filter>
       |        <action android:name="android.intent.action.BOOT_COMPLETED" />
       |        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
       |      </intent-filter>
       |    </receiver>
       |    <service android:name="$jobsClass" android:exported="true"
       |      android:permission="android.permission.BIND_JOB_SERVICE" />
       |""".stripMargin

  /** `manifest` with the entries put at the end of its application, and the permissions it does not declare already
    * put before the application. It must have one, and must not declare what is written here itself. */
  def manifest(manifest: String, app: String): Either[String, String] = {
    val start = manifest.indexOf("<application")
    val end = manifest.lastIndexOf("</application>")
    if (start < 0 || end < 0)
      Left("the app's AndroidManifest.xml has no application to put the background's entries in")
    else if (manifest.contains("\"everyui.background\"") || manifest.contains(receiverClass) ||
      manifest.contains(jobsClass))
      Left("the app's AndroidManifest.xml declares its background itself: leave it to everyuiBackground")
    else {
      // at the start of the line each is on, so it keeps its indent
      def lineOf(at: Int): Int = {
        val line = manifest.lastIndexOf('\n', at) + 1
        if (manifest.substring(line, at).trim.isEmpty) line else at
      }
      val endAt = lineOf(end)
      val startAt = lineOf(start)
      val indent = manifest.substring(startAt, start).takeWhile(_ == ' ')
      val missing = permissions.filterNot(p => manifest.contains("\"" + p + "\""))
      val uses = missing.map(p => s"""$indent<uses-permission android:name="$p" />\n""").mkString
      Right(manifest.substring(0, startAt) + uses + manifest.substring(startAt, endAt) + entries(app) +
        manifest.substring(endAt))
    }
  }

  /** The shrinker's rules for what the manifest names and the receiver loads by name: the app's object, the receiver
    * and the job service. */
  def rules(app: String): String =
    "# What runs with the app closed, written by sbt-everyui from everyuiBackground: the object the receiver and\n" +
      "# the job service load by name, and the two the manifest names.\n" +
      s"-keep class $app$$ { *; }\n-keep class $receiverClass { *; }\n-keep class $jobsClass { *; }\n"

  /** Why `app` is not a `BackgroundApp` among the compiled classes, or None where it is or where the classes could
    * not be read to tell (the second element says why not). Read through reflection in a loader of its own. */
  def check(classpath: Seq[File], app: String): (Option[String], Option[String]) = {
    val loader = new java.net.URLClassLoader(classpath.map(_.toURI.toURL).toArray, ClassLoader.getPlatformClassLoader)
    try {
      val wanted = loader.loadClass(appTrait)
      val found =
        try Some(loader.loadClass(app + "$"))
        catch { case _: ClassNotFoundException => None }
      found match {
        case None => (Some(s"everyuiBackground names $app, but there is no such object"), None)
        case Some(c) if !wanted.isAssignableFrom(c) =>
          (Some(s"everyuiBackground names $app, which is not a BackgroundApp"), None)
        case Some(_) => (None, None)
      }
    }
    catch {
      case t: Throwable => (None, Some(t.toString))
    }
    finally loader.close()
  }
}
