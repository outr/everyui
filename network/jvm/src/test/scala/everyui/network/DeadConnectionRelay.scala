package everyui.network

import java.io.{InputStream, OutputStream}
import java.net.{InetAddress, ServerSocket, Socket}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.util.Try

/** A relay on a port of its own to `target` on this machine, as the network between a device and its server: `freeze`
  * stops it carrying the connections open then, each byte either way lost and neither end told, as a network that
  * dropped them says nothing, while connections made after go through. `connections` is how many have been made. */
class DeadConnectionRelay(target: Int) {
  private val listening = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
  private val made = new AtomicInteger(0)
  @volatile private var open: List[(AtomicBoolean, Socket, Socket)] = Nil
  @volatile private var closed = false

  def port: Int = listening.getLocalPort
  def connections: Int = made.get()

  /** Every connection open now dead, silently. */
  def freeze(): Unit = open.foreach(_._1.set(true))

  def close(): Unit = {
    closed = true
    Try(listening.close())
    open.foreach { case (_, a, b) =>
      Try(a.close())
      Try(b.close())
    }
  }

  private def thread(name: String)(run: => Unit): Unit = {
    val t = new Thread(() => run, name)
    t.setDaemon(true)
    t.start()
  }

  // bytes from `in` to `out` until either closes, or lost once the connection is frozen
  private def pump(frozen: AtomicBoolean, in: InputStream, out: OutputStream): Unit = Try {
    val buffer = new Array[Byte](16384)
    var n = in.read(buffer)
    while (n >= 0) {
      if (!frozen.get()) {
        out.write(buffer, 0, n)
        out.flush()
      }
      n = in.read(buffer)
    }
  }

  thread("relay-accept") {
    while (!closed) Try(listening.accept()).foreach { device =>
      val server = new Socket(InetAddress.getLoopbackAddress, target)
      val frozen = new AtomicBoolean(false)
      open = (frozen, device, server) :: open
      made.incrementAndGet()
      thread("relay-up")(pump(frozen, device.getInputStream, server.getOutputStream))
      thread("relay-down")(pump(frozen, server.getInputStream, device.getOutputStream))
    }
  }
}
