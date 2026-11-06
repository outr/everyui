package everyui.access

import everyui.component.*
import everyui.date.DatePicker
import everyui.dialog.Dialogs
import everyui.kit.Toast
import everyui.menu.{Menu, MenuItem}
import everyui.{FxSpec, ui}

/** At a screen 320 across (WCAG's reflow), what runs off its edge is found, the first part of it only, and not what a
  * scroll holds; EveryUI's dialogs, menus, toasts and date picker all fit. */
class ReflowSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    offStage()
    super.afterAll()
  }

  private def narrow[T](test: => T): T = {
    val was = fx((ui.size.width(), ui.size.height()))
    fx {
      ui.children.clear()
      ui.size.width @= 320.0
      ui.size.height @= 640.0
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      Toast.hide()
      ui.children.clear()
      ui.size.width @= was._1
      ui.size.height @= was._2
    }
  }

  private def reflow(): List[AccessFinding] = fx(Access.check().filter(_.rule == AccessRule.Reflow))

  "The reflow check" should {
    "find the first part that runs past the screen's edge, and not what a scroll holds" in narrow {
      val (wide, inner) = fx {
        val w = new Container
        w.size.width @= 500.0
        w.size.height @= 40.0
        val i = new Container
        i.size.width @= 480.0
        i.size.height @= 20.0
        w.children += i
        ui.children += w
        (w, i)
      }
      reflow().map(_.component) shouldBe List(wide)
      reflow().head.detail shouldBe "runs 180 past the edge of a screen 320 across"
      fx(ui.children.clear())
      fx {
        val s = new ScrollContainer
        s.size.width @= 300.0
        s.size.height @= 200.0
        s.content.children += wide
        ui.children += s
      }
      reflow() shouldBe Nil
    }
    "find nothing in EveryUI's dialogs, menus, toasts and date picker" in narrow {
      fx(Dialogs.confirm("Archive these 3 conversations?", "They leave the inbox but can be found by search.").start())
      eventually(Modal.showing) shouldBe true
      reflow().map(_.toString) shouldBe Nil
      fx(Modal.close())
      fx(Menu.at(300.0, 40.0, List(MenuItem("Open in a new window"), MenuItem("Copy the link to this message"))))
      reflow().map(_.toString) shouldBe Nil
      fx(Modal.close())
      fx(Toast.show("Your changes were saved, and everyone in the workspace can see them now"))
      reflow().map(_.toString) shouldBe Nil
      fx(Toast.hide())
      fx(DatePicker.pick().start())
      eventually(Modal.showing) shouldBe true
      reflow().map(_.toString) shouldBe Nil
    }
  }
}
