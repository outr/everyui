package everyui.terminal

import everyui.Color
import everyui.theme.Palette
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets.UTF_8

/** The bridge's text (TerminalWire), the palette's colours as a terminal's (TerminalTheme), and UTF-8 read a piece at
  * a time (Utf8Stream). */
class TerminalWireSpec extends AnyWordSpec with Matchers {
  "The wire" should {
    "carry any payload whole, the separator included" in {
      val text = TerminalWire.message("input", "everyui-embed-3", "a\u001fb\u0003\u001b[A")
      TerminalWire.parse(text) shouldBe Some(("input", "everyui-embed-3", "a\u001fb\u0003\u001b[A"))
      TerminalWire.parse("bell\u001fx\u001f") shouldBe Some(("bell", "x", ""))
      TerminalWire.parse("nonsense") shouldBe None
    }
    "read a grid" in {
      TerminalWire.size("80,24") shouldBe Some(TerminalSize(80, 24))
      TerminalWire.size("80") shouldBe None
      TerminalWire.size("a,b") shouldBe None
    }
    "write options and chords as JSON the guest reads" in {
      TerminalWire.obj("fontSize" -> TerminalWire.num(13.0), "fontFamily" -> TerminalWire.str("'Roboto Mono'\n")) shouldBe
        "{\"fontSize\":13,\"fontFamily\":\"'Roboto Mono'\\n\"}"
      TerminalWire.num(13.5) shouldBe "13.5"
      TerminalWire.chords(List(("=", true, false, false, false), (" ", false, true, false, true))) shouldBe
        "[{\"key\":\"=\",\"ctrl\":true,\"meta\":false,\"alt\":false,\"shift\":false}," +
          "{\"key\":\"space\",\"ctrl\":false,\"meta\":true,\"alt\":false,\"shift\":true}]"
    }
    "write a theme as xterm.js takes it" in {
      val json = TerminalWire.theme(TerminalTheme.of(Palette.dark))
      json should include("\"background\":\"#1e1e1e\"")
      json should include("\"foreground\":\"#e0e0e0\"")
      json should include("\"brightWhite\":\"#ffffff\"")
      json should include("\"selectionBackground\":\"rgba(144, 202, 249, 0.")
    }
  }

  "A terminal's theme" should {
    "take the ANSI colours for its palette's ground" in {
      TerminalTheme.of(Palette.dark).ansi shouldBe TerminalTheme.darkAnsi
      TerminalTheme.of(Palette.light).ansi shouldBe TerminalTheme.lightAnsi
      TerminalTheme.of(Palette.light).background shouldBe Color.White
      TerminalTheme.darkAnsi should have size 16
      TerminalTheme.lightAnsi should have size 16
    }
  }

  "A UTF-8 stream" should {
    "hold back a character split between pieces" in {
      val bytes = "héllo → 日本 🎉".getBytes(UTF_8)
      // every split point, including inside each multi-byte character
      (0 to bytes.length).foreach { at =>
        val s = new Utf8Stream
        (s.decode(bytes.take(at)) + s.decode(bytes.drop(at))) shouldBe "héllo → 日本 🎉"
      }
      // a byte at a time
      val s = new Utf8Stream
      bytes.map(b => s.decode(Array(b))).mkString shouldBe "héllo → 日本 🎉"
    }
    "give what is left as replacement characters when it ends" in {
      val s = new Utf8Stream
      s.decode(Array(0x61.toByte, 0xe6.toByte, 0x97.toByte)) shouldBe "a"
      s.flush() shouldBe "\ufffd"
      s.flush() shouldBe ""
    }
  }
}
