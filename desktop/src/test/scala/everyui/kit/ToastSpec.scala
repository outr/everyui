package everyui.kit

import everyui.component.*
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.render.Nodes
import everyui.{FxSpec, ui}

/** Where a toast sits (the app's `placement`, under its header's `topInset`, clear of what it keeps clear of), and how
  * it is closed: a tap or a swipe toward its edge, the close button of one with an action, a screen reader's
  * activation. It never takes the focus, and what it keeps clear of is let go of with the component. */
class ToastSpec extends FxSpec {
  /** `test` on an empty screen, the toasts as an app finds them, all put back after. */
  private def clean[T](test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
    }
    try test finally fx {
      Toast.hide()
      Toast.presenter @= None
      Toast.placement @= ToastPlacement.Bottom
      Toast.topInset @= 0.0
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def plate: Container = fx(ui.children().last.asInstanceOf[Container])
  private def screen: Double = fx(ui.size.height())
  private def top(c: Component): Double = fx(c.position.top())
  private def bottom(c: Component): Double = fx(c.position.top() + c.size.height())

  /** A box `height` tall from `at` down the screen, as wide as it. */
  private def box(at: Double, height: Double): Container = fx {
    val c = new Container(Nodes.container())
    c.size.width := ui.size.width()
    c.size.height @= height
    c.position.top @= at
    ui.children += c
    c
  }

  private def pointer(c: Component, kind: PointerKind, y: Double): Unit = fx {
    val e = PointerEvent(kind, c.absoluteLeft + 30.0, y, finger = true)
    kind match {
      case PointerKind.Down => c.event.pointer.down @= e
      case PointerKind.Move => c.event.pointer.move @= e
      case _ => c.event.pointer.up @= e
    }
  }

  /** A finger down on `c` and drawn `by` down the screen (up, where negative), and lifted. */
  private def swipe(c: Component, by: Double): Unit = {
    val from = fx(c.absoluteTop + c.size.height() / 2.0)
    pointer(c, PointerKind.Down, from)
    pointer(c, PointerKind.Move, from + by / 2.0)
    pointer(c, PointerKind.Move, from + by)
    pointer(c, PointerKind.Up, from + by)
  }

  private def gone(c: Component): Boolean = fx(!ui.children().exists(_ eq c))

  "A toast's placement" should {
    "stay at the foot by default, go to the head where the app says, and give way to a call's own" in clean {
      fx(Toast.show("Saved", seconds = 60.0))
      bottom(plate) shouldBe (screen - 32.0 +- 0.5)
      fx(Toast.placement @= ToastPlacement.Top)
      fx(Toast.show("Saved again", seconds = 60.0))
      top(plate) shouldBe (32.0 +- 0.5)
      fx(Toast.placement @= ToastPlacement(top = true, margin = 12.0))
      fx(Toast.show("Closer", seconds = 60.0))
      top(plate) shouldBe (12.0 +- 0.5)
      // the call's own word wins over the app's
      fx(Toast.show("At the foot", seconds = 60.0, top = false, margin = 20.0))
      bottom(plate) shouldBe (screen - 20.0 +- 0.5)
      fx(Toast.placement @= ToastPlacement.Bottom)
      fx(Toast.show("Head", seconds = 60.0, top = true))
      top(plate) shouldBe (32.0 +- 0.5)
    }
    "sit under the app's header, following its height" in clean {
      val header = box(0.0, 56.0)
      fx(Toast.topInset := header.size.height())
      fx(Toast.placement @= ToastPlacement.Top)
      fx(Toast.show("Saved", seconds = 60.0))
      top(plate) shouldBe (56.0 + Toast.clearance +- 0.5)
      fx(header.size.height @= 100.0)
      top(plate) shouldBe (100.0 + Toast.clearance +- 0.5)
      // a margin further in than the header leaves still holds
      fx(Toast.show("Lower", seconds = 60.0, margin = 140.0))
      top(plate) shouldBe (140.0 +- 0.5)
    }
  }

  "A toast kept clear" should {
    "rise above a composer at the foot, and sit below a bar at the head, as they move and grow" in clean {
      val composer = box(screen - 70.0, 60.0)
      fx(Toast.keepClear(composer))
      fx(Toast.show("Copied", seconds = 60.0))
      val toast = plate
      bottom(toast) shouldBe (screen - 70.0 - Toast.clearance +- 0.5)
      // the composer grows upward as lines are typed
      val foot = screen
      fx {
        composer.size.height @= 120.0
        composer.position.top @= foot - 130.0
      }
      bottom(toast) shouldBe (screen - 130.0 - Toast.clearance +- 0.5)
      // a bar at the head keeps a top toast below it
      val bar = box(0.0, 90.0)
      fx(Toast.keepClear(bar))
      fx(Toast.show("Saved", seconds = 60.0, top = true))
      top(plate) shouldBe (90.0 + Toast.clearance +- 0.5)
    }
    "clear the highest at the foot and the lowest at the head" in clean {
      val composer = box(screen - 70.0, 60.0)
      val tray = box(screen - 200.0, 50.0)
      val header = box(0.0, 50.0)
      val tabs = box(50.0, 40.0)
      fx(List(composer, tray, header, tabs).foreach(c => Toast.keepClear(c)))
      fx(Toast.show("Copied", seconds = 60.0))
      bottom(plate) shouldBe (screen - 200.0 - Toast.clearance +- 0.5)
      fx(Toast.show("Saved", seconds = 60.0, top = true))
      top(plate) shouldBe (90.0 + Toast.clearance +- 0.5)
    }
    "ignore one off the screen or hidden, and let one go as it is taken apart" in clean {
      val composer = box(screen - 70.0, 60.0)
      val stop = fx(Toast.keepClear(composer))
      fx(Toast.show("Copied", seconds = 60.0))
      val toast = plate
      fx(composer.visible @= false)
      bottom(toast) shouldBe (screen - 32.0 +- 0.5)
      fx(composer.visible @= true)
      bottom(toast) shouldBe (screen - 70.0 - Toast.clearance +- 0.5)
      fx(ui.children -= composer)
      bottom(toast) shouldBe (screen - 32.0 +- 0.5)
      // ended by the function it gave
      fx(stop())
      fx(Toast.keepsClear(composer)) shouldBe false
      // and by the component taken apart
      val other = box(screen - 70.0, 60.0)
      fx(Toast.keepClear(other))
      fx(Toast.keepsClear(other)) shouldBe true
      fx(other.dispose())
      fx(Toast.keepsClear(other)) shouldBe false
      bottom(toast) shouldBe (screen - 32.0 +- 0.5)
    }
    "leave nothing made behind it once it goes" in clean {
      val composer = box(screen - 70.0, 60.0)
      fx(Toast.keepClear(composer))
      fx(())
      val before = fx(Component.liveNow)
      fx(Component.warnUndisposed @= true)
      try {
        (1 to 5).foreach { i =>
          fx(Toast.show("Message " + i.toString, seconds = 60.0, action = if (i % 2 == 0) Some("Undo" -> (() => ())) else None))
        }
        fx(Toast.hide())
        eventually(Component.liveNow == before) shouldBe true
      }
      finally fx(Component.warnUndisposed @= false)
    }
  }

  "A toast" should {
    "close on a tap, or a screen reader's activation, where it has no action" in clean {
      fx(Toast.show("Saved", seconds = 60.0))
      val toast = plate
      fx(toast.semantics().role) shouldBe "button"
      val y = fx(toast.absoluteTop + 10.0)
      pointer(toast, PointerKind.Down, y)
      pointer(toast, PointerKind.Up, y)
      gone(toast) shouldBe true
      fx(Toast.show("Saved again", seconds = 60.0))
      val again = plate
      fx(again.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
      gone(again) shouldBe true
    }
    "close on a swipe toward its edge, and go back on a short one" in clean {
      fx(Toast.show("Saved", seconds = 60.0))
      val toast = plate
      val at = top(toast)
      // up, away from the foot it sits at, and then too short a way down: it stays where it was
      swipe(toast, -60.0)
      gone(toast) shouldBe false
      top(toast) shouldBe (at +- 0.5)
      swipe(toast, 14.0)
      gone(toast) shouldBe false
      top(toast) shouldBe (at +- 0.5)
      swipe(toast, 60.0)
      gone(toast) shouldBe true
      // a toast at the head goes up
      fx(Toast.show("Saved", seconds = 60.0, top = true))
      val head = plate
      swipe(head, 60.0)
      gone(head) shouldBe false
      swipe(head, -60.0)
      gone(head) shouldBe true
    }
    "keep a tap for its action, and still close on a swipe or its close button" in clean {
      var undone = 0
      fx(Toast.show("Archived", seconds = 60.0, action = Some("Undo" -> (() => undone += 1))))
      val toast = plate
      val y = fx(toast.absoluteTop + 10.0)
      pointer(toast, PointerKind.Down, y)
      pointer(toast, PointerKind.Up, y)
      gone(toast) shouldBe false
      swipe(toast, 60.0)
      gone(toast) shouldBe true
      undone shouldBe 0
      fx(Toast.show("Archived", seconds = 60.0, action = Some("Undo" -> (() => undone += 1))))
      val again = plate
      val close = fx(again.children().collectFirst { case b: IconButton => b }.get)
      fx(close.semantics().label) shouldBe everyui.i18n.Words.close
      fx(close.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
      gone(again) shouldBe true
      undone shouldBe 0
    }
    "never take the focus" in clean {
      val field = fx {
        val c = new Component(Nodes.container())
        c.focusable @= true
        ui.children += c
        c.focus()
        c
      }
      fx(Toast.show("Archived", seconds = 60.0, action = Some("Undo" -> (() => ()))))
      fx(Toast.show("Saved", seconds = 60.0))
      fx(Focus.focused()) shouldBe Some(field)
    }
    }

  "A toast with the app's presenter" should {
    "go to it, drawn by Toast not at all, still heard and read out, and drawn again once it is let go of" in clean {
      var asked = List.empty[ToastRequest]
      var undone = 0
      val heard = fx {
        var words = List.empty[String]
        var said = List.empty[String]
        val a = Toast.shown.attach(w => words = words :+ w)
        val b = everyui.access.LiveRegions.announced.attach(s => said = said :+ s.words)
        Toast.presenter @= Some(r => asked = asked :+ r)
        val before = ui.children().length
        Toast.show("Archived", seconds = 3.0, top = true, action = Some("Undo" -> (() => undone += 1)))
        Toast.show("Copied")
        Toast.show("Copied", tone = ToastTone.Error)
        Toast.shown.reactions -= a
        everyui.access.LiveRegions.announced.reactions -= b
        (words, said, ui.children().length - before)
      }
      val (words, said, added) = heard
      added shouldBe 0
      words shouldBe List("Archived", "Copied", "Copied")
      said shouldBe List("Archived. Undo", "Copied", "Copied")
      asked.map(_.words) shouldBe List("Archived", "Copied", "Copied")
      asked.map(_.seconds) shouldBe List(3.0, 4.0, 4.0)
      asked.map(_.top) shouldBe List(true, false, false)
      asked.map(_.tone) shouldBe List(ToastTone.Info, ToastTone.Info, ToastTone.Error)
      // an error two seconds longer than the same words not one
      asked(2).lasts should be > asked(1).lasts
      // four seconds more while an action is offered, and never less than asked
      asked.head.lasts should be >= 7.0
      asked(1).lasts should be >= 4.0
      asked(1).action shouldBe None
      asked.head.action.map(_._1) shouldBe Some("Undo")
      asked.head.action.foreach(_._2())
      undone shouldBe 1
      // let go of, Toast draws them again
      fx {
        Toast.presenter @= None
        Toast.show("Saved", tone = ToastTone.Success)
      }
      val drawn = plate
      fx(drawn.children().exists {
        case t: TextView => t.content() == "Saved"
        case _ => false
      }) shouldBe true
      // with a bar of the success colour at its start
      fx(drawn.children().exists(c => c.size.width() == 4.0 && c.backgroundColor() == everyui.theme.Theme.success())) shouldBe true
      // and Info (the default) none
      fx(Toast.show("Plain"))
      val plain = plate
      fx(plain.children().exists(_.size.width() == 4.0)) shouldBe false
    }
  }
}
