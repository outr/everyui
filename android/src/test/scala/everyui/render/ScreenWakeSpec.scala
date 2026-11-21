package everyui.render

import everyui.activity.ActivityPresence
import everyui.app.Presence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The motions go on after the screen goes off and on with the app in front, in the order Android 15 and 16 call an
  * activity back for it (onPause, onStop, the window's focus lost; on waking and unlocking onStart, onResume, the focus
  * back), and after the activity was made again while the screen was off (onDestroy, then a new one's onCreate,
  * onStart and onResume): Voidcraft's spinners stood still after standby on a Pixel Fold (0.1.36), the new activity
  * never saying the app was back. Wired as `Motion` wires its pacer: away on away, back on a return, and back on the
  * window's focus if still away. */
class ScreenWakeSpec extends AnyWordSpec with Matchers {
  private class Wake extends PacerRig {
    private def back(): Unit = pacer.back()
    val presence = new ActivityPresence(new Presence(() => pacer.away(), () => back()))
    def focus(has: Boolean): Unit = if (has && pacer.isAway) back()

    /** The screen off with `activity` in front: no frame comes until it is on again. */
    def off(activity: AnyRef): Unit = {
      presence.stopped(activity)
      focus(false)
    }
    /** The screen on and unlocked, `activity` in front. */
    def on(activity: AnyRef): Unit = {
      presence.resumed(activity)
      focus(true)
    }
    /** `ms` with the screen off: time passes and no frame comes. */
    def dark(ms: Long): Unit = {
      advance(ms)
      frameAsked shouldBe false
    }
  }

  "The motions" should {
    "go on after the screen goes off and on" in {
      val w = new Wake
      val a = new Object
      w.presence.created(a)
      w.presence.resumed(a)
      w.pacer.ask()
      w.run(5)
      w.advance(30L)
      w.off(a)
      w.dark(20000L)
      w.steps shouldBe 5
      w.posted shouldBe empty
      w.on(a)
      w.run(5)
      w.steps shouldBe 10
    }

    "go on after the activity was made again while the screen was off" in {
      val w = new Wake
      val a = new Object
      w.presence.created(a)
      w.presence.resumed(a)
      w.pacer.ask()
      w.run(3)
      w.off(a)
      w.dark(5000L)
      val b = new Object
      w.presence.created(b)
      w.dark(5000L)
      w.on(b)
      w.pacer.isAway shouldBe false
      w.run(4)
      w.steps shouldBe 7
    }

    "go on when a new activity comes in front before the old one is stopped" in {
      val w = new Wake
      val a = new Object
      val b = new Object
      w.presence.created(a)
      w.presence.resumed(a)
      w.pacer.ask()
      w.run(2)
      w.presence.created(b)
      w.presence.resumed(b)
      w.presence.stopped(a)
      w.pacer.isAway shouldBe false
      w.run(3)
      w.steps shouldBe 5
    }

    "go on when the window has the focus again with no return said" in {
      val w = new Wake
      val a = new Object
      w.presence.created(a)
      w.presence.resumed(a)
      w.pacer.ask()
      w.run(2)
      w.off(a)
      w.dark(3000L)
      w.focus(true)
      w.run(3)
      w.steps shouldBe 5
    }

    "go on through rounds of the screen off and on, with and without the activity made again" in {
      val w = new Wake
      var front: AnyRef = new Object
      w.presence.created(front)
      w.presence.resumed(front)
      w.pacer.ask()
      (1 to 6).foreach { round =>
        w.run(2)
        w.off(front)
        w.dark(round * 1000L)
        if (round % 2 == 0) {
          front = new Object
          w.presence.created(front)
        }
        w.on(front)
      }
      w.run(2)
      w.steps shouldBe 14
    }
  }
}
