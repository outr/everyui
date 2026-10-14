package everyui.list

import everyui.component.Component
import everyui.event.Pointer
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.perf.{Quality, QualityLevel}
import everyui.task.Frames
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** A `RichList`'s items expanding in place (`RichList.expansion`): a click on the title's line, a tap or Enter opening
  * one to its whole text as Markdown with its actions as buttons under it, and again folding it, as Escape does; one at
  * a time or several; a click on the text or a button never folding it; Right past the row's action onto the buttons,
  * Enter running one; the row as tall as its text and kept in view as it grows; search and grouping as they were; in a
  * tree and in blocks, mirrored right to left. Lists without an expansion are made as they were. */
class RowExpandSpec extends FxSpec {
  private val width = 320.0
  private var ran: List[String] = Nil

  private val long: String = (1 to 12).map(i => "Paragraph " + i.toString + " of a long memory, wrapped onto lines of " +
    "its own within the row and never cut short.").mkString("\n\n")

  // "x" items expand (x2's text is long), "p" items do not; x0 has a star
  private def look(s: String): RichLook = RichLook(s, detail = Some("detail of " + s),
    action = if (s == "x0") Some(RichAction(MaterialIcon.StarBorder, "Star " + s, () => ran = ran :+ ("star " + s))) else None)

  private def expansion(s: String): Option[RichExpansion] = if (!s.startsWith("x")) None
    else Some(RichExpansion(if (s == "x2") long else if (s == "x3") "Plain words of " + s + ", **not** Markdown"
      else "The whole of " + s + ", with **bold** words and a list:\n\n- one\n- two",
      List(RichAction(MaterialIcon.PushPin, "Pin", () => ran = ran :+ ("pin " + s)),
        RichAction(MaterialIcon.Delete, "Delete", () => ran = ran :+ ("delete " + s), danger = true)),
      markdown = s != "x3"))

  private def sections(): Var[Vector[RichSection[String]]] = Var(Vector(
    RichSection("a", "Alpha", Vector("x0", "x1", "p0", "x2")),
    RichSection("b", "Beta", Vector("x3", "p1"), group = Some(RichGroup("g", "Group")))))

  private def made(style: RichStyle, height: Double = 1000.0, expands: Boolean = true): RichList[String] = {
    val list = new RichList[String](sections(), s => s, look)
    list.style @= style
    if (expands) list.expansion @= ((s: String) => expansion(s))
    list.size.width @= width
    list.size.height @= height
    list
  }

  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def lines(list: RichList[String]): List[String] = onFx((0 until list.list.count).map(list.list(_)).map(l =>
    l.item.getOrElse(l.groupHead.map("##" + _.key).getOrElse("#" + l.section.key))).toList)

  private def at(list: RichList[String], line: String): Int = lines(list).indexOf(line)

  private def row(list: RichList[String], line: String): RichRow[String] =
    onFx(list.list.rowAt(at(list, line)).get.asInstanceOf[RichRow[String]])

  private def cursor(list: RichList[String]): Option[String] = fx(list.list.currentItem.map(l =>
    l.item.getOrElse("#" + l.section.key)))

