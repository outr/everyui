package everyui.tray

/** Whole D-Bus messages to bytes and back: the fixed header, the header's fields (each a code and a variant), the
  * header padded to 8, then the body. */
object DBusWire {
  /** `m` as bytes, little-endian. */
  def encode(m: DBusMessage): Array[Byte] = {
    val body = new DBusWriter
    body.values(m.signature, m.body)
    val head = new DBusWriter
    head.byte('l')
    head.byte(m.kind)
    head.byte(if (m.noReply) 1 else 0)
    head.byte(1)
    head.int32(body.size)
    head.int32(m.serial)
    var fields = List.empty[DBusStruct]
    def field(code: Int, sig: String, v: Any): Unit =
      fields = DBusStruct(List(code.toByte, DBusVariant(sig, v))) :: fields
    if (m.path.nonEmpty) field(1, "o", DBusPath(m.path))
    if (m.interface.nonEmpty) field(2, "s", m.interface)
    if (m.member.nonEmpty) field(3, "s", m.member)
    if (m.errorName.nonEmpty) field(4, "s", m.errorName)
    if (m.replySerial != 0) field(5, "u", m.replySerial)
    if (m.destination.nonEmpty) field(6, "s", m.destination)
    if (m.signature.nonEmpty) field(8, "g", DBusSignature(m.signature))
    head.value("a(yv)", fields.reverse)
    head.pad(8)
    // the body's alignment counts from the message's start, which a header padded to 8 leaves unchanged
    val h = head.bytes
    val b = body.bytes
    val all = new Array[Byte](h.length + b.length)
    System.arraycopy(h, 0, all, 0, h.length)
    System.arraycopy(b, 0, all, h.length, b.length)
    all
  }

  /** How long the whole message is, from its first 16 bytes. */
  def length(first16: Array[Byte]): Int = {
    val r = new DBusReader(first16, first16(0) == 'l'.toByte)
    r.seek(4)
    val body = r.int32()
    r.seek(12)
    val fields = r.int32()
    val head = 16 + fields
    val padded = head + ((8 - head % 8) % 8)
    padded + body
  }

  /** The message in `bytes`. */
  def decode(bytes: Array[Byte]): DBusMessage = {
    val r = new DBusReader(bytes, bytes(0) == 'l'.toByte)
    r.seek(1)
    val kind = r.byte()
    val flags = r.byte()
    r.seek(8)
    val serial = r.int32()
    val fields = r.value("a(yv)").asInstanceOf[Vector[DBusStruct]]
    r.align(8)
    var m = DBusMessage(kind, serial, noReply = (flags & 1) != 0)
    fields.foreach { f =>
      val code = f.fields.head.asInstanceOf[Byte].toInt
      val v = f.fields(1).asInstanceOf[DBusVariant].value
      code match {
        case 1 => m = m.copy(path = v.asInstanceOf[DBusPath].path)
        case 2 => m = m.copy(interface = v.toString)
        case 3 => m = m.copy(member = v.toString)
        case 4 => m = m.copy(errorName = v.toString)
        case 5 => m = m.copy(replySerial = v.asInstanceOf[Int])
        case 6 => m = m.copy(destination = v.toString)
        case 7 => m = m.copy(sender = v.toString)
        case 8 => m = m.copy(signature = v.asInstanceOf[DBusSignature].text)
        case _ => ()
      }
    }
    m.copy(body = if (m.signature.isEmpty) Nil else r.values(m.signature))
  }
}
