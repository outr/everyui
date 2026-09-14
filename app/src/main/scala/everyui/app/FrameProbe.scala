package everyui.app

import reactify.*
import everyui.component.TextView
import everyui.render.Platform
import everyui.ui

/** One animated label: its reactive position and its direction of travel. */
class Mover(val pos: Var[Double], private var dir: Double) {
  def step(): Unit = {
    val next = pos() + dir
    if (next > FrameProbe.MaxX) dir = -FrameProbe.Step
    if (next < FrameProbe.MinX) dir = FrameProbe.Step
    pos @= next
  }
}

/** **Does reactive-driven animation survive a low-power TV?**
  *
  * Every frame pushes new values through the FULL reactify chain: `pos @= x`
  * fires `pos`; the label's `x` Prop, whose value is the expression `pos()`,
  * re-evaluates through reactify's dependency tracking and fires; its reaction
  * writes the SceneGraph / DOM node. Nothing pokes a node directly, so the
  * measured rate is the rate the ABSTRACTION sustains, not the renderer's.
  *
  * `Count` / `Animated` / `Bound` are the sweep knobs (see DESIGN.md).
  */
object FrameProbe {
  val Step: Double = 12.0
  val MinX: Double = 20.0
  val MaxX: Double = 1700.0
  private val ReportMs: Double = 1000.0
  /** Labels on screen. */
  private val Count: Int = 1
  /** How many of them are stepped each frame — set below `Count` to price the
    * cost of merely HAVING nodes on screen against the cost of updating them. */
  private val Animated: Int = 1
  /** When false the movers still run the reactive update but nothing is bound
    * to a node, which prices the reactive layer on its own. */
  private val Bound: Boolean = true
  private val RowHeight: Double = 56.0
  /** Rows that fit on a 1080p screen — movers WRAP so every one of them is
    * actually on screen and actually drawn. */
  private val Rows: Int = 17

  def run(): Unit = {
    val movers = build()
    var frames = 0.0
    var lastReport = Platform.ticks()

    Platform.animate(() => {
      movers.foreach(m => m.step())

      frames = frames + 1.0
      val now = Platform.ticks()
      val elapsed = now - lastReport
      if (elapsed >= ReportMs) {
        Platform.log("n=" + Count.toString + " animated=" + Animated.toString +
          " bound=" + Bound.toString + " fps=" + (frames * 1000.0 / elapsed).toString)
        frames = 0.0
        lastReport = now
      }
    })
  }

  private def build(): List[Mover] =
    (0 until Count).toList.map(i => {
      val label = new TextView
      label.content @= "mover " + i.toString
      label.position.top @= 40.0 + (i % Rows).toDouble * RowHeight
      ui.children += label

      // The whole point: the animated property is BOUND, never poked.
      val pos = Var(MinX + (i / Rows).toDouble * 7.0)
      if (Bound) label.position.left := pos() else label.position.left @= pos()
      new Mover(pos, Step)
    }).take(Animated)
}
