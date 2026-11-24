package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What sbt-everyui writes from `everyuiLocation` (LocationUse): the Android manifest's location permissions, the one
  * with the app closed only where asked for, the location hardware not required, each once; and the iOS Info.plist's
  * reasons. */
class LocationUseSpec extends AnyWordSpec with Matchers {
  private val manifest =
    """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
      |  <uses-permission android:name="android.permission.INTERNET" />
      |  <application android:label="Example">
      |    <activity android:name="com.example.Main" />
      |  </application>
      |</manifest>
      |""".stripMargin
  private def permission(p: String): String = s"""  <uses-permission android:name="android.permission.$p" />\n"""
  private def feature(f: String): String = s"""  <uses-feature android:name="$f" android:required="false" />\n"""

  "The location's manifest entries" should {
    "declare precise and approximate, and the hardware not required, before the application" in {
      val written = LocationUse.manifest(manifest, LocationUse()).toOption.get
      written should include(permission("INTERNET") + permission("ACCESS_COARSE_LOCATION") +
        permission("ACCESS_FINE_LOCATION") + feature("android.hardware.location") +
        feature("android.hardware.location.gps") + "  <application android:label=\"Example\">")
      written should not include "ACCESS_BACKGROUND_LOCATION"
    }

    "declare the location with the app closed only with background, and nothing else of it" in {
      val written = LocationUse.manifest(manifest, LocationUse(background = true)).toOption.get
      written should include(permission("ACCESS_FINE_LOCATION") + permission("ACCESS_BACKGROUND_LOCATION"))
      // declaring asks for nothing: no receiver, no service, nothing in the application
      written.substring(written.indexOf("<application")) shouldBe manifest.substring(manifest.indexOf("<application"))
    }

    "declare each once, keeping what the manifest has" in {
      val declared = manifest.replace("  <application", permission("ACCESS_FINE_LOCATION") +
        """  <uses-feature android:name="android.hardware.location.gps" android:required="true" />""" + "\n  <application")
      val written = LocationUse.manifest(declared, LocationUse(background = true)).toOption.get
      written.split("ACCESS_FINE_LOCATION").length shouldBe 2
      written.split("android.hardware.location.gps").length shouldBe 2
      written should include("ACCESS_COARSE_LOCATION")
      LocationUse.manifest(written, LocationUse(background = true)).toOption.get shouldBe written
    }

    "refuse a manifest with no application" in {
      LocationUse.manifest("<manifest></manifest>", LocationUse()).isLeft shouldBe true
    }
  }

  "The location's Info.plist reasons" should {
    val plist = "<plist version=\"1.0\">\n<dict>\n  <key>CFBundleName</key><string>Example</string>\n</dict>\n</plist>\n"

    "say why while in use, and always with background, in the app's words" in {
      val inUse = LocationUse.infoPlist(plist, LocationUse(purpose = "To find places near you & <yours>"))
      inUse should include("<key>NSLocationWhenInUseUsageDescription</key>" +
        "<string>To find places near you &amp; &lt;yours&gt;</string>")
      inUse should not include "NSLocationAlwaysAndWhenInUseUsageDescription"
      val always = LocationUse.infoPlist(plist, LocationUse(background = true, purpose = "Places"))
      always should include("<key>NSLocationAlwaysAndWhenInUseUsageDescription</key><string>Places</string>")
      always should endWith("</string>\n</dict>\n</plist>\n")
    }

    "write nothing without a purpose, and keep the app's own reasons" in {
      LocationUse.infoPlist(plist, LocationUse(background = true)) shouldBe plist
      val own = LocationUse.infoPlist(plist, LocationUse(purpose = "Mine"))
      LocationUse.infoPlist(own, LocationUse(purpose = "Other")) shouldBe own
    }
  }
}
