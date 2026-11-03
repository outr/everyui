package everyui.error

import everyui.component.Component
import everyui.focus.Focus
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/** The app's errors heard in one place: a click's or a key's handler that throws told with where it was, the app going
  * on after it, and an exception a thread throws that nothing catches; nothing caught until something listens. */
class ErrorsSpec extends FxSpec {
  private val heard = new ConcurrentLinkedQueue[Problem]()

  private def card(): Component = fx {
    ui.children.clear()
    val c = new Component(Nodes.container())
    c.focusable @= true
    c.size.width @= 100.0
    c.size.height @= 40.0
    c.position.left @= 50.0
    c.position.top @= 50.0
    ui.children += c
    c
  }

  private def click(on: Component): Unit = fx {
    val x = on.absoluteLeft + 10.0
    val y = on.absoluteTop + 10.0
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y, MouseButton.PRIMARY,
      1, false, false, false, false, true, false, false, false, false, true, null))
  }

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private def listening[T](test: => T): T = {
    heard.clear()
    val stop = Errors.onProblem(p => heard.add(p))
    try test finally {
      stop()
      fx {
        ui.children.clear()
        Focus.focused @= None
      }
    }
  }

  "Errors" should {
    "leave a throw where it went while nothing listens" in {
      val c = card()
      var clicks = 0
      fx(c.event.click.attach { _ =>
        clicks += 1
        throw new IllegalStateException("unheard")
      })
      fx(Errors.listening) shouldBe false
      // thrown on as before (out of the event's dispatch here), the click having run
      val thrown = try { click(c); None } catch { case t: Throwable => Some(t) }
      thrown.map(_.getMessage) shouldBe Some("unheard")
      clicks shouldBe 1
      fx(ui.children.clear())
    }
    "tell a click's throw with where it was, and go on" in listening {
      val c = card()
      var clicks = 0
      fx(c.event.click.attach { _ =>
        clicks += 1
        if (clicks == 1) throw new IllegalStateException("card broke")
      })
      click(c)
      click(c)
      clicks shouldBe 2
      val told = heard.asScala.toList
      told.map(_.message) shouldBe List("java.lang.IllegalStateException: card broke")
      told.head.where should startWith("pointer Click on")
      told.head.stack should include("ErrorsSpec")
    }
    "tell a key's throw as that key's, on what had the focus" in listening {
      val c = card()
      fx {
        c.event.click.attach(_ => throw new IllegalArgumentException("key broke"))
        c.focus()
      }
      press(KeyCode.ENTER)
      eventually(!heard.isEmpty) shouldBe true
      val told = heard.asScala.toList
      told.map(_.message) shouldBe List("java.lang.IllegalArgumentException: key broke")
      told.head.where should startWith("key Enter down on")
    }
    "tell what a thread throws that nothing catches" in listening {
      val thread = new Thread(new Runnable {
        def run(): Unit = throw new RuntimeException("away from the screen")
      })
      thread.start()
      thread.join(5000L)
      eventually(heard.asScala.exists(_.message.contains("away from the screen"))) shouldBe true
      val told = heard.asScala.find(_.message.contains("away from the screen")).get
      told.where shouldBe "uncaught"
      told.stack should include("RuntimeException")
    }
  }
}
