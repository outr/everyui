package everyui.graph

import everyui.component.Component
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** A graph laid out by forces on the desktop's real nodes: stepped on the frames and stopping when it settles, a node
  * dragged by the pointer, an added node settling in beside its neighbours, the arrows among the nodes, and back to
  * layers. */
class GraphForceSpec extends FxSpec {
  case class Word(name: String)
  case class Pair(a: String, b: String)

  private val words = Vector("tea", "milk", "sugar", "lemon", "honey", "coffee", "cream", "ice").map(Word(_))
  private val pairs = Vector(Pair("tea", "milk"), Pair("tea", "sugar"), Pair("tea", "lemon"), Pair("tea", "honey"),
    Pair("coffee", "milk"), Pair("coffee", "cream"), Pair("coffee", "sugar"), Pair("coffee", "ice"), Pair("lemon", "ice"))

  private def graph(): (GraphView[Word, Pair], Var[Vector[Word]], Var[Vector[Pair]]) = fx {
    val ns = Var(words)
    val es = Var(pairs)
    val g = new GraphView[Word, Pair](ns, es, _.name, _.name, _.a, _.b)
    g.mode @= GraphMode.Force
    g.position.left @= 10.0
    g.position.top @= 20.0
    g.size.width @= 800.0
    g.size.height @= 500.0
    (g, ns, es)
  }

  private def onScreen[T](c: Component)(test: => T): T = {
    fx(ui.children += c)
    try test finally fx {
      ui.children -= c
      Focus.focused @= None
    }
  }

  private def mouse(g: GraphView[?, ?], kind: javafx.event.EventType[MouseEvent], x: Double, y: Double): Unit = fx {
    javafx.event.Event.fireEvent(g.node.fx, new MouseEvent(kind, x + 10.0, y + 20.0, x, y, MouseButton.PRIMARY, 1,
      false, false, false, false, true, false, false, false, false, true, null))
  }

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private def overlapping(boxes: Vector[everyui.spatial.Rect]): Int =
    (for (i <- boxes.indices; j <- i + 1 until boxes.length) yield {
      val a = boxes(i)
      val b = boxes(j)
      a.x < b.right - 1.0 && b.x < a.right - 1.0 && a.y < b.bottom - 1.0 && b.y < a.bottom - 1.0
    }).count(identity)

