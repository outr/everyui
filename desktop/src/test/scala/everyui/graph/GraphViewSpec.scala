package everyui.graph

import everyui.component.Component
import everyui.draw.Drawing
import everyui.focus.Focus
import everyui.{Color, FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** A graph on the desktop's real nodes: its boxes and edges from the app's lists, laid out again as they change, a
  * click on a node and on an edge, the neighbours lit, the arrows among the nodes, and the looks. */
class GraphViewSpec extends FxSpec {
  case class Mod(name: String, planned: Boolean = false)
  case class Dep(from: String, to: String, bad: Boolean = false)

  private def graph(mods: Vector[Mod], deps: Vector[Dep]): (GraphView[Mod, Dep], Var[Vector[Mod]], Var[Vector[Dep]]) = fx {
    val ns = Var(mods)
    val es = Var(deps)
    val g = new GraphView[Mod, Dep](ns, es, _.name, _.name, _.from, _.to)
    g.glides @= false
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

  private def click(g: GraphView[?, ?], x: Double, y: Double): Unit = fx {
    val (sx, sy) = g.toScreen(x, y)
    List(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED).foreach { kind =>
      javafx.event.Event.fireEvent(g.node.fx, new MouseEvent(kind, sx + 10.0, sy + 20.0, sx, sy, MouseButton.PRIMARY, 1,
        false, false, false, false, true, false, false, false, false, true, null))
    }
  }

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private val mods = Vector(Mod("app"), Mod("ui"), Mod("core"), Mod("store"), Mod("sync", planned = true))
  private val deps = Vector(Dep("app", "ui"), Dep("app", "core"), Dep("ui", "core"), Dep("core", "store"),
    Dep("core", "ui", bad = true), Dep("sync", "store"))

  "A graph view" should {
    "draw a box for each node and its edges, laid out in layers" in {
      val (g, _, _) = graph(mods, deps)
      onScreen(g) {
        val plan = fx(g.plan.get)
        plan.nodes.length shouldBe 5
        fx(g.componentAt("app").exists(_.isInstanceOf[GraphNode])) shouldBe true
        // the boxes, and a drawing of the edges leaving each layer
        fx(g.showing) should be > 5
        plan.routes.count(_.reversed) shouldBe 1
        fx(g.neighbours("core")) shouldBe ((List("app", "ui"), List("store", "ui")))
        fx(g.semantics().role) shouldBe "application"
      }
    }
    "choose a node on a click, light its neighbours, and choose an edge on a click on its line" in {
      val (g, _, _) = graph(mods, deps)
      var clicked: List[String] = Nil
      var edges: List[Dep] = Nil
      fx {
        g.nodeClicked.attach(m => clicked = m.name :: clicked)
        g.edgeClicked.attach(d => edges = d :: edges)
      }
      onScreen(g) {
        val plan = fx(g.plan.get)
        val store = plan.nodes(3)
        click(g, store.centerX, store.centerY)
        clicked shouldBe List("store")
        fx(g.selected()) shouldBe Some("store")
        fx(g.lit()) shouldBe Some("store")
        // app is no neighbour of store's: it fades; core is
        fx(g.componentAt("app").get.opacity()) shouldBe 0.3
        fx(g.componentAt("core").get.opacity()) shouldBe 1.0
        // the middle of the edge from sync to store
        val route = plan.routes(5).points
        val mid = ((route.head._1 + route.last._1) / 2.0, (route.head._2 + route.last._2) / 2.0)
        click(g, mid._1, mid._2)
        edges shouldBe List(Dep("sync", "store"))
        fx(g.selected()) shouldBe None
        fx(g.selectedEdge()) shouldBe Some(Dep("sync", "store"))
      }
    }
    "move among the nodes with the arrows, following edges along the flow" in {
      val (g, _, _) = graph(mods, deps)
      onScreen(g) {
        fx {
          g.focus()
          g.selected @= Some("app")
        }
        press(KeyCode.RIGHT)
        val next = fx(g.selected())
        next.flatMap(k => fx(g.plan.get).layerOf.lift(Vector("app", "ui", "core", "store", "sync").indexOf(k))) shouldBe Some(1)
        press(KeyCode.LEFT)
        fx(g.selected()) shouldBe Some("app")
        // nothing before app: the arrow is the focus layer's
        fx(g.keepsEvent(everyui.event.KeyEvent(everyui.event.Key.ArrowLeft, "ArrowLeft"))) shouldBe false
        var activated: List[String] = Nil
        fx(g.activated.attach(m => activated = m.name :: activated))
        press(KeyCode.ENTER)
        activated shouldBe List("app")
      }
    }
    "lay out again as the lists change, and draw its looks" in {
      val (g, ns, es) = graph(mods, deps)
      onScreen(g) {
        fx(ns @= ns() :+ Mod("docs"))
        fx(es @= es() :+ Dep("docs", "app"))
        fx(g.plan.get.nodes.length) shouldBe 6
        fx(g.plan.get.layerOf(5)) shouldBe 0
        fx(g.componentAt("docs").nonEmpty) shouldBe true
        fx(g.edgeLook @= (d => if (d.bad) EdgeLook(color = Some(Color.Red), dashed = true) else EdgeLook()))
        val drawings = fx(ui.children().head.isInstanceOf[GraphView[?, ?]])
        drawings shouldBe true
        // the violation's line is cut into dashes, in red
        val figures = fx(List("0", "1", "2", "3").flatMap(l => g.componentAt(" edges " + l).toList)
          .flatMap(_.asInstanceOf[Drawing].figures()))
        figures.exists(f => f.stroke.contains(Color.Red) && f.path.ops.count(_.kind == "M") > 2) shouldBe true
      }
    }
    "glide to a new layout" in {
      val (g, ns, _) = graph(mods, deps)
      onScreen(g) {
        fx(g.glides @= true)
        val was = fx(g.boxOf("app").get)
        fx(ns @= Mod("first") +: ns())
        val will = fx(g.plan.get.nodes(1))
        // the frames an app's screen manager would run, a tenth of a second each
        fx(ui.update(0.1))
        val on = fx(g.boxOf("app").get)
        on should not be was
        on should not be will
        (1 to 5).foreach(_ => fx(ui.update(0.1)))
        fx(g.boxOf("app")) shouldBe Some(will)
      }
    }
  }
}
