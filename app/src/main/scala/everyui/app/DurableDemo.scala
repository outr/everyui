package everyui.app

import everyui.app.durable.{ClientInfo, DemoRequest, DemoResponse, Tick}
import everyui.component.TextView
import everyui.network.Network
import everyui.render.Platform
import everyui.ui
import rapid.Task
import spice.http.durable.{DurableSocketConfig, ProtocolState, ReconnectStrategy}

import scala.concurrent.duration.DurationInt

/** The network module's example, and its check on every backend (tools/check.sh: `demo=durable`,
  * `socket=ws://host:8731/durable` — the endpoint `server/` hosts).
  *
  * It connects a durable client, takes five ticker events, makes an RPC round trip, then asks the server to drop
  * the connection: the client must reconnect, resume, and receive the events it missed with no gap in either the
  * sequence numbers or the server's counter. Five more events after that and it logs
  * `[ui] durable ok events=N resumed=1 gaps=0`, or `[ui] durable FAIL …`. The screen shows the state and the last
  * event, so a device that runs it live shows something too. */
object DurableDemo:
  class State:
    var received: Int = 0
    var lastSeq: Long = 0L
    var lastN: Long = -1L
    var gaps: Int = 0
    var activations: Int = 0
    var phase: String = "connecting"

  def run(): Unit =
    val url = Platform.launchArg("socket")
    val status = new TextView
    status.position.left @= 40.0
    status.position.top @= 40.0
    status.content @= "durable: " + url
    ui.children += status
    val last = new TextView
    last.position.left @= 40.0
    last.position.top @= 100.0
    ui.children += last
    if url.isEmpty then fail("no socket url: launch with socket=ws://host:8731/durable")
    else start(url, status, last)

  private def start(url: String, status: TextView, last: TextView): Unit =
    val state = new State
    val client = Network.durable[Tick, ClientInfo](url, "ticker", ClientInfo("demo-" + Platform.ticks().toString),
      DurableSocketConfig(reconnectStrategy = ReconnectStrategy.fixedInterval(250.millis)))

    client.state.attach { s =>
      Platform.log("durable state=" + s.toString)
      status.content @= "durable " + s.toString + " events=" + state.received.toString
      if s == ProtocolState.Active then state.activations = state.activations + 1
    }

    client.onEvent.attach { case (seq, tick) =>
      if state.lastSeq != 0L && seq != state.lastSeq + 1L then state.gaps = state.gaps + 1
      if state.lastN >= 0L && tick.n != state.lastN + 1L then state.gaps = state.gaps + 1
      state.lastSeq = seq
      state.lastN = tick.n
      state.received = state.received + 1
      last.content @= "event seq=" + seq.toString + " n=" + tick.n.toString
      Platform.log("durable event seq=" + seq.toString + " n=" + tick.n.toString)

      if state.phase == "connecting" && state.received >= 5 then
        state.phase = "rpc"
        client.ask[DemoRequest, DemoResponse](DemoRequest("echo", Some("ping"))).map { answer =>
          Platform.log("durable rpc echo=" + answer.echo.getOrElse(""))
          if answer.echo.contains("ping") then
            state.phase = "dropping"
            Platform.log("durable asking the server to drop the connection")
            // the answer may not make it back before the socket goes; that is the point
            client.ask[DemoRequest, DemoResponse](DemoRequest("drop")).handleError(_ => Task.unit).start()
          else fail("rpc echo mismatch")
        }.handleError { t =>
          fail("rpc failed: " + t.toString)
          Platform.logError("durable rpc", t)
          Task.unit
        }.start()
      else if state.phase == "dropping" && state.activations >= 2 && state.received >= 10 then
        state.phase = "done"
        if state.gaps == 0 then
          Platform.log("durable ok events=" + state.received.toString + " resumed=" + (state.activations - 1).toString + " gaps=0")
        else fail("gaps=" + state.gaps.toString)
        client.close()
        Platform.quit()
    }

    client.connect().handleError { t =>
      fail("connect failed: " + t.toString)
      Platform.logError("durable connect", t)
      Task.unit
    }.start()

  private def fail(why: String): Unit = Platform.log("durable FAIL " + why)
