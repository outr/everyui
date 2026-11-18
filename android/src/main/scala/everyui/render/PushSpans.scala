package everyui.render

import android.graphics.Typeface
import android.text.{SpannableStringBuilder, Spanned}
import android.text.style.{BackgroundColorSpan, StyleSpan, TypefaceSpan}
import everyui.push.{PushFormat, PushMessage, PushText}

/** A push's body as a notification shows it (`PushAccess`): a Markdown one (`PushFormat.Markdown`) styled, its words
  * `PushText.plain`'s, bold and slanted words a StyleSpan (struck ones left out: PushText), code in the monospaced
  * face on a light wash; a link its words in the text's own colour, with no URLSpan, since a notification opens none.
  * A plain one as it is. */
private[render] object PushSpans {
  // code's wash: a mid grey a fifth opaque, which shows on the light shade and the dark alike
  private val wash = 0x33888888

  /** `message`'s body, styled where it is Markdown. */
  def body(message: PushMessage): CharSequence =
    if (message.format != PushFormat.Markdown) message.body
    else {
      val out = new SpannableStringBuilder
      PushText.runs(message.body).foreach { r =>
        val start = out.length
        out.append(r.text)
        val span = (s: AnyRef) => out.setSpan(s, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val bold = r.weight >= 700
        if (bold || r.italic) span(new StyleSpan(
          if (bold && r.italic) Typeface.BOLD_ITALIC else if (bold) Typeface.BOLD else Typeface.ITALIC))
        if (r.family == "monospace") {
          span(new TypefaceSpan("monospace"))
          span(new BackgroundColorSpan(wash))
        }
      }
      out
    }

  /** The first line of `text`, as a collapsed notification shows it; all of it where it has one line. */
  def firstLine(text: CharSequence): CharSequence = {
    val end = text.toString.indexOf('\n')
    if (end < 0) text else text.subSequence(0, end)
  }
}
