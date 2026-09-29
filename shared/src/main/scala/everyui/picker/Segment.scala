package everyui.picker

/** One of a picker's segments: a chip over its list that shows only the matches whose group is `key` (a model
  * picker's providers, say). The segment `Segment.all` has no key and shows every match. */
case class Segment(key: String, label: String)

object Segment {
  /** The segment that shows every match, whatever its group. */
  def all(label: String): Segment = Segment("", label)
}
