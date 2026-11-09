package everyui.render

import everyui.draw.{ArtPen, Path}
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.scene.shape.{FillRule, StrokeLineCap, StrokeLineJoin}

/** [[ArtPen]] on a JavaFX canvas: each shape's path traced on `g` and filled or stroked there, antialiased, so a
  * snapshot of the canvas is the picture (`Host.svgPicture`). */
final class FxArtPen(g: GraphicsContext) extends ArtPen {
  def fill(path: Path, color: everyui.Color, evenOdd: Boolean): Unit = {
    trace(path)
    g.setFillRule(if (evenOdd) FillRule.EVEN_ODD else FillRule.NON_ZERO)
    g.setFill(FxArtPen.paint(color))
    g.fill()
  }

  def stroke(path: Path, color: everyui.Color, width: Double, cap: String, join: String): Unit = {
    trace(path)
    g.setStroke(FxArtPen.paint(color))
    g.setLineWidth(width)
    g.setLineCap(cap match {
      case "round" => StrokeLineCap.ROUND
      case "square" => StrokeLineCap.SQUARE
      case _ => StrokeLineCap.BUTT
    })
    g.setLineJoin(join match {
      case "round" => StrokeLineJoin.ROUND
      case "bevel" => StrokeLineJoin.BEVEL
      case _ => StrokeLineJoin.MITER
    })
    g.setMiterLimit(4.0)
    g.stroke()
  }

  private def trace(path: Path): Unit = {
    g.beginPath()
    path.ops.foreach { op =>
      val v = op.values
      op.kind match {
        case "M" => g.moveTo(v(0), v(1))
        case "L" => g.lineTo(v(0), v(1))
        case "C" => g.bezierCurveTo(v(0), v(1), v(2), v(3), v(4), v(5))
        case "Z" => g.closePath()
        case _ => ()
      }
    }
  }
}

object FxArtPen {
  def paint(c: everyui.Color): Color = Color.rgb(c.redInt, c.greenInt, c.blueInt, c.alpha)
}
