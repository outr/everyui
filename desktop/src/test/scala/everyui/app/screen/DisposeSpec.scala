package everyui.app.screen

import everyui.app.{DisposeGuide, DisposePlain, DisposeQuiz}
import everyui.component.{Component, TextView}
import everyui.focus.Focus
import everyui.keymap.Keymap
import everyui.task.Frames
import everyui.{FxSpec, ui}

import java.lang.ref.WeakReference

/** A screen shown once and left is taken apart, and then nothing keeps it: not the manager, the focus, the layout pass,
  * the theme, the clock or anything else that lives on. The guide is `DisposeGuide`: a dozen theatres, each with a tile
  * following the clock and a rail of showtimes, the focus on one of them as it is left.
  *
  * A reactify value keeps the value it had before its last change, so the screen manager's `active` holds the screen
  * shown before this one, and the focus the component focused before this one, until the next change: each check moves
  * on once more (another screen, and so another focus) before it looks, and a board is checked once two more have
  * followed it. */
class DisposeSpec extends FxSpec {
  private object Manager extends ScreenManager
  private lazy val home = new DisposePlain(Manager, "Home", "/")
  private lazy val calendar = new DisposePlain(Manager, "Calendar", "/calendar")
  private lazy val quiz = new DisposeQuiz(Manager)

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      ui.children.clear()
      Focus.focused @= None
      Manager.run(home, calendar)
    }
    eventually(Manager.active().exists(_ eq home)) shouldBe true
  }

  override def afterAll(): Unit = {
    fx {
      Frames.drive(_ => (), () => false, () => (), _ => ())
      ui.children.clear()
      Focus.focused @= None
      Focus.home @= (() => None)
      Focus.page @= (() => None)
      Keymap.shown @= (() => None)
      ScreenManager.running = None
    }
    super.afterAll()
  }

  // what a screen and everything it built leave behind to look for: nothing strong
  private final case class Left(screen: WeakReference[AnyRef], parts: Vector[WeakReference[Component]])

  /** The guide shown, the focus moved along its showtimes, and the guide left for the calendar over home. */
  private def visit(): Left = {
    val guide = fx(new DisposeGuide(Manager))
    fx(Manager.show(guide).startUnit())
    eventually(Manager.active().exists(_ eq guide)) shouldBe true
    eventually(Focus.focused().exists(f => guide.showtimes.exists(_ eq f))) shouldBe true
    fx(guide.showtimes(3).focus())
    val parts = fx {
      var all = Vector.empty[Component]
      var pending: List[Component] = List(guide.screenRoot)
      while (pending.nonEmpty) {
        all = all :+ pending.head
        pending = pending.head.childComponents.toList ++ pending.tail
      }
      all
    }
    parts.count {
      case t: TextView => fx(t.content()).startsWith(DisposeGuide.Theatre)
      case _ => false
    } shouldBe DisposeGuide.Theatres
    fx(Manager.showOver(calendar, home).startUnit())
    eventually(Manager.active().exists(_ eq calendar)) shouldBe true
    Left(new WeakReference(guide), parts.map(p => new WeakReference(p)))
  }

  /** How many of what `left` names are still reachable: waited for, a collection at a time, until none is or eight
    * seconds have passed (it is taken apart a second after it is out of sight, a slice a turn). */
  private def surviving(left: Left): Int = {
    // on to another screen, so what was last shown and last focused before it is not the guide (see above)
    fx(Manager.showOver(home, home).startUnit())
    eventually(Manager.active().exists(_ eq home)) shouldBe true
    fx(Manager.showOver(calendar, home).startUnit())
    eventually(Manager.active().exists(_ eq calendar)) shouldBe true
    def count = (left.parts :+ left.screen).count(_.get() != null)
    val until = System.currentTimeMillis() + 8000L
    while (count > 0 && System.currentTimeMillis() < until) {
      fx(())
      System.gc()
      Thread.sleep(50L)
    }
    count
  }

  "A screen made for one showing" should {
    "be let go of whole once it is left and taken apart" in {
      val left = visit()
      withClue(s"of ${left.parts.size + 1}: ") {
        surviving(left) shouldBe 0
      }
    }
    "be let go of each time, however many are shown and left" in {
      val all = (1 to 3).map(_ => visit())
      all.map(surviving).sum shouldBe 0
    }
  }

  "A board drawn afresh for each question" should {
    "leave only the one showing, the rest let go of" in {
      fx(Manager.showOver(quiz, home).startUnit())
      eventually(Manager.active().exists(_ eq quiz)) shouldBe true
      // each board and everything on it, as it was asked
      val boards = (1 to 10).map { n =>
        val parts = fx {
          quiz.ask(n)
          var all = Vector.empty[Component]
          var pending: List[Component] = quiz.stage.children().toList
          while (pending.nonEmpty) {
            all = all :+ pending.head
            pending = pending.head.childComponents.toList ++ pending.tail
          }
          all
        }
        parts.map(p => new WeakReference(p))
      }
      boards.last.count(_.get() == null) shouldBe 0
      // (the board before the last is still the stage's previous children: see above)
      surviving(Left(new WeakReference(new Object), boards.dropRight(2).flatten.toVector)) shouldBe 0
    }
  }
}
