package everyui.app

import everyui.Color
import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, ButtonStyle, Component, Container, Row, TextView}
import everyui.component.types.FontWeight
import everyui.draw.{Drawing, Figure, Path}
import everyui.graph.{EdgeLook, GraphFlow, GraphView, NodeLook}
import everyui.net.*
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** The graph demo's one screen (GraphDemo): a bar with the graph's name, the module chosen, and the flow, Add planned
  * and Fit, over the graph, with a key to its colours and lines in the corner. */
class GraphScreen extends UIScreen {
  override def manager: ScreenManager = GraphDemo
  override def title: String = "Module dependencies"
  override def path: URLPath = path"/graph"

  private val groups = List("interface", "domain", "data", "platform")
  private val hues = List(210.0, 150.0, 35.0, 275.0)

  private val modules: Var[Vector[DemoModule]] = Var(Vector.empty)
  private val dependencies: Var[Vector[DemoDependency]] = Var(Vector.empty)
  private val graph = new GraphView[DemoModule, DemoDependency](modules, dependencies, _.name, _.name, _.from, _.to)
  private val said = Var("")
  private var added = 0

  override def defaultFocus(): Component = graph

  private def colour(group: String): Color = {
    val i = math.max(0, groups.indexOf(group))
    Color.fromHSB(hues(i) / 360.0, 0.6, if (Theme.palette().background.brightness < 0.5) 0.9 else 0.7)
  }

  override def createUI(): Task[Unit] = Task {
    // a phone's screen: the graph runs down it, and the buttons go under the name
    val narrow = container.size.width() < 600.0
    val bar = if (narrow) 100.0 else 56.0
    graph.position.top @= bar
    graph.size.width := container.size.width()
    graph.backgroundColor := Theme.background()
    graph.description @= "Module dependencies"
    graph.detailOf @= (m => Some(if (m.planned) "planned" else m.files.toString + " files"))
    graph.nodeLook @= (m => NodeLook(accent = Some(colour(m.group)), dashed = m.planned))
    graph.edgeLook @= (d =>
      if (d.violation) EdgeLook(color = Some(Theme.danger()), width = 2.5)
      else EdgeLook(dashed = d.planned))
    val asked = Platform.launchArg("flow")
    if (asked == "down" || (narrow && asked != "across")) graph.flow @= GraphFlow.TopToBottom
    said @= "Choose a module to light what it depends on and what depends on it"
    graph.lit.attach(k => said @= k.flatMap(describe).getOrElse(said()))
    graph.selectedEdge.attach(_.foreach(d => said @= d.from + " depends on " + d.to +
      (if (d.violation) ": the domain must not depend on the interface" else if (d.planned) " (planned)" else "")))
    graph.nodeClicked.attach(m => Platform.log("graph chose " + m.name))

    val title = TextView.line("Module dependencies", 18.0, Theme.text(), FontWeight.SemiBold)
    val status = TextView.line(said(), 12.0, Theme.muted(), widest = math.max(80.0, container.size.width() - 560.0))
    lazy val turn: Button = Button.bound(if (graph.flow() == GraphFlow.LeftToRight) "Top to bottom" else "Left to right",
      () => graph.flow @= (if (graph.flow() == GraphFlow.LeftToRight) GraphFlow.TopToBottom else GraphFlow.LeftToRight),
      style = ButtonStyle.Outlined)
    val add = new Button("Add planned", () => addPlanned(), style = ButtonStyle.Outlined)
    val fit = new Button("Fit", () => graph.fit(), style = ButtonStyle.Outlined)
    val buttons = Row(8.0, middle = true)(turn, add, fit)
    val legend = key(narrow)
    // the graph between the bar and the key under it
    graph.size.height := container.size.height() - bar - legend.size.height() - 20.0
    title.position.left @= 16.0
    title.position.top := (56.0 - title.size.height()) / 2.0
    status.position.left := title.position.left() + title.size.width() + 16.0
    status.position.top := (56.0 - status.size.height()) / 2.0
    status.visible := container.size.width() >= 760.0
    buttons.position.left := (if (narrow) 12.0 else container.size.width() - buttons.size.width() - 12.0)
    buttons.position.top := (if (narrow) 56.0 else (56.0 - buttons.size.height()) / 2.0)
    legend.position.left @= 12.0
    legend.position.top := container.size.height() - legend.size.height() - 10.0
    container.children ++= List(graph, title, status, buttons, legend)
  }

