package everyui.event

import everyui.render.Platform
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A consumed pointer event keeps the components under it from firing it while it climbs, and no longer: as long as
  * the platform's one event is being told, where it tells its events apart, else for a moment. */
class PointerSpec extends AnyWordSpec with Matchers {
  "A consumed event" should {
    "block its equal while the same event of the platform's climbs" in {
      val click = PointerEvent(PointerKind.Click, 10.0, 20.0)
      click.consume()
      Pointer.consumedEvent = Platform.pointerEvent()
      Pointer.blocks(PointerEvent(PointerKind.Click, 10.0, 20.0)) shouldBe true
      Pointer.blocks(PointerEvent(PointerKind.Click, 11.0, 20.0)) shouldBe false
    }
    "let the platform's next event fire, however soon" in {
      val click = PointerEvent(PointerKind.Click, 10.0, 20.0)
      click.consume()
      Pointer.consumedEvent = Platform.pointerEvent() + 1L
      Pointer.blocks(PointerEvent(PointerKind.Click, 10.0, 20.0)) shouldBe false
    }
    "block for a moment where the platform cannot tell its events apart, and let a second click fire after" in {
      val click = PointerEvent(PointerKind.Click, 10.0, 20.0)
      click.consume()
      Pointer.consumedEvent = -1L
      Pointer.blocks(PointerEvent(PointerKind.Click, 10.0, 20.0)) shouldBe true
      Pointer.consumedAt = Pointer.consumedAt - Pointer.hold - 1.0
      Pointer.blocks(PointerEvent(PointerKind.Click, 10.0, 20.0)) shouldBe false
    }
  }
}
