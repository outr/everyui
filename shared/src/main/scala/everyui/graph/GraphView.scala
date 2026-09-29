package everyui.graph

import everyui.{Color, PlatformKind}
import everyui.component.Component
import everyui.component.types.Semantics
import everyui.draw.{Drawing, Figure, Hit, Path, PathOp}
import everyui.event.{Key, KeyEvent, Keys}
import everyui.render.Platform
import everyui.spatial.{Rect, Size}
import everyui.task.TaskInstance
import everyui.theme.Theme
import everyui.ui
import everyui.zoom.{ZoomCanvas, ZoomGrab, ZoomItem, ZoomPoint, ZoomView}
import reactify.*

/** A directed graph, laid out in layers and drawn on a canvas that pans and zooms ([[ZoomCanvas]]): its `nodes` and
  * `edges` are the app's lists, and it is laid out again whenever they change ([[GraphLayout]], in shared code), the
  * nodes gliding to their new places where there are no more than a few hundred.
  *
  * Each node has a key (`keyOf`), a label and, with `detailOf`, a second line under it, drawn in a box of the graph's
  * own ([[GraphNode]]) sized to its words, or by the app's `renderer` at the size `sizeOf` gives. Each edge runs from
  * the node keyed `fromOf` to the one keyed `toOf` (one to a node not in the list is left out), drawn as a curve between
  * the layers with an arrowhead. `nodeLook` gives a node an accent colour or a dashed outline (planned), and `edgeLook`
  * an edge its colour, a dash or its width (a rule broken, in red).
  *
  * A click on a node chooses it (`selected`, `nodeClicked`), a second one soon after activates it (`activated`), and
  * a click on an edge chooses that (`selectedEdge`, `edgeClicked`). The node under the pointer, else the one chosen, is
  * lit with its neighbours, in and out, and the edges between them, and everything else fades.
  *
  * With the focus, the arrows move among the nodes. This is the graph's own navigation, as a list's arrows move its
  * cursor, not the app's focus moving from one component to another (which EveryUI never does by position): along the
  * flow (Right and Left, or Down and Up top to bottom) an arrow follows an edge to the neighbour most in line, or where
  * there is none goes to the node most in line in the next layer that way; across it an arrow goes to the next node in
  * the layer. Enter activates the node, Escape lets it go, and the view follows. An arrow with no node its way leaves
  * the graph, as the focus layer says. + and - zoom and 0 fits, as on any canvas.
  *
  * With `mode` set to `GraphMode.Force` the nodes go where forces settle them instead ([[ForceLayout]]): each edge
  * pulls its ends together (as hard as `strengthOf`, to `lengthOf` apart), every node pushes the others away (more for
  * a greater `weightOf`), and no two overlap. The graph moves as the layout runs, a step a frame, and stops, frames
  * and all, when it settles; a change to the nodes or edges keeps each node where it was, starts a new one beside its
  * neighbours, and settles again gently. A node can be dragged (pinned while it is, and let go after), `force` is the
  * layout itself (to pin a node, reheat it, or settle it at once), and `forceSettings` how it moves. Edges are straight
  * lines with arrowheads, and the arrows move to the neighbour, else the nearest node, most the arrow's way.
  *
  * {{{
  *   val graph = new GraphView[Module, Dependency](modules, dependencies, _.name, _.name, _.from, _.to)
  *   graph.detailOf @= (m => Some(m.files.toString + " files"))
  *   graph.nodeLook @= (m => NodeLook(accent = Some(m.layer.colour), dashed = m.planned))
  *   graph.edgeLook @= (d => if (d.violation) EdgeLook(color = Some(Theme.danger()), width = 2.0) else EdgeLook())
  *   graph.activated.attach(m => open(m))
  * }}} */
