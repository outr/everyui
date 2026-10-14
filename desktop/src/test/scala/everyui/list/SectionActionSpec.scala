package everyui.list

import everyui.component.{Component, TextView}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** A section's action (`RichSection.action`), a small button in its header: before the count in a block, at the end in
  * a tree (short of the ⋮), its label its tooltip and its name, a click or a tap running it without opening or closing
  * the section, Right from the open header onto it, Enter running it and Left back. And an empty section's line
  * (`RichSection.empty`): shown while it is open and holds nothing, inside its block, muted, never chosen, passed over
  * by the cursor and never found by search. A list with neither is drawn as it was. */
class SectionActionSpec extends FxSpec {
  private val side = RichRow.blockMargin + RichRow.blockPad
  private val width = 320.0
  private var ran: List[String] = Nil

  private def act(key: String): Option[RichAction] =
    Some(RichAction(MaterialIcon.Add, "New in " + key, () => ran = ran :+ key))

  private def sections(actions: Boolean = true): Var[Vector[RichSection[String]]] = Var(Vector(
    RichSection("a", "Alpha", Vector("a0", "a1"), action = if (actions) act("a") else None),
    RichSection("b", "Beta", Vector("b0"), count = Some(7)),
    RichSection("e", "Empty", Vector.empty, action = if (actions) act("e") else None, empty = Some("Nothing here yet")),
    RichSection("q", "Quiet", Vector.empty),
    RichSection("z", "Zeta", Vector("z0"))))

  private def made(style: RichStyle, collapsed: Var[Set[String]] = Var(Set.empty[String]), search: Boolean = false,
                   actions: Boolean = true): RichList[String] = {
    val list = new RichList[String](sections(actions), s => s, s => RichLook(s), collapsed, search)
    list.style @= style
    list.size.width @= width
    list.size.height @= 1000.0
    list
  }

  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def lines(list: RichList[String]): List[String] = onFx((0 until list.list.count).map(list.list(_)).map(l =>
    if (l.empty) "-" + l.section.key else l.item.getOrElse("#" + l.section.key)).toList)

  private def at(list: RichList[String], line: String): Int = lines(list).indexOf(line)

  private def row(list: RichList[String], line: String): RichRow[String] =
    onFx(list.list.rowAt(at(list, line)).get.asInstanceOf[RichRow[String]])

  private def cursor(list: RichList[String]): Option[String] = fx(list.list.currentItem.map(l =>
    if (l.empty) "-" + l.section.key else l.item.getOrElse("#" + l.section.key)))

