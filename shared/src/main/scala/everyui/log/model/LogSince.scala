package everyui.log

/** A client's request for the lines it does not have (the durable socket's RPC): those of `channel` after `after`, the
  * last seq it holds (0 for all the server keeps). It asks on a new session, where there is no replay to resume. */
case class LogSince(after: Long, channel: String = "")
