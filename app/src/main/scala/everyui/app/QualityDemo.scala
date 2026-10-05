package everyui.app

import everyui.app.screen.ScreenManager

/** The quality level and what it is measured from (`everyui.perf`): the level, the window under way, the device as it
  * said at launch, a button to run at each level, and a rail of shadowed cards that glides with the focus beside a
  * turning square and a blurred one, which each level draws differently. Each window closed and each summary is
  * logged:
  *
  *   [ui] quality window 5s keys 4 p50/p95 120/180ms update p95 3ms frame p95 17ms drop 0% of 290 mem - 41% at Full
  *   [ui] quality summary Full keys p50/p95 120/180 drop 0% update p95 3 frame p95 17 stepped false
  *
  * `window=<seconds>` and `slow=<windows>` shorten the policy for a look at a step down, `level=Reduced` starts there,
  * and `tiles=<n>` adds that many boxes recoloured on each move of the focus, work to feel on a slow device.
  */
object QualityDemo extends ScreenManager {
  lazy val screen: QualityScreen = new QualityScreen

  def run(): Unit = run(screen)
}