  private def key(code: KeyCode, down: Boolean): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(if (down) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED, "", "",
      code, false, false, false, false))
  }

  private def press(code: KeyCode): Unit = {
    key(code, down = true)
    key(code, down = false)
  }

  // a click on `on`, `along` from its left (its middle, where not given)
  private def click(on: Component, along: Double = -1.0): Unit = fx {
    val x = on.absoluteLeft + (if (along < 0.0) on.size.width() / 2.0 else along)
    val y = on.absoluteTop + on.size.height() / 2.0
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y, MouseButton.PRIMARY,
      1, false, false, false, false, true, false, false, false, false, true, null))
  }

  private def all(c: Component): List[Component] = c :: (c match {
    case k: everyui.component.Container => k.children().toList.flatMap(all)
    case _ => Nil
  })

  private def shown(c: Component, upTo: Component): Boolean = c.visible() && c.parent().forall(p => (p eq upTo) ||
    shown(p, upTo))

  // where the action's glyph starts and ends, from the list's left
  private def glyph(r: RichRow[String]): (Double, Double) = onFx {
    val g = r.actionButton.icon
    (g.absoluteLeft, g.absoluteLeft + g.size.width())
  }

  // the words of row `r`'s header shown, apart from its count: its title
  private def titleOf(r: RichRow[String], start: String): TextView = onFx(all(r).collect {
    case t: TextView if shown(t, r) && t.content().startsWith(start) => t
  }.head)

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

  "A section's action" should {
    "sit just before the count in a block, named by its label, the title cut short of it" in {
      val list = fx(made(RichStyle.Blocks))
      onScreen(list) {
        val alpha = row(list, "#a")
        fx(alpha.actionButton.visible()) shouldBe true
        fx(alpha.actionButton.icon.icon()) shouldBe MaterialIcon.Add
        fx(alpha.actionButton.tooltip()) shouldBe "New in a"
        fx(alpha.actionButton.semantics().role) shouldBe "button"
        fx(alpha.actionButton.semantics().label) shouldBe "New in a"
        // the count where it always is, the action's glyph 6 before it, the title short of the glyph
        val countAt = fx(alpha.count.absoluteLeft)
        fx(countAt + alpha.count.size.width()) shouldBe (width - side - RichRow.toggleSize - 6.0 +- 0.5)
        glyph(alpha)._2 shouldBe (countAt - 6.0 +- 0.5)
        fx(titleOf(alpha, "Alpha").absoluteLeft + titleOf(alpha, "Alpha").size.width()) should be <= glyph(alpha)._1
        // in the middle of the bar
        fx(alpha.actionButton.absoluteTop + alpha.actionButton.size.height() / 2.0) shouldBe
          (fx(alpha.absoluteTop) + RichRow.blockHeader / 2.0 +- 0.5)
        // a section with none has no action, its count where it would be
        val beta = row(list, "#b")
        fx(beta.count.absoluteLeft + beta.count.size.width()) shouldBe (width - side - RichRow.toggleSize - 6.0 +- 0.5)
        fx(all(beta).contains(beta.actionButton)) shouldBe false
      }
    }
    "sit at the end of a tree's header, short of the ⋮, every count in one line before it" in {
      val list = fx(made(RichStyle.Tree))
      onScreen(list) {
        val alpha = row(list, "#a")
        glyph(alpha)._2 shouldBe (width - RichRow.inset +- 0.5)
        val badgeEnd = fx {
          val b = all(alpha).collect { case t: TextView if shown(t, alpha) && t.content() == "2" => t }.head
          b.absoluteLeft + b.size.width()
        }
        badgeEnd should be <= glyph(alpha)._1
        // Beta, with no action, has its count in the same line as Alpha's
        val beta = row(list, "#b")
        fx {
          val b = all(beta).collect { case t: TextView if shown(t, beta) && t.content() == "7" => t }.head
          b.absoluteLeft + b.size.width()
        } shouldBe (badgeEnd +- 0.5)
        // with a ⋮ in the headers, the action moves in short of it
        fx(list.sectionMenu @= ((_: RichSection[String]) => List(everyui.menu.MenuItem("Rename", () => ()))))
        glyph(alpha)._2 should be < fx(alpha.sectionMore.absoluteLeft + 3.0)
        glyph(alpha)._2 shouldBe (width - RichRow.inset - (RichRow.headerMore - 2.0) +- 0.5)
        fx(Direction.set(true))
        glyph(alpha)._1 shouldBe (RichRow.inset + (RichRow.headerMore - 2.0) +- 0.5)
      }
    }
    "run on a click or a tap without opening or closing the section, in a block and in a tree" in {
      for (style <- List(RichStyle.Blocks, RichStyle.Tree); touch <- List(false, true)) {
        // (a click at the same pixel within Pointer.hold of one consumed is taken for it)
        Thread.sleep(everyui.event.Pointer.hold.toLong * 2L)
        val collapsed = Var(Set("e"))
        val list = fx(made(style, collapsed))
        fx(list.list.clickActivates @= touch)
        onScreen(list) {
          fx(list.list.moveTo(at(list, "a1")))
          click(row(list, "#a").actionButton)
          ran shouldBe List("a")
          collapsed() shouldBe Set("e")
          lines(list).take(3) shouldBe List("#a", "a0", "a1")
          cursor(list) shouldBe Some("a1")
          // on a closed section, too
          click(row(list, "#e").actionButton)
          ran shouldBe List("a", "e")
          collapsed() shouldBe Set("e")
          // the click the list hears at the action's place, as where running it outlasts the click's climb, folds nothing
          val alpha = row(list, "#a")
          click(alpha, fx(alpha.actionButton.position.left() + alpha.actionButton.size.width() / 2.0))
          collapsed() shouldBe Set("e")
          // the bar beside it still opens and closes it
          click(row(list, "#a"), 40.0)
          collapsed() shouldBe Set("e", "a")
          ran shouldBe List("a", "e")
        }
      }
    }
    "be reached by Right from its open header and run by Enter, Left going back to the header" in {
      for (style <- List(RichStyle.Blocks, RichStyle.Tree)) {
        val collapsed = Var(Set("a"))
        val list = fx(made(style, collapsed))
        onScreen(list) {
          fx(list.list.moveTo(at(list, "#a")))
          // closed, Right opens it; open, Right goes onto its action
          press(KeyCode.RIGHT)
          collapsed() shouldBe Set.empty
          fx(list.atAction()) shouldBe false
          press(KeyCode.RIGHT)
          fx(list.atAction()) shouldBe true
          val alpha = row(list, "#a")
          fx(alpha.actionButton.borderWidth()) shouldBe 1.0
          fx(alpha.actionButton.borderColor()) shouldBe Theme.accent()
          fx(alpha.actionButton.backgroundColor()) shouldBe Theme.controlLit()
          // one ring: the action's, not the row's
          fx(alpha.borderWidth()) shouldBe 0.0
          if (style == RichStyle.Blocks) fx(alpha.light.borderWidth()) shouldBe 0.0
          // Enter runs it and folds nothing
          press(KeyCode.ENTER)
          ran shouldBe List("a")
          collapsed() shouldBe Set.empty
          cursor(list) shouldBe Some("#a")
          // Left back to the header, and Left again closes it
          press(KeyCode.LEFT)
          fx(list.atAction()) shouldBe false
          fx(alpha.actionButton.borderWidth()) shouldBe 0.0
          collapsed() shouldBe Set.empty
          press(KeyCode.ENTER)
          collapsed() shouldBe Set("a")
          ran shouldBe List("a")
          // moving off the header leaves the action
          press(KeyCode.RIGHT)
          press(KeyCode.RIGHT)
          fx(list.atAction()) shouldBe true
          press(KeyCode.DOWN)
          fx(list.atAction()) shouldBe false
          cursor(list) shouldBe Some("a0")
          // a header with none: Right on it open does nothing, Enter closes it
          fx(list.list.moveTo(at(list, "#b")))
          press(KeyCode.RIGHT)
          fx(list.atAction()) shouldBe false
          press(KeyCode.ENTER)
          collapsed() shouldBe Set("b")
          ran shouldBe List("a")
        }
      }
    }
    "be reached by Left, right to left" in {
      val list = fx(made(RichStyle.Blocks))
      onScreen(list) {
        fx(Direction.set(true))
        val alpha = row(list, "#a")
        glyph(alpha)._1 shouldBe (fx(alpha.count.absoluteLeft + alpha.count.size.width()) + 6.0 +- 0.5)
        fx(list.list.moveTo(at(list, "#a")))
        press(KeyCode.LEFT)
        fx(list.atAction()) shouldBe true
        press(KeyCode.ENTER)
        ran shouldBe List("a")
        press(KeyCode.RIGHT)
        fx(list.atAction()) shouldBe false
        fx(list.collapsed()) shouldBe Set.empty
      }
    }
  }

  "An empty section" should {
    "show its words on a line of their own while open, inside its block, muted" in {
      val collapsed = Var(Set.empty[String])
      val list = fx(made(RichStyle.Blocks, collapsed))
      onScreen(list) {
        lines(list) shouldBe List("#a", "a0", "a1", "#b", "b0", "#e", "-e", "#q", "#z", "z0")
        val empty = row(list, "-e")
        fx(empty.emptyText.visible()) shouldBe true
        fx(empty.emptyText.content()) shouldBe "Nothing here yet"
        fx(empty.emptyText.color()) shouldBe Theme.muted()
        fx(empty.emptyText.absoluteLeft) shouldBe (side +- 0.5)
        fx(empty.size.height()) shouldBe RichRow.emptyHeight + RichRow.blockPad + RichRow.blockGap
        // the block's last part: its header goes on into it
        fx(row(list, "#e").size.height()) shouldBe RichRow.blockHeader
        fx(empty.holder.visible()) shouldBe true
        fx(empty.panel.position.top()) shouldBe -RichRow.blockRadius
        fx(empty.semantics().label) shouldBe "Nothing here yet"
        // one with no words for it is its header alone, as it was
        fx(row(list, "#q").size.height()) shouldBe RichRow.blockHeader + RichRow.blockGap
        fx(all(row(list, "#q")).contains(row(list, "#q").emptyText)) shouldBe false
        // closed, it shows none
        fx(collapsed @= Set("e"))
        lines(list) shouldBe List("#a", "a0", "a1", "#b", "b0", "#e", "#q", "#z", "z0")
        fx(row(list, "#e").size.height()) shouldBe RichRow.blockHeader + RichRow.blockGap
      }
    }
    "show it in a tree too" in {
      val list = fx(made(RichStyle.Tree))
      onScreen(list) {
        val empty = row(list, "-e")
        fx(empty.emptyText.content()) shouldBe "Nothing here yet"
        fx(empty.emptyText.absoluteLeft) shouldBe (RichRow.inset +- 0.5)
        fx(empty.size.height()) shouldBe RichRow.emptyHeight
      }
    }
    "never be chosen, lit or activated, and the cursor passes over it" in {
      val list = fx(made(RichStyle.Blocks))
      var activated: List[String] = Nil
      fx(list.activated.attach(t => activated = activated :+ t))
      onScreen(list) {
        fx(list.list.moveTo(at(list, "#e")))
        press(KeyCode.DOWN)
        cursor(list) shouldBe Some("#q")
        press(KeyCode.UP)
        cursor(list) shouldBe Some("#e")
        // a click on it does nothing: the cursor stays, nothing is chosen
        click(row(list, "-e"), 40.0)
        cursor(list) shouldBe Some("#e")
        fx(list.selected()) shouldBe None
        activated shouldBe Nil
        fx(list.collapsed()) shouldBe Set.empty
        // nor is it lit
        fx(row(list, "-e").light.backgroundColor()) shouldBe everyui.Color.Clear
        // the cursor sent onto it goes on the way it was going, or back where there is nothing past it
        fx(list.list.moveTo(at(list, "-e")))
        cursor(list) shouldBe Some("#q")
        fx(list.list.moveTo(at(list, "#q")))
        fx(list.list.moveTo(at(list, "-e")))
        cursor(list) shouldBe Some("#e")
        fx(list.sections @= Vector(RichSection("only", "", Vector.empty[String], empty = Some("None"))))
        lines(list) shouldBe List("-only")
        fx(list.list.moveTo(0))
        cursor(list) shouldBe Some("-only")
      }
    }
    "take a drop onto its line into it" in {
      val list = fx(made(RichStyle.Blocks))
      var moves: List[RichMove] = Nil
      fx {
        list.moved.attach(m => moves = moves :+ m)
        list.reorders @= true
        list.accepts @= ((_: RichMove) => true)
      }
      onScreen(list) {
        fx(list.list.dropAt(20.0, row(list, "-e").absoluteTop + 4.0, Dragged(list.list, List(GroupedList.itemKey("a", "a0")))))
        moves shouldBe List(RichMove(List("a0"), "a", "e", None))
      }
    }
    "be hidden by search, its words never found" in {
      val list = fx(made(RichStyle.Blocks, search = true))
      onScreen(list) {
        fx(list.query @= "a")
        lines(list) shouldBe List("#a", "a0", "a1")
        fx(list.query @= "nothing")
        lines(list) shouldBe Nil
        fx(list.query @= "")
        lines(list) should contain("-e")
      }
    }
  }

  "A list with no actions or empty words" should {
    "make its rows as it did" in {
      for (style <- List(RichStyle.Tree, RichStyle.Blocks)) {
        val list = fx(made(style, actions = false))
        onScreen(list) {
          fx(list.headersHaveActions()) shouldBe false
          fx(row(list, "#a").children().size) shouldBe (if (style == RichStyle.Tree) 12 else 17)
          fx(row(list, "a0").children().size) shouldBe (if (style == RichStyle.Tree) 12 else 17)
        }
      }
    }
  }
}
