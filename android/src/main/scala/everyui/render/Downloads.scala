package everyui.render

/** Files fetched once and kept on the device: a module's music and pictures, say, played from here rather than
  * streamed at every showing. Each `space` is a folder of its own under the app's files (not its cache, which
  * Android empties when it wants the room: these are what something needs in order to work, not a convenience).
  *
  * A file arrives beside its final name and takes that name only once all of it is there, so a fetch cut short never
  * leaves half a file where a whole one is expected. The fetching happens on one thread of its own, a file at a time;
  * what is asked for is answered on the thread that draws. */
object Downloads {
  private lazy val worker: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newSingleThreadExecutor(r => {
      val thread = new Thread(r, "everyui-downloads")
      thread.setDaemon(true)
      thread
    })

  /** Whether this platform keeps files at all: where it does not, what would be kept is streamed. */
  def available: Boolean = true

  private def safe(name: String): String =
    name.map(c => if (c.isLetterOrDigit || c == '.' || c == '-' || c == '_') c else '_')

  private def folder(space: String): java.io.File = new java.io.File(Host.activity.getFilesDir, "downloads/" + safe(space))

  private def file(space: String, name: String): java.io.File = new java.io.File(folder(space), safe(name))

  /** Where `name` in `space` is kept, as an address a player or a picture can be given. */
  def address(space: String, name: String): String = file(space, name).toURI.toString

  /** Fetch `url` into `name` in `space`, then `done` with its address, or with nothing where it could not be had. */
  def fetch(url: String, space: String, name: String, done: Option[String] => Unit): Unit = worker.execute(() => {
    val target = file(space, name)
    val part = new java.io.File(target.getPath + ".part")
    val result =
      try {
        target.getParentFile.mkdirs()
        val connection = new java.net.URL(url).openConnection()
        connection.setConnectTimeout(10000)
        connection.setReadTimeout(30000)
        val in = connection.getInputStream
        val out = new java.io.FileOutputStream(part)
        try {
          val buffer = new Array[Byte](64 * 1024)
          var read = in.read(buffer)
          while (read >= 0) {
            out.write(buffer, 0, read)
            read = in.read(buffer)
          }
        } finally {
          in.close()
          out.close()
        }
        if (target.exists()) target.delete()
        if (part.renameTo(target)) Some(target.toURI.toString) else None
      } catch {
        case t: Throwable =>
          Platform.logError("Downloading " + url, t)
          part.delete()
          None
      }
    Platform.later(() => done(result))
  })

  /** What `space` holds: each file's name and size in bytes. */
  def list(space: String, got: List[(String, Long)] => Unit): Unit = worker.execute(() => {
    val files = Option(folder(space).listFiles()).map(_.toList).getOrElse(Nil)
      .filter(f => f.isFile && !f.getName.endsWith(".part")).map(f => (f.getName, f.length()))
    Platform.later(() => got(files))
  })

  /** Everything `space` holds, gone. */
  def drop(space: String, done: () => Unit): Unit = worker.execute(() => {
    Option(folder(space).listFiles()).foreach(_.foreach(_.delete()))
    folder(space).delete()
    Platform.later(done)
  })
}
