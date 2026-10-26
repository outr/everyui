package everyui.list

import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.render.Host
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import reactify.*

/** An item's grip, while its list drags them (`RichList.dragsItems`): muted, at the row's start in room of its own, so
  * the row's pieces never shift as it shows; there with the pointer over the row or the cursor on it, and on every row
  * on a touch screen; never on a header; gone, with its room, while words narrow the list or `reorders` is off;
  * mirrored right to left, and clear of a live item's bar. */
class GripSpec extends FxSpec {
  private val width = 320.0

  private def made(style: RichStyle = RichStyle.Tree): RichList[String] = {
    val list = new RichList[String](Var(Vector(RichSection("a", "Alpha", Vector("one", "two", "three")))), s => s,
      s => RichLook(s, live = s == "two"), search = true)
    list.style @= style
    list.size.width @= width
    list.size.height @= 600.0
    list
  }

  private def at(list: RichList[String], line: String): Int = (0 until list.list.count).map(list.list(_))
    .indexWhere(_.item.contains(line))

  private def row(list: RichList[String], line: String): RichRow[String] =
    list.list.rowAt(at(list, line)).get.asInstanceOf[RichRow[String]]

  private def header(list: RichList[String]): RichRow[String] =
    list.list.rowAt((0 until list.list.count).indexWhere(list.list(_).header)).get.asInstanceOf[RichRow[String]]

  private def hasGrip(r: RichRow[String]): Boolean = r.children().exists(_ eq r.grip)
  private def gripShows(r: RichRow[String]): Boolean = hasGrip(r) && r.grip.visible()

  private def onScreen[T](list: RichList[String])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += list
    }
    try test finally fx {
      Direction.set(false)
      ui.children.clear()
      list.dispose()
      Focus.focused @= None
      Focus.visible @= false
    }
  }

  "An item's grip" should {
    "take room of its own at the start, shown with the pointer over the row, so nothing shifts" in {
      val plain = fx(made())
      val before = onScreen(plain)(fx(row(plain, "one").title.position.left()))
      val list = fx(made())
      onScreen(list) {
        fx(list.reorders @= true)
        val r = fx(row(list, "one"))
        fx(hasGrip(r)) shouldBe true
        fx(r.grip.visible()) shouldBe false
        fx(r.title.position.left()) shouldBe before + RichRow.gripRoom
        fx(r.event.pointer.enter @= PointerEvent(PointerKind.Enter, r.absoluteLeft + 40.0, r.absoluteTop + 10.0))
        fx(r.grip.visible()) shouldBe true
        fx(r.grip.color()) shouldBe Theme.muted()
        fx(r.title.position.left()) shouldBe before + RichRow.gripRoom
        // at the start, before the title, in the row's middle
        fx(r.grip.position.left() + r.grip.size.width()) should be < fx(r.title.position.left())
        fx(r.grip.position.top() + r.grip.size.height() / 2.0) shouldBe fx(r.size.height() / 2.0)
        // the others still rest without one
        fx(gripShows(row(list, "three"))) shouldBe false
        fx(r.event.pointer.leave @= PointerEvent(PointerKind.Leave, 0.0, 0.0))
        fx(r.grip.visible()) shouldBe false
      }
    }
    "show with the cursor on the row, and never on a header" in {
      val list = fx(made())
      onScreen(list) {
        fx {
          list.reorders @= true
          Focus.visible @= true
          list.focus()
          list.list.moveTo(at(list, "three"))
        }
        fx(gripShows(row(list, "three"))) shouldBe true
        fx(gripShows(row(list, "one"))) shouldBe false
        fx(hasGrip(header(list)) && header(list).grip.visible()) shouldBe false
      }
    }
    "go, with its room, while words narrow the list, and come back after" in {
      val list = fx(made())
      onScreen(list) {
        fx(list.reorders @= true)
        val start = fx(row(list, "one").title.position.left())
        fx(list.query @= "one")
        fx(list.list.draggable()) shouldBe false
        val r = fx(row(list, "one"))
        fx(r.title.position.left()) shouldBe start - RichRow.gripRoom
        fx(r.event.pointer.enter @= PointerEvent(PointerKind.Enter, r.absoluteLeft + 40.0, r.absoluteTop + 10.0))
        fx(gripShows(r)) shouldBe false
        fx(list.query @= "")
        fx(list.list.draggable()) shouldBe true
        fx(row(list, "one").title.position.left()) shouldBe start
      }
    }
    "show on every row on a touch screen" in {
      val was = fx(Host.args)
      fx(Host.args = was + ("pointer" -> "false"))
      try {
        val list = fx(made())
        onScreen(list) {
          fx(list.reorders @= true)
          for (k <- List("one", "two", "three")) fx(gripShows(row(list, k))) shouldBe true
          fx(list.reorders @= false)
          for (k <- List("one", "two", "three")) fx(gripShows(row(list, k))) shouldBe false
        }
      } finally fx(Host.args = was)
    }
    for (style <- List(RichStyle.Tree, RichStyle.Blocks)) {
      s"sit clear of a live item's bar, mirrored right to left ($style)" in {
        val list = fx(made(style))
        onScreen(list) {
          fx(list.reorders @= true)
          val r = fx(row(list, "two"))
          fx(r.event.pointer.enter @= PointerEvent(PointerKind.Enter, r.absoluteLeft + 40.0, r.absoluteTop + 10.0))
          fx(gripShows(r)) shouldBe true
          val left = fx(r.grip.position.left())
          left should be > fx(r.liveBar.position.left() + r.liveBar.size.width())
          fx(r.grip.position.left() + r.grip.size.width()) should be < fx(r.title.position.left())
          fx(Direction.set(true))
          val m = fx(row(list, "two"))
          fx(m.grip.position.left()) shouldBe (width - left - RichRow.gripSize)
          fx(m.grip.position.left() + m.grip.size.width()) should be < fx(m.liveBar.position.left())
          fx(m.grip.position.left()) should be > fx(m.title.position.left() + m.title.size.width())
        }
      }
    }
  }
}
