package everyui.app

import everyui.component.Modal
import everyui.dialog.DialogButton
import everyui.focus.Focus
import everyui.render.{Platform, WindowLeave}
import everyui.FxSpec
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.stage.{Stage, WindowEvent}
import reactify.*

/** A window's close asked of the app's guards: unguarded it closes as before; while a guard answers true it asks, Stay
  * keeping the window and Leave closing it, the window's own close handler hearing it once and after the answer; guards
  * removed, several at once, a flag's, and a close asked again while the question is up. */
class LeaveGuardSpec extends FxSpec {
  private var removers: List[() => Unit] = Nil

  private def guard(f: () => Boolean): () => Unit = {
    val remove = fx(Platform.leaveGuard(f))
    removers = remove :: removers
    remove
  }

  /** A window of its own, its close guarded as the launcher's is. */
  private def window(test: Stage => Unit): Unit = {
    val stage = fx {
      javafx.application.Platform.setImplicitExit(false)
      val s = new Stage()
      s.setScene(new Scene(new Pane(), 200.0, 100.0))
      WindowLeave.watch(s)
      s.show()
      s
    }
    try test(stage) finally fx {
      while (Modal.showing) Modal.close()
      if (stage.isShowing) stage.close()
      removers.foreach(r => r())
      removers = Nil
      Focus.focused @= None
    }
  }

  // as the window's × asks it to close
  private def closeAsked(stage: Stage): Unit = fx(stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)))
  private def showing(stage: Stage): Boolean = fx(stage.isShowing)
  private def asking: Boolean = fx(Modal.showing)
  private def focusedButton: Option[String] = fx(Focus.focused().collect { case b: DialogButton => b.action.label })
  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }
  private def leave(): Unit = {
    press("ArrowRight")
    focusedButton shouldBe Some("Leave")
    press("Enter")
  }

  "A window's close" should {
    "close at once with no guard" in window { stage =>
      fx(LeaveGuards.armed()) shouldBe false
      closeAsked(stage)
      asking shouldBe false
      showing(stage) shouldBe false
    }
    "close at once while every guard answers false" in window { stage =>
      guard(() => false)
      fx(LeaveGuards.armed()) shouldBe true
      closeAsked(stage)
      asking shouldBe false
      showing(stage) shouldBe false
    }
    "ask while a guard answers true, Stay keeping the window and Leave closing it" in window { stage =>
      guard(() => true)
      closeAsked(stage)
      asking shouldBe true
      showing(stage) shouldBe true
      focusedButton shouldBe Some("Stay")
      press("Enter")
      asking shouldBe false
      showing(stage) shouldBe true
      closeAsked(stage)
      asking shouldBe true
      press("Escape")
      showing(stage) shouldBe true
      closeAsked(stage)
      leave()
      asking shouldBe false
      showing(stage) shouldBe false
    }
    "close as before once its guard is removed" in window { stage =>
      val remove = guard(() => true)
      fx(remove())
      fx(LeaveGuards.armed()) shouldBe false
      closeAsked(stage)
      asking shouldBe false
      showing(stage) shouldBe false
    }
    "ask while any of several guards answers true" in window { stage =>
      var dirty = false
      guard(() => false)
      guard(() => dirty)
      val signedIn = guard(() => true)
      closeAsked(stage)
      asking shouldBe true
      press("Escape")
      fx(signedIn())
      closeAsked(stage)
      asking shouldBe false
      showing(stage) shouldBe false
    }
    "follow a flag" in window { stage =>
      val dirty = Var(false)
      val remove = fx(Platform.leaveGuardWhile(dirty))
      removers = remove :: removers
      fx(LeaveGuards.armed()) shouldBe false
      fx(dirty @= true)
      fx(LeaveGuards.armed()) shouldBe true
      closeAsked(stage)
      asking shouldBe true
      press("Escape")
      fx(dirty @= false)
      fx(LeaveGuards.armed()) shouldBe false
      closeAsked(stage)
      showing(stage) shouldBe false
    }
    "reach the window's own handler once, after Leave, which may still keep it" in window { stage =>
      var heard = 0
      var keep = true
      fx(stage.setOnCloseRequest((e: WindowEvent) => {
        heard += 1
        if (keep) e.consume()
      }))
      guard(() => true)
      closeAsked(stage)
      heard shouldBe 0
      press("Escape")
      heard shouldBe 0
      closeAsked(stage)
      leave()
      heard shouldBe 1
      showing(stage) shouldBe true
      keep = false
      closeAsked(stage)
      leave()
      heard shouldBe 2
      showing(stage) shouldBe false
    }
    "put up one question however often it is asked" in window { stage =>
      guard(() => true)
      val before = fx(Modal.opened)
      closeAsked(stage)
      closeAsked(stage)
      fx(Modal.opened) shouldBe before + 1
      leave()
      asking shouldBe false
      showing(stage) shouldBe false
    }
  }
}
