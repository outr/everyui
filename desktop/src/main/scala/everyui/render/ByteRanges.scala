package everyui.render

/** Parts of a file fetched by a byte range (a BIF's index, and each of its frames: `everyui.trickplay.TrickPlay`): a
  * `Range` request on a thread of its own, answered on the FX thread. A frame is shown by a `data:` address holding its
  * JPEG, which a picture reads as any other, so there is nothing to let go when it is forgotten. */
object ByteRanges {
  private lazy val worker: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newFixedThreadPool(2, r => {
      val thread = new Thread(r, "everyui-ranges")
      thread.setDaemon(true)
      thread
    })

  /** `length` bytes of `url` from `from`, `headers` with the request: Some once a 206 brings them (or a 200 from the
    * start, cut to the length asked), else None. */
  def read(url: String, headers: Map[String, String], from: Int, length: Int, done: Option[Array[Byte]] => Unit): Unit =
    worker.execute(() => {
      val got = fetch(url, headers, from, length).collect {
        case (status, bytes) if status == 206 || (status == 200 && from == 0) => bytes
      }
      Platform.later(() => done(got))
    })

  /** The same range as a picture: a `data:` address of a 206 of exactly that length, or None. */
  def picture(url: String, headers: Map[String, String], from: Int, length: Int, done: Option[String] => Unit): Unit =
    worker.execute(() => {
      val got = fetch(url, headers, from, length).collect {
        case (206, bytes) if bytes.length == length =>
          everyui.image.DataAddress.of("image/jpeg", bytes)
      }
      Platform.later(() => done(got))
    })

  /** Nothing is kept for a picture but its address. */
  def forget(address: String): Unit = ()

  // the status and no more than `length` bytes of the answer; None where none came
  private def fetch(url: String, headers: Map[String, String], from: Int,
                    length: Int): Option[(Int, Array[Byte])] =
    try {
      val connection = new java.net.URI(url).toURL.openConnection().asInstanceOf[java.net.HttpURLConnection]
      connection.setConnectTimeout(10000)
      connection.setReadTimeout(20000)
      headers.foreach(h => connection.setRequestProperty(h._1, h._2))
      connection.setRequestProperty("Range", "bytes=" + from.toString + "-" + (from + length - 1).toString)
      // the bytes as they are in the file, never compressed on the way (a range of a compressed answer is not
      // the file's)
      connection.setRequestProperty("Accept-Encoding", "identity")
      val status = connection.getResponseCode
      if (status != 200 && status != 206) {
        connection.disconnect()
        None
      } else {
        val in = connection.getInputStream
        try Some((status, in.readNBytes(length))) finally in.close()
      }
    } catch {
      case _: java.io.IOException | _: IllegalArgumentException | _: java.net.URISyntaxException => None
    }
}
