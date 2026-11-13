package reactifytest.app

import reactifytest.{Box, ReactifySuite}

/** Compound assignment to an object's var from outside the object (`AccountOffer.offered += "x"`): scalac binds the
  * object to a temporary first, which the Roku emitter wrote as a name nothing defined, and lint refused. */
class Offerer {
  def offer(): Unit = {
    AccountOffer.offered += "x"
    AccountOffer.offered += "y"
    AccountOffer.offered -= "x"
    AccountOffer.names ++= List("a", "b")
    AccountOffer.names :+= "c"
    AccountOffer.count += 5
    AccountOffer.count -= 2
    AccountOffer.Inner.n += 3
    AccountOffer.Inner.n *= 2
    Shown.seen += "z"
    Shown.seen ++= Set("w")
    OfferLog.seen :+= "r"
    OfferLog.seen ++= List("s")
  }
}

object Offerer {
  import ReactifySuite.check

  def run(ok: Box, total: Box): Unit = {
    val offerer = new Offerer
    offerer.offer()
    offerer.offer()
    check(ok, total, "emit.modassign.set", AccountOffer.offered.toList.sorted.mkString(","), "y")
    check(ok, total, "emit.modassign.list", AccountOffer.names.mkString(","), "a,b,c,a,b,c")
    check(ok, total, "emit.modassign.int", AccountOffer.count.toString, "6")
    check(ok, total, "emit.modassign.nested", AccountOffer.Inner.n.toString, "18")
    check(ok, total, "emit.modassign.companion", Shown.seen.toList.sorted.mkString(","), "w,z")
    check(ok, total, "emit.modassign.model", OfferLog.seen.mkString(","), "boot,r,s,r,s")
  }
}
