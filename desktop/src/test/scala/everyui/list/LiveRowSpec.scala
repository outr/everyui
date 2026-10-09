package everyui.list

import everyui.Color
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import reactify.*

/** A live item (`RichLook.live`), held somewhere else in the app: a faint accent tint behind it and a thin accent bar
  * at its start edge, clear of the cursor's ring and the busy ring (in a block, in the margin beside it), mirrored right
  * to left; apart from the item chosen, whose tint stays the stronger and which has no bar unless it is live too; lit by
  * the cursor as any row with its bar kept; its `liveLabel` said while it is live. Rows that are not live are made as
  * they were. */
class LiveRowSpec extends FxSpec {
  private val width = 320.0
  private val now = everyui.util.Clock.real()
  // which items are live; "t" is tall (with a detail), the rest short
  private val live: Var[Set[String]] = Var(Set.empty)

  private def look(s: String): RichLook = RichLook(s, detail = if (s.startsWith("t")) Some("detail of " + s) else None,
    time = Some(now - 5L * 60000L), live = live().contains(s), liveLabel = if (s == "l") None else Some("open in a tab"))

  private def made(style: RichStyle): RichList[String] = {
    val list = new RichList[String](Var(Vector(RichSection("a", "Alpha", Vector("t0", "t1", "s0", "s1", "l")))), s => s,
      look)
    list.style @= style
    list.size.width @= width
    list.size.height @= 1000.0
    list
  }

  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def at(list: RichList[String], line: String): Int = onFx((0 until list.list.count).map(list.list(_))
    .indexWhere(_.item.contains(line)))

  private def row(list: RichList[String], line: String): RichRow[String] =
    onFx(list.list.rowAt(at(list, line)).get.asInstanceOf[RichRow[String]])

  // what lights a row: its own background in a tree, its light in a block
  private def lit(style: RichStyle, r: RichRow[String]): Color = onFx(if (style == RichStyle.Blocks) r.light.backgroundColor()
    else r.backgroundColor())

  private def hasBar(r: RichRow[String]): Boolean = onFx(r.children().exists(_ eq r.liveBar) && r.liveBar.visible())

  // a row's children as it lays them out: kind, place, size, visibility, background
  private def shape(r: RichRow[String]): List[String] = onFx(r.children().toList.map(c => List(c.kind, c.position.left(),
    c.position.top(), c.size.width(), c.size.height(), c.visible(), c.backgroundColor()).mkString(" ")))