  // the graph given once the screen is showing, so its words are measured on the page
  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    if (modules().isEmpty) {
      val big = Platform.launchArg("big").toIntOption.getOrElse(0)
      if (big > 0) made(big) else project()
      // (laid out on the next turn, once for the nodes and the edges together)
      Platform.later(() => Platform.log("graph ok " + modules().length.toString + " nodes, " +
        dependencies().length.toString + " edges, laid out in " + math.round(graph.layoutMillis).toString + " ms" +
        graph.plan.map(p => ", " + p.crossings.toString + " crossings").getOrElse("")))
    }
  }

  private def describe(key: String): Option[String] = graph.nodeFor(key).map { m =>
    val (in, out) = graph.neighbours(key)
    m.name + (if (m.planned) " (planned)" else "") + ": used by " + in.length.toString + ", depends on " +
      out.length.toString
  }

  private def project(): Unit = {
    modules @= Vector(
      DemoModule("app", "interface", 4), DemoModule("screens", "interface", 38), DemoModule("widgets", "interface", 21),
      DemoModule("theme", "interface", 6), DemoModule("model", "domain", 17), DemoModule("rules", "domain", 9),
      DemoModule("search", "domain", 12), DemoModule("billing", "domain", 8), DemoModule("store", "data", 14),
      DemoModule("net", "data", 11), DemoModule("cache", "data", 5), DemoModule("sync", "data", 0, planned = true),
      DemoModule("files", "platform", 7), DemoModule("crypto", "platform", 4), DemoModule("log", "platform", 3),
      DemoModule("offline", "interface", 0, planned = true))
    dependencies @= Vector(
      DemoDependency("app", "screens"), DemoDependency("app", "theme"), DemoDependency("screens", "widgets"),
      DemoDependency("screens", "model"), DemoDependency("screens", "search"), DemoDependency("widgets", "theme"),
      DemoDependency("screens", "billing"), DemoDependency("search", "model"), DemoDependency("rules", "model"),
      DemoDependency("billing", "rules"), DemoDependency("search", "store"), DemoDependency("model", "store"),
      DemoDependency("store", "cache"), DemoDependency("store", "files"), DemoDependency("net", "crypto"),
      DemoDependency("billing", "net"), DemoDependency("store", "log"), DemoDependency("net", "log"),
      DemoDependency("model", "screens", violation = true),
      DemoDependency("sync", "store", planned = true), DemoDependency("sync", "net", planned = true),
      DemoDependency("offline", "sync", planned = true), DemoDependency("screens", "offline", planned = true))
  }

  // a made-up project of `n` modules, for a measure of a large graph
  private def made(n: Int): Unit = {
    var seed = 11L
    def next(bound: Int): Int = {
      seed = (seed * 1103515245L + 12345L) % 2147483648L
      // (the high bits: the low ones of this generator alternate)
      ((seed / 65536L) % bound.toLong).toInt
    }
    modules @= (0 until n).toVector.map(i => DemoModule("m" + i.toString, groups(i % 4), 1 + i % 30))
    dependencies @= (0 until n * 3 / 2).toVector.map { _ =>
      val a = next(n)
      DemoDependency("m" + a.toString, "m" + math.min(n - 1, a + 1 + next(math.max(1, n / 40))).toString)
    }.filter(d => d.from != d.to).distinct
  }

  private def addPlanned(): Unit = {
    added += 1
    val name = "plugin" + added.toString
    modules @= modules() :+ DemoModule(name, "domain", 0, planned = true)
    dependencies @= dependencies() ++ Vector(DemoDependency("screens", name, planned = true),
      DemoDependency(name, "rules", planned = true))
  }

  // the key: a dot for each group, a red line for a broken rule, a dashed one for what is planned
  private def key(narrow: Boolean): Container = {
    def entry(mark: Drawing, words: String): Container = Row(6.0, middle = true)(mark, TextView(words, 11.0, Theme.muted()))
    def dot(c: => Color): Drawing = {
      val d = new Drawing
      d.size.width @= 10.0
      d.size.height @= 10.0
      d.figures := List(Figure(Path.circle(5.0, 5.0, 4.5), fill = Some(c)))
      d
    }
    def line(c: => Color, dashed: Boolean): Drawing = {
      val d = new Drawing
      d.size.width @= 22.0
      d.size.height @= 10.0
      val p = Path.line(1.0, 5.0, 21.0, 5.0)
      d.figures := List(Figure(if (dashed) p.dashed(4.0, 3.0) else p, stroke = Some(c), width = 2.0))
      d
    }
    val colours = groups.map(g => entry(dot(colour(g)), g))
    val lines = List(entry(line(Theme.danger(), false), "breaks a rule"), entry(line(Theme.muted(), true), "planned"))
    // on a phone, the lines under the colours
    val row = if (narrow) everyui.component.Column(6.0)(Row(14.0, middle = true)(colours*),
        Row(14.0, middle = true)(lines*))
      else Row(14.0, middle = true)((colours ++ lines)*)
    new Container {
      backgroundColor := Theme.surface().withAlpha(0.9)
      radius @= 8.0
      borderWidth @= 1.0
      borderColor := Theme.border()
      size.width := row.size.width() + 20.0
      size.height := row.size.height() + 12.0
      row.position.left @= 10.0
      row.position.top @= 6.0
      children += row
    }
  }
}
