package everyui.monaco

/** Where in the overview ruler (the strip by the scrollbar) a decoration is marked. */
enum MonacoOverviewRulerLane(val wire: Int) {
  case Left extends MonacoOverviewRulerLane(1)
  case Center extends MonacoOverviewRulerLane(2)
  case Right extends MonacoOverviewRulerLane(4)
  case Full extends MonacoOverviewRulerLane(7)
}
