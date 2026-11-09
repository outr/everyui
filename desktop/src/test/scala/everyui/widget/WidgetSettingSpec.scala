package everyui.widget

import everyui.async.Task
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A kind's settings, each a choice of one option: what a widget is handed for them (the option chosen while it is
  * still offered, the default otherwise), kept with its setup and read back, a setup kept before settings were offered
  * reading with none; and a tree whose words are scaled, kept and read back, while a tree without a scale is kept as
  * it always was. */
class WidgetSettingSpec extends AnyWordSpec with Matchers {
  private val textSize = WidgetSetting("textSize", "Text size", Vector(WidgetSettingOption("small", "Small"),
    WidgetSettingOption("normal", "Normal"), WidgetSettingOption("large", "Large")), "normal")
  private val ahead = WidgetSetting("ahead", "Show", Vector(WidgetSettingOption("1", "Today"),
    WidgetSettingOption("7", "Next 7 days"), WidgetSettingOption("30", "Next 30 days")), "7", "How far ahead")
  private val offered = Vector(WidgetChoice("work", "Work"), WidgetChoice("home", "Home"))
  private val kind = WidgetKind("calendar", "Calendar", "", "", Set(WidgetSize.Medium), () => Task.pure(offered),
    choices => choices.map(_.key).toSet, _ => Task.pure(WidgetNode.Spacer), settings = Vector(textSize, ahead))

  "A kind's settings" should {
    "be their defaults for a widget never set" in {
      WidgetSetup.settled(kind, Map.empty) shouldBe Map("textSize" -> "normal", "ahead" -> "7")
      WidgetSetup.resolved(kind, offered, None).settings shouldBe Map("textSize" -> "normal", "ahead" -> "7")
    }
    "be the option saved where it is still offered" in {
      val saved = WidgetSetup("calendar", Set("work"), countsOnly = false, Map("textSize" -> "large"))
      WidgetSetup.resolved(kind, offered, Some(saved)) shouldBe
        WidgetSetup("calendar", Set("work"), countsOnly = false, Map("textSize" -> "large", "ahead" -> "7"))
    }
    "be the default where the option saved is no longer offered, and leave out a setting no longer offered" in {
      WidgetSetup.settled(kind, Map("textSize" -> "huge", "ahead" -> "30", "gone" -> "x")) shouldBe
        Map("textSize" -> "normal", "ahead" -> "30")
    }
    "be their defaults for a setup kept before settings, which reads back with none" in {
      val old = WidgetCodec.decodeSetup("w1|8:calendar2;4:home4:work1")
      old shouldBe Some(WidgetSetup("calendar", Set("home", "work"), countsOnly = true))
      old.get.settings.isEmpty shouldBe true
      WidgetSetup.resolved(kind, offered, old).settings shouldBe Map("textSize" -> "normal", "ahead" -> "7")
    }
    "be none for a kind that offers none" in {
      WidgetSetup.settled(kind.copy(settings = Vector.empty), Map("textSize" -> "large")).isEmpty shouldBe true
    }
  }

  "A setup with settings" should {
    "read back as it was kept" in {
      val set = WidgetSetup("calendar", Set("work", "home"), countsOnly = true,
        Map("textSize" -> "large", "ahead" -> "30", "odd: ;|" -> "a:1;"))
      WidgetCodec.decodeSetup(WidgetCodec.encode(set)) shouldBe Some(set)
    }
    "be kept as it always was where it has none" in {
      WidgetCodec.encode(WidgetSetup("calendar", Set("work", "home"), countsOnly = true)) shouldBe
        "w1|8:calendar2;4:home4:work1"
    }
    "read as None where it is cut short" in {
      val kept = WidgetCodec.encode(WidgetSetup("mail", Set("work"), countsOnly = false, Map("textSize" -> "large")))
      (1 to 8).foreach(n => WidgetCodec.decodeSetup(kept.dropRight(n)) shouldBe None)
    }
  }

  "A scaled tree" should {
    val tree: WidgetNode = WidgetNode.Scaled(1.25, WidgetNode.Column(Vector(
      WidgetNode.Row(Vector(WidgetNode.Text("Inbox", WidgetText.Title), WidgetNode.Spacer)),
      WidgetNode.Items(Vector(WidgetNode.Scaled(0.8, WidgetNode.Row(Vector(WidgetNode.Text("Ada"),
        WidgetNode.Text("9:41", WidgetText.Caption))))), "None")), gap = 8.0))

    "read back as it was kept" in {
      WidgetCodec.decode(WidgetCodec.encode(tree)) shouldBe Some(tree)
    }
    "read as None where it is cut short" in {
      val kept = WidgetCodec.encode(WidgetNode.Scaled(0.9, WidgetNode.Text("Ada")))
      (1 to 6).foreach(n => WidgetCodec.decode(kept.dropRight(n)) shouldBe None)
    }
    "hold its scale to a sane range" in {
      WidgetNode.scale(0.9) shouldBe 0.9
      WidgetNode.scale(1.5) shouldBe 1.5
      WidgetNode.scale(0.1) shouldBe WidgetNode.leastScale
      WidgetNode.scale(9.0) shouldBe WidgetNode.mostScale
      WidgetNode.scale(Double.NaN) shouldBe 1.0
    }
  }

  "A tree without a scale" should {
    "read as it did, and be kept as it was" in {
      val old = "w1|c1;r1;t5:Inbox0;0;1;0:8.0;1o6:/inbox4.0;0"
      val tree = WidgetNode.Column(Vector(
        WidgetNode.Row(Vector(WidgetNode.Text("Inbox", WidgetText.Title)), tap = Some(WidgetTap.Open("/inbox")))))
      WidgetCodec.decode(old) shouldBe Some(tree)
      WidgetCodec.encode(tree) shouldBe old
    }
  }
}
