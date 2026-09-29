package everyui.date

import everyui.i18n.{DateText, WeekStart}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Dates written and read the way each language writes them: numbers in the language's order, months named in it or
  * in English, two-digit years, ISO 8601, and what is not a date; and the first day of the week by region. */
class DateParseSpec extends AnyWordSpec with Matchers {
  private val ref = CalendarDate(2026, 9, 29)
  private def read(text: String, code: String = "en", order: String = ""): Option[CalendarDate] =
    DateParse.read(text, order, ref, code)

  "DateParse" should {
    "read numbers in the language's order" in {
      read("9/29/2026") shouldBe Some(CalendarDate(2026, 9, 29))
      read("9/10/2026") shouldBe Some(CalendarDate(2026, 9, 10))
      read("9/10/2026", "fr") shouldBe Some(CalendarDate(2026, 10, 9))
      read("29.9.2026", "de") shouldBe Some(CalendarDate(2026, 9, 29))
      read("29-9-2026", "nl") shouldBe Some(CalendarDate(2026, 9, 29))
      read("2026/9/29", "zh") shouldBe Some(CalendarDate(2026, 9, 29))
      read("2026年9月29日", "zh") shouldBe Some(CalendarDate(2026, 9, 29))
      read("29/9/2026") shouldBe None                       // no 29th month in English's order
    }
    "take a first number of four digits as the year, and eight digits together" in {
      read("2026/9/29", "fr") shouldBe Some(CalendarDate(2026, 9, 29))
      read("20260929") shouldBe Some(CalendarDate(2026, 9, 29))
      read("09292026") shouldBe Some(CalendarDate(2026, 9, 29))
      read("29092026", "de") shouldBe Some(CalendarDate(2026, 9, 29))
    }
    "read ISO 8601 in any language" in {
      read("2026-09-29", "de") shouldBe Some(CalendarDate(2026, 9, 29))
      read(" 2024-02-29 ") shouldBe Some(CalendarDate(2024, 2, 29))
    }
    "read two-digit years near the reference, and a year left out as its" in {
      read("9/29/26") shouldBe Some(CalendarDate(2026, 9, 29))
      read("1/2/70") shouldBe Some(CalendarDate(1970, 1, 2))
      read("1/2/45") shouldBe Some(CalendarDate(2045, 1, 2))
      read("1/2/46") shouldBe Some(CalendarDate(1946, 1, 2))
      read("12/25") shouldBe Some(CalendarDate(2026, 12, 25))
      read("25.12", "de") shouldBe Some(CalendarDate(2026, 12, 25))
      DateParse.century(99, 2026) shouldBe 1999
      DateParse.century(0, 2026) shouldBe 2000
      DateParse.century(5, 2090) shouldBe 2105
    }
    "read named months, cut short or in full, in the language or English" in {
      read("Sep 29, 2026") shouldBe Some(CalendarDate(2026, 9, 29))
      read("september 29 2026") shouldBe Some(CalendarDate(2026, 9, 29))
      read("29 Sept 2026") shouldBe Some(CalendarDate(2026, 9, 29))
      read("Tue, Sep 29, 2026") shouldBe Some(CalendarDate(2026, 9, 29))
      read("Tuesday, September 29, 2026") shouldBe Some(CalendarDate(2026, 9, 29))
      read("29 septembre 2026", "fr") shouldBe Some(CalendarDate(2026, 9, 29))
      read("29 sept. 2026", "fr") shouldBe Some(CalendarDate(2026, 9, 29))
      read("29 de septiembre de 2026", "es") shouldBe Some(CalendarDate(2026, 9, 29))
      read("29. März 2026", "de") shouldBe Some(CalendarDate(2026, 3, 29))
      read("2026 Sep 29") shouldBe Some(CalendarDate(2026, 9, 29))
      read("Sep 3") shouldBe Some(CalendarDate(2026, 9, 3))
      read("29 سبتمبر 2026", "ar") shouldBe Some(CalendarDate(2026, 9, 29))
    }
    "read Arabic-Indic digits" in {
      read("٢٩/٩/٢٠٢٦", "ar") shouldBe Some(CalendarDate(2026, 9, 29))
    }
    "refuse what is not a day on the calendar" in {
      List("", "  ", "2/30/2026", "13/1/2026", "hello", "Sep Oct 3", "1/2/3/4", "banana 3 2026", "2023-02-29", "7")
        .foreach(t => read(t) shouldBe None)
      read("2/29/2024") shouldBe Some(CalendarDate(2024, 2, 29))
      read("2/29/2023") shouldBe None
    }
    "take the order of a pattern" in {
      DateParse.fieldOrder("M/d/y") shouldBe "Mdy"
      DateParse.fieldOrder("d. MMMM y") shouldBe "dMy"
      DateParse.fieldOrder("y年M月d日") shouldBe "yMd"
      DateParse.fieldOrder("EEEE, d 'de' MMMM 'de' y") shouldBe "dMy"
      DateParse.fieldOrder("MMM d") shouldBe "Mdy"
      read("03.04.26", "en", "dd.MM.yy") shouldBe Some(CalendarDate(2026, 4, 3))
    }
  }

