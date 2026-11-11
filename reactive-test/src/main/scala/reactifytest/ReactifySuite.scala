package reactifytest

import reactify.*
import reactify.bind.BindSet
import reactify.reaction.{Reaction, ReactionStatus}
import reactify.transaction.Transaction

/** Mutable cells for test bookkeeping. Closures capture a REFERENCE to one of
  * these, so the suite's own scaffolding never depends on captured-local
  * mutation — a failure here is reactify's, not the harness's. */
class Box(var n: Int)

class Log(var items: List[String]) {
  def add(s: String): Unit = items = items ::: List(s)
  def show(): String = items.mkString(",")
}

/** **The acceptance test for emitting reactify unmodified.**
  *
  * Exercises reactify's real public API across every mechanism the emitter gap
  * list touches: by-name params (`Var(...)`, `Val(...)`), automatic dependency
  * tracking through a ThreadLocal, dynamic dependency switching, virtual
  * dispatch through trait/class hierarchies (groups, Dep, Channel), lazy-val
  * overrides, case objects compared by identity, anonymous `Reaction`s,
  * captured mutable locals (`once`), overloads (`Transaction.revert`),
  * `AtomicBoolean` (`bind`), immutable `Set` diffing and exceptions.
  *
  * Every line is deterministic and prefixed `RX`. Run natively it is the
  * expected output; emitted to the Roku it must match line for line.
  */
object ReactifySuite {
  def check(ok: Box, total: Box, name: String, got: String, expected: String): Unit = {
    total.n = total.n + 1
    if (got == expected) {
      ok.n = ok.n + 1
      println("RX PASS " + name + " = " + got)
    }
    else println("RX FAIL " + name + " got=" + got + " expected=" + expected)
  }

  def varBasics(ok: Box, total: Box): Unit = {
    val v = Var(1)
    check(ok, total, "var.initial", v().toString, "1")
    v := 2
    check(ok, total, "var.:=", v().toString, "2")
    v @= 3
    check(ok, total, "var.@=", v.get.toString, "3")
    v.set(4)
    check(ok, total, "var.set", v().toString, "4")
  }

  def valDependencies(ok: Box, total: Box): Unit = {
    val a = Var(1)
    val b = Var(2)
    val sum = Val(a() + b())
    check(ok, total, "val.derived", sum().toString, "3")
    a := 10
    check(ok, total, "val.tracks.a", sum().toString, "12")
    b := 20
    check(ok, total, "val.tracks.b", sum().toString, "30")
    val fires = new Box(0)
    sum.attach(_ => fires.n = fires.n + 1)
    a := 11
    check(ok, total, "val.fires.on.dep", fires.n.toString, "1")
    a := 11
    check(ok, total, "val.same.value.no.fire", fires.n.toString, "1")
  }

  def valDynamicDeps(ok: Box, total: Box): Unit = {
    val useA = Var(true)
    val a = Var("A")
    val b = Var("B")
    val pick = Val(if (useA()) a() else b())
    check(ok, total, "val.dyn.initial", pick(), "A")
    b := "B2"
    check(ok, total, "val.dyn.untracked", pick(), "A")
    useA := false
    check(ok, total, "val.dyn.switched", pick(), "B2")
    val fires = new Box(0)
    pick.attach(_ => fires.n = fires.n + 1)
    a := "A3"
    check(ok, total, "val.dyn.dropped.dep.no.fire", fires.n.toString, "0")
    b := "B3"
    check(ok, total, "val.dyn.retracked", pick(), "B3")
    check(ok, total, "val.dyn.retracked.fired", fires.n.toString, "1")
  }

  def attachAndDetach(ok: Box, total: Box): Unit = {
    val v = Var(0)
    val log = new Log(Nil)
    val r = v.attach(x => log.add("got" + x.toString))
    v := 1
    v := 2
    v.reactions -= r
    v := 3
    check(ok, total, "attach.detach", log.show(), "got1,got2")
  }

