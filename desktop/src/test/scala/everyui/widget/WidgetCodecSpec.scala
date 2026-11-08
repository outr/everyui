package everyui.widget

import everyui.font.MaterialIcon
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A widget's tree and its setup kept as text and read back as they went in, whatever words they hold; text that is
  * not one read as None, which draws as nothing kept. */
class WidgetCodecSpec extends AnyWordSpec with Matchers {
  private val tree: WidgetNode = WidgetNode.Column(Vector(
    WidgetNode.Row(Vector(
      WidgetNode.Picture(WidgetPicture.Icon(MaterialIcon.Mail), 24.0, "Mail"),
      WidgetNode.Text("Inbox", WidgetText.Title),
      WidgetNode.Text("12", WidgetText.Count, WidgetColour.Accent, label = "12 unread"),
      WidgetNode.Spacer,
      WidgetNode.Button("Refresh", Some(MaterialIcon.Refresh),
        WidgetTap.Action("refresh", Map("all" -> "1", "b" -> "")))),
      gap = 6.5, tap = Some(WidgetTap.Open("/inbox"))),
    WidgetNode.Items(Vector(
      WidgetNode.Row(Vector(WidgetNode.Text("Ada: 3:1; ratio", lines = 2),
        WidgetNode.Picture(WidgetPicture.Url("https://example.com/a.png?x=1;y=2"), 32.0))),
      WidgetNode.Picture(WidgetPicture.Asset("images/icon.png"), 16.0),
      WidgetNode.Button("Open", None, WidgetTap.Open(""))), "No mail: none at all"),
    WidgetNode.Text("", WidgetText.Caption, WidgetColour.Danger, lines = 0)), gap = 0.0)

  "A widget's tree" should {
    "read back as it was kept" in {
      WidgetCodec.decode(WidgetCodec.encode(tree)) shouldBe Some(tree)
    }
    "keep words with the codec's own marks in them, and every language's" in {
      val words = WidgetNode.Text("5:hello; 1;0 t\u0000 مرحبا 📨 \n", label = ":;")
      WidgetCodec.decode(WidgetCodec.encode(words)) shouldBe Some(words)
    }
    "read as None from text that is not one" in {
      val kept = WidgetCodec.encode(tree)
      WidgetCodec.decode("") shouldBe None
      WidgetCodec.decode(null) shouldBe None
      WidgetCodec.decode("garbage") shouldBe None
      WidgetCodec.decode(kept.dropRight(1)) shouldBe None
      WidgetCodec.decode(kept + "s") shouldBe None
      WidgetCodec.decode(kept.substring(3)) shouldBe None
    }
  }

  "A widget's setup" should {
    "read back as it was kept" in {
      val setup = WidgetSetup("mail", Set("work", "home: matt@example.com", ""), countsOnly = true)
      WidgetCodec.decodeSetup(WidgetCodec.encode(setup)) shouldBe Some(setup)
      val none = WidgetSetup("calendar", Set.empty, countsOnly = false)
      WidgetCodec.decodeSetup(WidgetCodec.encode(none)) shouldBe Some(none)
    }
    "read as None from text that is not one" in {
      WidgetCodec.decodeSetup("w1|4:mail2;4:work") shouldBe None
      WidgetCodec.decodeSetup(WidgetCodec.encode(tree)) shouldBe None
    }
  }
}
