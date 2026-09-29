package everyui.terminal

import everyui.render.Host
import everyui.ui
import javafx.scene.Scene
import javafx.scene.layout.Pane
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{CountDownLatch, TimeUnit}

/** A spec on the desktop backend's real nodes, on the JavaFX thread, in the one window every spec here shares: shown,
  * since the focus (and the keys) reach a web view only in a window that has it. */
trait TerminalFxSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  /** `f` on the FX thread, waited for. */
  protected def fx[T](f: => T): T = {
    var result: Option[Either[Throwable, T]] = None
    val done = new CountDownLatch(1)
    javafx.application.Platform.runLater(() => {
      result = Some(try Right(f) catch { case t: Throwable => Left(t) })
      done.countDown()
    })
    done.await(30, TimeUnit.SECONDS) shouldBe true
    result.get.fold(t => throw t, v => v)
  }

  /** `check` on the FX thread until it holds, for up to `seconds`. */
  protected def eventually(seconds: Double = 2.0)(check: => Boolean): Boolean = {
    val until = System.currentTimeMillis() + (seconds * 1000.0).toLong
    var held = fx(check)
    while (!held && System.currentTimeMillis() < until) {
      Thread.sleep(20L)
      held = fx(check)
    }
    held
  }

  override def beforeAll(): Unit = {
    val started = new CountDownLatch(1)
    try javafx.application.Platform.startup(() => started.countDown())
    catch { case _: IllegalStateException => started.countDown() }
    started.await(10, TimeUnit.SECONDS)
    fx {
      if (Host.scene == null) {
        Host.root = new Pane()
        Host.scene = new Scene(Host.root, 1280.0, 800.0)
      }
      if (Host.scene.getWindow == null) {
        val stage = new javafx.stage.Stage()
        stage.setScene(Host.scene)
        stage.show()
      }
      ui.size.width.get
    }
  }
}
