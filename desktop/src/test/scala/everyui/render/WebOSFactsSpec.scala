package everyui.render

import everyui.event.{Key, Keys}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What an LG TV's runtime says of itself, read as the web backend reads it there (WebOSFacts): whether the page is on
  * one, which webOS it is, its remote's keys, and how Back at the app's root leaves it. */
class WebOSFactsSpec extends AnyWordSpec with Matchers {
  // LG's user agents (its "Web API and Web Engine" table)
  private def agent(chrome: String): String =
    s"Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36 WebAppManager"
  private val webOS1 = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.41 (KHTML, like Gecko) Large Screen WebAppManager Safari/537.41"
  private val desktop = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"

  "detect" should {
    "find webOS by its runtime's object, either of them" in {
      WebOSFacts.detect(webOSSystem = true, palmSystem = false, desktop) shouldBe true
      WebOSFacts.detect(webOSSystem = false, palmSystem = true, desktop) shouldBe true
    }
    "find it by the user agent where the objects are not there yet" in {
      WebOSFacts.detect(webOSSystem = false, palmSystem = false, agent("108.0.5359.211")) shouldBe true
      WebOSFacts.detect(webOSSystem = false, palmSystem = false, webOS1) shouldBe true
    }
    "not find it in a browser" in {
      WebOSFacts.detect(webOSSystem = false, palmSystem = false, desktop) shouldBe false
      WebOSFacts.detect(webOSSystem = false, palmSystem = false,
        "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko) 76.0.3809.146/6.0 TV Safari/537.36") shouldBe false
    }
  }

  "version" should {
    "name each release by the Chromium in its user agent, as LG's table has it" in {
      List("38.0.2125.122" -> "3", "53.0.2785.34" -> "4", "68.0.3440.106" -> "5", "79.0.3945.79" -> "6",
        "87.0.4280.88" -> "22", "94.0.4606.128" -> "23", "108.0.5359.211" -> "24", "120.0.6099.270" -> "25")
        .foreach { case (chrome, release) => WebOSFacts.version(agent(chrome), "") shouldBe release }
    }
    "read a release the table does not have from deviceInfo" in {
      WebOSFacts.version(agent("132.0.0.0"), """{"modelName":"OLED55C6","platformVersionMajor":"11"}""") shouldBe "26"
      WebOSFacts.version(agent("132.0.0.0"), """{"platformVersionMajor":6}""") shouldBe "6"
    }
    "say one newer than the table with nothing else to go on as after its last" in {
      WebOSFacts.version(agent("132.0.0.0"), "") shouldBe "25+"
    }
    "tell webOS 1 and 2 by their WebKit" in {
      WebOSFacts.version(webOS1, "") shouldBe "1"
      WebOSFacts.version(webOS1.replace("537.41", "538.2"), "") shouldBe "2"
    }
    "say nothing in a browser" in {
      WebOSFacts.version("Mozilla/5.0 (X11; Linux x86_64) Gecko/20100101 Firefox/140.0", "") shouldBe ""
    }
  }

  "keys" should {
    "make the remote's Back EveryUI's Back" in {
      Keys.of(WebOSFacts.keys(461)) shouldBe Key.Back
    }
    "make the transport keys the player's" in {
      Map(415 -> Key.MediaPlay, 19 -> Key.MediaPause, 413 -> Key.MediaStop, 412 -> Key.MediaRewind,
        417 -> Key.MediaFastForward).foreach { case (code, key) => Keys.of(WebOSFacts.keys(code)) shouldBe key }
    }
    "name the coloured keys as the web does, and channel up and down as Page Up and Down" in {
      List(403, 404, 405, 406).map(WebOSFacts.keys) shouldBe List("ColorF0Red", "ColorF1Green", "ColorF2Yellow", "ColorF3Blue")
      List(403, 404, 405, 406).map(c => Keys.of(WebOSFacts.keys(c))).distinct shouldBe List(Key.Other)
      Keys.of(WebOSFacts.keys(33)) shouldBe Key.PageUp
      Keys.of(WebOSFacts.keys(34)) shouldBe Key.PageDown
    }
    "leave the arrows, OK and the letters to the engine's own names" in {
      List(13, 37, 38, 39, 40, 65).flatMap(WebOSFacts.keys.get) shouldBe Nil
    }
  }

  "exit" should {
    "call webOSTV.js's platformBack where the app bundles it" in {
      var called = List.empty[String]
      WebOSFacts.exit(Some(() => called :+= "webOS"), Some(() => called :+= "webOSSystem"),
        Some(() => called :+= "PalmSystem"), () => called :+= "close") shouldBe "webOS"
      called shouldBe List("webOS")
    }
    "call the runtime's own, webOSSystem's or else PalmSystem's, without it" in {
      var called = List.empty[String]
      WebOSFacts.exit(None, Some(() => called :+= "webOSSystem"), Some(() => called :+= "PalmSystem"),
        () => called :+= "close") shouldBe "webOSSystem"
      WebOSFacts.exit(None, None, Some(() => called :+= "PalmSystem"), () => called :+= "close") shouldBe "PalmSystem"
      called shouldBe List("webOSSystem", "PalmSystem")
    }
    "close the window where there is no platformBack" in {
      var closed = false
      WebOSFacts.exit(None, None, None, () => closed = true) shouldBe "close"
      closed shouldBe true
    }
  }
}
