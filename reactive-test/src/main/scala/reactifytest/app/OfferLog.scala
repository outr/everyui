package reactifytest.app

/** An object with a statement in its body (one of the object model), its var written with `:+=` and `++=` from a
  * class outside it (Offerer). */
object OfferLog {
  var seen: List[String] = Nil
  seen = seen :+ "boot"
}
