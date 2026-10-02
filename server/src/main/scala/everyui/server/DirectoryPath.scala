package everyui.server

import rapid.Task
import scribe.mdc.MDC
import spice.http.{Headers, HttpExchange}
import spice.http.content.Content
import spice.http.server.dsl.{ConnectionFilter, FilterResponse}
import spice.http.server.handler.SenderHandler
import spice.net.ContentType

import java.io.File

/** Serves files from a DIRECTORY ON DISK.
  *
  * Spice ships `ClassLoaderPath`, which serves from the classpath — right for a
  * packaged app, wrong here: `web/out-js/main.js` is rewritten by every
  * scala-cli build, and a classpath copy would be whatever was there when the
  * JVM started. This reads through to the file each request, so a rebuild shows
  * up on the next reload with no server restart.
  *
  * `index` names the file a directory request resolves to. Paths are resolved
  * against the root's canonical path and rejected if they escape it — the root
  * is a build output directory, and a request for `/../../../etc/passwd` should
  * not be able to walk out of it.
  *
  * A file compressed beside itself (`main.js.br`, `main.js.gz`: sbt-everyui's
  * WebBundle.precompress) is sent in its place to a browser that takes it, Brotli
  * first, with `Content-Encoding` saying so and the file's own type.
  */
case class DirectoryPath(root: File, index: String = "index.html") extends ConnectionFilter {
  private val canonicalRoot: String = root.getCanonicalPath

  override def apply(exchange: HttpExchange)(using mdc: MDC): Task[FilterResponse] =
    resolve(exchange.request.url.path.decoded) match {
      case Some(file) =>
        // every value, however the header was split into them
        val accepted = exchange.request.headers.get(Headers.Request.`Accept-Encoding`).flatMap(_.split(","))
          .map(_.trim.takeWhile(_ != ';').toLowerCase).toSet
        val encoded = DirectoryPath.encodings.collectFirst {
          case (name, suffix) if accepted(name) && File(file.getPath + suffix).isFile => (name, File(file.getPath + suffix))
        }
        val content = Content.file(encoded.fold(file)(_._2), DirectoryPath.contentType(file))
        SenderHandler(content, replace = true)
          .handle(exchange)
          .flatMap(_.modify(r => Task(encoded.fold(r)(e => r.setHeader("Content-Encoding", e._1))
            .setHeader("Vary", "Accept-Encoding"))))
          .map(e => FilterResponse.Continue(e))
      case None => Task.pure(FilterResponse.Stop(exchange))
    }

  private def resolve(path: String): Option[File] = {
    val relative = path.dropWhile(_ == '/')
    val target = if (relative.isEmpty) File(root, index) else File(root, relative)
    val resolved = if (target.isDirectory) File(target, index) else target
    val canonical = resolved.getCanonicalPath
    // Contained, and actually a readable file.
    val contained = canonical == canonicalRoot || canonical.startsWith(canonicalRoot + File.separator)
    if (contained && resolved.isFile) Some(resolved) else None
  }
}

object DirectoryPath {
  /** The encodings a file may be found compressed in beside itself, the first a browser takes chosen. */
  val encodings: Seq[(String, String)] = Seq("br" -> ".br", "gzip" -> ".gz")

  /** A file's type by its name, with the faces' and translations' that Spice does not name. */
  def contentType(file: File): ContentType = file.getName.substring(file.getName.lastIndexOf('.') + 1).toLowerCase match {
    case "woff2" => ContentType.parse("font/woff2")
    case "arb" => ContentType.`application/json`
    case _ => ContentType.byFileName(file.getName)
  }
}
