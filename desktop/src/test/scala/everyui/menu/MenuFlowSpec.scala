package everyui.menu

import everyui.component.{Button, Component, Container, Modal, ScrollContainer, TextView}
import everyui.focus.Focus
import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.motion.{Keyframe, Track}
import everyui.render.{Nodes, Platform}
import everyui.task.Frames
import everyui.{FxSpec, ui}

/** NaboTV's Mark Watched from a card's menu, as it goes: the card held for its menu, the entry chosen, a dialog that
  * cannot be dismissed while the server answers (its Busy), taken down by the answer, and a prompt in its place (the
  * rating's), answered. Once all three are taken apart (a turn after each closes), the focus is on the card, the arrows
  * move it along the rail, the rail glides after it, and the frame loop runs and idles as before. The menu is
  * EveryUI's (`Menu.below`), and one as NaboTV's own is made: a panel of rows in a ScrollContainer put up by
  * `Modal.show`, a row closing it and doing its work a turn later. */
class MenuFlowSpec extends FxSpec {
  private var asked: Vector[() => Unit] = Vector.empty

  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
      Frames.drive(_ => (), () => false, () => (), _ => ())
    }
    offStage()
    super.afterAll()
  }

  /** The frames asked for, each running `ui`'s tasks a sixtieth of a second on, until none is asked or `most` ran. */
  private def frames(most: Int = 600): Int = fx {
    var ran = 0
    while (asked.nonEmpty && ran < most) {
      val next = asked.head
      asked = asked.tail
      next()
      ran += 1
    }
    ran
  }

  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  /** `turns` turns of the FX thread, with the frames each asks for, for what is put off to a turn of its own. */
  private def settle(turns: Int): Unit = (1 to turns).foreach { _ =>
    fx(())
    frames()
    Thread.sleep(5L)
  }

  /** What choosing Mark Watched does: a dialog with a turning glyph that cannot be dismissed, taken down by the
    * server's answer a moment later, and the rating asked in its place. `prompted` hears the rating put up. */
  private def markWatched(prompted: () => Unit): Unit = {
    val spinner = TextView("…", 18.0)
    spinner.motion @= List(Track(Track.Rotation, 1.0, List(Keyframe(0.0, 0.0), Keyframe(1.0, 6.28))))
    val busy = new Container {
      size.width @= 200.0
      size.height @= 100.0
      children += spinner
    }
    Modal.over(busy, busy, dismissible = false)
    Platform.after(0.2, () => {
      Modal.remove(busy)
      val done = new Button("Done", () => Modal.close())
      val prompt = new Container {
        layout @= Some(VerticalLayout(12.0))
        children ++= List(TextView("Rate it", 18.0), done)
      }
      Modal.show(prompt, done)
      prompted()
    })
  }

  /** A menu as NaboTV's own is: rows in a ScrollContainer on a panel, put up in place of any dialog; a row chosen
    * closes it, and its work is done a turn later. */
  private def naboMenu(entries: List[(String, () => Unit)]): Unit = {
    val rows = entries.map { case (label, work) =>
      new Button(label, () => {
        Modal.close()
        Platform.later(work)
      })
    }
    val stack = new Container {
      layout @= Some(VerticalLayout(0.0))
      size.width @= 360.0
      children ++= rows
    }
    val scroll = new ScrollContainer {
      size.width @= 360.0
      size.height @= 200.0
      content.size.height := stack.size.height()
      content.children += stack
    }
    val panel = new Container {
      size.width @= 360.0
      size.height @= 212.0
      children += scroll
    }
    Modal.show(panel, rows.head)
  }

  /** The flow, its menu put up over the first card by `open` with Mark Watched first: the focus, the keys, the glide and
    * the frame loop afterwards. */
  private def flow(open: (Component, () => Unit) => Unit): Unit = {
    val (rail, cards) = fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
      asked = Vector.empty
      Frames.drive(_ => ui.update(1.0 / 60.0), () => ui.needsFrames, () => (), f => asked = asked :+ f)
      val rail = new ScrollContainer(horizontal = true) {
        position.left @= 40.0
        position.top @= 400.0
        size.width @= 600.0
        size.height @= 120.0
        revealGlide @= 0.4
        content.layout @= Some(HorizontalLayout(20.0))
      }
      val cards = Vector.tabulate(12) { _ =>
        val c = new Component(Nodes.container())
        c.focusable @= true
        c.size.width @= 160.0
        c.size.height @= 100.0
        c
      }
      rail.content.children := cards
      ui.children += rail
      cards.head.focus()
      (rail, cards)
    }
    settle(3)
    var prompted = false
    fx(open(cards.head, () => markWatched(() => prompted = true)))
    settle(3)
    fx(Modal.showing) shouldBe true
    press("Enter")
    eventually(prompted) shouldBe true
    settle(3)
    press("Enter")
    settle(10)
    fx(Modal.showing) shouldBe false
    fx(Focus.scope()) shouldBe None
    fx(Focus.focused()) shouldBe Some(cards.head)
    fx(Frames.running) shouldBe false
    // the arrows move along the rail, and the rail glides after the focus
    (1 to 5).foreach { i =>
      press("ArrowRight")
      fx(Focus.focused()) shouldBe Some(cards(i))
      frames()
    }
    fx(rail.offset()) should be > 0.0
    fx(Frames.running) shouldBe false
  }

  "Mark Watched from a card's menu" should {
    "leave the focus, the keys, the glide and the frame loop as they were, from EveryUI's menu" in {
      flow((card, mark) => Menu.below(card, List(MenuItem("Mark Watched", mark), MenuItem("Add to list"))))
    }

    "leave them as they were, from a menu as NaboTV's own is" in {
      flow((_, mark) => naboMenu(List("Mark Watched" -> mark, "Add to list" -> (() => ()))))
    }
  }
}
