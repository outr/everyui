package everyui.date

import everyui.chat.ChatTime
import everyui.i18n.DateText
import everyui.render.Platform
import java.time.{Instant, LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.{Locale, TimeZone}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** LocalClock's kept spans against the platform asked outright at every moment (`Platform.localDateTime` and the
  * offset and day read from it, as CalendarDate worked them out before): round every change of the clocks from 1900 to
  * 2100 in four zones (Chicago's and London's hour, Lord Howe's half hour, Kolkata's +5:30 and its wartime changes), at
  * random moments either side of the years it keeps, and along a year; then a zone changed mid-run, and a language. */
class LocalClockSpec extends AnyWordSpec with Matchers {
  private val zones = List("America/Chicago", "Europe/London", "Australia/Lord_Howe", "Asia/Kolkata")
  private val Hour = 3600000L

  private def in[A](zone: String)(f: => A): A = {
    val was = TimeZone.getDefault
    TimeZone.setDefault(TimeZone.getTimeZone(zone))
    LocalClock.reset()
    try f
    finally {
      TimeZone.setDefault(was)
      LocalClock.reset()
    }
  }

  // the moments where LocalClock and the platform differ, by what LocalClock said and the platform did
  private def differences(moments: Iterator[Long]): List[String] = moments.flatMap { m =>
    val text = LocalClock.dateTime(m)
    val offset = LocalClock.offset(m)
    val date = LocalClock.date(m)
    val second = LocalClock.secondOfDay(m)
    val platformText = Platform.localDateTime(m)
    val platformOffset = CalendarDate.platformOffsetMillis(m)
    val platformDate = CalendarDate.platformDate(m)
    val platformSecond = platformText.substring(11, 13).toInt * 3600 + platformText.substring(14, 16).toInt * 60 +
      platformText.substring(17, 19).toInt
    if (text == platformText && offset == platformOffset && date == platformDate && second == platformSecond) None
    else Some(s"$m: $text $offset $date $second, the platform $platformText $platformOffset $platformDate")
  }.take(5).toList

  // every change of `zone`'s clocks from 1900 to 2100
  private def changes(zone: String): List[Long] = {
    val rules = ZoneId.of(zone).getRules
    Iterator.iterate(Option(rules.nextTransition(Instant.parse("1900-01-01T00:00:00Z"))))(
      _.flatMap(t => Option(rules.nextTransition(t.getInstant))))
      .takeWhile(_.exists(_.getInstant.isBefore(Instant.parse("2100-01-01T00:00:00Z"))))
      .flatten.map(_.getInstant.toEpochMilli).toList
  }

