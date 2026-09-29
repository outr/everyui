package everyui.network

/** multipart/form-data (RFC 7578), written as a browser writes a form: each part under the boundary, with its
  * Content-Disposition (its name, and a file's name) and a file's Content-Type, then its bytes, each line ending in
  * CRLF. A name or file name has its `"`, CR and LF escaped as `%22`, `%0D` and `%0A`, as the HTML standard has it, and
  * is otherwise sent as its UTF-8. Every backend but the web sends this; the web gives the browser a FormData to write. */
object Multipart {
  private val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

  /** A boundary of its own: a fixed start and 24 random letters and digits, which no part is taken to contain. */
  def boundary(): String = {
    var out = "----EveryUIFormBoundary"
    var i = 0
    while (i < 24) {
      val at = (math.random() * letters.length).toInt % letters.length
      out = out + letters.substring(at, at + 1)
      i += 1
    }
    out
  }

  /** The request's Content-Type for a body under `boundary`. */
  def contentType(boundary: String): String = "multipart/form-data; boundary=" + boundary

  /** A name as it goes in a Content-Disposition's quotes. */
  def escape(name: String): String = name.replace("\"", "%22").replace("\r", "%0D").replace("\n", "%0A")

  /** `parts` as the body's bytes, under `boundary`. */
  def encode(parts: List[FormPart], boundary: String): Array[Byte] = {
    var chunks: List[Array[Byte]] = Nil
    parts.foreach { part =>
      val head = part match {
        case FormPart.FieldPart(name, _) =>
          "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + escape(name) + "\"\r\n\r\n"
        case FormPart.FilePart(name, fileName, _, contentType) =>
          "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + escape(name) + "\"; filename=\"" +
            escape(fileName) + "\"\r\nContent-Type: " + contentType + "\r\n\r\n"
      }
      val body = part match {
        case FormPart.FieldPart(_, value) => value.getBytes("UTF-8")
        case FormPart.FilePart(_, _, bytes, _) => bytes
      }
      chunks = "\r\n".getBytes("UTF-8") :: body :: head.getBytes("UTF-8") :: chunks
    }
    chunks = ("--" + boundary + "--\r\n").getBytes("UTF-8") :: chunks
    joined(chunks.reverse)
  }

  private def joined(chunks: List[Array[Byte]]): Array[Byte] = {
    var total = 0
    chunks.foreach(c => total += c.length)
    val out = new Array[Byte](total)
    var at = 0
    chunks.foreach { c =>
      var i = 0
      while (i < c.length) {
        out(at) = c(i)
        at += 1
        i += 1
      }
    }
    out
  }
}
