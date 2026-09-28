package everyui.list

import everyui.focus.Focus
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** Results under headers: the matches lit, the keys across groups, groups closed and opened, results activated and
  * stepped through, and fifty thousand of them. */
class GroupSpec extends FxSpec {
  case class Hit(file: String, row: Int, text: String, spans: Seq[(Int, Int)]) {
    def id: String = file + ":" + row
  }

  private def press(code: KeyCode, text: String = ""): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, false, false, false, false))
  }

  private def found(files: Int, each: Int): Vector[ResultGroup[Hit]] = (0 until files).map { f =>
    val name = "File" + f + ".scala"
    ResultGroup(name, name, (0 until each).map(r => Hit(name, r, "  val needle = " + r, Seq((6, 12)))).toVector)
  }.toVector

  private def keys(results: GroupedList[Hit]): List[String] =
    fx((0 until results.list.count).map(i => results.list(i)).map(l => l.item.map(_.id).getOrElse(l.group.key)).toList)

  private def onScreen[T](results: GroupedList[?])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += results
      results.focus()
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "Match text" should {
    "cut a line into its plain and lit stretches, starting a long one near its first match" in {
      MatchText.pieces("val needle = 1", Seq((4, 10))) shouldBe Vector("val " -> false, "needle" -> true, " = 1" -> false)
      MatchText.pieces("a\tb", Seq((2, 3), (0, 1))) shouldBe Vector("a" -> true, " " -> false, "b" -> true)
      MatchText.pieces("abc", Seq((1, 9), (1, 2))) shouldBe Vector("a" -> false, "bc" -> true)
      val long = "x" * 100 + "match"
      MatchText.pieces(long, Seq((100, 105))).head shouldBe ("…" -> false)
      MatchText.pieces(long, Seq((100, 105))).last shouldBe ("match" -> true)
    }
  }

  "A grouped list" should {
    "show headers with counts over lit results, and move across groups with the keys" in {
      val groups = Var(found(3, 2))
      val results = fx(new GroupedList[Hit](groups, _.id, _.text, _.spans, h => (h.row + 1).toString))
      var activated: List[String] = Nil
      fx(results.activated.attach(h => activated = activated :+ h.id))
      onScreen(results) {
        keys(results) shouldBe List("File0.scala", "File0.scala:0", "File0.scala:1", "File1.scala", "File1.scala:0",
          "File1.scala:1", "File2.scala", "File2.scala:0", "File2.scala:1")
        val header = fx(results.list.rowAt(0).get.asInstanceOf[GroupRow[Hit]])
        fx(header.title.label.content()) shouldBe "File0.scala"
        fx(header.title.children().collect { case t: everyui.component.TextView => t.content() }) should contain("2")
        val result = fx(results.list.rowAt(1).get.asInstanceOf[GroupRow[Hit]])
        fx(result.line.text()) shouldBe "  val needle = 0"
        fx(result.line.lead()) shouldBe "1"
        // down through the first group into the next, Enter on a result activates it
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        fx(results.list.currentItem.map(_.group.key)) shouldBe Some("File1.scala")
        press(KeyCode.DOWN)
        press(KeyCode.ENTER)
        activated shouldBe List("File1.scala:0")
        // Left to the header, Left again closes it, Right opens it, Right again to its first result
        press(KeyCode.LEFT)
        fx(results.list.currentItem.map(_.item.isEmpty)) shouldBe Some(true)
        press(KeyCode.LEFT)
        fx(results.collapsed()) shouldBe Set("File1.scala")
        keys(results).size shouldBe 7
        press(KeyCode.RIGHT)
        press(KeyCode.RIGHT)
        fx(results.list.currentItem.flatMap(_.item).map(_.id)) shouldBe Some("File1.scala:0")
        // Enter on a header opens and closes it; a click on it too
        press(KeyCode.UP)
        press(KeyCode.ENTER)
        fx(results.collapsed()) shouldBe Set("File1.scala")
        fx(results.list.clicks(0, 30.0)) shouldBe true
        fx(results.collapsed()) shouldBe Set("File1.scala", "File0.scala")
        // stepping from result to result opens what it passes through
        fx(results.nextResult().map(_.id)) shouldBe Some("File0.scala:0")
        fx(results.nextResult().map(_.id)) shouldBe Some("File0.scala:1")
        fx(results.nextResult().map(_.id)) shouldBe Some("File1.scala:0")
        fx(results.previousResult().map(_.id)) shouldBe Some("File0.scala:1")
        fx(results.collapsed()) shouldBe Set.empty
      }
    }
    "keep the cursor as a search adds more results" in {
      val groups = Var(found(2, 3))
      val results = fx(new GroupedList[Hit](groups, _.id, _.text, _.spans))
      onScreen(results) {
        fx(results.list.select(GroupedList.itemKey("File1.scala", "File1.scala:2")))
        fx(groups @= found(5, 3))
        fx(results.list.currentItem.flatMap(_.item).map(_.id)) shouldBe Some("File1.scala:2")
      }
    }
    "take fifty thousand results" in {
      val groups = Var(Vector.empty[ResultGroup[Hit]])
      val results = fx(new GroupedList[Hit](groups, _.id, _.text, _.spans))
      onScreen(results) {
        val started = System.nanoTime()
        fx(groups @= found(500, 100))
        info(f"50k results in 500 groups shown: ${(System.nanoTime() - started) / 1e6}%.0f ms")
        fx(results.resultCount) shouldBe 50000
        fx(results.list.count) shouldBe 50500
        fx(results.list.rows.size) should be < 40
        val scrolled = System.nanoTime()
        fx((0 until 20000 by 5).foreach(y => results.list.scrollTop(y.toDouble)))
        info(f"4000 scroll steps: ${(System.nanoTime() - scrolled) / 1e6}%.0f ms")
      }
    }
  }
}
