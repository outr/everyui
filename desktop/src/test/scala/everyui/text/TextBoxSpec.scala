package everyui.text

import everyui.Color
import everyui.component.{Button, Chip, Column, Container, Component, MaterialIconView, Row, ScrollContainer, TextView}
import everyui.component.types.{Semantics, TextAlign, TextOverflow, WhiteSpace}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.{FxSpec, ui}
import reactify.*

/** Words kept to their box (`TextView.textOverflow`): the room what they are in gives them. Words that run past it
  * are cut short with an ellipsis on one line and wrapped where they wrap, and read whole; words that fit are drawn
  * exactly as before; `TextOverflow.Visible`, on a text view or for the app, draws them whole past it as before. */
class TextBoxSpec extends FxSpec {
  private val long = "A rather long line of words that will not fit in a narrow panel at all"
  private val short = "Short words"

  private def words(value: String): TextView = new TextView {
    content @= value
    font.size @= 14.0
  }

  /** A panel `width` wide at the screen's left, holding `parts` placed by hand. */
  private def panel(width: Double, parts: Component*): Container = new Container {
    size.width @= width
    size.height @= 200.0
    children ++= parts
  }

  // a few turns for the look at the box (afterTurn) and what it lays out again
  private def settle(): Unit = (1 to 6).foreach(_ => fx(()))

  private def whole(value: String): Double = {
    val t = fx(words(value))
    val w = fx(t.size.width())
    fx(t.dispose())
    w
  }

  private def onScreen[T](parts: Component*)(check: => T): T = {
    fx(ui.children ++= parts)
    settle()
    try check
    finally fx {
      parts.foreach(_.dispose())
      ui.children.clear()
    }
  }

