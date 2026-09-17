package everyui.component

import everyui.Color
import everyui.image.QrCode
import reactify.*

/** A QR code for `text`, `across` pixels square with its quiet zone, dark on white whatever the theme (a camera reads
  * nothing else). Each row's dark modules are drawn as runs, so a code is a few hundred boxes rather than thousands. */
class QrView(text: String, across: Double) extends Container {
  override def kind: String = "QrView"

  size.width @= across
  size.height @= across
  backgroundColor @= Color.White
  QrCode.encode(text).foreach { code =>
    // a quiet zone of four modules about the code
    val module = math.floor(across / (code.size + 8))
    val offset = math.floor((across - module * code.size) / 2.0)
    val runs = (0 until code.size).toList.flatMap { y =>
      var found: List[(Int, Int)] = Nil
      var start = -1
      (0 to code.size).foreach { x =>
        val on = x < code.size && code.dark(x, y)
        if (on && start < 0) start = x
        if (!on && start >= 0) {
          found = found :+ ((start, x - start))
          start = -1
        }
      }
      found.map(run => (run._1, y, run._2))
    }
    children ++= runs.map { run =>
      val box: Component = new Container {
        position.left @= offset + run._1 * module
        position.top @= offset + run._2 * module
        size.width @= run._3 * module
        size.height @= module
        backgroundColor @= Color.Black
      }
      box
    }
  }
}
