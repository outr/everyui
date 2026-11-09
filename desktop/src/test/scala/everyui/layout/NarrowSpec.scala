package everyui.layout

import everyui.component.{Column, Container, Fill, Link, Row, TextInput, TextView}
import everyui.{FxSpec, ui}
import reactify.Var

/** A form and a row of links in a card whose width follows the window's, narrowed from 1000 to a phone's 390 and
  * widened again: fields bound to the card, or to a column that has the card's width, and a field filling a row
  * (`Fill`), follow it both ways; a row of links too wide for the card keeps each link's words whole, a word on a line
  * at the least, rather than one letter to a line, and takes them back to one line as it widens; a URL in a narrow
  * column is still broken where it must be. */
class NarrowSpec extends FxSpec {
  private def settle(): Unit = (1 to 8).foreach(_ => fx(()))

  private def line(points: Double = 14.0): Double = {
    val t = fx(TextView("Short", points))
    val h = fx(t.size.height())
    fx(t.dispose())
    h
  }

  private def whole(words: String): Double = {
    val t = fx(TextView(words))
    val w = fx(t.size.width())
    fx(t.dispose())
    w
  }

  private def onScreen[T](box: Container)(check: => T): T = {
    fx(ui.children += box)
    settle()
    try check
    finally fx {
      box.dispose()
      ui.children.clear()
    }
  }

  private val words = List("Home", "About", "Jobs", "Press", "Contact us", "Privacy policy", "Terms of service")

  "A form in a card that narrows" should {
    "have its fields follow the card down to a phone's width and back" in {
      val w = Var(1000.0)
      val card = fx(new Container { size.width := w(); size.height @= 800.0; backgroundColor @= everyui.Color.White })
      val to = fx(new TextInput("To"))
      val cc = fx(new TextInput("Cc"))
      val address = fx(new TextInput("Address"))
      val fromRow = fx(Row(8.0)(TextView("From"), Fill(address)))
      val form = fx(Column(12.0, 16.0)(to, cc, fromRow))
      fx {
        form.size.width := card.size.width()
        to.size.width := form.size.width() - 32.0
        cc.size.width := card.size.width() - 32.0
        fromRow.size.width := card.size.width() - 32.0
        card.children += form
      }
      onScreen(card) {
        def widths: (Double, Double, Double, Double) =
          fx((to.size.width(), cc.size.width(), address.position.left() + address.size.width(), form.size.width()))
        val (wideTo, wideCc, wideEnd, wideForm) = widths
        wideTo shouldBe 968.0
        wideCc shouldBe 968.0
        wideEnd shouldBe 968.0 +- 0.5
        wideForm shouldBe 1000.0
        fx(w @= 390.0)
        settle()
        val (narrowTo, narrowCc, narrowEnd, narrowForm) = widths
        narrowTo shouldBe 358.0
        narrowCc shouldBe 358.0
        narrowEnd shouldBe 358.0 +- 0.5
        narrowForm shouldBe 390.0
        fx(w @= 1000.0)
        settle()
        widths shouldBe (wideTo, wideCc, wideEnd, wideForm)
      }
    }
  }

  "A row of links too wide for its card" should {
    "keep each word whole, never a letter to a line, and go back to one line as it widens" in {
      val w = Var(1000.0)
      val card = fx(new Container { size.width := w(); size.height @= 400.0; backgroundColor @= everyui.Color.White })
      val texts = words.map(t => fx(TextView(t)))
      val links = texts.map(t => fx(Link("/" + t)(t)))
      val row = fx(Row(24.0)(links*))
      fx(card.children += Column(8.0, 16.0)(row))
      val one = line()
      val wholes = words.map(whole)
      onScreen(card) {
        texts.zip(wholes).foreach { (t, full) =>
          fx(t.size.height()) shouldBe one
          fx(t.size.width()) shouldBe full +- 0.5
        }
        fx(w @= 390.0)
        settle()
        texts.zip(wholes).foreach { (t, full) =>
          val count = t.content().split(' ').length
          // a word is never broken: as wide as the widest word at the least, as many lines as words at the most
          val widest = t.content().split(' ').map(whole).max
          fx(t.size.width()) should be >= widest - 0.5
          fx(t.size.height()) should be <= one * count + one / 2.0
          fx(t.node.getText()) shouldBe t.content()
        }
        // the words first in the row take the cut, and a word of their own keeps one line
        fx(texts(2).size.height()) shouldBe one
        fx(texts(2).size.width()) shouldBe wholes(2) +- 1.5
        fx(w @= 1000.0)
        settle()
        texts.zip(wholes).foreach { (t, full) =>
          fx(t.size.height()) shouldBe one
          fx(t.size.width()) shouldBe full +- 0.5
        }
      }
    }
    "still break a word too long for a line where its room holds a few letters: a URL in a narrow column" in {
      val url = "https://example.com/a/very/long/path/that/never/has/a/space/in/it/at/all"
      val t = fx(TextView(url))
      val card = fx(new Container { size.width @= 140.0; size.height @= 400.0; children += t })
      onScreen(card) {
        fx(t.size.width()) should be <= 140.5
        fx(t.size.height()) should be > line() * 2.0
      }
    }
  }
}
