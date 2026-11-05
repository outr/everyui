package everyui.focus

import everyui.component.*
import everyui.component.types.Semantics
import everyui.render.Host
import everyui.{FxSpec, ui}
import javafx.scene.AccessibleRole

/** A screen reader follows the focus layer's focus: the window's focus is put on what the app's lands on, which says
  * what it is by its role and name, while a field keeps the window's focus on its own text field. */
class ReaderFocusSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    offStage()
    super.afterAll()
  }

  private def card(name: String): Container = {
    val c = new Container
    c.size.width @= 200.0
    c.size.height @= 60.0
    c.focusable @= true
    c.semantics @= Semantics.button(name)
    c
  }

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      ui.children ++= parts
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def owner(): javafx.scene.Node = fx(Host.scene.getFocusOwner)

  "The window's focus" should {
    "follow the app's from card to card, each read by its role and name" in {
      val (a, b) = fx((card("Inbox"), card("Drafts")))
      onScreen(a, b) {
        fx(a.focus())
        owner() shouldBe fx(a.node.fx)
        fx(a.node.fx.getAccessibleRole) shouldBe AccessibleRole.BUTTON
        fx(a.node.fx.getAccessibleText) shouldBe "Inbox"
        fx(b.focus())
        owner() shouldBe fx(b.node.fx)
        fx(b.node.fx.getAccessibleText) shouldBe "Drafts"
      }
    }
    "stay on a field's own text field, and come to a card from it" in {
      val (a, input) = fx((card("Inbox"), new TextInput("Name")))
      onScreen(a, input) {
        val field = fx(input.children().collectFirst { case i: InputView => i.node.fx }.get)
        fx(input.focus())
        owner() shouldBe field
        fx(a.focus())
        owner() shouldBe fx(a.node.fx)
        fx(input.focus())
        owner() shouldBe field
      }
    }
    "leave a node's role as it was once its semantics are taken away" in {
      val a = fx(card("Inbox"))
      onScreen(a) {
        fx(a.semantics @= Semantics(""))
        fx(a.node.fx.getAccessibleRole) shouldBe AccessibleRole.PARENT
        fx(a.node.fx.getAccessibleText) shouldBe null
      }
    }
  }
}
