package everyui.graph

import everyui.spatial.Rect
import everyui.task.{TaskInstance, TaskSupport}
import everyui.ui

/** A graph laid out by forces, in plain shared code: every node pushes the others away (more for a greater `weight`),
  * each edge is a spring that pulls its ends to a length apart, a little gravity holds it all together, and no two
  * nodes overlap (by their sizes). It is a simulation that settles and stops: each `tick` moves the nodes a step,
  * hot at first and cooler each tick (`alpha`), until it is cool enough (`settled`) and nothing moves. As d3-force
  * does it; the pushes of a few thousand nodes are worked out over a quadtree (Barnes and Hut's method).
  *
  * `update` gives it the graph, and gives it again as the graph changes: a node keeps its place by its key, and a new
  * one starts beside the nodes it has edges to, so an added node settles in without the rest being shuffled. The
  * same nodes, edges and seed lay out the same way every time.
  *
  * The app can `reheat` it, `pin` a node where it is to stay, `drag` one (pinned while it is dragged, and let go with
  * `drop`), and step it (`tick`) or run it to the end at once (`settle`). `animate` runs it on the app's frames, a
  * tick a frame, for as long as it is warm: frames stop when it settles and start again when it is warmed.
  *
  * Only positions come out (`x`, `y`, `positionOf`, `boxOf`, `bounds`, `nodeAt`): the app draws the nodes and edges
  * as it likes (`GraphView` with `GraphMode.Force`, or its own items on a `ZoomCanvas`).
  *
  * {{{
  *   val layout = new ForceLayout(ForceSettings(linkLength = 40.0))
  *   layout.update(words.map(w => ForceNode.circle(w.text, 4.0 + w.count / 10.0, weight = 1.0 + w.count / 50.0)),
  *     pairs.map(p => ForceEdge(p.a, p.b, strength = p.together / most)))
  *   layout.animate(() => canvas.items @= drawn(layout))   // a tick a frame until it settles
  *   // or at once, for a first look
  *   layout.settle()
  * }}} */
class ForceLayout(initial: ForceSettings = ForceSettings()) {
  private var tuned: ForceSettings = initial
  private var random = new ForceRandom(initial.seed)
  private var graph: Vector[ForceNode] = Vector.empty
  private var links: Vector[ForceEdge] = Vector.empty
  private var index: Map[String, Int] = Map.empty
  // each node's place and speed, its pin (where it is held, with `held`), and each edge's ends
  private var xs: Array[Double] = new Array[Double](0)
  private var ys: Array[Double] = new Array[Double](0)
  private var vxs: Array[Double] = new Array[Double](0)
  private var vys: Array[Double] = new Array[Double](0)
  private var held: Array[Boolean] = new Array[Boolean](0)
  private var heldX: Array[Double] = new Array[Double](0)
  private var heldY: Array[Double] = new Array[Double](0)
  private var sources: Array[Int] = new Array[Int](0)
  private var targets: Array[Int] = new Array[Int](0)
  // each edge's length, centre to centre, its strength and how much of the pull its source takes
  private var lengths: Array[Double] = new Array[Double](0)
  private var strengths: Array[Double] = new Array[Double](0)
  private var biases: Array[Double] = new Array[Double](0)
  // each node's charge (negative: it pushes away)
  private var charges: Array[Double] = new Array[Double](0)
  private val tree = new ForceTree
  private var heat = 1.0
  private var target = 0.0
  private var ticks = 0
  // the node dragged, and whether it was pinned before
  private var dragged: Option[String] = None
  private var wasPinned = false
  // the app's frames: what is told after each tick, and the task that ticks
  private var listener: Option[() => Unit] = None
  private var support: Option[TaskSupport] = None
  private var running: Option[TaskInstance] = None
  // whether a node has moved since the app was last told
  private var moved = false

  /** How it moves its nodes. Set again, it applies from the next tick. */
  def settings: ForceSettings = tuned
  def settings_=(to: ForceSettings): Unit = {
    tuned = to
    measure()
  }

