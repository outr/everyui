package gaps

import fabric.*
import fabric.rw.*

// Codecs fabric derives that the emitters do not follow: each is a gap of the emit, never a codec passing values through.

case class Box[T](value: T)
object Box {
  given [T: RW]: RW[Box[T]] = RW.gen
}

case class Boxes(ints: Box[Int])
object Boxes {
  given rw: RW[Boxes] = RW.gen
}

case class Node(name: String, children: List[Node])
object Node {
  given rw: RW[Node] = RW.gen
}

case class Letter(c: Char)
object Letter {
  given rw: RW[Letter] = RW.gen
}

sealed trait Reply
object Reply {
  case object Yes extends Reply
  @unknownCase case class Other(value: String) extends Reply
  given rw: RW[Reply] = RW.gen
}

object Gaps {
  def run(): Unit = {
    println(Boxes(Box(1)).json)
    println(Node("a", Nil).json)
    println(Letter('x').json)
    println((Reply.Yes: Reply).json)
  }
}

@main def gapsMain(): Unit = Gaps.run()
