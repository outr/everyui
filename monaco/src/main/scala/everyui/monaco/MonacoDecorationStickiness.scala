package everyui.monaco

/** Whether a decoration grows when text is typed at its edges: Monaco's `TrackedRangeStickiness`. */
enum MonacoDecorationStickiness(val wire: Int) {
  case AlwaysGrowsWhenTypingAtEdges extends MonacoDecorationStickiness(0)
  case NeverGrowsWhenTypingAtEdges extends MonacoDecorationStickiness(1)
  case GrowsOnlyWhenTypingBefore extends MonacoDecorationStickiness(2)
  case GrowsOnlyWhenTypingAfter extends MonacoDecorationStickiness(3)
}
