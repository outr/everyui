package everyui.draw

/** One step of a [[Path]]: `M` move to (x, y), `L` a line to (x, y), `C` a cubic curve through two control points to
  * (x1, y1, x2, y2, x, y), `Z` closed back to where the figure began. Every backend draws these four; arcs and circles
  * are made of cubic curves before they reach one. */
case class PathOp(kind: String, values: List[Double])
