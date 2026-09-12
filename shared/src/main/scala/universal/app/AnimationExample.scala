package universal.app

import universal.render.{Nodes, Platform}
import reactify.*
import universal.ui.{Animator, Easing, Label, Prop, Task}

/** **youi's `AnimationExample`, reproduced on the render seam.**
  *
  * The original (`youi/example/.../ui/AnimationExample.scala`) builds one
  * labelled row per easing curve, runs each row forever
  * (`right → sleep → left → sleep`), and in parallel spins an image while
  * bouncing it around the four edges. This is that, on shared sources, against
  * whichever backend is linked.
  *
  * Two honest departures from youi, both noted in DESIGN.md:
  *  - youi animates `position.right` / `position.bottom`, which are DERIVED
  *    coordinates — they need the component's MEASURED width and height, and
  *    the seam has no measure step yet. Rows animate `x` between explicit
  *    bounds instead, which is the same motion with the arithmetic done here.
  *  - youi's rotation is in turns (`rotation to 6.0` = six revolutions);
  *    `RenderNode.setRotation` takes radians, so the example multiplies.
  */
object AnimationExample:
  private val RowTop: Double = 40.0
  /** The row pitch youi used, and the ceiling here. 31 rows at 31px need just
    * over 1000px — fine in the Roku's fixed 1080 design space, taller than a
    * laptop browser window. Rather than let the page grow (which makes the
    * browser show a scrollbar, and a scrollbar narrows the layout viewport and
    * shifts every measurement taken from it), the pitch is derived from the
    * height actually available, so all 31 rows fit whatever the viewport is.
    * On the Roku the arithmetic lands back on ~31px, so the two backends stay
    * proportionally identical. */
  private val MaxRowHeight: Double = 31.0
  private val MaxRowFontSize: Double = 24.0
  private val LeftMargin: Double = 50.0
  private val RowTravelSeconds: Double = 5.0
  private val RowPauseSeconds: Double = 2.0

  private val IconSize: Double = 200.0
  private val IconSpinSeconds: Double = 20.0
  private val IconSpinTurns: Double = 6.0
  private val IconEdgeSeconds: Double = 5.0

  /** Step the animation `FreezeFrames` times from a VIRTUAL clock (a fixed
    * 1/60 s per frame) SYNCHRONOUSLY at startup, then stop. Both backends then
    * render the identical animation time — no dependence on either one's real
    * frame rate, or on how a headless browser schedules rAF — so screenshots of
    * the two compare position for position. Set false for the live demo. */
  private val Deterministic: Boolean = false
  private val FreezeFrames: Int = 180
  private val FrameMs: Double = 1000.0 / 60.0

  def run(): Unit =
    val root = Nodes.root()
    val animator = new Animator
    val width = Platform.screenWidth()
    val height = Platform.screenHeight()

    // ── one row per easing curve ────────────────────────────────────────────
    // The whole point of the demo: 31 curves side by side, so a wrong one is
    // obvious at a glance rather than needing a stopwatch.
    val rowRight = width - LeftMargin - 380.0
    val rows = Easing.all.size
    val fit = (height - RowTop * 2.0) / rows.toDouble
    val rowHeight = if fit < MaxRowHeight then fit else MaxRowHeight
    val fontSize = if rowHeight * 0.78 < MaxRowFontSize then rowHeight * 0.78 else MaxRowFontSize
    // Retained only so the frozen frame can be reported numerically.
    var built = List.empty[Label]
    (0 until Easing.all.size).toList.foreach(i =>
      val e = Easing.all(i)
      val label = Label()
      label.content @= e.name + " Example"
      label.node.setFontSize(fontSize)
      label.x @= LeftMargin
      label.y @= RowTop + i.toDouble * rowHeight
      root.append(label.node)
      built = built :+ label

      animator.start(Task.forever(Task.sequential(List(
        Task.animate(() => label.x(), v => label.x @= v, () => rowRight, RowTravelSeconds, e.easing),
        Task.sleep(RowPauseSeconds),
        Task.animate(() => label.x(), v => label.x @= v, () => LeftMargin, RowTravelSeconds, e.easing),
        Task.sleep(RowPauseSeconds)
      ))))
    )

    // ── the image: spin and bounce, in parallel ─────────────────────────────
    val icon = Nodes.image(Platform.imagePath("icon.png"), IconSize, IconSize)
    root.append(icon)
    val spin = new Prop[Double](() => icon.getRotation(), r => icon.setRotation(r))
    val iconX = new Prop[Double](() => icon.getX(), v => icon.setX(v))
    val iconY = new Prop[Double](() => icon.getY(), v => icon.setY(v))
    iconY @= height / 2.0 - IconSize / 2.0

    val turn = 2.0 * Easing.Pi

    animator.start(Task.forever(Task.parallel(List(
      Task.sequential(List(
        Task.sync(() => spin @= 0.0),
        Task.animate(() => spin(), v => spin @= v, () => IconSpinTurns * turn, IconSpinSeconds, Easing.linear)
      )),
      Task.sequential(List(
        Task.animate(() => iconX(), v => iconX @= v, () => width - IconSize, IconEdgeSeconds, Easing.bounceOut),
        Task.animate(() => iconY(), v => iconY @= v, () => height - IconSize, IconEdgeSeconds, Easing.bounceOut),
        Task.animate(() => iconX(), v => iconX @= v, () => 0.0, IconEdgeSeconds, Easing.bounceOut),
        Task.animate(() => iconY(), v => iconY @= v, () => 0.0, IconEdgeSeconds, Easing.bounceOut)
      ))
    ))))

    Platform.log("AnimationExample: " + rows.toString + " easings, " +
      width.toString + "x" + height.toString + ", row pitch " + rowHeight.toString)

    if Deterministic then
      (0 until FreezeFrames).toList.foreach(f => animator.tick(f.toDouble * FrameMs))
      Platform.log("frozen at frame " + FreezeFrames.toString +
        " (" + (FreezeFrames.toDouble * FrameMs / 1000.0).toString + "s)")
      (0 until Easing.all.size).toList.foreach(i =>
        Platform.log("frozen " + Easing.all(i).name + " x=" + built(i).x().toString))
      Platform.log("frozen icon x=" + iconX().toString + " y=" + iconY().toString +
        " rot=" + spin().toString)
    else
      // Live: drive from the real frame clock, and report the rate — 31 rows
      // plus the image is ~130 bound properties changing every frame, which is
      // right at the budget the earlier FrameProbe sweep measured.
      var frames = 0.0
      var lastReport = Platform.ticks()
      Platform.animate(() =>
        val now = Platform.ticks()
        animator.tick(now)
        frames = frames + 1.0
        val elapsed = now - lastReport
        if elapsed >= 1000.0 then
          Platform.log("fps=" + (frames * 1000.0 / elapsed).toString)
          frames = 0.0
          lastReport = now
      )
