package everyui.app.screen

import everyui.async.Task
import everyui.component.{Button, Component, Container}
import everyui.focus.Focus
import everyui.keymap.Keymap
import everyui.net.URLPath
import everyui.render.Platform
import everyui.task.Frames
import everyui.{FxSpec, ui}
import reactify.*

import java.lang.ref.WeakReference

/** A screen built afresh in place (`ScreenManager.rebuild`), as a change of language wants on the web: what it built is
  * made again, in the words of the moment, and let go of; what it made as it was constructed (its fields) is kept and
  * placed again; the focus goes to what stands where it was; and `onRebuild` carries across what lives only in the
  * built UI. A screen not showing is rebuilt when Back returns to it. */
class RebuildSpec extends FxSpec {
  // the focus as the keys move it, which these are about
  override protected def keyed: Boolean = true

  private val word: Var[String] = Var("One")

  private class Board(val manager: ScreenManager, name: String) extends ScrollScreen {
    override def title: String = name
    override def path: URLPath = URLPath("/" + name)
    // a field, made with the screen: kept by a rebuild, and one placed in a row the build makes
    val kept = new Button("Kept")
    val inRow = new Button("In a row")
    var builds: Int = 0
    var made: Vector[Button] = Vector.empty

    def shown: Vector[Component] = body.children()

    override def defaultFocus(): Component = kept

    override def createUI(): Task[Unit] = Task {
      builds += 1
      made = (0 until 3).toVector.map(i => new Button(word() + " " + i))
      val row = new Container
      row.children += inRow
      body.children ++= List(kept, row) ++ made
    }

    // the last button's tooltip lives only in the built UI: carried across
    override protected def onRebuild(): () => Unit = {
      val tip = made.last.tooltip()
      () => made.last.tooltip @= tip
    }
  }

  private object Manager extends ScreenManager
  private lazy val home = new Board(Manager, "home")
  private lazy val other = new Board(Manager, "other")

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      ui.children.clear()
      Focus.focused @= None
      Platform.pushPath("/home")
      Manager.run(home, other)
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

  private def labels(b: Board): Vector[String] = fx(b.made.map(_.label()))

  "Rebuilding the showing screen" should {
    "make its UI again, keep its fields and put the focus on the equivalent" in {
      eventually(Focus.focused().exists(_ eq home.kept)) shouldBe true
      val old = fx(home.made)
      fx(old(2).focus())
      fx(old(2).tooltip @= "typed")
      fx(word @= "Two")
      val builds = fx(home.builds)
      fx(Manager.rebuild().startUnit())
      eventually(home.builds == builds + 1) shouldBe true
      labels(home) shouldBe Vector("Two 0", "Two 1", "Two 2")
      fx(home.made.exists(m => old.exists(_ eq m))) shouldBe false
      eventually(Focus.focused().exists(_ eq home.made(2))) shouldBe true
      // carried across by onRebuild
      fx(home.made(2).tooltip()) shouldBe "typed"
      // the fields are the same, shown once each, and not taken apart
      fx(Focus.showing(home.kept) && Focus.showing(home.inRow)) shouldBe true
      fx(home.kept.takenApart || home.inRow.takenApart) shouldBe false
      fx(home.shown.count(_ eq home.kept)) shouldBe 1
      fx(home.shown.size) shouldBe 5
      eventually(old.forall(_.takenApart)) shouldBe true
    }
    "let go of what it built before" in {
      val builds = fx(home.builds)
      val old = fx {
        var all = Vector.empty[Component]
        var pending: List[Component] = home.shown.toList.filterNot(c => (c eq home.kept))
        while (pending.nonEmpty) {
          // (the fields, and what they hold, are kept)
          val c = pending.head
          pending = pending.tail
          if (!(c eq home.inRow)) {
            all = all :+ c
            pending = c.childComponents.toList ++ pending
          }
        }
        all
      }.map(c => new WeakReference(c))
      fx(Manager.rebuild().startUnit())
      eventually(home.builds == builds + 1 && old.forall(r => r.get() == null || r.get().takenApart)) shouldBe true
      // on once more, so the focus's previous value is not one of them (a reactify value keeps it until the next change)
      fx(home.kept.focus())
      fx(home.made(0).focus())
      // and JavaFX drawn once: what it last marked to draw it holds until it draws, which a scene in no window never does
      fx(window.snapshot(null))
      def count = old.count(_.get() != null)
      val until = System.currentTimeMillis() + 8000L
      while (count > 0 && System.currentTimeMillis() < until) {
        fx(())
        System.gc()
        Thread.sleep(50L)
      }
      count shouldBe 0
    }
  }

  "A screen on the history marked to be rebuilt" should {
    "be rebuilt when Back returns to it, with its focus on the equivalent" in {
      fx(home.made(1).focus())
      fx(Manager.show(other).startUnit())
      eventually(Manager.active().exists(_ eq other)) shouldBe true
      val builds = fx(home.builds)
      fx(word @= "Three")
      fx(Manager.rebuild(home).startUnit())
      fx(())
      fx(home.builds) shouldBe builds
      labels(home).head shouldBe "Two 0"
      fx(Manager.back())
      eventually(Manager.active().exists(_ eq home)) shouldBe true
      eventually(home.builds == builds + 1) shouldBe true
      labels(home) shouldBe Vector("Three 0", "Three 1", "Three 2")
      eventually(Focus.focused().exists(_ eq home.made(1))) shouldBe true
    }
  }

  "Rebuilding every screen" should {
    "rebuild the showing one now and the rest when next shown" in {
      fx(Manager.show(other).startUnit())
      eventually(Manager.active().exists(_ eq other)) shouldBe true
      val homeBuilds = fx(home.builds)
      val otherBuilds = fx(other.builds)
      fx(word @= "Four")
      fx(Manager.rebuildAll().startUnit())
      eventually(other.builds == otherBuilds + 1) shouldBe true
      labels(other).head shouldBe "Four 0"
      fx(home.builds) shouldBe homeBuilds
      fx(Manager.back())
      eventually(Manager.active().exists(_ eq home)) shouldBe true
      eventually(home.builds == homeBuilds + 1) shouldBe true
      labels(home).head shouldBe "Four 0"
      // and only once: shown again, it is not rebuilt
      fx(Manager.show(other).startUnit())
      eventually(Manager.active().exists(_ eq other)) shouldBe true
      fx(other.builds) shouldBe otherBuilds + 1
    }
  }
}
