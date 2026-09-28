package everyui.monaco

/** One edit to a model's text: `rangeLength` characters from `rangeOffset` (UTF-16, as a Scala string counts them)
  * replaced with `text`. */
case class MonacoTextChange(rangeOffset: Int, rangeLength: Int, text: String)
