package everyui.log

/** A server's answer to `LogSince`: the `events` after the seq asked for that it still keeps, oldest first; the first
  * and last seq it keeps (`first` 0 where it keeps none), so a client can tell lines it will never have; and `epoch`,
  * its log's run, which changes when the server starts again and numbers its lines from 1 once more. */
case class LogBatch(events: List[LogEvent], first: Long, last: Long, epoch: String)
