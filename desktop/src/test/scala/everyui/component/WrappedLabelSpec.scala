package everyui.component

import everyui.{FxSpec, ui}

/** A label too long for its line wraps, and what is under it and around it makes room: a checkbox's note goes below
  * the label's last line, the box stays beside its first, and the checkbox and a switch grow to hold it all. */
class WrappedLabelSpec extends FxSpec {
  private val long = "Define the log entry data model: place, category (travel, restaurant, experience), review, optional " +
    "timestamp, edit history"

  // its words, not its box (a glyph drawn as a letter)
  private def words(c: Container): Vector[TextView] =
    fx(c.children().collect { case t: TextView if !t.isInstanceOf[MaterialIconView] => t })
  private def bottom(c: Component): Double = fx(c.position.top() + c.size.height())

  "A checkbox" should {
    "put its note under a wrapped label, its box by the first line, and hold both" in {
      val (short, wrapped) = fx {
        ui.children.clear()
        val a = new Checkbox("Set up sign-in", note = Some("added by a teammate"), width = 400.0)
        val b = new Checkbox(long, note = Some("added by a teammate"), width = 400.0)
        ui.children ++= List(a, b)
        (a, b)
      }
      val List(shortTitle, shortNote) = words(short).toList: @unchecked
      val List(title, note) = words(wrapped).toList: @unchecked
      // one line: as it always was
      fx(shortNote.position.top()) shouldBe 24.0
      fx(short.size.height()) shouldBe 24.0 + fx(shortNote.size.height()) + 8.0
      // wrapped: more than one line, kept within the checkbox, the note under it
      fx(title.size.height()) should be > 1.5 * fx(shortTitle.size.height())
      fx(title.position.left() + title.size.width()) should be <= 400.0
      fx(note.position.top()) should be >= bottom(title)
      fx(wrapped.size.height()) should be >= bottom(note)
      // its first line where a one-line label's is, by the box; nothing above the checkbox's top
      fx(title.position.top()) shouldBe fx(shortTitle.position.top()) +- 1.0
      fx(title.position.top()) should be >= 0.0
      fx(ui.children.clear())
    }
    "grow with a wrapped label and no note" in {
      val c = fx {
        val c = new Checkbox(long, width = 300.0)
        ui.children += c
        c
      }
      val title = words(c).head
      fx(c.size.height()) should be >= bottom(title)
      fx(c.size.height()) should be > 28.0
      fx(ui.children.clear())
    }
  }

  "A switch given a width" should {
    "wrap a long label before the switch, and grow to hold it" in {
      val s = fx {
        val s = new Switch(long, width = Some(300.0))
        ui.children += s
        s
      }
      val title = words(s).head
      val track = fx(s.children().collect { case c: Container => c }.head)
      fx(title.position.left() + title.size.width()) should be <= fx(track.position.left())
      fx(title.position.top()) should be >= 0.0
      fx(s.size.height()) should be >= bottom(title)
      fx(track.position.top() + track.size.height() / 2.0) shouldBe fx(s.size.height() / 2.0) +- 0.5
      fx(ui.children.clear())
    }
  }
}
