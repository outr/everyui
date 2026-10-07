package everyui.log

import fabric.rw.*

/** The codecs of a log's wire types, for a durable socket carrying `LogEvent`s (`LogFeed` on the client,
  * `LogBacklog` on the server): `import everyui.log.LogWire.given`. They live here, with the network module, so the
  * types themselves stay plain where no codec is wanted. */
object LogWire {
  given event: RW[LogEvent] = RW.gen
  given subscriber: RW[LogSubscriber] = RW.gen
  given since: RW[LogSince] = RW.gen
  given batch: RW[LogBatch] = RW.gen
}
