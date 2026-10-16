package everyui.event

import everyui.async.Task
import everyui.component.*
import everyui.dialog.Dialogs
import everyui.focus.Focus
import everyui.menu.{Menu, MenuItem}
import everyui.picker.FilterPicker
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import javafx.scene.control.TextInputControl
import javafx.scene.input.{MouseButton, MouseEvent}
import javafx.scene.{Node, Scene}
import reactify.*

/** A click outside a dialog, a menu or a picker closes it only when the press and the release both landed outside it:
  * a drag that selected words in a field and ended over the dim around the dialog leaves it up, with the words still
  * selected, and so does a press outside let go on it, which does nothing in it either. The mouse goes through the
  * scene's own handling (its picking, and the click it makes of a press and a release), as a window's does. */
class PressOwnerSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    offStage()
    super.afterAll()
  }

  // the scene's listener for what the toolkit hears from the window, made on the FX thread
  private lazy val peer: (AnyRef, java.lang.reflect.Method) = {
    val kind = Class.forName("javafx.scene.Scene$ScenePeerListener")
    val make = kind.getDeclaredConstructor(classOf[Scene])
    make.setAccessible(true)
    val heard = kind.getDeclaredMethods.find(_.getName == "mouseEvent").get
    heard.setAccessible(true)
    (make.newInstance(window).asInstanceOf[AnyRef], heard)
  }

  private def mouse(kind: javafx.event.EventType[MouseEvent], x: Double, y: Double, held: Boolean): Unit = {
    val (listener, heard) = peer
    val button = if (kind == MouseEvent.MOUSE_MOVED) MouseButton.NONE else MouseButton.PRIMARY
    val no = java.lang.Boolean.FALSE
    heard.invoke(listener, kind, Double.box(x), Double.box(y), Double.box(x), Double.box(y), button, no, no, no, no,
      no, no, Boolean.box(held), no, no, no, no)
  }

  /** The primary button pressed at `from`, dragged to `to` in steps and let go there, each on a turn of its own. */
  private def drag(from: (Double, Double), to: (Double, Double)): Unit = {
    fx(mouse(MouseEvent.MOUSE_MOVED, from._1, from._2, held = false))
    fx(mouse(MouseEvent.MOUSE_PRESSED, from._1, from._2, held = true))
    (1 to 6).foreach { s =>
      fx(mouse(MouseEvent.MOUSE_DRAGGED, from._1 + (to._1 - from._1) * s / 6.0, from._2 + (to._2 - from._2) * s / 6.0,
        held = true))
    }
    fx(mouse(MouseEvent.MOUSE_RELEASED, to._1, to._2, held = false))
  }

  /** The middle of `fx` in the scene. */
  private def middle(fx: Node): (Double, Double) = {
    val b = fx.localToScene(fx.getBoundsInLocal)
    (b.getMinX + b.getWidth / 2.0, b.getMinY + b.getHeight / 2.0)
  }

  /** A point in `fx`, `dx` in from its left. */
  private def in(fx: Node, dx: Double): (Double, Double) = {
    val b = fx.localToScene(fx.getBoundsInLocal)
    (b.getMinX + dx, b.getMinY + b.getHeight / 2.0)
  }

  // well away from every dialog, menu and picker here: the sheet under them
  private val outside: (Double, Double) = (20.0, 1060.0)

  private def field(in: Node): TextInputControl = in match {
    case t: TextInputControl => t
    case p: javafx.scene.Parent =>
      p.getChildrenUnmodifiable.toArray.iterator.collect { case n: Node => n }.map(n => scala.util.Try(field(n)))
        .collectFirst { case scala.util.Success(t) => t }.getOrElse(throw new NoSuchElementException("no field"))
    case _ => throw new NoSuchElementException("no field")
  }

  /** Once what was put up has been laid out: its sheet the window's size, as it is long before anybody can press on it. */
  private def settled(): Unit =
    eventually(Modal.top.flatMap(_.parent()).exists(_.node.fx.getLayoutBounds.getWidth >= 1900.0)) shouldBe true

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

  /** A prompt up with words in its field, and its answer caught. */
  private def prompt(): (Var[Option[Option[String]]], Container, TextInputControl) = {
    val answer: Var[Option[Option[String]]] = Var(None)
    fx(Dialogs.prompt("Rename", initial = "a few words to select").map(v => answer @= Some(v)).start())
    settled()
    val panel = fx(Modal.top.get)
    (answer, panel, fx(field(panel.node.fx)))
  }

  "A dialog" should {
    "stay up after words in its field are selected by a drag let go over the dim around it" in onScreen {
      val (answer, _, input) = prompt()
      val start = fx(in(input, 60.0))
      // a click in the words first, a little way off, which puts the caret there in place of the words selected as
      // it opened (a press in a selection drags the words, rather than selecting more)
      val first = fx(in(input, 30.0))
      drag(first, first)
      drag(start, (outside._1, start._2))
      fx(Modal.showing) shouldBe true
      fx(input.getSelectedText.nonEmpty) shouldBe true
      answer() shouldBe None
    }
    "close for a press and a release both on the dim around it" in onScreen {
      val (answer, _, _) = prompt()
      drag(outside, (outside._1 + 4.0, outside._2 - 4.0))
      fx(Modal.showing) shouldBe false
      eventually(answer().contains(None)) shouldBe true
    }
    "stay up for a press on the dim let go on its button, which it does not press" in onScreen {
      val (answer, panel, _) = prompt()
      val ok = fx(panel.asInstanceOf[everyui.dialog.DialogPanel].defaultButton.get)
      drag(outside, fx(middle(ok.node.fx)))
      fx(Modal.showing) shouldBe true
      answer() shouldBe None
    }
  }

  "A menu" should {
    var chosen: List[String] = Nil
    def open(): Menu = {
      val menu = fx {
        chosen = Nil
        Menu.at(300.0, 300.0, List("Cut", "Copy", "Paste").map(s => MenuItem(s, () => chosen = chosen :+ s)))
      }
      settled()
      menu
    }

    "stay up for a press on an entry let go outside it, choosing nothing" in onScreen {
      val menu = open()
      drag(fx(middle(menu.rows.head.node.fx)), outside)
      fx(Modal.showing) shouldBe true
      chosen shouldBe Nil
    }
    "close for a press and a release both outside it" in onScreen {
      open()
      drag(outside, (outside._1 + 4.0, outside._2 - 4.0))
      fx(Modal.showing) shouldBe false
      chosen shouldBe Nil
    }
    "stay up for a press outside let go on an entry, which it does not choose" in onScreen {
      val menu = open()
      drag(outside, fx(middle(menu.rows(1).node.fx)))
      fx(Modal.showing) shouldBe true
      chosen shouldBe Nil
    }
    "choose an entry clicked, as ever" in onScreen {
      val menu = open()
      val at = fx(middle(menu.rows(1).node.fx))
      drag(at, at)
      fx(Modal.showing) shouldBe false
      chosen shouldBe List("Copy")
    }
  }

  "A filtering picker" should {
    var picked: List[String] = Nil
    def open(): FilterPicker[String] = {
      val picker = fx(new FilterPicker[String](_ => Task.pure(Vector.tabulate(20)(i => "Model " + i)), identity,
        identity, debounce = 0.0))
      fx {
        picked = Nil
        picker.chosen.attach(s => picked = picked :+ s)
        picker.open("Model")
      }
      eventually(picker.list.count == 20) shouldBe true
      settled()
      picker
    }

    "stay up after words in its field are selected by a drag let go outside it" in onScreen {
      val picker = open()
      val input = fx(field(picker.field.node.fx))
      val first = fx(in(input, 20.0))
      drag(first, first)
      val start = fx(in(input, 40.0))
      drag(start, (outside._1, start._2))
      fx(Modal.showing) shouldBe true
      fx(input.getSelectedText.nonEmpty) shouldBe true
      picked shouldBe Nil
    }
    "close for a press and a release both outside it" in onScreen {
      open()
      drag(outside, (outside._1 + 4.0, outside._2 - 4.0))
      fx(Modal.showing) shouldBe false
      picked shouldBe Nil
    }
    "stay up for a press outside let go on a row, which it does not choose" in onScreen {
      val picker = open()
      val at = fx((picker.list.absoluteLeft + 40.0, picker.list.absoluteTop + picker.list.top(2) + 10.0))
      drag(outside, at)
      fx(Modal.showing) shouldBe true
      picked shouldBe Nil
    }
  }
}
