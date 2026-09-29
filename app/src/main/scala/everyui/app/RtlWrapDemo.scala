package everyui.app

import everyui.component.TextView
import everyui.component.types.WhiteSpace
import everyui.i18n.Direction
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui

/** Paragraphs 560 wide that wrap, in a right-to-left direction: an Arabic overview of about 150 characters, the same
  * cut to two lines, and one in English, in Open Sans, a face of the channel's own, as an app sets one (text in a
  * Roku's system font is drawn by its Label alone). On a Roku the Arabic was drawn as one line about 780 wide, since
  * text in a script its face has no glyphs for is drawn in runs, which did not wrap. Logs each one's laid-out size:
  *
  *   [ui] rtlwrap arabic 560x<height> clamped 560x<height> english 560x<height>
  */
object RtlWrapDemo {
  private val arabic = "رحلة عائلية تتحول إلى مغامرة غير متوقعة عندما يكتشف الأطفال خريطة قديمة في علية جدهم، فيقررون البحث عن كنز ضائع منذ مئة عام… قبل فوات الأوان."
  private val english = "A family trip becomes an unexpected adventure when the children find an old map in their grandfather's attic and set out to find a treasure lost a hundred years ago."

  def run(): Unit = {
    Direction.set(true)
    ui.backgroundColor := Theme.background()
    def paragraph(words: String, top: Double, lines: Int): TextView = new TextView {
      content @= words
      whiteSpace @= WhiteSpace.Normal
      maxLines @= lines
      font.family @= "Open Sans"
      font.size @= 20.0
      color := Theme.text()
      size.width @= 560.0
      position.left @= 40.0
      position.top @= top
    }
    val overview = paragraph(arabic, 40.0, 0)
    val clamped = paragraph(arabic, 200.0, 2)
    val plain = paragraph(english, 300.0, 0)
    ui.children ++= List(overview, clamped, plain)
    def box(t: TextView): String = t.size.width().round.toString + "x" + t.size.height().round.toString
    Platform.after(0.5, () => {
      Platform.log("rtlwrap arabic " + box(overview) + " clamped " + box(clamped) + " english " + box(plain))
      if (Platform.freezeSeconds() >= 0.0) Platform.frozen()
    })
  }
}
