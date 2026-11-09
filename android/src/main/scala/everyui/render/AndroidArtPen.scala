package everyui.render

import android.graphics.{Canvas, Paint}
import everyui.draw.{ArtPen, Path}

/** [[ArtPen]] on an Android canvas (one over a Bitmap, `Host.svgBitmap`): each shape's path made an
  * `android.graphics.Path` and drawn with an antialiased paint, filled or stroked. Safe on a loading thread, as the
  * canvas and its bitmap are its own. */
final class AndroidArtPen(canvas: Canvas) extends ArtPen {
  private val paint = new Paint(Paint.ANTI_ALIAS_FLAG)

  def fill(path: Path, color: everyui.Color, evenOdd: Boolean): Unit = {
    val p = AndroidArtPen.path(path)
    p.setFillType(if (evenOdd) android.graphics.Path.FillType.EVEN_ODD else android.graphics.Path.FillType.WINDING)
    paint.setStyle(Paint.Style.FILL)
    paint.setColor(AndroidArtPen.argb(color))
    canvas.drawPath(p, paint)
  }

  def stroke(path: Path, color: everyui.Color, width: Double, cap: String, join: String): Unit = {
    paint.setStyle(Paint.Style.STROKE)
    paint.setColor(AndroidArtPen.argb(color))
    paint.setStrokeWidth(width.toFloat)
    paint.setStrokeCap(cap match {
      case "round" => Paint.Cap.ROUND
      case "square" => Paint.Cap.SQUARE
      case _ => Paint.Cap.BUTT
    })
    paint.setStrokeJoin(join match {
      case "round" => Paint.Join.ROUND
      case "bevel" => Paint.Join.BEVEL
      case _ => Paint.Join.MITER
    })
    paint.setStrokeMiter(4f)
    canvas.drawPath(AndroidArtPen.path(path), paint)
  }
}

object AndroidArtPen {
  def argb(c: everyui.Color): Int = (c.alphaInt << 24) | (c.redInt << 16) | (c.greenInt << 8) | c.blueInt

  /** The four steps every backend draws, as Android's own path. */
  def path(path: Path): android.graphics.Path = {
    val p = new android.graphics.Path()
    path.ops.foreach { op =>
      val v = op.values.map(_.toFloat)
      op.kind match {
        case "M" => p.moveTo(v(0), v(1))
        case "L" => p.lineTo(v(0), v(1))
        case "C" => p.cubicTo(v(0), v(1), v(2), v(3), v(4), v(5))
        case "Z" => p.close()
        case _ => ()
      }
    }
    p
  }
}
