package everyui.focus

import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Button, Component, Container, TextInput}
import everyui.keymap.Keymap
import everyui.list.{ListRow, VirtualList}
import everyui.net.URLPath
import everyui.render.{Host, Platform}
import everyui.task.Frames
import everyui.tooltip.Tooltip
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}
import reactify.*

/** The focus where a pointer moves it, as on a web page: a click focuses only what is typed into or worked by the keys,
  * and lets the focus go otherwise; the focus is drawn only while the keys move it; a screen starts with nothing
  * focused unless it starts at a field; the focus taken off the screen goes nowhere unless the keys are moving it, and
  * an arrow with nothing focused is the page's. Where a remote moves the focus, none of that changes. Real JavaFX mouse
  * events through the desktop backend's nodes, and keys through the focus layer as the backend hands them over. */
class PointerFocusSpec extends FxSpec {
  /** Pressed, released and clicked at (`x`, `y`) on `on`, the scene's place, as a mouse does. */
  private def click(on: Component, x: Double, y: Double): Unit = fx {
    List(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED).foreach { kind =>
      javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false,
        false, false, kind == MouseEvent.MOUSE_PRESSED, false, false, false, false, true, null))
    }
  }

  private def key(name: String): Boolean = fx {
    val taken = Focus.key(name, true)
    Focus.key(name, false)
    taken
  }

  private def placed[C <: Component](c: C, left: Double, top: Double): C = {
    c.position.left @= left
    c.position.top @= top
    c
  }

  private def blank(): Container = new Container {
    size.width @= 200.0
    size.height @= 100.0
  }

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      Focus.scope @= None
      Focus.visible @= false
      val screen = new Container
      screen.size.width @= 1200.0
      screen.size.height @= 800.0
      screen.children ++= parts
      ui.children += screen
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
      Focus.visible @= false
    }
  }

  "A click" should {
    "leave a button unfocused and undrawn, and take a field's focus from it" in {
      val (field, button) = fx((placed(new TextInput("Name"), 0.0, 0.0), placed(new Button("Save"), 0.0, 200.0)))
      onScreen(field, button) {
        click(field, 10.0, 10.0)
        fx(Focus.focused()) shouldBe Some(field)
        click(button, 10.0, 210.0)
        fx(Focus.focused()) shouldBe None
        fx(button.focused()) shouldBe false
        fx(button.focusShown()) shouldBe false
        fx(button.borderWidth()) shouldBe 0.0
      }
    }
    "on the page's empty space let a field's focus go" in {
      val (field, space) = fx((placed(new TextInput("Name"), 0.0, 0.0), placed(blank(), 0.0, 400.0)))
      onScreen(field, space) {
        click(field, 10.0, 10.0)
        fx(Focus.focused()) shouldBe Some(field)
        click(space, 10.0, 410.0)
        fx(Focus.focused()) shouldBe None
      }
    }
    "focus a list, whose keys still move its cursor, drawn only once they do" in {
      val list = fx(placed(new VirtualList[String](Var(Vector("a", "b", "c", "d")), s => s, s => s)(() => new ListRow[String]) {
        size.width @= 300.0
        size.height @= 400.0
      }, 0.0, 0.0))
      onScreen(list) {
        click(list, 10.0, list.rowHeight * 1.5)
        fx(Focus.focused()) shouldBe Some(list)
        fx(list.current()) shouldBe Some("b")
        fx(list.focusShown()) shouldBe false
        fx(list.rowAt(1).exists(_.cursorShown())) shouldBe false
        key("ArrowDown") shouldBe true
        fx(list.current()) shouldBe Some("c")
        fx(Focus.visible()) shouldBe true
        fx(list.rowAt(2).exists(r => r.current() && r.cursorShown())) shouldBe true
      }
    }
  }

  "The keys" should {
    "draw the focus from Tab, and a pointer put down hides it again" in {
      val (one, two, space) = fx((placed(new Button("One"), 0.0, 0.0), placed(new Button("Two"), 200.0, 0.0),
        placed(blank(), 0.0, 400.0)))
      onScreen(one, two, space) {
        click(one, 10.0, 10.0)
        fx(Focus.focused()) shouldBe None
        key("Tab") shouldBe true
        fx(Focus.focused()) shouldBe Some(one)
        fx(one.focusShown()) shouldBe true
        fx(one.borderWidth()) shouldBe 2.5
        key("Tab")
        fx(two.focusShown()) shouldBe true
        click(space, 10.0, 410.0)
        fx(Focus.visible()) shouldBe false
        fx(two.focusShown()) shouldBe false
        fx(Focus.focused()) shouldBe None
      }
    }
    "leave an arrow with nothing focused to the page, until they move the focus" in {
      val (one, two) = fx((placed(new Button("One"), 0.0, 0.0), placed(new Button("Two"), 200.0, 0.0)))
      onScreen(everyui.component.Row(10.0)(one, two)) {
        key("ArrowRight") shouldBe false
        fx(Focus.focused()) shouldBe None
        fx(Focus.visible()) shouldBe false
        key("Tab") shouldBe true
        key("ArrowRight") shouldBe true
        fx(Focus.focused()) shouldBe Some(two)
      }
    }
  }

  "The focus taken off the screen" should {
    "go nowhere after a pointer, and to what took its place after the keys" in {
      val (one, two, three) = fx((new Button("One"), new Button("Two"), new Button("Three")))
      val row = fx(everyui.component.Row(10.0)(one, two, three))
      onScreen(row) {
        fx {
          two.focus()
          row.children -= two
        }
        fx(Focus.focused()) shouldBe None
        fx(row.children += two)
        key("Tab")
        key("Tab")
        fx(Focus.focused()) shouldBe Some(three)
        fx(row.children -= three)
        fx(Focus.focused()) shouldBe Some(two)
      }
    }
  }

  "A tip" should {
    "not follow a focus the keys did not bring, and follow one they did" in {
      val delay = fx(Tooltip.delay())
      val (one, two) = fx((new Button("One"), new Button("Two")))
      var made = List.empty[String]
      onScreen(everyui.component.Row(10.0)(one, two)) {
        try {
          fx {
            Tooltip.delay @= 0.0
            Tooltip.rich(one)(() => { made = made :+ "one"; new Container })
            Tooltip.rich(two)(() => { made = made :+ "two"; new Container })
            one.focus()
          }
          // the tip's turn, had it been asked for, has come by this one
          fx(made) shouldBe Nil
          fx(Tooltip.shownFor) shouldBe None
          key("Tab")
          fx(Focus.focused()) shouldBe Some(two)
          fx(made) shouldBe List("two")
          fx(Tooltip.shownFor) shouldBe Some(two)
        }
        finally fx {
          Tooltip.hide()
          Tooltip.delay @= delay
        }
      }
    }
  }

  // a page whose start is a button, or a field
  private class Page(val manager: ScreenManager, val id: Int, field: Boolean) extends ScrollScreen {
    override def title: String = "Page " + id
    override def path: URLPath = URLPath("/pointer/" + id)
    override def oneShowing: Boolean = true
    val first: Component = if (field) new TextInput("Search") else new Button("First")
    val second = new Button("Second")

    override def defaultFocus(): Component = first

    override def createUI(): Task[Unit] = Task {
      body.children ++= List(first, second)
    }
  }

  private object Manager extends ScreenManager

  "A screen shown" should {
    "start with nothing focused, unless it starts at a field" in {
      try {
        val home = fx(new Page(Manager, 0, field = false))
        fx {
          ui.children.clear()
          Focus.focused @= None
          Focus.visible @= false
          Platform.pushPath("/pointer/0")
          Manager.run(home)
        }
        eventually(Manager.active().exists(_ eq home)) shouldBe true
        fx(Focus.focused()) shouldBe None
        val search = fx(new Page(Manager, 1, field = true))
        fx(Manager.show(search).startUnit())
        eventually(Manager.active().exists(_ eq search)) shouldBe true
        eventually(Focus.focused().exists(_ eq search.first)) shouldBe true
        // with the keys moving the focus, a screen starts where it says, button or not
        val keyed = fx(new Page(Manager, 2, field = false))
        fx(Focus.visible @= true)
        fx(Manager.show(keyed).startUnit())
        eventually(Manager.active().exists(_ eq keyed)) shouldBe true
        eventually(Focus.focused().exists(_ eq keyed.first)) shouldBe true
        fx(keyed.first.focusShown()) shouldBe true
      }
      finally fx {
        Frames.drive(_ => (), () => false, () => (), _ => ())
        ui.children.clear()
        Focus.focused @= None
        Focus.visible @= false
        Focus.home @= (() => None)
        Focus.page @= (() => None)
        Keymap.shown @= (() => None)
        ScreenManager.running = None
      }
    }
  }

  "Where a remote moves the focus" should {
    "click, draw, move on and start as ever" in {
      val was = fx(Host.args)
      fx(Host.args = was + ("remote" -> "true"))
      try {
        val (one, two, three) = fx((placed(new Button("One"), 0.0, 0.0), placed(new Button("Two"), 200.0, 0.0),
          placed(new Button("Three"), 400.0, 0.0)))
        val row = fx(everyui.component.Row(10.0)(one, two, three))
        onScreen(row) {
          fx(Focus.home @= (() => Some(one)))
          // an arrow with nothing focused starts the focus
          key("ArrowRight") shouldBe true
          fx(Focus.focused()) shouldBe Some(one)
          fx(one.focusShown()) shouldBe true
          // a click focuses the button and draws it, though no key moved the focus
          fx(Focus.visible @= false)
          click(two, 210.0, 10.0)
          fx(Focus.focused()) shouldBe Some(two)
          fx(two.focusShown()) shouldBe true
          fx(two.borderWidth()) shouldBe 2.5
          // taken off the screen, the focus goes to what took its place
          fx(row.children -= two)
          fx(Focus.focused()) shouldBe Some(three)
        }
      }
      finally fx {
        Focus.home @= (() => None)
        Host.args = was
      }
    }
  }
}
