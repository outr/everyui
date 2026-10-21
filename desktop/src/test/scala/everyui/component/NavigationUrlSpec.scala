package everyui.component

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NavigationUrlSpec extends AnyWordSpec with Matchers {
  "Navigation URLs" should {
    "reject executable and local document schemes, including browser-normalized controls" in {
      List("javascript:alert(1)", " JavaScript:alert(1)", "java\nscript:alert(1)", "\u0000javascript:alert(1)",
        "data:text/html,test", "blob:https://example.com/id", "file:///etc/passwd", "vbscript:msgbox(1)", "about:blank")
        .foreach(url => withClue(url)(NavigationUrl.safe(url) shouldBe false))
    }
    "retain ordinary external links, app paths and phone navigation" in {
      List("https://example.com/a", "http://example.com", "mailto:me@example.com", "tel:+15551234567", "geo:0,0?q=NYC",
        "google.navigation:q=NYC", "/c/abc", "#details", "./folder/file", "//example.com/path", "/path?value=javascript:test")
        .foreach(url => withClue(url)(NavigationUrl.safe(url) shouldBe true))
    }
  }
}
