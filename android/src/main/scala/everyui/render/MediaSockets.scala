package everyui.render

import java.net.{InetAddress, Socket}

/** The sockets one player's media client opens, kept so they can all be closed at once ([[closeAll]]): the player
  * reopening a file lets go of every connection it held before it asks again, rather than leaving them to its
  * loading thread, which closes them when it gets round to it. Plain JVM: OkHttp is given it as its socket factory,
  * and a TLS connection is layered over the socket made here, so closing that closes it too, as OkHttp's own cancel
  * does. */
class MediaSockets extends javax.net.SocketFactory {
  // OkHttp's threads make them, the main thread closes them
  private var made: List[Socket] = Nil

  private def kept(s: Socket): Socket = synchronized {
    made = s :: made.filterNot(_.isClosed)
    s
  }

  override def createSocket(): Socket = kept(new Socket())
  override def createSocket(host: String, port: Int): Socket = kept(new Socket(host, port))
  override def createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket =
    kept(new Socket(host, port, local, localPort))
  override def createSocket(host: InetAddress, port: Int): Socket = kept(new Socket(host, port))
  override def createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket =
    kept(new Socket(host, port, local, localPort))

  /** How many are open now. */
  def open: Int = synchronized(made.count(!_.isClosed))

  /** Closes every socket made here that is still open, and says how many that was. */
  def closeAll(): Int = {
    val all = synchronized {
      val was = made
      made = Nil
      was.filterNot(_.isClosed)
    }
    all.foreach { s =>
      // shut first: a close alone waits for a thread blocked reading it to come out before the connection goes,
      // where a shutdown ends it at once, the reader with it
      try s.shutdownInput() catch { case _: Throwable => () }
      try s.shutdownOutput() catch { case _: Throwable => () }
      try s.close() catch { case _: Throwable => () }
    }
    all.size
  }
}
