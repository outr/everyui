package everyui.markdown

import everyui.component.{Component, RichText}
import everyui.component.feature.MeasuredFeature
import everyui.{FxSpec, ui}

/** A paragraph's lines measured once for each width, words and face they are drawn in, and not again as nothing that
  * changes them changes: the same width given again, or the view placed again. Its lines' runs are worked out once for
  * each change of its words, the mentions or the theme, and follow each. */
class ParagraphMeasureSpec extends FxSpec {
  // the lines (RichText) under `c` read for their size while `f` runs
  private def reads(c: Component)(f: => Unit): Int = fx {
    var n = 0
    MeasuredFeature.heard = Some(m => if (m.isInstanceOf[RichText] && within(m, c)) n += 1)
    try f finally MeasuredFeature.heard = None
    n
  }
  private def within(m: Component, c: Component): Boolean = (m eq c) || m.parent.get.exists(p => within(p, c))
  // (read on the FX thread)
  private def lines(c: Component): Vector[RichText] = c match {
    case r: RichText => Vector(r)
    case k: everyui.component.Container if k.visible() => k.children().flatMap(lines)
    case _ => Vector.empty
  }

  "A paragraph" should {
    "be measured again for a new width, new words and a new face, and not for the same again" in {
      val view = fx {
        ui.children.clear()
        val v = new MarkdownView("The socket drops after a minute idle, so it now sends a heartbeat every 25 seconds " +
          "and waits longer each time it reconnects.\n\nI ran the tests again and they pass.", measures = true,
          breaks = true)
        v.size.width @= 400.0
        ui.children += v
        v
      }
      try {
        (1 to 3).foreach(_ => fx(()))
        val tall = fx(view.size.height())
        reads(view)(view.size.width @= 400.0) shouldBe 0
        reads(view)(view.size.width @= 200.0) should be > 0
        fx(view.size.height()) should be > tall
        reads(view)(view.size.width @= 200.0) shouldBe 0
        val words = reads(view)(view.content @= view.content() + " Twice.")
        words should be > 0
        fx(lines(view).last.runs().map(_.text).mkString) should endWith ("Twice.")
        val first = fx(lines(view).head)
        reads(view)(first.font.size @= 22.0) should be > 0
        // a mention followed in the lines' runs
        fx(view.content @= "Ask @Ada about it.")
        fx(view.mentions @= Set("Ada"))
        fx(lines(view).head.runs().exists(r => r.text == "@Ada" && r.color == MarkdownView.mention().toHex)) shouldBe
          true
      } finally fx {
        ui.children.clear()
        view.dispose()
      }
    }
  }
}
