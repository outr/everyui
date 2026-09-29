package everyui.graph

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The force layout: the same layout for the same seed, settling and stopping, an added node settling in without the
  * rest moving far, pins and drags, sizes kept apart, and how long 100, 1,000 and 3,000 nodes take. */
class ForceLayoutSpec extends AnyWordSpec with Matchers {
  // a co-occurrence graph of `n` words: a few clusters, each word seen with a few in its own and now and then another
  private def words(n: Int, seed: Long): (Vector[ForceNode], Vector[ForceEdge]) = {
    val r = new scala.util.Random(seed)
    val clusters = math.max(1, n / 25)
    val nodes = (0 until n).toVector.map(i => ForceNode.circle("w" + i, 4.0 + r.nextInt(8), 1.0 + r.nextDouble()))
    val edges = (0 until n * 2).toVector.flatMap { _ =>
      val a = r.nextInt(n)
      val b = if (r.nextInt(8) == 0) r.nextInt(n) else {
        val c = a % clusters
        c + clusters * r.nextInt(math.max(1, (n - c + clusters - 1) / clusters))
      }
      if (a != b && b < n) Some(ForceEdge("w" + a, "w" + b, strength = 0.3 + r.nextDouble())) else None
    }
    (nodes, edges)
  }

  private def distance(a: (Double, Double), b: (Double, Double)): Double =
    math.sqrt((a._1 - b._1) * (a._1 - b._1) + (a._2 - b._2) * (a._2 - b._2))

  private def overlaps(layout: ForceLayout): Int = {
    var count = 0
    val n = layout.nodes.length
    for (i <- 0 until n; j <- i + 1 until n) {
      val a = layout.nodes(i)
      val b = layout.nodes(j)
      val gap = if (a.round && b.round) distance((layout.x(i), layout.y(i)), (layout.x(j), layout.y(j))) - a.reach - b.reach
        else {
          val bx = layout.boxOf(i)
          val by = layout.boxOf(j)
          -math.min(math.min(bx.right - by.x, by.right - bx.x), math.min(bx.bottom - by.y, by.bottom - bx.y))
        }
      // (a pixel's leeway: collision is a soft force, and settles to within it)
      if (gap < -1.0) count += 1
    }
    count
  }

