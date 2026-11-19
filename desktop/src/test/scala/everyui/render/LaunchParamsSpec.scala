package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A TV's launch parameters as the app is handed them (LaunchParams): webOS's `launchParams` at the start and a
  * relaunch's `detail`, JSON both. */
class LaunchParamsSpec extends AnyWordSpec with Matchers {
  "parse" should {
    "hand strings over as they are" in {
      LaunchParams.parse("""{"open": "/title/12", "contentId": "12", "mediaType": "movie"}""") shouldBe
        Map("open" -> "/title/12", "contentId" -> "12", "mediaType" -> "movie")
    }
    "undo a string's escapes" in {
      LaunchParams.parse("""{"q":"a \"b\" \\ c\nd é \/"}""") shouldBe Map("q" -> "a \"b\" \\ c\nd é /")
    }
    "write numbers and true or false as they are written, and leave null out" in {
      LaunchParams.parse("""{ "n": 12, "x": -1.5e3, "on": true, "off": false, "none": null }""") shouldBe
        Map("n" -> "12", "x" -> "-1.5e3", "on" -> "true", "off" -> "false")
    }
    "hand an object or a list over as its JSON" in {
      LaunchParams.parse("""{"query":{"a":"}","b":[1,{"c":"]"}]},"ids":[1, 2]}""") shouldBe
        Map("query" -> """{"a":"}","b":[1,{"c":"]"}]}""", "ids" -> "[1, 2]")
    }
    "make no parameters of nothing, an empty object, or what is not an object" in {
      List(null, "", "  ", "{}", "[1]", "\"open\"", "12").foreach(j => LaunchParams.parse(j) shouldBe Map.empty)
    }
    "make no parameters of JSON that does not parse" in {
      List("{", """{"a":}""", """{"a":"b"""", """{"a":"b",}""", """{"a":"b"} x""", """{"a":"\u12"}""")
        .foreach(j => LaunchParams.parse(j) shouldBe Map.empty)
    }
  }
}