  "Words kept to their box" should {
    "be cut short with an ellipsis where one line runs past the panel it is in" in {
      val t = fx(words(long))
      fx(t.position.left @= 10.0)
      onScreen(panel(200.0, t)) {
        fx(t.node.getText()) should endWith("…")
        fx(t.elided()) shouldBe true
        fx(t.size.width()) should be <= 190.0
        fx(t.size.width()) should be > 150.0
        fx(t.content()) shouldBe long
      }
    }

    "be read whole where they are cut, and give the name back once they fit" in {
      val t = fx(words(long))
      val box = fx(panel(200.0, t))
      onScreen(box) {
        fx(t.semantics()) shouldBe Semantics(label = long)
        fx(box.size.width @= 1200.0)
        settle()
        fx(t.node.getText()) shouldBe long
        fx(t.elided()) shouldBe false
        fx(t.semantics()) shouldBe Semantics.Plain
      }
    }

    "leave a name the app gave them" in {
      val t = fx(words(long))
      fx(t.semantics @= Semantics.heading(2))
      onScreen(panel(200.0, t)) {
        fx(t.elided()) shouldBe true
        fx(t.semantics()) shouldBe Semantics.heading(2)
      }
    }

    "draw words that fit exactly as before" in {
      val w = whole(short)
      val t = fx(words(short))
      fx(t.position.left @= 10.0)
      onScreen(panel(200.0, t)) {
        fx(t.node.getText()) shouldBe short
        fx(t.size.width()) shouldBe w
        fx(t.elided()) shouldBe false
        fx(t.semantics()) shouldBe Semantics.Plain
      }
    }

    "draw words that just fit whole" in {
      val w = whole(short)
      val t = fx(words(short))
      onScreen(panel(w, t)) {
        fx(t.node.getText()) shouldBe short
        fx(t.size.width()) shouldBe w
      }
    }

    "be kept to the room a row leaves them, before what comes after them" in {
      val t = fx(words(long))
      val chip = fx(new Chip("live", Color.fromHex("1565c0")))
      val row = fx(Row(8.0, padding = 4.0)(t, chip))
      onScreen(panel(300.0, row)) {
        fx(t.elided()) shouldBe true
        fx(row.size.width()) should be <= 300.0
        fx(chip.position.left() + chip.size.width()) should be <= 296.0
      }
    }

    "be kept to a column's room, less its padding, where the column is sized to them" in {
      val t = fx(words(long))
      val column = fx(Column(4.0, padding = 12.0)(words(short), t))
      fx(column.position.left @= 20.0)
      onScreen(panel(300.0, column)) {
        fx(t.elided()) shouldBe true
        fx(column.position.left() + column.size.width()) should be <= 300.0
        fx(t.size.width()) should be <= (300.0 - 20.0 - 24.0)
      }
    }

    "wrap within the box where they wrap" in {
      val t = fx(words(long))
      fx(t.whiteSpace @= WhiteSpace.Normal)
      val one = fx(t.size.height())
      onScreen(panel(200.0, t)) {
        fx(t.node.getText()) shouldBe long
        fx(t.elided()) shouldBe false
        fx(t.size.width()) should be <= 200.0
        fx(t.size.height()) should be > one * 2.0
      }
    }

    "wrap within the box and cut to their lines" in {
      val t = fx(words(long))
      fx {
        t.whiteSpace @= WhiteSpace.Normal
        t.maxLines @= 2
      }
      val one = fx(t.size.height())
      onScreen(panel(200.0, t)) {
        fx(t.node.getText()) should endWith("…")
        fx(t.size.width()) should be <= 200.0
        fx(t.size.height()) should be > one * 1.5
        fx(t.size.height()) should be <= one * 2.5
      }
    }

    "cut a button's words where the button runs past its box" in {
      val button = fx(new Button(long))
      onScreen(panel(240.0, button)) {
        fx(button.size.width()) should be <= 240.0
        fx(button.size.width()) should be > 200.0
      }
    }

    "leave a button that fits as it was" in {
      val alone = fx(new Button("Open dialog"))
      fx(ui.children += alone)
      settle()
      val w = fx(alone.size.width())
      fx {
        alone.dispose()
        ui.children.clear()
      }
      val button = fx(new Button("Open dialog"))
      onScreen(panel(240.0, button)) {
        fx(button.size.width()) shouldBe w
      }
    }

    "cut a button's words in a column sized to them, in a card given its width after its layout" in {
      // Voidcraft's question card: a padded column given a width once its layout had sized it to what it holds
      val long = fx(new Button("Proceed with the Voidcraft cleanup (Firebase key is already moved to `.env.firebase`)"))
      val short = fx(new Button("Skip"))
      val card = fx {
        val c = Column(10.0, padding = 16.0)(words("A question with a title that wraps"), Column(8.0)(long, short))
        c.size.width := 360.0
        c
      }
      onScreen(card) {
        fx(long.size.width()) should be <= (360.0 - 32.0)
        fx(long.size.width()) should be > 300.0
        fx(short.size.width()) shouldBe fx(new Button("Skip").size.width())
      }
    }

    "wrap a button's words within its room where it is given lines, the button as tall as they are" in {
      val words = "Keep the old key where it is for now, and remind me again tomorrow morning"
      val one = fx(new Button("Skip"))
      fx(ui.children += one)
      settle()
      val (skipWidth, lineHeight) = fx((one.size.width(), one.size.height()))
      fx {
        one.dispose()
        ui.children.clear()
      }
      val wrapped = fx(new Button(words))
      val two = fx(new Button(words))
      val short = fx(new Button("Skip"))
      fx {
        wrapped.labelLines @= 0
        two.labelLines @= 2
        short.labelLines @= 0
      }
      val card = fx {
        val c = Column(8.0, padding = 16.0)(wrapped, two, short)
        c.size.width := 260.0
        c
      }
      onScreen(card) {
        fx(wrapped.size.width()) should be <= 228.0
        fx(wrapped.size.height()) should be > lineHeight * 1.8
        fx(wrapped.radius()) should be < fx(wrapped.size.height()) / 2.0
        fx(two.size.width()) should be <= 228.0
        fx(two.size.height()) should be <= fx(wrapped.size.height())
        fx(two.size.height()) should be > lineHeight * 1.5
        // words that fit on a line: as on one
        fx(short.size.width()) shouldBe skipWidth
        fx(short.size.height()) shouldBe lineHeight
        // wrapped lines start where a paragraph's do, unless the button says otherwise
        def wordsOf(b: Button): TextView = {
          def find(c: Component): Option[TextView] = c match {
            case t: TextView => Some(t)
            case _ => c.childComponents.iterator.map(find).collectFirst { case Some(t) => t }
          }
          find(b).get
        }
        fx(wordsOf(wrapped).align()) shouldBe TextAlign.Start
        fx(wrapped.labelAlign @= TextAlign.Center)
        fx(wordsOf(wrapped).align()) shouldBe TextAlign.Center
        fx(wrapped.labelAlign @= TextAlign.Start)
        // the focus ring follows the box
        fx(wrapped.focus())
        settle()
        fx(wrapped.borderWidth()) should be > 0.0
        // and back to one line, cut short
        fx(wrapped.labelLines @= 1)
        settle()
        fx(wrapped.size.height()) shouldBe lineHeight
        fx(wrapped.size.width()) should be <= 228.0
      }
    }

    "wrap a button's words in a right-to-left language within its room" in {
      fx(Direction.set(true))
      try {
        val b = fx(new Button("Keep the old key where it is for now, and remind me again tomorrow morning"))
        fx(b.labelLines @= 0)
        val card = fx {
          val c = Column(8.0, padding = 16.0)(b)
          c.size.width := 260.0
          c
        }
        onScreen(card) {
          fx(b.position.left()) should be >= 16.0
          fx(b.position.left() + b.size.width()) should be <= 244.5
          fx(b.size.height()) should be > 40.0
        }
      }
      finally fx(Direction.set(false))
    }

    "cut a chip's word to the room around it" in {
      val chip = fx(new Chip(long, Color.fromHex("1565c0")))
      onScreen(panel(160.0, chip)) {
        fx(chip.size.width()) should be <= 160.0
      }
    }

    "be drawn whole again once the box grows" in {
      val t = fx(words(long))
      val box = fx(panel(200.0, t))
      onScreen(box) {
        fx(t.elided()) shouldBe true
        fx(box.size.width @= 150.0)
        settle()
        fx(t.size.width()) should be <= 150.0
        fx(box.size.width @= 2000.0)
        settle()
        fx(t.node.getText()) shouldBe long
        fx(t.size.width()) shouldBe whole(long)
      }
    }

    "follow their words as they change" in {
      val t = fx(words(short))
      onScreen(panel(200.0, t)) {
        fx(t.elided()) shouldBe false
        fx(t.content @= long)
        settle()
        fx(t.elided()) shouldBe true
        fx(t.size.width()) should be <= 200.0
        fx(t.content @= short)
        settle()
        fx(t.node.getText()) shouldBe short
        fx(t.size.width()) shouldBe whole(short)
      }
    }

    "leave words given a width of their own, and a glyph, alone" in {
      val sized = fx(words(long))
      fx(sized.size.width @= 600.0)
      val glyph = fx(new MaterialIconView(MaterialIcon.Home, 48.0))
      fx(glyph.position.left @= 30.0)
      onScreen(panel(40.0, sized, glyph)) {
        fx(sized.node.getText()) shouldBe long
        fx(sized.size.width()) shouldBe 600.0
        fx(glyph.node.getText()) shouldBe MaterialIcon.Home.glyph
      }
    }

    "leave words along a rail, which scrolls, whole" in {
      val names = fx((1 to 8).map(_ => words(long)))
      val rail = fx(new ScrollContainer(horizontal = true))
      fx {
        rail.size.width @= 300.0
        rail.size.height @= 40.0
        rail.content.children += Row(12.0)(names*)
      }
      onScreen(rail) {
        names.foreach(n => fx(n.node.getText()) shouldBe long)
      }
    }

    "leave words in something hidden, or placed past the box, whole until they are drawn there" in {
      val hidden = fx(words(long))
      val box = fx {
        val made = panel(200.0, hidden)
        made.visible @= false
        made
      }
      val past = fx(words(long))
      fx(past.position.left @= 300.0)
      onScreen(box, panel(200.0, past)) {
        fx(hidden.node.getText()) shouldBe long
        fx(past.node.getText()) shouldBe long
        fx(box.visible @= true)
        settle()
        fx(hidden.elided()) shouldBe true
      }
    }

    "draw them whole in something sized to them" in {
      val t = fx(words(long))
      fx(t.position.left @= 4.0)
      val sized = fx(new Container {
        size.width := t.size.width() + 8.0
        size.height @= 30.0
        children += t
      })
      onScreen(sized) {
        fx(t.node.getText()) shouldBe long
      }
    }

    "draw them whole, rather than shrink them away, where a box follows them out of its sight" in {
      val t = fx(words(long))
      fx(t.position.left @= 4.0)
      // bound to the words through a value of its own, which the box cannot see is the words' width
      val reach: Val[Double] = fx(Val(t.size.width() - 20.0))
      val sized = fx(new Container {
        size.width := reach()
        size.height @= 30.0
        children += t
      })
      onScreen(sized) {
        settle()
        fx(t.node.getText()) shouldBe long
      }
    }

    "be kept to their box from its start in a right-to-left language" in {
      fx(Direction.set(true))
      try {
        val t = fx(words(long))
        val row = fx(Row(8.0)(t, words(short)))
        val box = fx(panel(300.0, row))
        fx(row.position.left := box.size.width() - row.size.width() - 10.0)
        onScreen(box) {
          fx(t.elided()) shouldBe true
          fx(row.position.left()) should be >= 0.0
          fx(row.size.width()) should be <= 290.0
        }
      }
      finally fx(Direction.set(false))
    }

    "draw them whole past the box with TextOverflow.Visible" in {
      val t = fx(words(long))
      fx(t.textOverflow @= TextOverflow.Visible)
      onScreen(panel(200.0, t)) {
        fx(t.node.getText()) shouldBe long
        fx(t.size.width()) shouldBe whole(long)
        fx(t.textOverflow @= TextOverflow.Fit)
        settle()
        fx(t.elided()) shouldBe true
        fx(t.textOverflow @= TextOverflow.Visible)
        settle()
        fx(t.node.getText()) shouldBe long
      }
    }

    "draw every text view made after TextOverflow.default is Visible whole, as before" in {
      fx(TextOverflow.default @= TextOverflow.Visible)
      try {
        val t = fx(words(long))
        val button = fx(new Button(long))
        fx(button.position.top @= 40.0)
        onScreen(panel(200.0, t, button)) {
          fx(t.node.getText()) shouldBe long
          fx(button.size.width()) should be > 400.0
        }
      }
      finally fx(TextOverflow.default @= TextOverflow.Fit)
    }
  }
}
