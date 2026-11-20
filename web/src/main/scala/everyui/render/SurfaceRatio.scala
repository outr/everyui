package everyui.render

/** The screen's pixels to a CSS pixel as the page is really drawn, which an app host's engine may not say as
  * `devicePixelRatio` does: webOS 3's Chromium 38 says 2 on a 1080p web plane drawn 1:1 (its `screen` 1920 by 1080, its
  * screenshots too, and the resolution query matching nothing over 1; the 3840 its `deviceInfo` names is the panel's,
  * which the web plane is scaled up to). The resolution query is the engine's own word for the surface: where it agrees
  * with `devicePixelRatio` (every browser, a desktop's zoom included, and LG's Simulator at 1.1667), that is the
  * ratio; where it matches less, the most it matches, to a hundredth. Plain Scala, so the desktop's specs walk it. */
object SurfaceRatio {
  /** The ratio, from the `reported` one and `atLeast(r)`: whether the resolution query matches `r` or more, None where
    * the engine does not know the query. */
  def of(reported: Double, atLeast: Double => Option[Boolean]): Double =
    if (reported <= 0.0) 1.0
    else if (atLeast(reported - 0.005).forall(identity)) reported
    else {
      // the most it matches, between the least a screen has and what was said
      var low = 0.25
      var high = reported
      if (!atLeast(low).contains(true)) reported
      else {
        while (high - low > 0.005) {
          val mid = (low + high) / 2.0
          if (atLeast(mid).contains(true)) low = mid else high = mid
        }
        math.round(low * 100.0) / 100.0
      }
    }
}
