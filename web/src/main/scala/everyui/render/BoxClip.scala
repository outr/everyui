package everyui.render

/** How the web cuts a box to itself (`Container.clipsToBox`, a box hidden but laid out): `overflow: clip` where the
  * engine has it (Chromium 90), which makes no scroll box; before it `overflow: hidden`, which clips alike but makes
  * one the browser may scroll on its own (a focus put in it, a find in page), so a box clipped so is pinned: put back
  * unscrolled whenever it shifts. Plain Scala, so its rules are specified on the JVM. */
object BoxClip {
  /** Whether the engine has `overflow: clip`: its `CSS.supports` answer where it has one, else whether `clip` set on an
    * element's style reads back. */
  def known(supports: Option[Boolean], readBack: => String): Boolean = supports.getOrElse(readBack == "clip")

  /** The overflow a box clipped to itself is given. */
  def overflow(clipKnown: Boolean): String = if (clipKnown) "clip" else "hidden"

  /** Whether a box given `overflow` must be held unscrolled: only a scroll box the app did not ask to scroll. */
  def pinned(overflow: String): Boolean = overflow == "hidden"

  /** Whether a box cut with `hidden` is made a stacking context of its own (`z-index: 0`, no layer): before Chromium
    * 90, where an engine without one (53) cut nothing positioned in it to its rounded corners. */
  def stacks(clipKnown: Boolean): Boolean = !clipKnown

  /** Whether a pinned box has moved off its start: scrolled down, or along from its start edge. */
  def shifted(top: Double, fromStart: Double): Boolean = top != 0.0 || fromStart != 0.0
}
