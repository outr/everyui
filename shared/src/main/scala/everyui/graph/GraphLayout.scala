package everyui.graph

import everyui.spatial.{Rect, Size}

/** A directed graph laid out in layers, as Sugiyama's method does it, in plain shared code:
  *
  *  1. **Cycles broken.** A search from each node in turn, in the order given, turns round each edge that leads back to
  *     a node still being searched from, so what is left runs one way. The turned edges are still drawn from their
  *     source to their target (`GraphRoute.reversed`).
  *  1. **Layers.** Each node goes one layer past the furthest of those that lead to it (the longest path), and then as
  *     far on as its nearest successor allows, so an edge spans as few layers as it can. An edge that still spans more
  *     than one passes a point in each layer between (a dummy node), so it is routed round what is there.
  *  1. **Order in a layer.** Sweeps down and up the layers put each node at the average place of its neighbours in the
  *     layer before (the barycentre), and the order with the fewest crossings, counted after each sweep, is kept.
  *  1. **Places.** Each node is pulled towards the middle of its neighbours (their median), a layer at a time, down and
  *     up, as near as it can be while the nodes of a layer keep their order and their distance apart (a least-squares
  *     fit, pool-adjacent-violators); the dummies of a long edge are pulled hardest, which keeps it straight.
  *
  * `sizes` are the nodes' boxes, `edges` pairs of their indices (a node to itself is drawn as a loop). Layers are
  * `layerGap` apart along the flow, and the nodes of a layer `nodeGap` apart across it. */
object GraphLayout {
  def layout(sizes: Vector[Size], edges: Vector[(Int, Int)], flow: GraphFlow, layerGap: Double = 80.0,
             nodeGap: Double = 24.0): GraphPlan = {
    val n = sizes.length
    val m = edges.length
    val across = flow == GraphFlow.LeftToRight
    // along the flow and across it, for each node
    val along = sizes.map(s => if (across) s.width else s.height)
    val side = sizes.map(s => if (across) s.height else s.width)
    val loop = edges.map(e => e._1 == e._2)

    val reversed = breakCycles(n, edges, loop)
    // the edges as the layout runs them: from `from` to `to`, one way
    val from = GraphLayout.ints(m)
    val to = GraphLayout.ints(m)
    var e = 0
    while (e < m) {
      from(e) = if (reversed(e)) edges(e)._2 else edges(e)._1
      to(e) = if (reversed(e)) edges(e)._1 else edges(e)._2
      e += 1
    }
    val layer = layers(n, m, from, to, loop)

    // each edge's chain of vertices, a dummy in each layer it passes; vertices past `n` are dummies
    var vertexLayer: Vector[Int] = layer.toVector
    var chains: Vector[Vector[Int]] = Vector.empty
    e = 0
    while (e < m) {
      if (loop(e)) chains = chains :+ Vector(from(e))
      else {
        var chain = Vector(from(e))
        var l = layer(from(e)) + 1
        while (l < layer(to(e))) {
          chain = chain :+ vertexLayer.length
          vertexLayer = vertexLayer :+ l
          l += 1
        }
        chains = chains :+ (chain :+ to(e))
      }
      e += 1
    }
    val count = vertexLayer.length
    // who each vertex meets in the layer before (up) and after (down), once for each edge between them
    val up = GraphLayout.lists(count)
    val down = GraphLayout.lists(count)
    chains.foreach { chain =>
      var i = 0
      while (i + 1 < chain.length) {
        down(chain(i)) = chain(i + 1) :: down(chain(i))
        up(chain(i + 1)) = chain(i) :: up(chain(i + 1))
        i += 1
      }
    }
    val layerCount = if (count == 0) 0 else vertexLayer.max + 1
    val order = ordering(count, layerCount, vertexLayer, up, down)
    val crossings = crossingsOf(order, down)

    // the room each vertex takes across the flow, and from the next in its layer
    val room = GraphLayout.doubles(count)
    val weight = GraphLayout.doubles(count)
    var at = 0
    while (at < count) {
      room(at) = if (at < n) side(at) else 0.0
      weight(at) = if (at < n) 1.0 else 4.0
      at += 1
    }
    val centre = places(order, up, down, room, weight, n, nodeGap)
    // the layers along the flow: each as deep as its deepest node
    val depth = GraphLayout.doubles(layerCount)
    var v = 0
    while (v < n) {
      if (along(v) > depth(vertexLayer(v))) depth(vertexLayer(v)) = along(v)
      v += 1
    }
    val start = GraphLayout.doubles(layerCount)
    var l = 1
    while (l < layerCount) {
      start(l) = start(l - 1) + depth(l - 1) + layerGap
      l += 1
    }
    // across the flow from 0 at the edge of the nearest node
    var least = 0.0
    var most = 0.0
    if (count > 0) {
      least = 1.0e18
      most = -1.0e18
      v = 0
      while (v < count) {
        least = math.min(least, centre(v) - room(v) / 2.0)
        most = math.max(most, centre(v) + room(v) / 2.0)
        v += 1
      }
    }
    v = 0
    while (v < count) {
      centre(v) -= least
      v += 1
    }
    val boxes = (0 until n).toVector.map { i =>
      val mid = start(vertexLayer(i)) + depth(vertexLayer(i)) / 2.0
      if (across) Rect(mid - along(i) / 2.0, centre(i) - side(i) / 2.0, along(i), side(i))
      else Rect(centre(i) - side(i) / 2.0, mid - along(i) / 2.0, side(i), along(i))
    }
    val routes = route(chains, reversed, loop, boxes, vertexLayer, centre, start, depth, order, n, across)
    val lengthAlong = if (layerCount == 0) 0.0 else start(layerCount - 1) + depth(layerCount - 1)
    val realOrder = order.map(_.filter(_ < n))
    GraphPlan(boxes, routes, layer.toVector, realOrder, if (across) lengthAlong else most - least,
      if (across) most - least else lengthAlong, crossings, flow)
  }

