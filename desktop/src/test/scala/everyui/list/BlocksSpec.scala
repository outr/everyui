package everyui.list

import everyui.Color
import everyui.chat.{Avatar, Participant}
import everyui.component.{Component, MaterialIconView, TextView}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** A rich list drawn as blocks (`RichStyle.Blocks`): a group's title a caption, each section a block whose rows draw
  * their own part of its panel (the first's top corners round, the last's bottom ones, a gap after it), nothing set in,
  * a collapsed block its header bar with its count, the bar opening and closing it with a click, Enter, Left and Right,
  * search opening blocks, the item chosen lit inside its block, drags between blocks, right to left; and the tree,
  * the default, drawn as it was, before blocks and after them. */
class BlocksSpec extends FxSpec {
  private val work = RichGroup("work", "Work", Some(MaterialIcon.Work))
  private val personal = RichGroup("personal", "Personal")
  private val side = RichRow.blockMargin + RichRow.blockPad
  private val width = 320.0

  private def sections: Var[Vector[RichSection[String]]] = Var(Vector(
    RichSection("inbox", "Inbox", Vector("i0", "i1")),
    RichSection("a", "Alpha", Vector("a0", "a1"), group = Some(work)),
    RichSection("b", "Beta", Vector("b0", "b1", "b2"), count = Some(7), group = Some(work)),
    RichSection("me", "Me", Vector("m0"), group = Some(personal)),
    RichSection("work", "Work section", Vector("w0"))))

  private def made(collapsed: Var[Set[String]] = Var(Set.empty[String]), search: Boolean = false,
                   style: RichStyle = RichStyle.Blocks): RichList[String] = {
    val list = new RichList[String](sections, s => s, s => RichLook(s, who = Some(Participant(s, s))), collapsed, search)
    list.style @= style
    list.size.width @= width
    list.size.height @= 1000.0
    list
  }

  // `f` on the FX thread, where this is not already on it
  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def lines(list: RichList[String]): List[String] = onFx((0 until list.list.count).map(list.list(_)).map(l =>
    l.groupHead.map("^" + _.key).orElse(l.item).getOrElse("#" + l.section.key)).toList)

  private def at(list: RichList[String], line: String): Int = lines(list).indexOf(line)

  private def row(list: RichList[String], line: String): RichRow[String] =
    onFx(list.list.rowAt(at(list, line)).get.asInstanceOf[RichRow[String]])