class GraphView[N, E](val nodes: Var[Vector[N]], val edges: Var[Vector[E]], val keyOf: N => String,
                      val labelOf: N => String, val fromOf: E => String, val toOf: E => String) extends ZoomCanvas {
  override def kind: String = "GraphView"

  // the graph as last laid out: the nodes (the first of each key), where each is by key, the edges between them and
  // their ends, and each node's edges in and out
  private var present: Vector[N] = Vector.empty
  private var keyed: Map[String, Int] = Map.empty
  private var links: Vector[E] = Vector.empty
  private var ends: Vector[(Int, Int)] = Vector.empty
  private var arriving: Vector[List[Int]] = Vector.empty
  private var leaving: Vector[List[Int]] = Vector.empty
  private var laid: Option[GraphPlan] = None
  private var took: Double = 0.0
  // each node's value, which its box follows, by key
  private var valued: Map[String, Var[N]] = Map.empty
  // each edge's line in the world, and the box round it, for a click: made when one is looked for, after a layout
  private var worldLines: Vector[Path] = Vector.empty
  private var lineBoxes: Vector[Rect] = Vector.empty
  private var linesFresh = false
  // the force layout, whether the graph is laid out by it now, and whether the view follows it as it spreads out
  // (the first time, until the app or the user moves the view)
  private val simulation = new ForceLayout(GraphView.forces)
  private var forced = false
  private var following = false
  // a node to show once it is laid out
  private var revealing: Option[String] = None
  // the edges drawn together (those leaving each layer), in each bundle's own pixels: an edge, its line, its head
  private var bundles: Vector[Vector[(Int, Path, Path)]] = Vector.empty
  private val drawn: Var[Int] = own(Var(0))
  private var gliding: Option[TaskInstance] = None
  private var lastClick: Option[String] = None
  private var lastClickAt = 0.0
  private var arranging = false
  // each layer's nodes, for their outlines: a node and its box in the layer's own pixels
  private var outlined: Vector[Vector[(Int, Rect)]] = Vector.empty
  private var refit = false
  // the nodes new to the graph as it glides to a new layout, and how far in they have come
  private var newcomers: Set[String] = Set.empty
  private val arrival: Var[Double] = own(Var(1.0))

  /** Which way the edges run. */
  val flow: Var[GraphFlow] = own(Var(GraphFlow.LeftToRight))
  /** A second line under a node's label. */
  val detailOf: Var[N => Option[String]] = own(Var((_: N) => None))
  val nodeLook: Var[N => NodeLook] = own(Var((_: N) => NodeLook()))
  val edgeLook: Var[E => EdgeLook] = own(Var((_: E) => EdgeLook()))
  /** A node's size, in place of one worked out from its words (for the app's `renderer`). */
  val sizeOf: Var[Option[N => Size]] = own(Var(None))
  /** The app's own component for a node, in place of the graph's box. The graph fades it as it does its own. */
  val renderer: Var[Option[N => Component]] = own(Var(None))
  /** How far apart the layers are along the flow, and the nodes of a layer across it. */
  val layerGap: Var[Double] = own(Var(72.0))
  val nodeGap: Var[Double] = own(Var(20.0))
  /** Whether the nodes glide to their places when the graph is laid out again (up to 300 of them). */
  val glides: Var[Boolean] = own(Var(true))
  /** Whether edges are curves, or lines across and down (what a Roku or an Apple TV draws, which draw no curves). */
  val curved: Var[Boolean] = own(Var(GraphView.curves))
  /** How wide a node is on the screen, in pixels, before it is a box of its own with its words; narrower, it is an
    * outline among its layer's in one drawing. */
  val detailFrom: Var[Double] = own(Var(40.0))
  /** Whether the edges have a way, drawn with arrowheads; without, a graph whose edges are ties both ways (words seen
    * together) has plain lines. */
  val directed: Var[Boolean] = own(Var(true))
  /** What the graph is, to whatever reads the screen. */
  val description: Var[String] = own(Var("Graph"))

  /** How the nodes are laid out: in layers along the flow, or where forces settle them. */
  val mode: Var[GraphMode] = own(Var(GraphMode.Layered))
  /** How the force layout moves the nodes (`GraphMode.Force`). */
  val forceSettings: Var[ForceSettings] = own(Var(GraphView.forces))
  /** How hard a node pushes the others away in the force layout (1 by default; 2 twice as hard). */
  val weightOf: Var[N => Double] = own(Var((_: N) => 1.0))
  /** How hard an edge pulls its ends together in the force layout (1 by default), and how far apart, edge to edge
    * (the settings' `linkLength` where it says None). */
  val strengthOf: Var[E => Double] = own(Var((_: E) => 1.0))
  val lengthOf: Var[E => Option[Double]] = own(Var((_: E) => None))

  /** The force layout the graph uses in `GraphMode.Force`: to `pin` a node, `reheat` it, or `settle` it at once. */
  def force: ForceLayout = simulation
  /** Each step of the force layout, once it is drawn (`force.settled` says whether it was the last). */
  val forceStepped: Channel[Unit] = Channel[Unit]

  /** The key of the node chosen. */
  val selected: Var[Option[String]] = own(Var(None))
  val selectedEdge: Var[Option[E]] = own(Var(None))
  val nodeClicked: Channel[N] = Channel[N]
  val edgeClicked: Channel[E] = Channel[E]
  /** A node activated: clicked twice, or Enter on it. */
  val activated: Channel[N] = Channel[N]
  /** The key of the node lit with its neighbours: the one under the pointer, else the one chosen. */
  val lit: Val[Option[String]] = own(Val(hovered().map(_.key).filter(k => keyed.contains(k)).orElse(selected())))

  /** The layout drawn now, and how long it took, in milliseconds. */
  def plan: Option[GraphPlan] = laid
  def layoutMillis: Double = took

  /** The node keyed `key`, where it is in the graph. */
  def nodeFor(key: String): Option[N] = keyed.get(key).map(i => present(i))

  /** The keys of the nodes with an edge to `key` (in) and from it (out). */
  def neighbours(key: String): (List[String], List[String]) = keyed.get(key) match {
    case Some(i) => (arriving(i).map(e => keyOf(present(ends(e)._1))), leaving(i).map(e => keyOf(present(ends(e)._2))))
    case None => (Nil, Nil)
  }

  /** The view moved, gliding, to show the node keyed `key`, where it is out of sight: once it is laid out, for a node
    * the app has only just added. */
  def reveal(key: String): Unit =
    if (!keyed.contains(key)) revealing = Some(key)
    else keyed.get(key).flatMap(i => laid.map(_.nodes(i))).foreach { box =>
      val v = view()
      val (x, y) = toScreen(box.x, box.y)
      val room = margin()
      if (x < room || y < room || x + box.width * v.scale > size.width() - room ||
        y + box.height * v.scale > size.height() - room)
        moveTo(ZoomView(box.centerX - size.width() / 2.0 / v.scale, box.centerY - size.height() / 2.0 / v.scale,
          v.scale), animate = true)
    }

  semantics := Semantics("application", label = description())

  // ---- laying out ----

  // laid out once for all that changed in a turn: an app that sets the nodes and then the edges is laid out once, with both
  private def arrange(): Unit = if (!arranging) {
    arranging = true
    Platform.later(() => {
      arranging = false
      arrangeNow()
    })
  }

  private def arrangeNow(): Unit = {
    var index: Map[String, Int] = Map.empty
    var kept: Vector[N] = Vector.empty
    nodes().foreach { n =>
      val k = keyOf(n)
      if (!index.contains(k)) {
        index = index + (k -> kept.length)
        kept = kept :+ n
      }
    }
    val es = edges().filter(e => index.contains(fromOf(e)) && index.contains(toOf(e)))
    val pairs = es.map(e => (index(fromOf(e)), index(toOf(e))))
    val started = Platform.ticks()
    val wasForced = forced
    forced = mode() == GraphMode.Force
    val sizes = kept.map(boxOf)
    val next = if (forced) forceLayout(kept, sizes, es, pairs, wasForced)
      else {
        simulation.stop()
        GraphLayout.layout(sizes, pairs, flow(), layerGap(), nodeGap())
      }
    took = Platform.ticks() - started
    // where things were, to glide from
    val before = laid
    val wasKeyed = keyed
    val wasKeys = linkKeys(links)
    val wasRoutes: Map[String, GraphRoute] = before.map(p => links.indices.map(i => wasKeys(i) -> p.routes(i)).toMap)
      .getOrElse(Map.empty)
    // what each box shows follows its node; the app's own components are made again for a node that changed
    var values: Map[String, Var[N]] = Map.empty
    kept.foreach { n =>
      val k = keyOf(n)
      valued.get(k) match {
        case Some(v) =>
          if (v() != n) {
            v @= n
            if (renderer().nonEmpty) remake(k)
          }
          values = values + (k -> v)
        case None => values = values + (k -> Var(n))
      }
    }
    valued = values
    present = kept
    keyed = index
    links = es
    ends = pairs
    val into = GraphLayout.lists(kept.length)
    val outOf = GraphLayout.lists(kept.length)
    var e = pairs.length - 1
    while (e >= 0) {
      into(pairs(e)._2) = e :: into(pairs(e)._2)
      outOf(pairs(e)._1) = e :: outOf(pairs(e)._1)
      e -= 1
    }
    arriving = into.toVector
    leaving = outOf.toVector
    laid = Some(next)
    selected @= selected().filter(k => index.contains(k))
    selectedEdge @= selectedEdge().filter(e => es.contains(e))
    linesFresh = false
    gliding.foreach(_.cancel())
    gliding = None
    val from = before.map(p => kept.indices.toVector.map(i => wasKeyed.get(keyOf(kept(i))).map(j => p.nodes(j))
      .getOrElse(next.nodes(i))))
    from match {
      // (the force layout moves the nodes itself, a step a frame)
      case Some(old) if glides() && !forced && kept.length <= 300 && size.width() > 0.0 =>
        val keys = linkKeys(es)
        val routesBefore = es.indices.toVector.map(i => wasRoutes.get(keys(i)))
        newcomers = kept.map(n => keyOf(n)).filterNot(k => wasKeyed.contains(k)).toSet
        arrival @= 0.0
        gliding = Some(new GraphMove(0.4, t => {
          arrival @= t
          if (t >= 1.0) lay(next.nodes, next.routes)
          else lay(between(old, next.nodes, t), next.routes.indices.toVector.map(i =>
            routeBetween(routesBefore(i), next.routes(i), old, next.nodes, i, t)))
        }).start(ui))
      case _ =>
        newcomers = Set.empty
        lay(next.nodes, next.routes)
    }
    // a graph turned the other way is fitted in again
    if (refit) {
      refit = false
      fit()
    }
    revealing.foreach { k =>
      revealing = None
      if (keyed.contains(k)) reveal(k)
    }
  }

  // the force layout given the graph (each node a box its size), started from where the layers had the nodes when it
  // takes over from them, and run on the frames; its plan as it is now
  private def forceLayout(kept: Vector[N], sizes: Vector[Size], es: Vector[E], pairs: Vector[(Int, Int)],
                          wasForced: Boolean): GraphPlan = {
    val weight = weightOf()
    val strength = strengthOf()
    val length = lengthOf()
    simulation.settings = forceSettings()
    val first = simulation.nodes.isEmpty
    simulation.update(kept.indices.toVector.map(i => ForceNode.box(keyOf(kept(i)), sizes(i).width, sizes(i).height,
      weight(kept(i)))), es.map(e => ForceEdge(fromOf(e), toOf(e), strength(e), length(e))))
    if (!wasForced) {
      laid.foreach(p => kept.foreach { n =>
        val k = keyOf(n)
        keyed.get(k).foreach(j => simulation.place(k, p.nodes(j).centerX, p.nodes(j).centerY))
      })
      simulation.reheat(1.0)
    }
    if (first || !wasForced) following = true
    simulation.animate(() => stepped())
    forcePlan(pairs)
  }

  // the force layout's plan now: each node's box, and each edge straight from one to the other
  private def forcePlan(pairs: Vector[(Int, Int)]): GraphPlan = {
    val boxes = simulation.nodes.indices.toVector.map(i => simulation.boxOf(i))
    val routes = pairs.map { case (a, b) =>
      if (a == b) GraphRoute(Vector((boxes(a).right, boxes(a).y)), loop = true)
      else GraphRoute(GraphShapes.between(boxes(a), boxes(b), curved()))
    }
    val all = Rect.around(boxes).getOrElse(Rect(0.0, 0.0, 0.0, 0.0))
    GraphPlan(boxes, routes, boxes.map(_ => 0), Vector(boxes.indices.toVector), all.width, all.height, 0, flow())
  }

  // a step of the force layout: the graph drawn where it is now, the view following it while it spreads out
  private def stepped(): Unit = if (forced && simulation.nodes.length == present.length) {
    val next = forcePlan(ends)
    laid = Some(next)
    linesFresh = false
    lay(next.nodes, next.routes)
    if (following) {
      if (simulation.settled) following = false
      fit(animate = false)
    }
    forceStepped @= (())
  }

  // each edge's key, the same from one layout to the next: its ends' keys, and which of the edges between them it is
  private def linkKeys(es: Vector[E]): Vector[String] = {
    var seen: Map[String, Int] = Map.empty
    es.map { e =>
      val pair = fromOf(e).length.toString + ":" + fromOf(e) + toOf(e).length.toString + ":" + toOf(e)
      val n = seen.getOrElse(pair, 0)
      seen = seen + (pair -> (n + 1))
      pair + "#" + n.toString
    }
  }

  private def between(from: Vector[Rect], to: Vector[Rect], t: Double): Vector[Rect] = to.indices.toVector.map { i =>
    val a = from(i)
    val b = to(i)
    Rect(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.width + (b.width - a.width) * t,
      a.height + (b.height - a.height) * t)
  }

  // an edge on its way: point by point from where it was, where it had as many; otherwise where it is going, its ends
  // carried along with its nodes
  private def routeBetween(was: Option[GraphRoute], will: GraphRoute, from: Vector[Rect], to: Vector[Rect], e: Int,
                           t: Double): GraphRoute = was match {
    case Some(w) if w.points.length == will.points.length && w.loop == will.loop =>
      will.copy(points = will.points.indices.toVector.map { i =>
        val (ax, ay) = w.points(i)
        val (bx, by) = will.points(i)
        (ax + (bx - ax) * t, ay + (by - ay) * t)
      })
    case _ =>
      val (a, b) = ends(e)
      val ax = (from(a).x - to(a).x) * (1.0 - t)
      val ay = (from(a).y - to(a).y) * (1.0 - t)
      val bx = (from(b).x - to(b).x) * (1.0 - t)
      val by = (from(b).y - to(b).y) * (1.0 - t)
      val k = math.max(1, will.points.length - 1)
      // (the route runs from the edge's source, a, to its target, b)
      will.copy(points = will.points.indices.toVector.map { i =>
        val f = i.toDouble / k
        (will.points(i)._1 + ax * (1.0 - f) + bx * f, will.points(i)._2 + ay * (1.0 - f) + by * f)
      })
  }

  /** The canvas given the nodes at `boxes` and the edges along `routes`. */
  private def lay(boxes: Vector[Rect], routes: Vector[GraphRoute]): Unit = {
    // the edges leaving each layer drawn together, each bundle in a drawing of its own that the canvas shows only in
    // view; laid out by forces, a few hundred at a time
    val layerOf = laid.map(_.layerOf).getOrElse(Vector.empty)
    val count = if (layerOf.isEmpty) 0 else layerOf.max + 1
    var groups = if (forced) GraphView.chunks(routes.length) else (0 until count).toVector.map(_ => Vector.empty[Int])
    if (!forced) routes.indices.foreach { e =>
      val l = math.min(layerOf(ends(e)._1), layerOf(ends(e)._2))
      groups = groups.updated(l, groups(l) :+ e)
    }
    var groupLines: Vector[Vector[(Int, Path, Path)]] = Vector.empty
    var laidOut: Vector[ZoomItem] = Vector.empty
    groups.indices.foreach { g =>
      val group = groups(g)
      if (group.nonEmpty) {
        val box = Rect.around(group.map(e => boxAround(routes(e).points))).get
        val i = groupLines.length
        groupLines = groupLines :+ group.map(e => (e, lineOf(routes(e), box.x, box.y), headOf(routes(e), box.x, box.y)))
        laidOut = laidOut :+ ZoomItem(" edges " + g.toString, box.x, box.y, box.width, box.height, () => bundle(i),
          hits = false)
      }
    }
    bundles = groupLines
    // far out, the nodes of each layer are outlines in one drawing, until the narrowest is `detailFrom` pixels wide and
    // each is a box of its own with its words: a graph of thousands, fitted in, is a hundred drawings, not thousands
    // of boxes
    val detail = detailFrom()
    var shapes: Vector[(Int, Vector[(Int, Rect)])] = Vector.empty
    val sets = if (forced) GraphView.chunks(boxes.length) else laid.map(_.layers).getOrElse(Vector.empty)
    sets.foreach { layer =>
      if (layer.nonEmpty) {
        val box = Rect.around(layer.map(i => boxes(i))).get
        val narrowest = layer.map(i => boxes(i).width).min
        val s = shapes.length
        shapes = shapes :+ ((s, layer.map(i => (i, Rect(boxes(i).x - box.x, boxes(i).y - box.y, boxes(i).width,
          boxes(i).height)))))
        laidOut = laidOut :+ ZoomItem(" nodes " + s.toString, box.x, box.y, box.width, box.height, () => outlines(s),
          until = Some(detail * box.width / math.max(1.0, narrowest)), hits = false)
      }
    }
    outlined = shapes.map(_._2)
    boxes.indices.foreach { i =>
      val k = keyOf(present(i))
      val b = boxes(i)
      laidOut = laidOut :+ ZoomItem(k, b.x, b.y, b.width, b.height, () => componentFor(k), from = detail)
    }
    drawn @= drawn() + 1
    items @= laidOut
  }

  // what the edge through `route` is drawn with, from the point (x, y)
  private def lineOf(route: GraphRoute, x: Double, y: Double): Path = {
    val points = route.points.map(p => (p._1 - x, p._2 - y))
    if (route.loop) GraphShapes.loop(points.head)
    else if (forced) GraphShapes.straight(points, curved(), directed())
    else GraphShapes.line(points, flow(), curved(), directed())
  }

  private def headOf(route: GraphRoute, x: Double, y: Double): Path = {
    val points = route.points.map(p => (p._1 - x, p._2 - y))
    if (route.loop) GraphShapes.loopHead(points.head)
    else if (forced) GraphShapes.straightHead(points, curved())
    else GraphShapes.head(points, flow(), curved())
  }

  // a box round an edge's points, with room for its curves, its head and a loop
  private def boxAround(points: Vector[(Double, Double)]): Rect = {
    val xs = points.map(_._1)
    val ys = points.map(_._2)
    Rect(xs.min, ys.min, xs.max - xs.min, ys.max - ys.min).grow(32.0)
  }

  // a node's size: the app's, or its words' with room round them
  private def boxOf(n: N): Size = sizeOf() match {
    case Some(f) => f(n)
    case None =>
      val detail = detailOf()(n)
      val wide = math.max(labelOf(n).length * 7.6, detail.map(_.length * 6.4).getOrElse(0.0)) + 36.0
      Size(math.max(96.0, math.min(240.0, wide)), if (detail.nonEmpty) 52.0 else 36.0)
  }

  // ---- drawing ----

  private def componentFor(key: String): Component = {
    val value = valued(key)
    // faded while another is lit, and coming in as it glides into a graph it is new to
    val shows = Val((if (fades(key)) 0.3 else 1.0) * (if (newcomers.contains(key)) arrival() else 1.0))
    renderer() match {
      case Some(f) =>
        val c = f(value())
        c.opacity := shows()
        c
      case None => new GraphNode(Val(labelOf(value())), Val(detailOf()(value())), Val(nodeLook()(value())),
        Val(selected().contains(key)), shows)
    }
  }

  // whether a node fades: another is lit, and it is not one of its neighbours
  private def fades(key: String): Boolean = {
    drawn()
    lit() match {
      case Some(on) if on != key => keyed.get(on).exists { i =>
        !arriving(i).exists(e => keyOf(present(ends(e)._1)) == key) &&
          !leaving(i).exists(e => keyOf(present(ends(e)._2)) == key)
      }
      case _ => false
    }
  }

  // a layer's nodes as outlines, in their looks' colours
  private def outlines(s: Int): Drawing = {
    val d = new Drawing
    d.figures := {
      drawn()
      val looks = nodeLook()
      if (s >= outlined.length) Nil
      else GraphView.together(outlined(s).toList.flatMap { case (i, b) =>
        val look = looks(present(i))
        val edge = look.accent.getOrElse(Theme.border())
        val shape = Path.roundRect(b.x, b.y, b.width, b.height, 8.0)
        List(Figure(shape, fill = Some(if (look.dashed) Theme.surface().withAlpha(0.55) else Theme.raised())),
          Figure(if (look.dashed) shape.dashed(6.0, 4.0) else shape, stroke = Some(edge), width = 2.0))
      })
    }
    d
  }

  private def bundle(i: Int): Drawing = {
    val d = new Drawing
    d.figures := figures(i)
    d
  }

  // a bundle's figures: each edge's line and its head, lit, chosen or faded
  private def figures(i: Int): List[Figure] = {
    drawn()
    val on = lit().flatMap(k => keyed.get(k))
    val chosen = selectedEdge()
    val headed = directed()
    val looks = edgeLook()
    if (i >= bundles.length) Nil
    else GraphView.together(bundles(i).toList.flatMap { case (e, line, head) =>
      val look = looks(links(e))
      val touches = on.exists(n => ends(e)._1 == n || ends(e)._2 == n)
      val picked = chosen.contains(links(e))
      val base = look.color.getOrElse(Theme.muted())
      val colour = if (picked || (touches && look.color.isEmpty)) Theme.accent() else base
      val shown = if (on.nonEmpty && !touches && !picked) colour.withAlpha(colour.alpha * 0.2) else colour
      val width = look.width + (if (touches || picked) 1.0 else 0.0)
      val drawn = Figure(if (look.dashed) line.dashed(6.0, 4.0) else line, stroke = Some(shown), width = width)
      if (headed) List(drawn, Figure(head, fill = Some(shown))) else List(drawn)
    })
  }

  // ---- the pointer and the keys ----

  clicked.attach(p => pick(p))

  // (a node drawn as an outline, far out, is found by its box)
  private def pick(p: ZoomPoint): Unit = p.hit.map(_.key).filter(k => keyed.contains(k)).orElse(boxAt(p.x, p.y)) match {
    case Some(k) =>
      val node = present(keyed(k))
      val again = lastClick.contains(k) && Platform.ticks() - lastClickAt < 400.0
      lastClick = Some(k)
      lastClickAt = Platform.ticks()
      selectedEdge @= None
      selected @= Some(k)
      nodeClicked @= node
      if (again) activated @= node
    case None =>
      lastClick = None
      edgeIndexAt(p.x, p.y) match {
        case Some(e) =>
          selected @= None
          selectedEdge @= Some(links(e))
          edgeClicked @= links(e)
        case None =>
          selected @= None
          selectedEdge @= None
      }
  }

  // the key of the node whose box is at the world's point (`x`, `y`)
  private def boxAt(x: Double, y: Double): Option[String] =
    laid.flatMap(p => p.nodes.indices.find(i => p.nodes(i).contains(x, y))).map(i => keyOf(present(i)))

  /** The edge nearest the world's point (`x`, `y`), within a few pixels of the screen of its line. */
  def edgeAt(x: Double, y: Double): Option[E] = edgeIndexAt(x, y).map(e => links(e))

  private def edgeIndexAt(x: Double, y: Double): Option[Int] = {
    val slop = 5.0 / view().scale
    var best: Option[Int] = None
    var nearest = 1.0e18
    if (!linesFresh) {
      linesFresh = true
      val routes = laid.map(_.routes).getOrElse(Vector.empty)
      worldLines = routes.map(r => lineOf(r, 0.0, 0.0))
      lineBoxes = routes.map(r => boxAround(r.points))
    }
    lineBoxes.indices.foreach { e =>
      if (lineBoxes(e).contains(x, y)) {
        val d = Hit.distance(worldLines(e), x, y) - edgeLook()(links(e)).width / 2.0
        if (d <= slop && d < nearest) {
          nearest = d
          best = Some(e)
        }
      }
    }
    best
  }

  override protected def arrowsPan: Boolean = present.isEmpty

  keys = e => laid.flatMap(p => move(p, e))

  // the graph's own keys: the arrows among the nodes, Enter and Escape
  private def move(p: GraphPlan, e: KeyEvent): Option[() => Unit] = {
    val across = p.flow == GraphFlow.LeftToRight
    val at = selected().flatMap(k => keyed.get(k))
    if (e.key == Key.Enter) at.map(i => () => activated @= present(i))
    else if (e.key == Key.Escape) {
      if (selected().nonEmpty || selectedEdge().nonEmpty) Some(() => {
        selected @= None
        selectedEdge @= None
      })
      else None
    }
    else if (!Keys.isArrow(e.key) || present.isEmpty || e.ctrl || e.meta || e.alt) None
    else at match {
      case None => Some(() => choose(middle(p)))
      case Some(i) if forced => toward(p, i, e.key).map(t => () => choose(t))
      case Some(i) =>
        val forward = if (across) e.key == Key.ArrowRight else e.key == Key.ArrowDown
        val back = if (across) e.key == Key.ArrowLeft else e.key == Key.ArrowUp
        val target = if (forward) onward(p, i, 1) else if (back) onward(p, i, -1)
          else beside(p, i, if (e.key == Key.ArrowDown || e.key == Key.ArrowRight) 1 else -1)
        target.map(t => () => choose(t))
    }
  }

  private def choose(i: Int): Unit = {
    val k = keyOf(present(i))
    selectedEdge @= None
    selected @= Some(k)
    reveal(k)
    Platform.announce(labelOf(present(i)) + detailOf()(present(i)).map(d => ", " + d).getOrElse(""))
  }

  // the node nearest the middle of the view
  private def middle(p: GraphPlan): Int = {
    val (x, y) = toWorld(size.width() / 2.0, size.height() / 2.0)
    nearest(p.nodes.indices.toList, i => math.abs(p.nodes(i).centerX - x) + math.abs(p.nodes(i).centerY - y))
  }

  // the one of `pool` least far by `far`
  private def nearest(pool: List[Int], far: Int => Double): Int = {
    var best = pool.head
    var least = far(best)
    pool.tail.foreach { i =>
      val d = far(i)
      if (d < least) {
        best = i
        least = d
      }
    }
    best
  }

  // across the flow, where a node's centre is
  private def crossOf(p: GraphPlan, i: Int): Double =
    if (p.flow == GraphFlow.LeftToRight) p.nodes(i).centerY else p.nodes(i).centerX

  // along the flow `way` (1 on, -1 back): a neighbour that way most in line, or the node most in line in the next
  // layer that way that has one
  private def onward(p: GraphPlan, i: Int, way: Int): Option[Int] = {
    val layer = p.layerOf(i)
    val near = (leaving(i).map(e => ends(e)._2) ++ arriving(i).map(e => ends(e)._1))
      .filter(j => (p.layerOf(j) - layer) * way > 0)
    val pool = if (near.nonEmpty) near else {
      var l = layer + way
      while (l >= 0 && l < p.layers.length && p.layers(l).isEmpty) l += way
      if (l >= 0 && l < p.layers.length) p.layers(l).toList else Nil
    }
    if (pool.isEmpty) None
    else Some(nearest(pool, j => math.abs(crossOf(p, j) - crossOf(p, i)) + math.abs(p.layerOf(j) - layer) * 1000.0))
  }

  // with the nodes where forces put them, the arrow's way: the neighbour nearest that way (within half a right angle
  // of it), or, where there is none, the nearest node that way (within a wider angle); a node off to the side counts
  // as further
  private def toward(p: GraphPlan, i: Int, key: Key): Option[Int] = {
    val wx = if (key == Key.ArrowRight) 1.0 else if (key == Key.ArrowLeft) -1.0 else 0.0
    val wy = if (key == Key.ArrowDown) 1.0 else if (key == Key.ArrowUp) -1.0 else 0.0
    val x = p.nodes(i).centerX
    val y = p.nodes(i).centerY
    def along(j: Int): Double = (p.nodes(j).centerX - x) * wx + (p.nodes(j).centerY - y) * wy
    def aside(j: Int): Double = math.abs((p.nodes(j).centerX - x) * wy - (p.nodes(j).centerY - y) * wx)
    val near = (leaving(i).map(e => ends(e)._2) ++ arriving(i).map(e => ends(e)._1))
      .filter(j => j != i && along(j) > 0.0 && aside(j) <= along(j))
    val pool = if (near.nonEmpty) near
      else p.nodes.indices.toList.filter(j => j != i && along(j) > 0.0 && aside(j) <= along(j) * 2.0)
    if (pool.isEmpty) None else Some(nearest(pool, j => along(j) + aside(j) * 2.0))
  }

  // across the flow `way`: the next node in its layer
  private def beside(p: GraphPlan, i: Int, way: Int): Option[Int] = {
    val layer = p.layers(p.layerOf(i))
    val at = layer.indexWhere(_ == i) + way
    if (at >= 0 && at < layer.length) Some(layer(at)) else None
  }

  override def dispose(): Unit = {
    simulation.stop()
    super.dispose()
  }

  nodes.attach(_ => arrange())
  edges.attach(_ => arrange())
  flow.attach { _ =>
    if (!forced) {
      refit = true
      arrange()
    }
  }
  mode.attach { _ =>
    refit = mode() == GraphMode.Layered
    arrange()
  }
  forceSettings.attach { s =>
    simulation.settings = s
    if (forced) simulation.reheat()
  }
  weightOf.attach(_ => if (forced) arrange())
  strengthOf.attach(_ => if (forced) arrange())
  lengthOf.attach(_ => if (forced) arrange())
  // the view is the app's or the user's once they move it: it no longer follows the force layout spreading out
  event.pointer.down.attach(_ => following = false)
  event.pointer.wheel.attach(_ => following = false)
  event.key.down.attach(_ => following = false)
  grab = (x, y) => if (!forced) None else boxAt(x, y).flatMap { k =>
    simulation.positionOf(k).map { case (nx, ny) =>
      // held by where it was taken hold of, so it does not jump to the pointer
      val dx = nx - x
      val dy = ny - y
      ZoomGrab((mx, my) => simulation.drag(k, mx + dx, my + dy), () => simulation.drop(k))
    }
  }
  // the node chosen is the one lit, until the pointer moves over another
  selected.attach(_ => hovered @= None)
  sizeOf.attach(_ => arrange())
  detailOf.attach(_ => arrange())
  layerGap.attach(_ => arrange())
  nodeGap.attach(_ => arrange())
  curved.attach(_ => arrange())
  directed.attach { _ =>
    linesFresh = false
    laid.foreach(p => lay(p.nodes, p.routes))
  }
  detailFrom.attach(_ => laid.foreach(p => lay(p.nodes, p.routes)))
  edgeLook.attach(_ => laid.foreach(p => lay(p.nodes, p.routes)))
  renderer.attach(_ => present.foreach(n => remake(keyOf(n))))
  arrange()
}

