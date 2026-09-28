package everyui.monaco

/** How a marker's text is drawn besides its squiggle: faded, or struck through. Monaco's `MarkerTag`. */
enum MonacoMarkerTag(val wire: Int) {
  case Unnecessary extends MonacoMarkerTag(1)
  case Deprecated extends MonacoMarkerTag(2)
}
