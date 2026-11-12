package everyui.render

import everyui.roku.*

/** Parts of a file fetched by a byte range (a BIF's index, and each of its frames: `everyui.trickplay.TrickPlay`): a
  * `Range` request through the runtime's HTTP workers, so nothing waits on the render thread. A frame goes to a file of
  * its own in tmp:, which a Poster shows, and is taken away when it is forgotten. */
object ByteRanges {
  /** `length` bytes of `url` from `from`, `headers` with the request: Some once a 206 brings them (or a 200 from the
    * start, no longer than asked), else None. Each byte as the box's byte array has it, 0 to 255. */
  def read(url: String, headers: Map[String, String], from: Int, length: Int, done: Option[Array[Byte]] => Unit): Unit =
    uiByteRange(url, said(headers), from, length, (status: Int, bytes: Array[Byte]) =>
      done(if ((status == 206 || (status == 200 && from == 0)) && bytes.length > 0) Some(bytes) else None))

  /** The same range as a picture: the address of a file holding it (tmp:/…), or None. */
  def picture(url: String, headers: Map[String, String], from: Int, length: Int, done: Option[String] => Unit): Unit =
    uiRangeFile(url, said(headers), from, length, (status: Int, path: String) =>
      done(if (path.nonEmpty) Some(path) else None))

  /** A picture from `picture` no longer wanted: its file taken away. */
  def forget(address: String): Unit = if (address.startsWith("tmp:/")) uiTmpDelete(address)

  private def said(headers: Map[String, String]): List[String] = headers.toList.map(h => h._1 + ":" + h._2)
}