  // arrays filled by hand, as a Roku has no Array.fill: of empty lists, of false, of 0.0 and of 0
  private[graph] def lists(n: Int): Array[List[Int]] = {
    val a = new Array[List[Int]](n)
    var i = 0
    while (i < n) {
      a(i) = Nil
      i += 1
    }
    a
  }

  private[graph] def flags(n: Int): Array[Boolean] = {
    val a = new Array[Boolean](n)
    var i = 0
    while (i < n) {
      a(i) = false
      i += 1
    }
    a
  }

  private[graph] def doubles(n: Int): Array[Double] = {
    val a = new Array[Double](n)
    var i = 0
    while (i < n) {
      a(i) = 0.0
      i += 1
    }
    a
  }

  private[graph] def ints(n: Int): Array[Int] = {
    val a = new Array[Int](n)
    var i = 0
    while (i < n) {
      a(i) = 0
      i += 1
    }
    a
  }

  /** Which edges to turn round so none leads back: those a search meets pointing at a node it is still searching from. */
  private def breakCycles(n: Int, edges: Vector[(Int, Int)], loop: Vector[Boolean]): Array[Boolean] = {
    val m = edges.length
    val out = GraphLayout.lists(n)
    var e = m - 1
    while (e >= 0) {
      if (!loop(e)) out(edges(e)._1) = e :: out(edges(e)._1)
      e -= 1
    }
    val reversed = GraphLayout.flags(m)
    // 0 not met, 1 being searched from, 2 done
    val state = GraphLayout.ints(n)
    var root = 0
    while (root < n) {
      if (state(root) == 0) {
        state(root) = 1
        var stack: List[(Int, List[Int])] = List((root, out(root)))
        while (stack.nonEmpty) {
          val (v, rest) = stack.head
          if (rest.isEmpty) {
            state(v) = 2
            stack = stack.tail
          }
          else {
            stack = (v, rest.tail) :: stack.tail
            val w = edges(rest.head)._2
            if (state(w) == 1) reversed(rest.head) = true
            else if (state(w) == 0) {
              state(w) = 1
              stack = (w, out(w)) :: stack
            }
          }
        }
      }
      root += 1
    }
    reversed
  }

