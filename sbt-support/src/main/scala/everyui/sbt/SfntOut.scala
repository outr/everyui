package everyui.sbt

import java.io.{ByteArrayOutputStream, DataOutputStream}

/** A font table or file written big-endian, as the sfnt tables are. */
final class SfntOut {
  private val buffer = new ByteArrayOutputStream()
  private val data = new DataOutputStream(buffer)
  def u8(v: Int): Unit = data.writeByte(v)
  def u16(v: Int): Unit = data.writeShort(v)
  def s16(v: Int): Unit = data.writeShort(v)
  def u32(v: Long): Unit = data.writeInt(v.toInt)
  def raw(b: Array[Byte]): Unit = data.write(b)
  def pad(): Unit = while (buffer.size % 4 != 0) data.writeByte(0)
  def size: Int = buffer.size
  def bytes: Array[Byte] = { data.flush(); buffer.toByteArray }
}
