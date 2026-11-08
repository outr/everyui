package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What sbt-everyui writes for what runs with an Android app closed (AndroidBackground): the manifest's entries and
  * the permission they need, put after the widgets' where there are any, the rules, and the names it refuses. */
class AndroidBackgroundSpec extends AnyWordSpec with Matchers {
  private val manifest =
    """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
      |  <uses-permission android:name="android.permission.INTERNET" />
      |  <application android:label="Example">
      |    <activity android:name="com.example.Main" />
      |  </application>
      |</manifest>
      |""".stripMargin

  "The background's manifest entries" should {
    "go at the end of the application: the object's name, the receiver and the job service" in {
      val written = AndroidBackground.manifest(manifest, "com.example.Background").toOption.get
      written should endWith("  </application>\n</manifest>\n")
      written should include(
        """<meta-data android:name="everyui.background" android:value="com.example.Background" />""")
      written should include("""<receiver android:name="everyui.render.BackgroundReceiver" android:exported="false">""")
      written should include("""<action android:name="android.intent.action.BOOT_COMPLETED" />""")
      written should include("""<action android:name="android.intent.action.MY_PACKAGE_REPLACED" />""")
      written should include("""<service android:name="everyui.render.BackgroundJobs" android:exported="true"""")
      written should include("""android:permission="android.permission.BIND_JOB_SERVICE" />""")
      written.indexOf("everyui.background\"") should be > written.indexOf("com.example.Main")
    }
    "declare the permission to hear the device start, before the application, once" in {
      val written = AndroidBackground.manifest(manifest, "com.example.Background").toOption.get
      written should include(
        """  <uses-permission android:name="android.permission.INTERNET" />
          |  <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
          |  <application android:label="Example">""".stripMargin)
      val declared = manifest.replace("<application",
        "<uses-permission android:name=\"android.permission.RECEIVE_BOOT_COMPLETED\" />\n  <application")
      val again = AndroidBackground.manifest(declared, "com.example.Background").toOption.get
      again.split("RECEIVE_BOOT_COMPLETED").length shouldBe 2
    }
    "leave the rest of the manifest as it was" in {
      val written = AndroidBackground.manifest(manifest, "com.example.Background").toOption.get
      val entries = AndroidBackground.entries("com.example.Background")
      val permission = "  <uses-permission android:name=\"android.permission.RECEIVE_BOOT_COMPLETED\" />\n"
      written.replace(entries, "").replace(permission, "") shouldBe manifest
    }
    "follow the widgets' entries where there are both" in {
      val widgets = AndroidWidgets.manifest(manifest, "com.example.Widgets",
        Seq(AndroidWidget("inbox", "Inbox", Seq("compact")))).toOption.get
      val both = AndroidBackground.manifest(widgets, "com.example.Background").toOption.get
      both should include("everyui.widget.generated.InboxWidgetReceiver")
      both.indexOf("everyui.render.BackgroundReceiver") should be > both.indexOf("InboxWidgetReceiver")
      both.split("</application>").length shouldBe 2
    }
    "refuse a manifest with no application, or one that declares the background itself" in {
      AndroidBackground.manifest("<manifest></manifest>", "com.example.Background").isLeft shouldBe true
      val declared = manifest.replace("<activity", "<receiver android:name=\"everyui.render.BackgroundReceiver\" />\n" +
        "    <activity")
      AndroidBackground.manifest(declared, "com.example.Background").left.toOption.get should include(
        "leave it to everyuiBackground")
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
    "keep the object, the receiver and the job service" in {
      val rules = AndroidBackground.rules("com.example.Background")
      rules should include("-keep class com.example.Background$ { *; }")
      rules should include("-keep class everyui.render.BackgroundReceiver { *; }")
      rules should include("-keep class everyui.render.BackgroundJobs { *; }")
      rules.linesIterator.filter(_.startsWith("-keep")).size shouldBe 3
    }
  }
}
