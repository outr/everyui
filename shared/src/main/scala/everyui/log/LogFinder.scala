package everyui.log

/** A search through the lines a log view shows, a stretch at a time (`step`) so a long log never holds the screen up
  * while it is searched: which lines hold a match and how many each holds, and the match it is on. New lines are
  * searched as they come once it has caught up; the oldest let go take their matches with them. */
private[log] class LogFinder(shown: LogShown, lineOf: Long => Option[LogLine]) {
  var matcher: LogMatcher = new LogMatcher("")
  private var hitIds: Array[Long] = new Array[Long](64)
  private var hitCounts: Array[Int] = new Array[Int](64)
  private var first = 0
  private var last = 0
  /** How many matches there are in what has been searched. */
  var total: Int = 0
  // how far through the lines shown it has searched
  private var scanned = 0
  /** The match it is on: its line, and which of that line's matches (0 the first); -1 for none. */
  var currentId: Long = -1L
  var currentOccurrence: Int = 0

  /** Whether every line shown has been searched. */
  def done: Boolean = matcher.empty || scanned >= shown.size

  /** Searching for what `m` looks for, from the first line shown. */
  def restart(m: LogMatcher): Unit = {
    matcher = m
    first = 0
    last = 0
    total = 0
    scanned = 0
    currentId = -1L
    currentOccurrence = 0
  }

  /** Lines searched until `budget` milliseconds have gone by `clock`; whether it has caught up. */
  def step(budget: Double, clock: () => Double): Boolean = {
    val began = clock()
    while (!done && clock() - began < budget) {
      val stop = math.min(shown.size, scanned + 64)
      while (scanned < stop) {
        val id = shown.id(scanned)
        lineOf(id).foreach(l => if (!l.marker) note(id, matcher.count(l.text)))
        scanned += 1
      }
    }
    done
  }

  /** A line shown after the rest: searched now, where it has caught up. */
  def appended(line: LogLine): Unit = if (!matcher.empty && scanned == shown.size - 1) {
    if (!line.marker) note(line.id, matcher.count(line.text))
    scanned += 1
  }

  /** `count` lines let go from the front of those shown, the first kept now `firstId`. */
  def dropped(count: Int, firstId: Long): Unit = if (count > 0) {
    scanned = math.max(0, scanned - count)
    while (first < last && hitIds(first) < firstId) {
      total -= hitCounts(first)
      first += 1
    }
    if (currentId >= 0L && currentId < firstId) {
      currentId = -1L
      currentOccurrence = 0
    }
  }

  /** How many lines hold a match. */
  def lines: Int = last - first

  /** The id of the `k`th line holding a match, and how many it holds. */
  def hit(k: Int): (Long, Int) = (hitIds(first + k), hitCounts(first + k))

  /** Which line holding a match `id` is, or would be: the first not before it. */
  def hitIndex(id: Long): Int = {
    var low = first
    var high = last
    while (low < high) {
      val mid = (low + high) / 2
      if (hitIds(mid) < id) low = mid + 1 else high = mid
    }
    low - first
  }

  /** How many matches `id`'s line holds. */
  def countIn(id: Long): Int = {
    val k = hitIndex(id)
    if (k < lines && hitIds(first + k) == id) hitCounts(first + k) else 0
  }

  /** Where the match it is on stands among them all, 1 the first; 0 for none. */
  def ordinal: Int = if (currentId < 0L) 0 else {
    val k = hitIndex(currentId)
    var before = 0
    var i = first
    while (i < first + k) {
      before += hitCounts(i)
      i += 1
    }
    before + currentOccurrence + 1
  }

  /** On the next match after the one it is on (from line index `from` where it is on none), round to the first after
    * the last; whether there is one. */
  def next(fromId: Long): Boolean = if (lines == 0) false else {
    if (currentId >= 0L && currentOccurrence + 1 < countIn(currentId)) currentOccurrence += 1
    else {
      val k = if (currentId >= 0L) hitIndex(currentId + 1L) else hitIndex(fromId)
      currentId = hitIds(first + (if (k >= lines) 0 else k))
      currentOccurrence = 0
    }
    true
  }

  /** On the match before the one it is on (the last before `fromId` where it is on none), round to the last. */
  def previous(fromId: Long): Boolean = if (lines == 0) false else {
    if (currentId >= 0L && currentOccurrence > 0 && countIn(currentId) > 0) currentOccurrence -= 1
    else {
      val k = (if (currentId >= 0L) hitIndex(currentId) else hitIndex(fromId)) - 1
      currentId = hitIds(first + (if (k < 0) lines - 1 else k))
      currentOccurrence = hitCounts(first + (if (k < 0) lines - 1 else k)) - 1
    }
    true
  }

  private def note(id: Long, count: Int): Unit = if (count > 0) {
    if (last == hitIds.length) {
      val n = last - first
      val bigger = n * 2 >= hitIds.length
      val ids = if (bigger) new Array[Long](hitIds.length * 2) else hitIds
      val counts = if (bigger) new Array[Int](hitIds.length * 2) else hitCounts
      var i = 0
      while (i < n) {
        ids(i) = hitIds(first + i)
        counts(i) = hitCounts(first + i)
        i += 1
      }
      hitIds = ids
      hitCounts = counts
      first = 0
      last = n
    }
    hitIds(last) = id
    hitCounts(last) = count
    last += 1
    total += count
  }
}
