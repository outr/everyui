package lib

import fabric.rw.*

// A library's types, compiled apart from the program and on its classpath only, as a jar's are: an enum with its own
// codec, a case class with none (the program gives it one), and a case class with a codec of each beside it.

enum Level {
  case Debug, Info, Warn
}

object Level {
  given rw: RW[Level] = RW.gen
}

case class Stamp(at: Long, zone: String = "UTC", tags: List[String] = Nil)

case class Holder(level: Level, stamp: Stamp, note: Option[String] = None)

object Holder {
  given stampRW: RW[Stamp] = RW.gen
  given rw: RW[Holder] = RW.gen
}
