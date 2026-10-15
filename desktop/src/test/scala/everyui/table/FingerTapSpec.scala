package everyui.table

import everyui.component.{Component, Container, ScrollContainer}
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}

/** A finger and a mouse on the same table, on a desktop with a touch screen (a touch laptop), told apart by each
  * press, not the device: a finger's tap adds a row to those chosen or takes it away, as on a phone, while a mouse's
  * click chooses that row alone, as on a desktop. The finger comes to JavaFX as the mouse, made from the touch
  * (synthesized), as a touch screen's does. */
class FingerTapSpec extends FxSpec {
  private val rows = Vector(Vector("Grace", "1"), Vector("ada", "2"), Vector("Alan", "3"), Vector("Barbara", "4"))

  private def grid(t: DataTable): Container = t.children().collectFirst { case s: ScrollContainer => s }.get
    .content.children().head.asInstanceOf[Container]
  private def body(t: DataTable): Container = grid(t).children().collectFirst { case s: ScrollContainer => s }.get
    .content.children().head.asInstanceOf[Container]
  private def row(t: DataTable, name: String): DataRow =
    body(t).children().collectFirst { case r: DataRow if r.content.head.text == name => r }.get

  // a press, its release and its click on `cell`, as one turn of the FX thread
  private def click(t: DataTable, cell: Component, finger: Boolean): Unit = fx {
    val x = cell.absoluteLeft + 2.0
    val y = cell.absoluteTop + 2.0
    List(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED).foreach { kind =>
      javafx.event.Event.fireEvent(grid(t).node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false,
        false, false, false, true, false, false, finger, false, kind == MouseEvent.MOUSE_CLICKED, null))
    }
  }

  "A table on a touch laptop" should {
    "add the rows a finger taps, and choose the one a mouse clicks alone" in {
      val t = fx(DataTable(Vector(DataColumn("Name"), DataColumn("Count")), rows))
      fx {
        ui.children.clear()
        t.size.width @= 600.0
        ui.children += t
      }
      try {
        eventually(body(t).children().size == 4 && row(t, "Alan").size.height() > 0.0) shouldBe true
        click(t, fx(row(t, "ada").cells(0)), finger = true)
        click(t, fx(row(t, "Barbara").cells(1)), finger = true)
        fx(t.selection()) shouldBe Set(1, 3)
        // a second tap takes it away
        click(t, fx(row(t, "ada").cells(0)), finger = true)
        fx(t.selection()) shouldBe Set(3)
        // the mouse chooses the one it clicks, alone, on the same table
        click(t, fx(row(t, "Grace").cells(0)), finger = false)
        fx(t.selection()) shouldBe Set(0)
        click(t, fx(row(t, "Alan").cells(0)), finger = false)
        fx(t.selection()) shouldBe Set(2)
        // and a finger after it adds again
        click(t, fx(row(t, "Grace").cells(1)), finger = true)
        fx(t.selection()) shouldBe Set(0, 2)
      } finally fx {
        ui.children.clear()
        Focus.focused @= None
      }
    }
  }
}
