package everyui.app

import everyui.Color
import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, ButtonStyle, Component, Container, Row, TextView}
import everyui.component.types.FontWeight
import everyui.draw.{Drawing, Figure, Path}
import everyui.graph.{EdgeLook, ForceEdge, ForceLayout, ForceNode, GraphMode, GraphView, NodeLook}
import everyui.net.*
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** The force graph demo's one screen (ForceGraphDemo): a bar with its name, the word lit, and Add, Remove, Shake and
  * Fit, over the graph, with a key to the topics' colours in the corner. */
class ForceGraphScreen extends UIScreen {
  override def manager: ScreenManager = ForceGraphDemo
  override def title: String = "Words seen together"
  override def path: URLPath = path"/forcegraph"

  private val topics = Vector("kitchen", "travel", "devices", "music", "garden", "weather", "health", "money")
  private val hues = Vector(28.0, 200.0, 250.0, 320.0, 120.0, 180.0, 0.0, 50.0)
  private val lists = Vector(
    "tea coffee milk sugar bread butter cheese apple lemon honey garlic onion pepper salt rice pasta tomato basil olive flour egg cream soup oven knife spoon kettle jam oats yogurt",
    "train ticket station airport hotel map passport luggage beach museum harbour ferry bridge castle market taxi bus route border visa hostel tram platform delay gate journey city village coast island",
    "laptop phone battery charger screen keyboard cable update password router cloud backup server browser app email printer camera bluetooth wifi tablet mouse drive code bug release login sync storage network",
    "guitar piano drums song album concert band chorus melody rhythm violin choir lyrics tempo stage vinyl playlist singer bass jazz opera festival record tune harmony rehearsal speaker microphone headphones radio",
    "rose tulip seed soil compost hedge lawn shovel rake bee butterfly pond fence shed daisy ivy oak maple pine fern moss bulb hose weed pot greenhouse orchard blossom berry vine",
    "rain snow wind storm sun fog frost thunder lightning umbrella forecast heat drizzle hail rainbow breeze gale flood drought sleet mist humidity pressure sunset sunrise winter summer autumn spring dew",
    "doctor nurse clinic fever cough sleep walk run yoga water vitamin pill appointment dentist back knee stretch diet salad gym swim headache allergy rest pulse therapy bandage cold flu massage",
    "rent bill bank budget salary tax invoice receipt loan savings card cash coin price discount refund insurance mortgage pension wallet account transfer deposit interest expense payment fee balance shares bonus")
  private val extras = Vector("picnic", "sketch", "puzzle", "lantern", "compass", "ribbon", "marble", "velvet", "canvas",
    "anchor", "candle", "blanket", "ladder", "mirror", "basket")

  private val words: Var[Vector[DemoWord]] = Var(Vector.empty)
  private val pairs: Var[Vector[DemoPair]] = Var(Vector.empty)
  private val graph = new GraphView[DemoWord, DemoPair](words, pairs, _.text, _.text, _.a, _.b)
  private val said = Var("")
  private val random = new scala.util.Random(5)
  private var added = 0
  private var started = 0.0
  private var logged = false

  override def defaultFocus(): Component = graph

  private def colour(topic: Int): Color =
    Color.fromHSB(hues(topic % hues.length) / 360.0, 0.6, if (Theme.palette().background.brightness < 0.5) 0.9 else 0.7)