  "LocalClock" should {
    "agree with the platform round every change of the clocks, minute by minute and at the change itself" in {
      zones.foreach { zone =>
        in(zone) {
          val all = changes(zone)
          all.size should be > 1
          val moments = all.iterator.flatMap { t =>
            Iterator(t - 1L, t, t + 1L, t - 1000L, t + 999L) ++ Iterator.range(-120, 121).map(t + _ * 60000L + 17L)
          }
          withClue(zone + ": ") { differences(moments) shouldBe Nil }
        }
      }
    }
    "agree with the platform at random moments from 1890 to 2110, and either side of the years it keeps" in {
      val from = Instant.parse("1890-01-01T00:00:00Z").toEpochMilli
      val to = Instant.parse("2110-01-01T00:00:00Z").toEpochMilli
      zones.foreach { zone =>
        in(zone) {
          val rng = new scala.util.Random(zone.hashCode)
          val random = Iterator.fill(100000)(from + (rng.nextDouble() * (to - from)).toLong)
          val ends = Iterator(CalendarDate(1900, 1, 2), CalendarDate(9999, 12, 30)).map(_.toEpochMillis)
            .flatMap(e => Iterator(e - 1L, e, e + 1L, e - 7L * Hour, e + 7L * Hour))
          withClue(zone + ": ") { differences(random ++ ends) shouldBe Nil }
        }
      }
    }
    "agree with the platform along a year, now's span and an older one in turn as a transcript asks" in {
      zones.foreach { zone =>
        in(zone) {
          val start = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli
          val walk = Iterator.range(0, 75000).map(i => start + i * 7L * 60000L + i % 1000)
          val turns = Iterator.range(0, 20000).flatMap(i => Iterator(start + i * 977000L, start + 300L * 86400000L))
          withClue(zone + ": ") { differences(walk ++ turns) shouldBe Nil }
          // the days and times a transcript shows, the same
          (0 until 2000).foreach { i =>
            val time = start + i * 4111111L
            val local = Platform.localDateTime(time)
            ChatTime.dayOf(time) shouldBe local.substring(0, 10)
            ChatTime.clock(time) shouldBe DateText.timeOfDay(local.substring(11, 13).toInt, local.substring(14, 16).toInt)
          }
        }
      }
    }
    "find a day's local midnight as java.time does" in {
      zones.foreach { zone =>
        in(zone) {
          val id = ZoneId.of(zone)
          (0 until 20000 by 3).foreach { i =>
            val day = CalendarDate(1990, 1, 1).plusDays(i)
            val local = java.time.LocalDate.of(day.year, day.month, day.day)
            day.toLocalEpochMillis shouldBe local.atStartOfDay(id).toInstant.toEpochMilli
          }
        }
      }
    }
    "see the zone change mid-run: at once where told, on its next look where not" in {
      val moment = Instant.parse("2026-07-01T12:00:00Z").toEpochMilli
      in("America/Chicago") {
        LocalClock.dateTime(moment) shouldBe "2026-07-01 07:00:00"
        // told by TimeZones, as a window's focus or a platform's word brings it
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        TimeZones.check()
        LocalClock.dateTime(moment) shouldBe "2026-07-01 17:30:00"
        LocalClock.offset(moment) shouldBe 19800000L
        // not told: seen as LocalClock reads the zone again
        val every = LocalClock.checkEvery
        LocalClock.checkEvery = 0L
        try {
          TimeZone.setDefault(TimeZone.getTimeZone("Australia/Lord_Howe"))
          LocalClock.dateTime(moment) shouldBe "2026-07-01 22:30:00"
          LocalClock.date(moment + 2L * Hour) shouldBe CalendarDate(2026, 7, 2)
          TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
          differences(Iterator.range(0, 2000).map(i => moment + i * 3L * Hour)) shouldBe Nil
        }
        finally LocalClock.checkEvery = every
      }
      TimeZones.check()
    }
    "write the same in every locale, and each language's times in that language as it changes" in {
      val moment = Instant.parse("2026-03-08T08:30:05Z").toEpochMilli
      val was = Locale.getDefault
      try {
        List("en-US", "ar-SA", "th-TH-u-nu-thai", "ja-JP-u-ca-japanese", "hi-IN-u-nu-deva", "fr-FR").foreach { tag =>
          Locale.setDefault(Locale.forLanguageTag(tag))
          // what each call made before: a formatter in the locale of the moment
          val before = LocalDateTime.ofInstant(Instant.ofEpochMilli(moment), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          withClue(tag + ": ") {
            Platform.localDateTime(moment) shouldBe before
            LocalClock.dateTime(moment) shouldBe before
          }
        }
      }
      finally Locale.setDefault(was)
      // the language kept from one call to the next, and changed as the next asks for another
      List("en", "de", "en", "zh-TW", "zh_Hant", "pt-BR", "pt", "xx", "de").foreach { code =>
        val normal = code.replace('-', '_')
        val expected = if (normal == "zh_TW") "zh_Hant" else if (normal == "pt_BR") "pt" else if (normal == "xx") "en"
          else normal
        DateText.symbols(code).code shouldBe expected
      }
      DateText.timeOfDay(15, 7, "en") shouldBe "3:07 PM"
      DateText.timeOfDay(15, 7, "de") shouldBe "15:07"
      DateText.timeOfDay(15, 7, "en") shouldBe "3:07 PM"
    }
  }
}
