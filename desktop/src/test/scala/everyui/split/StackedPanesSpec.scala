package everyui.split

import everyui.component.*
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.list.RichAction
import everyui.perf.{Quality, QualityLevel}
import everyui.render.Nodes
import everyui.theme.Theme
import everyui.{FxSpec, LeakPaths, ui}
import javafx.scene.input.{MouseButton, MouseEvent}
import reactify.*

import java.lang.ref.WeakReference

/** Stacked panes: the room shared by weight, kept to the minimums, all to the one left open and none with all folded;
  * a handle dragged between two open panes, kept to their minimums, its result written to the weights; a pane opened
  * again at its weight; the headers folded by a click, Enter and Space, and moved among by the arrows, into the content
  * and out, and onto the actions, which run without folding; mirrored right to left; the app's Vars followed both ways;
  * and the stack let go of once disposed, though the app keeps its Vars. */
class StackedPanesSpec extends FxSpec {
  private val header = 32.0

  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      Focus.focused @= None
    }
    offStage()
    super.afterAll()
  }

  private def card(): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c
  }

  /** Three panes over cards, each of the app's Vars given: weights 1, 1 and 2. */
  private class Made(val closed: Vector[Var[Boolean]] = Vector(Var(false), Var(false), Var(false)),
                     val weights: Vector[Var[Double]] = Vector(Var(1.0), Var(1.0), Var(2.0))) {
    val cards: Vector[Component] = Vector(card(), card(), card())
    var ran: List[String] = Nil
    val panes: Vector[StackedPane] = cards.indices.toVector.map(i =>
      new StackedPane("Pane " + i.toString, cards(i), closed(i), weights(i)))
    panes(0).icon @= Some(MaterialIcon.Folder)
    panes(0).actions @= List(RichAction(MaterialIcon.Add, "New", () => ran = ran :+ "new"),
      RichAction(MaterialIcon.Refresh, "Refresh", () => ran = ran :+ "refresh"))
    panes(1).actions @= List(RichAction(MaterialIcon.Add, "Start", () => ran = ran :+ "start", shown = true))
    val stack: StackedPanes = new StackedPanes(panes)
    stack.glide @= 0.0
  }

  /** `m` on the screen, 300 by 600 (504 for the content under three headers), and taken away after. */
  private def onScreen[T](m: Made)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      m.stack.size.width @= 300.0
      m.stack.size.height @= 600.0
      ui.children += m.stack
    }
    try test finally fx {
      Direction.set(false)
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def heights(m: Made): Vector[Double] = fx(m.cards.map(c => if (c.parent().exists(_.parent().exists(_.visible())))
    c.size.height() else 0.0))
  private def tops(m: Made): Vector[Double] = fx(m.panes.map(_.position.top()))

  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private def pointer(c: Component, kind: PointerKind, x: Double, y: Double): Unit = fx {
    val e = PointerEvent(kind, x, y)
    kind match {
      case PointerKind.Down => c.event.pointer.down @= e
      case PointerKind.Move => c.event.pointer.move @= e
      case PointerKind.Up => c.event.pointer.up @= e
      case _ => c.event.click @= e
    }
  }

  // a real click on `on`, `along` from its left (its middle, where not given)
  private def click(on: Component, along: Double = -1.0): Unit = fx {
    val x = on.absoluteLeft + (if (along < 0.0) on.size.width() / 2.0 else along)
    val y = on.absoluteTop + on.size.height() / 2.0
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y, MouseButton.PRIMARY,
      1, false, false, false, false, true, false, false, false, false, true, null))
  }

  "The layout" should {
    "share the room by weight, keeping each open pane to its minimum" in {
      StackedPanes.heights(504.0, Vector(true, true, true), Vector(1.0, 1.0, 2.0), Vector(60.0, 60.0, 60.0)) shouldBe
        Vector(126.0, 126.0, 252.0)
      // the first two held at their minimums, the rest to the third
      StackedPanes.heights(300.0, Vector(true, true, true), Vector(1.0, 1.0, 8.0), Vector(60.0, 60.0, 60.0)) shouldBe
        Vector(60.0, 60.0, 180.0)
      // no room for all the minimums: shared by them
      StackedPanes.heights(100.0, Vector(true, true), Vector(1.0, 5.0), Vector(60.0, 90.0)) shouldBe Vector(40.0, 60.0)
      // a weight of nothing still has its minimum
      StackedPanes.heights(200.0, Vector(true, true), Vector(0.0, 1.0), Vector(60.0, 60.0)) shouldBe Vector(60.0, 140.0)
    }
    "give all the room to the one left open, and none with all folded" in {
      StackedPanes.heights(504.0, Vector(false, true, false), Vector(1.0, 1.0, 2.0), Vector(60.0, 600.0, 60.0)) shouldBe
        Vector(0.0, 504.0, 0.0)
      StackedPanes.heights(504.0, Vector(false, false, false), Vector(1.0, 1.0, 2.0), Vector(60.0, 60.0, 60.0)) shouldBe
        Vector(0.0, 0.0, 0.0)
      StackedPanes.heights(-20.0, Vector(true, true), Vector(1.0, 1.0), Vector(60.0, 60.0)) shouldBe Vector(0.0, 0.0)
    }
    "move a handle's room between its two panes, kept to their minimums, into weights that share it the same" in {
      val from = Vector(126.0, 126.0, 252.0)
      val mins = Vector(60.0, 60.0, 60.0)
      StackedPanes.dragged(from, mins, 0, 1, 40.0) shouldBe Vector(166.0, 86.0, 252.0)
      StackedPanes.dragged(from, mins, 0, 1, 100.0) shouldBe Vector(192.0, 60.0, 252.0)
      StackedPanes.dragged(from, mins, 0, 1, -100.0) shouldBe Vector(60.0, 192.0, 252.0)
      // past a folded pane, to the open one beyond it
      StackedPanes.dragged(Vector(252.0, 0.0, 252.0), mins, 0, 2, 50.0) shouldBe Vector(302.0, 0.0, 202.0)
      // where the two cannot both have their minimums, nothing moves
      StackedPanes.dragged(Vector(40.0, 60.0, 0.0), mins, 0, 1, 10.0) shouldBe Vector(40.0, 60.0, 0.0)
      val open = Vector(true, true, true)
      val w = StackedPanes.weightsFor(Vector(192.0, 60.0, 252.0), open, Vector(1.0, 1.0, 2.0))
      w.sum shouldBe (4.0 +- 1e-9)
      StackedPanes.heights(504.0, open, w, mins).zip(Vector(192.0, 60.0, 252.0)).foreach(p => p._1 shouldBe (p._2 +- 1e-6))
      // a folded pane's weight kept, the open ones' total kept
      StackedPanes.weightsFor(Vector(126.0, 378.0, 0.0), Vector(true, true, false), Vector(1.0, 1.0, 5.0)) shouldBe
        Vector(0.5, 1.5, 5.0)
    }
  }

  "Stacked panes" should {
    "lay their panes out one under another, the open ones sharing the height by weight" in {
      val m = fx(new Made)
      onScreen(m) {
        heights(m) shouldBe Vector(126.0, 126.0, 252.0)
        tops(m) shouldBe Vector(0.0, 158.0, 316.0)
        fx(m.panes.map(_.size.height())) shouldBe Vector(158.0, 158.0, 284.0)
        fx(m.cards.map(_.size.width())) shouldBe Vector(300.0, 300.0, 300.0)
        fx(m.panes.map(_.header.size.height())) shouldBe Vector(header, header, header)
        // a handle under each open pane with an open one below it, its rule on the line between them
        fx(m.stack.handles.map(_.visible())) shouldBe Vector(true, true)
        fx(m.stack.handles.map(h => h.position.top() + StackedPanes.above)) shouldBe Vector(158.0, 316.0)
        fx(m.stack.handles(0).cursor()) shouldBe "row-resize"
      }
    }
    "fold a pane to its header, the others sharing what it gave up, and open it again at its weight" in {
      val m = fx(new Made)
      onScreen(m) {
        fx(m.closed(1) @= true)
        heights(m) shouldBe Vector(168.0, 0.0, 336.0)
        tops(m) shouldBe Vector(0.0, 200.0, 232.0)
        fx(m.panes(1).size.height()) shouldBe header
        fx(m.panes(1).body.visible()) shouldBe false
        // the handle under the first goes past the folded one to the third; none under the folded one
        fx(m.stack.handles.map(_.visible())) shouldBe Vector(true, false)
        fx(m.closed(1) @= false)
        heights(m) shouldBe Vector(126.0, 126.0, 252.0)
        m.weights.map(_()) shouldBe Vector(1.0, 1.0, 2.0)
      }
    }
    "give the one left open all the room, and stack the headers at the top with all folded" in {
      val m = fx(new Made)
      onScreen(m) {
        fx {
          m.closed(0) @= true
          m.closed(2) @= true
        }
        heights(m) shouldBe Vector(0.0, 504.0, 0.0)
        tops(m) shouldBe Vector(0.0, 32.0, 568.0)
        fx(m.stack.handles.map(_.visible())) shouldBe Vector(false, false)
        fx(m.closed(1) @= true)
        tops(m) shouldBe Vector(0.0, 32.0, 64.0)
        fx(m.panes.map(_.size.height())) shouldBe Vector(header, header, header)
      }
    }
    "follow a handle dragged, kept to the minimums, and write what it leaves to the weights" in {
      val m = fx(new Made)
      onScreen(m) {
        val h = m.stack.handles(0)
        pointer(h, PointerKind.Down, 150.0, 158.0)
        pointer(h, PointerKind.Move, 150.0, 198.0)
        fx(ui.children().size) shouldBe 2   // the catcher over the screen, while it drags
        heights(m) shouldBe Vector(166.0, 86.0, 252.0)
        // past the second's minimum, held at it
        pointer(fx(ui.children().last), PointerKind.Move, 150.0, 400.0)
        heights(m) shouldBe Vector(192.0, 60.0, 252.0)
        pointer(fx(ui.children().last), PointerKind.Up, 150.0, 400.0)
        fx(ui.children().size) shouldBe 1
        // the weights share the room as it was left, their total kept
        val w = m.weights.map(_())
        w.sum shouldBe (4.0 +- 1e-9)
        w(0) / w(2) shouldBe (192.0 / 252.0 +- 1e-9)
        // the second handle, between the second and the third
        val h2 = m.stack.handles(1)
        pointer(h2, PointerKind.Down, 150.0, 284.0)
        pointer(h2, PointerKind.Move, 150.0, 234.0)
        pointer(h2, PointerKind.Up, 150.0, 234.0)
        heights(m).zip(Vector(192.0, 60.0, 252.0)).foreach(p => p._1 shouldBe (p._2 +- 1e-6))
        pointer(h2, PointerKind.Down, 150.0, 284.0)
        pointer(h2, PointerKind.Move, 150.0, 334.0)
        pointer(h2, PointerKind.Up, 150.0, 334.0)
        heights(m).zip(Vector(192.0, 110.0, 202.0)).foreach(p => p._1 shouldBe (p._2 +- 1e-6))
        // folded and opened again, the first comes back as it was left
        fx(m.closed(0) @= true)
        fx(m.closed(0) @= false)
        heights(m).zip(Vector(192.0, 110.0, 202.0)).foreach(p => p._1 shouldBe (p._2 +- 1e-6))
        // a click on a handle that drags nothing is the header's under it
        val y = fx(h2.absoluteTop + h2.size.height() - 1.0)
        pointer(h2, PointerKind.Down, 150.0, y)
        pointer(h2, PointerKind.Up, 150.0, y)
        pointer(h2, PointerKind.Click, 150.0, y)
        m.closed(2)() shouldBe true
      }
    }
    "be laid out again as the app's Vars change, and made again from them as they were kept" in {
      val m = fx(new Made)
      onScreen(m) {
        fx(m.weights(2) @= 4.0)
        heights(m) shouldBe Vector(84.0, 84.0, 336.0)
        fx(m.closed(0) @= true)
        heights(m).zip(Vector(0.0, 100.8, 403.2)).foreach(p => p._1 shouldBe (p._2 +- 0.5))
        val again = fx(new Made(m.closed, m.weights))
        fx(ui.children.clear())
        onScreen(again) {
          heights(again).zip(Vector(0.0, 100.8, 403.2)).foreach(p => p._1 shouldBe (p._2 +- 0.5))
        }
      }
    }
    "fold on a click on its header or Enter or Space with it focused, a click on an action running it alone" in {
      val m = fx(new Made)
      onScreen(m) {
        click(m.panes(2).header, 40.0)
        m.closed(2)() shouldBe true
        fx(m.panes(2).header.semantics()) shouldBe everyui.component.types.Semantics.disclosure("Pane 2", open = false)
        fx(m.panes(2).body.semantics().role) shouldBe "region"
        fx(m.panes(2).body.semantics().label) shouldBe "Pane 2"
        fx(Focus.focused()) shouldBe Some(m.panes(2).header)
        press("Enter")
        m.closed(2)() shouldBe false
        fx(m.panes(2).header.semantics().expanded) shouldBe Some(true)
        press(" ")
        m.closed(2)() shouldBe true
        // a click on the second pane's action, always shown: run, nothing folded
        val start = fx(m.panes(1).buttons()(0))
        fx(start.visible()) shouldBe true
        click(start)
        m.ran shouldBe List("start")
        m.closed(1)() shouldBe false
        // the header's own click at the action's place, as where running it outlasted the click's climb, folds nothing
        Thread.sleep(everyui.event.Pointer.hold.toLong * 2L)
        fx {
          val x = start.absoluteLeft + 14.0
          val y = start.absoluteTop + 14.0
          val e = PointerEvent(PointerKind.Click, x, y)
          e.consume()
          m.panes(1).header.event.click @= e
        }
        m.closed(1)() shouldBe false
      }
    }
    "show its actions with the focus or the pointer in its header, or always where they say, the title cut short of them" in {
      val m = fx(new Made)
      onScreen(m) {
        fx(m.panes(0).buttons().map(_.visible())) shouldBe Vector(false, false)
        fx(m.panes(0).header.focus())
        fx(m.panes(0).buttons().map(_.visible())) shouldBe Vector(true, true)
        fx(m.panes(0).buttons().map(_.tooltip())) shouldBe Vector("New", "Refresh")
        fx(m.panes(0).buttons().map(_.semantics().label)) shouldBe Vector("New", "Refresh")
        // at the header's end, in order, 4 from it
        fx(m.panes(0).buttons().map(_.absoluteLeft)) shouldBe Vector(300.0 - 4.0 - 56.0, 300.0 - 4.0 - 28.0)
        fx(m.panes(1).buttons()(0).visible()) shouldBe true
        // a long title is cut short of the actions' room, whether they show or not
        fx(m.panes(0).title @= "A title far too long to fit in the header of a pane of a sidebar")
        fx(m.panes(0).titleText.absoluteLeft + m.panes(0).titleText.size.width()) should be <= 300.0 - 4.0 - 56.0 - 8.0 + 0.5
        fx(m.panes(0).titleText.elided()) shouldBe true
        // a pane's chevron, icon and title from the start
        fx(m.panes(0).titleText.absoluteLeft) shouldBe 54.0
        fx(m.panes(1).titleText.absoluteLeft) shouldBe 30.0
      }
    }
    "move the focus among the headers, into the panes and out, and onto the actions by Right" in {
      val m = fx(new Made)
      onScreen(m) {
        fx(m.panes(0).header.focus())
        press("ArrowDown")
        fx(Focus.focused()) shouldBe Some(m.cards(0))
        press("ArrowDown")
        fx(Focus.focused()) shouldBe Some(m.panes(1).header)
        press("ArrowUp")
        fx(Focus.focused()) shouldBe Some(m.cards(0))
        press("ArrowUp")
        fx(Focus.focused()) shouldBe Some(m.panes(0).header)
        // Right onto the actions, one by one, and Left back
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(m.panes(0).buttons()(0))
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(m.panes(0).buttons()(1))
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(m.panes(0).buttons()(1))
        press("Enter")
        m.ran shouldBe List("refresh")
        m.closed(0)() shouldBe false
        press("ArrowLeft")
        press("ArrowLeft")
        fx(Focus.focused()) shouldBe Some(m.panes(0).header)
        // Down from an action as from its header
        press("ArrowRight")
        press("ArrowDown")
        fx(Focus.focused()) shouldBe Some(m.cards(0))
        // a folded pane is passed into nothing: Down from its header to the next
        fx(m.panes(1).header.focus())
        press("Enter")
        m.closed(1)() shouldBe true
        press("ArrowDown")
        fx(Focus.focused()) shouldBe Some(m.panes(2).header)
        press("ArrowUp")
        fx(Focus.focused()) shouldBe Some(m.panes(1).header)
        // Up from the first header is left to what the stack is in
        fx(m.panes(0).header.focus())
        press("ArrowUp")
        fx(Focus.focused()) shouldBe Some(m.panes(0).header)
        // a pane folded with the focus in it hands the focus to its header
        fx(m.cards(2).focus())
        fx(m.closed(2) @= true)
        fx(Focus.focused()) shouldBe Some(m.panes(2).header)
      }
    }
    "mirror its headers right to left, the handles dragging the same way" in {
      val m = fx(new Made)
      fx(Direction.set(true))
      onScreen(m) {
        fx(m.panes(0).header.focus())
        // the chevron, the icon and the title from the right, the actions from the left
        fx(m.panes(0).titleText.absoluteLeft + m.panes(0).titleText.size.width()) shouldBe 300.0 - 54.0
        fx(m.panes(0).buttons().map(_.absoluteLeft)) shouldBe Vector(4.0 + 28.0, 4.0)
        // Left onto the actions
        press("ArrowLeft")
        fx(Focus.focused()) shouldBe Some(m.panes(0).buttons()(0))
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(m.panes(0).header)
        val h = m.stack.handles(0)
        pointer(h, PointerKind.Down, 150.0, 158.0)
        pointer(h, PointerKind.Move, 150.0, 198.0)
        pointer(h, PointerKind.Up, 150.0, 198.0)
        heights(m) shouldBe Vector(166.0, 86.0, 252.0)
      }
    }
    "glide as a pane folds, and fold at once at Minimal" in {
      val m = fx(new Made)
      fx(m.stack.glide @= 200.0)
      onScreen(m) {
        fx(m.closed(0) @= true)
        // under way, a frame at a time: neither where it was nor where it is going
        (1 to 4).foreach(_ => fx(ui.update(1.0 / 60.0)))
        val first = fx(m.stack.heights()(0))
        first should (be > 0.0 and be < 126.0)
        (1 to 30).foreach(_ => fx(ui.update(1.0 / 60.0)))
        fx(m.stack.heights()) shouldBe fx(m.stack.target())
        heights(m) shouldBe Vector(0.0, 168.0, 336.0)
        fx(Quality.start(Some(QualityLevel.Minimal)))
        try {
          fx(m.closed(0) @= false)
          fx(m.stack.heights()) shouldBe Vector(126.0, 126.0, 252.0)
        }
        finally fx(Quality.forget())
      }
    }
    "be let go of once disposed, though the app keeps its Vars, mid-drag and mid-glide" in {
      // what the app keeps, which outlives the stack
      val closed = Vector(Var(false), Var(false), Var(false))
      val weights = Vector(Var(1.0), Var(1.0), Var(2.0))
      val other = fx(card())
      val held = fx {
        ui.children.clear()
        val m = new Made(closed, weights)
        m.stack.glide @= 5000.0
        m.stack.size.width @= 300.0
        m.stack.size.height @= 600.0
        ui.children ++= List(m.stack, other)
        m.panes(0).header.focus()
        val parts: List[Component] = m.stack :: m.panes.toList ++ m.cards ++ m.stack.handles ++ m.panes.flatMap(_.buttons())
        // a handle held, and a glide under way
        m.stack.handles(0).event.pointer.down @= PointerEvent(PointerKind.Down, 150.0, 158.0)
        m.stack.handles(0).event.pointer.move @= PointerEvent(PointerKind.Move, 150.0, 170.0)
        closed(2) @= true
        parts.map(p => new WeakReference(p))
      }
      fx(ui.children().size) shouldBe 3   // the catcher, while the handle is held
      fx {
        val stack = ui.children().head
        ui.children -= stack
        stack.dispose()
        other.focus()
      }
      fx(ui.children().size) shouldBe 1
      fx(Focus.focused @= Some(other))
      def count = held.count(_.get() != null)
      val until = System.currentTimeMillis() + 5000L
      while (count > 0 && System.currentTimeMillis() < until) {
        fx(())
        System.gc()
        Thread.sleep(50L)
      }
      if (count > 0) LeakPaths.find(held.flatMap(r => Option(r.get()))).foreach(p => info("held: " + p))
      count shouldBe 0
      // and they still change, heard by nothing
      fx(closed(0) @= true)
      fx(weights(0) @= 3.0)
      fx(ui.children.clear())
    }
  }
}