  override def createUI(): Task[Unit] = Task {
    // a phone's screen: the buttons go under the name
    val narrow = container.size.width() < 600.0
    val bar = if (narrow) 100.0 else 56.0
    graph.mode @= GraphMode.Force
    // words seen together: ties both ways, with no arrowheads
    graph.directed @= false
    graph.position.top @= bar
    graph.size.width := container.size.width()
    graph.backgroundColor := Theme.background()
    graph.description @= "Words seen together"
    graph.detailOf @= (w => Some(w.count.toString + (if (w.count == 1) " note" else " notes")))
    graph.nodeLook @= (w => NodeLook(accent = Some(colour(w.topic))))
    // a common word pushes harder; a pair seen together often pulls harder, and is drawn heavier
    graph.weightOf @= (w => 0.6 + w.count / 20.0)
    graph.strengthOf @= (p => 0.3 + math.min(1.0, p.together / 12.0))
    graph.edgeLook @= (p => EdgeLook(width = 1.0 + math.min(3.0, p.together / 4.0)))
    said @= "Hover or choose a word to light the words it is seen with; drag one to move it"
    graph.lit.attach(k => said @= k.flatMap(describe).getOrElse(said()))
    graph.nodeClicked.attach(w => Platform.log("forcegraph chose " + w.text))
    graph.forceStepped.attach { _ =>
      if (!logged && graph.force.settled) {
        logged = true
        Platform.log("forcegraph settled in " + graph.force.tickCount.toString + " ticks, " +
          math.round(Platform.ticks() - started).toString + " ms")
        if (Platform.launchArg("measure") == "1") Platform.later(() => measure())
      }
    }

    val title = TextView.line("Words seen together", 18.0, Theme.text(), FontWeight.SemiBold)
    val status = TextView.line(said(), 12.0, Theme.muted(), widest = math.max(80.0, container.size.width() - 600.0))
    val add = new Button("Add", () => addWord(), style = ButtonStyle.Outlined)
    val remove = new Button("Remove", () => removeWord(), style = ButtonStyle.Outlined)
    val shake = new Button("Shake", () => graph.force.reheat(0.6), style = ButtonStyle.Outlined)
    val fit = new Button("Fit", () => graph.fit(), style = ButtonStyle.Outlined)
    val buttons = Row(8.0, middle = true)(add, remove, shake, fit)
    val legend = key(narrow)
    graph.size.height := container.size.height() - bar - legend.size.height() - 20.0
    title.position.left @= 16.0
    title.position.top := (56.0 - title.size.height()) / 2.0
    status.position.left := title.position.left() + title.size.width() + 16.0
    status.position.top := (56.0 - status.size.height()) / 2.0
    status.visible := container.size.width() >= 800.0
    buttons.position.left := (if (narrow) 12.0 else container.size.width() - buttons.size.width() - 12.0)
    buttons.position.top := (if (narrow) 56.0 else (56.0 - buttons.size.height()) / 2.0)
    legend.position.left @= 12.0
    legend.position.top := container.size.height() - legend.size.height() - 10.0
    container.children ++= List(graph, title, status, buttons, legend)
  }

