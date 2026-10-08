package everyui.split

/** Which edge of a part a [[ResizeHandle]] is on: its `End` (the right in a left-to-right language, the left in a
  * right-to-left one), as a sidebar at the start has; its `Start`, as a panel at the end has; its `Bottom` or its
  * `Top`. */
enum ResizeEdge {
  case Start, End, Top, Bottom
}