  /** The nodes and edges it has (the first of each key, and the edges between them), in order. */
  def nodes: Vector[ForceNode] = graph
  def edges: Vector[ForceEdge] = links

  /** Where the node keyed `key` is in the order of `nodes`. */
  def indexOf(key: String): Option[Int] = index.get(key)

  /** The centre of node `i`. */
  def x(i: Int): Double = xs(i)
  def y(i: Int): Double = ys(i)

  def positionOf(key: String): Option[(Double, Double)] = index.get(key).map(i => (xs(i), ys(i)))

  /** Each node's centre, in the order of `nodes`. */
  def positions: Vector[(Double, Double)] = graph.indices.toVector.map(i => (xs(i), ys(i)))

  /** The box node `i` takes, round its centre. */
  def boxOf(i: Int): Rect = {
    val n = graph(i)
    Rect(xs(i) - n.width / 2.0, ys(i) - n.height / 2.0, n.width, n.height)
  }

  /** The box round every node; None with none. */
  def bounds: Option[Rect] = Rect.around(graph.indices.toVector.map(i => boxOf(i)))

  /** The key of the node at the point (`px`, `py`), within its circle or its box: the last in order, drawn over the
    * others, where they meet. */
  def nodeAt(px: Double, py: Double): Option[String] = {
    var i = graph.length - 1
    var found: Option[String] = None
    while (found.isEmpty && i >= 0) {
      val n = graph(i)
      val dx = px - xs(i)
      val dy = py - ys(i)
      val inside = if (n.round) dx * dx + dy * dy <= n.reach * n.reach
        else math.abs(dx) <= n.width / 2.0 && math.abs(dy) <= n.height / 2.0
      if (inside) found = Some(n.key)
      i -= 1
    }
    found
  }

  /** How warm it is: 1 at the start, falling each tick towards the target (0, or warmer while a node is dragged). */
  def alpha: Double = heat

  /** How many ticks it has taken. */
  def tickCount: Int = ticks

  /** Whether it has cooled enough to stop: its ticks no longer move anything. */
  def settled: Boolean = heat < tuned.alphaMin && target < tuned.alphaMin

