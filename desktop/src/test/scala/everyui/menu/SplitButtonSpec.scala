package everyui.menu

import everyui.component.*
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.{FxSpec, ui}

/** A split button: the main action, and its alternatives dropped under it by the arrow, Down or OK held. */
class SplitButtonSpec extends FxSpec {
  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private def focusedLabel: Option[String] = fx(Focus.focused().collect { case r: MenuRow => r.item.label })

  "A split button" should {
    "do its action, and drop its alternatives from the arrow or on Down, focus and all" in {
      var did: List[String] = Nil
      val b = fx {
        ui.children.clear()
        val b = new SplitButton("Commit", () => did = did :+ "commit",
          List(MenuItem("Commit and Push…", () => did = did :+ "push"), MenuItem("Amend", () => did = did :+ "amend")),
          moreLabel = "More")
        b.position.left @= 100.0
        b.position.top @= 100.0
        ui.children += b
        b.main.focus()
        b
      }
      try {
        press("Enter")
        did shouldBe List("commit")
        // a pointer's click, which takes no focus to the arrow where a pointer is used, and the menu goes back where the
        // focus was as it closes
        fx {
          everyui.event.Pointer.usedAt = everyui.render.Platform.ticks()
          b.more.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0)
          everyui.event.Pointer.usedAt = -1.0e9
        }
        focusedLabel shouldBe Some("Commit and Push…")
        val menu = fx(Focus.focused().get.parent().flatMap(_.parent()).flatMap(_.parent()).flatMap(_.parent()).get)
        fx(menu.position.top()) shouldBe fx(b.absoluteTop + b.size.height() + 4.0)
        press("Enter")
        did shouldBe List("commit", "push")
        fx(Focus.focused()) shouldBe Some(b.main)
        fx(b.more.focus())
        press("ArrowDown")
        focusedLabel shouldBe Some("Commit and Push…")
        press("ArrowDown")
        press("Enter")
        did shouldBe List("commit", "push", "amend")
        fx(Focus.focused()) shouldBe Some(b.more)
      }
      finally fx {
        while (Modal.showing) Modal.close()
        ui.children.clear()
        Focus.focused @= None
      }
    }
  }
}
