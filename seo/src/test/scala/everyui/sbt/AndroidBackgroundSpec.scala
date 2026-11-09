package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What sbt-everyui writes for what runs with an Android app closed (AndroidBackground): the manifest's entries
  * (EveryUI's receiver and provider, WorkManager's components) and the permissions they need, put after the widgets'
  * where there are any, the rules, and the manifests and names it refuses. */
class AndroidBackgroundSpec extends AnyWordSpec with Matchers {
  private val manifest =
    """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
      |  <uses-permission android:name="android.permission.INTERNET" />
      |  <application android:label="Example">
      |    <activity android:name="com.example.Main" />
      |  </application>
      |</manifest>
      |""".stripMargin
  private val permissions = Seq("RECEIVE_BOOT_COMPLETED", "WAKE_LOCK", "ACCESS_NETWORK_STATE")
    .map(p => s"""  <uses-permission android:name="android.permission.$p" />\n""").mkString

  "The background's manifest entries" should {
    "go at the end of the application: the object's name, the receiver and the provider" in {
      val written = AndroidBackground.manifest(manifest, "com.example.Background").toOption.get
      written should endWith("  </application>\n</manifest>\n")
      written should include(
        """<meta-data android:name="everyui.background" android:value="com.example.Background" />""")
      written should include("""<receiver android:name="everyui.render.BackgroundReceiver" android:exported="false">""")
      written should include("""<action android:name="android.intent.action.MY_PACKAGE_REPLACED" />""")
      written should include(
        """<provider android:name="everyui.render.BackgroundStart" """ +
          """android:authorities="com.example.everyui-background"""")
      written.indexOf("everyui.background\"") should be > written.indexOf("com.example.Main")
    }
    "declare WorkManager's components as its own manifest does, but its foreground service" in {
      val written = AndroidBackground.manifest(manifest, "com.example.Background").toOption.get
      val work = "androidx.work.impl."
      written should include(s"""<service android:name="${work}background.systemjob.SystemJobService" """ +
        """android:exported="true"""")
      written should include(
        """android:directBootAware="false" android:permission="android.permission.BIND_JOB_SERVICE" />""")
      written should include(s"""<service android:name="${work}background.systemalarm.SystemAlarmService" """ +
        """android:enabled="false"""")
      written should include(s"""<receiver android:name="${work}utils.ForceStopRunnable$$BroadcastReceiver"""")
      written should include("""<action android:name="androidx.work.diagnostics.REQUEST_DIAGNOSTICS" />""")
      written should not include "SystemForegroundService"
      written should not include "androidx.startup"
    }
    "declare the permissions they need, before the application, each once" in {
      val written = AndroidBackground.manifest(manifest, "com.example.Background").toOption.get
      written should include("  <uses-permission android:name=\"android.permission.INTERNET\" />\n" + permissions +
        "  <application android:label=\"Example\">")
      val declared = manifest.replace("<application",
        "<uses-permission android:name=\"android.permission.WAKE_LOCK\" />\n  <application")
      val again = AndroidBackground.manifest(declared, "com.example.Background").toOption.get
      again.split("permission.WAKE_LOCK").length shouldBe 2
      again.split("RECEIVE_BOOT_COMPLETED").length shouldBe 2
    }
    "leave the rest of the manifest as it was" in {
      val written = AndroidBackground.manifest(manifest, "com.example.Background").toOption.get
      val entries = AndroidBackground.entries("com.example.Background", "com.example")
      written.replace(entries, "").replace(permissions, "") shouldBe manifest
    }
    "follow the widgets' entries where there are both" in {
      val widgets = AndroidWidgets.manifest(manifest, "com.example.Widgets",
        Seq(AndroidWidget("inbox", "Inbox", Seq("compact")))).toOption.get
      val both = AndroidBackground.manifest(widgets, "com.example.Background").toOption.get
      both should include("everyui.widget.generated.InboxWidgetReceiver")
      both.indexOf("everyui.render.BackgroundReceiver") should be > both.indexOf("InboxWidgetReceiver")
      both.split("</application>").length shouldBe 2
    }
    "refuse a manifest with no application or no package, or one that declares the background or WorkManager" in {
      AndroidBackground.manifest("<manifest></manifest>", "com.example.Background").isLeft shouldBe true
      AndroidBackground.manifest(manifest.replace(" package=\"com.example\"", ""), "com.example.Background")
        .left.toOption.get should include("names no package")
      val declared = manifest.replace("<activity", "<receiver android:name=\"everyui.render.BackgroundReceiver\" />\n" +
        "    <activity")
      AndroidBackground.manifest(declared, "com.example.Background").left.toOption.get should include(
        "leave it to everyuiBackground")
      val work = manifest.replace("<activity", "<service android:name=\"" +
        "androidx.work.impl.background.systemjob.SystemJobService\" />\n    <activity")
      AndroidBackground.manifest(work, "com.example.Background").isLeft shouldBe true
      val written = AndroidBackground.manifest(manifest, "com.example.Background").toOption.get
      AndroidBackground.manifest(written, "com.example.Background").isLeft shouldBe true
    }
  }

  "The background's name" should {
    "be an object's full name" in {
      AndroidBackground.problem("com.example.Background") shouldBe None
      AndroidBackground.problem("Background") shouldBe None
      AndroidBackground.problem("") should not be None
      AndroidBackground.problem("com.example.") should not be None
      AndroidBackground.problem("com.example.Back ground") should not be None
      AndroidBackground.problem("com.\"example\"") should not be None
    }
    "say it cannot tell where the classes cannot be read" in {
      val (wrong, unread) = AndroidBackground.check(Nil, "com.example.Background")
      wrong shouldBe None
      unread should not be None
    }
  }

  "The background's rules" should {
    "keep the object, EveryUI's receiver, provider and worker, and WorkManager's components" in {
      val rules = AndroidBackground.rules("com.example.Background")
      rules should include("-keep class com.example.Background$ { *; }")
      Seq("BackgroundReceiver", "BackgroundStart", "BackgroundWorker").foreach(c =>
        rules should include(s"-keep class everyui.render.$c { *; }"))
      AndroidBackground.workComponents.foreach(c => rules should include(s"-keep class $c { <init>(); }"))
    }
    "keep what WorkManager and Room make by name" in {
      val rules = AndroidBackground.rules("com.example.Background")
      rules should include("-keepnames class * extends androidx.work.ListenableWorker")
      rules should include("-keep class * extends androidx.room.RoomDatabase { void <init>(); }")
      rules should include("-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }")
    }
  }

  "An archive's resources" should {
    "be linked only where its classes read its own R class" in {
      AndroidLibraries.packageOf("""<manifest xmlns:android="x"
                                   |    package="androidx.work" >""".stripMargin) shouldBe Some("androidx.work")
      AndroidLibraries.packageOf("<manifest xmlns:android=\"x\">") shouldBe None
      val reads = "\u0001\u0000\u0019androidx/work/R$bool\u0007".getBytes("ISO-8859-1")
      val other = "\u0001\u0000\u0019androidx/core/R$bool\u0007".getBytes("ISO-8859-1")
      AndroidLibraries.readsOwnR("androidx.work", Iterator(other, reads)) shouldBe true
      AndroidLibraries.readsOwnR("androidx.work", Iterator(other)) shouldBe false
      AndroidLibraries.readsOwnR("androidx.work", Iterator.empty) shouldBe false
    }
  }
}
