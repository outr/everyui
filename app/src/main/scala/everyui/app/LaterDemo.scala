package everyui.app

import everyui.component.TextView
import everyui.render.Platform
import everyui.ui

/** `Platform.later` as every backend has it: not at once but on a later turn, in the order asked, and a step that asks
  * for the next one never deeper than one step. Logs `later sync=<ran at once> order=<a,b,c>`, then
  * `later chain=<steps> deepest=<steps on the stack at once> ms=<time>` after 10,000 steps, and shows the same. */
object LaterDemo {
  def run(): Unit = {
    val shown = TextView("later: waiting", 22.0)
    shown.position.left @= 24.0
    shown.position.top @= 24.0
    ui.children += shown
    var ran = false
    var order = List.empty[String]
    Platform.later(() => ran = true)
    val sync = ran
    List("a", "b", "c").foreach(s => Platform.later(() => order = order :+ s))
    Platform.later { () =>
      Platform.log(s"later sync=$sync order=${order.mkString(",")}")
      val steps = 10000
      val started = Platform.ticks()
      var done = 0
      var depth = 0
      var deepest = 0
      def step(): Unit = {
        depth += 1
        deepest = math.max(deepest, depth)
        done += 1
        if (done < steps) Platform.later(() => step())
        else {
          val said = s"later chain=$done deepest=$deepest ms=${(Platform.ticks() - started).toLong}"
          Platform.log(said)
          shown.content @= s"sync=$sync order=${order.mkString(",")} " + said
        }
        depth -= 1
      }
      step()
    }
  }
}
