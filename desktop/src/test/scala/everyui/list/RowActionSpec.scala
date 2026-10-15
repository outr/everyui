package everyui.list

import everyui.component.Component
import everyui.event.Pointer
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.menu.MenuItem
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** An item's action (`RichLook.action`), a star: a small button at its row's end before the time, shown with the
  * cursor (or the pointer) on the row, or always where `actionShown`, the title cut short of its room whether it shows
  * or not; a click or a tap running it without choosing or activating the item, Right onto it, Enter running it and
  * Left back, in a tree and in blocks, mirrored right to left. Rows without one are made as they were. */
class RowActionSpec extends FxSpec {
  private val width = 320.0
  private var ran: List[String] = Nil
  private val now = everyui.util.Clock.real()

  // the items starting "s" have a star, "s1" starred; "l" has a long title and a star
  private def look(s: String): RichLook = RichLook(if (s == "l") "A title far too long to fit on one line of this list" else s,
    detail = Some("detail of " + s), time = Some(now - 5L * 60000L), count = if (s == "s0") 3 else 0,
    action = if (s.startsWith("s") || s == "l") Some(RichAction(if (s == "s1") MaterialIcon.Star else MaterialIcon.StarBorder,
      "Star " + s, () => ran = ran :+ s)) else None,
    actionShown = s == "s1")

  private def sections(): Var[Vector[RichSection[String]]] = Var(Vector(
    RichSection("a", "Alpha", Vector("s0", "s1", "p0", "l")),
    RichSection("b", "Beta", Vector("p1"))))

  private def made(style: RichStyle, plain: Boolean = false): RichList[String] = {
    val list = new RichList[String](if (plain) Var(Vector(RichSection("a", "Alpha", Vector("p0", "p1")))) else sections(),
      s => s, look)
    list.style @= style
    list.size.width @= width
    list.size.height @= 1000.0
    list
  }

  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def lines(list: RichList[String]): List[String] = onFx((0 until list.list.count).map(list.list(_)).map(l =>
    l.item.getOrElse("#" + l.section.key)).toList)

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