  /** Each node's layer: one past the furthest before it, then on towards its nearest successor. */
  private def layers(n: Int, m: Int, from: Array[Int], to: Array[Int], loop: Vector[Boolean]): Array[Int] = {
    val next = GraphLayout.lists(n)
    val waiting = GraphLayout.ints(n)
    var e = 0
    while (e < m) {
      if (!loop(e)) {
        next(from(e)) = to(e) :: next(from(e))
        waiting(to(e)) += 1
      }
      e += 1
    }
    // the nodes in an order where each comes after all that lead to it
    var ready = (0 until n).filter(v => waiting(v) == 0).toList
    var sorted: Vector[Int] = Vector.empty
    while (ready.nonEmpty) {
      val v = ready.head
      ready = ready.tail
      sorted = sorted :+ v
      next(v).foreach { w =>
        waiting(w) -= 1
        if (waiting(w) == 0) ready = w :: ready
      }
    }
    val layer = GraphLayout.ints(n)
    sorted.foreach(v => next(v).foreach(w => if (layer(v) + 1 > layer(w)) layer(w) = layer(v) + 1))
    sorted.reverse.foreach { v =>
      if (next(v).nonEmpty) {
        val nearest = next(v).map(w => layer(w)).min - 1
        if (nearest > layer(v)) layer(v) = nearest
      }
    }
    layer
  }

  /** The vertices of each layer in order: first as a search down from the top meets them, then sweeps by the
    * barycentre, keeping the order with the fewest crossings. */
  private def ordering(count: Int, layerCount: Int, vertexLayer: Vector[Int], up: Array[List[Int]],
                       down: Array[List[Int]]): Vector[Vector[Int]] = {
    var order = (0 until layerCount).toVector.map(_ => Vector.empty[Int])
    val met = GraphLayout.flags(count)
    var v = 0
    while (v < count) {
      if (!met(v) && up(v).isEmpty) {
        var stack = List(v)
        met(v) = true
        while (stack.nonEmpty) {
          val at = stack.head
          stack = stack.tail
          order = order.updated(vertexLayer(at), order(vertexLayer(at)) :+ at)
          down(at).reverse.foreach(w => if (!met(w)) {
            met(w) = true
            stack = w :: stack
          })
        }
      }
      v += 1
    }
    // what a search from the top never reached (every vertex has one before it, in a loop the layers broke)
    v = 0
    while (v < count) {
      if (!met(v)) {
        met(v) = true
        order = order.updated(vertexLayer(v), order(vertexLayer(v)) :+ v)
      }
      v += 1
    }
    val place = GraphLayout.ints(count)
    placeAll(order, place)
    var best = order
    var fewest = crossingsOf(order, down)
    val rounds = if (count < 600) 20 else if (count < 3000) 10 else 4
    var round = 0
    var worse = 0
    while (round < rounds && fewest > 0) {
      val downward = round % 2 == 0
      var l = if (downward) 1 else layerCount - 2
      while (l >= 0 && l < layerCount) {
        val by = if (downward) up else down
        val centre = order(l).map { w =>
          val near = by(w)
          if (near.isEmpty) place(w).toDouble else near.map(x => place(x).toDouble).sum / near.length
        }
        val sorted = order(l).indices.sortWith { (a, b) =>
          centre(a) < centre(b) || (centre(a) == centre(b) && a < b)
        }.map(i => order(l)(i)).toVector
        order = order.updated(l, sorted)
        var i = 0
        while (i < sorted.length) {
          place(sorted(i)) = i
          i += 1
        }
        l = if (downward) l + 1 else l - 1
      }
      if (count < 1500) order = transpose(order, up, down, place)
      val crossings = crossingsOf(order, down)
      if (crossings < fewest) {
        fewest = crossings
        best = order
        worse = 0
      }
      else {
        // two sweeps (one each way) that made it no better start again from the best
        worse += 1
        if (worse % 2 == 0) {
          order = best
          placeAll(order, place)
        }
      }
      round += 1
    }
    best
  }

  /** Neighbours in a layer swapped wherever that alone crosses fewer edges, until none is. */
  private def transpose(start: Vector[Vector[Int]], up: Array[List[Int]], down: Array[List[Int]],
                        place: Array[Int]): Vector[Vector[Int]] = {
    var order = start
    var better = true
    var passes = 0
    while (better && passes < 4) {
      better = false
      var l = 0
      while (l < order.length) {
        val layer = order(l).toArray
        var i = 0
        while (i + 1 < layer.length) {
          val u = layer(i)
          val v = layer(i + 1)
          if (pairCrossings(u, v, up, down, place) > pairCrossings(v, u, up, down, place)) {
            layer(i) = v
            layer(i + 1) = u
            place(u) = i + 1
            place(v) = i
            better = true
          }
          i += 1
        }
        order = order.updated(l, layer.toVector)
        l += 1
      }
      passes += 1
    }
    order
  }

