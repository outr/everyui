package everyui.log

import everyui.component.{Component, Modal}
import everyui.focus.Focus
import everyui.theme.{Palette, Theme}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}

/** The log view on the desktop's real nodes: streaming in and the cap, following the newest and keeping a reader's
  * place, searching (counts, next and previous, case, regular expressions), filtering by words, pattern and level,
  * continuation lines, wrapping, copying plain words, the colours in both themes, events in their order, and a log
  * of 50,000 lines filled, searched and filtered. */
class LogViewSpec extends FxSpec {
  private val E = "\u001b["

  private def copied: String = Option(javafx.scene.input.Clipboard.getSystemClipboard.getString).getOrElse("")

  private def onScreen[T](c: Component)(test: => T): T = {
    val kept = fx {
      val clipboard = javafx.scene.input.Clipboard.getSystemClipboard
      val content = new javafx.scene.input.ClipboardContent
      clipboard.getContentTypes.forEach(t => Option(clipboard.getContent(t)).foreach(v => content.put(t, v)))
      ui.children.clear()
      ui.children += c
      content
    }
    try test finally fx {
      Modal.close()
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
      javafx.scene.input.Clipboard.getSystemClipboard.setContent(kept)
    }
  }

  private def made(options: LogOptions = LogOptions(), width: Double = 800.0, height: Double = 400.0): LogView = fx {
    val v = new LogView(options)
    v.size.width @= width
    v.size.height @= height
    v
  }

  private def texts(v: LogView): Vector[String] = fx((0 until v.list.count).toVector.flatMap(i => v.list.itemAt(i)).map(_.text))

  private def atBottom(v: LogView): Boolean = fx(v.list.offset() >= v.list.total - v.list.size.height() - 1.0)

  private def settled(v: LogView): Unit = fx {
    v.settle()
    v.list.refresh()
  }

