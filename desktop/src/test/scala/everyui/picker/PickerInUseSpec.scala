package everyui.picker

import everyui.async.Task
import everyui.component.*
import everyui.focus.Focus
import everyui.list.ListRow
import everyui.render.Nodes
import everyui.theme.Theme
import everyui.{FxSpec, ui}

/** A picker's one in use (`inUse`): its row ringed in the accent on a faint accent ground, its words in the accent,
  * the cursor on it as the picker opens, and on the first match once words narrow the list. */
class PickerInUseSpec extends FxSpec {
  private def onScreen[T](test: => T): T = {
    fx {
      ui.children.clear()
      val c = new Component(Nodes.container())
      c.focusable @= true
      c.size.width @= 100.0
      c.size.height @= 40.0
      ui.children += c
      c.focus()
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private val models = Vector("Auto", "Haiku", "Sonnet", "Opus")

  private def picker(inUse: String => Boolean): FilterPicker[String] = {
    val p = fx(new FilterPicker[String](q => Task.pure(models.filter(_.toLowerCase.contains(q.toLowerCase))), identity,
      identity, debounce = 0.0, inUse = inUse))
    fx(p.open())
    eventually(p.list.count == models.size) shouldBe true
    p
  }

  private def row(p: FilterPicker[String], name: String): ListRow[String] =
    fx(p.list.rows.find(_.item().contains(name)).get)

  "A picker's one in use" should {
    "be ringed in the accent on a faint accent ground, its words in the accent" in onScreen {
      val p = picker(_ == "Sonnet")
      val sonnet = row(p, "Sonnet")
      val haiku = row(p, "Haiku")
      fx(sonnet.inUse()) shouldBe true
      fx(sonnet.borderWidth()) shouldBe ListRow.inUseRing
      fx(sonnet.borderColor()) shouldBe Theme.accent()
      fx(haiku.inUse()) shouldBe false
      fx(haiku.borderWidth()) shouldBe 0.0
      fx(haiku.backgroundColor()) shouldBe everyui.Color.Clear
      def words(r: ListRow[String]): everyui.Color = fx(r.children().collectFirst { case t: RichText => t.color() }.get)
      words(sonnet) shouldBe Theme.accent()
      words(haiku) shouldBe Theme.text()
    }
    "have the cursor as the picker opens, and the first match once words narrow the list" in onScreen {
      val p = picker(_ == "Opus")
      fx(p.list.current()) shouldBe Some("Opus")
      fx(p.words @= "o")
      eventually(p.list.count == 3) shouldBe true
      fx(p.list.current()) shouldBe Some("Auto")
    }
    "leave the cursor on the first with none in use" in onScreen {
      val p = picker(_ => false)
      fx(p.list.current()) shouldBe Some("Auto")
      fx(p.list.rows.exists(_.inUse())) shouldBe false
    }
  }
}
