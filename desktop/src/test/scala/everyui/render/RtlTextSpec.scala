package everyui.render

import everyui.FxSpec
import javafx.scene.text.{Font, Text}

/** Right-to-left words broken into lines by the desktop itself, as JavaFX cannot wrap them: at spaces where the next
  * word would not fit, a word wider than a line between its letters, the text's own line breaks kept, and a paragraph
  * of runs in several faces broken as one; and JavaFX lays them out without throwing. */
class RtlTextSpec extends FxSpec {
  private val font = Font.font("System", 14.0)

  "RtlText" should {
    "break at spaces where the next word would not fit, and keep the text's own breaks" in {
      val words = "مرحبا بكم في الاجتماع الأسبوعي\nشكرا"
      val laid = fx(RtlText.lines(words, font, 90.0))
      laid.replace('\n', ' ') shouldBe words.replace('\n', ' ')
      laid.split("\n").length should be > 3
      laid.split("\n").last shouldBe "شكرا"
      fx(laid.split("\n").forall(l => RtlText.across(font, l) <= 90.0)) shouldBe true
    }
    "break a word wider than a line between its letters" in {
      val laid = fx(RtlText.lines("ا" * 60, font, 50.0))
      laid.replace("\n", "") shouldBe "ا" * 60
      fx(laid.split("\n").forall(l => RtlText.across(font, l) <= 50.0)) shouldBe true
    }
    "break runs in several faces as one paragraph, each break in the run it falls in" in {
      val mono = Font.font("Monospaced", 14.0)
      val pieces = List(("قبل الإصدار ", font), ("SocketSpec", mono), (" بعد الظهر وأرسل لكم النتيجة", font))
      val laid = fx(RtlText.lines(pieces, 120.0))
      laid.size shouldBe 3
      laid(1) shouldBe "SocketSpec"
      laid.mkString.replace('\n', ' ') shouldBe pieces.map(_._1).mkString
      laid.mkString.count(_ == '\n') should be >= 2
    }
    "lay a Text's words so JavaFX need not wrap them" in {
      val t = fx {
        val t = new Text()
        t.setFont(font)
        t.setWrappingWidth(80.0)
        RtlText.lay(t, "صباح الخير! هل ملاحظات الإصدار جاهزة؟")
        t
      }
      fx(t.getLayoutBounds.getHeight) should be > 40.0
      fx(RtlText.words(t)) shouldBe "صباح الخير! هل ملاحظات الإصدار جاهزة؟"
      // narrower, laid again
      fx(t.setWrappingWidth(50.0))
      fx(t.getText.count(_ == '\n')) should be > 2
    }
  }
}
