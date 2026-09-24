package everyui.render

import android.graphics.{Paint, Typeface}
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/** Part of a text drawn in another face: a fallback's colour emoji, or a family's own italic. A span that sets the
  * face on the paint rather than `TypefaceSpan(Typeface)`, which is Android 9's, and the televisions this runs on are
  * older than that. */
class FaceSpan(face: Typeface) extends MetricAffectingSpan {
  override def updateDrawState(paint: TextPaint): Unit = apply(paint)
  override def updateMeasureState(paint: TextPaint): Unit = apply(paint)

  private def apply(paint: Paint): Unit = {
    // the face's own slant replaces one the paint was asked to fake (a bold asked for is kept)
    paint.setTextSkewX(0f)
    paint.setTypeface(face)
  }
}

object FaceSpan {
  /** `text` with what `drawn` has no glyph for in the first of the fallback faces (`Faces.fallbackFaces`): a run of
    * such characters at a time, the joiners and variation selectors that belong to an emoji with it. The text as it
    * is when there is no fallback, or nothing that needs one. */
  def fallen(text: CharSequence, drawn: Paint): CharSequence = {
    val fallbacks = everyui.font.Faces.fallbackFaces()
    if (fallbacks.isEmpty || !beyondLatin(text)) text
    else {
      val face = Host.typeface(fallbacks.head)
      val out = new android.text.SpannableString(text)
      val s = text.toString
      var i = 0
      var start = -1
      var any = false
      while (i < s.length) {
        val cp = s.codePointAt(i)
        val size = Character.charCount(cp)
        val joins = cp == 0x200D || cp == 0xFE0F || cp == 0x20E3 || (cp >= 0x1F3FB && cp <= 0x1F3FF)
        val missing = cp >= 0x2000 && !joins && !drawn.hasGlyph(new String(Character.toChars(cp)))
        if (missing || (joins && start >= 0)) { if (start < 0) start = i }
        else if (start >= 0) {
          out.setSpan(new FaceSpan(face), start, i, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          any = true
          start = -1
        }
        i += size
      }
      if (start >= 0) {
        out.setSpan(new FaceSpan(face), start, s.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        any = true
      }
      if (any) out else text
    }
  }

  // whether anything in `text` is past the scripts every face here has, which is nearly never: the rest is skipped
  private def beyondLatin(text: CharSequence): Boolean = {
    var i = 0
    var found = false
    while (!found && i < text.length) {
      if (text.charAt(i) >= 0x2000) found = true
      i += 1
    }
    found
  }
}
