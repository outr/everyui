package everyui.store

/** How a value of `T` is kept as a document in the [[Store]]: written as text, and read back from it.
  *
  * Reading answers None for text it cannot make sense of — a document a person edited, or one an older version of
  * the app wrote in a shape this one no longer reads — and whoever asked falls back to its default, the same as for a
  * document that was never kept. A kept value is a convenience, never something an app must have.
  *
  * The plain types have theirs here (String, Int, Long, Double, Boolean, and an Option of any of them); an enum is
  * kept by name (`Codec.named`); anything else is two functions (`Codec.from`), or, in an app that has fabric, its
  * `RW` (`FabricCodec`, beside the network module). */
trait Codec[T] {
  /** `value` as the text kept. */
  def encode(value: T): String

  /** The value `text` holds, or None when it holds none this codec reads. */
  def decode(text: String): Option[T]
}

object Codec {
  /** The codec in scope for `T`. */
  def apply[T](using codec: Codec[T]): Codec[T] = codec

  /** A codec of two functions: `encode` writes the text, `decode` reads it back or answers None. */
  def from[T](encode: T => String, decode: String => Option[T]): Codec[T] = {
    val e = encode
    val d = decode
    new Codec[T] {
      override def encode(value: T): String = e(value)
      override def decode(text: String): Option[T] = d(text)
    }
  }

  /** One of `values`, kept by the name `name` gives it: an enum as `Codec.named(Sort.values.toList)(_.toString)`. A
    * name that no longer matches any of them (a case since renamed or removed) reads as None. */
  def named[T](values: List[T])(name: T => String): Codec[T] =
    from[T](name, text => values.find(v => name(v) == text))

  given Codec[String] = from[String](s => s, s => Some(s))

  given Codec[Int] = from[Int](_.toString, _.trim.toIntOption)

  given Codec[Long] = from[Long](_.toString, _.trim.toLongOption)

  given Codec[Double] = from[Double](_.toString, _.trim.toDoubleOption)

  given Codec[Boolean] = from[Boolean](b => if (b) "true" else "false", {
    case "true" => Some(true)
    case "false" => Some(false)
    case _ => None
  })

  /** An Option, told apart from what it holds: "none", or "some:" and the value's own text, so `Some("")` and
    * `None` (and `Some(None)`) each come back as they went in. */
  given [T](using inner: Codec[T]): Codec[Option[T]] = from[Option[T]](
    {
      case Some(value) => "some:" + inner.encode(value)
      case None => "none"
    },
    text =>
      if (text == "none") Some(None)
      else if (text.startsWith("some:")) inner.decode(text.substring(5)).map(v => Some(v))
      else None
  )
}
