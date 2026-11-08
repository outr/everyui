package everyui.widget

import everyui.async.Task
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What a widget's configure screen offers by group and has chosen as it opens: what the widget was set to, or its
  * kind's defaults where it never was (or was set as another kind). */
class WidgetConfigureSpec extends AnyWordSpec with Matchers {
  private val offered = Vector(WidgetChoice("work", "Work", "Mail"), WidgetChoice("cal", "Calendar", "Google: a@b"),
    WidgetChoice("home", "Home", "Mail"), WidgetChoice("loose", "Loose"))
  private val kind = WidgetKind("mail", "Mail", "Unread mail", "", Set(WidgetSize.Compact), () => Task.pure(offered),
    choices => choices.filter(_.group == "Mail").map(_.key).toSet, _ => Task.pure(WidgetNode.Spacer))

  "A widget's configure screen" should {
    "offer its choices by group, in the order they come" in {
      WidgetConfigure.grouped(offered) shouldBe Vector(
        "Mail" -> Vector(offered(0), offered(2)), "Google: a@b" -> Vector(offered(1)), "" -> Vector(offered(3)))
      WidgetConfigure.grouped(Vector.empty) shouldBe Vector.empty
    }
    "open on what the widget was set to, or its kind's defaults" in {
      val set = WidgetSetup("mail", Set("cal"), countsOnly = true)
      WidgetConfigure.initial(kind, offered, Some(set)) shouldBe set
      WidgetConfigure.initial(kind, offered, None) shouldBe WidgetSetup("mail", Set("work", "home"), countsOnly = false)
      WidgetConfigure.initial(kind, offered, Some(WidgetSetup("calendar", Set("cal"), countsOnly = true))) shouldBe
        WidgetSetup("mail", Set("work", "home"), countsOnly = false)
    }
  }
}
