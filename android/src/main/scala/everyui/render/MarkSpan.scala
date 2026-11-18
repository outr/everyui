package everyui.render

import android.os.Build
import android.text.TextPaint
import android.text.style.{CharacterStyle, UpdateAppearance}

/** Part of a text in a highlighted passage (`TextRun.mark`): `wash` behind its letters and, from Android 10, where the
  * platform draws an underline in a colour of its own, a line of `line` under them (none where `line` is 0). */
class MarkSpan(wash: Int, line: Int) extends CharacterStyle with UpdateAppearance {
  override def updateDrawState(paint: TextPaint): Unit = {
    paint.bgColor = wash
    if (line != 0 && Build.VERSION.SDK_INT >= 29) {
      paint.underlineColor = line
      paint.underlineThickness = 1.5f * paint.density
    }
  }
}
