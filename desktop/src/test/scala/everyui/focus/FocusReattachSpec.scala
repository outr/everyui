package everyui.focus

import everyui.component.*
import everyui.render.Host
import everyui.text.TextArea
import everyui.{FxSpec, ui}

/** The platform's focus follows the focus layer's through a move: a focused field moved to another container, or taken
  * off the screen and put back, has its caret again; `focus()` on the field that has the focus takes the platform's
  * again; and a blur the app did not make is not undone. */
class FocusReattachSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    offStage()
    super.afterAll()
  }

  private def box(): Container = {
    val c = new Container
    c.size.width @= 400.0
    c.size.height @= 100.0
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

  // the platform's field under a field of EveryUI's
  private def field(c: Container): javafx.scene.Node = fx(c.children().collectFirst { case i: InputView => i.node.fx }.get)
  // whether the platform's focus is on it
  private def caret(c: Container): Boolean = {
    val f = field(c)
    fx(Host.scene.getFocusOwner eq f)
  }

  "A focused field" should {
    "keep the platform's focus as it is moved to another container" in {
      val (a, b, input) = fx((box(), box(), new TextInput("Name")))
      onScreen(a, b) {
        fx {
          a.children += input
          input.focus()
        }
        caret(input) shouldBe true
        fx(b.children @= Vector(input))
        fx(Focus.focused()) shouldBe Some(input)
        fx(input.editing()) shouldBe true
        caret(input) shouldBe true
        fx(a.children().contains(input)) shouldBe false
      }
    }
    "have it back once put on the screen again, a turn after it was taken off" in {
      val (a, b, input) = fx((box(), box(), new TextArea("Message")))
      onScreen(a, b) {
        fx {
          a.children += input
          input.focus()
        }
        caret(input) shouldBe true
        fx(a.children -= input)
        // the platform's focus gone with the node, as a browser takes it
        fx(Host.root.requestFocus())
        caret(input) shouldBe false
        fx(Focus.focused()) shouldBe Some(input)
        fx(b.children += input)
        caret(input) shouldBe true
        fx(input.editing()) shouldBe true
      }
    }
    "have it back as the container it is in is put on the screen again" in {
      val (a, b, holder, input) = fx((box(), box(), box(), new TextInput("Name")))
      onScreen(a, b) {
        fx {
          holder.children += input
          a.children += holder
          input.focus()
        }
        caret(input) shouldBe true
        // as a chat's composer goes from one view to the next (ChatView.composerSlot)
        fx {
          a.children -= holder
          b.children += holder
        }
        caret(input) shouldBe true
        fx(Focus.focused()) shouldBe Some(input)
      }
    }
    "take the platform's focus again when focused again" in {
      val (a, input) = fx((box(), new TextInput("Name")))
      onScreen(a) {
        fx {
          a.children += input
          input.focus()
        }
        caret(input) shouldBe true
        // a blur the app did not make
        fx(Host.root.requestFocus())
        caret(input) shouldBe false
        fx(input.editing()) shouldBe false
        fx(Focus.focused()) shouldBe Some(input)
        fx(input.focus())
        caret(input) shouldBe true
        fx(input.editing()) shouldBe true
      }
    }
    "be left as a blur the app did not make leaves it" in {
      val (a, input) = fx((box(), new everyui.kit.SearchField()))
      onScreen(a) {
        fx {
          a.children += input
          input.focus()
        }
        caret(input) shouldBe true
        fx(Host.root.requestFocus())
        // turns go by and nothing takes it back
        (1 to 5).foreach(_ => fx(()))
        Thread.sleep(300L)
        caret(input) shouldBe false
        fx(input.editing()) shouldBe false
      }
    }
    "not take the platform's focus as something else is moved" in {
      val (a, b, input, other) = fx((box(), box(), new TextInput("Name"), new Container))
      onScreen(a, b) {
        fx {
          a.children ++= List(input, other)
          input.focus()
        }
        fx(Host.root.requestFocus())
        fx(b.children += other)
        caret(input) shouldBe false
      }
    }
  }
}
