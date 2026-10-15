package everyui.i18n

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** EveryUI's own words come in every language it ships, each with the same placeholders as the English. */
class WordsSpec extends AnyWordSpec with Matchers {
  private val shipped = List("en", "de", "es", "fr", "it", "nl", "pt", "tr", "ar", "zh", "zh_Hant")
  private val english = Words.languages("en")
  private val tabs = List("add", "close", "closeOthers", "closeAll", "openRight", "openDown", "moveRight", "moveLeft",
    "moveDown", "moveUp", "unsplit", "nextGroup", "previousGroup", "maximize", "unmaximize", "none").map("tabs." + _)

  /** The names of a message's arguments: `{n}`, `{n, plural, …}`, not a plural's own branches. */
  private def arguments(message: String): Set[String] = "\\{(\\w+)[,}]".r.findAllMatchIn(message).map(_.group(1)).toSet

  "Words" should {
    "ship every language" in {
      shipped.foreach(code => Words.languages.keySet should contain(code))
    }
    "have every key in every language" in {
      shipped.foreach { code =>
        withClue(code + ": ")(Words.languages(code).keySet shouldBe english.keySet)
      }
    }
    "keep each message's placeholders in every language" in {
      for (code <- shipped; (key, message) <- Words.languages(code)) {
        withClue(code + " " + key + ": ")(arguments(message) shouldBe arguments(english(key)))
      }
    }
    "say the tab menu's words in every language" in {
      english.keySet should contain allElementsOf tabs
      shipped.filter(_ != "en").foreach { code =>
        withClue(code + ": ")(tabs.count(k => Words.languages(code)(k) != english(k)) shouldBe tabs.size)
      }
    }
  }
}
