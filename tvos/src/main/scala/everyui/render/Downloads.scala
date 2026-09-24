package everyui.render

/** Files fetched once and kept on the device (see the Android backend's). This platform keeps none: what would be
  * kept is streamed each time instead, so every call answers at once that nothing was kept. */
object Downloads {
  /** Whether this platform keeps files at all: where it does not, what would be kept is streamed. */
  def available: Boolean = false

  /** Where `name` in `space` would be kept: nowhere. */
  def address(space: String, name: String): String = ""

  /** Nothing is fetched: `done` hears so. */
  def fetch(url: String, space: String, name: String, done: Option[String] => Unit): Unit = done(None)

  /** What `space` holds: nothing. */
  def list(space: String, got: List[(String, Long)] => Unit): Unit = got(Nil)

  /** Nothing to drop. */
  def drop(space: String, done: () => Unit): Unit = done()
}
