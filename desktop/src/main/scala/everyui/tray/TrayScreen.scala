package everyui.tray

/** A screen as JavaFX lays them out (its place and size in the desktop's logical pixels, and how many of its own
  * pixels it draws each with), for finding where a tray icon was clicked (`locate`). */
case class TrayScreen(minX: Double, minY: Double, width: Double, height: Double, scale: Double) {
  def holds(x: Double, y: Double): Boolean = x >= minX && x < minX + width && y >= minY && y < minY + height
}

object TrayScreen {
  /** Whether this is Plasma's Wayland session, whose panel says where its icon was clicked oddly. */
  def plasmaWayland: Boolean =
    Option(System.getenv("XDG_CURRENT_DESKTOP")).exists(_.toUpperCase.contains("KDE")) &&
      Option(System.getenv("WAYLAND_DISPLAY")).exists(_.nonEmpty)

  /** Which of `screens` the panel's point `x`, `y` is on, and the point there in logical pixels.
    *
    * Plasma under Wayland names a point as its screen's place, in logical pixels, and then how far into the screen it
    * is in the screen's own pixels: a click 1846 logical pixels into a screen at 4390, drawn at 1.75, comes as 7621,
    * which is past every screen, and was put on the primary one. There, each screen the point comes back inside of, so
    * read, is one it may be on; of those, the first along (the leftmost, then the topmost) is the one, since a point
    * read against a screen further along comes out nearer that screen's start, not past it.
    *
    * Elsewhere the point is taken as it is. A point on no screen goes to the nearest. */
  def locate(x: Int, y: Int, screens: List[TrayScreen], plasma: Boolean): (Int, Double, Double) = {
    val indexed = screens.zipWithIndex
    val scaled = if (!plasma) Nil else indexed.flatMap { case (s, i) =>
      val lx = s.minX + (x - s.minX) / s.scale
      val ly = s.minY + (y - s.minY) / s.scale
      if (x >= s.minX && y >= s.minY && s.holds(lx, ly)) List((i, lx, ly, s)) else Nil
    }
    scaled.sortBy(c => (c._4.minX, c._4.minY)).headOption.map(c => (c._1, c._2, c._3)).getOrElse {
      indexed.find(_._1.holds(x, y)).map(s => (s._2, x.toDouble, y.toDouble)).getOrElse {
        if (screens.isEmpty) (0, x.toDouble, y.toDouble)
        else {
          // the screen nearest the point, and the point brought onto it
          def gap(s: TrayScreen): Double = {
            val dx = math.max(0.0, math.max(s.minX - x, x - (s.minX + s.width)))
            val dy = math.max(0.0, math.max(s.minY - y, y - (s.minY + s.height)))
            dx * dx + dy * dy
          }
          val (near, i) = indexed.minBy(s => gap(s._1))
          (i, math.max(near.minX, math.min(near.minX + near.width - 1.0, x.toDouble)),
            math.max(near.minY, math.min(near.minY + near.height - 1.0, y.toDouble)))
        }
      }
    }
  }
}
