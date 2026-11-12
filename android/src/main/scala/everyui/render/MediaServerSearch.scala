package everyui.render

import android.content.Context
import android.net.wifi.WifiManager
import everyui.MediaServer

import java.net.{DatagramPacket, DatagramSocket, Inet4Address, InetAddress, NetworkInterface, SocketTimeoutException}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters.*

/** The Jellyfin, Emby and Plex servers on the local network, each asked the way its own apps ask
  * (Platform.findMediaServers).
  *
  * Jellyfin and Emby answer a broadcast "who is JellyfinServer?" / "who is EmbyServer?" on UDP 7359 with JSON naming
  * their address, id and name. Plex answers GDM: an `M-SEARCH` to 239.0.0.250:32414 (and a broadcast, for a network
  * that drops multicast) with HTTP-like headers, from the address it serves on. Every answer comes straight back to
  * the asking socket, so each kind gets its own socket and the socket says which kind answered.
  *
  * The answers are unicast and need nothing held (a Google TV Streamer on Wi-Fi hears all three without it). The
  * Wi-Fi multicast lock is held for the search all the same where the app may take it
  * (CHANGE_WIFI_MULTICAST_STATE), as a device that filters multicast for an app holding none may drop more than it
  * should; without the permission the search goes on without it. */
object MediaServerSearch {
  private val JellyfinPort = 7359
  private val PlexPort = 32414

  def find(seconds: Double, found: List[MediaServer] => Unit): Unit = {
    val thread = new Thread(() => {
      val servers = try search(seconds) catch {
        case t: Throwable =>
          android.util.Log.w("everyui", "media server search failed", t)
          Nil
      }
      Platform.later(() => found(servers))
    }, "everyui-media-servers")
    thread.setDaemon(true)
    thread.start()
  }

  private def search(seconds: Double): List[MediaServer] = {
    val lock = multicastLock()
    val jellyfin = socket()
    val emby = socket()
    val plex = socket()
    try {
      val targets = broadcasts()
      targets.foreach { to =>
        send(jellyfin, "who is JellyfinServer?", to, JellyfinPort)
        send(emby, "who is EmbyServer?", to, JellyfinPort)
        send(plex, "M-SEARCH * HTTP/1.1\r\n\r\n", to, PlexPort)
      }
      send(plex, "M-SEARCH * HTTP/1.1\r\n\r\n", InetAddress.getByName("239.0.0.250"), PlexPort)
      val deadline = System.currentTimeMillis() + (seconds * 1000.0).toLong
      var servers = Map.empty[(String, String), MediaServer]
      var hosts = Set.empty[String]
      while (System.currentTimeMillis() < deadline) {
        List("jellyfin" -> jellyfin, "emby" -> emby, "plex" -> plex).foreach { (kind, s) =>
          receive(s).foreach { (text, from) =>
            val server = if (kind == "plex") plexAnswer(text, from) else jsonAnswer(kind, text)
            server.foreach(m => servers += (m.kind, m.id) -> m)
            hosts += from.getHostAddress
          }
        }
      }
      // a Jellyfin can keep quiet (its discovery turned off, or a container that never hears a broadcast): asked on
      // its usual port on each machine that answered for something else
      val quiet = hosts.filterNot { h =>
        servers.values.exists(m => m.kind == "jellyfin" && m.address.contains("//" + h + ":"))
      }
      quiet.flatMap(jellyfinAt).foreach(m => servers += (m.kind, m.id) -> m)
      servers.values.toList.sortBy(m => (m.kind, m.name.toLowerCase))
    }
    finally {
      jellyfin.close()
      emby.close()
      plex.close()
      lock.foreach(l => if (l.isHeld) l.release())
    }
  }

  private def socket(): DatagramSocket = {
    val s = new DatagramSocket()
    s.setBroadcast(true)
    s.setSoTimeout(60)
    s
  }

