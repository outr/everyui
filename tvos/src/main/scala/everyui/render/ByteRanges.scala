package everyui.render

/** Parts of a file fetched by a byte range (a BIF's frames: `everyui.trickplay.TrickPlay`): not here yet, so a BIF's
  * thumbnails are none on an Apple TV, where tile sheets, shown as any picture is, are drawn as everywhere. */
object ByteRanges {
  /** Nothing is fetched: `done` hears None. */
  def read(url: String, headers: Map[String, String], from: Int, length: Int, done: Option[Array[Byte]] => Unit): Unit =
    done(None)

  /** Nothing is fetched: `done` hears None. */
  def picture(url: String, headers: Map[String, String], from: Int, length: Int, done: Option[String] => Unit): Unit =
    done(None)

  /** Nothing is kept. */
  def forget(address: String): Unit = ()
}
