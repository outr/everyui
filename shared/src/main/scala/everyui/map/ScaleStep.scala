package everyui.map

/** What a map's scale bar shows: a round distance (`label`: "500 m", "2 km", "1,000 ft", "5 mi") and how many pixels
  * of the screen it is. */
private[map] case class ScaleStep(pixels: Double, label: String)
