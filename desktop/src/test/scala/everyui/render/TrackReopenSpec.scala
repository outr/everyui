package everyui.render

import java.net.{InetAddress, InetSocketAddress, ServerSocket, Socket}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A track chosen on Android's player reopening a file played whole over the network (TrackReopen), and the player's
  * connections closed outright before it is asked for again (MediaSockets). */
class TrackReopenSpec extends AnyWordSpec with Matchers {
  private val plex = "https://192-168-1-2.abc.plex.direct:32400/library/parts/1/file.mkv?X-Plex-Token=t"

  "TrackReopen" should {
    "reopen a file played whole over the network for a track of its own" in {
      TrackReopen.needed(plex, adaptive = false, Some("2")) shouldBe true
      TrackReopen.needed("HTTP://host/film.mp4", adaptive = false, Some("0:1")) shouldBe true
      // the stream's own choice of sound turns one of the file's on
      TrackReopen.needed(plex, adaptive = false, Some("")) shouldBe true
    }
    "switch in place for an adaptive stream, a file on the device, text off and a sidecar" in {
      TrackReopen.needed("https://host/master.m3u8", adaptive = true, Some("0:1")) shouldBe false
      TrackReopen.needed("file:///sdcard/film.mkv", adaptive = false, Some("1")) shouldBe false
      TrackReopen.needed("asset:///media/test.mkv", adaptive = false, Some("1")) shouldBe false
      TrackReopen.needed(plex, adaptive = false, None) shouldBe false
      TrackReopen.needed(plex, adaptive = false, Some("1:https://host/subs.srt")) shouldBe false
      TrackReopen.needed(plex, adaptive = false, Some("12:x")) shouldBe false
    }
  }

  /** A server that streams to its first connection until it is closed, and says (the latch) when that was. */
  private def streaming(): (ServerSocket, CountDownLatch) = {
    val server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
    val ended = new CountDownLatch(1)
    val thread = new Thread(() => {
      val s = server.accept()
      val chunk = new Array[Byte](64 * 1024)
      try while (true) s.getOutputStream.write(chunk)
      catch { case _: Throwable => ended.countDown() }
      finally s.close()
    })
    thread.setDaemon(true)
    thread.start()
    (server, ended)
  }

  "MediaSockets" should {
    "keep the sockets it makes, connected after as OkHttp does, and close them all at once" in {
      val sockets = new MediaSockets
      val (server, ended) = streaming()
      try {
        val s = sockets.createSocket()
        s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress, server.getLocalPort), 5000)
        s.getInputStream.read(new Array[Byte](1000)) should be > 0
        sockets.open shouldBe 1
        // the server stops being able to send the moment they are closed, unread as they are
        sockets.closeAll() shouldBe 1
        s.isClosed shouldBe true
        sockets.open shouldBe 0
        ended.await(10, TimeUnit.SECONDS) shouldBe true
        sockets.closeAll() shouldBe 0
      }
      finally server.close()
    }
    "end a read left waiting on a server that sends nothing" in {
      val sockets = new MediaSockets
      val silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
      try {
        val s = sockets.createSocket(InetAddress.getLoopbackAddress, silent.getLocalPort)
        val held = silent.accept()
        val failed = new CountDownLatch(1)
        val reading = new AtomicBoolean(false)
        val reader = new Thread(() => {
          reading.set(true)
          try s.getInputStream.read() catch { case _: Throwable => () }
          failed.countDown()
        })
        reader.setDaemon(true)
        reader.start()
        while (!reading.get) Thread.sleep(5)
        sockets.closeAll() shouldBe 1
        failed.await(10, TimeUnit.SECONDS) shouldBe true
        held.close()
      }
      finally silent.close()
    }
    "count only those still open" in {
      val sockets = new MediaSockets
      val listening = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
      try {
        val a = sockets.createSocket(InetAddress.getLoopbackAddress, listening.getLocalPort)
        val b = sockets.createSocket(InetAddress.getLoopbackAddress.getHostAddress, listening.getLocalPort)
        sockets.open shouldBe 2
        a.close()
        sockets.open shouldBe 1
        sockets.closeAll() shouldBe 1
        b.isClosed shouldBe true
      }
      finally listening.close()
    }
  }
}
