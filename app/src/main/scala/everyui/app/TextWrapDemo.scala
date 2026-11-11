package everyui.app

import everyui.component.{Container, TextView}
import everyui.component.types.{TextAlign, TextDirection, WhiteSpace}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui

/** Words wrapped at a width, as a TV app's synopsis is: one paragraph at three widths, given each, and the same with no
  * width of its own in a box that width (kept to its box); cut to two lines with an ellipsis; centred and at the end;
  * an Arabic paragraph that runs right to left, at its start and at its end; code laid out as it is written (`Pre`),
  * on its own lines and never wrapped; a short line in a wide box, whole with no ellipsis; and emoji, a flag, a family
  * joined by ZWJ, Chinese and Arabic in one line, each in the system's face where the app's has none. Every width
  * follows the screen's (`k`); on a phone they stand one under another, a third of them at a time (`part=1`,
  * 2, 3). Logs `wrap <name> <width>x<height>` for each once laid out, and `wrap ok`;
  * `--freeze` takes its frame a second in. */
object TextWrapDemo {
  private val synopsis = "A family trip becomes an unexpected adventure when the children find an old map in their " +
    "grandfather's attic and set out to find a treasure lost a hundred years ago, before the summer ends and the " +
    "house is sold."
  private val arabic = "رحلة عائلية تتحول إلى مغامرة غير متوقعة عندما يكتشف الأطفال خريطة قديمة في علية جدهم، " +
    "فيقررون البحث عن كنز ضائع منذ مئة عام قبل فوات الأوان."
  private val code =
    "def run(): Unit = {\n  val answer = 42\n  println(s\"the answer is $answer, and this line runs past\")\n}"

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    // on a phone the boxes are one under another, a third of them at a time (`part=1`, 2 or 3), as wide as it allows
    val narrow = Platform.screenWidth() < 900.0
    val k = if (narrow) (Platform.screenWidth() - 24.0) / 600.0
      else math.min(1.0, (Platform.screenWidth() - 24.0) / 1240.0)
    val points = if (narrow) 15.0 else math.max(11.0, 18.0 * k)
    val sheet = new Container
    var shown: List[(String, TextView)] = Nil
    def words(name: String, text: String, left: Double, top: Double, width: Double): TextView = {
      val t = new TextView {
        content @= text
        font.family @= "Open Sans"
        font.size @= points
        color := Theme.text()
      }
      if (width > 0.0) t.size.width @= width * k
      t.position.left @= left * k
      t.position.top @= top * k
      shown = shown :+ (name -> t)
      t
    }
    // a paragraph given no width, in a box `width` wide: kept to it, it wraps there
    def boxed(name: String, left: Double, top: Double, width: Double): Container = {
      val box = new Container
      box.size.width @= width * k
      box.size.height @= 200.0 * k
      box.position.left @= left * k
      box.position.top @= top * k
      box.children += words(name, synopsis, 0.0, 0.0, 0.0)
      box
    }
    val at600 = words("600", synopsis, 12.0, 12.0, 600.0)
    val at360 = words("360", synopsis, 640.0, 12.0, 360.0)
    val at200 = words("200", synopsis, 1020.0, 12.0, 200.0)
    val box = boxed("box 360", 640.0, 300.0, 360.0)
    val two = words("2 lines", synopsis, 12.0, 220.0, 600.0)
    two.whiteSpace @= WhiteSpace.Normal
    two.maxLines @= 2
    val centred = words("centre", synopsis, 12.0, 320.0, 600.0)
    centred.align @= TextAlign.Center
    val atEnd = words("end", synopsis, 12.0, 520.0, 600.0)
    atEnd.align @= TextAlign.End
    val rtl = words("arabic", arabic, 640.0, 520.0, 560.0)
    rtl.direction @= TextDirection.RightToLeft
    val rtlEnd = words("arabic end", arabic, 640.0, 660.0, 560.0)
    rtlEnd.direction @= TextDirection.RightToLeft
    rtlEnd.align @= TextAlign.End
    val pre = words("pre", code, 12.0, 720.0, 360.0)
    pre.whiteSpace @= WhiteSpace.Pre
    pre.font.family @= "Roboto Mono"
    val short = words("short", "Fits whole.", 400.0, 720.0, 200.0)
    val scripts = words("scripts", "Emoji 😀🎬 flag 🇺🇸 family 👨‍👩‍👧 中文字幕 العربية", 640.0, 800.0, 560.0)
    val all = List(at600, at360, at200, box, two, centred, atEnd, rtl, rtlEnd, pre, short, scripts)
    if (!narrow) {
      sheet.children ++= all
      sheet.size.width @= 1240.0 * k
      sheet.size.height @= 900.0 * k
    } else {
      val part = math.max(1, math.min(3, Platform.launchArg("part").toIntOption.getOrElse(1)))
      val parts = List(List(at600, at360, at200, box), List(two, centred, atEnd, rtl, rtlEnd),
        List(pre, short, scripts))
      var above: Option[everyui.component.Component] = None
      parts(part - 1).foreach { c =>
        c.position.left @= 12.0
        val over = above
        c.position.top := over.map(a => a.position.top() + a.size.height() + 16.0).getOrElse(12.0)
        above = Some(c)
      }
      sheet.children ++= parts(part - 1)
      sheet.size.width @= Platform.screenWidth()
      sheet.size.height @= Platform.screenHeight()
    }
    ui.children += sheet
    Platform.log("wrap ok")
    Platform.after(0.6, () => {
      shown.foreach { case (name, t) =>
        Platform.log("wrap " + name + " " + t.size.width().round.toString + "x" + t.size.height().round.toString +
          (if (t.elided()) " elided" else ""))
      }
      if (Platform.freezeSeconds() >= 0.0) Platform.after(0.4, () => Platform.frozen())
    })
  }
}
