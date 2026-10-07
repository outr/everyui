package everyui.log

import everyui.log.LogWire.given
import fabric.*
import fabric.rw.*
import rapid.Task
import spice.http.durable.EventLog

/** A server's log, kept for the clients reading it: the newest `capacity` `LogEvent`s of each channel, numbered from
  * 1 as they are sent. It is the `EventLog` of a Spice `DurableSocketServer` carrying them, so a client that drops and
  * resumes has what it missed replayed from here (as far back as it reaches: a `LogView` marks what it could not
  * have); and it answers a new session's `LogSince` (`answer`) with what it holds after the client's last seq.
  *
  * {{{
  *   val logs = new LogBacklog(10000)
  *   val server = new DurableSocketServer[String, LogEvent, LogSubscriber](eventLog = logs,
  *     resolveChannel = (_, who) => Task.pure(who.channel), onRequest = (who, json) => logs.answer(who.channel, json))
  *   server.broadcast("pipeline", LogEvent.of("\u001b[32mstarted\u001b[0m", Some(LogLevel.Info)))
  * }}} */
class LogBacklog(capacity: Int = 10000) extends EventLog[String, LogEvent] {
  /** This run of the log: a client seeing it change knows the numbering started again. */
  val epoch: String = System.currentTimeMillis().toString

  private var channels: Map[String, LogRing] = Map.empty

  private def kept(channel: String): LogRing = synchronized {
    channels.getOrElse(channel, {
      val k = new LogRing(math.max(1, capacity))
      channels = channels.updated(channel, k)
      k
    })
  }

  /** `event` kept with the next seq of its channel (and the time, where it has none); that seq. */
  override def append(channelId: String, event: LogEvent): Task[Long] = Task(kept(channelId).add(event))

  /** The events of a channel after `afterSeq` it still keeps, oldest first. */
  override def replay(channelId: String, afterSeq: Long): Task[List[(Long, LogEvent)]] =
    Task(kept(channelId).after(afterSeq).map(e => (e.seq, e)))

  /** What a new session asking `LogSince` is sent: the events after its seq, and what is kept. */
  def since(channel: String, after: Long): LogBatch = {
    val k = kept(channel)
    k.synchronized(LogBatch(k.after(after), k.first, k.last, epoch))
  }

  /** The answer to a client's request on `channel`, where it is a `LogSince`; an error for anything else. */
  def answer(channel: String, request: Json): Task[Json] =
    if (request.get("after").isEmpty) Task.error(new spice.http.durable.RpcException("not_log", "Not a LogSince"))
    else Task(since(channel, request.as[LogSince].after).json)
}