  private def key(code: KeyCode, down: Boolean): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(if (down) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED, "", "",
      code, false, false, false, false))
  }

  private def press(code: KeyCode): Unit = {
    key(code, down = true)
    key(code, down = false)
  }

  // a click on `on`, `along` from its left
  private def click(on: Component, along: Double): Unit = fx {
    val x = on.absoluteLeft + along
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

  private def face(r: RichRow[String]): Avatar = onFx(all(r).collect { case a: Avatar if shown(a, r) => a }.head)

  // the words shown in row `r` that are all digits: a header's count
  private def count(r: RichRow[String]): List[String] = onFx(all(r).collect {
    case t: TextView if shown(t, r) && t.content().nonEmpty && t.content().forall(_.isDigit) => t.content()
  })

  // the tree's chevrons shown in row `r` (18 across), apart from a block's ▾ or ▸
  private def chevrons(r: RichRow[String]): Int = onFx(all(r).count {
    case v: MaterialIconView => shown(v, r) && v.size.width() == 18.0
    case _ => false
  })

  private def onScreen[T](list: RichList[String])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += list
      list.focus()
    }
    try test finally fx {
      Direction.set(false)
      ui.children.clear()
      // taken apart, so its clock (Ticker) stops: left running, it moved on every twenty seconds for the rest of the run,
      // and woke the frame loop of the specs after
      list.dispose()
      Focus.focused @= None
    }
  }

  "A rich list drawn as blocks" should {
    "put each group's title in a caption over its blocks, and nothing set in" in {
      val list = fx(made())
      onScreen(list) {
        lines(list) shouldBe List("#inbox", "i0", "i1", "^work", "#a", "a0", "a1", "#b", "b0", "b1", "b2",
          "^personal", "#me", "m0", "#work", "w0")
        val caption = row(list, "^work")
        fx(caption.size.height()) shouldBe RichRow.captionHeight
        fx(caption.caption.visible()) shouldBe true
        fx(caption.caption.content()) shouldBe "WORK"
        fx(caption.caption.absoluteLeft) shouldBe (side +- 0.5)
        fx(caption.holder.visible()) shouldBe false
        fx(caption.toggle.visible()) shouldBe false
        chevrons(caption) shouldBe 0
        count(caption) shouldBe Nil
        // a section's header is a bar across the top of its block, its ▾ at the end and no chevron before it
        val alpha = row(list, "#a")
        fx(alpha.size.height()) shouldBe RichRow.blockHeader
        chevrons(alpha) shouldBe 0
        fx(alpha.toggle.visible()) shouldBe true
        fx(alpha.toggle.icon()) shouldBe MaterialIcon.ExpandMore
        fx(alpha.toggle.absoluteLeft + alpha.toggle.size.width()) shouldBe (width - side +- 0.5)
        count(alpha) shouldBe List("2")
        // the items of a section in a group start where those of one in none do
        fx(face(row(list, "a0")).absoluteLeft) shouldBe (side +- 0.5)
        fx(face(row(list, "i0")).absoluteLeft) shouldBe (side +- 0.5)
        fx(row(list, "a0").size.height()) shouldBe RichRow.shortHeight
      }
    }
    "draw each block's panel a row at a part, round only at the block's own corners, with a gap after it" in {
      val list = fx(made())
      onScreen(list) {
        val r = RichRow.blockRadius
        val head = row(list, "#a")
        val middle = row(list, "a0")
        val last = row(list, "a1")
        fx(head.holder.absoluteLeft) shouldBe (RichRow.blockMargin +- 0.5)
        fx(head.holder.size.width()) shouldBe (width - 2.0 * RichRow.blockMargin)
        fx(head.panel.radius()) shouldBe r
        // the first: its top at the row's, reaching a corner past its bottom
        fx(head.panel.position.top()) shouldBe 0.0
        fx(head.panel.size.height()) shouldBe RichRow.blockHeader + r
        fx(head.holder.size.height()) shouldBe RichRow.blockHeader
        // one in the middle: reaching a corner past each end, so it is cut square
        fx(middle.panel.position.top()) shouldBe -r
        fx(middle.panel.size.height()) shouldBe RichRow.shortHeight + 2.0 * r
        // the last: the block's padding under it, then the gap, which the panel stops short of
        fx(last.size.height()) shouldBe RichRow.shortHeight + RichRow.blockPad + RichRow.blockGap
        fx(last.holder.size.height()) shouldBe RichRow.shortHeight + RichRow.blockPad
        fx(last.panel.position.top()) shouldBe -r
        fx(last.panel.size.height()) shouldBe RichRow.shortHeight + RichRow.blockPad + r
        // the item's line where it would be without the padding under it
        fx(face(last).position.top()) shouldBe fx(face(middle).position.top())
        fx(last.panel.backgroundColor()) shouldBe RichRow.blockTint()
        fx(last.panel.backgroundColor().alpha) should be > 0.0
        // the rows end to end, the caption between two blocks in none
        val i = at(list, "^work")
        fx(list.list.top(i)) shouldBe fx(list.list.top(i - 1) + list.list.heightAt(i - 1))
        fx(row(list, "i1").holder.visible()) shouldBe true
        fx(row(list, "^work").panel.parent().exists(_.visible())) shouldBe false
      }
    }
    "show a collapsed block as its header bar with its count, opened and closed by a click anywhere on it, Enter, Left and Right" in {
      val collapsed = Var(Set("b"))
      val list = fx(made(collapsed))
      onScreen(list) {
        lines(list).slice(7, 9) shouldBe List("#b", "^personal")
        val beta = row(list, "#b")
        fx(beta.size.height()) shouldBe RichRow.blockHeader + RichRow.blockGap
        fx(beta.holder.size.height()) shouldBe RichRow.blockHeader
        fx(beta.panel.position.top()) shouldBe 0.0
        fx(beta.panel.size.height()) shouldBe RichRow.blockHeader
        fx(beta.toggle.icon()) shouldBe MaterialIcon.ChevronRight
        count(beta) shouldBe List("7")
        fx(beta.count.absoluteLeft + beta.count.size.width()) shouldBe (width - side - RichRow.toggleSize - 6.0 +- 0.5)
        // the bar's start, its end (the ▸), and its middle
        click(beta, 20.0)
        collapsed() shouldBe Set.empty
        fx(list.list.currentItem.map(_.key)) shouldBe Some(GroupedList.headerKey("b"))
        lines(list).slice(7, 11) shouldBe List("#b", "b0", "b1", "b2")
        click(row(list, "#b"), width - side - 4.0)
        collapsed() shouldBe Set("b")
        click(row(list, "#b"), width / 2.0)
        collapsed() shouldBe Set.empty
        fx(row(list, "#b").toggle.icon()) shouldBe MaterialIcon.ExpandMore
        press(KeyCode.ENTER)
        collapsed() shouldBe Set("b")
        press(KeyCode.RIGHT)
        collapsed() shouldBe Set.empty
        press(KeyCode.LEFT)
        collapsed() shouldBe Set("b")
        // Up and Down through rows across blocks: the caption, Alpha's last item, and back
        press(KeyCode.UP)
        fx(list.list.currentItem.map(_.key)) shouldBe Some(GroupedList.itemKey("a", "a1"))
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        fx(list.list.currentItem.map(_.key)) shouldBe Some(RichList.groupLineKey("personal"))
      }
    }
    "keep a long title's count in its bar, the title cut short of it" in {
      val long = Var(Vector(RichSection("long", "A section whose title is far too long to fit on one line of it", Vector("x0"))))
      val list = fx {
        val l = new RichList[String](long, s => s, s => RichLook(s), Var(Set("long")))
        l.style @= RichStyle.Blocks
        l.size.width @= width
        l
      }
      onScreen(list) {
        val r = row(list, "#long")
        count(r) shouldBe List("1")
        val end = fx(r.count.absoluteLeft)
        fx(end + r.count.size.width()) shouldBe (width - side - RichRow.toggleSize - 6.0 +- 0.5)
        val title = fx(all(r).collect { case t: TextView if shown(t, r) && t.content().startsWith("A section") => t }.head)
        fx(title.absoluteLeft) shouldBe (side +- 0.5)
        fx(title.absoluteLeft + title.size.width()) should be <= (end - 4.0)
      }
    }
    "open the blocks the words find something in, to those items" in {
      val collapsed = Var(Set("a", "b"))
      val list = fx(made(collapsed, search = true))
      onScreen(list) {
        fx(list.query @= "1")
        lines(list) shouldBe List("#inbox", "i1", "^work", "#a", "a1", "#b", "b1")
        fx(row(list, "#a").toggle.icon()) shouldBe MaterialIcon.ExpandMore
        fx(row(list, "#a").size.height()) shouldBe RichRow.blockHeader
        fx(row(list, "a1").size.height()) shouldBe RichRow.shortHeight + RichRow.blockPad + RichRow.blockGap
        fx(list.query @= "")
        collapsed() shouldBe Set("a", "b")
        fx(row(list, "#a").size.height()) shouldBe RichRow.blockHeader + RichRow.blockGap
      }
    }
    "light the item chosen and the cursor inside its block, not across the row" in {
      val list = fx(made())
      onScreen(list) {
        fx(list.selected @= Some("a0"))
        val r = row(list, "a0")
        fx(r.chosen()) shouldBe true
        fx(r.backgroundColor()) shouldBe Color.Clear
        fx(r.borderWidth()) shouldBe 0.0
        fx(r.light.backgroundColor().alpha) should be > 0.0
        fx(r.light.borderWidth()) shouldBe 1.0
        fx(r.light.absoluteLeft) should be > fx(r.holder.absoluteLeft)
        fx(r.light.absoluteLeft + r.light.size.width()) should be < fx(r.holder.absoluteLeft + r.holder.size.width())
        fx(r.light.absoluteTop) should be >= fx(r.absoluteTop)
        fx(r.light.absoluteTop + r.light.size.height()) should be <= fx(r.absoluteTop + r.size.height())
        // the rows not chosen unlit
        fx(row(list, "a1").light.backgroundColor()) shouldBe Color.Clear
      }
    }
    "drag items within a block and into another, as the app allows" in {
      val list = fx(made())
      var moves: List[RichMove] = Nil
      fx(list.moved.attach(m => moves = moves :+ m))
      fx(list.reorders @= true)
      onScreen(list) {
        def dragTo(key: String, line: String, down: Double): Unit = fx {
          val target = row(list, line)
          list.list.dropAt(20.0, target.absoluteTop + down, Dragged(list.list, List(GroupedList.itemKey("a", key))))
        }
        // within its block, by the pointer: over the top of b-less a1, before it
        dragTo("a0", "a1", 4.0)
        moves shouldBe List(RichMove(List("a0"), "a", "a", Some("a1")))
        // into another block is the app's to allow
        dragTo("a0", "b1", 4.0)
        moves.size shouldBe 1
        fx(list.accepts @= ((_: RichMove) => true))
        dragTo("a0", "b1", 4.0)
        // over the bottom of Beta's last, in its padding: after it, at Beta's end
        dragTo("a1", "b2", RichRow.shortHeight)
        // onto Beta's header bar's lower half: Beta's start
        dragTo("a1", "#b", RichRow.blockHeader - 4.0)
        moves.drop(1) shouldBe List(RichMove(List("a0"), "a", "b", Some("b1")), RichMove(List("a1"), "a", "b", None),
          RichMove(List("a1"), "a", "b", Some("b0")))
      }
    }
    "be mirrored right to left" in {
      val list = fx(made())
      onScreen(list) {
        fx(Direction.set(true))
        val alpha = row(list, "#a")
        fx(alpha.toggle.absoluteLeft) shouldBe (side +- 0.5)
        val f = face(row(list, "a0"))
        fx(f.absoluteLeft + f.size.width()) shouldBe (width - side +- 0.5)
        val caption = row(list, "^work").caption
        fx(caption.absoluteLeft + caption.size.width()) shouldBe (width - side +- 0.5)
        fx(row(list, "a0").holder.absoluteLeft) shouldBe (RichRow.blockMargin +- 0.5)
        // Left opens and Right closes, right to left
        fx(list.list.moveTo(at(list, "#a")))
        press(KeyCode.RIGHT)
        fx(list.collapsed()) shouldBe Set("a")
        fx(alpha.toggle.icon()) shouldBe MaterialIcon.ChevronLeft
        press(KeyCode.LEFT)
        fx(list.collapsed()) shouldBe Set.empty
      }
    }
  }

  "A rich list drawn as a tree" should {
    "be drawn as it always was, before blocks and after them" in {
      val list = fx(made(style = RichStyle.Tree))
      onScreen(list) {
        def tree(): Unit = {
          fx(row(list, "#a").size.height()) shouldBe RichRow.headerHeight
          fx(row(list, "^work").size.height()) shouldBe RichRow.headerHeight
          fx(row(list, "a1").size.height()) shouldBe RichRow.shortHeight
          chevrons(row(list, "#a")) shouldBe 1
          chevrons(row(list, "^work")) shouldBe 1
          fx(face(row(list, "a0")).absoluteLeft) shouldBe (RichRow.inset +- 0.5)
          fx(row(list, "^work").backgroundColor()) shouldBe Color.Clear
        }
        tree()
        // no block's parts made in a tree
        fx(row(list, "a0").children().size) shouldBe 12
        fx(list.style @= RichStyle.Blocks)
        fx(row(list, "a1").size.height()) shouldBe RichRow.shortHeight + RichRow.blockPad + RichRow.blockGap
        fx(list.style @= RichStyle.Tree)
        tree()
        fx(row(list, "a0").holder.visible()) shouldBe false
        fx(row(list, "a0").light.visible()) shouldBe false
        fx(row(list, "^work").caption.visible()) shouldBe false
        fx(list.list.moveTo(at(list, "a0")))
        fx(row(list, "a0").borderWidth()) shouldBe 1.0
      }
    }
  }
}
