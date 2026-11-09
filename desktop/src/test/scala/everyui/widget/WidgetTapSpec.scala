package everyui.widget

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A tap on a part of a list's row as the words it hands with its tap (`WidgetTap.extras`, Android's fill-in intent)
  * and read back from them among the list's template's own (`WidgetTap.of`): an open tap with its path, an action with
  * its name and its args, whatever their names and words, and nothing where they hold no tap. */
class WidgetTapSpec extends AnyWordSpec with Matchers {
  // what the list's template carries of its own, which the row's extras are merged into
  private val template = Map("everyui.widget.receiver" -> "com.example/everyui.widget.generated.InboxWidgetReceiver")

  private def back(tap: WidgetTap): Option[WidgetTap] = WidgetTap.of(template ++ WidgetTap.extras(tap))

  "A row's tap" should {
    "be an open tap with its path, read back" in {
      WidgetTap.extras(WidgetTap.Open("/tasks/42")) shouldBe Map(WidgetTap.tapKey -> "open", "path" -> "/tasks/42")
      back(WidgetTap.Open("/tasks/42")) shouldBe Some(WidgetTap.Open("/tasks/42"))
      back(WidgetTap.Open("")) shouldBe Some(WidgetTap.Open(""))
    }
    "be an action with its name and args, read back" in {
      val tick = WidgetTap.Action("tick", Map("id" -> "42", "list" -> "home"))
      WidgetTap.extras(tick) shouldBe Map(WidgetTap.tapKey -> "action", WidgetTap.nameKey -> "tick",
        WidgetTap.argPrefix + "id" -> "42", WidgetTap.argPrefix + "list" -> "home")
      back(tick) shouldBe Some(tick)
    }
    "keep args whatever their names and words: dots, the tap's own names, empty, other scripts" in {
      val odd = WidgetTap.Action("mark.read", Map("a.b" -> "x=y&z", "path" -> "/elsewhere", "tap" -> "open",
        "" -> "unnamed", "empty" -> "", "名前" -> "テスト ✓"))
      back(odd) shouldBe Some(odd)
    }
    "be an action with no args, read back with none" in {
      back(WidgetTap.Action("refresh")) shouldBe Some(WidgetTap.Action("refresh", Map.empty))
    }
    "take only the args of the part tapped, each part's extras its own" in {
      val row = WidgetTap.extras(WidgetTap.Open("/tasks/42"))
      val tick = WidgetTap.extras(WidgetTap.Action("tick", Map("id" -> "42")))
      WidgetTap.of(template ++ row) shouldBe Some(WidgetTap.Open("/tasks/42"))
      WidgetTap.of(template ++ tick) shouldBe Some(WidgetTap.Action("tick", Map("id" -> "42")))
    }
    "be nothing where the extras hold no tap, or one cut short" in {
      WidgetTap.of(Map.empty) shouldBe None
      WidgetTap.of(template) shouldBe None
      WidgetTap.of(Map("path" -> "/inbox")) shouldBe None
      WidgetTap.of(Map(WidgetTap.tapKey -> "open")) shouldBe None
      WidgetTap.of(Map(WidgetTap.tapKey -> "action")) shouldBe None
      WidgetTap.of(Map(WidgetTap.tapKey -> "action", WidgetTap.nameKey -> "")) shouldBe None
      WidgetTap.of(Map(WidgetTap.tapKey -> "jump", "path" -> "/inbox")) shouldBe None
    }
  }
}
