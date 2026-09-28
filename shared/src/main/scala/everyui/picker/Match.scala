package everyui.picker

/** Where a query's letters fall in a text, for a picker to light them: each `(start, end)` a run of the text the query
  * matched, in order. */
object Match {
  /** The runs of `text` that `query` matches, its letters in order but not necessarily together ("gfp" in
    * "GoFilePicker"), ignoring case, each letter as early as it can be; None where they are not all there. An empty
    * query matches with no runs. */
  def spans(query: String, text: String): Option[List[(Int, Int)]] = {
    val q = query.toLowerCase
    val t = text.toLowerCase
    var runs: List[(Int, Int)] = Nil
    var at = 0
    var i = 0
    var missing = false
    while (!missing && i < q.length) {
      val found = t.indexOf(q.charAt(i).toInt, at)
      if (found < 0) missing = true
      else {
        runs = runs match {
          case (start, end) :: rest if end == found => (start, found + 1) :: rest
          case _ => (found, found + 1) :: runs
        }
        at = found + 1
      }
      i += 1
    }
    if (missing) None else Some(runs.reverse)
  }
}
