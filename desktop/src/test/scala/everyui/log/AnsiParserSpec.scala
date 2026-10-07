package everyui.log

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The ANSI parser: every SGR form, the style carried from line to line, the escapes it drops (cursor moves, private
  * CSI, OSC by BEL and ST, the others), OSC 8 links, `\r` drawing over a line, tabs, escapes cut between pieces of a
  * stream, garbage, and a large input. */
class AnsiParserSpec extends AnyWordSpec with Matchers {
  private val E = "\u001b["

  private def one(text: String): AnsiLine = {
    val lines = new AnsiParser().feed(text + "\n")
    lines.length shouldBe 1
    lines.head
  }

  // the style over `words` in `line`
  private def styleOf(line: AnsiLine, words: String): AnsiStyle = {
    val at = line.text.indexOf(words)
    at should be >= 0
    line.spans.find(s => s.from <= at && s.to >= at + words.length).map(_.style).getOrElse(AnsiStyle.Plain)
  }

  "The ANSI parser" should {
    "read the sixteen colours, their bright forms and the defaults" in {
      val l = one(E + "31mred" + E + "39m plain " + E + "42mon green" + E + "49m " + E + "91mbright" + E + "0m " +
        E + "103mbright ground" + E + "m end")
      l.text shouldBe "red plain on green bright bright ground end"
      styleOf(l, "red").fg shouldBe 1
      styleOf(l, "plain") shouldBe AnsiStyle.Plain
      styleOf(l, "on green").bg shouldBe 2
      styleOf(l, "bright").fg shouldBe 9
      styleOf(l, "bright ground").bg shouldBe 11
      styleOf(l, "end") shouldBe AnsiStyle.Plain
    }

    "read the attributes and their undoing" in {
      val l = one(E + "1mB" + E + "2mBD" + E + "22mN" + E + "3mI" + E + "23m" + E + "4mU" + E + "24m" + E + "9mS" +
        E + "29m" + E + "7mR" + E + "27m" + E + "8mC" + E + "28mX" + E + "21mW" + E + "0m")
      l.text shouldBe "BBDNIUSRCXW"
      styleOf(l, "B") shouldBe AnsiStyle(bold = true)
      styleOf(l, "BD") shouldBe AnsiStyle(bold = true, dim = true)
      styleOf(l, "N") shouldBe AnsiStyle.Plain
      styleOf(l, "I").italic shouldBe true
      styleOf(l, "U").underline shouldBe true
      styleOf(l, "S").strike shouldBe true
      styleOf(l, "R").reverse shouldBe true
      styleOf(l, "C").conceal shouldBe true
      styleOf(l, "X") shouldBe AnsiStyle.Plain
      styleOf(l, "W").underline shouldBe true
    }

    "read several codes at once, and an empty one as a reset" in {
      val l = one(E + "1;4;31;44mall" + E + ";32mafter")
      styleOf(l, "all") shouldBe AnsiStyle(fg = 1, bg = 4, bold = true, underline = true)
      styleOf(l, "after") shouldBe AnsiStyle(fg = 2)
    }

    "read the 256 colours and colours of their own, by ';' and by ':'" in {
      val l = one(E + "38;5;196ma" + E + "48;5;21mb" + E + "0m" + E + "38;2;10;20;30mc" + E + "48;2;1;2;3md" + E + "0m" +
        E + "38:5:82me" + E + "38:2::200:100:50mf" + E + "38:2:7:8:9mg" + E + "0m" + E + "38;5;300mh")
      styleOf(l, "a").fg shouldBe 196
      styleOf(l, "b") shouldBe AnsiStyle(fg = 196, bg = 21)
      styleOf(l, "c").fg shouldBe AnsiStyle.rgb(10, 20, 30)
      styleOf(l, "d") shouldBe AnsiStyle(fg = AnsiStyle.rgb(10, 20, 30), bg = AnsiStyle.rgb(1, 2, 3))
      styleOf(l, "e").fg shouldBe 82
      styleOf(l, "f").fg shouldBe AnsiStyle.rgb(200, 100, 50)
      styleOf(l, "g").fg shouldBe AnsiStyle.rgb(7, 8, 9)
      styleOf(l, "h").fg shouldBe 255
      val t = LogTheme.of(everyui.theme.Palette.dark, raw = true)
      t.colour(196) shouldBe everyui.Color.fromHex("ff0000")
      t.colour(21) shouldBe everyui.Color.fromHex("0000ff")
      t.colour(232) shouldBe everyui.Color.fromHex("080808")
      t.colour(AnsiStyle.rgb(10, 20, 30)) shouldBe everyui.Color.fromHex("0a141e")
    }

    "carry the style on from line to line, as a terminal does" in {
      val p = new AnsiParser
      val lines = p.feed(E + "33mone\ntwo\n" + E + "0mthree\n")
      lines.map(_.text) shouldBe List("one", "two", "three")
      lines(1).spans shouldBe List(LogSpan(0, 3, AnsiStyle(fg = 3)))
      lines(2).spans shouldBe Nil
    }

    "drop cursor moves, private sequences, OSC and the other escapes" in {
      val l = one("a" + E + "2K" + E + "1;1H" + E + "?25l" + E + "?2004h" + E + ">4;1m" + "b" + "\u001b]0;title\u0007" +
        "c" + "\u001b]2;other\u001b\\" + "d" + "\u001b7\u001b8\u001b=\u001b(B" + "e" + "\u001bP+q544e\u001b\\" + "f" +
        "\u009b31mg" + "\u0007\b")
      l.text shouldBe "abcdefg"
      styleOf(l, "g").fg shouldBe 1
      styleOf(l, "b") shouldBe AnsiStyle.Plain
    }

    "make OSC 8 hyperlinks links" in {
      val l = one("see \u001b]8;;https://everyui.com/docs\u001b\\the docs\u001b]8;;\u001b\\ now")
      l.text shouldBe "see the docs now"
      l.spans shouldBe List(LogSpan(4, 12, AnsiStyle.Plain, "https://everyui.com/docs"))
      val bel = one("\u001b]8;id=1;https://x.y/\u0007x\u001b]8;;\u0007")
      bel.spans shouldBe List(LogSpan(0, 1, AnsiStyle.Plain, "https://x.y/"))
    }

    "keep only what a \\r drew last, with the style it set before" in {
      val p = new AnsiParser
      p.feed("Downloading 10%\r") shouldBe Nil
      p.feed("Downloading 50%\r" + E + "32mDownloading 100%") shouldBe Nil
      val lines = p.feed("\r\n")
      lines.map(_.text) shouldBe List("Downloading 100%")
      lines.head.spans.head.style.fg shouldBe 2
      new AnsiParser().feed("a\r\nb\n").map(_.text) shouldBe List("a", "b")
      one(E + "31mold\rnew").spans shouldBe List(LogSpan(0, 3, AnsiStyle(fg = 1)))
    }

    "set tabs out to every eighth column" in {
      one("a\tb").text shouldBe "a       b"
      one("abcdefgh\tx").text shouldBe "abcdefgh        x"
      one(E + "31mab" + E + "0m\tc").text shouldBe "ab      c"
      one("\t").text shouldBe "        "
    }

    "read an escape cut between two pieces of a stream" in {
      val text = E + "1;38;5;208mhot" + E + "0m and " + "\u001b]8;;https://a.b/\u0007link\u001b]8;;\u0007" + "\n"
      val whole = new AnsiParser().feed(text)
      var i = 1
      while (i < text.length) {
        val p = new AnsiParser
        (p.feed(text.substring(0, i)) ++ p.feed(text.substring(i))) shouldBe whole
        i += 1
      }
      whole.head.text shouldBe "hot and link"
      val p = new AnsiParser
      p.feed("half") shouldBe Nil
      p.pending shouldBe "half"
      p.flush().map(_.text) shouldBe List("half")
    }

    "survive garbage" in {
      val junk = List("\u001b", "\u001b[", "\u001b[38;5m", "\u001b[38;2;1m", "\u001b[38;5;mx", "\u001b[999999999999m",
        "\u001b[1;2;3;4;5;6;7;8;9;10;11;12;13;14;15;16;17;18;19;20;21;22m", "\u001b]8;;unterminated", "\u001b[é",
        "\u001b\u001b[31m", "\u001b[;;;m", "\u001b[38:2m", "\u0000\u0001\u0002", "\u009b", "\u009d0;t", "[31m")
      junk.foreach { j =>
        val lines = new AnsiParser().feed("<" + j + ">\n")
        lines.length shouldBe 1
        lines.head.text should startWith("<")
        lines.head.text.indexOf("\u001b") shouldBe -1
      }
      // random bytes, every one of them: never an exception, never an escape left in the words
      val random = new scala.util.Random(7)
      (1 to 200).foreach { _ =>
        val s = (1 to 80).map(_ => random.nextInt(140).toChar).mkString
        new AnsiParser().feed(s + "\n").foreach(l => l.text.indexOf("\u001b") shouldBe -1)
      }
    }

    "read a large input quickly, and cut a line that never ends" in {
      val line = E + "36;1m2026.10.07 12:00:01" + E + "0m [main] " + E + "34mINFO" + E + "0m dw.Loader - loaded 1204 wells\n"
      val began = System.nanoTime()
      val lines = new AnsiParser().feed(line * 50000)
      val took = (System.nanoTime() - began) / 1e6
      lines.length shouldBe 50000
      lines.last.text shouldBe "2026.10.07 12:00:01 [main] INFO dw.Loader - loaded 1204 wells"
      took should be < 3000.0
      val p = new AnsiParser
      val cut = p.feed("x" * (AnsiParser.longest + 10))
      cut.length shouldBe 1
      cut.head.text.length shouldBe AnsiParser.longest
      p.pending.length shouldBe 10
    }

    "strip a line to its words" in {
      AnsiParser.strip(E + "31merror" + E + "0m: " + E + "1mboom") shouldBe "error: boom"
    }
  }
}
