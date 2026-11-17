package everyui.render

import everyui.{Color, FxSpec, ui}
import everyui.component.{Container, TextView}
import everyui.network.onScreen
import javafx.collections.ListChangeListener
import javafx.scene.layout.Pane
import javafx.scene.text.Text
import rapid.Task

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration.*

/** Components changed from a rapid worker, as an app's `ask(...).map { … }` changes them: what is on the screen is
  * changed on the FX thread, in the order it was changed in, with nothing thrown and nothing lost (`Hop`); what is not
  * on it is changed where it is; and `onScreen` runs a task's answer on the FX thread. */
class OffThreadSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    offStage()
    super.afterAll()
  }

  // nodes changed on a thread other than the FX thread while on the screen, and what was thrown on the FX thread
  private val wrong = new AtomicInteger(0)
  @volatile private var thrown: List[Throwable] = Nil

  private def fxThread: Boolean = javafx.application.Platform.isFxApplicationThread

  /** `f` on a rapid worker, after a sleep (a continuation, as an answer is), waited for: what it threw is thrown. */
  private def onWorker(f: => Unit): Unit = {
    val done = new CountDownLatch(1)
    @volatile var failed: Throwable = null
    @volatile var where = ""
    Task.sleep(5.millis).map { _ =>
      where = Thread.currentThread.getName
      f
    }.handleError(t => Task { failed = t }).map(_ => done.countDown()).startUnit()
    done.await(60, TimeUnit.SECONDS) shouldBe true
    fxThread shouldBe false
    where should not be fx(Thread.currentThread.getName)
    if (failed != null) throw failed
  }

  /** A container on the screen whose node's children, and its children's words, are watched for a change made off
    * the FX thread. */
  private def watched(): Container = {
    val box = fx {
      val b = new Container
      b.size.width @= 800.0
      b.size.height @= 600.0
      ui.children @= Vector(b)
      b.node.fx.asInstanceOf[Pane].getChildren.addListener((_: ListChangeListener.Change[? <: javafx.scene.Node]) =>
        if (!fxThread) wrong.incrementAndGet())
      Thread.currentThread.setUncaughtExceptionHandler((_, t) => thrown = t :: thrown)
      b
    }
    wrong.set(0)
    thrown = Nil
    box
  }

  private def label(words: String): TextView = {
    val t = new TextView
    t.content @= words
    t.node.fx.asInstanceOf[Text].textProperty.addListener((_, _, _) => if (!fxThread && t.node.fx.getScene != null) wrong.incrementAndGet())
    t
  }

  // (on the FX thread)
  private def nodesOf(box: Container): List[javafx.scene.Node] =
    box.node.fx.asInstanceOf[Pane].getChildren.toArray.toList.map(_.asInstanceOf[javafx.scene.Node])

  private def clean(): Unit = {
    wrong.get shouldBe 0
    thrown shouldBe Nil
  }

  "A component on the screen changed from a rapid worker" should {
    "have children added, removed and moved on the FX thread, in order" in {
      val box = watched()
      val (a, b, c) = fx((label("a"), label("b"), label("c")))
      onWorker {
        box.children += a
        box.children += b
        box.children -= a
        box.children += c
        box.children @= Vector(c, b, a)
        box.children -= b
      }
      eventually(nodesOf(box) == List(c.node.fx, a.node.fx)) shouldBe true
      fx(box.children().toList) shouldBe List(c, a)
      clean()
    }

    "have its words and properties drawn on the FX thread, and measured as drawn" in {
      val box = watched()
      val (shown, twin) = fx {
        val s = label("short")
        val t = label("words a good deal longer than the ones it had")
        box.children @= Vector(s, t)
        (s, t)
      }
      onWorker {
        shown.content @= "words a good deal longer than the ones it had"
        box.backgroundColor @= Color.Red
      }
      eventually(shown.node.getText() == "words a good deal longer than the ones it had") shouldBe true
      eventually(shown.size.width() == twin.size.width() && shown.size.width() > 100.0) shouldBe true
      fx(box.node.fx.asInstanceOf[Pane].getBackground.getFills.get(0).getFill) shouldBe javafx.scene.paint.Color.web("#ff0000")
      clean()
    }

    "have a change made on the FX thread after one still on its way drawn last" in {
      val box = watched()
      val shown = fx {
        val s = label("first")
        box.children += s
        s
      }
      // the FX thread held while the worker changes the words, so its change waits; the FX thread's own change after
      // it must not be overtaken by it
      fx {
        val changed = new CountDownLatch(1)
        Task.sleep(5.millis).map(_ => shown.content @= "from the worker").map(_ => changed.countDown()).startUnit()
        changed.await(30, TimeUnit.SECONDS) shouldBe true
        shown.content @= "from the FX thread"
      }
      fx(shown.node.getText()) shouldBe "from the FX thread"
      fx(Thread.sleep(50L))
      fx(shown.node.getText()) shouldBe "from the FX thread"
      clean()
    }

    "lose nothing to many changes made while the FX thread lays out" in {
      val box = watched()
      val made = fx(Vector.tabulate(300)(i => label("line " + i)))
      @volatile var running = true
      val worker = new CountDownLatch(1)
      @volatile var failed: Throwable = null
      Task.sleep(1.millis).map { _ =>
        var i = 0
        while (i < made.length) {
          box.children += made(i)
          if (i > 0) made(i - 1).content @= "line " + (i - 1) + " changed"
          if (i % 3 == 2) box.children -= made(i - 2)
          if (i % 50 == 49) box.children @= box.children().reverse
          i += 1
        }
      }.handleError(t => Task { failed = t }).map { _ =>
        running = false
        worker.countDown()
      }.startUnit()
      // the FX thread meanwhile lays the box out again and again, at another width each time
      var turn = 0
      while (running) {
        fx {
          box.size.width @= 600.0 + (turn % 7) * 30.0
          ui.children().foreach(_.size.width())
          box.node.fx.asInstanceOf[Pane].layout()
        }
        turn += 1
      }
      worker.await(60, TimeUnit.SECONDS) shouldBe true
      if (failed != null) throw failed
      val listed = fx(box.children().toList)
      eventually(nodesOf(box) == listed.map(_.node.fx)) shouldBe true
      listed.size shouldBe 200
      fx(made.forall(t => t.node.getText() == t.content())) shouldBe true
      fx(made.last.node.getText()) shouldBe "line 299"
      clean()
    }
  }

  "A component not on the screen" should {
    "be changed where it is changed, at once" in {
      watched()
      @volatile var read = ""
      @volatile var thread = ""
      onWorker {
        val t = new TextView
        t.content @= "made off the FX thread"
        t.node.fx.asInstanceOf[Text].textProperty.addListener((_, _, _) => thread = Thread.currentThread.getName)
        t.content @= "changed off it"
        read = t.node.getText()
      }
      read shouldBe "changed off it"
      thread should not be fx(Thread.currentThread.getName)
      clean()
    }
  }

  "onScreen" should {
    "run a task's answer on the FX thread, and hand on what it worked out" in {
      val (on, value) = Task.sleep(5.millis).map(_ => 20).onScreen(n => (fxThread, n + 1)).sync()
      on shouldBe true
      value shouldBe 21
    }

    "fail as what it ran failed" in {
      val failed = Task.sleep(5.millis).onScreen(_ => throw new IllegalStateException("no")).attempt.sync()
      failed.isFailure shouldBe true
      failed.failed.get.getMessage shouldBe "no"
    }
  }
}
