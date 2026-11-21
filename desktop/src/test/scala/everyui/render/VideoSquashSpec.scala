package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Whether an engine squashes what lies over a video (`TvEngine.squashesOverVideo`), told by its version alone: webOS
  * 3.5's Chromium 38 reads `#rrggbbaa` and squashes all the same. */
class VideoSquashSpec extends AnyWordSpec with Matchers {
  private val webOS35 = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.122 " +
    "Safari/537.36 WebAppManager"
  private val webOS3 = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) QtWebEngine/5.2.1 " +
    "Chrome/38.0.2125.122 Safari/537.36 WebAppManager"
  private val webOS4 = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.34 " +
    "Safari/537.36 WebAppManager"
  private val tizen4 = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 4.0) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "SamsungBrowser/2.1 Chrome/56.0.2924.0 TV Safari/537.36"
  private val webOS5 = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/68.0.3440.106 " +
    "Safari/537.36 WebAppManager"
  private val webOS2 = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/538.2 (KHTML, like Gecko) Large Screen " +
    "WebAppManager Safari/538.2"

  "An engine" should {
    "squash what lies over a video before Chromium 62: webOS 3 and 4, Tizen 4" in {
      List(webOS35, webOS3, webOS4, tizen4).foreach(a => TvEngine.squashesOverVideo(a) shouldBe true)
      TvEngine.squashesOverVideo("Mozilla/5.0 (X11; Linux x86_64) Chrome/61.0.3163.0 Safari/537.36") shouldBe true
    }
    "not from Chromium 62, nor where it names no Chromium" in {
      List(webOS5, webOS2, "", "Mozilla/5.0 (X11; Linux x86_64) Chrome/62.0.3202.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15")
        .foreach(a => TvEngine.squashesOverVideo(a) shouldBe false)
    }
  }
}
