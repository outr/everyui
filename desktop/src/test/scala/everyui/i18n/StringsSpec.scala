package everyui.i18n

import everyui.FxSpec

/** The app's words found once at the start: where the language spoken is the template's, its file is read once. */
class StringsSpec extends FxSpec {
  private val english = Language("en", "English", "English")
  private val french = Language("fr", "Français", "French")

  /** Start Strings with `current`, waited for: the codes read, in order. */
  private def started(current: Language): List[String] = {
    var read = List.empty[String]
    var done = false
    val before = Strings.source
    fx {
      Strings.source = code => {
        read = read :+ code
        "/l10n/missing_" + code + ".arb"
      }
      Strings.start(english, current).map(_ => done = true).start()
    }
    eventually(done) shouldBe true
    fx(Strings.source = before)
    read
  }

  // English again, as the specs after this one read the kit's own words in it
  override def afterAll(): Unit = {
    started(english)
    super.afterAll()
  }

  "Strings.start" should {
    "read the template's file once where it is the language spoken" in {
      started(english) shouldBe List("en")
      fx(Strings.language().code) shouldBe "en"
    }
    "read the template's file and then the language spoken where they differ" in {
      started(french) shouldBe List("en", "fr")
      fx(Strings.language().code) shouldBe "fr"
    }
  }
}
