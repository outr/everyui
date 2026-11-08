package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What sbt-everyui writes for an app's home-screen widgets on Android (AndroidWidgets): the manifest's entries, each
  * kind's provider and receiver, the rules, and the build's kinds checked against the app's own. */
class AndroidWidgetsSpec extends AnyWordSpec with Matchers {
  private val inbox = AndroidWidget("inbox", "Inbox", Seq("compact", "medium", "large"),
    description = "Unread mail & more", preview = "@mipmap/ic_launcher")
  private val calendar = AndroidWidget("next_event", "Next <event>", Seq("medium"), updateMinutes = 0,
    configurable = false)
  private val manifest =
    """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
      |  <application android:label="Example">
      |    <activity android:name="com.example.Main" />
      |  </application>
      |</manifest>
      |""".stripMargin

  "The widgets' manifest entries" should {
    "go at the end of the application, with a receiver for each kind and the configure screen" in {
      val written = AndroidWidgets.manifest(manifest, "com.example.Widgets", Seq(inbox, calendar)).toOption.get
      written should startWith(manifest.substring(0, manifest.indexOf("  </application>")))
      written should endWith("  </application>\n</manifest>\n")
      written should include("""<meta-data android:name="everyui.widgets" android:value="com.example.Widgets" />""")
      written should include(
        """<receiver android:name="everyui.widget.generated.InboxWidgetReceiver" android:label="Inbox"""")
      written should include(
        """android:name="everyui.widget.generated.NextEventWidgetReceiver" android:label="Next &lt;event&gt;"""")
      written should include("""android:resource="@xml/everyui_widget_next_event"""")
      written should include("""<meta-data android:name="everyui.widget.kind" android:value="inbox" />""")
      written should include("""android:process=":widgets"""")
      written.split("WidgetConfigureActivity").length shouldBe 2
    }
    "leave out the configure screen where no kind has one" in {
      val written = AndroidWidgets.manifest(manifest, "com.example.Widgets", Seq(calendar)).toOption.get
      written should not include "WidgetConfigureActivity"
    }
    "refuse a manifest with no application, or one that declares widgets itself" in {
      AndroidWidgets.manifest("<manifest/>", "a.B", Seq(inbox)).isLeft shouldBe true
      val already = AndroidWidgets.manifest(manifest, "a.B", Seq(inbox)).toOption.get
      AndroidWidgets.manifest(already, "a.B", Seq(inbox)).isLeft shouldBe true
    }
  }

  "A kind's provider" should {
    "run from its smallest size to its largest, with its preview, description and configure screen" in {
      val xml = AndroidWidgets.provider(inbox)
      xml should include("""android:minWidth="110dp"""")
      xml should include("""android:minHeight="110dp"""")
      xml should include("""android:maxResizeWidth="640dp"""")
      xml should include("""android:maxResizeHeight="640dp"""")
      xml should include("""android:updatePeriodMillis="1800000"""")
      xml should include("""android:previewImage="@mipmap/ic_launcher"""")
      xml should include("""android:description="@string/everyui_widget_inbox_description"""")
      xml should include("""android:configure="everyui.activity.WidgetConfigureActivity"""")
      xml should include("""android:widgetFeatures="reconfigurable|configuration_optional"""")
    }
    "be a medium one's width and a short height, never drawn again unasked, with no configure screen" in {
      val xml = AndroidWidgets.provider(calendar)
      xml should include("""android:minWidth="250dp"""")
      xml should include("""android:maxResizeHeight="190dp"""")
      xml should include("""android:updatePeriodMillis="0"""")
      xml should not include "previewImage"
      xml should not include "description"
      xml should not include "configure"
    }
    "have its description as a string resource, escaped" in {
      AndroidWidgets.strings(Seq(calendar)) shouldBe None
      AndroidWidgets.strings(Seq(inbox)).get should include(
        """<string name="everyui_widget_inbox_description">Unread mail &amp; more</string>""")
    }
  }

  "The receivers and rules" should {
    "name each kind's receiver after its key, and keep it and the app's object" in {
      AndroidWidgets.receiverClass("next_event") shouldBe "everyui.widget.generated.NextEventWidgetReceiver"
      AndroidWidgets.receiverSource(inbox) should include(
        "class InboxWidgetReceiver extends everyui.render.WidgetReceiver")
      val rules = AndroidWidgets.rules("com.example.Widgets", Seq(inbox, calendar))
      rules should include("-keep class com.example.Widgets$ { *; }")
      rules should include("-keep class everyui.widget.generated.InboxWidgetReceiver { *; }")
      rules should include("-keep class everyui.widget.generated.NextEventWidgetReceiver { *; }")
    }
  }

  "The build's kinds" should {
    "be refused where they cannot be written" in {
      AndroidWidgets.problems("a.B", Seq(inbox, calendar)) shouldBe Nil
      AndroidWidgets.problems("a.B", Nil) should have size 1
      AndroidWidgets.problems("a.B", Seq(inbox, inbox)) should have size 1
      AndroidWidgets.problems("a.B", Seq(inbox.copy(key = "Inbox"))) should have size 1
      AndroidWidgets.problems("a.B", Seq(inbox.copy(sizes = Seq("huge")))) should have size 1
      AndroidWidgets.problems("a.B", Seq(inbox.copy(updateMinutes = 5))) should have size 1
      AndroidWidgets.problems("a.B", Seq(inbox.copy(label = " "))) should have size 1
    }
    "agree with the app's own, and say where they do not" in {
      val own = Seq(("inbox", "Inbox", "Unread mail & more", Set("compact", "medium", "large")),
        ("next_event", "Next <event>", "Your next event", Set("medium")))
      AndroidWidgets.mismatches(Seq(inbox, calendar), own) shouldBe Nil
      AndroidWidgets.mismatches(Seq(inbox.copy(label = "Mail"), calendar), own) should have size 1
      AndroidWidgets.mismatches(Seq(inbox.copy(sizes = Seq("compact")), calendar), own) should have size 1
      AndroidWidgets.mismatches(Seq(inbox.copy(description = "Other"), calendar), own) should have size 1
      AndroidWidgets.mismatches(Seq(inbox), own) should have size 1
      AndroidWidgets.mismatches(Seq(inbox, calendar, inbox.copy(key = "spare")), own) should have size 1
    }
  }
}
