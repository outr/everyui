package universal.server

import rapid.Task
import scribe.mdc.MDC
import spice.http.HttpExchange
import spice.http.content.Content
import spice.http.server.dsl.{ConnectionFilter, FilterResponse}
import spice.http.server.handler.SenderHandler

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
  */
case class DirectoryPath(root: File, index: String = "index.html") extends ConnectionFilter:
  private val canonicalRoot: String = root.getCanonicalPath

  override def apply(exchange: HttpExchange)(using mdc: MDC): Task[FilterResponse] =
    resolve(exchange.request.url.path.decoded) match
      case Some(file) =>
        SenderHandler(Content.file(file), replace = true)
          .handle(exchange)
          .map(e => FilterResponse.Continue(e))
      case None => Task.pure(FilterResponse.Stop(exchange))

  private def resolve(path: String): Option[File] =
    val relative = path.dropWhile(_ == '/')
    val target = if relative.isEmpty then File(root, index) else File(root, relative)
    val resolved = if target.isDirectory then File(target, index) else target
    val canonical = resolved.getCanonicalPath
    // Contained, and actually a readable file.
    val contained = canonical == canonicalRoot || canonical.startsWith(canonicalRoot + File.separator)
    if contained && resolved.isFile then Some(resolved) else None
