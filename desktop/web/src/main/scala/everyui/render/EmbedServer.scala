package everyui.render

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import everyui.component.{EmbedGuest, EmbedSite}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/** Serves the web guests' files to their web views on the desktop (FxWebGuest): the JDK's own HTTP server, on the
  * loopback address alone, at a port the system picks and under a path no other program can guess.
  *
  * A guest needs a real origin: from a `file:` or `jar:` page its workers, made from blobs, cannot load their scripts.
  * Each site is served under its own name from the classpath, `site.root` under it, with the guest's end of the
  * channel (EmbedGuest) at `everyui-embed.js` beside it; everything but the page itself as immutable, since a
  * versioned root never changes under its name. One server for every guest, so every web view shares what the engine
  * keeps of the files. */
object EmbedServer {
  private val sites = new java.util.concurrent.ConcurrentHashMap[String, EmbedSite]()
  private val token = java.util.UUID.randomUUID().toString.replace("-", "")

  private lazy val server: HttpServer = {
    val s = HttpServer.create(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress, 0), 0)
    s.createContext("/" + token + "/", (e: HttpExchange) => serve(e))
    val pool = java.util.concurrent.Executors.newFixedThreadPool(4, (r: Runnable) => {
      val t = new Thread(r, "everyui-embed-server")
      t.setDaemon(true)
      t
    })
    s.setExecutor(pool)
    s.start()
    s
  }

  /** Where `site`'s files are served: its root, ending in `/`. */
  def url(site: EmbedSite): String = {
    sites.put(site.name, site)
    "http://127.0.0.1:" + server.getAddress.getPort.toString + "/" + token + "/" + site.name + "/"
  }

  private def serve(e: HttpExchange): Unit = try {
    val parts = e.getRequestURI.getPath.stripPrefix("/" + token + "/").split("/", 2)
    val site = Option(sites.get(parts(0)))
    val path = if (parts.length > 1) parts(1) else ""
    val page = site.exists(s => path == s.page.stripPrefix("/"))
    val bytes: Option[Array[Byte]] =
      if (site.isEmpty || path.contains("..")) None
      else if (path == "everyui-embed.js") Some(EmbedGuest.script.getBytes(StandardCharsets.UTF_8))
      else Option(getClass.getClassLoader.getResourceAsStream(site.get.resolve(path))).map { in =>
        try in.readAllBytes() finally in.close()
      }
    bytes match {
      case Some(body) =>
        e.getResponseHeaders.set("Content-Type", mime(path))
        e.getResponseHeaders.set("Cache-Control",
          if (page || path == "everyui-embed.js") "no-cache" else "public, max-age=31536000, immutable")
        e.sendResponseHeaders(200, body.length.toLong)
        e.getResponseBody.write(body)
      case None =>
        e.sendResponseHeaders(404, -1L)
    }
  }
  finally e.close()

  private def mime(path: String): String = {
    val dot = path.lastIndexOf('.')
    val ext = if (dot < 0) "" else path.substring(dot + 1).toLowerCase
    if (ext == "js" || ext == "mjs") "text/javascript; charset=utf-8"
    else if (ext == "css") "text/css; charset=utf-8"
    else if (ext == "html" || ext == "htm") "text/html; charset=utf-8"
    else if (ext == "json" || ext == "map") "application/json"
    else if (ext == "svg") "image/svg+xml"
    else if (ext == "png") "image/png"
    else if (ext == "ttf") "font/ttf"
    else if (ext == "woff2") "font/woff2"
    else "application/octet-stream"
  }
}
