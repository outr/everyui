package reactifytest.app

/** A top-level object's vars, and a nested object's, written with `+=`, `-=` and `++=` from a class outside it
  * (Offerer). */
object AccountOffer {
  var offered: Set[String] = Set.empty
  var count: Int = 0
  var names: List[String] = Nil

  object Inner {
    var n: Int = 0
  }
}
