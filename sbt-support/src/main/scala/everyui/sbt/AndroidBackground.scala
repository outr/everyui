package everyui.sbt

import java.io.File

/** What an Android app needs for what runs with it closed (`everyui.background`), written from its build
  * (`everyuiBackground`) rather than by hand: the meta-data naming its `BackgroundApp`, EveryUI's receiver (the device
  * started, the app updated, an ongoing notice's or a push's actions), WorkManager's components (scheduled work: the
  * entries its archive's own manifest has, which nothing here merges) with EveryUI's provider that makes WorkManager
  * ready as the process starts, the permissions they need, and the shrinker's rules for what the manifest names and
  * what is made by name.
  *
  * The plugin's tasks put these together (EveryUIPlugin); what is here is the text of each. */
object AndroidBackground {
  /** The receiver, the provider and the worker: EveryUI's own, one of each for any app. */
  val receiverClass: String = "everyui.render.BackgroundReceiver"
  val startClass: String = "everyui.render.BackgroundStart"
  val workerClass: String = "everyui.render.BackgroundWorker"

  /** WorkManager's components, as its own manifest declares them: the job service it schedules through, its alarm
    * service and reschedule receiver (off: for Android before 6), and the receivers that put its work back after a
    * force stop and answer its diagnostics broadcast. Not its foreground service: EveryUI asks for expedited work only
    * from Android 12, where it is a job, and runs no work in the foreground. */
  val workComponents: Seq[String] = Seq("androidx.work.impl.background.systemjob.SystemJobService",
    "androidx.work.impl.background.systemalarm.SystemAlarmService",
    "androidx.work.impl.background.systemalarm.RescheduleReceiver",
    "androidx.work.impl.utils.ForceStopRunnable$BroadcastReceiver",
    "androidx.work.impl.diagnostics.DiagnosticsReceiver")

  /** What the app's `BackgroundApp` must be, as the build checks it. */
  val appTrait: String = "everyui.background.BackgroundApp"

  /** The permissions the entries need: to hear the device start and keep scheduled work through it, and
    * WorkManager's, to hold the device awake while work runs and to know the network. */
  val permissions: Seq[String] = Seq("android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.WAKE_LOCK",
    "android.permission.ACCESS_NETWORK_STATE")

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

  /** The application's entries: the meta-data naming `app`, the receiver, the provider (its authority from the
    * app's package, `pkg`) and WorkManager's components. */
  def entries(app: String, pkg: String): String =
    s"""    <!-- what runs with the app closed, written by sbt-everyui from everyuiBackground -->
       |    <meta-data android:name="everyui.background" android:value="${xml(app)}" />
       |    <receiver android:name="$receiverClass" android:exported="false">
       |      <intent-filter>
       |        <action android:name="android.intent.action.BOOT_COMPLETED" />
       |        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
       |      </intent-filter>
       |    </receiver>
       |    <provider android:name="$startClass" android:authorities="${xml(pkg)}.everyui-background"
       |      android:exported="false" />
       |    <!-- WorkManager, which runs the app's scheduled work: its archive's own entries -->
       |    <service android:name="${workComponents(0)}" android:exported="true"
       |      android:directBootAware="false" android:permission="android.permission.BIND_JOB_SERVICE" />
       |    <service android:name="${workComponents(1)}" android:enabled="false" android:exported="false"
       |      android:directBootAware="false" />
       |    <receiver android:name="${workComponents(2)}" android:enabled="false" android:exported="false"
       |      android:directBootAware="false">
       |      <intent-filter>
       |        <action android:name="android.intent.action.BOOT_COMPLETED" />
       |      </intent-filter>
       |    </receiver>
       |    <receiver android:name="${xml(workComponents(3))}" android:exported="false"
       |      android:directBootAware="false" />
       |    <receiver android:name="${workComponents(4)}" android:exported="true"
       |      android:directBootAware="false" android:permission="android.permission.DUMP">
       |      <intent-filter>
       |        <action android:name="androidx.work.diagnostics.REQUEST_DIAGNOSTICS" />
       |      </intent-filter>
       |    </receiver>
       |""".stripMargin

  /** `manifest` with the entries put at the end of its application, and the permissions it does not declare already
    * put before the application. It must have one, and must not declare what is written here itself. */
  def manifest(manifest: String, app: String): Either[String, String] = {
    val start = manifest.indexOf("<application")
    val end = manifest.lastIndexOf("</application>")
    if (start < 0 || end < 0)
      Left("the app's AndroidManifest.xml has no application to put the background's entries in")
    else if (manifest.contains("\"everyui.background\"") || manifest.contains(receiverClass) ||
      manifest.contains(startClass) || workComponents.exists(c => manifest.contains(c)))
      Left("the app's AndroidManifest.xml declares its background (or WorkManager) itself: leave it to " +
        "everyuiBackground")
    else if (AndroidLibraries.packageOf(manifest).isEmpty)
      Left("the app's AndroidManifest.xml names no package, which the background's provider is named by")
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
      Right(manifest.substring(0, startAt) + uses + manifest.substring(startAt, endAt) +
        entries(app, AndroidLibraries.packageOf(manifest).get) +
        manifest.substring(endAt))
    }
  }

  /** The shrinker's rules for what the manifest names and what is made by name: the app's object, EveryUI's
    * receiver, provider and worker, WorkManager's components, and what WorkManager, Room and Kotlin's coroutines make
    * by name (their archives' own rules, for a build that does not pass those). */
  def rules(app: String): String =
    "# What runs with the app closed, written by sbt-everyui from everyuiBackground: the object EveryUI loads by\n" +
      "# name, what the manifest names, and what WorkManager makes by name.\n" +
      s"-keep class $app$$ { *; }\n" +
      Seq(receiverClass, startClass, workerClass).map(c => s"-keep class $c { *; }\n").mkString +
      workComponents.map(c => s"-keep class $c { <init>(); }\n").mkString +
      """-keepnames class * extends androidx.work.ListenableWorker
        |-keepclassmembers public class * extends androidx.work.ListenableWorker { public <init>(...); }
        |-keep class androidx.work.WorkerParameters
        |-keepnames class * extends androidx.work.InputMerger
        |-keepclassmembers class * extends androidx.work.InputMerger { void <init>(); }
        |-keep class * extends androidx.room.RoomDatabase { void <init>(); }
        |-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
        |-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
        |-dontwarn androidx.room.paging.**
        |-dontwarn org.jspecify.**
        |""".stripMargin

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
