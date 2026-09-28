package everyui.monaco

import fabric.*

/** Room above the first line and below the last (`padding`), in pixels. */
case class MonacoPaddingOptions(top: Option[Int] = None, bottom: Option[Int] = None, raw: Json = obj()) extends MonacoOptionSet
