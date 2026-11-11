package everyui.color

import everyui.component.Container
import everyui.{Color, Gradient, PlatformKind}
import everyui.render.Platform

/** A strip of colour running left to right through `stops`: a gradient where the platform draws one, and elsewhere
  * (a Roku, an Apple TV, which fill a gradient with its first colour) `segments` boxes of solid colour, each the
  * gradient's colour at its middle. Size it; the stops are read again as they change. */
class ColorTrack(stops: => List[Color], segments: Int = ColorTrack.segments) extends Container {
  if (ColorTrack.gradients) gradient := Some(Gradient(90.0, stops*))
  else {
    var i = 0
    while (i < segments) {
      children += segment(i)
      i += 1
    }
  }

  private def segment(i: Int): Container = {
    val box = new Container
    box.position.left := size.width() * i.toDouble / segments.toDouble
    // a pixel over, so no seam of the ground shows between two boxes
    box.size.width := size.width() / segments.toDouble + 1.0
    box.size.height := size.height()
    box.backgroundColor := Gradient(90.0, stops*).at((i.toDouble + 0.5) / segments.toDouble)
    box
  }

  override def kind: String = "ColorTrack"
}

object ColorTrack {
  /** How many boxes a track is drawn in where there are no gradients. */
  val segments: Int = 24

  /** Whether the platform draws a gradient (not a Roku, not an Apple TV, an iPhone or an iPad). */
  def gradients: Boolean =
    Platform.kind != PlatformKind.Roku && Platform.kind != PlatformKind.TvOS && Platform.kind != PlatformKind.IOS
}
