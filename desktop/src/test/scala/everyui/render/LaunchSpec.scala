package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A launch that reaches the app once it is running (`Platform.onLaunch`): each listener hears it with what it was
  * launched with, in the order they asked, until it stops listening. The platform says so (`launched`): a Roku's
  * resume or deep link, an Android intent; here the spec does. */
class LaunchSpec extends AnyWordSpec with Matchers {
  "A later launch" should {
    "reach each listener with its parameters, until it stops listening" in {
      var heard = List.empty[String]
      val first = Platform.onLaunch(params => heard = heard :+ ("first " + params.getOrElse("open", "")))
      val second = Platform.onLaunch(params => heard = heard :+ ("second " + params.getOrElse("open", "")))
      Platform.launched(Map("open" -> "/onboarding?step=done&preview=1"))
      heard shouldBe List("first /onboarding?step=done&preview=1", "second /onboarding?step=done&preview=1")
      first()
      Platform.launched(Map("open" -> "/settings"))
      heard.last shouldBe "second /settings"
      heard.size shouldBe 3
      second()
      Platform.launched(Map("open" -> "/home"))
      heard.size shouldBe 3
    }
  }
}
