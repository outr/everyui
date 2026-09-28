package everyui.render

import android.content.Context
import android.graphics.{Canvas, Paint, Path}
import android.view.View
import everyui.draw.Figure

/** A drawing node's view (everyui.draw.Drawing): its figures drawn on its own canvas, again only when they change or
  * the view changes size. The paths are made once per change, not per frame. */
final class DrawingView(context: Context) extends View(context) {
  private var drawn: List[(Path, Figure)] = Nil
  private val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
  paint.setStrokeCap(Paint.Cap.ROUND)
  paint.setStrokeJoin(Paint.Join.ROUND)

  def figures(all: List[Figure]): Unit = {
    drawn = all.map(f => DrawingView.path(f) -> f)
    invalidate()
  }

  override def onDraw(canvas: Canvas): Unit = drawn.foreach { case (path, f) =>
    f.fill.foreach { c =>
      paint.setStyle(Paint.Style.FILL)
      paint.setColor(DrawingView.argb(c))
      canvas.drawPath(path, paint)
    }
    f.stroke.foreach { c =>
      paint.setStyle(Paint.Style.STROKE)
      paint.setStrokeWidth(f.width.toFloat)
      paint.setColor(DrawingView.argb(c))
      canvas.drawPath(path, paint)
    }
  }
}

object DrawingView {
  def argb(c: everyui.Color): Int = android.graphics.Color.argb(c.alphaInt, c.redInt, c.greenInt, c.blueInt)

  /** A figure's path as Android draws one. */
  def path(f: Figure): Path = {
    val p = new Path
    f.path.ops.foreach { op =>
      val v = op.values.map(_.toFloat).toArray
      op.kind match {
        case "M" => p.moveTo(v(0), v(1))
        case "L" => p.lineTo(v(0), v(1))
        case "C" => p.cubicTo(v(0), v(1), v(2), v(3), v(4), v(5))
        case _ => p.close()
      }
    }
    p
  }
}
