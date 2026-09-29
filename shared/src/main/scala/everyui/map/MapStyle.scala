package everyui.map

import everyui.Color

/** How a shape on a map is drawn: filled with `fill`, its outline `stroke`d `width` pixels of the screen wide (the
  * same at every zoom), either or both, the whole of it at `opacity` (over what each colour's own alpha says), and the
  * outline in dashes with `dash`.
  *
  * {{{
  *   MapStyle(fill = Some(Color.Teal.withAlpha(0.35)), stroke = Some(Color.Teal), width = 1.5)
  *   MapStyle(stroke = Some(Color.Orange), width = 1.0, opacity = 0.4, dash = Some(MapDash(4.0, 3.0)))
  * }}} */
case class MapStyle(fill: Option[Color] = None, stroke: Option[Color] = None, width: Double = 1.5,
                    opacity: Double = 1.0, dash: Option[MapDash] = None)
