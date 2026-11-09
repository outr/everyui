package everyui.widget

import everyui.Color
import everyui.async.Task
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A translucent widget: its own ground's opacity on its kind, and rows and columns on tinted cards, kept and read back
  * with their grounds, while a tree kept before grounds were offered reads as it did. */
class WidgetGroundSpec extends AnyWordSpec with Matchers {
  private val card = WidgetGround(WidgetColour.Surface, opacity = 0.8, radius = 12.0, padding = 8.0)
  private val tree: WidgetNode = WidgetNode.Column(Vector(
    WidgetNode.Row(Vector(WidgetNode.Text("Inbox", WidgetText.Title), WidgetNode.Spacer),
      tap = Some(WidgetTap.Open("/"))),
    WidgetNode.Items(Vector(
      WidgetNode.Row(Vector(WidgetNode.Text("Ada"), WidgetNode.Text("9:41")), ground = Some(card)),
      WidgetNode.Row(Vector(
        WidgetNode.Column(Vector(WidgetNode.Text("Grace")), gap = 2.0,
          ground = Some(WidgetGround(WidgetColour.Raised, 0.5, 8.0, 0.0, Some(Color.fromHex("33669980"))))),
        WidgetNode.Text("now")), gap = 6.0, tap = Some(WidgetTap.Action("open", Map("id" -> "m1"))),
        ground = Some(WidgetGround(WidgetColour.Accent, 0.25, 16.0, 4.5)))), "None")),
    gap = 8.0, ground = Some(WidgetGround()))

  "A tree with grounds" should {
    "read back as it was kept" in {
      WidgetCodec.decode(WidgetCodec.encode(tree)) shouldBe Some(tree)
    }
    "keep every role a ground may have, and an exact colour with its alpha" in {
      WidgetColour.values.foreach { role =>
        val row = WidgetNode.Row(Vector(WidgetNode.Spacer), ground = Some(WidgetGround(role, 0.4, 0.0, 12.0,
          if (role.ordinal % 2 == 0) Some(Color.fromHex("ff000040")) else None)))
        WidgetCodec.decode(WidgetCodec.encode(row)) shouldBe Some(row)
      }
    }
    "read as None where its ground is cut short" in {
      val kept = WidgetCodec.encode(WidgetNode.Row(Vector.empty, ground = Some(card)))
      (1 to 6).foreach(n => WidgetCodec.decode(kept.dropRight(n)) shouldBe None)
    }
  }

  "A tree kept before grounds" should {
    "read as it did, with no grounds" in {
      // a column of a row of words, as an earlier EveryUI kept it
      val old = "w1|c1;r1;t5:Inbox0;0;1;0:8.0;1o6:/inbox4.0;0"
      WidgetCodec.decode(old) shouldBe Some(WidgetNode.Column(Vector(
        WidgetNode.Row(Vector(WidgetNode.Text("Inbox", WidgetText.Title)), tap = Some(WidgetTap.Open("/inbox"))))))
    }
    "be kept as it was without grounds" in {
      val plain = WidgetNode.Column(Vector(
        WidgetNode.Row(Vector(WidgetNode.Text("Inbox", WidgetText.Title)), tap = Some(WidgetTap.Open("/inbox")))))
      WidgetCodec.encode(plain) shouldBe "w1|c1;r1;t5:Inbox0;0;1;0:8.0;1o6:/inbox4.0;0"
    }
    "name its colours by the places they had" in {
      List(WidgetColour.Text, WidgetColour.Muted, WidgetColour.Accent, WidgetColour.Danger, WidgetColour.OnAccent)
        .map(_.ordinal) shouldBe List(0, 1, 2, 3, 4)
    }
  }

  "A ground's opacity" should {
    "be drawn as an alpha from 0 to 255, held to 0..1" in {
      WidgetGround.alpha(1.0) shouldBe 255
      WidgetGround.alpha(0.0) shouldBe 0
      WidgetGround.alpha(0.35) shouldBe 89
      WidgetGround.alpha(0.8) shouldBe 204
      WidgetGround.alpha(1.5) shouldBe 255
      WidgetGround.alpha(-0.2) shouldBe 0
      WidgetGround.alpha(Double.NaN) shouldBe 255
    }
    "multiply in an exact colour's own alpha" in {
      card.alpha shouldBe 204
      WidgetGround(opacity = 0.5, exact = Some(Color.fromHex("ffffff80"))).alpha shouldBe 64
      WidgetGround(exact = Some(Color.White)).alpha shouldBe 255
    }
    "be the whole of the theme's surface on a kind that does not say" in {
      val kind = WidgetKind("k", "K", "", "", Set(WidgetSize.Compact), () => Task.pure(Vector.empty), _ => Set.empty,
        _ => Task.pure(WidgetNode.Spacer))
      kind.groundOpacity shouldBe 1.0
      WidgetGround.alpha(kind.copy(groundOpacity = 0.35).groundOpacity) shouldBe 89
    }
  }

  "A ground's corners" should {
    "be the nearest of the shapes drawn" in {
      WidgetGround.radii shouldBe Vector(0, 8, 12, 16)
      WidgetGround.radius(0.0) shouldBe 0
      WidgetGround.radius(3.0) shouldBe 0
      WidgetGround.radius(6.0) shouldBe 8
      WidgetGround.radius(12.0) shouldBe 12
      WidgetGround.radius(15.0) shouldBe 16
      WidgetGround.radius(40.0) shouldBe 16
    }
  }
}