  private def click(on: Component, along: Double = -1.0): Unit = fx {
    val x = on.absoluteLeft + (if (along < 0.0) on.size.width() / 2.0 else along)
    val y = on.absoluteTop + on.size.height() / 2.0
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y, MouseButton.PRIMARY,
      1, false, false, false, false, true, false, false, false, false, true, null))
  }

  private def glyph(r: RichRow[String]): (Double, Double) = onFx {
    val g = r.actionButton.icon
    (g.absoluteLeft, g.absoluteLeft + g.size.width())
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

  "An item's action" should {
    for (style <- List(RichStyle.Tree, RichStyle.Blocks)) {
      s"sit before the time on the title's line, the title short of it whether it shows or not ($style)" in {
        val list = fx(made(style))
        onScreen(list) {
          fx(list.list.moveTo(at(list, "#a")))
          val l = row(list, "l")
          // at rest it does not show, but its room is kept
          fx(l.actionButton.visible()) shouldBe false
          val cut = fx(l.title.size.width())
          fx(list.list.moveTo(at(list, "l")))
          fx(l.actionButton.visible()) shouldBe true
          fx(l.title.size.width()) shouldBe cut
          glyph(l)._2 shouldBe (fx(l.time.absoluteLeft) - 6.0 +- 0.5)
          fx(l.title.absoluteLeft + l.title.size.width()) should be <= glyph(l)._1
          fx(l.actionButton.absoluteTop + l.actionButton.size.height() / 2.0) shouldBe
            (fx(l.title.absoluteTop + l.title.size.height() / 2.0) +- 0.5)
          fx(l.actionButton.tooltip()) shouldBe "Star l"
          fx(l.actionButton.semantics().role) shouldBe "button"
          fx(l.actionButton.semantics().label) shouldBe "Star l"
          // with a menu, the ⋮ takes the time's place and the star keeps its own
          val before = glyph(l)
          fx(list.menu @= ((_: String) => List(MenuItem("Rename", () => ()))))
          fx(l.more.visible()) shouldBe true
          fx(l.time.visible()) shouldBe false
          glyph(l)._2 should be <= fx(l.more.icon.absoluteLeft)
          fx(list.list.moveTo(at(list, "s0")))
          fx(list.list.moveTo(at(list, "l")))
          // (the room kept is the wider of the ⋮ and the time)
          val side = if (style == RichStyle.Blocks) RichRow.blockMargin + RichRow.blockPad else RichRow.inset
          glyph(l)._2 shouldBe (fx(l.absoluteLeft + l.size.width() - side - math.max(l.time.size.width(), l.more.size.width() - 11.0)) -
            6.0 +- 0.5)
          glyph(l)._2 should be <= before._2
          fx(l.title.size.width()) should be <= cut
        }
      }
      s"show with the cursor on its row, or always where actionShown ($style)" in {
        val list = fx(made(style))
        onScreen(list) {
          fx(list.list.moveTo(at(list, "#a")))
          fx(row(list, "s0").actionButton.visible()) shouldBe false
          fx(row(list, "s1").actionButton.visible()) shouldBe true
          fx(row(list, "s1").actionButton.icon.icon()) shouldBe MaterialIcon.Star
          fx(list.list.moveTo(at(list, "s0")))
          fx(row(list, "s0").actionButton.visible()) shouldBe true
          fx(row(list, "s0").actionButton.icon.icon()) shouldBe MaterialIcon.StarBorder
        }
      }
      s"run on a click or a tap without choosing or activating the item ($style)" in {
        for (touch <- List(false, true)) {
          // (a click at the same pixel within Pointer.hold of one consumed is taken for it)
          Thread.sleep(Pointer.hold.toLong * 2L)
          val list = fx(made(style))
          var activated: List[String] = Nil
          fx {
            list.list.clickActivates @= Some(touch)
            list.activated.attach(t => activated = activated :+ t)
          }
          onScreen(list) {
            fx(list.list.moveTo(at(list, "#a")))
            click(row(list, "s1").actionButton)
            ran shouldBe List("s1")
            fx(list.selected()) shouldBe None
            activated shouldBe Nil
            cursor(list) shouldBe Some("#a")
            // the list's own click at its place, as where running it outlasts the click's climb, chooses nothing
            val s1 = row(list, "s1")
            click(s1, fx(s1.actionButton.position.left() + s1.actionButton.size.width() / 2.0))
            fx(list.selected()) shouldBe None
            cursor(list) shouldBe Some("#a")
            // the row beside it still is chosen
            click(s1, 40.0)
            cursor(list) shouldBe Some("s1")
            if (touch) activated shouldBe List("s1")
            ran shouldBe List("s1")
          }
        }
      }
      s"be reached by Right from its row and run by Enter, Left going back ($style)" in {
        val list = fx(made(style))
        var activated: List[String] = Nil
        fx(list.activated.attach(t => activated = activated :+ t))
        onScreen(list) {
          fx(list.list.moveTo(at(list, "s0")))
          press(KeyCode.RIGHT)
          fx(list.atAction()) shouldBe true
          val s0 = row(list, "s0")
          fx(s0.actionButton.borderWidth()) shouldBe 1.0
          fx(s0.actionButton.borderColor()) shouldBe Theme.accent()
          fx(s0.actionButton.backgroundColor()) shouldBe Theme.controlLit()
          fx(s0.borderWidth()) shouldBe 0.0
          if (style == RichStyle.Blocks) fx(s0.light.borderWidth()) shouldBe 0.0
          press(KeyCode.ENTER)
          ran shouldBe List("s0")
          activated shouldBe Nil
          fx(list.selected()) shouldBe None
          press(KeyCode.LEFT)
          fx(list.atAction()) shouldBe false
          press(KeyCode.ENTER)
          activated shouldBe List("s0")
          ran shouldBe List("s0")
          // Down leaves it; a row without one is as it was
          press(KeyCode.RIGHT)
          press(KeyCode.DOWN)
          fx(list.atAction()) shouldBe false
          cursor(list) shouldBe Some("s1")
          fx(list.list.moveTo(at(list, "p0")))
          press(KeyCode.RIGHT)
          fx(list.atAction()) shouldBe false
        }
      }
      s"be mirrored right to left, reached by Left ($style)" in {
        val list = fx(made(style))
        onScreen(list) {
          fx(Direction.set(true))
          val s1 = row(list, "s1")
          glyph(s1)._1 shouldBe (fx(s1.time.absoluteLeft + s1.time.size.width()) + 6.0 +- 0.5)
          fx(list.list.moveTo(at(list, "s1")))
          press(KeyCode.LEFT)
          fx(list.atAction()) shouldBe true
          press(KeyCode.ENTER)
          ran shouldBe List("s1")
          press(KeyCode.RIGHT)
          fx(list.atAction()) shouldBe false
        }
      }
    }
  }

  "A row without an action" should {
    "be made as it was, beside rows with one or not" in {
      for (style <- List(RichStyle.Tree, RichStyle.Blocks)) {
        val plain = fx(made(style, plain = true))
        val (kids, cut, timeAt) = onScreen(plain) {
          val p = row(plain, "p0")
          fx((p.children().size, p.title.maxWidth(), p.time.absoluteLeft))
        }
        kids shouldBe (if (style == RichStyle.Tree) 12 else 17)
        val list = fx(made(style))
        onScreen(list) {
          val p = row(list, "p0")
          fx(p.children().size) shouldBe kids
          fx(p.title.maxWidth()) shouldBe cut
          fx(p.time.absoluteLeft) shouldBe timeAt
        }
      }
    }
  }
}
