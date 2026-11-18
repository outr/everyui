package probe

import fabric.*
import fabric.define.{DefType, Definition}
import fabric.rw.*
import lib.{Holder, Level, Stamp}

// Every shape of fabric codec FabricCodecSpec writes and reads, on the JVM and as emitted.

object Codecs {
  given stampRW: RW[Stamp] = RW.gen
}
import Codecs.stampRW

enum Priority {
  case Low, Normal, High
}
object Priority {
  given rw: RW[Priority] = RW.gen
}

enum Purpose {
  case Login, Reset
}
object Purpose {
  given rw: RW[Purpose] = RW.string(_.toString.toLowerCase, s => if (s == "login") Purpose.Login else Purpose.Reset)
}

enum Kind {
  case A, B
}
object Kind {
  given rw: RW[Kind] = RW.gen
}

sealed trait Attendee
object Attendee {
  case class Person(email: String, name: Option[String]) extends Attendee
  case class Room(code: String) extends Attendee
  object Person { given rw: RW[Person] = RW.gen }
  object Room { given rw: RW[Room] = RW.gen }
  given rw: RW[Attendee] = RW.poly[Attendee]()(Person.rw, Room.rw)
}

sealed trait Shape
object Shape {
  case class Circle(r: Int) extends Shape
  case class Rect(w: Int, h: Int = 1) extends Shape
  case object Dot extends Shape
  given rw: RW[Shape] = RW.gen
}

enum Signal {
  case Stop
  case Go(speed: Int)
}
object Signal {
  given rw: RW[Signal] = RW.gen
}

case class Inner(n: Int, label: String = "x")
object Inner {
  given rw: RW[Inner] = RW.gen
}

case class Event(id: String, purpose: Purpose, inner: Inner, stamp: Stamp, held: Holder,
                 priority: Priority = Priority.Normal, attendees: List[Attendee] = Nil, shapes: List[Shape] = Nil,
                 signal: Option[Signal] = None, levels: Set[Level] = Set.empty, byKind: Map[Kind, Int] = Map.empty,
                 tags: Map[String, Inner] = Map.empty, maybe: Option[Inner] = None, prios: List[Option[Priority]] = Nil,
                 grid: List[List[Shape]] = Nil, kinds: Map[String, List[Kind]] = Map.empty)
object Event {
  given rw: RW[Event] = RW.gen
}

sealed trait Mode
object Mode {
  case object Light extends Mode
  case object Dark extends Mode
  given rw: RW[Mode] = RW.gen
}

sealed trait Theme
object Theme {
  case object Plain extends Theme
  case object Fancy extends Theme
  given rw: RW[Theme] = RW.enumeration(List(Plain, Fancy))
}

sealed trait Tone
object Tone {
  case object Warm extends Tone
  case object Cool extends Tone
  given rw: RW[Tone] = RW.enumeration(List(Warm, Cool), caseSensitive = false)
}

enum Size {
  case Small, Large
}
object Size {
  given rw: RW[Size] = RW.enumeration(List(Size.Small, Size.Large), _.toString.toLowerCase, caseSensitive = true)
}

case class Point(x: Int, y: Int)
object Point {
  given rw: RW[Point] =
    RW.from(p => num(p.x * 1000 + p.y), j => Point(j.asInt / 1000, j.asInt % 1000), Definition(DefType.Int))
}

@typeField("kind")
sealed trait Msg
object Msg {
  case class Hello(name: String) extends Msg
  case class Bye(code: Int = 0) extends Msg
  case object Ping extends Msg
  given rw: RW[Msg] = RW.gen
}

sealed trait Animal
object Animal {
  case class Dog(name: String) extends Animal
  case class Cat(lives: Int) extends Animal
  object Dog { given rw: RW[Dog] = RW.gen }
  object Cat { given rw: RW[Cat] = RW.gen }
  given rw: RW[Animal] = RW.poly[Animal]("species", List("Hound" -> "Animal.Dog"))(Dog.rw, Cat.rw)
}

case class Extras(mode: Mode, theme: Theme, size: Size, point: Point, msgs: List[Msg], pets: List[Animal],
                  byInt: Map[Int, String], shapeSet: Set[Shape], sizes: Option[List[Size]], stamp: Option[Stamp],
                  modes: Map[String, Mode] = Map.empty, tone: Tone = Tone.Cool)
object Extras {
  given rw: RW[Extras] = RW.gen
}
