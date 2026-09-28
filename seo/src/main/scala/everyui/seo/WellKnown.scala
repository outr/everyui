package everyui.seo

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

/** The server's side of an app's web domains (the build's `everyui.sbt.AppDomains`): the `/.well-known/` files it
  * wrote next to the web bundle, answered as the platforms want them. Android's assetlinks.json, Apple's
  * apple-app-site-association (JSON, at that exact address, never a redirect) and passkey-endpoints are JSON;
  * change-password redirects to the site's page for it, which password managers send a user to.
  *
  * {{{
  *   WellKnown.answer(webRoot, request.path) match {
  *     case Some(a) if a.location.nonEmpty => redirect(a.location)
  *     case Some(a) => respond(a.status, a.contentType, a.body)
  *     case None => next
  *   }
  * }}} */
object WellKnown {
  /** The files it answers, by name. */
  val names: Set[String] = Set("assetlinks.json", "apple-app-site-association", "passkey-endpoints", "change-password")

  /** The reply for `path` ("/.well-known/assetlinks.json") from `root`/.well-known/, if it is one of these and the
    * build wrote it. */
  def answer(root: Path, path: String): Option[WellKnownAnswer] = {
    val name = path.stripPrefix("/.well-known/")
    if (!path.startsWith("/.well-known/") || !names.contains(name)) None
    else {
      val file = root.resolve(".well-known").resolve(name)
      if (!Files.isRegularFile(file)) None
      else {
        val content = new String(Files.readAllBytes(file), UTF_8)
        if (name == "change-password") Some(WellKnownAnswer(302, "text/plain", "", content.trim))
        else Some(WellKnownAnswer(200, "application/json", content))
      }
    }
  }
}
