package everyui.graph

import everyui.spatial.Size
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The layered layout: layers that follow the edges, a cycle broken, crossings taken out, nodes kept apart, long edges
  * kept straight, and how long it takes for 50, 500 and 2,000 nodes. */
class GraphLayoutSpec extends AnyWordSpec with Matchers {
  private def sizes(n: Int): Vector[Size] = Vector.fill(n)(Size(120.0, 40.0))

  // a random graph of `n` nodes that mostly runs one way, with about 1.5 edges a node and a few back
  private def random(n: Int, seed: Long): Vector[(Int, Int)] = {
    val r = new scala.util.Random(seed)
    (0 until (n * 3 / 2)).toVector.map { _ =>
      val a = r.nextInt(n)
      val b = if (r.nextInt(20) == 0) r.nextInt(n) else math.min(n - 1, a + 1 + r.nextInt(math.max(1, n / 10)))
      (a, b)
    }.filter(e => e._1 != e._2).distinct
  }

  "A graph layout" should {
    "put each node a layer past those that lead to it, along the flow" in {
      val plan = GraphLayout.layout(sizes(3), Vector((0, 1), (1, 2)), GraphFlow.LeftToRight)
      plan.layerOf shouldBe Vector(0, 1, 2)
      plan.nodes(1).x should be > plan.nodes(0).right
      plan.routes(0).points.head shouldBe ((plan.nodes(0).right, plan.nodes(0).centerY))
      plan.routes(0).points.last shouldBe ((plan.nodes(1).x, plan.nodes(1).centerY))
      val down = GraphLayout.layout(sizes(3), Vector((0, 1), (1, 2)), GraphFlow.TopToBottom)
      down.nodes(1).y should be > down.nodes(0).bottom
      down.routes(1).points.last shouldBe ((down.nodes(2).centerX, down.nodes(2).y))
    }
    "turn round one edge of a cycle, and still draw it from its source" in {
      val plan = GraphLayout.layout(sizes(3), Vector((0, 1), (1, 2), (2, 0)), GraphFlow.LeftToRight)
      plan.routes.count(_.reversed) shouldBe 1
      val back = plan.routes(2)
      back.reversed shouldBe true
      // from node 2 back to node 0, through a point in layer 1
      back.points.head._1 shouldBe plan.nodes(2).x
      back.points.last._1 shouldBe plan.nodes(0).right
      back.points.length shouldBe 4
    }
    "draw a node's edge to itself as a loop" in {
      val plan = GraphLayout.layout(sizes(2), Vector((0, 0), (0, 1)), GraphFlow.LeftToRight)
      plan.routes(0).loop shouldBe true
      plan.layerOf shouldBe Vector(0, 1)
    }
    "take out crossings" in {
      // a search down from each node in turn meets them in an order that crosses; the sweeps undo it
      val twisted = GraphLayout.layout(sizes(6), Vector((0, 3), (0, 4), (1, 2), (1, 5), (2, 5), (3, 4)),
        GraphFlow.LeftToRight)
      twisted.crossings shouldBe 0
      val edges = Vector((0, 4), (0, 5), (1, 3), (1, 5), (2, 3), (2, 4), (3, 7), (4, 6), (5, 8), (3, 8))
      val grid = GraphLayout.layout(sizes(9), edges, GraphFlow.LeftToRight)
      // the fewest any order of the three layers has, tried one by one
      val least = (for (a <- (0 to 2).permutations; b <- (3 to 5).permutations; c <- (6 to 8).permutations) yield {
        val at = (a ++ b ++ c).zipWithIndex.map { case (v, i) => v -> (i % 3) }.toMap
        edges.combinations(2).count { case Seq((p, q), (r, s)) =>
          p / 3 == r / 3 && (at(p) - at(r)) * (at(q) - at(s)) < 0
        }
      }).min
      info("a 3-3-3 graph: " + grid.crossings.toString + " crossings, the least possible " + least.toString)
      grid.crossings should be <= least + 1
    }
    "keep the nodes of a layer apart, and a long edge straight" in {
      val plan = GraphLayout.layout(sizes(5), Vector((0, 1), (1, 2), (2, 3), (0, 3), (4, 3), (4, 1)),
        GraphFlow.LeftToRight, nodeGap = 24.0)
      plan.layers.foreach { layer =>
        layer.sliding(2).filter(_.length == 2).foreach(p => plan.nodes(p(1)).y should be >= plan.nodes(p(0)).bottom + 23.999)
      }
      val long = plan.routes(3).points
      long.length shouldBe 6
      long(1)._2 shouldBe long(4)._2 +- 0.5
    }
    "lay out 50, 500 and 2,000 nodes in good time" in {
      List(50, 500, 2000).foreach { n =>
        val edges = random(n, n.toLong)
        GraphLayout.layout(sizes(n), edges, GraphFlow.LeftToRight)
        val start = System.nanoTime()
        val plan = GraphLayout.layout(sizes(n), edges, GraphFlow.LeftToRight)
        val ms = (System.nanoTime() - start) / 1e6
        info(f"$n%d nodes, ${edges.length}%d edges: $ms%.0f ms, ${plan.layers.length}%d layers, ${plan.crossings}%d crossings")
        plan.nodes.length shouldBe n
        ms should be < 20000.0
      }
    }
  }
}