  // how many of `u`'s edges cross `v`'s, with `u` just before `v`
  private def pairCrossings(u: Int, v: Int, up: Array[List[Int]], down: Array[List[Int]], place: Array[Int]): Int = {
    var crossings = 0
    up(u).foreach(a => up(v).foreach(b => if (place(a) > place(b)) crossings += 1))
    down(u).foreach(a => down(v).foreach(b => if (place(a) > place(b)) crossings += 1))
    crossings
  }

  private def placeAll(order: Vector[Vector[Int]], place: Array[Int]): Unit = order.foreach { layer =>
    var i = 0
    while (i < layer.length) {
      place(layer(i)) = i
      i += 1
    }
  }

  /** How many pairs of edges cross between each layer and the next: the inversions of their lower ends, read in the
    * order of their upper ends, counted in an accumulator tree (Barth, Jünger and Mutzel's). */
  private[graph] def crossingsOf(order: Vector[Vector[Int]], down: Array[List[Int]]): Int = {
    val place = GraphLayout.ints(down.length)
    placeAll(order, place)
    var total = 0
    var l = 0
    while (l + 1 < order.length) {
      val ends = order(l).flatMap(v => down(v).map(w => place(w)).sorted)
      // a leaf for each place in the lower layer; each end counts those already in, to the right of it
      var first = 1
      while (first < order(l + 1).length) first *= 2
      val tree = GraphLayout.ints(first * 2 - 1)
      ends.foreach { at =>
        var index = at + first - 1
        tree(index) += 1
        while (index > 0) {
          if (index % 2 == 1) total += tree(index + 1)
          index = (index - 1) / 2
          tree(index) += 1
        }
      }
      l += 1
    }
    total
  }

  /** Each vertex's centre across the flow: packed in order, then pulled towards its neighbours' median a layer at a
    * time, down, up, and both at the end, as near as the order and the gaps allow. */
  private def places(order: Vector[Vector[Int]], up: Array[List[Int]], down: Array[List[Int]], room: Array[Double],
                     weight: Array[Double], n: Int, nodeGap: Double): Array[Double] = {
    val centre = GraphLayout.doubles(room.length)
    order.foreach { layer =>
      var at = 0.0
      var i = 0
      while (i < layer.length) {
        if (i > 0) at += gap(layer(i - 1), layer(i), room, n, nodeGap)
        centre(layer(i)) = at
        i += 1
      }
    }
    // down, up, down, up, then both ways twice
    val passes = List(0, 1, 0, 1, 0, 1, 2, 2)
    passes.foreach { pass =>
      val layers = if (pass == 1) order.indices.reverse else order.indices
      layers.foreach { l =>
        val layer = order(l)
        val wanted = layer.map { v =>
          val near = if (pass == 0) up(v) else if (pass == 1) down(v) else up(v) ++ down(v)
          if (near.isEmpty) centre(v) else median(near.map(w => centre(w)).sorted)
        }
        fit(layer, wanted, room, weight, n, nodeGap, centre)
      }
    }
    centre
  }

  private def median(sorted: List[Double]): Double = {
    val k = sorted.length
    if (k % 2 == 1) sorted(k / 2) else (sorted(k / 2 - 1) + sorted(k / 2)) / 2.0
  }

  // how far apart two neighbours in a layer are kept, centre to centre
  private def gap(a: Int, b: Int, room: Array[Double], n: Int, nodeGap: Double): Double =
    (room(a) + room(b)) / 2.0 + (if (a >= n && b >= n) 10.0 else if (a >= n || b >= n) nodeGap * 0.75 else nodeGap)

