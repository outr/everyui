package everyui.date

import everyui.event.{KeyEvent, Keys}
import everyui.{FxSpec, ui}
import reactify.*

/** A time of day: read back as people type it and written on the language's clock; a picker's wheels turned by the
  * keys and by digits, both ways with its value, on a 12- and a 24-hour clock, and heard as spin buttons; and a field
  * typed into and tidied. */
class TimePickerSpec extends FxSpec {
  private def press(w: TimeWheel, name: String): Unit = fx(w.event.key.down @= KeyEvent(Keys.of(name), name))

  "A time typed" should {
    "be read however it is written, and nothing that is not a time" in {
      val read = (s: String) => ClockTime.read(s, "en")
      List("14:30", "2:30 pm", "2:30PM", "2.30", "1430", "230", "2 pm", "12 am", "12pm", "0:05", "9:5x")
        .map(read) shouldBe List(Some(ClockTime(14, 30)), Some(ClockTime(14, 30)), Some(ClockTime(14, 30)),
          Some(ClockTime(2, 30)), Some(ClockTime(14, 30)), Some(ClockTime(2, 30)), Some(ClockTime(14, 0)),
          Some(ClockTime(0, 0)), Some(ClockTime(12, 0)), Some(ClockTime(0, 5)), None)
      List("25:00", "13 pm", "abc", "", "7:61").map(read) shouldBe List(None, None, None, None, None)
    }
    "be written on the language's clock, and go round it" in {
      ClockTime(15, 7).text("en") shouldBe "3:07 PM"
      ClockTime(15, 7).text("de") shouldBe "15:07"
      ClockTime(23, 50).plus(20) shouldBe ClockTime(0, 10)
      ClockTime.rounded(ClockTime(9, 52), 15) shouldBe ClockTime(9, 45)
      ClockTime.rounded(ClockTime(9, 53), 15) shouldBe ClockTime(10, 0)
    }
  }

  "A time picker" should {
    "turn its wheels by the keys and by digits, both ways with its value" in {
      val value = Var(Option(ClockTime(14, 35)))
      val p = fx(new TimePicker(value, step = 5, twelve = true))
      fx((p.hours.at(), p.minuteWheel.at(), p.period.get.at())) shouldBe (2, 7, 1)
      press(p.minuteWheel, "ArrowDown")
      fx(value()) shouldBe Some(ClockTime(14, 40))
      press(p.hours, "4")
      fx(value()) shouldBe Some(ClockTime(16, 40))
      press(p.period.get, "Home")
      fx(value()) shouldBe Some(ClockTime(4, 40))
      // round from the last minute to the first
      fx(value @= Some(ClockTime(4, 55)))
      fx(p.minuteWheel.at()) shouldBe 11
      press(p.minuteWheel, "ArrowDown")
      fx(value()) shouldBe Some(ClockTime(4, 0))
      fx(p.minuteWheel.semantics().role) shouldBe "spinbutton"
      fx(p.minuteWheel.semantics().range.map(_.text)) shouldBe Some("00")
      fx(p.hours.semantics().label) shouldBe DateWords.hour
    }
    "count 24 hours where the clock does, with no morning or afternoon" in {
      val value = Var(Option.empty[ClockTime])
      val p = fx(new TimePicker(value, step = 15, twelve = false))
      fx(p.period) shouldBe None
      fx(p.hours.at()) shouldBe 12
      press(p.hours, "1")
      press(p.hours, "8")
      fx(value()) shouldBe Some(ClockTime(18, 0))
      press(p.minuteWheel, "ArrowUp")
      fx(value()) shouldBe Some(ClockTime(18, 45))
    }
  }

  "A time field" should {
    "read what is typed, write it on the language's clock once left, and say what is not a time" in {
      val value = Var(Option.empty[ClockTime])
      val f = fx(new TimeField("Arrived", value))
      fx {
        ui.children.clear()
        ui.children += f
      }
      try {
        val input = f.input.get
        fx(input.value @= "2:30 pm")
        fx(value()) shouldBe Some(ClockTime(14, 30))
        fx(input.submitted @= input.value())
        fx(input.value()) shouldBe "2:30 PM"
        fx(input.value @= "later")
        fx(f.error()) shouldBe Some(DateWords.invalidTime)
        fx(value()) shouldBe Some(ClockTime(14, 30))
        fx(input.value @= "")
        fx(value()) shouldBe None
        fx(value @= Some(ClockTime(9, 5)))
        fx(input.value()) shouldBe "9:05 AM"
      } finally fx(ui.children.clear())
    }
  }
}
