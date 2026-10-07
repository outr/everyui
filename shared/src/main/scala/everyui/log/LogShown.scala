package everyui.log

/** The ids of the lines a log view shows, in order (those its filter lets through), and, where its lines wrap, how
  * many rows each takes before it, kept as running totals and worked out as far as they are asked for. */
private[log] class LogShown {
  private var ids: Array[Long] = new Array[Long](256)
  private var rows: Array[Int] = new Array[Int](257)
  private var start = 0
  private var end = 0
  // the running totals are right up to here
  private var valid = 0

  def size: Int = end - start

  /** The id shown at `index`. */
  def id(index: Int): Long = ids(start + index)

  /** Where `id` is shown, -1 where it is not. */
  def indexOf(id: Long): Int = {
    val at = place(id)
    if (at < end && ids(at) == id) at - start else -1
  }

  /** `id` shown after the rest. */
  def append(id: Long): Unit = {
    room()
    ids(end) = id
    end += 1
  }

  /** `id` shown in its place among the rest, by its order; where it is shown. */
  def insert(id: Long): Int = {
    val at = place(id)
    if (at < end && ids(at) == id) at - start
    else {
      room()
      var k = end
      while (k > at) {
        ids(k) = ids(k - 1)
        k -= 1
      }
      ids(at) = id
      end += 1
      if (valid > at) valid = at
      at - start
    }
  }

  /** The ids before `id` let go from the front; how many went. */
  def dropBefore(id: Long): Int = {
    val from = start
    while (start < end && ids(start) < id) start += 1
    if (valid < start) {
      valid = start
      rows(start) = 0
    }
    start - from
  }

  def clear(): Unit = {
    start = 0
    end = 0
    valid = 0
    rows(0) = 0
  }

  /** The rows' running totals are to be worked out again: the lines wrap to a new width. */
  def rewrap(): Unit = valid = start

  /** How many rows the lines before `index` take, each `rowsOf` its id. */
  def rowsBefore(index: Int, rowsOf: Long => Int): Int = {
    val k = start + math.max(0, math.min(index, size))
    total(k, rowsOf)
    rows(k) - rows(start)
  }

  /** The line at `row` rows down, kept within them. */
  def indexAtRow(row: Int, rowsOf: Long => Int): Int = if (size == 0) -1 else {
    total(end, rowsOf)
    val base = rows(start)
    var low = start
    var high = end - 1
    while (low < high) {
      val mid = (low + high + 1) / 2
      if (rows(mid) - base <= row) low = mid else high = mid - 1
    }
    low - start
  }

  private def total(to: Int, rowsOf: Long => Int): Unit = {
    if (valid < start) {
      valid = start
      rows(start) = 0
    }
    while (valid < to) {
      rows(valid + 1) = rows(valid) + rowsOf(ids(valid))
      valid += 1
    }
  }

  // the first place whose id is not below `id`
  private def place(id: Long): Int = {
    var low = start
    var high = end
    while (low < high) {
      val mid = (low + high) / 2
      if (ids(mid) < id) low = mid + 1 else high = mid
    }
    low
  }

  // room for one more at the end: the front's let-go places taken back, else twice the room
  private def room(): Unit = if (end == ids.length) {
    val count = end - start
    val keep = valid - start
    val nextIds = if (start > ids.length / 2) ids else new Array[Long](ids.length * 2)
    val nextRows = if (start > ids.length / 2) rows else new Array[Int](ids.length * 2 + 1)
    var i = 0
    while (i < count) {
      nextIds(i) = ids(start + i)
      i += 1
    }
    i = 0
    while (i <= keep) {
      nextRows(i) = rows(start + i)
      i += 1
    }
    ids = nextIds
    rows = nextRows
    start = 0
    end = count
    valid = keep
  }
}