object GraphView {
  /** Whether edges are drawn as curves here: everywhere but a Roku and an Apple TV, which draw only rectangles. */
  def curves: Boolean = Platform.kind != PlatformKind.Roku && Platform.kind != PlatformKind.TvOS

  /** How a graph's force layout moves its nodes by default: boxes with words in them, pushed further apart and linked
    * further than dots would be. */
  def forces: ForceSettings = ForceSettings(charge = 800.0, linkLength = 60.0, padding = 12.0, gravity = 0.03)

  // figures that look the same drawn as one, so a drawing of hundreds of lines is a handful of paths: the faded ones
  // under the rest, and otherwise in the order they came
  private[graph] def together(figures: List[Figure]): List[Figure] = {
    var groups: Vector[(Figure, Vector[PathOp])] = Vector.empty
    figures.foreach { f =>
      val at = groups.indexWhere(g => g._1.fill == f.fill && g._1.stroke == f.stroke && g._1.width == f.width)
      if (at < 0) groups = groups :+ ((f, f.path.ops))
      else groups = groups.updated(at, (groups(at)._1, groups(at)._2 ++ f.path.ops))
    }
    def faint(f: Figure): Boolean = f.fill.orElse(f.stroke).exists(_.alpha < 0.5)
    val merged = groups.toList.map(g => g._1.copy(path = new Path(g._2)))
    merged.filter(faint) ++ merged.filterNot(faint)
  }

  // the numbers up to `n` in runs of a few hundred: the edges, or the nodes, drawn together in one drawing
  private[graph] def chunks(n: Int): Vector[Vector[Int]] = {
    var all: Vector[Vector[Int]] = Vector.empty
    var at = 0
    while (at < n) {
      val end = math.min(n, at + 256)
      all = all :+ (at until end).toVector
      at = end
    }
    all
  }
}
