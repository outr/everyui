package reactifytest.app

import reactifytest.lib.Holder

/** An app's case class mixing in a library's trait (Kinds). */
case class Episode(n: Int) extends Holder