  /** The places nearest `wanted` (weighted, least squares) that keep `layer`'s order and gaps: the offsets of the gaps
    * taken off, what is left must not fall, which pool-adjacent-violators solves exactly. */
  private def fit(layer: Vector[Int], wanted: Vector[Double], room: Array[Double], weight: Array[Double], n: Int,
                  nodeGap: Double, centre: Array[Double]): Unit = {
    val k = layer.length
    val offset = GraphLayout.doubles(k)
    var i = 1
    while (i < k) {
      offset(i) = offset(i - 1) + gap(layer(i - 1), layer(i), room, n, nodeGap)
      i += 1
    }
    // blocks of equal value: their value, weight and how many they hold
    val value = GraphLayout.doubles(k)
    val mass = GraphLayout.doubles(k)
    val size = GraphLayout.ints(k)
    var blocks = 0
    i = 0
    while (i < k) {
      value(blocks) = wanted(i) - offset(i)
      mass(blocks) = weight(layer(i))
      size(blocks) = 1
      blocks += 1
      while (blocks > 1 && value(blocks - 2) > value(blocks - 1)) {
        val total = mass(blocks - 2) + mass(blocks - 1)
        value(blocks - 2) = (value(blocks - 2) * mass(blocks - 2) + value(blocks - 1) * mass(blocks - 1)) / total
        mass(blocks - 2) = total
        size(blocks - 2) += size(blocks - 1)
        blocks -= 1
      }
      i += 1
    }
    var at = 0
    var b = 0
    while (b < blocks) {
      var j = 0
      while (j < size(b)) {
        centre(layer(at)) = value(b) + offset(at)
        at += 1
        j += 1
      }
      b += 1
    }
  }

  /** Each edge's points from its source's side to its target's, in the world. An edge leaves its node from a place
    * along its side of its own, in the order of where the edges go, and arrives the same way. */
  private def route(chains: Vector[Vector[Int]], reversed: Array[Boolean], loop: Vector[Boolean], boxes: Vector[Rect],
                    vertexLayer: Vector[Int], centre: Array[Double], start: Array[Double], depth: Array[Double],
                    order: Vector[Vector[Int]], n: Int, across: Boolean): Vector[GraphRoute] = {
    // where each edge leaves its first vertex and reaches its last, across the flow, spread along their sides
    val leave = GraphLayout.doubles(chains.length)
    val reach = GraphLayout.doubles(chains.length)
    val outs = GraphLayout.lists(n)
    val ins = GraphLayout.lists(n)
    chains.indices.foreach { e =>
      if (!loop(e)) {
        outs(chains(e).head) = e :: outs(chains(e).head)
        ins(chains(e).last) = e :: ins(chains(e).last)
      }
    }
    (0 until n).foreach { v =>
      val room = if (across) boxes(v).height else boxes(v).width
      spread(outs(v).sortBy(e => centre(chains(e)(1))), room, leave)
      spread(ins(v).sortBy(e => centre(chains(e)(chains(e).length - 2))), room, reach)
    }
    chains.indices.toVector.map { e =>
      val chain = chains(e)
      if (loop(e)) {
        val b = boxes(chain.head)
        GraphRoute(Vector((b.right, b.y)), loop = true)
      }
      else {
        val first = chain.head
        val last = chain.last
        var points: Vector[(Double, Double)] = Vector.empty
        // along and across the flow, turned into the world's x and y
        val fb = boxes(first)
        points = points :+ (if (across) (fb.right, centre(first) + leave(e)) else (centre(first) + leave(e), fb.bottom))
        var i = 1
        while (i < chain.length - 1) {
          val d = chain(i)
          val l = vertexLayer(d)
          points = points :+ (if (across) (start(l), centre(d)) else (centre(d), start(l)))
          points = points :+ (if (across) (start(l) + depth(l), centre(d)) else (centre(d), start(l) + depth(l)))
          i += 1
        }
        val lb = boxes(last)
        points = points :+ (if (across) (lb.x, centre(last) + reach(e)) else (centre(last) + reach(e), lb.y))
        GraphRoute(if (reversed(e)) points.reverse else points, reversed(e))
      }
    }
  }

  // the edges `es` leave (or reach) a side `room` long at places spread about its middle, in their order
  private def spread(es: List[Int], room: Double, at: Array[Double]): Unit = {
    val k = es.length
    if (k > 1) {
      val width = math.min(room * 0.6, k * 10.0)
      var i = 0
      es.foreach { e =>
        at(e) = -width / 2.0 + width * (i + 0.5) / k
        i += 1
      }
    }
  }
}