  /** The limited broadcast and each IPv4 network's own: a router may drop the first where it passes the second. */
  private def broadcasts(): List[InetAddress] = {
    val own = try NetworkInterface.getNetworkInterfaces.asScala.toList
      .filter(n => n.isUp && !n.isLoopback)
      .flatMap(_.getInterfaceAddresses.asScala)
      .filter(_.getAddress.isInstanceOf[Inet4Address])
      .flatMap(a => Option(a.getBroadcast))
    catch { case _: Throwable => Nil }
    (InetAddress.getByName("255.255.255.255") :: own).distinct
  }

  private def send(s: DatagramSocket, text: String, to: InetAddress, port: Int): Unit = {
    val bytes = text.getBytes(UTF_8)
    // one unreachable network (a VPN with no broadcast) must not stop the others being asked
    try s.send(new DatagramPacket(bytes, bytes.length, to, port))
    catch { case _: Throwable => () }
  }

  private def receive(s: DatagramSocket): Option[(String, InetAddress)] = {
    val packet = new DatagramPacket(new Array[Byte](4096), 4096)
    try {
      s.receive(packet)
      Some(new String(packet.getData, 0, packet.getLength, UTF_8) -> packet.getAddress)
    }
    catch { case _: SocketTimeoutException => None }
  }

  /** `{"Address":"http://192.168.1.50:8096","Id":"…","Name":"fileserver"}` */
  private def jsonAnswer(kind: String, text: String): Option[MediaServer] = try {
    val json = new org.json.JSONObject(text)
    val id = json.optString("Id")
    val address = json.optString("Address")
    if (id.isEmpty || address.isEmpty) None
    else Some(MediaServer(kind, json.optString("Name", address), id, address.stripSuffix("/")))
  }
  catch { case _: Throwable => None }

  /** `HTTP/1.0 200 OK` then `Name: …`, `Port: 32400`, `Resource-Identifier: …`, answered from the server's address. */
  private def plexAnswer(text: String, from: InetAddress): Option[MediaServer] = {
    val headers = text.split("\r?\n").toList.drop(1).flatMap { line =>
      val colon = line.indexOf(':')
      if (colon <= 0) None else Some(line.substring(0, colon).trim.toLowerCase -> line.substring(colon + 1).trim)
    }.toMap
    headers.get("resource-identifier").filter(_.nonEmpty).map { id =>
      val port = headers.getOrElse("port", "32400")
      MediaServer("plex", headers.getOrElse("name", from.getHostAddress), id,
        "http://" + from.getHostAddress + ":" + port)
    }
  }

  /** A Jellyfin at `host`:8096 by its public system info, which needs no sign-in. */
  private def jellyfinAt(host: String): Option[MediaServer] = try {
    val address = "http://" + host + ":8096"
    val c = new java.net.URL(address + "/System/Info/Public").openConnection().asInstanceOf[java.net.HttpURLConnection]
    c.setConnectTimeout(700)
    c.setReadTimeout(700)
    try {
      val json = new org.json.JSONObject(new String(Host.bytesOf(c.getInputStream), UTF_8))
      val id = json.optString("Id")
      if (id.isEmpty || !json.optString("ProductName").toLowerCase.contains("jellyfin")) None
      else Some(MediaServer("jellyfin", json.optString("ServerName", host), id, address))
    }
    finally c.disconnect()
  }
  catch { case _: Throwable => None }

  private def multicastLock(): Option[WifiManager#MulticastLock] = try {
    val wifi = Host.activity.getApplicationContext.getSystemService(Context.WIFI_SERVICE).asInstanceOf[WifiManager]
    Option(wifi).map { w =>
      val lock = w.createMulticastLock("everyui-media-servers")
      lock.setReferenceCounted(false)
      lock.acquire()
      lock
    }
  }
  // no Wi-Fi, or the app does not ask for CHANGE_WIFI_MULTICAST_STATE: the search goes on without it
  catch { case _: Throwable => None }
}
