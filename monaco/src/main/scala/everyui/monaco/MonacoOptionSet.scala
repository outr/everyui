package everyui.monaco

import fabric.*

/** A set of Monaco options as a case class of optional fields, named as Monaco names them: what is not set is left
  * out, so an update changes only what it sets. `raw` adds any option Monaco has that is not typed here, winning
  * over the fields. */
trait MonacoOptionSet extends Product {
  def raw: Json

  def toJson: Json = Wire.merge(fabric.obj(productElementNames.zip(productIterator).toList.collect {
    case (name, Some(value)) => name -> MonacoOptionSet.value(value)
  }*), raw)
}

object MonacoOptionSet {
  /** An option's value as Monaco takes it. */
  def value(v: Any): Json = v match {
    case b: Boolean => bool(b)
    case i: Int => num(i)
    case d: Double => num(d)
    case s: String => str(s)
    case e: MonacoEnum => str(e.wire)
    case o: MonacoOptionSet => o.toJson
    case j: Json => j
    case l: List[?] => arr(l.map(value)*)
    case other => str(other.toString)
  }
}
