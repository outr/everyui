package everyui.component

/** A file a page in a WebView asked to download (`WebView.onDownload`): its address, the name it is to be saved as,
  * its media type and its size where the answer said it (None where it did not). `save()` saves it as a WebView does
  * when the app does not say otherwise (on Android, into the device's Downloads, with the system's notifications), so
  * an app that only wants to say something, or ask first, can still save it the usual way. */
class WebDownload(val url: String, val name: String, val mime: String, val size: Option[Long], saver: () => Unit) {
  def save(): Unit = saver()

  override def toString: String = s"WebDownload($url, $name, $mime, ${size.fold("?")(_.toString)})"
}

object WebDownload {
  /** Whether a page with this `sandbox` may download at all: unsandboxed, or sandboxed with "allow-downloads", as an
    * iframe's sandbox allows it. */
  def allowed(sandbox: Option[Set[String]]): Boolean = sandbox.forall(_.contains("allow-downloads"))

  /** The name a download is saved as, as a browser chooses it: the answer's Content-Disposition `filename*` (RFC 5987,
    * UTF-8 or Latin-1, percent-encoded) or `filename`, then the page's `download` attribute (`named`), then the last
    * part of the address's path (not of a `blob:` or `data:` one), then "download". An extension is added from `mime`
    * where the name has none (`extension`), never changed; anything that would leave the folder (a path, `..`) or that
    * a file system refuses is taken out. */
  def name(url: String, disposition: String, mime: String, named: String = "",
           extension: String => Option[String] = extensionOf): String = {
    val chosen = List(dispositionName(disposition), clean(named), clean(fromPath(url))).find(_.nonEmpty).getOrElse("download")
    val kind = mime.takeWhile(_ != ';').trim.toLowerCase
    if (chosen.lastIndexOf('.') > 0) chosen
    else extension(kind).filter(_.nonEmpty).fold(chosen)(e => chosen + "." + e)
  }

  /** The extension of the common media types; None for the rest, and for `application/octet-stream`. */
  def extensionOf(mime: String): Option[String] = mime match {
    case "text/plain" => Some("txt")
    case "text/html" => Some("html")
    case "text/csv" => Some("csv")
    case "text/css" => Some("css")
    case "text/calendar" => Some("ics")
    case "application/pdf" => Some("pdf")
    case "application/zip" => Some("zip")
    case "application/json" => Some("json")
    case "application/xml" | "text/xml" => Some("xml")
    case "application/gzip" => Some("gz")
    case "application/vnd.android.package-archive" => Some("apk")
    case "application/x-msi" | "application/x-ms-installer" => Some("msi")
    case "image/png" => Some("png")
    case "image/jpeg" => Some("jpg")
    case "image/gif" => Some("gif")
    case "image/webp" => Some("webp")
    case "image/svg+xml" => Some("svg")
    case "audio/mpeg" => Some("mp3")
    case "video/mp4" => Some("mp4")
    case _ => None
  }

  /** The file name a Content-Disposition gives, cleaned (`name`); empty where it gives none. */
  def dispositionName(disposition: String): String = {
    val params = parameters(disposition)
    val extended = params.get("filename*").flatMap(decoded).map(clean).getOrElse("")
    if (extended.nonEmpty) extended else params.get("filename").map(clean).getOrElse("")
  }

  // a header's `key=value` parameters after its first `;`, a quoted value unquoted (`\"` kept as `"`)
  private def parameters(header: String): Map[String, String] = {
    val out = Map.newBuilder[String, String]
    var i = 0
    val s = if (header == null) "" else header
    while (i < s.length) {
      val semi = s.indexOf(';', i)
      if (semi < 0) i = s.length
      else {
        i = semi + 1
        val eq = s.indexOf('=', i)
        val nextSemi = s.indexOf(';', i)
        if (eq > 0 && (nextSemi < 0 || eq < nextSemi)) {
          val key = s.substring(i, eq).trim.toLowerCase
          var j = eq + 1
          while (j < s.length && s.charAt(j) == ' ') j += 1
          val value = new StringBuilder
          if (j < s.length && s.charAt(j) == '"') {
            j += 1
            while (j < s.length && s.charAt(j) != '"') {
              if (s.charAt(j) == '\\' && j + 1 < s.length) j += 1
              value.append(s.charAt(j))
              j += 1
            }
            i = j + 1
          }
          else {
            val end = if (s.indexOf(';', j) < 0) s.length else s.indexOf(';', j)
            value.append(s.substring(j, end).trim)
            i = end
          }
          out += key -> value.toString
        }
      }
    }
    out.result()
  }

  // `charset'language'percent-encoded`, as RFC 5987 writes it
  private def decoded(value: String): Option[String] = {
    val parts = value.split("'", 3)
    if (parts.length != 3) None
    else {
      val charset = parts(0).trim.toUpperCase
      if (charset != "UTF-8" && charset != "ISO-8859-1") None
      else percentDecoded(parts(2), charset)
    }
  }

  private def percentDecoded(s: String, charset: String): Option[String] = {
    val out = new java.io.ByteArrayOutputStream()
    var i = 0
    var ok = true
    while (i < s.length && ok) {
      val c = s.charAt(i)
      if (c == '%') {
        if (i + 2 < s.length && Character.digit(s.charAt(i + 1), 16) >= 0 &&
          Character.digit(s.charAt(i + 2), 16) >= 0) {
          out.write(Character.digit(s.charAt(i + 1), 16) * 16 + Character.digit(s.charAt(i + 2), 16))
          i += 3
        }
        else ok = false
      }
      else {
        val bytes = c.toString.getBytes(charset)
        out.write(bytes, 0, bytes.length)
        i += 1
      }
    }
    if (ok) Some(new String(out.toByteArray, charset)) else None
  }

  // the address's last path part, percent-decoded as UTF-8; nothing for `blob:` and `data:`
  private def fromPath(url: String): String = {
    val lower = url.toLowerCase
    if (lower.startsWith("blob:") || lower.startsWith("data:")) ""
    else {
      val afterScheme = url.indexOf("://") match {
        case -1 => url
        case at => url.substring(at + 3).dropWhile(_ != '/')
      }
      val path = afterScheme.takeWhile(c => c != '?' && c != '#')
      val last = path.substring(path.lastIndexOf('/') + 1)
      percentDecoded(last, "UTF-8").getOrElse(last)
    }
  }

  /** A name safe to save as: the last part of any path in it, without characters a file system refuses or control
    * characters, without leading dots or spaces or trailing ones, and at most 200 characters (its extension kept). */
  def clean(name: String): String = {
    val base = Option(name).getOrElse("").split("[/\\\\]").lastOption.getOrElse("")
    val kept = base.map(c => if (c < ' ' || c == 0x7f || ":*?\"<>|".indexOf(c) >= 0) '_' else c)
      .dropWhile(c => c == '.' || c == ' ').reverse.dropWhile(c => c == '.' || c == ' ').reverse
    if (kept.length <= 200) kept
    else {
      val dot = kept.lastIndexOf('.')
      val ext = if (dot > 0 && kept.length - dot <= 16) kept.substring(dot) else ""
      kept.take(200 - ext.length) + ext
    }
  }
}
