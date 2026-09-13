package universal.app

import universal.easing.Easing
import universal.render.Platform

/** Dumps every easing curve as numbers, so the two backends can be compared
  * arithmetically rather than by eye. Values are printed raw: each backend
  * formats a Double its own way, and Roku's are 32-bit, so the comparison is
  * done with a tolerance on the parsed numbers rather than on the strings. */
object EasingTable:
  def run(): Unit =
    Easing.map.toList.sortBy(_._1).foreach { case (name, e) =>
      Platform.log("easing " + name +
        " .25=" + e.calculate(0.25).toString +
        " .50=" + e.calculate(0.50).toString +
        " .75=" + e.calculate(0.75).toString +
        " 1.0=" + e.calculate(1.0).toString)
    }
