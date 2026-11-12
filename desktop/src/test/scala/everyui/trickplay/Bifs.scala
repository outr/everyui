package everyui.trickplay

/** BIF files written for the specs: a header, an index and each frame's bytes as given, as Roku's format has them. */
object Bifs {
  /** A BIF of `frames`, one each `unitMs` × its timestamp, the timestamps `times` (0, 1, 2… unless given), the
    * header's interval `interval` (0 means 1000 ms). */
  def apply(frames: Seq[Array[Byte]], interval: Int = 1000, times: Option[Seq[Int]] = None): Array[Byte] = {
    val out = new java.io.ByteArrayOutputStream()
    def word(v: Long): Unit = (0 until 4).foreach(i => out.write(((v >> (8 * i)) & 0xff).toInt))
    out.write(Array(0x89, 0x42, 0x49, 0x46, 0x0d, 0x0a, 0x1a, 0x0a).map(_.toByte))
    word(0)
    word(frames.size.toLong)
    word(interval.toLong)
    out.write(new Array[Byte](44))
    val stamps = times.getOrElse(frames.indices)
    var offset = 64 + 8 * (frames.size + 1)
    frames.zip(stamps).foreach { (f, t) =>
      word(t.toLong)
      word(offset.toLong)
      offset += f.length
    }
    word(0xffffffffL)
    word(offset.toLong)
    frames.foreach(f => out.write(f))
    out.toByteArray
  }

  /** Frame `i`'s bytes, told apart from every other's. */
  def frame(i: Int): Array[Byte] = ("frame " + i.toString + " " + ("x" * (i % 7))).getBytes("UTF-8")
}
