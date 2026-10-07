package everyui.log

/** One channel of a `LogBacklog`: a ring of its newest events, and the last seq given. */
private[log] class LogRing(capacity: Int) {
  private val ring = new Array[LogEvent](capacity)
  private var head = 0
  private var held = 0
  var last: Long = 0L

  /** The oldest seq kept, 0 for none. */
  def first: Long = synchronized(if (held == 0) 0L else ring(head).seq)

  /** `event` kept with the next seq (and the time, where it has none); that seq. */
  def add(event: LogEvent): Long = synchronized {
    last += 1L
    val e = event.copy(seq = last, at = if (event.at > 0L) event.at else System.currentTimeMillis())
    ring((head + held) % capacity) = e
    if (held < capacity) held += 1 else head = (head + 1) % capacity
    last
  }

  /** The events kept after `seq`, oldest first. */
  def after(seq: Long): List[LogEvent] = synchronized {
    var out: List[LogEvent] = Nil
    var i = held - 1
    while (i >= 0 && ring((head + i) % capacity).seq > seq) {
      out = ring((head + i) % capacity) :: out
      i -= 1
    }
    out
  }
}
