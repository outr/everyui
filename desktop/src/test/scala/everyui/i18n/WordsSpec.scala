package everyui.i18n

import everyui.FxSpec
import everyui.chat.{ChatTime, ChatWords, ComposerWords}
import everyui.color.ColorPicker
import everyui.date.DateWords
import everyui.list.RichList
import everyui.log.LogWords
import everyui.table.TableWords

/** EveryUI's own words come in every language it ships, each with the same placeholders as the English. */
class WordsSpec extends FxSpec {
  private val shipped = List("en", "de", "es", "fr", "it", "nl", "pt", "tr", "ar", "zh", "zh_Hant")
  private val english = Words.languages("en")
  private val tabs = List("add", "close", "closeOthers", "closeAll", "openRight", "openDown", "moveRight", "moveLeft",
    "moveDown", "moveUp", "unsplit", "nextGroup", "previousGroup", "maximize", "unmaximize", "none").map("tabs." + _)
  /** The families of words that were English alone until they were given to Words, and how many each has. */
  private val families = List("chat." -> 48, "composer." -> 11, "date." -> 20, "log." -> 22, "table." -> 14,
    "list." -> 2, "color." -> 6, "map." -> 2, "graph." -> 1, "markdown." -> 3)

  /** The names of a message's arguments: `{n}`, `{n, plural, …}`, not a plural's own branches. */
  private def arguments(message: String): Set[String] = "\\{(\\w+)[,}]".r.findAllMatchIn(message).map(_.group(1)).toSet

  /** Everything the components say through their words objects, in the language in use. */
  private def everything: List[String] = List(
    ChatWords.conversation, ChatWords.jumpToLatest, ChatWords.newCount(3), ChatWords.workingCount(2), ChatWords.now,
    ChatWords.minutesAgo(5L), ChatWords.hoursAgo(3L), ChatWords.daysAgo(2L), ChatWords.today, ChatWords.yesterday,
    ChatWords.thinking, ChatWords.thoughtFor("12s"), ChatWords.input, ChatWords.output, ChatWords.running,
    ChatWords.busy, ChatWords.succeeded, ChatWords.failed, ChatWords.connecting, ChatWords.offline,
    ChatWords.loadingOlder, ChatWords.show, ChatWords.hide, ChatWords.showMore, ChatWords.showLess, ChatWords.copy,
    ChatWords.copyMessage, ChatWords.copied, ChatWords.messageActions, ChatWords.selectAll, ChatWords.about("Ada"),
    ChatWords.queued(2), ChatWords.queuePaused, ChatWords.resume, ChatWords.sendNext, ChatWords.clearQueue,
    ChatWords.queueMore, ChatWords.addToQueue, ChatWords.edit, ChatWords.remove, ChatWords.fileCount(2),
    ChatWords.queuedItem(1, 2, "Hi"), ChatWords.queuedHelp, ChatWords.moved(1, 2), ChatWords.reorder,
    ChatTime.span(12000L), ChatTime.span(69000L), ChatTime.span(7500000L),
    DateWords.selectDate, DateWords.selectRange, DateWords.date, DateWords.start, DateWords.end,
    DateWords.previousMonth, DateWords.nextMonth, DateWords.previousYears, DateWords.nextYears, DateWords.previousYear,
    DateWords.nextYear, DateWords.chooseYear, DateWords.chooseMonth, DateWords.openCalendar, DateWords.today,
    DateWords.selected, DateWords.invalid, DateWords.outOfRange, DateWords.endBeforeStart, DateWords.noDate,
    LogWords.log, LogWords.jumpToLatest, LogWords.newCount(12), LogWords.matchOf(3, 41), LogWords.noMatches,
    LogWords.badPattern, LogWords.hidden(7), LogWords.missed(1L), LogWords.missed(12L), LogWords.restarted,
    LogWords.search, LogWords.filter, LogWords.previous, LogWords.next, LogWords.matchCase, LogWords.regex,
    LogWords.wrap, LogWords.follow, LogWords.clear, LogWords.copyLine, LogWords.copyLines(3), LogWords.selectAll,
    TableWords.actions, TableWords.copy, TableWords.copyMarkdown, TableWords.copyRows(1), TableWords.copyRows(3),
    TableWords.exportCsv, TableWords.exportMarkdown, TableWords.filter, TableWords.shownOf(12, 60), TableWords.rows(1),
    TableWords.rows(60), TableWords.copied(1), TableWords.copied(12), TableWords.copiedCell,
    RichList.moreLabel, RichList.unreadLabel, ColorPicker.hueLabel, ColorPicker.saturationLabel,
    ColorPicker.valueLabel, ColorPicker.alphaLabel, ColorPicker.hexLabel, ColorPicker.recentLabel,
    Words("map.zoomIn"), Words("map.zoomOut"), Words("graph.graph"), Words("markdown.copy"), Words("markdown.done"),
    Words("markdown.notDone")) ++ {
    val c = ComposerWords()
    List(c.send, c.stop, c.attach, c.more, c.remove, c.drop, c.suggestion, c.accept, c.tooLarge, c.notAccepted,
      c.unavailable)
  }

