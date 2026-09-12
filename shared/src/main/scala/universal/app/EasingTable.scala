package universal.app

import universal.render.Platform
import universal.ui.Easing

/** Dumps every easing curve as numbers, so the two backends can be compared
  * arithmetically rather than by eye. Values are printed raw: each backend
  * formats a Double its own way, and Roku's are 32-bit, so the comparison is
  * done with a tolerance on the parsed numbers rather than on the strings. */
object EasingTable:
  def run(): Unit =
    (0 until Easing.all.size).toList.foreach(i =>
      val e = Easing.all(i)
      Platform.log("easing " + e.name +
        " .25=" + e.easing(0.25).toString +
        " .50=" + e.easing(0.50).toString +
        " .75=" + e.easing(0.75).toString +
        " 1.0=" + e.easing(1.0).toString)
    )
