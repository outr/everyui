package everyui.emoji

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Emoji by name (GitHub's table): every one there, found by its names and its words, its first name its own, put in a
  * skin tone where it takes one; and words turned into emoji where they name one, in Slack's tones too, but not in code
  * (a span or a block), nor where a name touches the words around it (a time, an address). */
class EmojiSpec extends AnyWordSpec with Matchers {
  "Emoji" should {
    "know every emoji in GitHub's table, by its names, in its categories" in {
      Emoji.all.size shouldBe 1870
      Emoji.categories.head shouldBe "Smileys & Emotion"
      Emoji.lookup("tada") shouldBe Some("🎉")
      Emoji.lookup("+1") shouldBe Some("👍")
      Emoji.lookup("thumbsup") shouldBe Some("👍")
      Emoji.lookup("White_Check_Mark") shouldBe Some("✅")
      Emoji.lookup("no_such_emoji") shouldBe None
    }
    "give an emoji's own name, whatever its tone" in {
      Emoji.shortcode("👍") shouldBe Some("+1")
      Emoji.shortcode("👍🏽") shouldBe Some("+1")
      Emoji.shortcode("🎉") shouldBe Some("tada")
      Emoji.shortcode("a") shouldBe None
    }
    "find emoji by their names and words, a name first" in {
      Emoji.search("tada").head.emoji shouldBe "🎉"
      Emoji.search("party").map(_.emoji) should contain("🎉")
      Emoji.search("thumbs up").map(_.emoji) should contain("👍")
      Emoji.search("") shouldBe Vector.empty
      Emoji.search("x", limit = 5).size should be <= 5
    }
    "put one in a skin tone where it takes one, and leave the rest" in {
      Emoji.withTone("👍", 3) shouldBe "👍🏽"
      Emoji.withTone("👍🏽", 5) shouldBe "👍🏿"
      Emoji.withTone("👍🏽", 0) shouldBe "👍"
      Emoji.withTone("✌️", 1) shouldBe "✌🏻"
      Emoji.withTone("🎉", 2) shouldBe "🎉"
    }
    "turn names into emoji, in Slack's tones too" in {
      Emoji.convert("Shipped :tada: and :+1:") shouldBe "Shipped 🎉 and 👍"
      Emoji.convert(":white_check_mark: done") shouldBe "✅ done"
      Emoji.convert("ok :+1::skin-tone-4:") shouldBe "ok 👍🏽"
      Emoji.convert(":tada::tada:") shouldBe "🎉🎉"
      Emoji.convert("no :nosuchthing: here") shouldBe "no :nosuchthing: here"
    }
    "leave code, times and addresses as they are" in {
      Emoji.convert("see `:tada:` and :tada:") shouldBe "see `:tada:` and 🎉"
      Emoji.convert("```\n:tada:\n```\n:tada:") shouldBe "```\n:tada:\n```\n🎉"
      Emoji.convert("at 12:30:45") shouldBe "at 12:30:45"
      Emoji.convert("http://host:smile:8080") shouldBe "http://host:smile:8080"
      Emoji.convert("plain text") shouldBe "plain text"
    }
  }
}