  def attachAndFire(ok: Box, total: Box): Unit = {
    val v = Var(7)
    val log = new Log(Nil)
    v.attachAndFire(x => log.add(x.toString))
    v := 8
    check(ok, total, "attachAndFire", log.show(), "7,8")
  }

  def changesAndOnce(ok: Box, total: Box): Unit = {
    val v = Var(1)
    val log = new Log(Nil)
    v.changes((before, after) => log.add(before.toString + ">" + after.toString))
    v := 2
    v := 3
    check(ok, total, "changes", log.show(), "1>2,2>3")
    val once = new Log(Nil)
    v.once(x => once.add(x.toString))
    v := 4
    v := 5
    check(ok, total, "once", once.show(), "4")
  }

  def priorityAndStop(ok: Box, total: Box): Unit = {
    val v = Var(0)
    val log = new Log(Nil)
    v.attach(_ => log.add("normal"), Priority.Normal)
    v.attach(_ => log.add("low"), Priority.Low)
    v.attach(_ => log.add("high"), Priority.High)
    v := 1
    check(ok, total, "priority.order", log.show(), "low,normal,high")

    val s = Var(0)
    val slog = new Log(Nil)
    s.attach(_ => slog.add("first"), Priority.Low)
    s.attach(_ => { slog.add("stopper"); s.stopPropagation() }, Priority.Normal)
    s.attach(_ => slog.add("never"), Priority.High)
    s := 1
    check(ok, total, "stopPropagation", slog.show(), "first,stopper")

    val t = Var(0)
    val tlog = new Log(Nil)
    t.reactions += new Reaction[Int] {
      override def apply(value: Int, previous: Option[Int]): ReactionStatus = {
        tlog.add("anon" + value.toString)
        ReactionStatus.Stop
      }
      override def priority: Double = Priority.Low
    }
    t.attach(_ => tlog.add("after"), Priority.High)
    t := 5
    check(ok, total, "anon.reaction.stop", tlog.show(), "anon5")
  }

  def channels(ok: Box, total: Box): Unit = {
    val c = Channel[Int]
    val log = new Log(Nil)
    c.attach(x => log.add("c" + x.toString))
    val doubled = c.map(x => x * 2)
    doubled.attach(x => log.add("d" + x.toString))
    c := 3
    check(ok, total, "channel.map", log.show(), "c3,d6")

    val trig = Trigger()
    val tb = new Box(0)
    trig.on(tb.n = tb.n + 1)
    trig.trigger()
    trig.trigger()
    check(ok, total, "trigger", tb.n.toString, "2")

    val v = Var(5)
    val m = v.map(x => x + 1)
    check(ok, total, "var.map.initial", m().toString, "6")
    v := 9
    check(ok, total, "var.map.tracks", m().toString, "10")
  }

  def deps(ok: Box, total: Box): Unit = {
    val left = Var(10)
    val right = Dep[Int, Int](left)((r: Int) => r + 5, (t: Int) => t - 5)
    check(ok, total, "dep.read", right().toString, "15")
    right := 30
    check(ok, total, "dep.write.through", left().toString, "25")
    left := 1
    check(ok, total, "dep.follows", right().toString, "6")
  }

  def groups(ok: Box, total: Box): Unit = {
    val a = Var(0)
    val b = Var(0)
    val both = a and b
    both := 7
    check(ok, total, "group.set.a", a().toString, "7")
    check(ok, total, "group.set.b", b().toString, "7")
    val glog = new Log(Nil)
    both.attach(x => glog.add(x.toString))
    a := 8
    check(ok, total, "group.attach.via.item", glog.show(), "8")
  }

  def binding(ok: Box, total: Box): Unit = {
    val x = Var(1)
    val y = Var(100)
    val bnd = x.bind(y, BindSet.LeftToRight)((i: Int) => i, (i: Int) => i)
    check(ok, total, "bind.initial.l2r", y().toString, "1")
    x := 2
    check(ok, total, "bind.l2r", y().toString, "2")
    y := 3
    check(ok, total, "bind.r2l", x().toString, "3")
    bnd.detach()
    x := 4
    check(ok, total, "bind.detached", y().toString, "3")
  }

