package everyui.render

import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/** Part of a text with its own letter spacing (`TextRun.letterSpacing`), `px` after each letter. Android spaces letters
  * in ems, so it is the pixels over the size the part is drawn at, which a size span before this one has set. */
class SpacingSpan(px: Double) extends MetricAffectingSpan {
  override def updateDrawState(paint: TextPaint): Unit = apply(paint)
  override def updateMeasureState(paint: TextPaint): Unit = apply(paint)

  private def apply(paint: TextPaint): Unit =
    paint.setLetterSpacing(if (paint.getTextSize > 0f) (px / paint.getTextSize).toFloat else 0f)
}
