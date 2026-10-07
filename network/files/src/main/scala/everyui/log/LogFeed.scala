package everyui.log

import everyui.log.LogWire.given
import everyui.network.Network
import everyui.render.Platform
import rapid.Task
import spice.http.durable.{DurableSocketClient, DurableSocketConfig}

/** A server's log streamed into a `LogView` over a durable socket (`LogFeed.connect`), the server keeping it with a
  * `LogBacklog`.
  *
  * The socket's own sequencing does the work: each event comes with its seq, and a connection that drops and resumes
  * has what it missed replayed. A session the server no longer holds (the first, or one it let go) asks for what it
  * does not have (`LogSince` the view's last seq) and shows those before what arrives meanwhile. Lines the server no
  * longer keeps are shown as a marker ("… 12 lines missed"); a server that started again, numbering from 1 once
  * more, is marked too, and its lines follow.
  *
  * {{{
  *   val client = LogFeed.connect(panel.view, "wss://example.com/logs", "pipeline")
  *   // … client.close() when the screen goes
  * }}} */
class LogFeed(view: LogView, ask: Long => Task[LogBatch]) {
  // whether a new session's backlog is being asked for, and what arrived meanwhile (newest first)
  private var asking = false
  private var waiting: List[LogEvent] = Nil
  private var epoch = ""

  /** A handshake done: `resumed` where the server held the session, and replays what it missed. */
  def connected(resumed: Boolean): Unit = if (!resumed) request(view.lastSeq)

  // what is held after `after` asked for, live events held meanwhile
  private def request(after: Long): Unit = {
    asking = true
    ask(after).map(batch => Platform.later(() => settle(batch))).handleError { t =>
      Platform.later(() => {
        Platform.logError("log feed: the backlog", t)
        flush()
      })
      Task.unit
    }.start()
  }

  /** An event, with the seq the socket gave it. */
  def event(seq: Long, event: LogEvent): Unit = {
    val e = event.copy(seq = seq)
    if (asking) waiting = e :: waiting else live(e)
  }

  /** The backlog a new session asked for: what is no longer kept marked, its events, then what came meanwhile. From
    * a server that started again (another `epoch`), that is marked and its whole log asked for. */
  def settle(batch: LogBatch): Unit =
    if (epoch.nonEmpty && batch.epoch != epoch) {
      epoch = batch.epoch
      view.restarted()
      request(0L)
    } else {
      epoch = batch.epoch
      val after = view.lastSeq
      if (after > 0L && batch.first > after + 1L) view.missed(batch.first - after - 1L)
      batch.events.foreach(e => view.append(e))
      flush()
    }

  private def flush(): Unit = {
    asking = false
    val held = waiting.reverse
    waiting = Nil
    held.foreach(live)
  }

  // an event as it comes: a gap before it that the replay could not fill marked first
  private def live(e: LogEvent): Unit = {
    val last = view.lastSeq
    if (last > 0L && e.seq > last + 1L) view.missed(e.seq - last - 1L)
    view.append(e)
  }
}

object LogFeed {
  /** `view` fed from the log a server keeps under `channel` at `url` (a durable socket's endpoint), from now on and
    * across dropped connections; the client, to close when it is no longer wanted. */
  def connect(view: LogView, url: String, channel: String = "logs",
              config: DurableSocketConfig = DurableSocketConfig()): DurableSocketClient[String, LogEvent, LogSubscriber] = {
    val client = Network.durable[LogEvent, LogSubscriber](url, channel, LogSubscriber(channel), config)
    val feed = new LogFeed(view, after => client.ask[LogSince, LogBatch](LogSince(after, channel)))
    client.onConnected.attach(resumed => Platform.later(() => feed.connected(resumed)))
    client.onEvent.attach { case (seq, e) => Platform.later(() => feed.event(seq, e)) }
    client.connect().handleError { t =>
      Platform.later(() => Platform.logError("log feed: connecting to " + url, t))
      Task.unit
    }.start()
    client
  }
}