  "A force layout" should {
    "lay out the same way every time for a seed, and another way for another seed" in {
      val (nodes, edges) = words(200, 7)
      def run(seed: Int): Vector[(Double, Double)] = {
        val layout = new ForceLayout(ForceSettings(seed = seed))
        layout.update(nodes, edges)
        layout.settle()
        layout.positions
      }
      run(1) shouldBe run(1)
      run(1) should not be run(2)
    }
    "cool, settle and stop by itself, and move on again when reheated" in {
      val (nodes, edges) = words(100, 3)
      val layout = new ForceLayout
      layout.update(nodes, edges)
      layout.alpha shouldBe 1.0
      layout.settled shouldBe false
      val ticks = layout.settle()
      info(s"settled in $ticks ticks")
      layout.settled shouldBe true
      ticks should (be > 250 and be < 350)
      // settled, the nodes are all but still
      val before = layout.positions
      layout.tick()
      before.indices.map(i => distance(before(i), layout.positions(i))).max should be < 0.5
      layout.reheat(0.5)
      layout.settled shouldBe false
      layout.alpha shouldBe 0.5
      layout.settle() should be > 0
    }
    "pull linked nodes together and push the rest apart" in {
      val layout = new ForceLayout
      layout.update(Vector("a", "b", "c", "d").map(k => ForceNode.circle(k, 5.0)),
        Vector(ForceEdge("a", "b"), ForceEdge("c", "d")))
      layout.settle()
      def apart(a: String, b: String): Double = distance(layout.positionOf(a).get, layout.positionOf(b).get)
      // an edge's length is its ends' edges apart: 30 plus the two radii
      apart("a", "b") shouldBe 40.0 +- 4.0
      apart("c", "d") shouldBe 40.0 +- 4.0
      List(apart("a", "c"), apart("a", "d"), apart("b", "c"), apart("b", "d")).sum / 4.0 should be > 50.0
    }
    "keep an added node beside its neighbours and the rest where they were" in {
      val (nodes, edges) = words(300, 11)
      val layout = new ForceLayout
      layout.update(nodes, edges)
      layout.settle()
      val before = layout.positions
      val box = layout.bounds.get
      layout.update(nodes :+ ForceNode.circle("new", 6.0), edges ++ Vector(ForceEdge("new", "w5"), ForceEdge("new", "w30")))
      layout.alpha shouldBe ForceSettings().reheat
      val start = layout.positionOf("new").get
      val middle = ((layout.x(5) + layout.x(30)) / 2.0, (layout.y(5) + layout.y(30)) / 2.0)
      distance(start, middle) should be < 60.0
      layout.settle()
      val moved = before.indices.map(i => distance(before(i), layout.positions(i))).sorted
      val size = math.max(box.width, box.height)
      info(f"graph ${box.width}%.0f by ${box.height}%.0f; nodes moved by a median ${moved(moved.length / 2)}%.1f, " +
        f"at most ${moved.last}%.1f")
      moved(moved.length / 2) should be < size * 0.01
      moved(moved.length * 9 / 10) should be < size * 0.03
      // no more than it moves warmed as much with nothing changed: what little moves is the layout settling further
      val still = new ForceLayout
      still.update(nodes, edges)
      still.settle()
      val was = still.positions
      still.reheat(ForceSettings().reheat)
      still.settle()
      val drift = was.indices.map(i => distance(was(i), still.positions(i))).sorted
      info(f"warmed with nothing changed, a median ${drift(drift.length / 2)}%.1f")
      moved(moved.length / 2) should be < drift(drift.length / 2) * 1.5 + 1.0
      // and one taken away leaves the rest where they were, by key
      val keep = layout.positionOf("w100").get
      layout.update(layout.nodes.filterNot(_.key == "w0"), layout.edges)
      layout.positionOf("w100").get shouldBe keep
      layout.indexOf("w0") shouldBe None
    }
    "hold a pinned node where it is put, and let it go" in {
      val (nodes, edges) = words(100, 5)
      val layout = new ForceLayout
      layout.update(nodes, edges)
      layout.pin("w3", 500.0, -200.0)
      layout.settle()
      layout.positionOf("w3").get shouldBe ((500.0, -200.0))
      layout.pinned("w3") shouldBe true
      // its neighbours followed it out
      val w3 = layout.indexOf("w3").get
      val near = layout.edges.filter(e => e.from == "w3" || e.to == "w3").map(e => if (e.from == "w3") e.to else e.from)
      near.foreach(k => layout.positionOf(k).get._1 should be > 250.0)
      layout.unpin("w3")
      layout.reheat()
      layout.settle()
      layout.x(w3) should be < 450.0
    }
    "follow a drag, pinned while dragged, and settle once dropped" in {
      val (nodes, edges) = words(100, 9)
      val layout = new ForceLayout
      layout.update(nodes, edges)
      layout.settle()
      layout.drag("w7", 300.0, 300.0)
      layout.pinned("w7") shouldBe true
      // warm while it is dragged, however long
      layout.settle(1000) shouldBe 1000
      layout.settled shouldBe false
      layout.positionOf("w7").get shouldBe ((300.0, 300.0))
      layout.drop("w7")
      layout.pinned("w7") shouldBe false
      layout.settle() should be < 400
      layout.settled shouldBe true
      // a node pinned before a drag stays pinned after
      layout.pin("w8", 0.0, 0.0)
      layout.drag("w8", 50.0, 50.0)
      layout.drop("w8")
      layout.pinned("w8") shouldBe true
      layout.positionOf("w8").get shouldBe ((50.0, 50.0))
    }
    "keep nodes of every size from overlapping, round or boxes" in {
      val (nodes, edges) = words(300, 13)
      val big = nodes.zipWithIndex.map { case (n, i) => if (i % 10 == 0) ForceNode.circle(n.key, 30.0, 4.0) else n }
      val layout = new ForceLayout
      layout.update(big, edges)
      layout.settle()
      overlaps(layout) shouldBe 0
      val boxes = new ForceLayout(ForceSettings(linkLength = 20.0))
      boxes.update(Vector.tabulate(120)(i => ForceNode.box("b" + i, 60.0 + (i % 5) * 20.0, 24.0)),
        Vector.tabulate(200)(i => ForceEdge("b" + (i % 120), "b" + ((i * 7 + 3) % 120))))
      boxes.settle()
      overlaps(boxes) shouldBe 0
      // a bigger node pushes the others further off
      val two = new ForceLayout
      two.update(Vector(ForceNode.circle("a", 5.0), ForceNode.circle("b", 5.0), ForceNode.circle("c", 5.0, weight = 8.0)),
        Vector.empty)
      two.settle()
      distance(two.positionOf("c").get, two.positionOf("a").get) should be > distance(two.positionOf("a").get,
        two.positionOf("b").get)
    }
    "lean a directed graph's edges along its flow" in {
      val nodes = Vector.tabulate(20)(i => ForceNode.circle("n" + i, 5.0))
      val edges = Vector.tabulate(19)(i => ForceEdge("n" + i, "n" + (i + 1)))
      val layout = new ForceLayout(ForceSettings(flow = Some(GraphFlow.LeftToRight)))
      layout.update(nodes, edges)
      layout.settle()
      (0 until 19).count(i => layout.x(i + 1) > layout.x(i)) should be >= 18
    }
    "find the node at a point" in {
      val layout = new ForceLayout
      layout.update(Vector(ForceNode.circle("a", 10.0), ForceNode.box("b", 80.0, 20.0)), Vector.empty)
      layout.settle()
      val (ax, ay) = layout.positionOf("a").get
      layout.nodeAt(ax + 9.0, ay) shouldBe Some("a")
      val b = layout.boxOf(layout.indexOf("b").get)
      layout.nodeAt(b.right - 1.0, b.bottom - 1.0) shouldBe Some("b")
      layout.nodeAt(ax + 500.0, ay + 500.0) shouldBe None
    }
    "settle 100, 1,000 and 3,000 nodes in good time" in {
      List(100, 1000, 3000).foreach { n =>
        val (nodes, edges) = words(n, n.toLong)
        // (once to warm the JIT)
        val warm = new ForceLayout
        warm.update(nodes, edges)
        warm.settle()
        val layout = new ForceLayout
        layout.update(nodes, edges)
        val start = System.nanoTime()
        val ticks = layout.settle()
        val ms = (System.nanoTime() - start) / 1e6
        info(f"$n%d nodes, ${layout.edges.length}%d edges: settled in $ticks%d ticks, $ms%.0f ms, ${ms / ticks}%.2f ms a tick")
        layout.settled shouldBe true
        ms should be < 60000.0
      }
    }
  }
}
