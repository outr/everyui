package everyui.list

/** The keys a `LazyColumn` or a `LazyRail` holds its made parts by, one for each item: the app's own where each is given
  * once, and where one is given again (a person billed twice in a cast, a title listed twice), each after the first told
  * apart by its place among those of that key (`key#2`, `key#3`), so every item has a part of its own where it belongs
  * and none leaves a gap. Told once for each kind, as an app should give each item a key of its own. */
private[list] object LazyKeys {
  private var warned: Set[String] = Set.empty

  /** `keys`, each made unique; the same keys where none repeats. */
  def unique(keys: Vector[String], kind: String): Vector[String] = {
    var counts = Map.empty[String, Int]
    var repeated = ""
    var i = 0
    while (i < keys.length) {
      val n = counts.getOrElse(keys(i), 0)
      if (n > 0 && repeated.isEmpty) repeated = keys(i)
      counts = counts.updated(keys(i), n + 1)
      i += 1
    }
    if (repeated.isEmpty) keys
    else {
      if (!warned.contains(kind)) {
        warned = warned + kind
        everyui.scribe.warn(kind + ": the key \"" + repeated + "\" is given to more than one item; each after the first " +
          "is told apart by its place among them (give each item a key of its own)")
      }
      var seen = Map.empty[String, Int]
      var taken = counts
      keys.map { k =>
        val n = seen.getOrElse(k, 0) + 1
        seen = seen.updated(k, n)
        if (n == 1) k
        else {
          var at = n
          var made = k + "#" + at.toString
          // (past a key of the app's own that reads the same)
          while (taken.contains(made)) {
            at += 1
            made = k + "#" + at.toString
          }
          taken = taken.updated(made, 1)
          made
        }
      }
    }
  }
}
