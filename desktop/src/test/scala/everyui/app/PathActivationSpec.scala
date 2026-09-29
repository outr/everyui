package everyui.app

import everyui.app.screen.PathActivation
import everyui.net.URLPath
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A screen is found by its path whatever query or fragment the page's URL carries. */
class PathActivationSpec extends AnyWordSpec with Matchers {
  private def at(p: String, pat: Option[String] = None): PathActivation = new PathActivation {
    override def path: URLPath = URLPath(p)
    override def pattern: Option[String] = pat
  }

  "A plain path" should {
    "match with or without a query or fragment" in {
      val signup = at("/signup.html")
      signup.matches(URLPath("/signup.html")) shouldBe true
      signup.matches(URLPath("/signup.html?token=x")) shouldBe true
      signup.matches(URLPath("/signup.html#top")) shouldBe true
      signup.matches(URLPath("/signup.html?x=1#top")) shouldBe true
      signup.matches(URLPath("/signin.html?x=1")) shouldBe false
    }
  }
  "A pattern" should {
    "match its shape with a query or fragment after it" in {
      val run = at("/run", Some("/run/:id"))
      run.matches(URLPath("/run/42")) shouldBe true
      run.matches(URLPath("/run/42?tab=log")) shouldBe true
      run.matches(URLPath("/run/42#end")) shouldBe true
      run.matches(URLPath("/run/42/log")) shouldBe false
    }
  }
}
