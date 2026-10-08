package everyui.split

import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.{FxSpec, ui}
import reactify.*

/** A part's edge dragged by hand: its length followed and kept to its minimum and its most, each edge growing the part
  * away from it, mirrored across right to left; folded by a double-click and by a drag most of the way past its
  * minimum where it is collapsible, and brought back by a drag out; moved by the arrows and folded by Enter when
  * focused; and let go of with nothing left on the screen where it is taken away mid-drag. */
class ResizeHandleSpec extends FxSpec {
  private def pointer(h: SplitHandle, kind: PointerKind, x: Double, y: Double): Unit = fx {
    val e = PointerEvent(kind, x, y)
    kind match {
      case PointerKind.Down => h.event.pointer.down @= e
      case PointerKind.Move => h.event.pointer.move @= e
      case PointerKind.Up => h.event.pointer.up @= e
      case _ => h.event.click @= e
    }
  }

  /** `h` pressed at `x`, `y`, dragged by `dx`, `dy` and let go. */
  private def drag(h: SplitHandle, x: Double, y: Double, dx: Double, dy: Double): Unit = {
    pointer(h, PointerKind.Down, x, y)
    pointer(h, PointerKind.Move, x + dx / 2.0, y + dy / 2.0)
    // on over the catcher it puts over the screen, and let go there
    fx {
      val catcher = ui.children().last
      catcher.event.pointer.move @= PointerEvent(PointerKind.Move, x + dx, y + dy)
      catcher.event.pointer.up @= PointerEvent(PointerKind.Up, x + dx, y + dy)
    }
  }

  private def onScreen[T](h: ResizeHandle)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      ui.children += h
    }
    try test finally fx {
      Direction.set(false)
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A resize handle" should {
    "drag its part's length, kept to its minimum and its most" in {
      val width = Var(280.0)
      val h = fx(new ResizeHandle(width, min = 180.0, max = 480.0))
      onScreen(h) {
        fx(h.size.width()) shouldBe 8.0
        fx(h.cursor()) shouldBe "col-resize"
        drag(h, 280.0, 300.0, 60.0, 0.0)
        width() shouldBe 340.0
        fx(ui.children().size) shouldBe 1   // the catcher gone as it let go
        drag(h, 340.0, 300.0, 400.0, 0.0)
        width() shouldBe 480.0
        drag(h, 480.0, 300.0, -400.0, 0.0)
        width() shouldBe 180.0
      }
    }
    "grow its part away from the edge it is on, mirrored across right to left" in {
      val start = Var(300.0)
      val atStart = fx(new ResizeHandle(start, min = 100.0, edge = ResizeEdge.Start))
      onScreen(atStart) {
        drag(atStart, 900.0, 300.0, -50.0, 0.0)
        start() shouldBe 350.0
      }
      val bottom = Var(200.0)
      val atBottom = fx(new ResizeHandle(bottom, min = 100.0, edge = ResizeEdge.Bottom))
      onScreen(atBottom) {
        fx((atBottom.size.height(), atBottom.cursor())) shouldBe (8.0, "row-resize")
        drag(atBottom, 300.0, 200.0, 0.0, 30.0)
        bottom() shouldBe 230.0
      }
      val top = Var(200.0)
      val atTop = fx(new ResizeHandle(top, min = 100.0, edge = ResizeEdge.Top))
      onScreen(atTop) {
        drag(atTop, 300.0, 600.0, 0.0, 30.0)
        top() shouldBe 170.0
      }
      // a sidebar's end is on its left right to left: dragged left, it grows
      val rtl = Var(280.0)
      val end = fx(new ResizeHandle(rtl, min = 180.0))
      fx(Direction.set(true))
      onScreen(end) {
        drag(end, 1600.0, 300.0, -40.0, 0.0)
        rtl() shouldBe 320.0
      }
    }
    "fold on a double-click or a drag most of the way past its minimum, and come back by a drag out" in {
      val width = Var(280.0)
      val folded = Var(false)
      val h = fx(new ResizeHandle(width, min = 180.0, max = 480.0, collapsed = Some(folded)))
      onScreen(h) {
        pointer(h, PointerKind.Click, 280.0, 300.0)
        pointer(h, PointerKind.Click, 280.0, 300.0)
        folded() shouldBe true
        width() shouldBe 280.0
        pointer(h, PointerKind.Click, 280.0, 300.0)
        pointer(h, PointerKind.Click, 280.0, 300.0)
        folded() shouldBe false
        // past half its minimum, folded, its width kept for when it comes back
        drag(h, 280.0, 300.0, -200.0, 0.0)
        folded() shouldBe true
        width() shouldBe 180.0
        // dragged out from the folded edge, back at what the drag says
        drag(h, 48.0, 300.0, 200.0, 0.0)
        folded() shouldBe false
        width() shouldBe 200.0
        // with no Var to fold, a double-click does nothing
        val plain = Var(250.0)
        val p = fx(new ResizeHandle(plain, min = 180.0))
        fx(ui.children += p)
        pointer(p, PointerKind.Click, 250.0, 300.0)
        pointer(p, PointerKind.Click, 250.0, 300.0)
        plain() shouldBe 250.0
        drag(p, 250.0, 300.0, -200.0, 0.0)
        plain() shouldBe 180.0
      }
    }
    "move by the arrows along it and fold by Enter, focused" in {
      val width = Var(280.0)
      val folded = Var(false)
      val h = fx(new ResizeHandle(width, min = 180.0, max = 480.0, collapsed = Some(folded)))
      onScreen(h) {
        fx {
          h.focusable @= true
          h.focus()
          Focus.key("ArrowRight", true)
          Focus.key("ArrowRight", false)
        }
        width() shouldBe 304.0
        fx(Focus.key("ArrowLeft", true))
        width() shouldBe 280.0
        fx(Focus.key("Enter", true))
        folded() shouldBe true
        // an arrow brings it back
        fx(Focus.key("Enter", false))
        fx(Focus.key("ArrowRight", true))
        folded() shouldBe false
        fx(Focus.focused()) shouldBe Some(h)
      }
    }
    "leave nothing on the screen where it is taken away while it is held" in {
      val width = Var(280.0)
      val h = fx(new ResizeHandle(width, min = 180.0))
      onScreen(h) {
        pointer(h, PointerKind.Down, 280.0, 300.0)
        pointer(h, PointerKind.Move, 300.0, 300.0)
        fx(ui.children().size) shouldBe 2
        fx {
          ui.children -= h
          h.dispose()
        }
        fx(ui.children().size) shouldBe 0
      }
    }
  }
}
