package everyui.text

import everyui.component.{Component, Container, Row, TextView}
import everyui.component.types.{TextOverflow, WhiteSpace}
import everyui.{FxSpec, ui}

/** Words wrapped by default (`TextOverflow.Wrap`, Matt's ruling of 2026-10-27): a line wider than its box takes as many
  * lines as it needs within it, its words whole and not cut, a word too long for a line broken where it must be; words
  * given a `maxWidth` wrap at it; `maxLines` still cuts what passes it with an ellipsis; words that fit are drawn exactly
  * as before; and a view, or the app, asking for `Ellipsis` cuts as before. */
class TextWrapSpec extends FxSpec {
  private val long = "A rather long line of words that will not fit in a narrow panel at all"

  private def words(value: String): TextView = new TextView {
    content @= value
    font.size @= 14.0
  }

  private def panel(width: Double, parts: Component*): Container = fx(new Container {
    size.width @= width
    size.height @= 400.0
    children ++= parts
  })

  private def settle(): Unit = (1 to 6).foreach(_ => fx(()))

  private def onScreen[T](parts: Component*)(check: => T): T = {
    fx(ui.children ++= parts)
    settle()
    try check
    finally fx {
      parts.foreach(_.dispose())
      ui.children.clear()
    }
  }

  private def oneLine(): Double = {
    val t = fx(words("Short"))
    val h = fx(t.size.height())
    fx(t.dispose())
    h
  }

  "Words by default" should {
    "wrap within the panel they run past, whole, taking the lines they need" in {
      fx(TextOverflow.default()) shouldBe TextOverflow.Wrap
      val t = fx(words(long))
      onScreen(panel(160.0, t)) {
        fx(t.content()) shouldBe long
        fx(t.elided()) shouldBe false
        fx(t.size.width()) should be <= 160.5
        fx(t.size.height()) should be > oneLine() * 2.0
      }
    }
    "wrap a line given a width of its own, narrower than it, within that width: a heading in a narrowed card" in {
      val t = fx(words(long))
      fx(t.size.width @= 112.0)
      onScreen(panel(400.0, t)) {
        fx(t.content()) shouldBe long
        fx(t.node.getText()) shouldBe long
        fx(t.elided()) shouldBe false
        fx(t.size.width()) shouldBe 112.0
        fx(t.size.height()) should be > oneLine() * 2.0
        fx(t.node.textWidth()) should be <= 112.5
        // given room for them again, the words go back on one line
        fx(t.size.width @= 900.0)
        settle()
        fx(t.size.height()) shouldBe oneLine()
      }
    }
    "draw words that fit exactly as before" in {
      val fits = fx(words("Short words"))
      val alone = fx(words("Short words"))
      val before = (fx(alone.size.width()), fx(alone.size.height()))
      onScreen(panel(400.0, fits)) {
        (fx(fits.size.width()), fx(fits.size.height())) shouldBe before
      }
      fx(alone.dispose())
    }
    "wrap at their maxWidth, and be cut to maxLines with an ellipsis where they pass it" in {
      val t = fx(words(long))
      fx(t.maxWidth @= 150.0)
      onScreen(panel(600.0, t)) {
        fx(t.elided()) shouldBe false
        fx(t.size.width()) should be <= 150.5
        fx(t.size.height()) should be > oneLine() * 2.0
        fx(t.maxLines @= 2)
        settle()
        fx(t.elided()) shouldBe true
        fx(t.size.height()) should be <= oneLine() * 2.5
      }
    }
    "break a word too long for a line where it must be" in {
      val t = fx(words("https://example.com/a/very/long/path/that/never/has/a/space/in/it/at/all"))
      onScreen(panel(140.0, t)) {
        fx(t.size.width()) should be <= 140.5
        fx(t.elided()) shouldBe false
      }
    }
    "wrap in a row, within the room it leaves them before what comes after" in {
      val t = fx(words(long))
      val after = fx(words("after"))
      val row = fx(Row(8.0)(t, after))
      onScreen(panel(260.0, row)) {
        fx(t.size.width() + 8.0 + after.size.width()) should be <= 260.5
        fx(t.elided()) shouldBe false
      }
    }
    "be cut with an ellipsis where the view asks for it, or the app does for every view" in {
      val t = fx(words(long))
      fx(t.textOverflow @= TextOverflow.Ellipsis)
      onScreen(panel(160.0, t)) {
        fx(t.elided()) shouldBe true
        fx(t.size.height()) shouldBe oneLine()
        // and wrapping again where it is told to
        fx(t.textOverflow @= TextOverflow.Wrap)
        settle()
        fx(t.elided()) shouldBe false
        fx(t.size.height()) should be > oneLine() * 2.0
      }
      fx(TextOverflow.default @= TextOverflow.Ellipsis)
      try {
        val cut = fx(words(long))
        onScreen(panel(160.0, cut)) {
          fx(cut.elided()) shouldBe true
        }
      } finally fx(TextOverflow.default @= TextOverflow.Wrap)
    }
    "keep words laid out as written (code) on their lines" in {
      val t = fx(words(long))
      fx(t.whiteSpace @= WhiteSpace.Pre)
      onScreen(panel(160.0, t)) {
        fx(t.size.height()) shouldBe oneLine()
      }
    }
  }
}
