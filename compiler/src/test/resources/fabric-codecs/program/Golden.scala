package probe

import fabric.*
import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw.*
import lib.{Holder, Level, Stamp}
import Codecs.stampRW

/** Values written to JSON and read back, and fabric's JSON read: `RW json <label> <json>` and `RW eq <label> <bool>`
  * lines, the JVM's the golden the emitted program's are held to. */
object Golden {
  def show(label: String, j: Json): Unit = println("RW json " + label + " " + JsonFormatter.Compact(j))
  def same(label: String, a: Any, b: Any): Unit = println("RW eq " + label + " " + (a == b))

  def run(): Unit = {
    val full = Event("e1", Purpose.Login, Inner(2, "two"), Stamp(10L, "CET", List("a", "b")),
      Holder(Level.Warn, Stamp(11L), Some("held")), Priority.High,
      List(Attendee.Person("a@b.c", Some("A")), Attendee.Room("R1"), Attendee.Person("d@e.f", None)),
      List(Shape.Circle(3), Shape.Dot, Shape.Rect(4, 5)), Some(Signal.Go(7)), Set(Level.Debug, Level.Info),
      Map(Kind.A -> 1, Kind.B -> 2), Map("k" -> Inner(9)), Some(Inner(8, "eight")),
      List(Some(Priority.Low), None, Some(Priority.High)), List(List(Shape.Dot), Nil, List(Shape.Circle(1))),
      Map("ks" -> List(Kind.B, Kind.A)))
    show("full", full.json)
    val back = JsonParser(JsonFormatter.Compact(full.json)).as[Event]
    same("full", back, full)
    show("full.again", back.json)

    val stopped = full.copy(signal = Some(Signal.Stop), shapes = Nil, attendees = Nil)
    show("stopped", stopped.json)
    same("stopped", JsonParser(JsonFormatter.Compact(stopped.json)).as[Event], stopped)

    // missing fields, and a null one, taking their defaults
    val minimal = JsonParser("""{"id":"m","purpose":"reset","inner":{"n":1},"stamp":{"at":5},""" +
      """"held":{"level":"Level.Info","stamp":{"at":6,"zone":null}},"priority":null}""").as[Event]
    show("minimal", minimal.json)
    same("minimal", minimal, Event("m", Purpose.Reset, Inner(1), Stamp(5L), Holder(Level.Info, Stamp(6L))))

    // older wire forms fabric still reads: an enum by its case alone, in any case; a case by its name alone
    val legacy = JsonParser("""{"id":"l","purpose":"login","inner":{"n":3},"stamp":{"at":7},""" +
      """"held":{"level":"warn","stamp":{"at":8}},"priority":"low",""" +
      """"attendees":[{"type":"Room","code":"R9"},{"type":"person","email":"x@y.z","name":"X"}],""" +
      """"shapes":[{"type":"Rect","w":2},{"type":"Dot"}],"signal":{"type":"Go","speed":1}}""").as[Event]
    show("legacy", legacy.json)
    same("legacy", legacy, Event("l", Purpose.Login, Inner(3), Stamp(7L), Holder(Level.Warn, Stamp(8L)), Priority.Low,
      List(Attendee.Room("R9"), Attendee.Person("x@y.z", Some("X"))), List(Shape.Rect(2), Shape.Dot), Some(Signal.Go(1))))

    // each codec alone
    show("priority", Priority.Normal.json)
    show("shape", (Shape.Dot: Shape).json)
    show("attendee", (Attendee.Room("R2"): Attendee).json)
    same("attendee", JsonParser("""{"type":"Attendee.Person","email":"q","name":null}""").as[Attendee],
      Attendee.Person("q", None))
    same("level", JsonParser("\"Level.Debug\"").as[Level], Level.Debug)
    show("stamp", Stamp(1L).json)

    val extras = Extras(Mode.Dark, Theme.Fancy, Size.Large, Point(3, 4),
      List(Msg.Hello("hi"), Msg.Ping, Msg.Bye(2)), List(Animal.Cat(9), Animal.Dog("rex")),
      Map(1 -> "one", 2 -> "two"), Set(Shape.Dot, Shape.Circle(5)), Some(List(Size.Small)), Some(Stamp(4L)),
      Map("m" -> Mode.Light), Tone.Warm)
    show("extras", extras.json)
    same("extras", JsonParser(JsonFormatter.Compact(extras.json)).as[Extras], extras)
    val older = JsonParser("""{"mode":"light","theme":"plain","tone":"TONE.WARM","size":"small","point":5006,""" +
      """"msgs":[{"kind":"bye"},{"kind":"Msg.Ping"}],"pets":[{"species":"Hound","name":"fido"},{"species":"cat","lives":1}],""" +
      """"byInt":[{"key":7,"value":"seven"}],"shapeSet":[],"sizes":null,"stamp":null}""").as[Extras]
    show("older", older.json)
    same("older", older, Extras(Mode.Light, Theme.Plain, Size.Small, Point(5, 6), List(Msg.Bye(), Msg.Ping),
      List(Animal.Dog("fido"), Animal.Cat(1)), Map(7 -> "seven"), Set.empty, None, None, tone = Tone.Warm))
    println("RW done")
  }
}

@main def goldenMain(): Unit = Golden.run()
