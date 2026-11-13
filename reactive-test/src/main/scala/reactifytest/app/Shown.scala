package reactifytest.app

/** A trait whose companion has a var, written with `+=` from a class outside it (Offerer). */
trait Shown

object Shown {
  var seen: Set[String] = Set.empty
}
