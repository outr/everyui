package everyui.tray

import java.net.{StandardProtocolFamily, UnixDomainSocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** A connection to a D-Bus bus over its Unix socket, spoken by hand (no library: the desktop builds as a GraalVM
  * native image, where one that reflects is trouble): authenticated as this process's user (EXTERNAL), then said
  * Hello to. Messages are read on a thread of its own and handed to `deliver` (the JavaFX thread, for an app), where
  * a method call to an `serve`d path is answered, a reply goes to the call that asked for it, and a signal to each
  * `onSignal`. Writes are one at a time from any thread. */
class DBusConnection private (channel: SocketChannel, deliver: (() => Unit) => Unit) {
  private val serials = new AtomicInteger(0)
  private val replies = new ConcurrentHashMap[Int, DBusMessage => Unit]()
  // replies heard on the reading thread itself, by a caller waiting for them (`ask`)
  private val direct = new ConcurrentHashMap[Int, DBusMessage => Unit]()
  @volatile private var exported: Map[String, DBusMessage => Option[(String, List[Any])]] = Map.empty
  @volatile private var listeners: List[DBusMessage => Unit] = Nil
  @volatile private var open = true
  /** The bus's name for this connection (":1.42"), once it has said Hello. */
  @volatile var uniqueName: String = ""

  private def nextSerial(): Int = serials.incrementAndGet()

  private def send(m: DBusMessage): Unit = if (open) {
    val bytes = ByteBuffer.wrap(DBusWire.encode(m))
    channel.synchronized {
      try while (bytes.hasRemaining) channel.write(bytes)
      catch { case _: java.io.IOException => close() }
    }
  }

  /** Method `member` of `interface` on `path` at `destination`, with `args` typed by `signature`; `reply` hears its
    * return or its error (`errorName` set), on `deliver`'s thread. */
  def call(destination: String, path: String, interface: String, member: String, signature: String = "",
           args: List[Any] = Nil)(reply: DBusMessage => Unit = _ => ()): Unit = {
    val serial = nextSerial()
    replies.put(serial, reply)
    send(DBusMessage(DBusMessage.Call, serial, path, interface, member, destination = destination,
      signature = signature, body = args))
  }

  /** `call`, waited for (five seconds at most) on this thread, its reply heard here rather than through `deliver`: for
    * what must be known before going on (the bus's Hello, whether a name has an owner). Never from the reading
    * thread. */
  def ask(destination: String, path: String, interface: String, member: String, signature: String = "",
          args: List[Any] = Nil): Option[DBusMessage] = {
    val answer = new java.util.concurrent.CompletableFuture[DBusMessage]()
    val serial = nextSerial()
    direct.put(serial, m => answer.complete(m))
    send(DBusMessage(DBusMessage.Call, serial, path, interface, member, destination = destination,
      signature = signature, body = args))
    try Some(answer.get(5, java.util.concurrent.TimeUnit.SECONDS)).filter(_.kind == DBusMessage.Return)
    catch { case _: Exception =>
      direct.remove(serial)
      None
    }
  }

  /** Signal `member` of `interface` from `path`, with `args` typed by `signature`. */
  def signal(path: String, interface: String, member: String, signature: String = "", args: List[Any] = Nil): Unit =
    send(DBusMessage(DBusMessage.Signal, nextSerial(), path, interface, member, signature = signature, body = args))

  /** Calls to `path` answered by `handler`: its return's signature and values, or None for an error that it has no
    * such method. */
  def serve(path: String)(handler: DBusMessage => Option[(String, List[Any])]): Unit =
    exported = exported + (path -> handler)

  /** Every signal that reaches this connection (as the bus's match rules let them: `addMatch`). */
  def onSignal(f: DBusMessage => Unit): Unit = listeners = f :: listeners

  /** Signals matching `rule` ("type='signal',interface='…'") sent here by the bus. */
  def addMatch(rule: String): Unit =
    call(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "AddMatch", "s", List(rule))()

  /** The connection let go of. */
  def close(): Unit = if (open) {
    open = false
    try channel.close() catch { case _: java.io.IOException => () }
  }

  def isOpen: Boolean = open

  private def received(m: DBusMessage): Unit = m.kind match {
    case DBusMessage.Return | DBusMessage.Error =>
      val now = direct.remove(m.replySerial)
      if (now != null) now(m)
      else {
        val f = replies.remove(m.replySerial)
        if (f != null) deliver(() => f(m))
      }
    case DBusMessage.Signal => deliver(() => listeners.foreach(_(m)))
    case DBusMessage.Call => deliver(() => answer(m))
    case _ => ()
  }

  private def answer(m: DBusMessage): Unit = {
    val handled = exported.get(m.path).flatMap(h =>
      try h(m)
      catch { case t: Throwable =>
        everyui.scribe.warn("dbus: " + m.interface + "." + m.member + " on " + m.path + " failed: " + t)
        None
      })
    if (!m.noReply) handled match {
      case Some((sig, values)) =>
        send(DBusMessage(DBusMessage.Return, nextSerial(), replySerial = m.serial, destination = m.sender,
          signature = sig, body = values))
      case None =>
        send(DBusMessage(DBusMessage.Error, nextSerial(), errorName = "org.freedesktop.DBus.Error.UnknownMethod",
          replySerial = m.serial, destination = m.sender, signature = "s",
          body = List("No " + m.interface + "." + m.member + " on " + m.path)))
    }
  }

  private def readLoop(): Unit = {
    val head = ByteBuffer.allocate(16)
    try while (open) {
      head.clear()
      fill(head)
      val first = head.array().clone()
      val whole = ByteBuffer.allocate(DBusWire.length(first))
      whole.put(first)
      fill(whole)
      received(DBusWire.decode(whole.array()))
    }
    catch { case _: java.io.IOException | _: IllegalStateException => close() }
  }

  private def fill(b: ByteBuffer): Unit = while (b.hasRemaining) {
    if (channel.read(b) < 0) throw new java.io.IOException("the bus closed the connection")
  }

  private def start(): Unit = {
    val reader = new Thread(() => readLoop(), "dbus-reader")
    reader.setDaemon(true)
    reader.start()
  }
}

object DBusConnection {
  val Bus: String = "org.freedesktop.DBus"
  val BusPath: String = "/org/freedesktop/DBus"

  /** The session bus (`DBUS_SESSION_BUS_ADDRESS`, else the user's at `/run/user/<uid>/bus`), its messages handed to
    * `deliver`; None where there is none to be had (not Linux, no session, a socket refused). */
  def session(deliver: (() => Unit) => Unit): Option[DBusConnection] =
    sessionPath.flatMap(p => open(p, deliver))

  /** The bus at the Unix socket `path`. */
  def open(path: String, deliver: (() => Unit) => Unit): Option[DBusConnection] =
    try {
      val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
      channel.connect(UnixDomainSocketAddress.of(path))
      if (!authenticate(channel)) {
        channel.close()
        None
      }
      else {
        val c = new DBusConnection(channel, deliver)
        c.start()
        c.uniqueName = c.ask(Bus, BusPath, Bus, "Hello").flatMap(_.body.headOption).map(_.toString).getOrElse("")
        Some(c)
      }
    }
    catch { case _: Exception => None }

  private def sessionPath: Option[String] = {
    val address = Option(System.getenv("DBUS_SESSION_BUS_ADDRESS")).getOrElse("")
    val named = address.split(';').toList.flatMap { a =>
      if (!a.startsWith("unix:")) Nil
      else a.substring(5).split(',').toList.collect { case kv if kv.startsWith("path=") => kv.substring(5) }
    }.headOption
    named.orElse(uid.map(u => "/run/user/" + u + "/bus")).filter(p => new java.io.File(p).exists)
  }

  /** This process's user id, from `/proc/self/status` (Linux). */
  def uid: Option[String] =
    try {
      val lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get("/proc/self/status"))
      var found: Option[String] = None
      lines.forEach(l =>
        if (found.isEmpty && l.startsWith("Uid:")) found = l.substring(4).trim.split("\\s+").headOption)
      found
    } catch { case _: Exception => None }

  // EXTERNAL: the bus knows who is on the other end of a Unix socket, and takes their id as said
  private def authenticate(channel: SocketChannel): Boolean = uid.exists { id =>
    val hex = id.getBytes(StandardCharsets.US_ASCII).map(b => "%02x".format(b)).mkString
    write(channel, "\u0000AUTH EXTERNAL " + hex + "\r\n")
    val answer = line(channel)
    answer.startsWith("OK") && {
      write(channel, "BEGIN\r\n")
      true
    }
  }

  private def write(channel: SocketChannel, s: String): Unit = {
    val b = ByteBuffer.wrap(s.getBytes(StandardCharsets.US_ASCII))
    while (b.hasRemaining) channel.write(b)
  }

  private def line(channel: SocketChannel): String = {
    val sb = new StringBuilder
    val one = ByteBuffer.allocate(1)
    var done = false
    while (!done) {
      one.clear()
      if (channel.read(one) <= 0) done = true
      else {
        val c = one.get(0).toChar
        if (c == '\n') done = true else if (c != '\r') sb.append(c)
      }
    }
    sb.toString
  }
}
