package everyui.monaco

import fabric.*

/** The hover (`hover`): whether it shows, after how many milliseconds, whether it stays under the pointer, and
  * whether it prefers to open above. */
case class MonacoHoverOptions(enabled: Option[Boolean] = None, delay: Option[Int] = None, sticky: Option[Boolean] = None,
                              hidingDelay: Option[Int] = None, above: Option[Boolean] = None,
                              raw: Json = obj()) extends MonacoOptionSet