  def transactions(ok: Box, total: Box): Unit = {
    val v = Var(1)
    val txn = Transaction {
      v := 2
      v := 3
    }
    check(ok, total, "txn.applied", v().toString, "3")
    txn.revert()
    check(ok, total, "txn.revert", v().toString, "1")
  }

  def listSugar(ok: Box, total: Box): Unit = {
    val xs = Var(List(1, 2))
    xs += 3
    check(ok, total, "listvar.+=", xs().mkString(","), "1,2,3")
    xs -= 1
    check(ok, total, "listvar.-=", xs().mkString(","), "2,3")
  }

  def failures(ok: Box, total: Box): Unit = {
    val flag = Var(false)
    val risky = Val(if (flag()) throw new RuntimeException("boom") else 1)
    check(ok, total, "val.ok", risky().toString, "1")
    flag := true
    val caught = new Box(0)
    try risky()
    catch case _: Throwable => caught.n = 1
    check(ok, total, "val.failure.throws", caught.n.toString, "1")
  }

  /** Edge semantics of the hot paths (`set` / `evaluate` / `get` / `fire` /
    * `Reactions.+=` / `Transaction.change`). The expected values were taken
    * from the UNMODIFIED library, so any performance fast path that changes
    * observable behaviour — even in a corner — fails here. The one deliberate
    * change since: dependency tracking (`references`) is maintained. */
  def hotPathEdges(ok: Box, total: Box): Unit = {
    // A function that reads its own Var sees the current state on a direct
    // set, but the PREVIOUS value when a dependency re-evaluates it.
    val base = Var(1)
    val acc = Var(0)
    acc := acc() + base()
    check(ok, total, "edge.self.read.initial", acc().toString, "1")
    base := 10
    check(ok, total, "edge.self.read.updating", acc().toString, "10")
    base := 20
    check(ok, total, "edge.self.read.again", acc().toString, "21")

    // Switching a Var's function detaches it from the old dependency.
    val a = Var(1)
    val b = Var(2)
    val x = Var(0)
    x := a()
    x := b()
    check(ok, total, "edge.refs.old.dep.detached", a.reactions().size.toString, "0")
    check(ok, total, "edge.refs.new.dep.attached", b.reactions().size.toString, "1")
    check(ok, total, "edge.references", x.references.size.toString, "1")
    val xf = new Box(0)
    x.attach(_ => xf.n = xf.n + 1)
    a := 5
    check(ok, total, "edge.refs.old.dep.no.fire", x().toString + "/" + xf.n.toString, "2/0")
    b := 6
    check(ok, total, "edge.refs.new.dep.fires", x().toString + "/" + xf.n.toString, "6/1")

    // Reactions are kept distinct by equality, not just identity.
    val d = Var(0)
    val fn = (i: Int) => ()
    val r1 = Reaction[Int](fn)
    d.reactions += r1
    d.reactions += r1
    d.reactions += Reaction[Int](fn)
    check(ok, total, "edge.reactions.distinct", d.reactions().size.toString, "1")

    // A nested fire with NO reactions still ends the outer propagation.
    val re = Var(0)
    val rlog = new Log(Nil)
    re.attach(v => {
      rlog.add("r1:" + v.toString)
      if (v == 1) {
        re.reactions.clear()
        re := 2
      }
    }, Priority.Low)
    re.attach(v => rlog.add("r2:" + v.toString), Priority.High)
    re := 1
    check(ok, total, "edge.nested.empty.fire", rlog.show() + "|" + re().toString, "r1:1|2")

    val st = Var(0)
    val slog = new Log(Nil)
    st.attach(_ => slog.add(st.status.contains(ReactionStatus.Continue).toString))
    st := 1
    slog.add(st.status.isEmpty.toString)
    check(ok, total, "edge.status.during.after", slog.show(), "true,true")

    // Val.static doesn't fire, and stops listening to its dependencies.
    val src = Var(3)
    val derived = Val(src() * 2)
    val dfires = new Box(0)
    derived.attach(_ => dfires.n = dfires.n + 1)
    derived.static(100)
    check(ok, total, "edge.val.static", derived().toString + "/" + dfires.n.toString, "100/0")
    check(ok, total, "edge.val.static.detached", src.reactions().size.toString, "0")
    src := 4
    check(ok, total, "edge.val.static.dep", derived().toString + "/" + dfires.n.toString, "100/0")

    val same = Var(5)
    val sf = new Box(0)
    same.attach(_ => sf.n = sf.n + 1)
    same @= 5
    same @= 6
    same @= 6
    check(ok, total, "edge.static.same.value", sf.n.toString, "1")

    flagRecovery(ok, total)

    val tv = Var(1)
    val ref = new TxnRef(null)
    val outer = Transaction {
      tv := 2
      ref.t = Transaction { tv := 3 }
      check(ok, total, "edge.txn.nested.active", Transaction.active.toString, "true")
    }
    check(ok, total, "edge.txn.nested.same", (ref.t eq outer).toString, "true")
    check(ok, total, "edge.txn.inactive.after", Transaction.active.toString, "false")
    tv := 9
    outer.revert()
    check(ok, total, "edge.txn.revert", tv().toString, "1")
  }

