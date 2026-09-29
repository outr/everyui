package everyui.map

import everyui.Color
import everyui.component.{Component, Container, TextView}
import everyui.draw.{Drawing, Figure, Path}
import everyui.theme.Theme

/** Something at a place on a map that stays its own size at every zoom: a pin, a dot, an icon, a label, or any
  * component the app makes (`make`, asked only once the place comes into view, and let go a while after it leaves).
  * The component sizes itself; `anchor` says which of its points is at the place (the tip of a pin, the middle of a
  * dot).
  *
  * `key` is the app's name for it, which `MapView.clicked` and `MapView.hovered` give back; `label`, where it has one,
  * shows in a tip as the pointer rests on it. It is found by the pointer where it `hits`, drawn only between the zooms
  * `from` and `until`, and gathered with its neighbours into one where the map clusters (`MapView.cluster`) and it
  * `clusters`.
  *
  * {{{
  *   MapMarker.pin("office", LatLng(35.0844, -106.6504), label = "Albuquerque office")
  *   MapMarker("well-7", at, () => new WellBadge(well), MapAnchor.Center, label = well.name)
  * }}} */
case class MapMarker(key: String, at: LatLng, make: () => Component, anchor: MapAnchor = MapAnchor.Center,
                     label: String = "", hits: Boolean = true, from: Double = 0.0, until: Option[Double] = None,
                     clusters: Boolean = true)

object MapMarker {
  /** A map pin, `colour` with a white middle, its tip at the place. */
  def pin(key: String, at: LatLng, colour: => Color = Theme.accent(), label: String = ""): MapMarker =
    MapMarker(key, at, () => pinView(colour), MapAnchor.Bottom, label)

  /** A dot `radius` pixels round, `colour` ringed in white, its middle at the place. Every platform draws it, a Roku's
    * too (it is a rounded box, not a drawing). */
  def dot(key: String, at: LatLng, colour: => Color = Theme.accent(), radius: Double = 6.0,
          label: String = ""): MapMarker = MapMarker(key, at, () => dotView(colour, radius), MapAnchor.Center, label)

  /** Words at the place, their middle on it, with a dark edge round them so they read over any tiles. */
  def text(key: String, at: LatLng, words: String, points: Double = 11.0, colour: => Color = Color.White,
           label: String = ""): MapMarker =
    MapMarker(key, at, () => textView(words, points, colour), MapAnchor.Center, label)

  /** A pin: a drop 24 pixels wide and 34 tall, as a drawing. */
  def pinView(colour: => Color): Component = {
    val view = new Drawing
    view.size.width @= 24.0
    view.size.height @= 34.0
    // a circle of radius 11 about (12, 12) with its sides drawn down to a point at (12, 33)
    val drop = Path.empty.moveTo(12.0, 33.0).cubicTo(9.0, 27.0, 1.0, 20.0, 1.0, 12.0).arc(12.0, 12.0, 11.0, math.Pi,
      2.0 * math.Pi).cubicTo(23.0, 20.0, 15.0, 27.0, 12.0, 33.0).close
    view.figures := List(Figure(drop, fill = Some(colour), stroke = Some(Color.Black.withAlpha(0.35)), width = 1.0),
      Figure(Path.circle(12.0, 12.0, 4.5), fill = Some(Color.White)))
    view
  }

  /** A dot: a round box `radius` pixels round with a white rim. */
  def dotView(colour: => Color, r: Double): Component = new Container {
    size.width @= r * 2.0
    size.height @= r * 2.0
    radius @= r
    backgroundColor := colour
    borderWidth @= 2.0
    borderColor @= Color.White
  }

  /** Words with an edge. */
  def textView(words: String, points: Double, colour: => Color): Component = {
    val view = TextView(words, points, colour)
    view.textOutline @= Some(everyui.TextOutline(1.0, Color.Black.withAlpha(0.8)))
    view
  }
}
