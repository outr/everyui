package everyui.tray

import java.nio.charset.StandardCharsets
import java.nio.{ByteBuffer, ByteOrder}

/** Values read from a D-Bus message (`bytes`, in its own byte order), each aligned from the message's start, as
  * `DBusWriter` writes them: an array as a `Vector` (of pairs, for a dictionary), a struct as a `DBusStruct`, a variant
  * as a `DBusVariant`, `o` as a `DBusPath`, `g` as a `DBusSignature`, `u` as an Int (its bits), `t` as a Long. */
class DBusReader(bytes: Array[Byte], littleEndian: Boolean) {
  private val buffer = ByteBuffer.wrap(bytes).order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

  def at: Int = buffer.position()
  def seek(to: Int): Unit = buffer.position(to)
  def align(to: Int): Unit = while (buffer.position() % to != 0) buffer.position(buffer.position() + 1)

  def byte(): Int = buffer.get() & 0xff
  def int32(): Int = {
    align(4)
    buffer.getInt()
  }
  def string(): String = {
    val n = int32()
    val s = new String(bytes, buffer.position(), n, StandardCharsets.UTF_8)
    buffer.position(buffer.position() + n + 1)
    s
  }
  def signature(): String = {
    val n = byte()
    val s = new String(bytes, buffer.position(), n, StandardCharsets.UTF_8)
    buffer.position(buffer.position() + n + 1)
    s
  }

  /** The values of `signature`, in order. */
  def values(signature: String): List[Any] = DBusWriter.types(signature).map(value)

  def value(t: String): Any = t.charAt(0) match {
    case 'y' => byte().toByte
    case 'b' => int32() != 0
    case 'n' =>
      align(2)
      buffer.getShort().toInt
    case 'q' =>
      align(2)
      buffer.getShort() & 0xffff
    case 'i' | 'u' | 'h' => int32()
    case 'x' | 't' =>
      align(8)
      buffer.getLong()
    case 'd' =>
      align(8)
      buffer.getDouble()
    case 's' => string()
    case 'o' => DBusPath(string())
    case 'g' => DBusSignature(signature())
    case 'v' =>
      val s = signature()
      DBusVariant(s, value(s))
    case '(' =>
      align(8)
      DBusStruct(values(t.substring(1, t.length - 1)))
    case 'a' =>
      val element = t.substring(1)
      val length = int32()
      align(DBusWriter.alignment(element))
      val end = buffer.position() + length
      var items = Vector.empty[Any]
      if (element.startsWith("{")) {
        val kv = DBusWriter.types(element.substring(1, element.length - 1))
        while (buffer.position() < end) {
          align(8)
          val k = value(kv.head)
          items = items :+ (k, value(kv(1)))
        }
      }
      else while (buffer.position() < end) items = items :+ value(element)
      buffer.position(end)
      items
    case other => throw new IllegalArgumentException("D-Bus type " + other + " is not read here")
  }
}