  /** The graph given, or given again: nodes keep their places by their keys, a new node starts beside those it has
    * edges to (or near the middle, with none), and edges to a key it does not have are left out. It warms again to
    * `ForceSettings.reheat` (or all the way, for the first nodes), so an added node settles in and the rest barely
    * move. */
  def update(nodes: Vector[ForceNode], edges: Vector[ForceEdge]): Unit = {
    var keyed: Map[String, Int] = Map.empty
    var kept: Vector[ForceNode] = Vector.empty
    nodes.foreach { n =>
      if (!keyed.contains(n.key)) {
        keyed = keyed + (n.key -> kept.length)
        kept = kept :+ n
      }
    }
    val es = edges.filter(e => keyed.contains(e.from) && keyed.contains(e.to))
    val n = kept.length
    val wasEmpty = index.isEmpty
    val nx = GraphLayout.doubles(n)
    val ny = GraphLayout.doubles(n)
    val nvx = GraphLayout.doubles(n)
    val nvy = GraphLayout.doubles(n)
    val pinned = GraphLayout.flags(n)
    val px = GraphLayout.doubles(n)
    val py = GraphLayout.doubles(n)
    val placed = GraphLayout.flags(n)
    var i = 0
    while (i < n) {
      index.get(kept(i).key).foreach { was =>
        nx(i) = xs(was)
        ny(i) = ys(was)
        nvx(i) = vxs(was)
        nvy(i) = vys(was)
        pinned(i) = held(was)
        px(i) = heldX(was)
        py(i) = heldY(was)
        placed(i) = true
      }
      i += 1
    }
    // each node's neighbours, to start a new one beside them
    val beside = GraphLayout.lists(n)
    es.foreach { e =>
      val a = keyed(e.from)
      val b = keyed(e.to)
      if (a != b) {
        beside(a) = b :: beside(a)
        beside(b) = a :: beside(b)
      }
    }
    val spacing = math.max(10.0, meanReach(kept) * 1.5)
    // the new nodes placed breadth first, each beside those of its neighbours already placed: from the nodes it had
    // (so an added node starts by what it is linked to), then from the most linked of the rest, so each part of the
    // graph starts gathered together rather than spread through the others (as a spiral in their order would)
    val queue = GraphLayout.ints(n)
    val queued = GraphLayout.flags(n)
    var head = 0
    var tail = 0
    var left = 0
    i = 0
    while (i < n) {
      if (!placed(i)) {
        left += 1
        if (beside(i).exists(j => placed(j))) {
          queue(tail) = i
          queued(i) = true
          tail += 1
        }
      }
      i += 1
    }
    var seeds = 0
    var lone = 0
    // how far from the middle the placed nodes reach
    var extent = 0.0
    i = 0
    while (i < n) {
      if (placed(i)) extent = math.max(extent, math.sqrt(nx(i) * nx(i) + ny(i) * ny(i)))
      i += 1
    }
    while (left > 0) {
      if (head == tail) {
        // a part with nothing placed starts from its most linked node: the first in the middle, where nothing is, and
        // each after it just past what is placed, turned a golden angle on from the one before; a node with no edges
        // at all on a spiral out from the middle
        var best = -1
        i = 0
        while (i < n) {
          if (!placed(i) && !queued(i) && (best < 0 || beside(i).length > beside(best).length)) best = i
          i += 1
        }
        val alone = beside(best).isEmpty
        val r = if (alone) spacing * 2.0 * math.sqrt(0.5 + lone) else if (n - left == 0) 0.0 else extent + spacing * 3.0
        val angle = (if (alone) lone else seeds) * 2.399963229728653
        if (alone) lone += 1 else seeds += 1
        nx(best) = r * math.cos(angle)
        ny(best) = r * math.sin(angle)
        extent = math.max(extent, r)
        placed(best) = true
        left -= 1
        beside(best).foreach { j =>
          if (!placed(j) && !queued(j)) {
            queue(tail) = j
            queued(j) = true
            tail += 1
          }
        }
      }
      else {
        val at = queue(head)
        head += 1
        var sx = 0.0
        var sy = 0.0
        var count = 0
        beside(at).foreach { j =>
          if (placed(j)) {
            sx += nx(j)
            sy += ny(j)
            count += 1
          }
          else if (!queued(j)) {
            queue(tail) = j
            queued(j) = true
            tail += 1
          }
        }
        // a link's length from its neighbours' middle, in a direction of its own
        val angle = random.next() * 6.283185307179586
        val away = tuned.linkLength + kept(at).reach + spacing / 2.0
        nx(at) = sx / math.max(1, count) + math.cos(angle) * away
        ny(at) = sy / math.max(1, count) + math.sin(angle) * away
        extent = math.max(extent, math.sqrt(nx(at) * nx(at) + ny(at) * ny(at)))
        placed(at) = true
        left -= 1
      }
    }
    graph = kept
    links = es
    index = keyed
    xs = nx
    ys = ny
    vxs = nvx
    vys = nvy
    held = pinned
    heldX = px
    heldY = py
    if (dragged.exists(k => !keyed.contains(k))) {
      dragged = None
      target = 0.0
    }
    val m = es.length
    sources = GraphLayout.ints(m)
    targets = GraphLayout.ints(m)
    var e = 0
    while (e < m) {
      sources(e) = keyed(es(e).from)
      targets(e) = keyed(es(e).to)
      e += 1
    }
    measure()
    moved = true
    reheat(if (wasEmpty) 1.0 else tuned.reheat)
  }

  /** Warmer again, to `to`, so it moves on from where it is: after the app changed something itself. */
  def reheat(to: Double = 1.0): Unit = {
    if (to > heat) heat = to
    wake()
  }