  "A log view" should {
    "show a stream's lines as they end, and keep the newest up to its cap" in {
      val v = made(LogOptions(maxLines = 100, lineNumbers = true))
      onScreen(v) {
        fx(v.append(E + "32mone" + E + "0m\ntw"))
        settled(v)
        texts(v) shouldBe Vector("one")
        fx(v.list.itemAt(0).get.spans) shouldBe List(LogSpan(0, 3, AnsiStyle(fg = 2)))
        fx(v.append("o\n"))
        fx(v.appendLine("three"))
        settled(v)
        texts(v) shouldBe Vector("one", "two", "three")
        fx((4 to 250).foreach(i => v.append("line " + i + "\n")))
        settled(v)
        fx(v.count()) shouldBe 100
        texts(v).head shouldBe "line 151"
        texts(v).last shouldBe "line 250"
        // numbered as the stream counts them, the gone included
        fx(v.gutterText(v.list.itemAt(0).get).trim) shouldBe "151"
        fx(v.clear())
        settled(v)
        fx(v.count()) shouldBe 0
        fx(v.list.count) shouldBe 0
      }
    }

    "follow the newest line, keep a reader's place as lines come and go, and jump back down" in {
      val v = made(LogOptions(maxLines = 300), height = 300.0)
      onScreen(v) {
        fx((1 to 200).foreach(i => v.appendLine("line " + i)))
        settled(v)
        atBottom(v) shouldBe true
        fx(v.following()) shouldBe true
        fx((201 to 220).foreach(i => v.appendLine("line " + i)))
        settled(v)
        atBottom(v) shouldBe true
        // scrolled up: let go, and the line at the top stays where it is as more come and the oldest go
        fx(v.list.scrollBy(-600.0))
        fx(v.following()) shouldBe false
        val (key, at) = fx {
          val i = v.list.indexAt(v.list.offset())
          (v.list.keyAt(i), v.list.top(i) - v.list.offset())
        }
        fx((221 to 330).foreach(i => v.appendLine("line " + i)))
        settled(v)
        fx(v.count()) shouldBe 300
        fx(v.unseen()) shouldBe 110
        fx(v.following()) shouldBe false
        fx(v.list.top(v.list.indexOf(key)) - v.list.offset()) shouldBe at
        // "Jump to latest"
        fx(v.toLatest())
        settled(v)
        atBottom(v) shouldBe true
        fx(v.unseen()) shouldBe 0
        fx(v.appendLine("newest"))
        settled(v)
        atBottom(v) shouldBe true
        texts(v).last shouldBe "newest"
        // the focus arriving puts the cursor on the newest line, in view, not on the first held
        fx(v.focus())
        fx(v.list.current()) shouldBe Some(fx(v.list.keyAt(v.list.count - 1)))
      }
    }

    "count a search's matches, go from one to the next and back, and mind case and patterns" in {
      val v = made(LogOptions(lineNumbers = true))
      onScreen(v) {
        fx((0 until 1000).foreach { i =>
          val words = if (i % 100 == 0) "a timeout, then " + E + "31manother timeout" + E + "0m" else if (i % 10 == 0) "read timeout" else "ok"
          v.appendLine("line " + i + ": " + words)
        })
        settled(v)
        fx(v.search @= "TIMEOUT")
        eventually(!v.searching()) shouldBe true
        fx(v.matches()) shouldBe 110
        val first = fx(v.matchIndex())
        first should be > 0
        fx(v.nextMatch())
        fx(v.matchIndex()) shouldBe (first % 110) + 1
        fx(v.previousMatch())
        fx(v.matchIndex()) shouldBe first
        // round from the last to the first, and back
        fx((1 to 110 - first).foreach(_ => v.nextMatch()))
        fx(v.matchIndex()) shouldBe 110
        fx(v.nextMatch())
        fx(v.matchIndex()) shouldBe 1
        fx(v.previousMatch())
        fx(v.matchIndex()) shouldBe 110
        // the match it is on is in view, and marked in its line
        val (line, shows) = fx {
          val id = v.list.current().get
          val i = v.list.indexOf(id)
          (v.list.itemAt(i).get, v.list.top(i) >= v.list.offset() && v.list.top(i) + 19.0 <= v.list.offset() + v.list.size.height())
        }
        shows shouldBe true
        fx(v.matchesIn(line).length) should be > 0
        fx(v.searchCase @= true)
        eventually(!v.searching()) shouldBe true
        fx(v.matches()) shouldBe 0
        fx(v.searchCase @= false)
        fx(v.searchRegex @= true)
        fx(v.search @= "line \\d*0: a time")
        eventually(!v.searching()) shouldBe true
        fx(v.matches()) shouldBe 10
        fx(v.search @= "(unclosed")
        fx(v.broken()) shouldBe true
        fx(v.matches()) shouldBe 0
        fx(v.searchRegex @= false)
        fx(v.broken()) shouldBe false
        eventually(!v.searching()) shouldBe true
        fx(v.matches()) shouldBe 0
      }
    }

    "filter by words, by a pattern and by level, counting what it hides" in {
      val v = made()
      onScreen(v) {
        fx {
          v.appendLine("2026.10.07 12:00:00 [main] INFO dw.Loader - loaded 12 wells")
          v.appendLine("2026.10.07 12:00:01 [main] WARN dw.Loader - slow page")
          v.appendLine("2026.10.07 12:00:02 [main] ERROR dw.Upload - upload timed out")
          v.appendLine("\tat dw.Upload.send(Upload.scala:64)")
          v.appendLine("2026.10.07 12:00:03 [main] DEBUG dw.Loader - parsed")
          v.missed(3L)
          v.appendLine("2026.10.07 12:00:04 [main] INFO dw.Loader - loaded 40 wells")
        }
        settled(v)
        fx(v.count()) shouldBe 7
        fx(v.filter @= LogFilter(text = "WELLS"))
        texts(v) shouldBe Vector("2026.10.07 12:00:00 [main] INFO dw.Loader - loaded 12 wells", LogWords.missed(3L),
          "2026.10.07 12:00:04 [main] INFO dw.Loader - loaded 40 wells")
        fx(v.hidden()) shouldBe 4
        fx(v.filter @= LogFilter(text = "loaded \\d{2} wells", regex = true))
        texts(v).length shouldBe 3
        fx(v.filter @= LogFilter(text = "WELLS", caseSensitive = true))
        texts(v) shouldBe Vector(LogWords.missed(3L))
        // by level: the stack trace's line follows its error
        fx(v.filter @= LogFilter(levels = Set(LogLevel.Warn, LogLevel.Error)))
        texts(v) shouldBe Vector("2026.10.07 12:00:01 [main] WARN dw.Loader - slow page",
          "2026.10.07 12:00:02 [main] ERROR dw.Upload - upload timed out", "        at dw.Upload.send(Upload.scala:64)",
          LogWords.missed(3L))
        fx(v.continuations @= false)
        texts(v).length shouldBe 3
        fx(v.continuations @= true)
        // a line coming in while filtered shows where it passes
        fx {
          v.appendLine("2026.10.07 12:00:05 [main] ERROR dw.Upload - again")
          v.appendLine("2026.10.07 12:00:06 [main] INFO dw.Upload - fine")
        }
        settled(v)
        texts(v).last shouldBe "2026.10.07 12:00:05 [main] ERROR dw.Upload - again"
        fx(v.hidden()) shouldBe 4
        // the app's own classifier
        fx(v.level @= (line => if (line.contains("wells")) Some(LogLevel.Error) else None))
        texts(v).count(_.contains("wells")) shouldBe 2
        fx(v.filter @= LogFilter())
        fx(v.hidden()) shouldBe 0
      }
    }

    "wrap long lines at its columns, each as tall as the rows it takes" in {
      val v = made(LogOptions(wrap = true), width = 400.0)
      onScreen(v) {
        fx {
          v.appendLine("short")
          v.appendLine("x" * 200)
          v.appendLine("after")
        }
        settled(v)
        val cols = fx(v.columns)
        cols should be > 8
        cols should be < 200
        fx(v.list.heightAt(0)) shouldBe v.lineHeight
        fx(v.list.heightAt(1)) shouldBe v.lineHeight * ((200 + cols - 1) / cols)
        fx(v.list.top(2)) shouldBe fx(v.list.heightAt(0) + v.list.heightAt(1))
        fx(v.list.indexAt(v.list.top(1) + v.lineHeight * 1.5)) shouldBe 1
        fx(v.wrap @= false)
        settled(v)
        fx(v.list.heightAt(1)) shouldBe v.lineHeight
        fx(v.furthestX) should be > 0.0
        fx(v.scrollSideways(100.0))
        fx(v.scrollX()) shouldBe 100.0
      }
    }

    "copy its lines' words without their escapes" in {
      val v = made()
      onScreen(v) {
        fx {
          v.appendLine(E + "31merror" + E + "0m: " + E + "1mboom" + E + "0m")
          v.appendLine("plain\tline")
          v.appendLine(E + "38;5;208mthird")
        }
        settled(v)
        fx(v.list.selectRange(0, 2))
        fx(v.selectedText) shouldBe "error: boom\nplain   line\nthird"
        fx(v.copySelected())
        eventually(copied == "error: boom\nplain   line\nthird") shouldBe true
        // the context menu's Copy line
        val line = fx(v.list.itemAt(1).get)
        val items = fx(v.menuFor(line))
        items.head.label shouldBe LogWords.copyLine
        fx(items.head.action())
        eventually(copied == "plain   line") shouldBe true
        items.map(_.label) should contain(LogWords.copyLines(3))
        // Ctrl+C with the focus on the lines and one chosen
        fx {
          v.focus()
          v.list.selection.only(v.list.keyAt(2))
          v.list.current @= Some(v.list.keyAt(2))
        }
        fx {
          javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "c", "c", KeyCode.C, false, true, false, false))
          javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "c", "c", KeyCode.C, false, true, false, false))
        }
        eventually(copied == "third") shouldBe true
      }
    }

    "draw the ANSI colours so they read in a dark theme and a light one" in {
      val dark = LogTheme.of(Palette.dark)
      val light = LogTheme.of(Palette.light)
      def contrast(t: LogTheme, c: everyui.Color): Double =
        LogTheme.contrast(LogTheme.luminance(c), LogTheme.luminance(t.background))
      (0 until 16).foreach { c =>
        contrast(dark, dark.letters(AnsiStyle(fg = c))) should be >= 4.5
        contrast(light, light.letters(AnsiStyle(fg = c))) should be >= 4.5
      }
      contrast(light, light.letters(AnsiStyle(fg = AnsiStyle.rgb(255, 255, 0)))) should be >= 4.5
      contrast(dark, dark.letters(AnsiStyle(fg = 16))) should be >= 4.5
      // a colour that reads already is left as it is
      dark.letters(AnsiStyle(fg = 10)) shouldBe dark.colour(10)
      // xterm's own, as they are
      val raw = LogTheme.of(Palette.light, raw = true)
      raw.letters(AnsiStyle(fg = 11)) shouldBe everyui.Color.fromHex("ffff00")
      // reversed, dimmed, a ground
      dark.letters(AnsiStyle(reverse = true)) shouldBe dark.background
      dark.ground(AnsiStyle(reverse = true)) shouldBe Some(dark.foreground)
      dark.ground(AnsiStyle(bg = 4)) shouldBe Some(dark.colour(4))
      dark.letters(AnsiStyle(dim = true)) should not be dark.foreground
      // a view's follow the theme's palette
      val v = made()
      fx(v.theme().background) shouldBe Palette.dark.surface
      fx(Theme.palette @= Palette.light)
      try fx(v.theme().background) shouldBe Palette.light.surface
      finally fx(Theme.palette @= Palette.dark)
      fx(v.dispose())
    }

    "show a server's events in their order, each once, and mark what it missed" in {
      val v = made(LogOptions(lineNumbers = true, timestamps = true, sources = true))
      onScreen(v) {
        def ev(seq: Long, text: String, level: String = "INFO"): LogEvent = LogEvent(seq, 1790000000000L + seq, level, text, "api")
        fx {
          v.append(ev(1, "one"))
          v.append(ev(2, "two"))
          v.append(ev(4, "four"))
          v.append(ev(2, "two again"))
          v.append(ev(3, "three", "WARN"))
          v.append(ev(5, "five\n\tat trace", "ERROR"))
          v.append(ev(4, "four again"))
        }
        settled(v)
        texts(v) shouldBe Vector("one", "two", "three", "four", "five", "        at trace")
        fx(v.lastSeq) shouldBe 5L
        fx(v.list.itemAt(2).get.level) shouldBe Some(LogLevel.Warn)
        fx(v.list.itemAt(5).get.level) shouldBe Some(LogLevel.Error)
        // its time and source in the gutter
        fx(v.gutterText(v.list.itemAt(0).get)) should include(LogView.time(1790000000001L))
        fx(v.gutterText(v.list.itemAt(0).get)) should include("api")
        // a seq put in between two that are next to each other
        fx {
          v.missed(2L)
          v.append(ev(8, "eight"))
          v.append(ev(6, "six"))
        }
        settled(v)
        texts(v).drop(6) shouldBe Vector("six", LogWords.missed(2L), "eight")
        // the server started again: its numbering from 1
        fx {
          v.restarted()
          v.append(ev(1, "new one"))
        }
        settled(v)
        texts(v).takeRight(2) shouldBe Vector(LogWords.restarted, "new one")
      }
    }

    "read a burst a stretch a turn, knowing its newest seq before it is read" in {
      val v = made()
      onScreen(v) {
        fx {
          (0 until 30000).foreach(i => v.appendLine("line " + i + " " + E + "32m" + "x" * 40 + E + "0m"))
          v.append(LogEvent(7L, 0L, "", "an event"))
        }
        fx(v.lastSeq) shouldBe 7L
        fx(v.count()) should be < 30001
        eventually(v.count() == 30001) shouldBe true
        texts(v).last shouldBe "an event"
      }
    }

    "fill, search and filter 50,000 lines in good time" in {
      val v = made(LogOptions(maxLines = 50000, lineNumbers = true), height = 600.0)
      onScreen(v) {
        val levels = Vector("INFO", "DEBUG", "INFO", "WARN", "INFO", "ERROR")
        val began = System.nanoTime()
        fx {
          var i = 0
          while (i < 60000) {
            v.append(E + "36;1m2026.10.07 12:00:" + (i % 60) + E + "0m [main] " + E + "34m" + levels(i % 6) + E +
              "0m dw.Loader.load:" + i + " - loaded " + (i * 7 % 9000) + " wells from louisiana\n")
            i += 1
          }
        }
        settled(v)
        val filled = (System.nanoTime() - began) / 1e6
        fx(v.count()) shouldBe 50000
        atBottom(v) shouldBe true
        val searching = System.nanoTime()
        fx(v.search @= "wells from")
        eventually(!v.searching()) shouldBe true
        // (eventually gives up after ten seconds)
        val searched = (System.nanoTime() - searching) / 1e6
        fx(v.matches()) shouldBe 50000
        val filtering = System.nanoTime()
        fx(v.filter @= LogFilter(levels = Set(LogLevel.Error)))
        val filtered = (System.nanoTime() - filtering) / 1e6
        fx(v.list.count) shouldBe (10000 until 60000).count(_ % 6 == 5)
        info(f"filled 60,000 in $filled%.0f ms, searched 50,000 in $searched%.0f ms, filtered in $filtered%.0f ms")
        filled should be < 8000.0
        filtered should be < 2000.0
      }
    }
  }
}
