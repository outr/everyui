package everyui.access

import everyui.component.types.Semantics
import everyui.focus.Focus
import everyui.list.{TextRow, VirtualList}
import everyui.{FxSpec, ui}
import javafx.scene.AccessibleRole
import reactify.Var

/** A list is a list box of options to a screen reader, and the row under its cursor is told as it moves while the list
  * has the focus: the list is named by it on the desktop. */
class ListAccessSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    offStage()
    super.afterAll()
  }

  private val mail = Vector("Lunch on Friday?", "The plan for next week", "Invoice 42")

  "A list" should {
    "be a list box of options, chosen or not, with nothing found by the check" in {
      val list = fx {
        ui.children.clear()
        val list = new VirtualList[String](Var(mail), identity, identity)(() => new TextRow[String](identity))
        list.semantics @= Semantics(label = "Inbox")
        list.size.height @= 200.0
        ui.children += list
        list
      }
      fx(list.node.fx.getAccessibleRole) shouldBe AccessibleRole.LIST_VIEW
      fx(Access.roleOf(list)) shouldBe "listbox"
      val rows = fx(list.children().collect { case r: TextRow[?] @unchecked if r.item().nonEmpty => r }.toList)
      rows.map(r => fx(Access.roleOf(r))).distinct shouldBe List("option")
      fx(list.focus())
      fx(list.current @= Some(mail(1)))
      fx(list.selection.only(mail(1)))
      fx(())
      // a chosen row in a list with the focus, its words over the accent's faint fill, once
      fx(Access.check()) shouldBe Nil
      fx(Focus.focused @= None)
      fx(ui.children.clear())
    }
    "tell the row under its cursor as it moves, while it has the focus" in {
      val list = fx {
        val list = new VirtualList[String](Var(mail), identity, identity)(() => new TextRow[String](identity))
        list.size.height @= 200.0
        ui.children += list
        list
      }
      fx(list.focus())
      fx(list.node.fx.getAccessibleText) shouldBe "Lunch on Friday?"
      fx(list.current @= Some(mail(2)))
      fx(list.node.fx.getAccessibleText) shouldBe "Invoice 42"
      fx(Focus.focused @= None)
      fx(ui.children.clear())
    }
  }
}