  /** Node `key` held at (`px`, `py`), where the forces leave it, until `unpin`. */
  def pin(key: String, px: Double, py: Double): Unit = index.get(key).foreach { i =>
    held(i) = true
    heldX(i) = px
    heldY(i) = py
    xs(i) = px
    ys(i) = py
    vxs(i) = 0.0
    vys(i) = 0.0
    moved = true
    wake()
  }

  /** Node `key` let go, to move with the forces again. */
  def unpin(key: String): Unit = index.get(key).foreach(i => held(i) = false)

  def pinned(key: String): Boolean = index.get(key).exists(i => held(i))

  /** Node `key` put at (`px`, `py`), from where the forces move it on. */
  def place(key: String, px: Double, py: Double): Unit = index.get(key).foreach { i =>
    xs(i) = px
    ys(i) = py
    vxs(i) = 0.0
    vys(i) = 0.0
    if (held(i)) {
      heldX(i) = px
      heldY(i) = py
    }
    moved = true
    wake()
  }

  /** Node `key` dragged to (`px`, `py`): pinned there, and the rest kept warm to follow, until `drop`. */
  def drag(key: String, px: Double, py: Double): Unit = if (index.contains(key)) {
    if (!dragged.contains(key)) {
      dragged.foreach(k => drop(k))
      wasPinned = pinned(key)
      dragged = Some(key)
    }
    pin(key, px, py)
    target = 0.3
    reheat(0.3)
  }

  /** The node dragged let go (left pinned if it was pinned before), and the rest left to cool and settle. */
  def drop(key: String): Unit = if (dragged.contains(key)) {
    if (!wasPinned) unpin(key)
    dragged = None
    target = 0.0
    wake()
  }

  /** One step, or `count`: every force applied, each node moved by its speed, and it cools a little. A tick once it
    * has settled still moves what is left to move, and cools no further than it is. */
  def tick(count: Int = 1): Unit = {
    var t = 0
    while (t < count) {
      step()
      t += 1
    }
    wake()
  }

  /** Ticked until it settles (or `most` ticks): its final layout at once, for a first look or a test. How many ticks
    * it took. */
  def settle(most: Int = 3000): Int = {
    var t = 0
    while (t < most && !settled) {
      step()
      t += 1
    }
    wake()
    t
  }

  /** Run on the frames of `on` (the app's, `ui`), a tick a frame, with `onTick` told after each, for as long as it is
    * warm: frames stop when it settles, and start again when it is warmed (`update`, `reheat`, `drag`). */
  def animate(onTick: () => Unit, on: TaskSupport = ui): Unit = {
    listener = Some(onTick)
    support = Some(on)
    wake()
  }

  /** No longer run on the app's frames. */
  def stop(): Unit = {
    running.foreach(_.cancel())
    running = None
    listener = None
    support = None
  }

  /** Whether it is running on the app's frames now. */
  def animating: Boolean = running.nonEmpty

  // a frame of the app's: a tick while it is warm, and the app told of any move since the last (the app's own `settle`,
  // a pin); settled, the task is done and its frames let go
  private[graph] def frame(): Boolean = {
    if (!settled) step()
    if (moved) {
      moved = false
      listener.foreach(f => f())
    }
    if (settled) running = None
    running.nonEmpty
  }

  // the task that ticks started, if the app asked for frames and there is anything to move or to show
  private def wake(): Unit = if (running.isEmpty && (!settled || moved)) support.foreach { s =>
    running = Some(new ForceRun(this).start(s))
  }