  "A graph laid out by forces" should {
    "move a step each frame, draw where the layout has the nodes, and stop its frames when it settles" in {
      val (g, _, _) = graph()
      onScreen(g) {
        fx(g.force.animating) shouldBe true
        fx(g.plan.get.nodes.length) shouldBe 8
        val before = fx(g.force.tickCount)
        val was = fx(g.boxOf("tea").get)
        (1 to 5).foreach(_ => fx(ui.update(1.0 / 60.0)))
        fx(g.force.tickCount) shouldBe before + 5
        val now = fx(g.boxOf("tea").get)
        now should not be was
        // the box drawn is where the layout has the node
        val (x, y) = fx(g.force.positionOf("tea").get)
        now.centerX shouldBe x +- 1e-6
        now.centerY shouldBe y +- 1e-6
        // run to the end, a frame at a time: then it asks for none
        var frames = 0
        while (fx(g.force.animating) && frames < 1000) {
          fx(ui.update(1.0 / 60.0))
          frames += 1
        }
        info(s"settled after ${frames + 5} frames")
        fx(g.force.settled) shouldBe true
        fx(g.force.animating) shouldBe false
        fx(ui.needsFrames) shouldBe false
        overlapping(fx(g.plan.get.nodes)) shouldBe 0
        // the edges are straight, from box to box
        fx(g.plan.get.routes.forall(_.points.length == 2)) shouldBe true
      }
    }
    "drag a node, pinned while it is dragged and let go after, with no click" in {
      val (g, _, _) = graph()
      var clicked: List[String] = Nil
      fx(g.nodeClicked.attach(w => clicked = w.name :: clicked))
      onScreen(g) {
        fx(g.force.settle())
        fx(ui.update(1.0 / 60.0))
        val box = fx(g.boxOf("honey").get)
        val (sx, sy) = fx(g.toScreen(box.centerX, box.centerY))
        mouse(g, MouseEvent.MOUSE_PRESSED, sx, sy)
        mouse(g, MouseEvent.MOUSE_DRAGGED, sx + 40.0, sy + 10.0)
        mouse(g, MouseEvent.MOUSE_DRAGGED, sx + 120.0, sy + 60.0)
        fx(g.force.pinned("honey")) shouldBe true
        val scale = fx(g.view().scale)
        val (hx, hy) = fx(g.force.positionOf("honey").get)
        hx shouldBe box.centerX + 120.0 / scale +- 1e-6
        hy shouldBe box.centerY + 60.0 / scale +- 1e-6
        // the view did not pan, and the rest warm again to follow
        fx(g.force.settled) shouldBe false
        fx(g.force.animating) shouldBe true
        mouse(g, MouseEvent.MOUSE_RELEASED, sx + 120.0, sy + 60.0)
        mouse(g, MouseEvent.MOUSE_CLICKED, sx + 120.0, sy + 60.0)
        fx(g.force.pinned("honey")) shouldBe false
        clicked shouldBe Nil
        // a press that does not move still chooses the node
        val tea = fx(g.boxOf("tea").get)
        val (tx, ty) = fx(g.toScreen(tea.centerX, tea.centerY))
        mouse(g, MouseEvent.MOUSE_PRESSED, tx, ty)
        mouse(g, MouseEvent.MOUSE_RELEASED, tx, ty)
        mouse(g, MouseEvent.MOUSE_CLICKED, tx, ty)
        clicked shouldBe List("tea")
        fx(g.force.pinned("tea")) shouldBe false
      }
    }
    "start an added node beside its neighbours and keep the rest where they were" in {
      val (g, ns, es) = graph()
      onScreen(g) {
        fx(g.force.settle())
        val was = fx(words.map(w => g.force.positionOf(w.name).get))
        fx {
          ns @= ns() :+ Word("mint")
          es @= es() :+ Pair("mint", "tea")
        }
        val mint = fx(g.force.positionOf("mint").get)
        val tea = fx(g.force.positionOf("tea").get)
        math.hypot(mint._1 - tea._1, mint._2 - tea._2) should be < 300.0
        fx(g.plan.get.nodes.length) shouldBe 9
        // (the view does not follow once the app has it)
        fx(g.force.settle())
        val moved = fx(words.map(w => g.force.positionOf(w.name).get)).zip(was)
          .map { case (a, b) => math.hypot(a._1 - b._1, a._2 - b._2) }
        info(f"the rest moved by at most ${moved.max}%.1f")
        moved.max should be < 60.0
        overlapping(fx(g.force.nodes.indices.toVector.map(i => g.force.boxOf(i)))) shouldBe 0
      }
    }
    "move to the neighbour the arrow's way, and back to layers" in {
      val (g, _, _) = graph()
      onScreen(g) {
        fx {
          g.force.settle()
          g.focus()
          g.selected @= Some("tea")
        }
        val plan = fx(g.plan.get)
        val tea = plan.nodes(0)
        press(KeyCode.RIGHT)
        val next = fx(g.selected()).get
        val i = words.indexWhere(_.name == next)
        plan.nodes(i).centerX should be > tea.centerX
        fx(g.neighbours("tea")._2 ++ g.neighbours("tea")._1).contains(next) shouldBe
          fx((g.neighbours("tea")._2 ++ g.neighbours("tea")._1).exists(k => plan.nodes(words.indexWhere(_.name == k)).centerX > tea.centerX))
        fx(g.mode @= GraphMode.Layered)
        fx(g.force.animating) shouldBe false
        fx(g.plan.get.layers.length) should be > 1
      }
    }
  }
}
