package everyui.render

import everyui.apps.{AppLink, Apps}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Opening another app (`everyui.apps.Apps`): what an LG TV's Application Manager is sent and what its answers say
  * (WebOSApps), the intent Android starts (IntentPlan), and the desktop, which opens none. */
class AppsSpec extends AnyWordSpec with Matchers {
  private val title = "https://www.netflix.com/watch/80100172"

  "WebOSApps" should {
    "send launch the app's id and its params as strings" in {
      WebOSApps.launch(AppLink("netflix", params = Map("contentTarget" -> title))) shouldBe
        "{\"id\":\"netflix\",\"params\":{\"contentTarget\":\"" + title + "\"}}"
      WebOSApps.launch(AppLink("youtube.leanback.v4")) shouldBe "{\"id\":\"youtube.leanback.v4\",\"params\":{}}"
    }
    "send its JSON params as they are, in place of the strings" in {
      WebOSApps.launch(AppLink("amazon", params = Map("ignored" -> "x"), json = """ {"contentId": 12, "deep": {"a": [1]}} """)) shouldBe
        """{"id":"amazon","params":{"contentId": 12, "deep": {"a": [1]}}}"""
    }
    "quote what JSON must" in {
      WebOSApps.launch(AppLink("a\"b", params = Map("q" -> "line\nnext\\ \u0001"))) shouldBe
        "{\"id\":\"a\\\"b\",\"params\":{\"q\":\"line\\nnext\\\\ \\u0001\"}}"
    }
    "ask getAppLoadStatus by appId" in {
      WebOSApps.loadStatus("hulu") shouldBe "{\"appId\":\"hulu\"}"
    }
    "read a launch taken or refused" in {
      WebOSApps.launched("""{"returnValue":true}""") shouldBe true
      WebOSApps.launched("""{"returnValue":false,"errorCode":-101,"errorText":"no such app"}""") shouldBe false
      WebOSApps.launched("") shouldBe false
    }
    "read an app there, not there, or a status that could not be had" in {
      WebOSApps.exists("""{"returnValue":true,"exist":true}""") shouldBe Some(true)
      WebOSApps.exists("""{"returnValue":true,"exist":false}""") shouldBe Some(false)
      WebOSApps.exists("""{"returnValue":false,"errorCode":-1,"errorText":"Denied method call"}""") shouldBe None
      WebOSApps.exists("""{"returnValue":true}""") shouldBe None
      WebOSApps.exists("") shouldBe None
    }
  }

  "IntentPlan" should {
    "start a package's launcher where the link has only its id, with its params as extras" in {
      IntentPlan.of(AppLink("com.netflix.ninja", params = Map("source" -> "nabo"))) shouldBe
        IntentPlan("", "", "", "", "com.netflix.ninja", launcher = true, Map("source" -> "nabo"))
    }
    "view a data URI in the package" in {
      IntentPlan.of(AppLink("com.netflix.ninja", uri = title)) shouldBe
        IntentPlan("", IntentPlan.View, title, "", "com.netflix.ninja", launcher = false, Map.empty)
    }
    "view it in whichever app takes it where no package is named, with the action and the type asked" in {
      IntentPlan.of(AppLink("", uri = "content://x/1", action = "android.intent.action.EDIT", mime = "video/mp4")) shouldBe
        IntentPlan("", "android.intent.action.EDIT", "content://x/1", "video/mp4", "", launcher = false, Map.empty)
    }
    "take a whole intent URI in place of the rest" in {
      val uri = "intent://watch/1#Intent;scheme=nflx;package=com.netflix.ninja;end"
      IntentPlan.of(AppLink("ignored", uri = title, intent = uri)) shouldBe
        IntentPlan(uri, "", "", "", "", launcher = false, Map.empty)
    }
    "send an action alone to the package" in {
      IntentPlan.of(AppLink("com.example", action = "com.example.PLAY")) shouldBe
        IntentPlan("", "com.example.PLAY", "", "", "com.example", launcher = false, Map.empty)
    }
    "have nothing to launch for an empty link" in {
      IntentPlan.of(AppLink("")).launcher shouldBe false
    }
  }

  "The desktop" should {
    "open no other app, and not say which are installed" in {
      var opened = Option.empty[Boolean]
      var found = Option.empty[Option[Boolean]]
      Apps.launch(AppLink("netflix")).map(o => opened = Some(o)).start()
      Apps.installed("netflix").map(f => found = Some(f)).start()
      Apps.launches shouldBe false
      opened shouldBe Some(false)
      found shouldBe Some(None)
    }
  }
}
