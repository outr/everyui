package everyui.app

import reactify.*
import everyui.component.TextView
import everyui.component.types.Prop
import everyui.render.{Nodes, Platform, RenderNode}
import everyui.ui

/** Per-operation cost of reactify on the linked backend, one step of the
  * binding chain at a time, so an optimisation can be aimed at the step that
  * actually costs. Each figure is microseconds per operation with the
  * loop-and-call baseline subtracted. */
object ReactifyBench:
  /** A row repeats its batch of `n` until it has run this long, so fast
    * backends measure well above the timer's resolution. */
  private val MinMs: Double = 250.0

  def time(label: String, n: Int, baselineUs: Double, body: () => Unit): Double =
    val start = Platform.ticks()
    var ops = 0
    var elapsed = 0.0
    while elapsed < MinMs do
      var i = 0
      while i < n do
        body()
        i = i + 1
      ops = ops + n
      elapsed = Platform.ticks() - start
    val us = elapsed * 1000.0 / ops.toDouble
    Platform.log("bench " + label + " us/op=" + (us - baselineUs).toString)
    us

  /** Two full passes: the first warms a JIT (Scala.js) so the second pass —
    * the one to read — measures steady state. */
  def run(): Unit =
    val root = Nodes.root()
    pass(root)
    pass(root)

  private def pass(root: RenderNode): Unit =
    var flip = false
    val base = time("(loop+call baseline)", 20000, 0.0, () => ())

    val v = Var(0.0)
    time("read v()", 20000, base, () => { v(); () })
    time("v @= x (changes, no reactions)", 4000, base, () => {
      flip = !flip
      v @= (if flip then 1.0 else 2.0)
    })
    time("v @= x (same value)", 4000, base, () => v @= 2.0)

    val w = Var(0.0)
    w.attach(_ => ())
    time("v @= x (1 attached reaction)", 4000, base, () => {
      flip = !flip
      w @= (if flip then 1.0 else 2.0)
    })

    // fire + one reaction, with no Val evaluation around it
    val ch = Channel[Double]
    ch.attach(_ => ())
    time("channel @= x (1 attached reaction)", 4000, base, () => {
      flip = !flip
      ch @= (if flip then 1.0 else 2.0)
    })
    val attached = w.reactions().head
    time("reactions += (already attached)", 4000, base, () => { w.reactions += attached; () })

    val src = Var(0.0)
    val dep = Var(0.0)
    dep := src()
    time("v @= x (1 dependent Var)", 2000, base, () => {
      flip = !flip
      src @= (if flip then 1.0 else 2.0)
    })

    val src2 = Var(0.0)
    val prop = new Prop[Double](0.0, _ => ())
    prop := src2()
    time("v @= x -> bound Prop (no-op setter)", 2000, base, () => {
      flip = !flip
      src2 @= (if flip then 1.0 else 2.0)
    })

    val src3 = Var(0.0)
    val label = new TextView
    ui.children += label
    label.position.left := src3()
    time("v @= x -> bound Prop -> node", 2000, base, () => {
      flip = !flip
      src3 @= (if flip then 1.0 else 2.0)
    })

    time("direct node write (floor)", 4000, base, () => {
      flip = !flip
      label.node.setX(if flip then 1.0 else 2.0)
    })

    time("Var(1.0) creation", 2000, base, () => { Var(1.0); () })
    Platform.log("bench done")
