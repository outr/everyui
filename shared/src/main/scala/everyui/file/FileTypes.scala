package everyui.file

/** Files' types, by the web's words for them: a type ("image/png"), a family ("image/\*") or an extension (".txt"), as
  * an `accept` list names them. For a platform that says nothing of a file's type, and for asking whether one is
  * among those a picker or a drop takes. */
object FileTypes {
  private val known: Map[String, String] = Map(
    "png" -> "image/png", "jpg" -> "image/jpeg", "jpeg" -> "image/jpeg", "gif" -> "image/gif", "webp" -> "image/webp",
    "bmp" -> "image/bmp", "svg" -> "image/svg+xml", "heic" -> "image/heic", "avif" -> "image/avif",
    "txt" -> "text/plain", "log" -> "text/plain", "md" -> "text/markdown", "csv" -> "text/csv", "tsv" -> "text/tab-separated-values",
    "html" -> "text/html", "htm" -> "text/html", "css" -> "text/css", "xml" -> "application/xml", "json" -> "application/json",
    "yaml" -> "application/yaml", "yml" -> "application/yaml", "js" -> "text/javascript", "scala" -> "text/x-scala",
    "pdf" -> "application/pdf", "zip" -> "application/zip", "gz" -> "application/gzip",
    "mp3" -> "audio/mpeg", "wav" -> "audio/wav", "ogg" -> "audio/ogg", "mp4" -> "video/mp4", "webm" -> "video/webm",
    "mov" -> "video/quicktime")

  /** The extension a file of type `mime` is named with ("png" for "image/png"; its parameters, `; charset=…`, aside),
    * "bin" for a type not known here. */
  def extensionOf(mime: String): String = {
    val bare = mime.takeWhile(_ != ';').trim.toLowerCase
    if (bare == "text/plain") "txt"
    else if (bare == "text/html") "html"
    else known.toList.filter(_._2 == bare).map(_._1).sortBy(e => (e.length, e)).headOption.getOrElse("bin")
  }

  /** What comes after the name's last dot, in lower case; empty where there is none. */
  def extension(name: String): String = {
    val dot = name.lastIndexOf(".")
    if (dot < 0 || dot == name.length - 1) "" else name.substring(dot + 1).toLowerCase
  }

  /** The type a file of this name is taken to be: by its extension, or "application/octet-stream". */
  def of(name: String): String = known.getOrElse(extension(name), "application/octet-stream")

  /** Whether a file of this name and type is among `types` (every file where `types` is empty). */
  def accepts(types: List[String], name: String, mime: String): Boolean =
    types.isEmpty || types.exists(t => matches(t.toLowerCase, name.toLowerCase, mime.toLowerCase))

  private def matches(t: String, name: String, mime: String): Boolean =
    if (t.startsWith(".")) name.endsWith(t)
    else if (t.endsWith("/*")) mime.startsWith(t.substring(0, t.length - 1))
    else mime == t

  /** The extensions `types` covers, as far as they are known here (without the dot), for a picker that filters by
    * name: ".txt" is "txt", "image/\*" every picture's. */
  def extensions(types: List[String]): List[String] = types.flatMap { t =>
    val lower = t.toLowerCase
    if (lower.startsWith(".")) List(lower.substring(1))
    else known.toList.filter(k => matches(lower, "", k._2)).map(_._1).sorted
  }.distinct
}
