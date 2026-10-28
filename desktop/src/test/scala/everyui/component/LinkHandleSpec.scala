package everyui.component

import everyui.markdown.MarkdownView
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Links of a scheme of the app's own (`Link.handle`): handed to it wherever they are followed, a link it answers
  * false left to be followed as before, a handler replaced or taken away, and such a link never an address. */
class LinkHandleSpec extends AnyWordSpec with Matchers {
  "A link of the app's own scheme" should {
    "be handed to the app, from a Link, Markdown or rich text" in {
      var heard: List[String] = Nil
      val stop = Link.handle("voidcraft-share") { href =>
        heard = heard :+ href
        true
      }
      try {
        Link.follow("voidcraft-share:f81c")
        MarkdownView.follow("Voidcraft-Share:A1")
        new RichText().follow("voidcraft-share:b2")
        heard shouldBe List("voidcraft-share:f81c", "Voidcraft-Share:A1", "voidcraft-share:b2")
      } finally stop()
    }
    "be followed as before where the app answers false, and nowhere once it is taken away" in {
      var heard = 0
      val stop = Link.handle("voidcraft-share:") { _ =>
        heard += 1
        false
      }
      Link.answers("voidcraft-share:x") shouldBe true
      Link.follow("voidcraft-share:x")
      heard shouldBe 1
      stop()
      Link.answers("voidcraft-share:x") shouldBe false
      Link.follow("voidcraft-share:x")
      heard shouldBe 1
    }
    "be answered by the last handler given, the first's taking-away leaving it" in {
      var said: List[String] = Nil
      val first = Link.handle("note")(_ => { said = said :+ "first"; true })
      val second = Link.handle("note")(_ => { said = said :+ "second"; true })
      first()
      Link.follow("note:1")
      said shouldBe List("second")
      second()
      Link.answers("note:1") shouldBe false
    }
    "be the app's own rather than an address, where no platform opens its scheme" in {
      val stop = Link.handle("voidcraft-share")(_ => true)
      val https = Link.handle("https")(_ => false)
      try {
        Link.appOwn("voidcraft-share:f81c") shouldBe true
        Link.appOwn(RichText.action + "mention:Ada") shouldBe true
        Link.appOwn("https://everyui.com") shouldBe false
        Link.appOwn("other-scheme:x") shouldBe false
      } finally {
        stop()
        https()
      }
    }
    "know a scheme from a path" in {
      Link.schemeOf("voidcraft-share:f81c") shouldBe Some("voidcraft-share")
      Link.schemeOf("HTTPS://x") shouldBe Some("https")
      Link.schemeOf("/docs/a:b") shouldBe None
      Link.schemeOf("readme.md") shouldBe None
      Link.schemeOf("1abc:x") shouldBe None
    }
  }
}
