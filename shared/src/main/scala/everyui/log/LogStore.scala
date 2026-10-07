package everyui.log

/** A log view's lines, oldest first, at most `capacity` of them: a ring, so the oldest goes in one step as a new one
  * comes. Each line has an id, rising down the lines and spaced apart, so one put in between two (an event that came
  * late) has an id of its own between theirs. */
private[log] class LogStore(val capacity: Int) {
  private var ring: Array[LogLine] = new Array[LogLine](math.max(1, math.min(capacity, 256)))
  private var head = 0
  private var held = 0
  private var lastId = 0L
  /** How many lines have been let go from the front since the last `clear`: the first line held is line
    * `dropped + 1`. */
  var dropped: Long = 0L

  def size: Int = held

  /** The line at `index`, 0 the oldest held. */
  def apply(index: Int): LogLine = ring((head + index) % ring.length)

  /** The line at the end, `line` with an id after the last; the line let go to make room, if one was. */
  def add(line: LogLine): (LogLine, Option[LogLine]) = {
    val gone = if (held == capacity) Some(dropFirst()) else None
    grow()
    lastId = lastId + LogStore.spacing
    val placed = line.copy(id = lastId)
    ring((head + held) % ring.length) = placed
    held += 1
    (placed, gone)
  }

  /** `line` put in at `index` (0 to `size`), with an id between those either side; the line let go to make room, if
    * one was. None where it would have been the oldest of a full store, and let go at once. */
  def insert(index: Int, line: LogLine): Option[(LogLine, Option[LogLine])] =
    if (index >= held) Some(add(line))
    else if (held == capacity && index == 0) None
    else {
      val gone = if (held == capacity) Some(dropFirst()) else None
      val at = if (gone.nonEmpty) index - 1 else index
      grow()
      val before = if (at > 0) apply(at - 1).id else apply(0).id - LogStore.spacing * 2L
      if (apply(at).id - before < 2L) {
        // no id left between the two: every line from here on moves up a step
        var k = at
        while (k < held) {
          set(k, apply(k).copy(id = apply(k).id + LogStore.spacing))
          k += 1
        }
        lastId = apply(held - 1).id
      }
      val placed = line.copy(id = (before + apply(at).id) / 2L)
      var k = held
      while (k > at) {
        set(k, apply(k - 1))
        k -= 1
      }
      set(at, placed)
      held += 1
      Some((placed, gone))
    }

  /** Where the line `id` is, -1 where it is not held. */
  def find(id: Long): Int = {
    var low = 0
    var high = held - 1
    var found = -1
    while (found < 0 && low <= high) {
      val mid = (low + high) / 2
      val at = apply(mid).id
      if (at == id) found = mid
      else if (at < id) low = mid + 1
      else high = mid - 1
    }
    found
  }

  /** The line `id`, where it is held. */
  def line(id: Long): Option[LogLine] = {
    val i = find(id)
    if (i < 0) None else Some(apply(i))
  }

  /** Line `index` replaced, its id kept. */
  def replace(index: Int, line: LogLine): Unit = set(index, line.copy(id = apply(index).id))

  def clear(): Unit = {
    ring = new Array[LogLine](math.max(1, math.min(capacity, 256)))
    head = 0
    held = 0
    dropped = 0L
  }

  private def set(index: Int, line: LogLine): Unit = ring((head + index) % ring.length) = line

  private def dropFirst(): LogLine = {
    val first = ring(head)
    ring(head) = null
    head = (head + 1) % ring.length
    held -= 1
    dropped += 1L
    first
  }

  // room for one more, doubling up to the capacity
  private def grow(): Unit = if (held == ring.length && ring.length < capacity) {
    val next = new Array[LogLine](math.min(capacity, ring.length * 2))
    var i = 0
    while (i < held) {
      next(i) = apply(i)
      i += 1
    }
    ring = next
    head = 0
  }
}

private[log] object LogStore {
  /** How far apart the ids of lines added at the end are. */
  val spacing: Long = 1024L
}
