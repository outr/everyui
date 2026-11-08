package everyui.widget

import everyui.async.Task
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What a widget draws from and its configure screen opens on where nothing was chosen: one placed while the app had
  * no choices to offer (signed out) kept none, and opened its configure screen with nothing ticked though it drew
  * everything. Nothing chosen is the kind's defaults among what is offered now, in the request and the screen alike. */
class WidgetSetupSpec extends AnyWordSpec with Matchers {
  private val offered = Vector(WidgetChoice("work", "Work", "Mail"), WidgetChoice("home", "Home", "Mail"))
  private val kind = WidgetKind("mail", "Mail", "Unread mail", "", Set(WidgetSize.Compact), () => Task.pure(offered),
    choices => choices.map(_.key).toSet, _ => Task.pure(WidgetNode.Spacer))

  "A widget's setup" should {
    "be what it was set to where something was chosen" in {
      val set = WidgetSetup("mail", Set("home"), countsOnly = true)
      WidgetSetup.resolved(kind, offered, Some(set)) shouldBe set
    }
    "be the kind's defaults where it was kept with nothing chosen, its counts only kept" in {
      val placedSignedOut = WidgetSetup("mail", Set.empty, countsOnly = true)
      WidgetSetup.resolved(kind, offered, Some(placedSignedOut)) shouldBe
        WidgetSetup("mail", Set("work", "home"), countsOnly = true)
      WidgetConfigure.initial(kind, offered, Some(placedSignedOut)) shouldBe
        WidgetSetup("mail", Set("work", "home"), countsOnly = true)
    }
    "be the kind's defaults where it was never set, or set as another kind" in {
      WidgetSetup.resolved(kind, offered, None) shouldBe WidgetSetup("mail", Set("work", "home"), countsOnly = false)
      WidgetSetup.resolved(kind, offered, Some(WidgetSetup("calendar", Set("cal"), countsOnly = true))) shouldBe
        WidgetSetup("mail", Set("work", "home"), countsOnly = false)
    }
    "be nothing chosen while there is nothing to offer, and the offer once there is" in {
      WidgetSetup.resolved(kind, Vector.empty, None).choices shouldBe empty
      WidgetSetup.resolved(kind, offered, Some(WidgetSetup.resolved(kind, Vector.empty, None))).choices shouldBe
        Set("work", "home")
    }
    "read back an empty choice kept before as it was kept" in {
      val kept = WidgetCodec.encode(WidgetSetup("mail", Set.empty, countsOnly = false))
      WidgetCodec.decodeSetup(kept) shouldBe Some(WidgetSetup("mail", Set.empty, countsOnly = false))
    }
  }
}
