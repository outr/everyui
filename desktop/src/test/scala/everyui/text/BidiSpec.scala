package everyui.text

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Which way text reads by its first letter with a direction, as the JVM's own character data has it. */
class BidiSpec extends AnyWordSpec with Matchers {
  private def jvm(c: Int): Option[Boolean] = Character.getDirectionality(c) match {
    case Character.DIRECTIONALITY_LEFT_TO_RIGHT => Some(false)
    case Character.DIRECTIONALITY_RIGHT_TO_LEFT | Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC => Some(true)
    case _ => None
  }

  "Bidi.firstStrong" should {
    "go by the first letter with a direction" in {
      Bidi.firstStrong("Morning! Where are we with the release?") shouldBe Some(false)
      Bidi.firstStrong("صباح الخير! أين وصلنا؟") shouldBe Some(true)
      Bidi.firstStrong("שלום") shouldBe Some(true)
      Bidi.firstStrong("2 مرحبا") shouldBe Some(true)
      Bidi.firstStrong("«src/Main.scala» ما زال") shouldBe Some(false)
      Bidi.firstStrong("#42: 修正") shouldBe Some(false)
      Bidi.firstStrong("Привет") shouldBe Some(false)
      Bidi.firstStrong("12:30 😀 ٣٤") shouldBe None
      Bidi.firstStrong("") shouldBe None
      Bidi.firstStrong("𐤀𐤁") shouldBe Some(true)
    }
    "agree with the JVM on the letters of the first 65536" in {
      val wrong = (0 until 0x10000).filter { c =>
        val letter = Character.isLetter(c)
        val ours = Bidi.firstStrong(new String(Character.toChars(c)))
        letter && !Character.isSurrogate(c.toChar) && ours != jvm(c)
      }
      // a handful of rare letters in blocks the ranges take whole may differ; none of the common scripts
      wrong.size should be < 200
      wrong.filter(c => c < 0x2000 || (c >= 0xFB1D && c <= 0xFEFF)) shouldBe empty
    }
  }

  "Bidi.rightToLeft" should {
    "take the words' own direction for auto, falling back to the app's" in {
      Bidi.rightToLeft("auto", "Hello", rtl = true) shouldBe false
      Bidi.rightToLeft("auto", "مرحبا", rtl = false) shouldBe true
      Bidi.rightToLeft("auto", "42", rtl = true) shouldBe true
      Bidi.rightToLeft("", "Hello", rtl = true) shouldBe true
      Bidi.rightToLeft("ltr", "مرحبا", rtl = true) shouldBe false
      Bidi.rightToLeft("rtl", "Hello", rtl = false) shouldBe true
    }
  }
}
