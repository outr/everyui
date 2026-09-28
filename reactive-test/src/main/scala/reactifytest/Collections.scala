package reactifytest

/** The emitter's collection operators: a Map joined with another Map, or with pairs from anywhere, is a Map (a lookup
  * in it works), a Set joined with anything is a Set, and a List joined with a List is a List. */
object Collections {
  import ReactifySuite.check

  private def show(m: Map[String, String]): String = m.toList.sortBy(_._1).map(t => t._1 + "=" + t._2).mkString(",")
  private def showSet(s: Set[Int]): String = s.toList.sorted.mkString(",")

  def maps(ok: Box, total: Box, done: Boolean): Unit = {
    val steps = List("a" -> "1", "b" -> "2")
    val known = steps.toMap ++ (if (done) Map("done" -> "done") else Map.empty)
    check(ok, total, "emit.map.concat.map", known.getOrElse("done", "-") + " " + known.getOrElse("a", "-"), "done 1")
    val base = Map("a" -> "1")
    check(ok, total, "emit.map.concat.list", show(base ++ List("b" -> "2", "a" -> "9")), "a=9,b=2")
    check(ok, total, "emit.map.concat.vector", show(base ++ Vector("c" -> "3")), "a=1,c=3")
    check(ok, total, "emit.map.concat.some", show(base ++ Some("d" -> "4")), "a=1,d=4")
    check(ok, total, "emit.map.concat.none", show(base ++ (None: Option[(String, String)])), "a=1")
    check(ok, total, "emit.map.concat.method", show(base.concat(Map("f" -> "6"))), "a=1,f=6")
    check(ok, total, "emit.map.concat.empty", show(Map.empty[String, String] ++ base), "a=1")
    check(ok, total, "emit.map.concat.lookup", (base ++ Map("g" -> "7")).get("g").getOrElse("-"), "7")
    check(ok, total, "emit.map.plus.pairs", show(base + ("h" -> "8", "i" -> "9")), "a=1,h=8,i=9")
    check(ok, total, "emit.map.minus.keys", show((base ++ steps) -- List("a", "z")), "b=2")
    check(ok, total, "emit.map.minus.many", show((base ++ steps) - ("a", "b")), "")
    check(ok, total, "emit.map.from", show(Map.from(steps)), "a=1,b=2")
    check(ok, total, "emit.map.tomap.concat", show((steps ++ List("c" -> "3")).toMap), "a=1,b=2,c=3")
    check(ok, total, "emit.map.tomap.map", show((base ++ steps).toMap), "a=1,b=2")
    check(ok, total, "emit.map.toset", Map("a" -> "1").toSet.map(t => t._1 + t._2).mkString(","), "a1")
    check(ok, total, "emit.map.prepend", (List("z" -> "0") ++: base).toList.map(_._1).mkString(","), "z,a")
    check(ok, total, "emit.map.concat.keyed", show(base.map(t => t._1 -> (t._2 + "!")) ++ Map("A" -> "2")), "A=2,a=1!")
    val keyed = Map(1 -> "one")
    check(ok, total, "emit.map.concat.intkeys", (keyed ++ Map(2 -> "two")).getOrElse(2, "-"), "two")
  }

  def sets(ok: Box, total: Box): Unit = {
    val s = Set(1, 2)
    check(ok, total, "emit.set.concat.set", showSet(s ++ Set(2, 3)), "1,2,3")
    check(ok, total, "emit.set.concat.list", showSet(s ++ List(3, 3, 4)), "1,2,3,4")
    check(ok, total, "emit.set.concat.some", showSet(s ++ Some(5)), "1,2,5")
    check(ok, total, "emit.set.union.bar", showSet(s | Set(1, 6)), "1,2,6")
    check(ok, total, "emit.set.union", showSet(s.union(Set(7))), "1,2,7")
    check(ok, total, "emit.set.plus.many", showSet(s + (8, 9)), "1,2,8,9")
    check(ok, total, "emit.set.minus.list", showSet(Set(1, 2, 3) -- List(2)), "1,3")
    check(ok, total, "emit.set.minus.many", showSet(Set(1, 2, 3, 4) - (2, 3)), "1,4")
    check(ok, total, "emit.set.diff.bar", showSet(Set(1, 2, 3) &~ Set(1)), "2,3")
    check(ok, total, "emit.set.intersect", showSet(Set(1, 2, 3) & Set(2, 3, 4)), "2,3")
    check(ok, total, "emit.set.concat.map.keys", showSet(s ++ Map(5 -> "x").keySet), "1,2,5")
    check(ok, total, "emit.set.prepend", showSet(List(1, 10) ++: s), "1,2,10")
    check(ok, total, "emit.set.from", showSet(Set.from(List(4, 4, 5))), "4,5")
  }

  def lists(ok: Box, total: Box): Unit = {
    val xs = List(1, 2)
    check(ok, total, "emit.list.concat", (xs ++ List(3)).mkString(","), "1,2,3")
    check(ok, total, "emit.list.concat.option", (xs ++ Some(4)).mkString(","), "1,2,4")
    check(ok, total, "emit.list.prepend", (List(0) ++: xs).mkString(","), "0,1,2")
    check(ok, total, "emit.list.appendall", (xs :++ List(5)).mkString(","), "1,2,5")
  }
}
