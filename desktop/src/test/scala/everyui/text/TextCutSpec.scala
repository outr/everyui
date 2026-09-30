package everyui.text

import everyui.component.{Row, TextView}
import everyui.component.types.WhiteSpace
import everyui.{FxSpec, ui}

/** Words cut short to fit (`TextView.maxWidth`, `maxLines`): cut where they were made off the screen, cut again when
  * their face or size changes after the words were set, and measured as cut. */
class TextCutSpec extends FxSpec {
  private val name = "A fairly long name · 180 eps"

  "Words cut short to fit" should {
    "be cut where they were made before the row that holds them was on the screen" in {
      val names = fx((1 to 5).map { _ =>
        new TextView {
          content @= name
          font.size @= 11.0
          maxWidth @= 80.0
        }
      })
      val row = fx(Row(spacing = 12.0)(names*))
      fx(ui.children += row)
      try {
        names.foreach { t =>
          fx(t.node.getText()) should endWith("…")
          fx(t.size.width()) should be <= 80.0
          fx(t.size.width()) shouldBe (fx(t.node.getWidth()) +- 0.001)
        }
        fx(row.size.width()) should be <= (5 * 80.0 + 4 * 12.0)
      }
      finally fx(ui.children.clear())
    }

    "be cut again when the size changes after the words" in {
      val label = fx(new TextView {
        content @= name
        maxWidth @= 80.0
      })
      fx(ui.children += label)
      try {
        // cut at the default 16, then made smaller: more of the words fit
        val large = fx(label.node.getText())
        fx(label.font.size @= 11.0)
        val small = fx(label.node.getText())
        small.length should be > large.length
        small should endWith("…")
        fx(label.size.width()) should be <= 80.0
        // and larger again
        fx(label.font.size @= 16.0)
        fx(label.node.getText()) shouldBe large
      }
      finally fx(ui.children.clear())
    }

    "cut wrapped words to their lines" in {
      val words = "A family trip becomes an unexpected adventure when the children find an old map in their " +
        "grandfather's attic and set out to find a treasure lost a hundred years ago."
      val paragraph = fx(new TextView {
        content @= words
        whiteSpace @= WhiteSpace.Normal
        maxLines @= 2
        font.size @= 14.0
        size.width @= 220.0
      })
      fx(ui.children += paragraph)
      try {
        fx(paragraph.node.getText()) should endWith("…")
        val one = fx {
          paragraph.maxLines @= 1
          paragraph.size.height()
        }
        fx(paragraph.maxLines @= 2)
        fx(paragraph.size.height()) should be <= (one * 2.5)
        fx(paragraph.size.height()) should be > (one * 1.5)
      }
      finally fx(ui.children.clear())
    }
  }
}
