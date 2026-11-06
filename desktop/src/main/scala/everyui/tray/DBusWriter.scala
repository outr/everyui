package everyui.tray

import java.nio.charset.StandardCharsets

/** Values written in D-Bus's wire format, little-endian, each aligned from the start of the message as its type asks.
  * A value is what its signature says: `y` a Byte, `b` a Boolean, `n` and `q` an Int, `i` and `u` an Int, `x` and `t`
  * a Long, `d` a Double, `s` a String, `o` a `DBusPath` (or a String), `g` a `DBusSignature` (or a String), an array a
  * `Seq` (of pairs, or a `Map`, for a dictionary), a struct a `DBusStruct`, a variant a `DBusVariant`. */
class DBusWriter {
  // what is written so far: the first `size` bytes of `held`, grown as it fills
  private var held = new Array[Byte](256)
  private var count = 0
  private def put(b: Int): Unit = {
    if (count == held.length) held = java.util.Arrays.copyOf(held, held.length * 2)
    held(count) = b.toByte
    count += 1
  }
  private def putAll(bs: Array[Byte]): Unit = bs.foreach(b => put(b.toInt))

  def size: Int = count
  def bytes: Array[Byte] = java.util.Arrays.copyOf(held, count)

  def pad(to: Int): Unit = while (count % to != 0) put(0)

  def byte(v: Int): Unit = put(v & 0xff)

  def int16(v: Int): Unit = {
    pad(2)
    put(v & 0xff)
    put((v >> 8) & 0xff)
  }

  def int32(v: Int): Unit = {
    pad(4)
    put(v & 0xff)
    put((v >> 8) & 0xff)
    put((v >> 16) & 0xff)
    put((v >>> 24) & 0xff)
  }

  def int64(v: Long): Unit = {
    pad(8)
    var i = 0
    while (i < 8) {
      put(((v >>> (8 * i)) & 0xff).toInt)
      i += 1
    }
  }

  def string(s: String): Unit = {
    val b = s.getBytes(StandardCharsets.UTF_8)
    int32(b.length)
    putAll(b)
    put(0)
  }

  def signature(s: String): Unit = {
    val b = s.getBytes(StandardCharsets.UTF_8)
    put(b.length)
    putAll(b)
    put(0)
  }

  /** The values of `signature`, one complete type each, in order. */
  def values(signature: String, values: List[Any]): Unit =
    DBusWriter.types(signature).zip(values).foreach { case (t, v) => value(t, v) }

  /** One value of complete type `t`. */
  def value(t: String, v: Any): Unit = t.charAt(0) match {
    case 'y' => byte(v.asInstanceOf[Number].intValue)
    case 'b' => int32(if (v.asInstanceOf[Boolean]) 1 else 0)
    case 'n' | 'q' => int16(v.asInstanceOf[Number].intValue)
    case 'i' | 'u' | 'h' => int32(v.asInstanceOf[Number].intValue)
    case 'x' | 't' => int64(v.asInstanceOf[Number].longValue)
    case 'd' => int64(java.lang.Double.doubleToLongBits(v.asInstanceOf[Number].doubleValue))
    case 's' => string(v.toString)
    case 'o' => string(v match {
      case p: DBusPath => p.path
      case other => other.toString
    })
    case 'g' => signature(v match {
      case g: DBusSignature => g.text
      case other => other.toString
    })
    case 'v' =>
      val variant = v.asInstanceOf[DBusVariant]
      signature(variant.signature)
      value(variant.signature, variant.value)
    case '(' =>
      pad(8)
      val fields = v match {
        case s: DBusStruct => s.fields
        case p: Product => p.productIterator.toList
        case other => List(other)
      }
      values(t.substring(1, t.length - 1), fields)
    case 'a' =>
      val element = t.substring(1)
      int32(0)
      val lengthAt = count - 4
      pad(DBusWriter.alignment(element))
      val from = count
      val items: Iterable[Any] = v match {
        case m: Map[?, ?] => m.toList
        case s: Iterable[?] => s
        case a: Array[Byte] => a.toList
        case other => List(other)
      }
      if (element.startsWith("{")) {
        val kv = DBusWriter.types(element.substring(1, element.length - 1))
        items.foreach { item =>
          pad(8)
          val (k, x) = item.asInstanceOf[(Any, Any)]
          value(kv.head, k)
          value(kv(1), x)
        }
      }
      else items.foreach(item => value(element, item))
      patch(lengthAt, count - from)
    case other => throw new IllegalArgumentException("D-Bus type " + other + " is not written here")
  }

  // the length written at `at` once it is known
  private def patch(at: Int, length: Int): Unit = {
    held(at) = (length & 0xff).toByte
    held(at + 1) = ((length >> 8) & 0xff).toByte
    held(at + 2) = ((length >> 16) & 0xff).toByte
    held(at + 3) = ((length >>> 24) & 0xff).toByte
  }
}

object DBusWriter {
  /** The complete types `signature` is made of, in order ("sa{sv}as" is "s", "a{sv}", "as"). */
  def types(signature: String): List[String] = {
    var out = List.empty[String]
    var i = 0
    while (i < signature.length) {
      val end = next(signature, i)
      out = signature.substring(i, end) :: out
      i = end
    }
    out.reverse
  }

  // where the complete type starting at `i` ends
  private def next(s: String, i: Int): Int = s.charAt(i) match {
    case 'a' => next(s, i + 1)
    case '(' | '{' =>
      val close = if (s.charAt(i) == '(') ')' else '}'
      var depth = 0
      var j = i
      var end = -1
      while (end < 0) {
        val c = s.charAt(j)
        if (c == '(' || c == '{') depth += 1
        else if (c == ')' || c == '}') {
          depth -= 1
          if (depth == 0 && c == close) end = j + 1
        }
        j += 1
      }
      end
    case _ => i + 1
  }

  /** How a value of type `t` is aligned. */
  def alignment(t: String): Int = t.charAt(0) match {
    case 'y' | 'g' | 'v' => 1
    case 'n' | 'q' => 2
    case 'x' | 't' | 'd' | '(' | '{' => 8
    case _ => 4
  }
}