  // the words given once the screen is showing, so they are measured on the page
  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    if (words().isEmpty) {
      val (ws, ps) = notes(Platform.launchArg("n").toIntOption.getOrElse(240), random)
      words @= ws
      pairs @= ps
      started = Platform.ticks()
      Platform.log("forcegraph ok " + ws.length.toString + " words, " + ps.length.toString + " pairs")
    }
  }

  private def describe(key: String): Option[String] = graph.nodeFor(key).map { w =>
    val (in, out) = graph.neighbours(key)
    val seen = (in ++ out).distinct
    w.text + ": in " + w.count.toString + " notes, seen with " + seen.length.toString +
      (if (seen.isEmpty) "" else " (" + seen.take(4).mkString(", ") + (if (seen.length > 4) ", ..." else "") + ")")
  }

  // `n` words from the topics' lists (numbered past them), each in some notes; each seen with two or three of its own
  // topic, most often its commoner ones, and one in eight with a word of another topic too
  private def notes(n: Int, r: scala.util.Random): (Vector[DemoWord], Vector[DemoPair]) = {
    val all = lists.map(_.split(" ").toVector)
    val ws = (0 until n).toVector.map { i =>
      val topic = i % topics.length
      val list = all(topic)
      val at = i / topics.length
      val text = if (at < list.length) list(at) else list(at % list.length) + " " + (at / list.length + 1).toString
      // the first few of each topic the commonest
      DemoWord(text, topic, 1 + r.nextInt(6) + 40 / (at + 2))
    }
    val byTopic = ws.indices.groupBy(i => ws(i).topic)
    var seen: Set[(Int, Int)] = Set.empty
    var ps: Vector[DemoPair] = Vector.empty
    def link(a: Int, b: Int, together: Int): Unit = if (a != b && !seen.contains((a, b)) && !seen.contains((b, a))) {
      seen = seen + ((a, b))
      ps = ps :+ DemoPair(ws(a).text, ws(b).text, together)
    }
    ws.indices.foreach { i =>
      val mates = byTopic(ws(i).topic)
      (0 until 2 + r.nextInt(2)).foreach { _ =>
        // (a square leans towards the start of the list, where the common words are)
        val pick = mates((r.nextDouble() * r.nextDouble() * mates.length).toInt)
        link(i, pick, 1 + r.nextInt(1 + math.min(ws(i).count, ws(pick).count) / 2))
      }
      if (r.nextInt(8) == 0) link(i, r.nextInt(ws.length), 1 + r.nextInt(2))
    }
    (ws, ps)
  }

  // a word brought in, seen with three of one topic
  private def addWord(): Unit = {
    val topic = random.nextInt(topics.length)
    val text = if (added < extras.length) extras(added) else "note " + (added + 1).toString
    added += 1
    val mates = words().filter(_.topic == topic)
    val with3 = (0 until 3).map(_ => mates(random.nextInt(mates.length)).text).distinct
    words @= words() :+ DemoWord(text, topic, 3)
    pairs @= pairs() ++ with3.map(m => DemoPair(text, m, 2 + random.nextInt(4)))
    // (chosen once it is laid out, on the next turn, so what it is seen with is known)
    Platform.later(() => graph.selected @= Some(text))
    graph.reveal(text)
  }

  // the chosen word taken out, with its pairs; or, with none chosen, the last one
  private def removeWord(): Unit = graph.selected().orElse(words().lastOption.map(_.text)).foreach { text =>
    words @= words().filterNot(_.text == text)
    pairs @= pairs().filterNot(p => p.a == text || p.b == text)
    graph.selected @= None
    said @= text + " taken out"
  }

  // settled at once in plain layouts of 100, 1,000 and 3,000 words, for how long this platform takes
  private def measure(): Unit = List(100, 1000, 3000).foreach { n =>
    val (ws, ps) = notes(n, new scala.util.Random(n.toLong))
    val layout = new ForceLayout
    layout.update(ws.map(w => ForceNode.circle(w.text, 6.0, 0.6 + w.count / 20.0)),
      ps.map(p => ForceEdge(p.a, p.b, 0.3 + math.min(1.0, p.together / 12.0))))
    val start = Platform.ticks()
    val ticks = layout.settle()
    val ms = Platform.ticks() - start
    Platform.log("forcegraph measure " + n.toString + " words, " + layout.edges.length.toString + " pairs: " +
      ticks.toString + " ticks in " + math.round(ms).toString + " ms, " + (math.round(ms / ticks * 100.0) / 100.0).toString +
      " ms a tick")
  }

  // the key: a dot in each topic's colour
  private def key(narrow: Boolean): Container = {
    def entry(topic: Int): Container = {
      val d = new Drawing
      d.size.width @= 10.0
      d.size.height @= 10.0
      d.figures := List(Figure(Path.circle(5.0, 5.0, 4.5), fill = Some(colour(topic))))
      Row(6.0, middle = true)(d, TextView(topics(topic), 11.0, Theme.muted()))
    }
    val entries = topics.indices.toList.map(entry)
    // on a phone, in two rows
    val row = if (narrow) everyui.component.Column(6.0)(Row(12.0, middle = true)(entries.take(4)*),
        Row(12.0, middle = true)(entries.drop(4)*))
      else Row(14.0, middle = true)(entries*)
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
