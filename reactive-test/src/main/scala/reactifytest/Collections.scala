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

  def seqs(ok: Box, total: Box): Unit = {
    val xs = List(5, 6, 7)
    val v = Vector(1, 2, 3)
    val arr = Array("a", "bb", "c", "a", "bb")
    check(ok, total, "emit.seq.indexwhere.array", arr.indexWhere(_.length > 1).toString + " " + arr.indexWhere(_.length > 1, 2).toString, "1 4")
    check(ok, total, "emit.seq.indexwhere.from", xs.indexWhere(_ > 5, 2).toString + " " + v.indexWhere(_ > 5).toString, "2 -1")
    check(ok, total, "emit.seq.indexwhere.string", "ab1c".indexWhere(_.isDigit).toString + " " + "a1b2".indexWhere(_.isDigit, 2).toString, "2 3")
    check(ok, total, "emit.seq.lift", xs.lift(1).getOrElse(0).toString + " " + xs.lift(3).isEmpty.toString + " " + xs.lift(-1).isEmpty.toString + " " + v.lift(2).getOrElse(0).toString, "6 true true 3")
    val at = xs.lift
    check(ok, total, "emit.seq.lift.value", at(0).getOrElse(0).toString, "5")
    check(ok, total, "emit.seq.range", List.range(0, 3).mkString(",") + " " + List.range(0, 10, 3).mkString(",") + " " + Vector.range(2, 4).mkString(","), "0,1,2 0,3,6,9 2,3")
    check(ok, total, "emit.seq.prepend", (0 +: v).mkString(",") + " " + (4 +: xs).mkString(","), "0,1,2,3 4,5,6,7")
    check(ok, total, "emit.seq.append", (v :+ 4).mkString(","), "1,2,3,4")
    val (even, odd) = List(1, 2, 3, 4).partition(_ % 2 == 0)
    check(ok, total, "emit.seq.partition", even.mkString(",") + " " + odd.mkString(","), "2,4 1,3")
    check(ok, total, "emit.seq.sortwith", List(3, 1, 2).sortWith(_ > _).mkString(",") + " " + Vector("b", "a").sortWith(_ < _).mkString(","), "3,2,1 a,b")
    check(ok, total, "emit.seq.sortwith.stable", List("bb", "a", "cc", "d").sortWith(_.length < _.length).mkString(","), "a,d,bb,cc")
    check(ok, total, "emit.seq.reduce", List(1, 2, 3).reduce(_ + _).toString + " " + v.reduceLeft(_ * _).toString, "6 6")
    check(ok, total, "emit.seq.init", v.init.mkString(",") + " " + xs.init.mkString(","), "1,2 5,6")
    check(ok, total, "emit.seq.updated", v.updated(1, 9).mkString(",") + " " + xs.updated(0, 1).mkString(","), "1,9,3 1,6,7")
    check(ok, total, "emit.range.by", (1 to 10 by 3).mkString(",") + " " + (10 to 1 by -3).mkString(",") + " " + (0 until 10 by 5).mkString(","), "1,4,7,10 10,7,4,1 0,5")
    check(ok, total, "emit.string.capitalize", "hello".capitalize + " " + "".capitalize, "Hello ")
    // an `s` part's escapes taken, a `raw` part's kept, each shown by its character codes (a tab or a newline would
    // not survive the console)
    val n = 7
    val codes = (s: String) => s.map(_.toInt).mkString(",")
    check(ok, total, "emit.string.interp.escapes", codes(s"a\tb\n$n\"\\\u0041\r"), "97,9,98,10,55,34,92,65,13")
    check(ok, total, "emit.string.interp.raw", codes(raw"a\tb$n\n"), "97,92,116,98,55,92,110")
    check(ok, total, "emit.string.interp.plain", codes(s"\t") + " " + codes(s"$n\t$n"), "9 55,9,55")
    check(ok, total, "emit.seq.collect", List(1, 2, 3, 4).collect { case x if x % 2 == 0 => x * 10 }.mkString(","), "20,40")
    check(ok, total, "emit.seq.collectfirst", List(1, 2, 3).collectFirst { case x if x > 1 => x }.getOrElse(0).toString, "2")
    check(ok, total, "emit.seq.array.ints", Array(3, 4).indexWhere(_ > 3).toString, "1")
    val made = new Box(0)
    check(ok, total, "emit.seq.fill", Vector.fill(3) { made.n = made.n + 1; made.n }.mkString(","), "1,2,3")
    check(ok, total, "emit.seq.tabulate", List.tabulate(3)(i => i * i).mkString(","), "0,1,4")
  }
}
