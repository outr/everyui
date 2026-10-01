package everyui.kit

import everyui.component.{Component, Container, MaterialIconView, TextView}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.theme.UiScale
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}

/** − / 100% / +: a click on − or + a step, on the percentage back to 100%, the ends faded where the steps end; with the
  * focus, Left and Right (mirrored right to left), - and + a step, Enter back to 100%, Up and Down left to the focus. */
class ScaleControlSpec extends FxSpec {
  private def press(code: KeyCode, text: String = ""): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, false, false, false, false))
  }

  // (on the FX thread) the part drawing `glyph`, and the words saying the size
  private def part(c: ScaleControl, glyph: MaterialIcon): Container = c.children().collect {
    case p: Container if p.children().exists { case m: MaterialIconView => m.icon() == glyph; case _ => false } => p
  }.head
  private def middle(c: ScaleControl): Container = c.children().collect {
    case p: Container if p.children().exists(t => t.isInstanceOf[TextView] && !t.isInstanceOf[MaterialIconView]) => p
  }.head
  private def words(c: ScaleControl): String = middle(c).children().collect { case t: TextView => t.content() }.head
  private def click(c: Component): Unit = fx(c.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      UiScale.reset()
      Direction.set(false)
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A scale control" should {
    "step with − and +, go back to 100% from the percentage, and fade each end where the steps end" in {
      val c = fx(new ScaleControl("Size"))
      onScreen(c) {
        fx(words(c)) shouldBe "100%"
        click(fx(part(c, MaterialIcon.Add)))
        fx((UiScale(), words(c))) shouldBe ((1.1, "110%"))
        click(fx(part(c, MaterialIcon.Remove)))
        click(fx(part(c, MaterialIcon.Remove)))
        fx((UiScale(), words(c))) shouldBe ((0.9, "90%"))
        click(fx(middle(c)))
        fx((UiScale(), words(c))) shouldBe ((1.0, "100%"))
        // laid out the same however large the app is drawn: − , the percentage, +
        fx(c.size.width()) shouldBe ScaleControl.width
        fx(part(c, MaterialIcon.Remove).position.left()) shouldBe 0.0
        fx(part(c, MaterialIcon.Add).position.left()) shouldBe ScaleControl.width - ScaleControl.height
        (1 to 12).foreach(_ => click(fx(part(c, MaterialIcon.Add))))
        fx(UiScale()) shouldBe 2.0
        fx(part(c, MaterialIcon.Add).children().collect { case m: MaterialIconView => m.opacity() }.head) shouldBe 0.5
        fx(part(c, MaterialIcon.Remove).children().collect { case m: MaterialIconView => m.opacity() }.head) shouldBe 1.0
        fx(c.semantics().range.map(_.text)) shouldBe Some("200%")
      }
    }
    "take a real click on + as a step, though it changes the size the click is read over as it climbs" in {
      val c = fx(new ScaleControl())
      onScreen(c) {
        val plus = fx(part(c, MaterialIcon.Add))
        def clickPlus(): Unit = fx {
          val at = plus.node.fx.localToScene(ScaleControl.height / 2.0, ScaleControl.height / 2.0)
          javafx.event.Event.fireEvent(plus.node.fx, new javafx.scene.input.MouseEvent(
            javafx.scene.input.MouseEvent.MOUSE_CLICKED, at.getX, at.getY, at.getX, at.getY,
            javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, true,
            null))
        }
        clickPlus()
        fx(UiScale()) shouldBe 1.1
        clickPlus()
        fx(UiScale()) shouldBe 1.2
      }
    }
    "step with Left and Right, - and +, go back to 100% on Enter, and leave Up and Down to the focus" in {
      val c = fx(new ScaleControl())
      onScreen(c) {
        fx(c.focus())
        press(KeyCode.RIGHT)
        press(KeyCode.RIGHT)
        fx(UiScale()) shouldBe 1.2
        press(KeyCode.LEFT)
        fx(UiScale()) shouldBe 1.1
        press(KeyCode.MINUS, "-")
        press(KeyCode.MINUS, "-")
        fx(UiScale()) shouldBe 0.9
        press(KeyCode.EQUALS, "=")
        fx(UiScale()) shouldBe 1.0
        press(KeyCode.PLUS, "+")
        fx(UiScale()) shouldBe 1.1
        press(KeyCode.ENTER)
        fx(UiScale()) shouldBe 1.0
        fx(c.keepsEvent(everyui.event.KeyEvent(everyui.event.Key.ArrowDown, "ArrowDown"))) shouldBe false
        fx(c.keepsEvent(everyui.event.KeyEvent(everyui.event.Key.ArrowRight, "ArrowRight"))) shouldBe true
        fx(Focus.focused().exists(_ eq c)) shouldBe true
      }
    }
    "run right to left with + at the left, and Left making it larger" in {
      val c = fx {
        Direction.set(true)
        new ScaleControl()
      }
      onScreen(c) {
        fx(part(c, MaterialIcon.Add).position.left()) shouldBe 0.0
        fx(part(c, MaterialIcon.Remove).position.left()) shouldBe ScaleControl.width - ScaleControl.height
        fx(c.focus())
        press(KeyCode.LEFT)
        fx(UiScale()) shouldBe 1.1
        press(KeyCode.RIGHT)
        press(KeyCode.RIGHT)
        fx(UiScale()) shouldBe 0.9
      }
    }
  }
}