  private def onScreen[T](list: RichList[String])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += list
      list.focus()
    }
    try test finally fx {
      Direction.set(false)
      live @= Set.empty
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A live item" should {
    for (style <- List(RichStyle.Tree, RichStyle.Blocks)) {
      s"be tinted faintly with a thin accent bar at its start edge ($style)" in {
        fx(live @= Set("t0", "s0"))
        val list = fx(made(style))
        onScreen(list) {
          fx(list.list.moveTo(at(list, "s1")))
          for (k <- List("t0", "s0")) {
            val r = row(list, k)
            lit(style, r) shouldBe RichRow.liveTint()
            hasBar(r) shouldBe true
            fx(r.liveBar.backgroundColor()) shouldBe Theme.accent()
            fx(r.liveBar.size.width()) shouldBe RichRow.liveWidth
            fx(r.liveBar.position.left()) shouldBe RichRow.liveFrom
            // clear of the cursor's ring, and of the avatar and its busy ring (in a block, in the margin beside it)
            fx(r.liveBar.position.left()) should be > 1.0
            val face = fx(if (k == "t0") r.tallFace.position.left() - everyui.kit.BusyRing.outset(RichRow.tallLead) else r.title.position.left())
            fx(r.liveBar.position.left() + r.liveBar.size.width()) should be < face
            if (style == RichStyle.Blocks) fx(r.liveBar.position.left() + r.liveBar.size.width()) should be <= RichRow.blockMargin
            fx(r.liveBar.position.left() + r.liveBar.size.width()) should be < fx(r.title.position.left())
            // as tall as its line less the inset at each end, in its middle
            val line = if (k == "t0") RichRow.tallHeight else RichRow.shortHeight
            fx(r.liveBar.size.height()) shouldBe (line - 2.0 * RichRow.liveInset)
            val above = if (style == RichStyle.Blocks) fx(r.light.position.top()) - 2.0 else 0.0
            fx(r.liveBar.position.top()) shouldBe above + RichRow.liveInset
          }
          // the tint is faint: the accent, well under the item chosen's
          RichRow.liveTint().alpha should be < 0.12
          RichRow.liveTint().withAlpha(1.0) shouldBe Theme.accent().withAlpha(1.0)
          // a row that is not live has none of it
          val s1 = row(list, "t1")
          lit(style, s1) shouldBe Color.Clear
          hasBar(s1) shouldBe false
        }
      }
      s"stay apart from the item chosen, which is the stronger, and show both where it is both ($style)" in {
        fx(live @= Set("s0", "s1"))
        val list = fx(made(style))
        onScreen(list) {
          fx(list.list.moveTo(at(list, "t0")))
          fx(list.selected @= Some("t1"))
          // chosen, not live: its own tint, no bar
          val chosen = row(list, "t1")
          lit(style, chosen) shouldBe Theme.accent().withAlpha(0.26)
          hasBar(chosen) shouldBe false
          lit(style, chosen).alpha should be > RichRow.liveTint().alpha * 3.0
          // chosen and live: the chosen tint and the bar
          fx(list.selected @= Some("s0"))
          val both = row(list, "s0")
          lit(style, both) shouldBe Theme.accent().withAlpha(0.26)
          hasBar(both) shouldBe true
          lit(style, row(list, "s1")) shouldBe RichRow.liveTint()
          lit(style, chosen) shouldBe Color.Clear
          // in a list without the focus the chosen tint is lower, and still the stronger
          fx(both.active @= false)
          lit(style, both) shouldBe Theme.accent().withAlpha(0.18)
          lit(style, both).alpha should be > RichRow.liveTint().alpha * 2.0
          hasBar(both) shouldBe true
        }
      }
      s"be lit by the cursor as any row, its bar kept, and go back to its tint ($style)" in {
        fx(live @= Set("s0"))
        val list = fx(made(style))
        onScreen(list) {
          fx(list.list.moveTo(at(list, "s0")))
          val r = row(list, "s0")
          lit(style, r) shouldBe Theme.control()
          hasBar(r) shouldBe true
          if (style == RichStyle.Blocks) fx(r.light.borderWidth()) shouldBe 1.0 else fx(r.borderWidth()) shouldBe 1.0
          fx(list.list.moveTo(at(list, "s1")))
          lit(style, r) shouldBe RichRow.liveTint()
          // no longer live: as a row that never was, the bar hidden
          fx(live @= Set.empty)
          lit(style, r) shouldBe Color.Clear
          fx(r.liveBar.visible()) shouldBe false
        }
      }
      s"be mirrored right to left ($style)" in {
        fx(live @= Set("s0"))
        val list = fx(made(style))
        onScreen(list) {
          fx(Direction.set(true))
          val r = row(list, "s0")
          fx(r.liveBar.position.left()) shouldBe (width - RichRow.liveFrom - RichRow.liveWidth)
          fx(r.liveBar.position.left()) should be > fx(r.title.position.left() + r.title.size.width())
          hasBar(r) shouldBe true
        }
      }
    }
    "be said with its liveLabel while it is live" in {
      fx(live @= Set("s0", "l"))
      val list = fx(made(RichStyle.Tree))
      onScreen(list) {
        fx(row(list, "s0").semantics().label) should endWith(", open in a tab")
        // not live, its label is not said; live without one, nothing more is
        fx(row(list, "s1").semantics().label) should not include "open in a tab"
        fx(row(list, "l").semantics().label) shouldBe RichList.said(RichLook("l", time = Some(now - 5L * 60000L)), list.now(),
          list.timeText)
        fx(live @= Set.empty)
        fx(row(list, "s0").semantics().label) should not include "open in a tab"
      }
      RichList.said(RichLook("x", live = true, liveLabel = Some("playing")), 0L, (_, _) => "") shouldBe "x, playing"
      RichList.said(RichLook("x", liveLabel = Some("playing")), 0L, (_, _) => "") shouldBe "x"
    }
  }

  "A row that is not live" should {
    "be made as it was, beside live rows or not" in {
      for (style <- List(RichStyle.Tree, RichStyle.Blocks)) {
        val plain = fx(made(style))
        val (kids, before) = onScreen(plain) {
          val r = row(plain, "t1")
          (fx(r.children().size), shape(r))
        }
        kids shouldBe (if (style == RichStyle.Tree) 12 else 17)
        fx(live @= Set("t0", "s0"))
        val list = fx(made(style))
        onScreen(list) {
          val r = row(list, "t1")
          fx(r.children().size) shouldBe kids
          shape(r) shouldBe before
        }
      }
    }
  }
}