  private def key(code: KeyCode, down: Boolean): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(if (down) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED, "", "",
      code, false, false, false, false))
  }

  private def press(code: KeyCode): Unit = {
    key(code, down = true)
    key(code, down = false)
  }

  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double): Unit =
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false, false,
      false, kind == MouseEvent.MOUSE_PRESSED, false, false, false, false, true, null))

  // a click as a mouse makes it, down and up and the click, `down` from the component's top (its middle, unless given)
  private def click(on: Component, along: Double = -1.0, down: Double = -1.0): Unit = {
    // (a click at the same pixel within Pointer.hold of one consumed is taken for it)
    Thread.sleep(Pointer.hold.toLong * 2L)
    fx {
      val x = on.absoluteLeft + (if (along < 0.0) on.size.width() / 2.0 else along)
      val y = on.absoluteTop + (if (down < 0.0) on.size.height() / 2.0 else down)
      mouse(on, MouseEvent.MOUSE_PRESSED, x, y)
      mouse(on, MouseEvent.MOUSE_RELEASED, x, y)
      mouse(on, MouseEvent.MOUSE_CLICKED, x, y)
    }
  }

  // the row on line `line` drawn at the height the list has for it, and the list settled
  private def settled(list: RichList[String], line: String): Boolean = eventually {
    val i = at(list, line)
    val r = list.list.rowAt(i)
    r.exists(x => x.size.height() == list.list.heightAt(i))
  }

  private def onScreen[T](list: RichList[String])(test: => T): T = {
    ran = Nil
    fx {
      ui.children.clear()
      ui.children += list
      list.focus()
    }
    try test finally fx {
      Direction.set(false)
      ui.children.clear()
      // taken apart, done with (its clock, a Ticker, rests already, off the screen)
      list.dispose()
      Focus.focused @= None
    }
  }

  "An item that expands" should {
    for (style <- List(RichStyle.Tree, RichStyle.Blocks)) {
      s"open on a click on its title's line to its whole text and buttons, and fold on another ($style)" in {
        val list = fx(made(style))
        onScreen(list) {
          val x1 = row(list, "x1")
          val folded = fx(x1.size.height())
          val below = fx(row(list, "p0").position.top())
          fx(x1.semantics().expanded) shouldBe Some(false)
          fx(row(list, "p0").semantics().expanded) shouldBe None
          click(x1, 60.0, 20.0)
          fx(list.expanded()) shouldBe Set("x1")
          settled(list, "x1") shouldBe true
          val open = row(list, "x1")
          fx(open.expansionOf().map(_.text)) shouldBe expansion("x1").map(_.text)
          fx(open.markdown.visible()) shouldBe true
          fx(open.plain.visible()) shouldBe false
          fx(open.markdown.content()) shouldBe expansion("x1").get.text
          fx(open.markdown.size.height()) should be > 40.0
          fx(open.semantics().expanded) shouldBe Some(true)
          // the text under the title's line, starting where the title does and within the row
          fx(open.markdown.absoluteLeft) shouldBe (fx(open.title.absoluteLeft) +- 0.5)
          fx(open.markdown.position.top()) should be >= fx(open.headHeight - 6.0)
          fx(open.markdown.absoluteLeft + open.markdown.size.width()) should be <= fx(open.absoluteLeft + open.size.width())
          // the buttons under it, Pin then Delete in the danger colour, the first's words in line with the text
          val bs = fx(open.buttons())
          bs.map(b => fx(b.label())) shouldBe Vector("Pin", "Delete")
          fx(bs(0).position.top()) should be >= fx(open.markdown.position.top() + open.markdown.size.height())
          fx(bs(0).absoluteLeft) shouldBe (fx(open.markdown.absoluteLeft) - RichRow.buttonLead +- 0.5)
          fx(bs(1).absoluteLeft) should be > fx(bs(0).absoluteLeft + bs(0).size.width())
          fx(bs(1).danger()) shouldBe true
          fx(bs(0).danger()) shouldBe false
          // the row as tall as all of it, measured, and the rows under it moved down by as much
          val tall = fx(open.size.height())
          tall should be > folded + 60.0
          fx(bs(1).position.top() + bs(1).size.height()) should be <= tall
          fx(list.list.heightAt(at(list, "x1"))) shouldBe tall
          fx(row(list, "p0").position.top()) shouldBe (below + tall - folded +- 0.5)
          // a click on its text or a button folds nothing
          click(open, 80.0, fx(open.markdown.position.top() + 10.0))
          fx(list.expanded()) shouldBe Set("x1")
          cursor(list) shouldBe Some("x1")
          click(bs(0))
          ran shouldBe List("pin x1")
          fx(list.expanded()) shouldBe Set("x1")
          click(bs(1))
          ran shouldBe List("pin x1", "delete x1")
          fx(list.expanded()) shouldBe Set("x1")
          // another click on its title's line folds it, as it was
          click(open, 60.0, 20.0)
          fx(list.expanded()) shouldBe Set.empty
          settled(list, "x1") shouldBe true
          fx(row(list, "x1").size.height()) shouldBe folded
          fx(row(list, "p0").position.top()) shouldBe below
          fx(row(list, "x1").semantics().expanded) shouldBe Some(false)
          fx(list.selected()) shouldBe None
        }
      }
      s"open and fold with Enter, fold with Escape, and leave Escape alone when folded ($style)" in {
        val list = fx(made(style))
        var activated: List[String] = Nil
        fx(list.activated.attach(t => activated = activated :+ t))
        onScreen(list) {
          fx(list.list.moveTo(at(list, "x1")))
          press(KeyCode.ENTER)
          fx(list.expanded()) shouldBe Set("x1")
          cursor(list) shouldBe Some("x1")
          press(KeyCode.ENTER)
          fx(list.expanded()) shouldBe Set.empty
          press(KeyCode.ENTER)
          fx(list.expanded()) shouldBe Set("x1")
          fx(list.list.keepsEvent(everyui.event.KeyEvent(everyui.event.Key.Escape, "Escape"))) shouldBe true
          press(KeyCode.ESCAPE)
          fx(list.expanded()) shouldBe Set.empty
          cursor(list) shouldBe Some("x1")
          fx(list.list.keepsEvent(everyui.event.KeyEvent(everyui.event.Key.Escape, "Escape"))) shouldBe false
          // an item without one is activated as ever
          fx(list.list.moveTo(at(list, "p0")))
          press(KeyCode.ENTER)
          activated shouldBe List("p0")
          fx(list.expanded()) shouldBe Set.empty
          activated.size shouldBe 1
        }
      }
      s"keep one open at a time, or several where the app says ($style)" in {
        val list = fx(made(style))
        onScreen(list) {
          click(row(list, "x0"), 60.0, 20.0)
          fx(list.expanded()) shouldBe Set("x0")
          click(row(list, "x1"), 60.0, 20.0)
          fx(list.expanded()) shouldBe Set("x1")
          settled(list, "x1") shouldBe true
          fx(row(list, "x0").expansionOf()) shouldBe None
          fx(list.expandsOne @= false)
          click(row(list, "x0"), 60.0, 20.0)
          fx(list.expanded()) shouldBe Set("x0", "x1")
          settled(list, "x0") shouldBe true
          fx(row(list, "x0").expansionOf().nonEmpty) shouldBe true
          fx(row(list, "x1").expansionOf().nonEmpty) shouldBe true
          fx(list.list.moveTo(at(list, "x0")))
          press(KeyCode.ESCAPE)
          fx(list.expanded()) shouldBe Set("x1")
          // the app's own set
          fx(list.expanded @= Set("x0", "x3"))
          fx(list.list.items().filter(_.expanded).flatMap(_.item)) shouldBe Vector("x0", "x3")
        }
      }
      s"reach its buttons by Right past its action, run one by Enter, and go back by Left ($style)" in {
        val list = fx(made(style))
        var activated: List[String] = Nil
        fx(list.activated.attach(t => activated = activated :+ t))
        onScreen(list) {
          fx(list.list.moveTo(at(list, "x0")))
          press(KeyCode.ENTER)
          fx(list.expanded()) shouldBe Set("x0")
          press(KeyCode.RIGHT)
          fx(list.atAction()) shouldBe true
          press(KeyCode.RIGHT)
          fx(list.atAction()) shouldBe false
          fx(list.atButton()) shouldBe 0
          val x0 = row(list, "x0")
          val bs = fx(x0.buttons())
          fx(bs(0).borderWidth()) shouldBe 1.5
          fx(bs(0).borderColor()) shouldBe Theme.accent()
          fx(x0.borderWidth()) shouldBe 0.0
          if (style == RichStyle.Blocks) fx(x0.light.borderWidth()) shouldBe 0.0
          press(KeyCode.RIGHT)
          fx(list.atButton()) shouldBe 1
          fx(bs(0).borderWidth()) shouldBe 0.0
          fx(bs(1).borderWidth()) shouldBe 1.5
          // the last: Right goes no further
          press(KeyCode.RIGHT)
          fx(list.atButton()) shouldBe 1
          press(KeyCode.ENTER)
          ran shouldBe List("delete x0")
          fx(list.expanded()) shouldBe Set("x0")
          activated shouldBe Nil
          press(KeyCode.LEFT)
          fx(list.atButton()) shouldBe 0
          press(KeyCode.LEFT)
          fx(list.atButton()) shouldBe -1
          fx(list.atAction()) shouldBe true
          press(KeyCode.LEFT)
          fx(list.atAction()) shouldBe false
          // without an action, Right goes straight onto the buttons; Down leaves them
          fx(list.list.moveTo(at(list, "x1")))
          press(KeyCode.ENTER)
          press(KeyCode.RIGHT)
          fx(list.atButton()) shouldBe 0
          press(KeyCode.ENTER)
          ran shouldBe List("delete x0", "pin x1")
          press(KeyCode.DOWN)
          fx(list.atButton()) shouldBe -1
          cursor(list) shouldBe Some("p0")
          // folded, Right is the action's alone
          fx(list.list.moveTo(at(list, "x0")))
          press(KeyCode.RIGHT)
          press(KeyCode.RIGHT)
          fx(list.atButton()) shouldBe -1
        }
      }
      s"be mirrored right to left, its buttons reached by Left ($style)" in {
        val list = fx(made(style))
        onScreen(list) {
          fx(Direction.set(true))
          fx(list.list.moveTo(at(list, "x1")))
          press(KeyCode.ENTER)
          settled(list, "x1") shouldBe true
          val x1 = row(list, "x1")
          fx(x1.markdown.absoluteLeft + x1.markdown.size.width()) shouldBe
            (fx(x1.title.absoluteLeft + x1.title.size.width()) +- 0.5)
          val bs = fx(x1.buttons())
          fx(bs(0).absoluteLeft + bs(0).size.width()) shouldBe
            (fx(x1.markdown.absoluteLeft + x1.markdown.size.width()) + RichRow.buttonLead +- 0.5)
          fx(bs(1).absoluteLeft + bs(1).size.width()) should be < fx(bs(0).absoluteLeft)
          press(KeyCode.LEFT)
          fx(list.atButton()) shouldBe 0
          press(KeyCode.RIGHT)
          fx(list.atButton()) shouldBe -1
        }
      }
    }
    "show plain words wrapped where the expansion is not Markdown, within the row" in {
      val list = fx(made(RichStyle.Blocks))
      onScreen(list) {
        fx(list.expanded @= Set("x3"))
        settled(list, "x3") shouldBe true
        val x3 = row(list, "x3")
        fx(x3.plain.visible()) shouldBe true
        fx(x3.markdown.visible()) shouldBe false
        fx(x3.plain.content()) shouldBe "Plain words of x3, **not** Markdown"
        fx(x3.plain.absoluteLeft + x3.plain.size.width()) should be <= fx(x3.absoluteLeft + x3.size.width())
      }
    }
    "be kept in view as it grows: its bottom shown, or its top where it is taller than the list" in {
      for (style <- List(RichStyle.Tree, RichStyle.Blocks)) {
        val list = fx(made(style))
        onScreen(list) {
          // x3, the last but one row and the last in view: opened, it is scrolled the least that shows it whole
          val i = at(list, "x3")
          val view = fx(list.list.top(i) + list.list.heightAt(i) + 4.0)
          fx(list.size.height @= view)
          val before = fx(list.list.offset())
          fx(list.list.moveTo(i))
          press(KeyCode.ENTER)
          settled(list, "x3") shouldBe true
          eventually {
            val j = at(list, "x3")
            list.list.top(j) + list.list.heightAt(j) <= list.list.offset() + view + 0.5
          } shouldBe true
          fx(list.list.offset()) should be > before
          fx(list.list.top(at(list, "x3"))) should be >= fx(list.list.offset())
          // x2's long text, taller than the list: its top at the list's top
          fx(list.list.moveTo(at(list, "x2")))
          press(KeyCode.ENTER)
          settled(list, "x2") shouldBe true
          fx(list.expanded()) shouldBe Set("x2")
          eventually(list.list.heightAt(at(list, "x2")) > view && list.list.offset() == list.list.top(at(list, "x2"))) shouldBe true
          // the text is never cut: the row is as tall as all of it
          val x2 = row(list, "x2")
          fx(x2.markdown.size.height()) should be > view
          fx(x2.size.height()) should be >= fx(x2.markdown.position.top() + x2.markdown.size.height())
          // folded from below its top, it is shown again
          fx(list.list.scrollTop(list.list.offset() + 200.0))
          press(KeyCode.ESCAPE)
          settled(list, "x2") shouldBe true
          eventually(list.list.offset() <= list.list.top(at(list, "x2"))) shouldBe true
        }
      }
    }
    "fade its text in as it opens, and show it at once where glides jump" in {
      val list = fx(made(RichStyle.Blocks))
      // the frame loop driven here, a frame at a time
      var asked = Vector.empty[() => Unit]
      def frames(n: Int): Unit = (1 to n).foreach { _ =>
        fx {
          val now = asked
          asked = Vector.empty
          now.foreach(f => f())
        }
      }
      fx(Frames.drive(_ => ui.update(1.0 / 60.0), () => ui.needsFrames, () => (), f => asked = asked :+ f))
      try onScreen(list) {
        fx(list.list.moveTo(at(list, "x1")))
        press(KeyCode.ENTER)
        val x1 = row(list, "x1")
        fx(x1.markdown.parent().get.opacity()) shouldBe 0.0
        // under way, and then whole
        frames(4)
        fx(x1.markdown.parent().get.opacity()) should (be > 0.0 and be < 1.0)
        frames(30)
        fx(x1.markdown.parent().get.opacity()) shouldBe 1.0
        // scrolled into view expanded, or opened at Minimal, it is simply there
        press(KeyCode.ENTER)
        fx(Quality.start(Some(QualityLevel.Minimal)))
        try {
          press(KeyCode.ENTER)
          fx(row(list, "x1").markdown.parent().get.opacity()) shouldBe 1.0
        }
        finally fx(Quality.forget())
      }
      finally fx(Frames.drive(_ => (), () => false, () => (), _ => ()))
    }
  }

  "Search and grouping" should {
    "be as they were, an expanded item found by its title and detail as ever" in {
      val list = fx(made(RichStyle.Blocks))
      onScreen(list) {
        val plainLines = lines(list)
        fx(list.expanded @= Set("x1"))
        lines(list) shouldBe plainLines
        fx(list.list.items().map(_.copy(key = "", expanded = false))) shouldBe
          fx(made(RichStyle.Blocks, expands = false).list.items().map(_.copy(key = "")))
        fx(list.query @= "x1")
        lines(list) shouldBe List("#a", "x1")
        fx(list.list.items().find(_.item.contains("x1")).exists(_.expanded)) shouldBe true
        // words only in an item's text are not found unless the app's `matches` says so
        fx(list.query @= "Paragraph 7")
        lines(list) shouldBe Nil
        fx(list.matches @= ((s: String, w: String) => RichList.holds(look(s), w) ||
          expansion(s).exists(_.text.toLowerCase.contains(w.toLowerCase))))
        lines(list) shouldBe List("#a", "x2")
        fx(list.query @= "")
        lines(list) shouldBe plainLines
      }
    }
  }

  "A list without an expansion" should {
    "make its rows as it did, activating its items" in {
      for (style <- List(RichStyle.Tree, RichStyle.Blocks)) {
        val list = fx(made(style, expands = false))
        var activated: List[String] = Nil
        fx(list.activated.attach(t => activated = activated :+ t))
        onScreen(list) {
          val x1 = row(list, "x1")
          fx(x1.children().size) shouldBe (if (style == RichStyle.Tree) 12 else 17)
          fx(x1.semantics().expanded) shouldBe None
          fx(list.list.moveTo(at(list, "x1")))
          press(KeyCode.ENTER)
          activated shouldBe List("x1")
          fx(list.expanded()) shouldBe Set.empty
          fx(x1.size.height()) shouldBe RichRow.tallHeight
          press(KeyCode.ESCAPE)
          fx(list.expanded()) shouldBe Set.empty
        }
      }
    }
  }
}
