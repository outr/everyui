package everyui.log

/** What a log client says about itself as it connects (the durable socket's `Info`): the `channel` it reads, the
  * log a server keeps under that name. */
case class LogSubscriber(channel: String)
