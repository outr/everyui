package everyui.graph

/** A quadtree over a force layout's nodes, in plain arrays: each cell a square, cut into four as points arrive, down
  * to a depth past which a cell keeps all that land in it. Built again each tick, it answers two things: the push of
  * every node on one (`repel`, Barnes and Hut's method: a far cell pushes as one from its centre), and which nodes are
  * near a box (`near`). */
private[graph] class ForceTree {
  private var capacity = 0
  private var count = 0
  // each cell's corner and side; whether it is cut into four, and its quarters (-1 for none); a leaf's first point
  private var left: Array[Double] = new Array[Double](0)
  private var top: Array[Double] = new Array[Double](0)
  private var side: Array[Double] = new Array[Double](0)
  private var inner: Array[Boolean] = new Array[Boolean](0)
  private var quarters: Array[Int] = new Array[Int](0)
  private var first: Array[Int] = new Array[Int](0)
  // each cell's charge, and the weight and centre of what is in it
  private var charge: Array[Double] = new Array[Double](0)
  private var mass: Array[Double] = new Array[Double](0)
  private var middleX: Array[Double] = new Array[Double](0)
  private var middleY: Array[Double] = new Array[Double](0)
  private var stack: Array[Int] = new Array[Int](0)
  // the points: where each is, and the next in its leaf (-1 for none)
  private var xs: Array[Double] = new Array[Double](0)
  private var ys: Array[Double] = new Array[Double](0)
  private var after: Array[Int] = new Array[Int](0)

  /** The tree over the first `n` of the points (`x`, `y`). */
  def build(n: Int, x: Array[Double], y: Array[Double]): Unit = {
    xs = x
    ys = y
    if (after.length < n) after = GraphLayout.ints(n)
    if (capacity < n * 2 + 8) grow(n * 2 + 8)
    count = 0
    var least = 0.0
    var most = 0.0
    var leastY = 0.0
    var mostY = 0.0
    var i = 0
    while (i < n) {
      if (i == 0 || x(i) < least) least = x(i)
      if (i == 0 || x(i) > most) most = x(i)
      if (i == 0 || y(i) < leastY) leastY = y(i)
      if (i == 0 || y(i) > mostY) mostY = y(i)
      i += 1
    }
    cell(least, leastY, math.max(most - least, mostY - leastY) + 1.0)
    i = 0
    while (i < n) {
      insert(i)
      i += 1
    }
  }

  /** Each cell's charge, from `q`, each point's (negative pushes away), and the centre of what is in it. */
  def weigh(q: Array[Double]): Unit = {
    // a cell is made after the one it is in, so from the last back each is weighed after its quarters
    var c = count - 1
    while (c >= 0) {
      var sum = 0.0
      var m = 0.0
      var sx = 0.0
      var sy = 0.0
      if (inner(c)) {
        var k = 0
        while (k < 4) {
          val d = quarters(c * 4 + k)
          if (d >= 0) {
            sum += charge(d)
            m += mass(d)
            sx += middleX(d) * mass(d)
            sy += middleY(d) * mass(d)
          }
          k += 1
        }
      }
      else {
        var p = first(c)
        while (p >= 0) {
          val w = math.abs(q(p))
          sum += q(p)
          m += w
          sx += xs(p) * w
          sy += ys(p) * w
          p = after(p)
        }
      }
      charge(c) = sum
      mass(c) = m
      middleX(c) = if (m > 0.0) sx / m else left(c) + side(c) / 2.0
      middleY(c) = if (m > 0.0) sy / m else top(c) + side(c) / 2.0
      c -= 1
    }
  }

  /** The push on point `i` at (`x`, `y`) from all the others, `q` their charges (negative pushes away): as a force
    * falling off with distance, no nearer than `nearest` and none past `farthest` (both squared), and a cell taken
    * whole once its side is `theta` (squared) of its distance or less. */
  def repel(i: Int, x: Double, y: Double, q: Array[Double], alpha: Double, theta: Double, nearest: Double,
            farthest: Double, random: ForceRandom): (Double, Double) = {
    var fx = 0.0
    var fy = 0.0
    var depth = 1
    stack(0) = 0
    while (depth > 0) {
      depth -= 1
      val c = stack(depth)
      if (mass(c) > 0.0) {
        val dx = middleX(c) - x
        val dy = middleY(c) - y
        val l = dx * dx + dy * dy
        val w = side(c)
        if (inner(c)) {
          if (w * w / theta < l) {
            if (l < farthest) {
              val d = if (l < nearest) math.sqrt(nearest * l) else l
              fx += dx * charge(c) * alpha / d
              fy += dy * charge(c) * alpha / d
            }
          }
          else {
            var k = 0
            while (k < 4) {
              val sub = quarters(c * 4 + k)
              if (sub >= 0) {
                stack(depth) = sub
                depth += 1
              }
              k += 1
            }
          }
        }
        else {
          var p = first(c)
          while (p >= 0) {
            if (p != i) {
              var px = xs(p) - x
              var py = ys(p) - y
              if (px == 0.0) px = random.jiggle()
              if (py == 0.0) py = random.jiggle()
              val l2 = px * px + py * py
              if (l2 < farthest) {
                val d = if (l2 < nearest) math.sqrt(nearest * l2) else l2
                fx += px * q(p) * alpha / d
                fy += py * q(p) * alpha / d
              }
            }
            p = after(p)
          }
        }
      }
    }
    (fx, fy)
  }

  /** Each point in the box from (`x0`, `y0`) to (`x1`, `y1`), or near it (those in the cells it meets). */
  def near(x0: Double, y0: Double, x1: Double, y1: Double, visit: Int => Unit): Unit = {
    var depth = 1
    stack(0) = 0
    while (depth > 0) {
      depth -= 1
      val c = stack(depth)
      if (left(c) <= x1 && left(c) + side(c) >= x0 && top(c) <= y1 && top(c) + side(c) >= y0) {
        if (inner(c)) {
          var k = 0
          while (k < 4) {
            val sub = quarters(c * 4 + k)
            if (sub >= 0) {
              stack(depth) = sub
              depth += 1
            }
            k += 1
          }
        }
        else {
          var p = first(c)
          while (p >= 0) {
            visit(p)
            p = after(p)
          }
        }
      }
    }
  }

  // point `i` put in the leaf it falls in, cutting a leaf into four where two points meet in it (down to a depth past
  // which a leaf keeps all that land in it, or all in one place)
  private def insert(i: Int): Unit = {
    val x = xs(i)
    val y = ys(i)
    var c = 0
    var depth = 0
    var done = false
    while (!done) {
      if (inner(c)) {
        val half = side(c) / 2.0
        val right = x >= left(c) + half
        val down = y >= top(c) + half
        val q = (if (right) 1 else 0) + (if (down) 2 else 0)
        val sub = quarters(c * 4 + q)
        if (sub < 0) {
          val made = cell(if (right) left(c) + half else left(c), if (down) top(c) + half else top(c), half)
          quarters(c * 4 + q) = made
          first(made) = i
          after(i) = -1
          done = true
        }
        else {
          c = sub
          depth += 1
        }
      }
      else if (first(c) < 0) {
        first(c) = i
        after(i) = -1
        done = true
      }
      else if (depth >= 24 || (xs(first(c)) == x && ys(first(c)) == y)) {
        after(i) = first(c)
        first(c) = i
        done = true
      }
      else {
        // the leaf's point (or points, all in one place) go down into the quarter they fall in, and `i` follows
        val p = first(c)
        val half = side(c) / 2.0
        val right = xs(p) >= left(c) + half
        val down = ys(p) >= top(c) + half
        val q = (if (right) 1 else 0) + (if (down) 2 else 0)
        val made = cell(if (right) left(c) + half else left(c), if (down) top(c) + half else top(c), half)
        inner(c) = true
        first(c) = -1
        quarters(c * 4 + q) = made
        first(made) = p
      }
    }
  }

  private def cell(x: Double, y: Double, s: Double): Int = {
    if (count >= capacity) grow(capacity * 2)
    val c = count
    count += 1
    left(c) = x
    top(c) = y
    side(c) = s
    inner(c) = false
    first(c) = -1
    var k = 0
    while (k < 4) {
      quarters(c * 4 + k) = -1
      k += 1
    }
    c
  }

  private def grow(to: Int): Unit = {
    left = more(left, to)
    top = more(top, to)
    side = more(side, to)
    charge = more(charge, to)
    mass = more(mass, to)
    middleX = more(middleX, to)
    middleY = more(middleY, to)
    val flags = GraphLayout.flags(to)
    val firsts = GraphLayout.ints(to)
    val fours = GraphLayout.ints(to * 4)
    var c = 0
    while (c < count) {
      flags(c) = inner(c)
      firsts(c) = first(c)
      var k = 0
      while (k < 4) {
        fours(c * 4 + k) = quarters(c * 4 + k)
        k += 1
      }
      c += 1
    }
    inner = flags
    first = firsts
    quarters = fours
    // (a path down the tree holds at most three cells a level beside the one it is in)
    stack = GraphLayout.ints(to + 8)
    capacity = to
  }

  private def more(a: Array[Double], to: Int): Array[Double] = {
    val b = GraphLayout.doubles(to)
    var i = 0
    while (i < count) {
      b(i) = a(i)
      i += 1
    }
    b
  }
}
