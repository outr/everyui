package everyui.terminal

import java.nio.charset.StandardCharsets

/** UTF-8 read a piece at a time, as a socket delivers a shell's output: a character split across two pieces is held
  * back until the rest of it arrives, rather than coming out as two replacement characters. */
class Utf8Stream {
  private var held: Array[Byte] = Array.emptyByteArray

  /** The text `bytes` completes, with whatever was held back before them. */
  def decode(bytes: Array[Byte]): String = {
    val all = if (held.isEmpty) bytes else held ++ bytes
    // a lead byte whose sequence runs past the end is held, with what follows it
    var end = all.length
    var i = all.length - 1
    var back = 0
    while (i >= 0 && back < 3 && (all(i) & 0xc0) == 0x80) {
      i -= 1
      back += 1
    }
    if (i >= 0) {
      val lead = all(i) & 0xff
      val need = if (lead >= 0xf0) 4 else if (lead >= 0xe0) 3 else if (lead >= 0xc0) 2 else 1
      if (all.length - i < need) end = i
    }
    held = java.util.Arrays.copyOfRange(all, end, all.length)
    new String(all, 0, end, StandardCharsets.UTF_8)
  }

  /** What is held back, as replacement characters: the stream has ended. */
  def flush(): String = {
    val rest = new String(held, StandardCharsets.UTF_8)
    held = Array.emptyByteArray
    rest
  }
}