  def flagRecovery(ok: Box, total: Box): Unit = {
    val flag = Var(true)
    val risky = Val(if (flag()) throw new RuntimeException("boom") else 1)
    // The first evaluation threw before its dependencies were recorded, so
    // the Val never hears about `flag` and stays failed.
    flag := false
    val got =
      try risky().toString
      catch case _: Throwable => "threw"
    check(ok, total, "edge.failure.first.eval.untracked", got, "threw")
  }

  /** Runs LAST: a Transaction whose body throws is never closed, so the
    * thread stays in it (and every later `set` is recorded). */
  // a call wider than a BrightScript function: the parameters past the 30th travel as one array
  def wideCalls(ok: Box, total: Box): Unit = {
    val w = Wide(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2)
    check(ok, total, "emit.wide.last", w.a33.toString, "2")
    check(ok, total, "emit.wide.method", w.weigh(3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4).toString, "4030201")
    check(ok, total, "emit.wide.copy", w.copy(a33 = 5).a33.toString, "5")
  }

  def transactionThrow(ok: Box, total: Box): Unit = {
    val lv = Var(1)
    try Transaction {
      lv := 2
      throw new RuntimeException("abort")
    }
    catch case _: Throwable => ()
    check(ok, total, "edge.txn.throw.stays.active", Transaction.active.toString, "true")
    lv := 3
    val t = Transaction { lv := 4 }
    t.revert()
    check(ok, total, "edge.txn.throw.records", lv().toString, "1")
  }
}

class TxnRef(var t: Transaction)

@main def reactifySuite(): Unit = {
  val ok = new Box(0)
  val total = new Box(0)
  ReactifySuite.varBasics(ok, total)
  ReactifySuite.valDependencies(ok, total)
  ReactifySuite.valDynamicDeps(ok, total)
  ReactifySuite.attachAndDetach(ok, total)
  ReactifySuite.attachAndFire(ok, total)
  ReactifySuite.changesAndOnce(ok, total)
  ReactifySuite.priorityAndStop(ok, total)
  ReactifySuite.channels(ok, total)
  ReactifySuite.deps(ok, total)
  ReactifySuite.groups(ok, total)
  ReactifySuite.binding(ok, total)
  ReactifySuite.transactions(ok, total)
  ReactifySuite.listSugar(ok, total)
  ReactifySuite.failures(ok, total)
  ReactifySuite.hotPathEdges(ok, total)
  ReactifySuite.transactionThrow(ok, total)
  ReactifySuite.wideCalls(ok, total)
  Collections.maps(ok, total, true)
  Collections.sets(ok, total)
  Collections.lists(ok, total)
  Collections.seqs(ok, total)
  reactifytest.app.Kinds.run(ok, total)
  println("RX DONE " + ok.n.toString + "/" + total.n.toString)
}
