package everyui.location

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The steps to the location with the app closed on Android (`BackgroundAsk`): the location itself first, Android 10's
  * prompt, the settings page from 11, nothing to ask where the manifest does not declare it, and a refusal or a
  * revocation asked again only as the app asks. */
class BackgroundAskSpec extends AnyWordSpec with Matchers {
  import BackgroundAsk.*

  "Asking for the location with the app closed" should {
    "be the location itself before Android 10" in {
      step(28, declared = false, foreground = false, background = false) shouldBe ForegroundFirst
      step(28, declared = false, foreground = true, background = false) shouldBe BackgroundGiven
    }

    "ask for the location itself first, then Android 10's prompt or, from 11, the settings page" in {
      step(29, declared = true, foreground = false, background = false) shouldBe ForegroundFirst
      step(29, declared = true, foreground = true, background = false) shouldBe BackgroundPrompt
      step(30, declared = true, foreground = true, background = false) shouldBe BackgroundSettings
      step(35, declared = true, foreground = true, background = false) shouldBe BackgroundSettings
    }

    "need nothing once given" in {
      step(34, declared = true, foreground = true, background = true) shouldBe BackgroundGiven
    }

    "ask nothing where the manifest does not declare it, whatever else is given" in {
      step(30, declared = false, foreground = true, background = false) shouldBe BackgroundUndeclared
      step(30, declared = false, foreground = false, background = false) shouldBe BackgroundUndeclared
    }

    "after a refusal or a revocation, be what it was before it was given: asked again only as the app asks" in {
      // revoked: the location itself kept, the one with the app closed taken back
      step(34, declared = true, foreground = true, background = false) shouldBe BackgroundSettings
      // all of it taken back
      step(34, declared = true, foreground = false, background = false) shouldBe ForegroundFirst
      // the one with the app closed is nothing without the location itself
      step(34, declared = true, foreground = false, background = true) shouldBe ForegroundFirst
    }
  }
}