  /** `f` in language `code`, the language put back after. */
  private def in[T](code: String)(f: => T): T = {
    val was = fx(Strings.language())
    try {
      fx(Strings.language @= Language(code, code, code))
      fx(f)
    }
    finally fx(Strings.language @= was)
  }

  "Words" should {
    "ship every language" in {
      shipped.foreach(code => Words.languages.keySet should contain(code))
    }
    "have every key in every language" in {
      shipped.foreach { code =>
        withClue(code + ": ")(Words.languages(code).keySet shouldBe english.keySet)
      }
    }
    "keep each message's placeholders in every language" in {
      for (code <- shipped; (key, message) <- Words.languages(code)) {
        withClue(code + " " + key + ": ")(arguments(message) shouldBe arguments(english(key)))
      }
    }
    "say the tab menu's words in every language" in {
      english.keySet should contain allElementsOf tabs
      shipped.filter(_ != "en").foreach { code =>
        withClue(code + ": ")(tabs.count(k => Words.languages(code)(k) != english(k)) shouldBe tabs.size)
      }
    }
    "have each family of the components' words, translated in every language" in {
      families.foreach { case (prefix, n) =>
        withClue(prefix + ": ")(english.keySet.count(_.startsWith(prefix)) shouldBe n)
      }
      val keys = english.keySet.filter(k => families.exists(f => k.startsWith(f._1)))
      shipped.filter(_ != "en").foreach { code =>
        val same = keys.filter(k => Words.languages(code)(k) == english(k))
        // a word or two is the same as the English ("Hex", "Offline", French "Conversation"), never many
        withClue(code + " same as English " + same + ": ")(same.size should be <= 6)
      }
    }
    "say every component's words by its key in every language, never the key itself" in {
      shipped.foreach { code =>
        in(code)(everything).foreach { said =>
          withClue(code + ": ")(said.matches("[a-z]+\\.[A-Za-z]+") shouldBe false)
          withClue(code + ": ")(said should not include "{")
        }
      }
    }
    "say the components' words in English as before" in {
      in("en") {
        ChatWords.minutesAgo(5L) shouldBe "5m"
        ChatTime.span(69000L) shouldBe "1m 9s"
        ChatWords.thoughtFor("12s") shouldBe "Thought for 12s"
        LogWords.missed(12L) shouldBe "… 12 lines missed"
        LogWords.matchOf(3, 41) shouldBe "3 of 41"
        TableWords.rows(60) shouldBe "60 rows"
        TableWords.copied(1) shouldBe "Copied 1 row"
        ComposerWords() shouldBe ComposerWords("Send", "Stop", "Attach files", "More", "Remove", "Drop files to attach",
          "Suggestion", "Tab or Right arrow accepts it", "is too large to attach", "cannot be attached here", "unavailable")
        ColorPicker.valueLabel shouldBe "Brightness"
      }
    }
    "follow the language" in {
      in("de")(ChatWords.jumpToLatest) shouldBe "Zum Neuesten springen"
      in("fr")(ChatTime.span(7500000L)) shouldBe "2 h 5 min"
      in("zh_Hant")(TableWords.rows(3)) shouldBe "3 列"
      in("pt_BR")(ComposerWords().send) shouldBe "Enviar"
    }
    "count in Arabic with its dual, its plural from 3 to 10 and its singular from 11" in {
      in("ar")(List(1, 2, 3, 10, 11, 99, 100, 103).map(TableWords.rows)) shouldBe
        List("صف واحد", "صفان", "3 صفوف", "10 صفوف", "11 صفًا", "99 صفًا", "100 صف", "103 صفوف")
      in("ar")(ChatWords.fileCount(2)) shouldBe "ملفان"
      in("ar")(LogWords.copyLines(2)) shouldBe "نسخ سطرين"
    }
    "count one in French and Portuguese from zero, in Chinese never" in {
      val message = Map("n" -> "0")
      in("fr")(Icu.format("{n, plural, one{# ligne} other{# lignes}}", message)) shouldBe "0 ligne"
      in("en")(Icu.format("{n, plural, one{# line} other{# lines}}", message)) shouldBe "0 lines"
      in("zh")(Icu.format("{n, plural, one{# one} other{# 行}}", Map("n" -> "1"))) shouldBe "1 行"
      in("es")(ChatWords.newCount(1)) shouldBe "1 nuevo"
      in("es")(ChatWords.newCount(4)) shouldBe "4 nuevos"
    }
  }
}
