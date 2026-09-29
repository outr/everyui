package everyui.color

import everyui.Color
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The picker's conversions: hue, saturation and value to a colour and back, hex and channels read and written, every
  * colour of a coarse grid round each way. */
class ColorConversionSpec extends AnyWordSpec with Matchers {
  private def grid: Seq[Color] = for {
    r <- 0 to 255 by 17
    g <- 0 to 255 by 17
    b <- 0 to 255 by 17
  } yield new Color(r, g, b, 255)

  "Hsv" should {
    "make the primaries and greys" in {
      Hsv(0.0, 1.0, 1.0).color shouldBe Color.fromHex("ff0000")
      Hsv(1.0 / 3.0, 1.0, 1.0).color shouldBe Color.fromHex("00ff00")
      Hsv(2.0 / 3.0, 1.0, 1.0).color shouldBe Color.fromHex("0000ff")
      Hsv(0.5, 1.0, 0.5).color shouldBe Color.fromHex("008080")
      Hsv(0.3, 0.0, 1.0).color shouldBe Color.White
      Hsv(0.3, 0.7, 0.0).color shouldBe Color.Black
      Hsv(1.0, 1.0, 1.0).color shouldBe Color.fromHex("ff0000")
    }
    "keep alpha, and drop it when asked for an opaque colour" in {
      Hsv(0.0, 1.0, 1.0, 0.5).color.alphaInt shouldBe 128
      Hsv(0.0, 1.0, 1.0, 0.5).color(false).alphaInt shouldBe 255
    }
    "clamp what is out of range" in {
      Hsv(0.0, 2.0, -1.0).color shouldBe Color.Black
      Hsv(0.0, -1.0, 2.0).color shouldBe Color.White
    }
    "come back from every colour to the same colour" in {
      grid.foreach(c => Hsv.of(c).color shouldBe c)
    }
    "come back from hue, saturation and value within a channel's rounding" in {
      for {
        h <- 0 until 360 by 7
        s <- 1 to 10
        v <- 1 to 10
      } {
        val start = Hsv(h / 360.0, s / 10.0, v / 10.0)
        val back = Hsv.of(start.color)
        back.color shouldBe start.color
        // a channel is 1/255: the hue is off by no more than that shows at this saturation and value
        math.abs(back.value - start.value) should be <= (1.0 / 255.0)
      }
    }
    "keep the hue of a grey and the saturation of black from what it was" in {
      val was = Hsv(0.6, 0.8, 0.9)
      Hsv.of(Color.fromHex("808080"), was) shouldBe Hsv(0.6, 0.0, 128.0 / 255.0)
      Hsv.of(Color.Black, was) shouldBe Hsv(0.6, 0.8, 0.0)
      Hsv.of(Color.fromHex("ff000080")).alpha shouldBe (128.0 / 255.0)
    }
  }

  "ColorText" should {
    "read hex in its four lengths, with or without #, in either case" in {
      ColorText.parseHex("#1e88e5") shouldBe Some(Color.fromHex("1e88e5"))
      ColorText.parseHex(" 1E88E5 ") shouldBe Some(Color.fromHex("1e88e5"))
      ColorText.parseHex("#abc") shouldBe Some(Color.fromHex("aabbcc"))
      ColorText.parseHex("abc8") shouldBe Some(new Color(170, 187, 204, 136))
      ColorText.parseHex("#1e88e580") shouldBe Some(new Color(30, 136, 229, 128))
    }
    "refuse what is not a colour" in {
      List("", "#", "#12", "12345", "1234567", "#1e88e5ff0", "#gg0000", "rgb(1,2,3)", "# 123").foreach { t =>
        ColorText.parseHex(t) shouldBe None
      }
    }
    "write hex, with alpha only where it is allowed and not opaque" in {
      val c = new Color(30, 136, 229, 128)
      ColorText.hex(c, alpha = false) shouldBe "#1e88e5"
      ColorText.hex(c, alpha = true) shouldBe "#1e88e580"
      ColorText.hex(c.withAlpha(1.0), alpha = true) shouldBe "#1e88e5"
      ColorText.hex(c.withAlpha(1.0), alpha = true, always = true) shouldBe "#1e88e5ff"
    }
    "round every colour through hex" in {
      grid.foreach { c =>
        ColorText.parseHex(ColorText.hex(c, alpha = false)) shouldBe Some(c)
        val clear = c.withAlpha(0.25)
        ColorText.parseHex(ColorText.hex(clear, alpha = true)) shouldBe Some(clear)
      }
    }
    "read a channel from 0 to 255 only" in {
      ColorText.parseChannel("0") shouldBe Some(0)
      ColorText.parseChannel(" 255 ") shouldBe Some(255)
      ColorText.parseChannel("037") shouldBe Some(37)
      List("", "256", "-1", "1.5", "abc", "1000").foreach(t => ColorText.parseChannel(t) shouldBe None)
    }
    "say a hue in degrees and a fraction as a percentage" in {
      ColorText.degrees(0.5) shouldBe "180°"
      ColorText.degrees(1.0) shouldBe "0°"
      ColorText.percent(0.426) shouldBe "43%"
    }
  }

  "The recent colours" should {
    "put the latest first, once, and keep ten" in {
      val all = (0 until 12).map(i => new Color(i, 0, 0, 255)).toList
      val kept = all.foldLeft(List.empty[Color])((list, c) => ColorPicker.remembered(list, c))
      kept.size shouldBe 10
      kept.head shouldBe all.last
      ColorPicker.remembered(kept, all(5)).head shouldBe all(5)
      ColorPicker.remembered(kept, all(5)).size shouldBe 10
    }
  }
}