  // each edge's length, strength and bias, and each node's charge, from the settings
  private def measure(): Unit = {
    val n = graph.length
    val m = links.length
    val degree = GraphLayout.ints(n)
    var e = 0
    while (e < m) {
      degree(sources(e)) += 1
      degree(targets(e)) += 1
      e += 1
    }
    lengths = GraphLayout.doubles(m)
    strengths = GraphLayout.doubles(m)
    biases = GraphLayout.doubles(m)
    e = 0
    while (e < m) {
      val s = sources(e)
      val t = targets(e)
      val edge = links(e)
      lengths(e) = edge.length.getOrElse(tuned.linkLength) + graph(s).reach + graph(t).reach
      // shared out among a busy node's edges, as d3 does, so a hub is not pulled apart
      strengths(e) = tuned.linkStrength * edge.strength / math.max(1, math.min(degree(s), degree(t)))
      biases(e) = degree(s).toDouble / math.max(1, degree(s) + degree(t))
      e += 1
    }
    charges = GraphLayout.doubles(n)
    var i = 0
    while (i < n) {
      charges(i) = -tuned.charge * graph(i).weight
      i += 1
    }
  }

  private def meanReach(of: Vector[ForceNode]): Double =
    if (of.isEmpty) 0.0 else of.map(_.reach).sum / of.length

  private def step(): Unit = {
    moved = true
    heat += (target - heat) * tuned.alphaDecay
    ticks += 1
    val n = graph.length
    if (n > 0) {
      springs()
      push()
      var pass = 0
      while (pass < tuned.collideIterations) {
        collide()
        pass += 1
      }
      if (tuned.flow.nonEmpty) lean()
      pull()
      var i = 0
      while (i < n) {
        if (held(i)) {
          xs(i) = heldX(i)
          ys(i) = heldY(i)
          vxs(i) = 0.0
          vys(i) = 0.0
        }
        else {
          vxs(i) *= 1.0 - tuned.velocityDecay
          vys(i) *= 1.0 - tuned.velocityDecay
          xs(i) += vxs(i)
          ys(i) += vys(i)
        }
        i += 1
      }
    }
  }

  // each edge pulls (or pushes) its ends towards its length apart, where they are headed
  private def springs(): Unit = {
    var e = 0
    while (e < links.length) {
      val s = sources(e)
      val t = targets(e)
      if (s != t) {
        var dx = xs(t) + vxs(t) - xs(s) - vxs(s)
        var dy = ys(t) + vys(t) - ys(s) - vys(s)
        if (dx == 0.0) dx = random.jiggle()
        if (dy == 0.0) dy = random.jiggle()
        val l = math.sqrt(dx * dx + dy * dy)
        val k = (l - lengths(e)) / l * heat * strengths(e)
        val fx = dx * k
        val fy = dy * k
        vxs(t) -= fx * biases(e)
        vys(t) -= fy * biases(e)
        vxs(s) += fx * (1.0 - biases(e))
        vys(s) += fy * (1.0 - biases(e))
      }
      e += 1
    }
  }

  // every node pushes every other away, a far group of them as one
  private def push(): Unit = if (tuned.charge != 0.0) {
    val n = graph.length
    tree.build(n, xs, ys)
    tree.weigh(charges)
    val theta = tuned.theta * tuned.theta
    val farthest = tuned.distanceMax.map(d => d * d).getOrElse(1.0e300)
    var i = 0
    while (i < n) {
      val (fx, fy) = tree.repel(i, xs(i), ys(i), charges, heat, theta, 1.0, farthest, random)
      vxs(i) += fx
      vys(i) += fy
      i += 1
    }
  }

  // no two nodes overlapping, by their sizes: a pair that would is pushed apart, the smaller moving further (a pinned
  // node not at all)
  private def collide(): Unit = if (tuned.collide > 0.0) {
    val n = graph.length
    val px = GraphLayout.doubles(n)
    val py = GraphLayout.doubles(n)
    var wide = 0.0
    var high = 0.0
    var i = 0
    while (i < n) {
      px(i) = xs(i) + vxs(i)
      py(i) = ys(i) + vys(i)
      wide = math.max(wide, graph(i).width)
      high = math.max(high, graph(i).height)
      i += 1
    }
    tree.build(n, px, py)
    val pad = tuned.padding
    i = 0
    while (i < n) {
      val a = graph(i)
      val ax = xs(i) + vxs(i)
      val ay = ys(i) + vys(i)
      val reachX = (a.width + wide) / 2.0 + pad
      val reachY = (a.height + high) / 2.0 + pad
      val from = i
      tree.near(ax - reachX, ay - reachY, ax + reachX, ay + reachY, j => if (j > from) part(from, j))
      i += 1
    }
  }

