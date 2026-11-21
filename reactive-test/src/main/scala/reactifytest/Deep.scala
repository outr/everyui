package reactifytest

import reactify.*

/** Chains of fires deeper than reactify's depth budget (8), the shape of a computed-size layout read up a dozen links
  * that overran a Roku's stack (the Detail screen): the chain finishes in the same turn on a bounded stack, the links
  * deferred past the budget run in the order they were deferred, and one that throws leaves none of the others to run
  * at the next, unrelated, fire. */
object Deep {
  import ReactifySuite.check

  /** A channel whose fire reaches `deep` seven links down, so that `deep`'s reactions run at the depth budget and what
    * they fire is deferred. */
  private def atBudget(deep: Channel[Int]): Channel[Int] = {
    val top = Channel[Int]
    var last = top
    var i = 1
    while (i < 7) {
      val next = Channel[Int]
      last.attach(v => next @= v)
      last = next
      i += 1
    }
    last.attach(v => deep @= v)
    top
  }

  def chains(ok: Box, total: Box): Unit = {
    val source = Var(0)
    var chain: Val[Int] = source
    var i = 0
    while (i < 300) {
      val before = chain
      chain = Val(before() + 1)
      i += 1
    }
    val end = chain
    val seen = new Box(-1)
    end.attach(v => seen.n = v)
    source @= 1
    check(ok, total, "deep.chain.heard", seen.n.toString, "301")
    check(ok, total, "deep.chain.value", end().toString, "301")

    // 2,000 fires from the deepest reaction, each deferred: they run in order
    val next = new Box(0)
    val misplaced = new Box(0)
    val channels = (0 until 2000).toList.map { index =>
      val c = Channel[Int]
      c.attach(_ => if (index == next.n) next.n = next.n + 1 else misplaced.n = misplaced.n + 1)
      c
    }
    val deep = Channel[Int]
    deep.attach(_ => channels.foreach(c => c @= 1))
    val top = atBudget(deep)
    top @= 1
    check(ok, total, "deep.deferred.order", next.n.toString + "/" + misplaced.n.toString, "2000/0")

    // the second of ten deferred fires throws: the eight behind it are dropped, not run at the next fire
    val heard = new Log(Nil)
    val ten = (0 until 10).toList.map { index =>
      val c = Channel[Int]
      c.attach(_ => heard.add(index.toString))
      c
    }
    ten(1).attach(_ => throw new RuntimeException("deferred"))
    val throwing = Channel[Int]
    throwing.attach(_ => ten.foreach(c => c @= 1))
    val throwingTop = atBudget(throwing)
    val threw =
      try {
        throwingTop @= 1
        "no"
      } catch {
        case _: Throwable => "yes"
      }
    check(ok, total, "deep.throw.thrown", threw, "yes")
    val other = Channel[Int]
    other.attach(_ => heard.add("other"))
    other @= 1
    check(ok, total, "deep.throw.nothing.stale", heard.show(), "0,1,other")
  }
}
