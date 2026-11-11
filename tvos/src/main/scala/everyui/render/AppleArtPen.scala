package everyui.render

import everyui.draw.{ArtPen, Path}
import everyui.tvos.*

/** [[ArtPen]] on Core Graphics: each shape's path made a CGPath on the canvas the runtime holds for the SVG being drawn
  * (`uiArtBegin`, swift/runtime/UniversalRuntime.swift's ArtCanvas) and filled or stroked there, antialiased. On the
  * main thread, as the program runs. */
final class AppleArtPen extends ArtPen {
  def fill(path: Path, color: everyui.Color, evenOdd: Boolean): Unit = {
    AppleArtPen.trace(path)
    uiArtFill(color.redInt.toDouble, color.greenInt.toDouble, color.blueInt.toDouble, color.alphaInt / 255.0, evenOdd)
  }

  def stroke(path: Path, color: everyui.Color, width: Double, cap: String, join: String): Unit = {
    AppleArtPen.trace(path)
    uiArtStroke(color.redInt.toDouble, color.greenInt.toDouble, color.blueInt.toDouble, color.alphaInt / 255.0, width,
      cap, join)
  }
}

object AppleArtPen {
  /** The four steps every backend draws, as the canvas's path. */
  def trace(path: Path): Unit = path.ops.foreach { op =>
    val v = op.values.toArray
    if (op.kind == "M") uiArtMove(v(0), v(1))
    else if (op.kind == "L") uiArtLine(v(0), v(1))
    else if (op.kind == "C") uiArtCurve(v(0), v(1), v(2), v(3), v(4), v(5))
    else if (op.kind == "Z") uiArtClose()
  }
}