  // nodes `i` and `j` pushed apart where they would overlap
  private def part(i: Int, j: Int): Unit = {
    val a = graph(i)
    val b = graph(j)
    var dx = xs(i) + vxs(i) - xs(j) - vxs(j)
    var dy = ys(i) + vys(i) - ys(j) - vys(j)
    val pad = tuned.padding
    val areaA = a.width * a.height
    val areaB = b.width * b.height
    // how much of the move each takes: the smaller more, a pinned one none
    val share = if (held(i) && held(j)) 0.0 else if (held(i)) 0.0 else if (held(j)) 1.0
      else areaB / math.max(1.0e-9, areaA + areaB)
    val other = if (held(i) && held(j)) 0.0 else 1.0 - share
    if (a.round && b.round) {
      val r = a.reach + b.reach + pad
      var l = dx * dx + dy * dy
      if (l < r * r) {
        if (dx == 0.0) {
          dx = random.jiggle()
          l += dx * dx
        }
        if (dy == 0.0) {
          dy = random.jiggle()
          l += dy * dy
        }
        val d = math.sqrt(l)
        val k = (r - d) / d * tuned.collide
        vxs(i) += dx * k * share
        vys(i) += dy * k * share
        vxs(j) -= dx * k * other
        vys(j) -= dy * k * other
      }
    }
    else {
      // boxes: apart along the way they overlap least
      val overX = (a.width + b.width) / 2.0 + pad - math.abs(dx)
      val overY = (a.height + b.height) / 2.0 + pad - math.abs(dy)
      if (overX > 0.0 && overY > 0.0) {
        if (overX < overY) {
          if (dx == 0.0) dx = random.jiggle()
          val move = (if (dx > 0.0) overX else -overX) * tuned.collide
          vxs(i) += move * share
          vxs(j) -= move * other
        }
        else {
          if (dy == 0.0) dy = random.jiggle()
          val move = (if (dy > 0.0) overY else -overY) * tuned.collide
          vys(i) += move * share
          vys(j) -= move * other
        }
      }
    }
  }

  // a directed graph's edges lean along the flow: a target not yet a length past its source that way is pushed on
  private def lean(): Unit = tuned.flow.foreach { flow =>
    val across = flow == GraphFlow.LeftToRight
    val k = tuned.flowStrength * heat
    var e = 0
    while (e < links.length) {
      val s = sources(e)
      val t = targets(e)
      if (s != t) {
        val short = if (across) xs(s) + lengths(e) - xs(t) else ys(s) + lengths(e) - ys(t)
        if (short > 0.0) {
          if (across) {
            vxs(t) += short * k
            vxs(s) -= short * k
          }
          else {
            vys(t) += short * k
            vys(s) -= short * k
          }
        }
      }
      e += 1
    }
  }

  // gravity towards the middle, and the whole graph's middle kept at (0, 0) while nothing is pinned (a pinned node
  // anchors it, and the rest would slide away from it)
  private def pull(): Unit = {
    val n = graph.length
    val g = tuned.gravity * heat
    var sx = 0.0
    var sy = 0.0
    var anyHeld = false
    var i = 0
    while (i < n) {
      vxs(i) -= xs(i) * g
      vys(i) -= ys(i) * g
      sx += xs(i)
      sy += ys(i)
      if (held(i)) anyHeld = true
      i += 1
    }
    if (tuned.centred && n > 0 && !anyHeld) {
      val mx = sx / n
      val my = sy / n
      i = 0
      while (i < n) {
        if (!held(i)) {
          xs(i) -= mx
          ys(i) -= my
        }
        i += 1
      }
    }
  }
}