  "DateFormat" should {
    "write each language's forms" in {
      val d = CalendarDate(2026, 9, 29)
      DateText.numericDate(2026, 9, 29, "en") shouldBe "9/29/2026"
      DateText.numericDate(2026, 9, 9, "fr") shouldBe "09/09/2026"
      DateText.numericDate(2026, 9, 29, "de") shouldBe "29.9.2026"
      DateText.numericDate(2026, 9, 29, "zh") shouldBe "2026/9/29"
      DateText.fullDate(2026, 9, 29, 2, "en") shouldBe "Tuesday, September 29, 2026"
      DateText.fullDate(2026, 9, 29, 2, "es") shouldBe "martes, 29 de septiembre de 2026"
      DateText.fullDate(2026, 9, 29, 2, "de") shouldBe "Dienstag, 29. September 2026"
      DateText.fullDate(2026, 9, 29, 2, "zh") shouldBe "2026年9月29日星期二"
      DateText.monthYear(2026, 9, "en") shouldBe "September 2026"
      DateText.monthYear(2026, 9, "pt") shouldBe "setembro de 2026"
      DateText.monthYear(2026, 9, "zh_Hant") shouldBe "2026年9月"
      DateText.narrowWeekday(3, "en") shouldBe "W"
      DateText.narrowWeekday(3, "es") shouldBe "X"
      DateText.pattern("dd.MM.yy", 2026, 4, 3) shouldBe "03.04.26"
      DateText.pattern("EEE d MMM yyyy", 2026, 9, 29, 2, "en") shouldBe "Tue 29 Sep 2026"
      DateFormat.iso.write(d) shouldBe "2026-09-29"
      DateFormat.pattern("d/M/y").read("3/4/2026") shouldBe Some(CalendarDate(2026, 4, 3))
    }
    "read back what it writes, in every language" in {
      val codes = List("en", "es", "pt", "fr", "de", "ar", "tr", "nl", "it", "zh", "zh_Hant")
      var d = CalendarDate(1999, 1, 1)
      while (d.year < 2031) {
        codes.foreach { code =>
          val s = DateText.symbols(code)
          DateParse.read(DateText.numericDate(d.year, d.month, d.day, code), s.numericDate, ref, code) shouldBe Some(d)
          DateParse.read(DateText.mediumDate(d.year, d.month, d.day, code), s.mediumDate, ref, code) shouldBe Some(d)
          DateParse.read(DateText.fullDate(d.year, d.month, d.day, d.weekday, code), s.fullDate, ref, code) shouldBe Some(d)
        }
        d = d.plusDays(23)
      }
    }
  }

  "WeekStart" should {
    "start the week where the region does" in {
      WeekStart.of("en") shouldBe 7
      WeekStart.of("en_US") shouldBe 7
      WeekStart.of("en-GB") shouldBe 1
      WeekStart.of("en_AU") shouldBe 1
      WeekStart.of("de") shouldBe 1
      WeekStart.of("fr_CA") shouldBe 7
      WeekStart.of("pt") shouldBe 7
      WeekStart.of("pt_PT") shouldBe 7
      WeekStart.of("es") shouldBe 1
      WeekStart.of("es_MX") shouldBe 7
      WeekStart.of("ar") shouldBe 6
      WeekStart.of("ar_SA") shouldBe 7
      WeekStart.of("ar_MA") shouldBe 1
      WeekStart.of("zh") shouldBe 7
      WeekStart.of("zh_Hant_TW") shouldBe 7
      WeekStart.of("tr") shouldBe 1
      WeekStart.of("ja") shouldBe 7
    }
  }
}
