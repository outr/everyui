package everyui.i18n

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A clock to the second (CLDR's `jms`), and a pattern's clock fields. */
class DateTextSecondsSpec extends AnyWordSpec with Matchers {
  "A time to the second" should {
    "be the language's own clock with the seconds after the minutes" in {
      DateText.timeWithSeconds(20, 44, 28, "en") shouldBe "8:44:28 PM"
      DateText.timeWithSeconds(9, 5, 3, "de") shouldBe DateText.timeOfDay(9, 5, "de") + ":03"
      DateText.timeWithSeconds(0, 7, 59, "en") shouldBe "12:07:59 AM"
    }
    "fill a pattern's hour, minute, second and half of the day" in {
      DateText.timePattern("HH:mm:ss", 15, 7, 12, "en") shouldBe "15:07:12"
      DateText.timePattern("h:mm:ss a", 15, 7, 12, "en") shouldBe "3:07:12 PM"
      DateText.dateTimePattern("EEE d MMM, h:mm a", 2026, 11, 4, 3, 9, 30, 0, "en") shouldBe "Wed 4 Nov, 9:30 AM"
    }
    "leave a date pattern as it was" in {
      DateText.pattern("dd.MM.yy", 2026, 4, 3) shouldBe "03.04.26"
      DateText.timeOfDay(15, 7, "en") shouldBe "3:07 PM"
    }
  }
}
