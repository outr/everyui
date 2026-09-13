package everyui.network

import fabric.*
import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw.*
import rapid.Task
import spice.http.{ConnectionStatus, WebSocket}
import spice.http.durable.*

// The probe's payloads: fabric case classes with derived codecs, as an application's would be.
case class Note(text: String)
object Note:
  given rw: RW[Note] = RW.gen
case class Who(device: String)
object Who:
  given rw: RW[Who] = RW.gen
case class Say(say: String)
object Say:
  given rw: RW[Say] = RW.gen
case class Echoed(echo: String)
object Echoed:
  given rw: RW[Echoed] = RW.gen

/** A `WebSocket` whose peer is a DurableSocket server living in the same object: every text the client sends is
  * answered synchronously, as spice's `DurableSocketServer` would answer it. The server half speaks the wire
  * protocol (the envelope is JSON by definition); the payloads inside it are case classes. No network, no
  * threads — the durable client's state machine alone, so the same program prints the same lines natively and
  * emitted. */
class LoopbackSocket(log: Log) extends WebSocket:
  var serverSeq: Long = 0L
  var lastAcked: Long = 0L
  var received: List[Note] = Nil

  override def connect(): Task[ConnectionStatus] =
    _status @= ConnectionStatus.Open
    send.text.attach(text => serve(text))
    Task.pure(ConnectionStatus.Open)

  override def disconnect(): Unit =
    _status @= ConnectionStatus.Closed
    receive.close @= ()

  /** The server pushes an event to the client (one Event type each way on a socket, in spice's model). */
  def push(value: Note): Unit =
    serverSeq = serverSeq + 1
    receive.text @= JsonFormatter.Default(obj("type" -> str("event"), "seq" -> num(serverSeq), "data" -> value.json))

  private def serve(text: String): Unit =
    val json = JsonParser(text)
    json("type").asString match
      case "connect" =>
        log.add("server: connect " + json("info").as[Who].device)
        receive.text @= JsonFormatter.Default(obj("type" -> str("connected"), "lastClientSeq" -> num(0L), "resumed" -> bool(false)))
      case "resume" =>
        log.add("server: resume lastSeq=" + json("lastSeq").asLong.toString)
        receive.text @= JsonFormatter.Default(obj("type" -> str("connected"), "lastClientSeq" -> num(0L), "resumed" -> bool(true)))
      case "event" =>
        val note = json("data").as[Note]
        received = received :+ note
        log.add("server: event seq=" + json("seq").asLong.toString + " " + note.text)
      case "ack" =>
        lastAcked = json("seq").asLong
      case "request" =>
        val answer = Echoed(json("data").as[Say].say)
        receive.text @= JsonFormatter.Default(obj("type" -> str("response"), "id" -> json("id"), "data" -> answer.json))
      case other =>
        log.add("server: ? " + other)

class Log(var items: List[String]):
  def add(s: String): Unit = items = items ::: List(s)

object NetworkProbe:
  def check(name: String, got: String, expected: String): Unit =
    if got == expected then println("RX PASS " + name + " = " + got)
    else println("RX FAIL " + name + " got=" + got + " expected=" + expected)

  def run(): Unit =
    val log = new Log(Nil)
    var socket: LoopbackSocket = null
    val client = new DurableSocketClient[String, Note, Who](
      createWebSocket = () => { socket = new LoopbackSocket(log); socket },
      config = DurableSocketConfig(reconnectStrategy = ReconnectStrategy.none),
      outboundLog = new InMemoryEventLog[String, Note],
      initialChannelId = "probe",
      info = Who("probe"),
      clientId = "client-1"
    )
    var events: List[String] = Nil
    client.onEvent.attach { case (seq, note) => events = events :+ (seq.toString + ":" + note.text) }

    client.connect().sync()
    check("state.active", client.state().toString, "Active")
    check("server.saw.connect", log.items.mkString("|"), "server: connect probe")

    socket.push(Note("one"))
    socket.push(Note("two"))
    check("events.received", events.mkString(","), "1:one,2:two")

    val seq = client.push(Note("hello")).sync()
    check("push.seq", seq.toString, "1")
    check("server.received", socket.received.map(_.text).mkString(","), "hello")

    val answer = client.ask[Say, Echoed](Say("ping")).sync()
    check("rpc.echo", answer.echo, "ping")

    // a dropped transport: the strategy is `none`, so the protocol closes
    socket.disconnect()
    check("state.after.drop", client.state().toString, "Closed")
    println("RX DONE")

@main def networkProbe(): Unit = NetworkProbe.run()
