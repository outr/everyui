package everyui.markdown

/** How a table's width is shared between its columns, as a browser's automatic table layout shares it: each column
  * has the width of its widest word (`least`, below which a word would be cut) and of its widest cell on one line
  * (`most`). Where every column fits at its `most` it takes it, and the table is no wider than its words. Between
  * that and the `least`, each column takes its `least` and a share of what is left in proportion to how much more it
  * would take on one line, so a column of short words keeps them whole while a column of long phrases wraps. Where
  * not even the `least` fit, the table is wider than `room` (it scrolls), and each column takes its `least`, or its
  * even share of the room where its words need that much (where a browser leaves it at its `least`, a word a line).
  */
object TableWidths {
  def apply(least: Vector[Double], most: Vector[Double], room: Double): Vector[Double] = {
    val wide = least.indices.toVector.map(i => math.max(least(i), most(i)))
    val small = least.foldLeft(0.0)(_ + _)
    val large = wide.foldLeft(0.0)(_ + _)
    if (large <= room) wide
    else if (small >= room || large <= small) {
      // wider than the room whatever it does, so it scrolls: no column narrower than its share of the room, or its
      // words on one line where they take less, so a column of words beside a long path is not cut to a word a line
      val share = room / math.max(1, least.size).toDouble
      least.indices.toVector.map(i => math.max(least(i), math.min(wide(i), share)))
    }
    else {
      val share = (room - small) / (large - small)
      least.indices.toVector.map(i => least(i) + (wide(i) - least(i)) * share)
    }
  }
}
